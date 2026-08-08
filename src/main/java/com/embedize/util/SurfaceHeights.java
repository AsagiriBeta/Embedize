package com.embedize.util;

import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;

/**
 * Heightmap helpers that ignore canopy (leaves/logs) so structures sit on dirt/stone.
 * Prefer {@link LimitedRegion} / loaded-chunk APIs — never force sync chunk loads on the server thread.
 */
public final class SurfaceHeights {

    private static final int[][] FOOTING_OFFSETS = {
            {0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}
    };

    /** Tri-state footing so callers can defer instead of sync-loading. */
    public enum Footing {
        STABLE,
        UNSTABLE,
        /** Required columns are not loaded yet — retry later. */
        NOT_READY
    }

    private SurfaceHeights() {
    }

    /**
     * Top solid ground block Y at column, walking down through foliage and tree trunks.
     * Only reads loaded chunks — never sync-loads. If unloaded, returns {@code minHeight + 1};
     * prefer {@link #groundYIfLoaded} on placement paths so you can defer instead.
     */
    public static int groundY(@NotNull World world, int x, int z) {
        return groundYIfLoaded(world, x, z).orElse(world.getMinHeight() + 1);
    }

    /**
     * Same as {@link #groundY(World, int, int)} but returns empty if the column's chunk is unloaded
     * (does not trigger {@code syncLoad}).
     */
    public static OptionalInt groundYIfLoaded(@NotNull World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(groundYInLoadedChunk(world, x, z));
    }

    /** Height walk using generation-time region data (no World chunk loads). */
    public static int groundY(@NotNull LimitedRegion region, @NotNull WorldInfo info, int x, int z) {
        int y = highestY(region, x, z);
        int minY = info.getMinHeight() + 1;
        for (int guard = 0; guard < 64 && y > minY; guard++) {
            if (!region.isInRegion(x, y, z)) {
                break;
            }
            Material type = region.getType(x, y, z);
            if (isCanopyOrPassThrough(type)) {
                y--;
                continue;
            }
            return y;
        }
        return Math.max(minY, y);
    }

    /**
     * Reject riverbeds, flooded columns, and canyon shelves so surface structures
     * don't float on a 1-block dirt pad over a gorge.
     */
    public static boolean isStableFooting(@NotNull World world, int x, int z) {
        Footing f = footingIfLoaded(world, x, z);
        if (f == Footing.NOT_READY) {
            // Legacy callers: only evaluate what is loaded; treat unknown neighbors as unstable
            // rather than sync-loading (quality: skip questionable shelves instead of freezing TPS).
            return false;
        }
        return f == Footing.STABLE;
    }

    public static Footing footingIfLoaded(@NotNull World world, int x, int z) {
        if (!columnsLoaded(world, x, z)) {
            return Footing.NOT_READY;
        }
        OptionalInt y0Opt = groundYIfLoaded(world, x, z);
        if (y0Opt.isEmpty()) {
            return Footing.NOT_READY;
        }
        int y0 = y0Opt.getAsInt();
        if (!isSolidDryColumnLoaded(world, x, y0, z)) {
            return Footing.UNSTABLE;
        }
        int minY = y0;
        int maxY = y0;
        for (int[] off : FOOTING_OFFSETS) {
            int ox = x + off[0];
            int oz = z + off[1];
            OptionalInt yOpt = groundYIfLoaded(world, ox, oz);
            if (yOpt.isEmpty()) {
                return Footing.NOT_READY;
            }
            int y = yOpt.getAsInt();
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            if (!isSolidDryColumnLoaded(world, ox, y, oz)) {
                return Footing.UNSTABLE;
            }
        }
        return maxY - minY <= 5 ? Footing.STABLE : Footing.UNSTABLE;
    }

    public static boolean isStableFooting(
            @NotNull LimitedRegion region,
            @NotNull WorldInfo info,
            int x,
            int z
    ) {
        int y0 = groundY(region, info, x, z);
        if (!isSolidDryColumn(region, x, y0, z)) {
            return false;
        }
        int minY = y0;
        int maxY = y0;
        for (int[] off : FOOTING_OFFSETS) {
            int ox = x + off[0];
            int oz = z + off[1];
            if (!region.isInRegion(ox, y0, oz)) {
                return false;
            }
            int y = groundY(region, info, ox, oz);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            if (!isSolidDryColumn(region, ox, y, oz)) {
                return false;
            }
        }
        return maxY - minY <= 5;
    }

    public static boolean columnsLoaded(@NotNull World world, int x, int z) {
        for (int[] off : FOOTING_OFFSETS) {
            if (!world.isChunkLoaded((x + off[0]) >> 4, (z + off[1]) >> 4)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isRiverBiomeKey(String biomeKey) {
        if (biomeKey == null || biomeKey.isBlank()) {
            return false;
        }
        String k = biomeKey.toLowerCase();
        return k.endsWith(":river")
                || k.endsWith(":frozen_river")
                || k.contains("river_corridor")
                || k.contains("frozen_riverbank");
    }

    public static boolean isRiverBiome(@NotNull Biome biome) {
        try {
            return isRiverBiomeKey(biome.getKey().toString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isCanopyOrPassThrough(@NotNull Material type) {
        if (type.isAir() || !type.isSolid()) {
            return true;
        }
        if (Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type)) {
            return true;
        }
        if (Tag.REPLACEABLE.isTagged(type)) {
            return true;
        }
        String name = type.name();
        return name.endsWith("_WOOD")
                || name.endsWith("_SAPLING")
                || name.equals("MANGROVE_ROOTS")
                || name.equals("MUDDY_MANGROVE_ROOTS")
                || name.equals("BAMBOO")
                || name.equals("CACTUS")
                || name.equals("MOSS_CARPET")
                || name.equals("SNOW")
                || name.contains("VINE")
                || name.endsWith("_FENCE")
                || name.endsWith("_WALL")
                || name.equals("LADDER")
                || name.equals("SCAFFOLDING")
                || name.equals("COBWEB");
    }

    private static int groundYInLoadedChunk(@NotNull World world, int x, int z) {
        int y = highestYLoaded(world, x, z);
        int minY = world.getMinHeight() + 1;
        Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
        for (int guard = 0; guard < 64 && y > minY; guard++) {
            Material type = chunk.getBlock(x & 15, y, z & 15).getType();
            if (isCanopyOrPassThrough(type)) {
                y--;
                continue;
            }
            return y;
        }
        return Math.max(minY, y);
    }

    private static int highestYLoaded(@NotNull World world, int x, int z) {
        try {
            return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        } catch (Throwable ignored) {
            return world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        }
    }

    private static int highestY(@NotNull LimitedRegion region, int x, int z) {
        try {
            return region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        } catch (Throwable ignored) {
            return region.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        }
    }

    private static boolean isSolidDryColumnLoaded(@NotNull World world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
        Material ground = chunk.getBlock(x & 15, y, z & 15).getType();
        if (!ground.isSolid() || isCanopyOrPassThrough(ground)) {
            return false;
        }
        int aboveY = y + 1;
        if (aboveY >= world.getMaxHeight()) {
            return true;
        }
        Material above = chunk.getBlock(x & 15, aboveY, z & 15).getType();
        return above != Material.WATER
                && above != Material.LAVA
                && above != Material.BUBBLE_COLUMN;
    }

    private static boolean isSolidDryColumn(@NotNull LimitedRegion region, int x, int y, int z) {
        if (!region.isInRegion(x, y, z)) {
            return false;
        }
        Material ground = region.getType(x, y, z);
        if (!ground.isSolid() || isCanopyOrPassThrough(ground)) {
            return false;
        }
        if (!region.isInRegion(x, y + 1, z)) {
            return true;
        }
        Material above = region.getType(x, y + 1, z);
        return above != Material.WATER
                && above != Material.LAVA
                && above != Material.BUBBLE_COLUMN;
    }

    /** @return true when every chunk covering [minX..maxX] x [minZ..maxZ] is loaded */
    public static boolean areaChunksLoaded(@NotNull World world, int minX, int maxX, int minZ, int maxZ) {
        int minCx = Math.floorDiv(minX, 16);
        int maxCx = Math.floorDiv(maxX, 16);
        int minCz = Math.floorDiv(minZ, 16);
        int maxCz = Math.floorDiv(maxZ, 16);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static OptionalInt highestOceanFloorIfLoaded(@NotNull World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR_WG));
        } catch (Throwable ignored) {
            try {
                return OptionalInt.of(world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR));
            } catch (Throwable ignored2) {
                return groundYIfLoaded(world, x, z);
            }
        }
    }

    @Nullable
    public static Material typeIfLoaded(@NotNull World world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        return world.getChunkAt(x >> 4, z >> 4).getBlock(x & 15, y, z & 15).getType();
    }
}
