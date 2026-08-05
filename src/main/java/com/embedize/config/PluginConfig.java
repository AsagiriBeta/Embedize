package com.embedize.config;

import com.embedize.EmbedizePlugin;
import com.embedize.group.GroupManager;
import com.embedize.group.StructureGroup;
import com.embedize.structure.IsolationPolicy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Optional;

public final class PluginConfig {

    private final EmbedizePlugin plugin;

    private boolean enabled;
    private boolean denyUnresolvedKeys;
    private boolean manageUngrouped;
    private boolean debugCancellations;
    private boolean installTfgBridge;
    private String installDirectory;
    private boolean resolveAliases;
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
                return false;
            }
        };
        this.isolationPolicy = new IsolationPolicy(enabled, denyUnresolvedKeys, manageUngrouped, resolver);
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
}
