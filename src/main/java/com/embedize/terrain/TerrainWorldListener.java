package com.embedize.terrain;

import com.embedize.EmbedizePlugin;
import com.embedize.structure.nms.StructurePlacementBridge;
import com.embedize.structure.nms.StructurePlacementPopulator;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;

/**
 * Attaches Embedize terrain + structure-placement populators.
 * <p>
 * Structure <em>starts</em> come from {@code shouldGenerateStructures()} + datapacks;
 * piece placement is forced via {@link StructurePlacementPopulator} (TFG-style
 * {@code placeInChunk}), because Paper's CustomChunkGenerator path alone has left
 * ghost starts on fully custom terrain. Non-Embedize worlds never get these
 * populators; bundled-structure natural spawn there is cancelled by
 * {@code StructureWorldGateListener}.
 * <p>
 * Order matters: structure placement runs before foliage decoration so village
 * {@code terrain_matching} roads prefer bare ground; the bridge also restores
 * {@code WORLD_SURFACE_WG} from {@code getBaseHeight} when trees already exist.
 */
public final class TerrainWorldListener implements Listener {

    private final EmbedizePlugin plugin;

    public TerrainWorldListener(EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        attach(event.getWorld());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        attach(event.getWorld());
    }

    public void attachLoadedWorlds() {
        for (World world : plugin.getServer().getWorlds()) {
            attach(world);
        }
    }

    public void refreshWorld(World world) {
        if (world != null) {
            attach(world);
        }
    }

    public void detachAll() {
        for (World world : plugin.getServer().getWorlds()) {
            world.getPopulators().removeIf(p ->
                    p instanceof TerrainDecorationPopulator
                            || p instanceof StructurePlacementPopulator);
        }
    }

    private void attach(World world) {
        ChunkGenerator gen = world.getGenerator();
        if (!(gen instanceof EmbedizeGenerator)) {
            return;
        }
        StructurePlacementBridge.ensureWorld(world, plugin.getLogger());
        // Drop obsolete + Embedize populators, then re-attach in correct order.
        world.getPopulators().removeIf(p ->
                p.getClass().getSimpleName().equals("TerrainSealPopulator")
                        || p.getClass().getSimpleName().equals("NativeStructurePopulator")
                        || p instanceof TerrainDecorationPopulator
                        || p instanceof StructurePlacementPopulator);

        world.getPopulators().add(new StructurePlacementPopulator(plugin));
        plugin.getLogger().info("Structure placeInChunk bridge on '" + world.getName()
                + "' (available=" + StructurePlacementBridge.available() + ")");

        if (gen instanceof OverworldTerrainGenerator) {
            world.getPopulators().add(new TerrainDecorationPopulator());
            plugin.getLogger().info("Terrain decoration on '" + world.getName() + "'");
        }
    }
}
