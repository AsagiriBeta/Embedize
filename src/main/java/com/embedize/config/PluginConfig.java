package com.embedize.config;

import com.embedize.EmbedizePlugin;
import com.embedize.structure.IsolationPolicy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PluginConfig {

    private final EmbedizePlugin plugin;

    private boolean enabled;
    private boolean denyUnresolvedKeys;
    private IsolationPolicy.StructureFilterMode filterMode;
    private Set<String> allowedWorlds;
    private Set<String> managedNamespaces;
    private boolean debugCancellations;
    private boolean installTfgBridge;
    private boolean dntEnabled;
    private String dntInstallMode;
    private String dntModrinthProject;
    private boolean preferDatapackZip;
    private String pinnedVersion;
    private String installDirectory;
    private boolean resolveAliases;
    private IsolationPolicy isolationPolicy;

    public PluginConfig(EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("enabled", true);
        // Default false: never cancel unidentified structures (safer for vanilla)
        this.denyUnresolvedKeys = cfg.getBoolean("deny-unresolved-structure-keys", false);
        this.allowedWorlds = toLowerSet(cfg.getStringList("allowed-worlds"));

        ConfigurationSection filter = cfg.getConfigurationSection("structure-filter");
        boolean includeVanilla = filter != null && filter.getBoolean("include-vanilla", false);
        if (cfg.contains("include-vanilla")) {
            includeVanilla = cfg.getBoolean("include-vanilla", false);
        }
        this.filterMode = parseFilterMode(filter == null
                ? cfg.getString("structure-filter-mode", "ALL_NON_MINECRAFT")
                : filter.getString("mode", "ALL_NON_MINECRAFT"));
        // Legacy "ALL" meant include vanilla — map to ALL_NON_MINECRAFT + warn unless opted in
        String rawMode = filter == null ? cfg.getString("structure-filter-mode") : filter.getString("mode");
        if (rawMode != null && rawMode.trim().equalsIgnoreCase("ALL")) {
            if (!includeVanilla) {
                plugin.getLogger().warning("structure-filter.mode ALL is no longer used. "
                        + "Vanilla minecraft: structures are never managed unless include-vanilla: true. "
                        + "Using ALL_NON_MINECRAFT.");
            }
            this.filterMode = IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT;
        }
        List<String> nsList = filter != null
                ? filter.getStringList("namespaces")
                : cfg.getStringList("managed-namespaces");
        this.managedNamespaces = toLowerSet(nsList);
        if (managedNamespaces.isEmpty() && filterMode == IsolationPolicy.StructureFilterMode.NAMESPACES) {
            managedNamespaces = Set.of("nova_structures");
        }
        if (!includeVanilla && managedNamespaces.contains("minecraft")) {
            plugin.getLogger().warning("Ignoring 'minecraft' in structure-filter.namespaces "
                    + "(vanilla is not managed unless include-vanilla: true).");
        }

        this.debugCancellations = cfg.getBoolean("debug-cancellations", false);

        ConfigurationSection packs = cfg.getConfigurationSection("datapacks");
        this.installTfgBridge = packs == null || packs.getBoolean("install-tfg-bridge", true);
        this.installDirectory = packs == null ? "default-world" : packs.getString("install-directory", "default-world");

        ConfigurationSection dnt = packs == null ? null : packs.getConfigurationSection("dungeons-and-taverns");
        this.dntEnabled = dnt == null || dnt.getBoolean("enabled", true);
        this.dntInstallMode = dnt == null ? "auto" : dnt.getString("install", "auto");
        this.dntModrinthProject = dnt == null ? "dungeons-and-taverns" : dnt.getString("modrinth-project", "dungeons-and-taverns");
        this.preferDatapackZip = dnt == null || dnt.getBoolean("prefer-datapack-zip", true);
        this.pinnedVersion = dnt == null ? null : dnt.getString("pinned-version", "");
        if (pinnedVersion != null && pinnedVersion.isBlank()) {
            pinnedVersion = null;
        }

        ConfigurationSection mv = cfg.getConfigurationSection("multiverse");
        this.resolveAliases = mv == null || mv.getBoolean("resolve-aliases", true);

        this.isolationPolicy = new IsolationPolicy(
                enabled,
                includeVanilla,
                filterMode,
                allowedWorlds,
                managedNamespaces,
                denyUnresolvedKeys
        );

        if (allowedWorlds.isEmpty()) {
            plugin.getLogger().warning("allowed-worlds is empty — managed structures will generate in NO worlds.");
        }
    }

    private static IsolationPolicy.StructureFilterMode parseFilterMode(String raw) {
        if (raw == null) {
            return IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT;
        }
        try {
            return IsolationPolicy.StructureFilterMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return IsolationPolicy.StructureFilterMode.ALL_NON_MINECRAFT;
        }
    }

    private static Set<String> toLowerSet(List<String> values) {
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                set.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(set);
    }

    public IsolationPolicy getIsolationPolicy() {
        return isolationPolicy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public IsolationPolicy.StructureFilterMode getFilterMode() {
        return filterMode;
    }

    public Set<String> getConfiguredWorlds() {
        return allowedWorlds;
    }

    public Set<String> getManagedNamespaces() {
        return managedNamespaces;
    }

    public boolean isDebugCancellations() {
        return debugCancellations;
    }

    public boolean isInstallTfgBridge() {
        return installTfgBridge;
    }

    public boolean isDntEnabled() {
        return dntEnabled;
    }

    public String getDntInstallMode() {
        return dntInstallMode;
    }

    public String getDntModrinthProject() {
        return dntModrinthProject;
    }

    public boolean isPreferDatapackZip() {
        return preferDatapackZip;
    }

    public String getPinnedVersion() {
        return pinnedVersion;
    }

    public String getInstallDirectory() {
        return installDirectory;
    }

    public boolean isResolveAliases() {
        return resolveAliases;
    }
}
