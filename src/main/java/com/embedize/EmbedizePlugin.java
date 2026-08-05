package com.embedize;

import com.embedize.border.BorderListener;
import com.embedize.border.BorderManager;
import com.embedize.command.EmbedizeCommand;
import com.embedize.compat.LuckPermsHook;
import com.embedize.compat.MultiverseHook;
import com.embedize.config.PluginConfig;
import com.embedize.datapack.DatapackService;
import com.embedize.group.GroupManager;
import com.embedize.structure.StructureIsolationListener;
import com.embedize.util.SchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;

public final class EmbedizePlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private GroupManager groupManager;
    private BorderManager borderManager;
    private DatapackService datapackService;
    private StructureIsolationListener isolationListener;
    private LuckPermsHook luckPermsHook;
    private MultiverseHook multiverseHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(this);
        this.groupManager = new GroupManager(this);
        this.pluginConfig.setGroupManager(groupManager);

        this.luckPermsHook = new LuckPermsHook(this);
        this.luckPermsHook.registerBasePermissions();
        this.groupManager.setLuckPermsHook(luckPermsHook);

        this.groupManager.load();
        this.pluginConfig.reload();

        this.borderManager = new BorderManager(this);
        this.borderManager.load();

        // Soft Multiverse-Core 5 typed API (MultiverseCoreApi.whenLoaded / ServicesManager)
        // Docs: https://mvplugins.org/core/developers/developer-api-starter/
        this.multiverseHook = new MultiverseHook(this);
        this.pluginConfig.setMultiverseHook(multiverseHook);

        this.datapackService = new DatapackService(this, pluginConfig);
        this.isolationListener = new StructureIsolationListener(this, pluginConfig, multiverseHook);
        getServer().getPluginManager().registerEvents(isolationListener, this);
        getServer().getPluginManager().registerEvents(new BorderListener(this), this);

        EmbedizeCommand command = new EmbedizeCommand(this);
        var pluginCommand = getCommand("embedize");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        // Optional TFG biome-tag datapack (not a TFG API — TFG has none for plugins)
        if (pluginConfig.isInstallTfgBridge()) {
            SchedulerUtil.runAsync(this, () -> {
                try {
                    datapackService.ensureInstalled();
                } catch (Exception e) {
                    getLogger().severe("Failed to install TFG biome-tag bridge: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }

        getLogger().info("Embedize " + getDescription().getVersion() + " enabled. groups="
                + groupManager.ids() + " borders=" + borderManager.worldNames()
                + " multiverse=" + multiverseHook.isPresent()
                + " tfg=" + multiverseHook.isTerraformGeneratorPresent());
    }

    @Override
    public void onDisable() {
        if (borderManager != null) {
            borderManager.shutdown();
            borderManager.save();
        }
        if (groupManager != null) {
            groupManager.save();
        }
        getLogger().info("Embedize disabled.");
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public GroupManager getGroupManager() {
        return groupManager;
    }

    public BorderManager getBorderManager() {
        return borderManager;
    }

    public DatapackService getDatapackService() {
        return datapackService;
    }

    public StructureIsolationListener getIsolationListener() {
        return isolationListener;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public MultiverseHook getMultiverseHook() {
        return multiverseHook;
    }

    public void reloadPlugin() {
        reloadConfig();
        groupManager.load();
        pluginConfig.reload();
        borderManager.load();
        getLogger().info("Configuration reloaded. groups=" + groupManager.ids()
                + " borders=" + borderManager.worldNames());
    }
}
