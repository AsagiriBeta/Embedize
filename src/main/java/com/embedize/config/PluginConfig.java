package com.embedize.config;

import com.embedize.EmbedizePlugin;
import com.embedize.group.GroupManager;
import com.embedize.group.StructureGroup;
import com.embedize.structure.IsolationPolicy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PluginConfig {

    public record DatapackSource(
            String id,
            boolean enabled,
            String type,
            String modrinthProject,
            boolean preferDatapackZip,
            String pinnedVersion,
            String groupId,
            List<String> namespaces
    ) {
    }

    private final EmbedizePlugin plugin;

    private boolean enabled;
    private boolean denyUnresolvedKeys;
    private boolean manageUngrouped;
    private boolean debugCancellations;
    private boolean installTfgBridge;
    private String installDirectory;
    private boolean resolveAliases;
    private List<DatapackSource> datapackSources;
    private IsolationPolicy isolationPolicy;
    private GroupManager groupManager;

    public PluginConfig(EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    public void setGroupManager(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("enabled", true);
        this.denyUnresolvedKeys = cfg.getBoolean("deny-unresolved-structure-keys", false);
        this.manageUngrouped = cfg.getBoolean("manage-ungrouped", false);
        this.debugCancellations = cfg.getBoolean("debug-cancellations", false);
        this.resolveAliases = cfg.getBoolean("multiverse.resolve-aliases", true);

        ConfigurationSection packs = cfg.getConfigurationSection("datapacks");
        this.installTfgBridge = packs == null || packs.getBoolean("install-tfg-bridge", true);
        this.installDirectory = packs == null ? "default-world" : packs.getString("install-directory", "default-world");
        this.datapackSources = parseSources(packs);

        rebuildIsolationPolicy();
    }

    public void rebuildIsolationPolicy() {
        IsolationPolicy.GroupWorldResolver resolver = new IsolationPolicy.GroupWorldResolver() {
            @Override
            public Optional<StructureGroup> findGroup(String namespace) {
                if (groupManager == null) {
                    return Optional.empty();
                }
                return groupManager.findByNamespace(namespace);
            }

            @Override
            public boolean isWorldAllowed(StructureGroup group, String worldName) {
                if (group.allowsWorld(worldName)) {
                    return true;
                }
                if (!resolveAliases || groupManager == null) {
                    return false;
                }
                // Alias check deferred to listener with MultiverseHook; exact name here
                return false;
            }
        };
        this.isolationPolicy = new IsolationPolicy(enabled, denyUnresolvedKeys, manageUngrouped, resolver);
    }

    private List<DatapackSource> parseSources(ConfigurationSection packs) {
        List<DatapackSource> list = new ArrayList<>();
        if (packs == null) {
            return list;
        }
        // New multi-source format
        if (packs.isList("sources")) {
            List<?> raw = packs.getList("sources");
            if (raw != null) {
                int i = 0;
                for (Object entry : raw) {
                    if (entry instanceof Map<?, ?> map) {
                        list.add(fromMap(map, "pack-" + (i++)));
                    } else if (entry instanceof ConfigurationSection sec) {
                        list.add(fromSection(sec, "pack-" + (i++)));
                    }
                }
            }
        }
        ConfigurationSection sourcesSec = packs.getConfigurationSection("sources");
        if (sourcesSec != null) {
            for (String key : sourcesSec.getKeys(false)) {
                ConfigurationSection sec = sourcesSec.getConfigurationSection(key);
                if (sec != null) {
                    list.add(fromSection(sec, key));
                }
            }
        }
        // Legacy single DnT block
        if (list.isEmpty()) {
            ConfigurationSection dnt = packs.getConfigurationSection("dungeons-and-taverns");
            if (dnt == null || dnt.getBoolean("enabled", true)) {
                list.add(new DatapackSource(
                        "dungeons-and-taverns",
                        dnt == null || dnt.getBoolean("enabled", true),
                        "modrinth",
                        dnt == null ? "dungeons-and-taverns" : dnt.getString("modrinth-project", "dungeons-and-taverns"),
                        dnt == null || dnt.getBoolean("prefer-datapack-zip", true),
                        blankToNull(dnt == null ? null : dnt.getString("pinned-version")),
                        "dungeons",
                        List.of("nova_structures")
                ));
            }
        }
        return Collections.unmodifiableList(list);
    }

    private static DatapackSource fromSection(ConfigurationSection sec, String fallbackId) {
        List<String> namespaces = sec.getStringList("namespaces");
        if (namespaces.isEmpty() && "dungeons-and-taverns".equalsIgnoreCase(sec.getString("id", fallbackId))) {
            namespaces = List.of("nova_structures");
        }
        return new DatapackSource(
                sec.getString("id", fallbackId),
                sec.getBoolean("enabled", true),
                sec.getString("type", "modrinth"),
                sec.getString("modrinth-project", sec.getString("id", fallbackId)),
                sec.getBoolean("prefer-datapack-zip", true),
                blankToNull(sec.getString("pinned-version")),
                blankToNull(sec.getString("group")),
                List.copyOf(namespaces)
        );
    }

    private static DatapackSource fromMap(Map<?, ?> map, String fallbackId) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) {
                m.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        String id = String.valueOf(m.getOrDefault("id", fallbackId));
        List<String> namespaces = new ArrayList<>();
        Object nsObj = m.get("namespaces");
        if (nsObj instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    namespaces.add(String.valueOf(o));
                }
            }
        }
        if (namespaces.isEmpty() && "dungeons-and-taverns".equalsIgnoreCase(id)) {
            namespaces = List.of("nova_structures");
        }
        return new DatapackSource(
                id,
                asBool(m.get("enabled"), true),
                String.valueOf(m.getOrDefault("type", "modrinth")),
                String.valueOf(m.getOrDefault("modrinth-project", id)),
                asBool(m.get("prefer-datapack-zip"), true),
                blankToNull(m.get("pinned-version") == null ? null : String.valueOf(m.get("pinned-version"))),
                blankToNull(m.get("group") == null ? null : String.valueOf(m.get("group"))),
                List.copyOf(namespaces)
        );
    }

    private static boolean asBool(Object o, boolean def) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o == null) {
            return def;
        }
        return Boolean.parseBoolean(String.valueOf(o));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public IsolationPolicy getIsolationPolicy() {
        return isolationPolicy;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isManageUngrouped() {
        return manageUngrouped;
    }

    public boolean isDebugCancellations() {
        return debugCancellations;
    }

    public boolean isInstallTfgBridge() {
        return installTfgBridge;
    }

    public String getInstallDirectory() {
        return installDirectory;
    }

    public boolean isResolveAliases() {
        return resolveAliases;
    }

    public List<DatapackSource> getDatapackSources() {
        return datapackSources;
    }

    // Back-compat accessors used by DatapackService / DntDownloader during transition
    public boolean isDntEnabled() {
        return datapackSources.stream().anyMatch(s ->
                s.enabled() && "dungeons-and-taverns".equalsIgnoreCase(s.id()));
    }

    public String getDntInstallMode() {
        return isDntEnabled() ? "auto" : "skip";
    }

    public String getDntModrinthProject() {
        return datapackSources.stream()
                .filter(s -> "dungeons-and-taverns".equalsIgnoreCase(s.id()))
                .map(DatapackSource::modrinthProject)
                .findFirst()
                .orElse("dungeons-and-taverns");
    }

    public boolean isPreferDatapackZip() {
        return datapackSources.stream()
                .filter(s -> "dungeons-and-taverns".equalsIgnoreCase(s.id()))
                .map(DatapackSource::preferDatapackZip)
                .findFirst()
                .orElse(true);
    }

    public String getPinnedVersion() {
        return datapackSources.stream()
                .filter(s -> "dungeons-and-taverns".equalsIgnoreCase(s.id()))
                .map(DatapackSource::pinnedVersion)
                .findFirst()
                .orElse(null);
    }
}
