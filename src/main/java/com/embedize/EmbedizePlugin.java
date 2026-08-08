package com.embedize;

import com.embedize.border.BorderListener;
import com.embedize.border.BorderManager;
import com.embedize.command.EmbedizeCommand;
import com.embedize.command.ResourceResetCommand;
import com.embedize.compat.LuckPermsHook;
import com.embedize.compat.MultiverseHook;
import com.embedize.compat.PlaceholderApiHook;
import com.embedize.reset.ResourceWorldResetListener;
import com.embedize.reset.ResourceWorldResetService;
import com.embedize.structure.BundledDatapackSync;
import com.embedize.structure.StructureAirPolicy;
import com.embedize.structure.StructureAirSkipListener;
import com.embedize.structure.StructureCatalog;
import com.embedize.structure.StructureWorldGate;
import com.embedize.structure.StructureWorldGateListener;
import com.embedize.terrain.TerrainGeneratorFactory;
import com.embedize.terrain.TerrainWorldListener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Embedize — Java terrain + vanilla structure engine via bundled datapack (GPLv3).
 */
public final class EmbedizePlugin extends JavaPlugin {

    private BorderManager borderManager;
    private LuckPermsHook luckPermsHook;
    private MultiverseHook multiverseHook;
    private PlaceholderApiHook placeholderApiHook;
    private StructureCatalog structureCatalog;
    private BundledDatapackSync bundledDatapackSync;
    private TerrainWorldListener terrainWorldListener;
    private ResourceWorldResetService resourceWorldResetService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        StructureAirPolicy.reload(getConfig());
        com.embedize.structure.nms.StructurePlacementBridge.reloadDebug(getConfig());

        this.luckPermsHook = new LuckPermsHook(this);
        this.luckPermsHook.registerBasePermissions();

        this.borderManager = new BorderManager(this);
        this.borderManager.load();

        this.multiverseHook = new MultiverseHook(this);
        this.structureCatalog = new StructureCatalog(this);

        // Before Multiverse (load: AFTER) brings Embedize worlds online: enable
        // bundled packs when config/MV expects them. Default world already loaded
        // with packs lazy-disabled at discovery when possible.
        this.bundledDatapackSync = new BundledDatapackSync(this);
        getServer().getPluginManager().registerEvents(bundledDatapackSync, this);
        bundledDatapackSync.syncNow("onEnable");

        this.resourceWorldResetService = new ResourceWorldResetService(this);
        this.resourceWorldResetService.start();
        this.placeholderApiHook = new PlaceholderApiHook(this);

        getServer().getPluginManager().registerEvents(new BorderListener(this), this);
        getServer().getPluginManager().registerEvents(
                new ResourceWorldResetListener(resourceWorldResetService), this);
        getServer().getPluginManager().registerEvents(new StructureAirSkipListener(this), this);
        getServer().getPluginManager().registerEvents(
                new StructureWorldGateListener(new StructureWorldGate(structureCatalog)), this);
        this.terrainWorldListener = new TerrainWorldListener(this);
        getServer().getPluginManager().registerEvents(terrainWorldListener, this);
        // Bind NMS placeInChunk bridge with plugin logger before attaching populators.
        com.embedize.structure.nms.StructurePlacementBridge.initialize(getLogger());
        terrainWorldListener.attachLoadedWorlds();

        registerCommand(
                "embedize",
                "Embedize admin commands",
                List.of("emb", "ez"),
                new EmbedizeCommand(this)
        );
        registerCommand(
                "resreset",
                "Manually reset the resource world",
                List.of(),
                new ResourceResetCommand(this, ResourceResetCommand.Mode.RESET)
        );
        registerCommand(
                "resresetstatus",
                "Show resource world reset status",
                List.of(),
                new ResourceResetCommand(this, ResourceResetCommand.Mode.STATUS)
        );
        registerCommand(
                "resresetunlock",
                "Force-clear the resource world reset lock",
                List.of(),
                new ResourceResetCommand(this, ResourceResetCommand.Mode.UNLOCK)
        );

        getLogger().info("Embedize " + getPluginMeta().getVersion()
                + " | gens=Embedize|Embedize:nether|Embedize:end"
                + " | structures=" + structureCatalog.structureCount()
                + " nbt≈" + structureCatalog.nbtCount()
                + " | structure-gate=catalog+custom-ns"
                + " | bundled-packs=" + (bundledDatapackSync.lastDesiredEnable() ? "enable" : "disable")
                + " | surface-ignore-air=" + StructureAirPolicy.get().surfaceIgnoreAirEnabled()
                + " | density-adapt=" + StructureAirPolicy.get().densityAdaptEnabled()
                + " | soft-beard=" + StructureAirPolicy.get().softBeardEnabled()
                + " | vanilla starts + placeInChunk bridge | GPLv3");
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return TerrainGeneratorFactory.create(worldName, id);
    }

    @Override
    public void onDisable() {
        if (resourceWorldResetService != null) {
            resourceWorldResetService.shutdown();
        }
        if (terrainWorldListener != null) {
            terrainWorldListener.detachAll();
        }
        if (borderManager != null) {
            borderManager.shutdown();
            borderManager.save();
        }
        if (placeholderApiHook != null) {
            placeholderApiHook.shutdown();
        }
        if (multiverseHook != null) {
            multiverseHook.shutdown();
        }
        getLogger().info("Embedize disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        StructureAirPolicy.reload(getConfig());
        com.embedize.structure.nms.StructurePlacementBridge.reloadDebug(getConfig());
        borderManager.load();
        if (bundledDatapackSync != null) {
            bundledDatapackSync.syncNow("reload");
        }
        if (resourceWorldResetService != null) {
            resourceWorldResetService.reload();
        }
    }

    public BorderManager getBorderManager() {
        return borderManager;
    }

    public StructureCatalog getStructureCatalog() {
        return structureCatalog;
    }

    public BundledDatapackSync getBundledDatapackSync() {
        return bundledDatapackSync;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public MultiverseHook getMultiverseHook() {
        return multiverseHook;
    }

    public TerrainWorldListener getTerrainWorldListener() {
        return terrainWorldListener;
    }

    public ResourceWorldResetService getResourceWorldResetService() {
        return resourceWorldResetService;
    }

    public PlaceholderApiHook getPlaceholderApiHook() {
        return placeholderApiHook;
    }
}
