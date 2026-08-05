package com.dsi.isolator;

import com.dsi.isolator.command.DsiCommand;
import com.dsi.isolator.config.PluginConfig;
import com.dsi.isolator.datapack.DatapackService;
import com.dsi.isolator.structure.StructureIsolationListener;
import com.dsi.isolator.util.SchedulerUtil;
import org.bukkit.plugin.java.JavaPlugin;

public final class DimensionStructureIsolatorPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private DatapackService datapackService;
    private StructureIsolationListener isolationListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(this);
        this.pluginConfig.reload();

        this.datapackService = new DatapackService(this, pluginConfig);
        this.isolationListener = new StructureIsolationListener(this, pluginConfig);

        getServer().getPluginManager().registerEvents(isolationListener, this);

        DsiCommand command = new DsiCommand(this);
        var pluginCommand = getCommand("dsi");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        // Datapack install may do IO — run off the region/global tick where possible
        SchedulerUtil.runAsync(this, () -> {
            try {
                datapackService.ensureInstalled();
            } catch (Exception e) {
                getLogger().severe("Failed to install bundled/downloaded datapacks: " + e.getMessage());
                e.printStackTrace();
            }
        });

        getLogger().info("DimensionStructureIsolator enabled. Mode=" + pluginConfig.getMode()
                + " worlds=" + pluginConfig.getConfiguredWorlds());
    }

    @Override
    public void onDisable() {
        getLogger().info("DimensionStructureIsolator disabled.");
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public DatapackService getDatapackService() {
        return datapackService;
    }

    public StructureIsolationListener getIsolationListener() {
        return isolationListener;
    }

    public void reloadPlugin() {
        reloadConfig();
        pluginConfig.reload();
        getLogger().info("Configuration reloaded. Mode=" + pluginConfig.getMode()
                + " worlds=" + pluginConfig.getConfiguredWorlds());
    }
}
