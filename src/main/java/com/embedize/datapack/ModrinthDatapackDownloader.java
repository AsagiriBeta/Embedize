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
 * Downloads structure datapacks from Modrinth (third-party; not redistributed in the repo).
 */
public final class ModrinthDatapackDownloader {

    private static final String USER_AGENT = "Embedize/1.0 (Paper plugin; +https://modrinth.com)";

    private final EmbedizePlugin plugin;
    private final HttpClient httpClient;

    public ModrinthDatapackDownloader(EmbedizePlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Path ensureCached(PluginConfig.DatapackSource source) throws IOException, InterruptedException {
        String project = source.modrinthProject();
        Path cacheDir = plugin.getDataFolder().toPath().resolve("cache").resolve(source.id());
        Files.createDirectories(cacheDir);

        Path manualDir = plugin.getDataFolder().toPath().resolve("datapacks");
        if (Files.isDirectory(manualDir)) {
            try (var stream = Files.list(manualDir)) {
                Optional<Path> local = stream
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return n.endsWith(".zip") && (n.contains(source.id().toLowerCase(Locale.ROOT))
                                    || n.contains(project.toLowerCase(Locale.ROOT).replace(' ', '-')));
                        })
                        .findFirst();
                if (local.isPresent()) {
                    plugin.getLogger().info("Using manual pack for " + source.id() + ": " + local.get().getFileName());
                    return local.get();
                }
            }
        }

        String serverVersion = detectMinecraftVersion();
        plugin.getLogger().info("Resolving " + project + " for MC " + serverVersion + " from Modrinth...");

        ModrinthFile chosen = selectVersion(project, serverVersion, source)
                .orElseThrow(() -> new IOException("No matching datapack on Modrinth for " + project + " / " + serverVersion));

        Path target = cacheDir.resolve(sanitize(chosen.filename()));
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            plugin.getLogger().info("Using cached " + source.id() + ": " + target.getFileName());
            return target;
        }

        plugin.getLogger().info("Downloading " + source.id() + " " + chosen.versionNumber());
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
        return target;
    }

    private Optional<ModrinthFile> selectVersion(
            String project,
            String serverVersion,
            PluginConfig.DatapackSource source
    ) throws IOException, InterruptedException {
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
            if (source.pinnedVersion() != null
                    && !versionNumber.toLowerCase(Locale.ROOT).contains(source.pinnedVersion().toLowerCase(Locale.ROOT))) {
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
            if (!matchesGame && source.pinnedVersion() == null) {
                continue;
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
                if (source.preferDatapackZip() && jar && !zip) {
                    continue;
                }
                ModrinthFile candidate = new ModrinthFile(versionNumber, filename, fileUrl, zip, primary);
                if (bestFile == null) {
                    bestFile = candidate;
                } else if (source.preferDatapackZip() && candidate.zip() && !bestFile.zip()) {
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
        String bukkit = org.bukkit.Bukkit.getBukkitVersion();
        String normalized = VersionUtil.normalizeVersion(bukkit);
        if (!normalized.isBlank() && Character.isDigit(normalized.charAt(0))) {
            return normalized;
        }
        try {
            return VersionUtil.normalizeVersion(org.bukkit.Bukkit.getMinecraftVersion());
        } catch (NoSuchMethodError err) {
            return VersionUtil.normalizeVersion(org.bukkit.Bukkit.getVersion());
        }
    }

    private static String sanitize(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._\\- ]", "_");
    }

    private record ModrinthFile(String versionNumber, String filename, String url, boolean zip, boolean primary) {
    }
}
