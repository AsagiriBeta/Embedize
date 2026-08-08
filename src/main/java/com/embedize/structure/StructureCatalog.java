package com.embedize.structure;

import com.embedize.EmbedizePlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.generator.structure.Structure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * Thin read-only index of structure ids shipped in the bundled structure datapacks.
 * Placement itself is done by the vanilla jigsaw / structure engine.
 * <p>
 * Also drives {@link StructureWorldGate}: Paper loads plugin datapacks into a
 * <em>global</em> registry when enabled. Natural spawn outside Embedize worlds is
 * filtered for (1) any id under a custom namespace present in the catalog, and
 * (2) exact catalog ids including overhauled {@code minecraft:*} (DnT ancient
 * city, etc.). {@code minecraft:stronghold} stays ungated for progression.
 * Pure vanilla ids that never appear in the catalog stay ungated.
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
    private final Set<String> customNamespaces = new HashSet<>();
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
                        "embedize-structure-packs/catalog.json missing — run buildStructureDatapack / use jar.");
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
                String key = id.toLowerCase(Locale.ROOT);
                byId.put(key, entry);
                rememberNamespace(key);
            }
            plugin.getLogger().info("Structure catalog: " + byId.size()
                    + " structures / nbt≈" + nbtCount
                    + " / packs=" + packNames.size()
                    + " / custom-ns=" + customNamespaces.size()
                    + " (vanilla engine via bundled datapack; catalog+custom-ns gated to Embedize worlds)");
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

    private void rememberNamespace(@NotNull String structureIdLower) {
        int colon = structureIdLower.indexOf(':');
        if (colon <= 0) {
            return;
        }
        String ns = structureIdLower.substring(0, colon);
        // minecraft:* (vanilla + pack overhauls / minecraft-ns additions) are never
        // namespace-gated; only custom pack namespaces are.
        if (!"minecraft".equals(ns)) {
            customNamespaces.add(ns);
        }
    }

    /**
     * True when this structure id should be spawn/locate-gated outside Embedize
     * worlds: custom namespaces from the catalog, or an exact id listed in
     * {@code catalog.json} (including overhauled {@code minecraft:*}).
     */
    public boolean isBundledStructure(@Nullable String id) {
        return matchesBundled(id, customNamespaces, byId.keySet());
    }

    /**
     * Pure matching used by {@link #isBundledStructure} and unit tests.
     */
    static boolean matchesBundled(
            @Nullable String id,
            @NotNull Set<String> customNamespaces,
            @NotNull Set<String> catalogIdsLower
    ) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        int colon = key.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        String ns = key.substring(0, colon);
        if (!"minecraft".equals(ns) && customNamespaces.contains(ns)) {
            return true;
        }
        return catalogIdsLower.contains(key);
    }

    /** @deprecated Prefer {@link #matchesBundled(String, Set, Set)}. */
    @Deprecated
    static boolean matchesBundled(
            @Nullable String id,
            @NotNull Set<String> customNamespaces
    ) {
        return matchesBundled(id, customNamespaces, Set.of());
    }

    public @Nullable String resolveStructureId(@Nullable Structure structure) {
        if (structure == null) {
            return null;
        }
        Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        if (registry == null) {
            return null;
        }
        NamespacedKey key = registry.getKey(structure);
        return key == null ? null : key.asString();
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public int customNamespaceCount() {
        return customNamespaces.size();
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
