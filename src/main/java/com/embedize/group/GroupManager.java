package com.embedize.group;

import com.embedize.EmbedizePlugin;
import com.embedize.compat.LuckPermsHook;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Persists datapack structure groups to {@code groups.yml}.
 */
public final class GroupManager {

    private static final Map<String, List<String>> BUILTIN_PACK_NAMESPACES = Map.of(
            "dungeons-and-taverns", List.of("nova_structures"),
            "dnt", List.of("nova_structures"),
            "towns-and-towers", List.of("towns_and_towers"),
            "towns_and_towers", List.of("towns_and_towers"),
            "t_and_t", List.of("towns_and_towers")
    );

    private final EmbedizePlugin plugin;
    private final File file;
    private final Map<String, StructureGroup> groups = new LinkedHashMap<>();
    private LuckPermsHook luckPermsHook;

    public GroupManager(EmbedizePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "groups.yml");
    }

    public void setLuckPermsHook(LuckPermsHook luckPermsHook) {
        this.luckPermsHook = luckPermsHook;
    }

    public synchronized void load() {
        groups.clear();
        if (!file.exists()) {
            plugin.saveResource("groups.yml", false);
        }
        if (!file.exists()) {
            migrateFromLegacyConfig();
            save();
            refreshDynamicPermissions();
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("groups");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(id);
                if (sec == null) {
                    continue;
                }
                String display = sec.getString("display-name", id);
                List<String> packs = sec.getStringList("packs");
                if (packs.isEmpty()) {
                    packs = sec.getStringList("datapacks");
                }
                List<String> namespaces = sec.getStringList("namespaces");
                List<String> worlds = sec.getStringList("allowed-worlds");
                try {
                    StructureGroup group = new StructureGroup(id, display, packs, namespaces, worlds);
                    groups.put(group.getId(), group);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Skipping invalid group '" + id + "': " + ex.getMessage());
                }
            }
        }
        if (groups.isEmpty()) {
            migrateFromLegacyConfig();
            save();
        }
        refreshDynamicPermissions();
    }

    private void migrateFromLegacyConfig() {
        FileConfiguration cfg = plugin.getConfig();
        List<String> worlds = cfg.getStringList("allowed-worlds");
        List<String> namespaces = cfg.getStringList("structure-filter.namespaces");
        if (namespaces.isEmpty()) {
            namespaces = List.of("nova_structures");
        }
        if (worlds.isEmpty()) {
            worlds = List.of("resource");
        }
        StructureGroup migrated = new StructureGroup(
                "dungeons",
                "Dungeons and Taverns",
                List.of("dungeons-and-taverns"),
                namespaces,
                worlds
        );
        groups.put(migrated.getId(), migrated);
        plugin.getLogger().info("Created groups.yml with migrated group 'dungeons'.");
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Embedize structure groups",
                "Place structure datapacks in the world datapacks folder yourself.",
                "packs = labels; namespaces = isolation keys; allowed-worlds = whitelist.",
                "Vanilla minecraft: structures are never managed."
        ));
        for (StructureGroup group : groups.values()) {
            String path = "groups." + group.getId();
            yaml.set(path + ".display-name", group.getDisplayName());
            yaml.set(path + ".packs", group.packList());
            yaml.set(path + ".namespaces", group.namespaceList());
            yaml.set(path + ".allowed-worlds", group.worldList());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save groups.yml: " + e.getMessage());
        }
        refreshDynamicPermissions();
    }

    private void refreshDynamicPermissions() {
        if (luckPermsHook != null) {
            luckPermsHook.registerGroupPermissions(groups.keySet());
        }
    }

    public synchronized Optional<StructureGroup> get(String id) {
        String nid = StructureGroup.normalizeId(id);
        if (nid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(groups.get(nid));
    }

    public synchronized Collection<StructureGroup> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(groups.values()));
    }

    public synchronized Set<String> ids() {
        return Collections.unmodifiableSet(groups.keySet());
    }

    public synchronized boolean create(String id, String displayName) {
        String nid = StructureGroup.normalizeId(id);
        if (nid == null || groups.containsKey(nid)) {
            return false;
        }
        groups.put(nid, new StructureGroup(nid, displayName, List.of(), List.of(), List.of()));
        save();
        return true;
    }

    public synchronized boolean delete(String id) {
        String nid = StructureGroup.normalizeId(id);
        if (nid == null || !groups.containsKey(nid)) {
            return false;
        }
        groups.remove(nid);
        save();
        return true;
    }

    /**
     * Resolve a pack id to structure namespaces (builtins + treat token as namespace).
     */
    public List<String> resolvePackNamespaces(String packId) {
        String id = packId == null ? null : packId.trim().toLowerCase(Locale.ROOT);
        if (id == null || id.isBlank()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        List<String> builtin = BUILTIN_PACK_NAMESPACES.get(id);
        if (builtin != null) {
            out.addAll(builtin);
        }
        // If still empty, treat the token itself as a namespace (except minecraft)
        if (out.isEmpty() && !"minecraft".equals(id)) {
            out.add(id);
        }
        out.remove("minecraft");
        return new ArrayList<>(out);
    }

    /**
     * Add a datapack to a group (records pack id + resolved namespaces).
     */
    public synchronized String addPackToGroup(String groupId, String packId) {
        Optional<StructureGroup> opt = get(groupId);
        if (opt.isEmpty()) {
            return "not-found";
        }
        StructureGroup group = opt.get();
        String pack = packId.trim().toLowerCase(Locale.ROOT);
        if ("minecraft".equals(pack)) {
            return "vanilla";
        }
        List<String> namespaces = resolvePackNamespaces(pack);
        for (String ns : namespaces) {
            Optional<StructureGroup> owner = findByNamespace(ns);
            if (owner.isPresent() && !owner.get().getId().equals(group.getId())) {
                return "ns-taken:" + owner.get().getId() + ":" + ns;
            }
        }
        boolean changed = group.addPack(pack);
        for (String ns : namespaces) {
            changed |= group.addNamespace(ns);
        }
        if (changed) {
            save();
        }
        return changed ? "ok" : "noop";
    }

    public synchronized String removePackFromGroup(String groupId, String packId) {
        Optional<StructureGroup> opt = get(groupId);
        if (opt.isEmpty()) {
            return "not-found";
        }
        StructureGroup group = opt.get();
        String pack = packId.trim().toLowerCase(Locale.ROOT);
        List<String> namespaces = resolvePackNamespaces(pack);
        boolean changed = group.removePack(pack);
        for (String ns : namespaces) {
            // Only remove namespace if no remaining pack in this group still needs it
            boolean stillNeeded = false;
            for (String remaining : group.getPacks()) {
                if (resolvePackNamespaces(remaining).contains(ns)) {
                    stillNeeded = true;
                    break;
                }
            }
            if (!stillNeeded) {
                changed |= group.removeNamespace(ns);
            }
        }
        // Also allow remove by bare namespace
        if (group.getNamespaces().contains(pack)) {
            changed |= group.removeNamespace(pack);
        }
        if (changed) {
            save();
        }
        return changed ? "ok" : "noop";
    }

    public synchronized Optional<StructureGroup> findByNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return Optional.empty();
        }
        String ns = namespace.trim().toLowerCase(Locale.ROOT);
        if ("minecraft".equals(ns)) {
            return Optional.empty();
        }
        for (StructureGroup group : groups.values()) {
            if (group.ownsNamespace(ns)) {
                return Optional.of(group);
            }
        }
        return Optional.empty();
    }

    public synchronized List<StructureGroup> findAllByNamespace(String namespace) {
        List<StructureGroup> out = new ArrayList<>();
        findByNamespace(namespace).ifPresent(out::add);
        return out;
    }
}
