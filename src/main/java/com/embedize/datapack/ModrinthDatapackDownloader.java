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
 * Prefers datapack zip files; falls back to Fabric/NeoForge mod jars (unzipped as datapacks)
 * when no zip exists for the server version (e.g. Towns and Towers on 1.21.4).
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
                            return (n.endsWith(".zip") || n.endsWith(".jar"))
                                    && (n.contains(source.id().toLowerCase(Locale.ROOT))
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
                .orElseThrow(() -> new IOException("No matching datapack/mod on Modrinth for " + project + " / " + serverVersion));

        Path target = cacheDir.resolve(sanitize(chosen.filename()));
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            plugin.getLogger().info("Using cached " + source.id() + ": " + target.getFileName()
                    + (chosen.zip() ? "" : " (mod jar → datapack)"));
            return target;
        }

        plugin.getLogger().info("Downloading " + source.id() + " " + chosen.versionNumber()
                + " (" + chosen.filename() + ")");
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
        List<ScoredFile> scored = new ArrayList<>();

        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            String versionNumber = version.get("version_number").getAsString();
            if (source.pinnedVersion() != null
                    && !versionNumber.toLowerCase(Locale.ROOT).contains(source.pinnedVersion().toLowerCase(Locale.ROOT))) {
                continue;
            }

            boolean exactGame = false;
            boolean familyGame = false;
            JsonArray gameVersions = version.getAsJsonArray("game_versions");
            for (JsonElement gv : gameVersions) {
                String g = gv.getAsString();
                if (VersionUtil.versionMatches(serverVersion, g)) {
                    exactGame = true;
                }
                if (VersionUtil.sameMinorFamily(serverVersion, g)) {
                    familyGame = true;
                }
            }
            if (!exactGame && !familyGame && source.pinnedVersion() == null) {
                continue;
            }
            if (!exactGame && !familyGame) {
                continue;
            }

            boolean datapackLoader = false;
            if (version.has("loaders")) {
                for (JsonElement loader : version.getAsJsonArray("loaders")) {
                    if ("datapack".equalsIgnoreCase(loader.getAsString())) {
                        datapackLoader = true;
                        break;
                    }
                }
            }

            JsonArray files = version.getAsJsonArray("files");
            for (JsonElement fileEl : files) {
                JsonObject file = fileEl.getAsJsonObject();
                String filename = file.get("filename").getAsString();
                String fileUrl = file.get("url").getAsString();
                boolean primary = file.has("primary") && file.get("primary").getAsBoolean();
                boolean zip = filename.toLowerCase(Locale.ROOT).endsWith(".zip");
                boolean jar = filename.toLowerCase(Locale.ROOT).endsWith(".jar");
                if (!zip && !jar) {
                    continue;
                }
                // When preferring datapack zip: skip jars on first-tier scoring (they get worse rank)
                ModrinthFile candidate = new ModrinthFile(versionNumber, filename, fileUrl, zip, primary);
                int score = 0;
                if (exactGame) {
                    score += 2000;
                } else if (familyGame) {
                    score += 400;
                }
                if (zip) {
                    score += 300;
                }
                if (datapackLoader && zip) {
                    score += 150;
                }
                if (primary) {
                    score += 20;
                }
                if (source.preferDatapackZip() && jar && !zip) {
                    // Still allow jar fallback, but rank below any zip for the same match tier
                    score -= 200;
                }
                scored.add(new ScoredFile(candidate, score, versionNumber));
            }
        }

        // Prefer zip when scores tie, but keep exact-version mod jars above
        // family-only datapack zips (e.g. TaT 1.21.4 jar vs 1.21.11 zip).

        return scored.stream()
                .sorted(Comparator
                        .comparingInt(ScoredFile::score).reversed()
                        .thenComparing(ScoredFile::versionNumber, Comparator.reverseOrder()))
                .map(ScoredFile::file)
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

    private record ScoredFile(ModrinthFile file, int score, String versionNumber) {
    }
}
