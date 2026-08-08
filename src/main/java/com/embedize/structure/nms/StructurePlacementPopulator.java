package com.embedize.structure.nms;

import com.embedize.EmbedizePlugin;
import com.embedize.terrain.ResetSpawnFastPath;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Runs during Bukkit decoration and force-calls {@code StructureStart#placeInChunk}
 * for any starts intersecting the chunk.
 * <p>
 * Attached <em>before</em> {@link com.embedize.terrain.TerrainDecorationPopulator} so
 * roads project onto bare ground when possible; the bridge also restores
 * {@code WORLD_SURFACE_WG} from {@code getBaseHeight} because vanilla decorations may
 * already have planted trees earlier in the same generation pass.
 */
public final class StructurePlacementPopulator extends BlockPopulator {

    private final EmbedizePlugin plugin;

    public StructurePlacementPopulator(EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void populate(
            @NotNull WorldInfo worldInfo,
            @NotNull Random random,
            int chunkX,
            int chunkZ,
            @NotNull LimitedRegion region
    ) {
        if (ResetSpawnFastPath.isSpawnChunk(chunkX, chunkZ)) {
            return;
        }
        if (worldInfo.getEnvironment() != World.Environment.NORMAL
                && worldInfo.getEnvironment() != World.Environment.NETHER
                && worldInfo.getEnvironment() != World.Environment.THE_END) {
            return;
        }
        StructurePlacementBridge.placeStartsInRegion(region, chunkX, chunkZ, plugin.getLogger());
    }
}
