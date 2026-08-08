package com.embedize.structure;

import com.embedize.EmbedizePlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Thin read-only index of structure ids shipped in the bundled structure datapacks.
 * Placement itself is done by the vanilla jigsaw / structure engine.
 */
public final class StructureCatalog {

    public record Entry(
            @NotNull String id,
            @NotNull String type,
            @NotNull String biomes,
            @NotNull World.Environment environment
    ) {
    }

    private final EmbedizePlugin plugin;
    private final Map<String, Entry> byId = new LinkedHashMap<>();
    private final List<String> packNames = new ArrayList<>();
    private int nbtCount;
    private int jsonCount;
    private int tagCount;

    public StructureCatalog(EmbedizePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        try (InputStream in = plugin.getResource("embedize-structure-packs/catalog.json")) {
            if (in == null) {
                plugin.getLogger().warning(
                        "embedize-structure-packs/catalog.json missing — run buildStructureDatapack / use fullJar.");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            nbtCount = root.has("nbtCount") ? root.get("nbtCount").getAsInt() : 0;
            jsonCount = root.has("jsonCount") ? root.get("jsonCount").getAsInt() : 0;
            tagCount = root.has("tagCount") ? root.get("tagCount").getAsInt() : 0;
            if (root.has("packs") && root.get("packs").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("packs")) {
                    packNames.add(el.getAsString());
                }
            }
            JsonArray structures = root.has("structures") ? root.getAsJsonArray("structures") : new JsonArray();
            for (JsonElement el : structures) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                String id = o.has("id") ? o.get("id").getAsString() : "";
                if (id.isBlank()) {
                    continue;
                }
                Entry entry = new Entry(
                        id,
                        o.has("type") ? o.get("type").getAsString() : "",
                        o.has("biomes") ? o.get("biomes").getAsString() : "",
                        envOf(o.has("env") ? o.get("env").getAsString() : "OVERWORLD")
                );
                byId.put(id.toLowerCase(Locale.ROOT), entry);
            }
            plugin.getLogger().info("Structure catalog: " + byId.size()
                    + " structures / nbt≈" + nbtCount
                    + " / packs=" + packNames.size()
                    + " (vanilla engine via bundled datapack)");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed loading structure catalog", ex);
        }
    }

    private static World.Environment envOf(String raw) {
        String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "NETHER" -> World.Environment.NETHER;
            case "END", "THE_END" -> World.Environment.THE_END;
            default -> World.Environment.NORMAL;
        };
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public int structureCount() {
        return byId.size();
    }

    public int nbtCount() {
        return nbtCount;
    }

    public int jsonCount() {
        return jsonCount;
    }

    public int tagCount() {
        return tagCount;
    }

    public @NotNull List<String> packNames() {
        return Collections.unmodifiableList(packNames);
    }

    public @NotNull Optional<Entry> find(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public @NotNull List<Entry> all() {
        return List.copyOf(byId.values());
    }

    public @NotNull List<Entry> forWorld(World.Environment env) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : byId.values()) {
            if (e.environment() == env) {
                out.add(e);
            }
        }
        return out;
    }

    public @NotNull List<String> suggest(@Nullable String prefix, int limit) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Entry e : byId.values()) {
            String id = e.id();
            if (p.isEmpty() || id.toLowerCase(Locale.ROOT).startsWith(p) || id.toLowerCase(Locale.ROOT).contains(p)) {
                out.add(id);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }
}
