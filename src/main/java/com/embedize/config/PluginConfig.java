package com.embedize.config;

import com.embedize.EmbedizePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PluginConfig {

    public enum Mode {
        ALLOWLIST,
        DENYLIST
    }

    private final EmbedizePlugin plugin;

    private boolean enabled;
    private Mode mode;
    private Set<String> configuredWorlds;
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

    public PluginConfig(EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("enabled", true);
        this.mode = parseMode(cfg.getString("mode", "ALLOWLIST"));
        this.configuredWorlds = toLowerSet(cfg.getStringList("allowed-worlds"));
        this.managedNamespaces = toLowerSet(cfg.getStringList("managed-namespaces"));
        if (managedNamespaces.isEmpty()) {
            managedNamespaces = Set.of("nova_structures");
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
    }

    private static Mode parseMode(String raw) {
        if (raw == null) {
            return Mode.ALLOWLIST;
        }
        try {
            return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Mode.ALLOWLIST;
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

    public boolean isEnabled() {
        return enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public Set<String> getConfiguredWorlds() {
        return configuredWorlds;
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

    public boolean isWorldAllowed(String worldName) {
        if (worldName == null) {
            return false;
        }
        boolean listed = configuredWorlds.contains(worldName.toLowerCase(Locale.ROOT));
        return mode == Mode.ALLOWLIST ? listed : !listed;
    }
}
