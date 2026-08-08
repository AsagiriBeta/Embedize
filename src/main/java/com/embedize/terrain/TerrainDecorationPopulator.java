package com.embedize.terrain;

import com.embedize.terrain.biome.EmbedizeBiomes;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Deterministic micro decoration for Embedize overworld biomes.
 * Uses only plugin code and vanilla blocks; no runtime datapack features are required.
 */
public final class TerrainDecorationPopulator extends BlockPopulator {

    private static final Material[] PLAINS_FLOWERS = {
            Material.DANDELION, Material.POPPY, Material.OXEYE_DAISY,
            Material.CORNFLOWER, Material.AZURE_BLUET
    };
    private static final Material[] MEADOW_FLOWERS = {
            Material.ALLIUM, Material.LILAC, Material.PEONY,
            Material.ROSE_BUSH, Material.SUNFLOWER, Material.OXEYE_DAISY
    };
    private static final Material[] FOREST_FLOWERS = {
            Material.POPPY, Material.DANDELION, Material.LILY_OF_THE_VALLEY
    };

    @Override
    public void populate(
            @NotNull WorldInfo worldInfo,
            @NotNull Random ignored,
            int chunkX,
            int chunkZ,
            @NotNull LimitedRegion region
    ) {
        if (worldInfo.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        Random random = new Random(mix(worldInfo.getSeed(), chunkX, chunkZ, 0x6A09E667F3BCC909L));
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        Biome centerBiome = region.getBiome(
                baseX + 8,
                Math.max(worldInfo.getMinHeight(), OverworldNoiseModel.SEA_LEVEL),
                baseZ + 8
        );

        // TFG-style structure buffer: thin large vegetation when this chunk may host a start
        boolean structureBuffer = (Math.floorMod(chunkX * 73428767L + chunkZ * 91243657L
                + worldInfo.getSeed(), 17) == 0);
        int trees = treeAttempts(centerBiome, random);
        if (structureBuffer) {
            trees = Math.max(0, trees / 3);
        }
        for (int i = 0; i < trees; i++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Biome biome = region.getBiome(x, Math.max(worldInfo.getMinHeight(), y), z);
            placeTree(region, x, y + 1, z, biome, random);
        }

        decorateSurfacePatches(region, worldInfo, baseX, baseZ, centerBiome, random);
        decorateGroundCover(region, worldInfo, baseX, baseZ, centerBiome, random);
        if (EmbedizeBiomes.isAbyssalDeep(centerBiome)) {
            decorateAbyssalTrench(region, worldInfo, baseX, baseZ, random);
        }

        if (random.nextInt(12) == 0) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            placeRock(region, x, y + 1, z, random);
        }
        if (trees > 0 && random.nextInt(18) == 0) {
            int x = baseX + 2 + random.nextInt(12);
            int z = baseZ + 2 + random.nextInt(12);
            int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            placeFallenLog(region, x, y + 1, z, centerBiome, random);
        }
    }

    /** Geophilic-inspired dirt/podzol/mud/moss patches before grass layer. */
    private static void decorateSurfacePatches(
            LimitedRegion region,
            WorldInfo worldInfo,
            int baseX,
            int baseZ,
            Biome centerBiome,
            Random random
    ) {
        int tries = 0;
        Material patch = null;
        if (EmbedizeBiomes.isLikeAny(centerBiome, Biome.FOREST, Biome.BIRCH_FOREST, Biome.DARK_FOREST)) {
            tries = 6;
            patch = random.nextBoolean() ? Material.PODZOL : Material.COARSE_DIRT;
        } else if (EmbedizeBiomes.isLikeAny(centerBiome, Biome.TAIGA, Biome.SNOWY_TAIGA, Biome.GROVE)) {
            tries = 8;
            patch = Material.PODZOL;
        } else if (EmbedizeBiomes.isLikeAny(centerBiome, Biome.SWAMP, Biome.MANGROVE_SWAMP)) {
            tries = 10;
            patch = Material.MUD;
        } else if (EmbedizeBiomes.isLikeAny(centerBiome, Biome.CHERRY_GROVE, Biome.MEADOW)) {
            tries = 4;
            patch = Material.MOSS_BLOCK;
        } else if (EmbedizeBiomes.isLikeAny(centerBiome, Biome.JUNGLE, Biome.BAMBOO_JUNGLE)) {
            tries = 5;
            patch = Material.PODZOL;
        }
        for (int i = 0; i < tries; i++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Material ground = type(region, x, y, z);
            if (ground == Material.GRASS_BLOCK || ground == Material.DIRT) {
                if (region.isInRegion(x, y, z) && patch != null) {
                    region.setType(x, y, z, patch);
                }
            }
            if (EmbedizeBiomes.isLikeAny(centerBiome, Biome.FOREST, Biome.TAIGA, Biome.DARK_FOREST)
                    && random.nextInt(4) == 0
                    && isAir(region, x, y + 1, z)
                    && isPlantable(type(region, x, y, z))) {
                setIfReplaceable(region, x, y + 1, z, Material.MOSS_CARPET);
            }
        }
        // Bushes (Geophilic cherry/taiga)
        if (EmbedizeBiomes.isLikeAny(centerBiome, Biome.CHERRY_GROVE, Biome.TAIGA, Biome.FLOWER_FOREST)) {
            int bushes = 1 + random.nextInt(3);
            for (int i = 0; i < bushes; i++) {
                int x = baseX + random.nextInt(16);
                int z = baseZ + random.nextInt(16);
                int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (!isPlantable(type(region, x, y, z)) || !isAir(region, x, y + 1, z)) {
                    continue;
                }
                Material leaf = EmbedizeBiomes.isLike(centerBiome, Biome.CHERRY_GROVE)
                        ? Material.CHERRY_LEAVES : Material.OAK_LEAVES;
                setIfReplaceable(region, x, y + 1, z, leaf);
                if (random.nextBoolean()) {
                    setIfReplaceable(region, x + 1, y + 1, z, leaf);
                }
            }
        }
    }

    private static void decorateGroundCover(
            LimitedRegion region,
            WorldInfo worldInfo,
            int baseX,
            int baseZ,
            Biome centerBiome,
            Random random
    ) {
        int grassTries = grassAttempts(centerBiome);
        for (int i = 0; i < grassTries; i++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Biome biome = region.getBiome(x, Math.max(worldInfo.getMinHeight(), y), z);
            placePatch(region, x, y + 1, z, biome, random);
        }

        int flowerTries = flowerAttempts(centerBiome);
        for (int i = 0; i < flowerTries; i++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Biome biome = region.getBiome(x, Math.max(worldInfo.getMinHeight(), y), z);
            placeFlower(region, x, y + 1, z, biome, random);
        }

        if (EmbedizeBiomes.isLikeAny(centerBiome, Biome.DESERT)) {
            int cactusTries = 1 + random.nextInt(3);
            for (int i = 0; i < cactusTries; i++) {
                int x = baseX + random.nextInt(16);
                int z = baseZ + random.nextInt(16);
                int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                placeCactus(region, x, y + 1, z, random);
            }
            if (random.nextInt(4) == 0) {
                int x = baseX + random.nextInt(16);
                int z = baseZ + random.nextInt(16);
                int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                placeDeadBush(region, x, y + 1, z);
            }
        }

        if (EmbedizeBiomes.isLikeAny(centerBiome, Biome.BADLANDS)) {
            if (random.nextInt(3) == 0) {
                int x = baseX + random.nextInt(16);
                int z = baseZ + random.nextInt(16);
                int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                placeDeadBush(region, x, y + 1, z);
            }
        }

        if (EmbedizeBiomes.isLikeAny(centerBiome, Biome.MANGROVE_SWAMP, Biome.SWAMP)) {
            int reedTries = 2 + random.nextInt(4);
            for (int i = 0; i < reedTries; i++) {
                int x = baseX + random.nextInt(16);
                int z = baseZ + random.nextInt(16);
                int y = region.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
                placeSugarCane(region, x, y + 1, z, random);
            }
            if (random.nextInt(5) == 0) {
                int x = baseX + random.nextInt(16);
                int z = baseZ + random.nextInt(16);
                int y = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                setIfReplaceable(region, x, y + 1, z, Material.LILY_PAD);
            }
        }

        // Beaches stay plain sand — no calcite/amethyst gimmicks.
    }

    /**
     * Far-ocean trench accents inspired by Alex's Caves Abyssal Chasm
     * (dead coral, pickles, short prismarine pillars — vanilla blocks only).
     */
    private static void decorateAbyssalTrench(
            LimitedRegion region,
            WorldInfo worldInfo,
            int baseX,
            int baseZ,
            Random random
    ) {
        int sea = OverworldNoiseModel.SEA_LEVEL;
        int tries = 5 + random.nextInt(5);
        for (int i = 0; i < tries; i++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = region.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
            if (y >= sea - 8 || y <= worldInfo.getMinHeight() + 2) {
                continue;
            }
            Material ground = type(region, x, y, z);
            if (!ground.isSolid()) {
                continue;
            }
            int roll = random.nextInt(10);
            if (roll < 3) {
                setIfWaterOrReplaceable(region, x, y + 1, z, Material.DEAD_BRAIN_CORAL_FAN);
            } else if (roll < 5) {
                setIfWaterOrReplaceable(region, x, y + 1, z, Material.DEAD_FIRE_CORAL);
            } else if (roll < 7) {
                setIfWaterOrReplaceable(region, x, y + 1, z, Material.SEA_PICKLE);
            } else if (roll == 7) {
                int h = 2 + random.nextInt(4);
                for (int dy = 1; dy <= h && y + dy < sea - 2; dy++) {
                    setIfWaterOrReplaceable(region, x, y + dy, z,
                            dy == h ? Material.PRISMARINE : Material.DARK_PRISMARINE);
                }
            } else if (roll == 8 && ground != Material.MAGMA_BLOCK) {
                region.setType(x, y, z, Material.MAGMA_BLOCK);
            }
        }
    }

    private static void setIfWaterOrReplaceable(LimitedRegion region, int x, int y, int z, Material mat) {
        if (!region.isInRegion(x, y, z)) {
            return;
        }
        Material cur = region.getType(x, y, z);
        if (cur.isAir() || cur == Material.WATER || cur == Material.CAVE_AIR) {
            region.setType(x, y, z, mat);
        }
    }

    private static int grassAttempts(Biome biome) {
        if (EmbedizeBiomes.isLikeAny(biome, Biome.PLAINS, Biome.SUNFLOWER_PLAINS, Biome.MEADOW)) {
            return 28;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.FOREST, Biome.BIRCH_FOREST, Biome.FLOWER_FOREST,
                Biome.DARK_FOREST, Biome.CHERRY_GROVE)) {
            return 18;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.TAIGA, Biome.SNOWY_TAIGA, Biome.GROVE)) {
            return 12;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.SAVANNA, Biome.SAVANNA_PLATEAU,
                Biome.JUNGLE, Biome.BAMBOO_JUNGLE, Biome.SWAMP, Biome.MANGROVE_SWAMP)) {
            return 16;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_GRAVELLY_HILLS)) {
            return 8;
        }
        return 0;
    }

    private static int flowerAttempts(Biome biome) {
        if (EmbedizeBiomes.isLikeAny(biome, Biome.FLOWER_FOREST, Biome.MEADOW, Biome.SUNFLOWER_PLAINS)) {
            return 10;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.PLAINS, Biome.CHERRY_GROVE)) {
            return 5;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.FOREST, Biome.BIRCH_FOREST)) {
            return 3;
        }
        return 0;
    }

    private static void placePatch(
            LimitedRegion region,
            int x,
            int y,
            int z,
            Biome biome,
            Random random
    ) {
        Material ground = type(region, x, y - 1, z);
        if (!isPlantable(ground) || !isAir(region, x, y, z)) {
            return;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.SAVANNA, Biome.SAVANNA_PLATEAU)) {
            setIfReplaceable(region, x, y, z,
                    random.nextInt(5) == 0 ? Material.DEAD_BUSH : Material.SHORT_GRASS);
            return;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.TAIGA, Biome.SNOWY_TAIGA, Biome.GROVE)) {
            if (random.nextInt(6) == 0) {
                setIfReplaceable(region, x, y, z, Material.SWEET_BERRY_BUSH);
            } else {
                setIfReplaceable(region, x, y, z,
                        random.nextBoolean() ? Material.FERN : Material.SHORT_GRASS);
            }
            return;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.JUNGLE, Biome.BAMBOO_JUNGLE)) {
            Material plant = switch (random.nextInt(5)) {
                case 0 -> Material.FERN;
                case 1 -> Material.LARGE_FERN;
                case 2 -> Material.BAMBOO;
                default -> Material.SHORT_GRASS;
            };
            setIfReplaceable(region, x, y, z, plant);
            return;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.SWAMP, Biome.MANGROVE_SWAMP)) {
            setIfReplaceable(region, x, y, z,
                    random.nextInt(4) == 0 ? Material.FERN : Material.SHORT_GRASS);
            return;
        }
        if (random.nextInt(8) == 0) {
            setIfReplaceable(region, x, y, z, Material.TALL_GRASS);
        } else {
            setIfReplaceable(region, x, y, z, Material.SHORT_GRASS);
        }
    }

    private static void placeFlower(
            LimitedRegion region,
            int x,
            int y,
            int z,
            Biome biome,
            Random random
    ) {
        Material ground = type(region, x, y - 1, z);
        if (!isPlantable(ground) || !isAir(region, x, y, z)) {
            return;
        }
        Material[] palette;
        if (EmbedizeBiomes.isLikeAny(biome, Biome.MEADOW, Biome.SUNFLOWER_PLAINS, Biome.FLOWER_FOREST)) {
            palette = MEADOW_FLOWERS;
        } else if (EmbedizeBiomes.isLikeAny(biome, Biome.FOREST, Biome.BIRCH_FOREST, Biome.CHERRY_GROVE)) {
            palette = FOREST_FLOWERS;
        } else {
            palette = PLAINS_FLOWERS;
        }
        setIfReplaceable(region, x, y, z, palette[random.nextInt(palette.length)]);
    }

    private static void placeCactus(LimitedRegion region, int x, int y, int z, Random random) {
        Material ground = type(region, x, y - 1, z);
        if (ground != Material.SAND && ground != Material.RED_SAND) {
            return;
        }
        if (!isAir(region, x, y, z)) {
            return;
        }
        int height = 1 + random.nextInt(3);
        for (int dy = 0; dy < height; dy++) {
            if (!setIfReplaceable(region, x, y + dy, z, Material.CACTUS)) {
                return;
            }
        }
    }

    private static void placeDeadBush(LimitedRegion region, int x, int y, int z) {
        Material ground = type(region, x, y - 1, z);
        if ((ground == Material.SAND || ground == Material.RED_SAND
                || ground == Material.TERRACOTTA || ground == Material.ORANGE_TERRACOTTA
                || ground == Material.COARSE_DIRT)
                && isAir(region, x, y, z)) {
            setIfReplaceable(region, x, y, z, Material.DEAD_BUSH);
        }
    }

    private static void placeSugarCane(LimitedRegion region, int x, int y, int z, Random random) {
        Material ground = type(region, x, y - 1, z);
        if (!(ground == Material.MUD || ground == Material.GRASS_BLOCK || ground == Material.DIRT
                || ground == Material.SAND)) {
            return;
        }
        if (!adjacentWater(region, x, y - 1, z) || !isAir(region, x, y, z)) {
            return;
        }
        int height = 2 + random.nextInt(3);
        for (int dy = 0; dy < height; dy++) {
            if (!setIfReplaceable(region, x, y + dy, z, Material.SUGAR_CANE)) {
                return;
            }
        }
    }

    private static boolean adjacentWater(LimitedRegion region, int x, int y, int z) {
        return type(region, x + 1, y, z) == Material.WATER
                || type(region, x - 1, y, z) == Material.WATER
                || type(region, x, y, z + 1) == Material.WATER
                || type(region, x, y, z - 1) == Material.WATER;
    }

    private static int treeAttempts(Biome biome, Random random) {
        if (EmbedizeBiomes.isLikeAny(biome, Biome.JUNGLE, Biome.BAMBOO_JUNGLE)) {
            return 7 + random.nextInt(5);
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.DARK_FOREST, Biome.FOREST, Biome.FLOWER_FOREST,
                Biome.BIRCH_FOREST, Biome.CHERRY_GROVE)) {
            return 4 + random.nextInt(5);
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.TAIGA, Biome.SNOWY_TAIGA, Biome.GROVE)) {
            return 3 + random.nextInt(4);
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.SAVANNA, Biome.SAVANNA_PLATEAU,
                Biome.MANGROVE_SWAMP, Biome.SWAMP)) {
            return 2 + random.nextInt(3);
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.PLAINS, Biome.SUNFLOWER_PLAINS, Biome.MEADOW)) {
            return random.nextInt(3);
        }
        return 0;
    }

    private static void placeTree(
            LimitedRegion region,
            int x,
            int y,
            int z,
            Biome biome,
            Random random
    ) {
        Material ground = type(region, x, y - 1, z);
        if (!isPlantable(ground) || !isAir(region, x, y, z)) {
            return;
        }
        Material log = Material.OAK_LOG;
        Material leaves = Material.OAK_LEAVES;
        if (EmbedizeBiomes.isLikeAny(biome, Biome.TAIGA, Biome.SNOWY_TAIGA, Biome.GROVE)) {
            log = Material.SPRUCE_LOG;
            leaves = Material.SPRUCE_LEAVES;
        } else if (EmbedizeBiomes.isLikeAny(biome, Biome.BIRCH_FOREST)) {
            log = Material.BIRCH_LOG;
            leaves = Material.BIRCH_LEAVES;
        } else if (EmbedizeBiomes.isLikeAny(biome, Biome.CHERRY_GROVE)) {
            log = Material.CHERRY_LOG;
            leaves = Material.CHERRY_LEAVES;
        } else if (EmbedizeBiomes.isLikeAny(biome, Biome.JUNGLE, Biome.BAMBOO_JUNGLE)) {
            log = Material.JUNGLE_LOG;
            leaves = Material.JUNGLE_LEAVES;
        } else if (EmbedizeBiomes.isLikeAny(biome, Biome.SAVANNA, Biome.SAVANNA_PLATEAU)) {
            log = Material.ACACIA_LOG;
            leaves = Material.ACACIA_LEAVES;
        } else if (EmbedizeBiomes.isLikeAny(biome, Biome.MANGROVE_SWAMP)) {
            log = Material.MANGROVE_LOG;
            leaves = Material.MANGROVE_LEAVES;
        }

        int height = 4 + random.nextInt(4);
        for (int dy = 0; dy < height; dy++) {
            if (!setIfReplaceable(region, x, y + dy, z, log)) {
                return;
            }
        }
        int top = y + height;
        for (int dy = -2; dy <= 1; dy++) {
            int radius = dy == 1 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && random.nextBoolean()) {
                        continue;
                    }
                    setIfReplaceable(region, x + dx, top + dy, z + dz, leaves);
                }
            }
        }
    }

    private static void placeRock(LimitedRegion region, int x, int y, int z, Random random) {
        if (!isPlantable(type(region, x, y - 1, z))) {
            return;
        }
        Material material = random.nextInt(5) == 0 ? Material.ANDESITE : Material.STONE;
        setIfReplaceable(region, x, y, z, material);
        if (random.nextBoolean()) {
            setIfReplaceable(region, x + 1, y, z, material);
        }
        if (random.nextInt(3) == 0) {
            setIfReplaceable(region, x, y + 1, z, material);
        }
    }

    private static void placeFallenLog(
            LimitedRegion region,
            int x,
            int y,
            int z,
            Biome biome,
            Random random
    ) {
        Material log = EmbedizeBiomes.isLikeAny(biome, Biome.TAIGA, Biome.SNOWY_TAIGA)
                ? Material.SPRUCE_LOG : Material.OAK_LOG;
        boolean alongX = random.nextBoolean();
        int length = 4 + random.nextInt(3);
        for (int i = 0; i < length; i++) {
            int bx = x + (alongX ? i : 0);
            int bz = z + (alongX ? 0 : i);
            if (!isPlantable(type(region, bx, y - 1, bz))
                    || !setIfReplaceable(region, bx, y, bz, log)) {
                return;
            }
        }
    }

    private static boolean setIfReplaceable(
            LimitedRegion region,
            int x,
            int y,
            int z,
            Material material
    ) {
        if (!region.isInRegion(x, y, z)) {
            return false;
        }
        Material current = region.getType(x, y, z);
        if (!current.isAir() && (current.isSolid()
                || current == Material.WATER || current == Material.LAVA)) {
            return false;
        }
        region.setType(x, y, z, material);
        return true;
    }

    private static boolean isAir(LimitedRegion region, int x, int y, int z) {
        return region.isInRegion(x, y, z) && region.getType(x, y, z).isAir();
    }

    private static Material type(LimitedRegion region, int x, int y, int z) {
        return region.isInRegion(x, y, z) ? region.getType(x, y, z) : Material.AIR;
    }

    private static boolean isPlantable(Material material) {
        return material == Material.GRASS_BLOCK || material == Material.DIRT
                || material == Material.COARSE_DIRT || material == Material.PODZOL
                || material == Material.MUD || material == Material.ROOTED_DIRT
                || material == Material.MOSS_BLOCK || material == Material.SNOW_BLOCK;
    }

    private static long mix(long seed, int chunkX, int chunkZ, long salt) {
        long value = seed ^ salt ^ ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
