package com.embedize.datapack;

import com.embedize.EmbedizePlugin;
import com.embedize.config.PluginConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Downloads Dungeons and Taverns from Modrinth (ARR third-party — not redistributed in the repo).
 */
public final class DntDownloader {

    private static final String USER_AGENT = "Embedize/1.0 (Paper plugin; +https://modrinth.com/datapack/dungeons-and-taverns)";

    private final EmbedizePlugin plugin;
    private final PluginConfig config;
    private final HttpClient httpClient;

    public DntDownloader(EmbedizePlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Path ensureCached() throws IOException, InterruptedException {
        Path cacheDir = plugin.getDataFolder().toPath().resolve("cache").resolve("dungeons-and-taverns");
        Files.createDirectories(cacheDir);

        // Manual drop-in support
        Path manual = plugin.getDataFolder().toPath().resolve("datapacks");
        if (Files.isDirectory(manual)) {
            try (var stream = Files.list(manual)) {
                Optional<Path> local = stream
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return n.endsWith(".zip") && (n.contains("dungeon") || n.contains("tavern") || n.contains("dnt"));
                        })
                        .findFirst();
                if (local.isPresent()) {
                    plugin.getLogger().info("Using manually provided DnT pack: " + local.get().getFileName());
                    return local.get();
                }
            }
        }

        String serverVersion = detectMinecraftVersion();
        plugin.getLogger().info("Resolving Dungeons and Taverns for MC " + serverVersion + " from Modrinth...");

        ModrinthFile chosen = selectVersion(serverVersion)
                .orElseThrow(() -> new IOException("No matching Dungeons and Taverns datapack found on Modrinth for " + serverVersion));

        Path target = cacheDir.resolve(sanitize(chosen.filename()));
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            plugin.getLogger().info("Using cached DnT: " + target.getFileName() + " (" + chosen.versionNumber() + ")");
            return target;
        }

        plugin.getLogger().info("Downloading DnT " + chosen.versionNumber() + " → " + chosen.filename());
        HttpRequest request = HttpRequest.newBuilder(URI.create(chosen.url()))
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(target);
            throw new IOException("Modrinth download failed HTTP " + response.statusCode());
        }
        plugin.getLogger().info("Downloaded DnT to " + target.toAbsolutePath());
        return target;
    }

    private Optional<ModrinthFile> selectVersion(String serverVersion) throws IOException, InterruptedException {
        String project = config.getDntModrinthProject();
        String url = "https://api.modrinth.com/v2/project/" + project + "/version";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Modrinth API HTTP " + response.statusCode());
        }

        JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
        List<ModrinthFile> candidates = new ArrayList<>();
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            String versionNumber = version.get("version_number").getAsString();
            if (config.getPinnedVersion() != null && !config.getPinnedVersion().equalsIgnoreCase(versionNumber)
                    && !versionNumber.toLowerCase(Locale.ROOT).contains(config.getPinnedVersion().toLowerCase(Locale.ROOT))) {
                continue;
            }

            JsonArray gameVersions = version.getAsJsonArray("game_versions");
            boolean matchesGame = false;
            for (JsonElement gv : gameVersions) {
                if (VersionUtil.versionMatches(serverVersion, gv.getAsString())) {
                    matchesGame = true;
                    break;
                }
            }
            if (!matchesGame && config.getPinnedVersion() == null) {
                continue;
            }
            if (!matchesGame && config.getPinnedVersion() != null) {
                matchesGame = true;
            }
            if (!matchesGame) {
                continue;
            }

            JsonArray files = version.getAsJsonArray("files");
            ModrinthFile bestFile = null;
            for (JsonElement fileEl : files) {
                JsonObject file = fileEl.getAsJsonObject();
                String filename = file.get("filename").getAsString();
                String fileUrl = file.get("url").getAsString();
                boolean primary = file.has("primary") && file.get("primary").getAsBoolean();
                boolean zip = filename.toLowerCase(Locale.ROOT).endsWith(".zip");
                boolean jar = filename.toLowerCase(Locale.ROOT).endsWith(".jar");
                if (config.isPreferDatapackZip() && jar && !zip) {
                    continue;
                }
                ModrinthFile candidate = new ModrinthFile(versionNumber, filename, fileUrl, zip, primary);
                if (bestFile == null) {
                    bestFile = candidate;
                } else if (config.isPreferDatapackZip() && candidate.zip() && !bestFile.zip()) {
                    bestFile = candidate;
                } else if (candidate.primary() && !bestFile.primary()) {
                    bestFile = candidate;
                }
            }
            if (bestFile != null) {
                candidates.add(bestFile);
            }
        }

        return candidates.stream()
                .sorted(Comparator
                        .comparing((ModrinthFile f) -> !f.zip())
                        .thenComparing(ModrinthFile::versionNumber, Comparator.reverseOrder()))
                .findFirst();
    }

    public static String detectMinecraftVersion() {
        String bukkit = BukkitVersionAccess.bukkitVersion();
        String normalized = VersionUtil.normalizeVersion(bukkit);
        if (!normalized.isBlank() && Character.isDigit(normalized.charAt(0))) {
            return normalized;
        }
        String minecraft = BukkitVersionAccess.minecraftVersion();
        return VersionUtil.normalizeVersion(minecraft);
    }

    private static String sanitize(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._\\- ]", "_");
    }

    private record ModrinthFile(String versionNumber, String filename, String url, boolean zip, boolean primary) {
    }

    static final class BukkitVersionAccess {
        static String bukkitVersion() {
            return org.bukkit.Bukkit.getBukkitVersion();
        }

        static String minecraftVersion() {
            try {
                return org.bukkit.Bukkit.getMinecraftVersion();
            } catch (NoSuchMethodError err) {
                return org.bukkit.Bukkit.getVersion();
            }
        }
    }
}
