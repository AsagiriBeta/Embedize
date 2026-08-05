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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Persists datapack structure groups to {@code groups.yml}.
 */
public final class GroupManager {

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
                List<String> namespaces = sec.getStringList("namespaces");
                List<String> worlds = sec.getStringList("allowed-worlds");
                try {
                    StructureGroup group = new StructureGroup(id, display, namespaces, worlds);
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
                "default",
                "Default",
                namespaces,
                worlds
        );
        groups.put(migrated.getId(), migrated);
        plugin.getLogger().info("Created groups.yml with migrated group 'default'.");
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Embedize structure groups",
                "Each group has its own namespaces (datapack structure namespaces)",
                "and its own allowed-worlds whitelist.",
                "Vanilla minecraft: structures are never managed."
        ));
        for (StructureGroup group : groups.values()) {
            String path = "groups." + group.getId();
            yaml.set(path + ".display-name", group.getDisplayName());
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
        groups.put(nid, new StructureGroup(nid, displayName, List.of(), List.of()));
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
     * Find the first group that owns this structure namespace (non-vanilla).
     */
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
        if (namespace == null || namespace.isBlank()) {
            return out;
        }
        String ns = namespace.trim().toLowerCase(Locale.ROOT);
        if ("minecraft".equals(ns)) {
            return out;
        }
        for (StructureGroup group : groups.values()) {
            if (group.ownsNamespace(ns)) {
                out.add(group);
            }
        }
        return out;
    }
}
