package com.embedize.terrain;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * End terrain — Nullscape-inspired: central island, mid ring, sparse far highlands.
 */
public final class EndTerrainGenerator extends ChunkGenerator implements EmbedizeGenerator {

    private final String worldName;
    private final String generatorId;

    private volatile long boundSeed = Long.MIN_VALUE;
    private SimplexOctaveGenerator island;
    private SimplexOctaveGenerator detail;
    private SimplexOctaveGenerator height;
    private SimplexOctaveGenerator ring;
    private SimplexOctaveGenerator pillars;
    private EndBiomeProvider biomeProvider;

    public EndTerrainGenerator(String worldName, @Nullable String id) {
        this.worldName = worldName == null ? "world_the_end" : worldName;
        this.generatorId = id == null || id.isBlank() ? "end" : id;
    }

    private synchronized void bindSeed(long seed) {
        if (boundSeed == seed && island != null) {
            return;
        }
        boundSeed = seed;
        island = new SimplexOctaveGenerator(seed, 5);
        island.setScale(1.0 / 110.0);
        detail = new SimplexOctaveGenerator(seed + 41L, 3);
        detail.setScale(1.0 / 28.0);
        height = new SimplexOctaveGenerator(seed + 73L, 3);
        height.setScale(1.0 / 56.0);
        ring = new SimplexOctaveGenerator(seed + 101L, 2);
        ring.setScale(1.0 / 220.0);
        pillars = new SimplexOctaveGenerator(seed + 151L, 2);
        pillars.setScale(1.0 / 18.0);
        biomeProvider = new EndBiomeProvider(seed);
    }

    private void ensure(@NotNull WorldInfo worldInfo) {
        bindSeed(worldInfo.getSeed());
    }

    @Override
    public @Nullable BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        ensure(worldInfo);
        return biomeProvider;
    }

    @Override
    public void generateNoise(
            @NotNull WorldInfo worldInfo,
            @NotNull Random random,
            int chunkX,
            int chunkZ,
            @NotNull ChunkData chunkData
    ) {
        ensure(worldInfo);
        int minY = worldInfo.getMinHeight();
        int maxY = Math.min(worldInfo.getMaxHeight(), 256);

        for (int lx = 0; lx < 16; lx++) {
            int x = (chunkX << 4) + lx;
            for (int lz = 0; lz < 16; lz++) {
                int z = (chunkZ << 4) + lz;
                EndShape shape = shapeAt(x, z, minY, maxY);

                if (!shape.solid()) {
                    // Tiny floating debris (Nullscape dust)
                    if (shape.distance() > 60 && shape.islandNoise() > 0.72 && random.nextInt(40) == 0) {
                        int y = 55 + (int) (shape.detailNoise() * 20);
                        if (y > minY && y < maxY) {
                            chunkData.setBlock(lx, y, lz, Material.END_STONE);
                        }
                    }
                    continue;
                }

                for (int y = shape.bottom(); y <= shape.top(); y++) {
                    Material mat = Material.END_STONE;
                    // Occasional end stone brick “ruin” crust on highlands
                    if (!shape.central() && y == shape.top()
                            && shape.islandNoise() > 0.55 && random.nextInt(8) == 0) {
                        mat = Material.END_STONE_BRICKS;
                    }
                    chunkData.setBlock(lx, y, lz, mat);
                }

                // Obsidian pillars near center (vanilla-like cues)
                if (shape.central() && shape.distance() > 20 && shape.distance() < 45
                        && shape.pillarNoise() > 0.62) {
                    for (int y = shape.top() + 1;
                         y < Math.min(maxY - 1, shape.top() + 25); y++) {
                        chunkData.setBlock(lx, y, lz, Material.OBSIDIAN);
                    }
                }

                if (!shape.central() && shape.top() + 1 < maxY) {
                    if (random.nextInt(shape.midRing() ? 28 : 18) == 0) {
                        chunkData.setBlock(lx, shape.top() + 1, lz, Material.CHORUS_FLOWER);
                    } else if (random.nextInt(22) == 0) {
                        chunkData.setBlock(lx, shape.top() + 1, lz, Material.CHORUS_PLANT);
                    }
                }
            }
        }
    }

    @Override
    public int getBaseHeight(
            @NotNull WorldInfo worldInfo,
            @NotNull Random random,
            int x,
            int z,
            @NotNull HeightMap heightMap
    ) {
        ensure(worldInfo);
        EndShape shape = shapeAt(x, z, worldInfo.getMinHeight(), Math.min(worldInfo.getMaxHeight(), 256));
        return shape.solid() ? shape.top() : worldInfo.getMinHeight();
    }

    /**
     * Nullscape-inspired: hub island + six concentric rings with sync/offset noise.
     * Ring radii (blocks): ~180, 420, 720, 1100, 1600, 2300.
     */
    private EndShape shapeAt(int x, int z, int minY, int maxY) {
        double dist = Math.hypot(x, z);
        double noise = island.noise(x, z, 0.5, 0.5, true);
        double detailNoise = detail.noise(x, z, 0.5, 0.5, true);
        double ringNoise = ring.noise(x, z, 0.5, 0.5, true);
        double pillarNoise = pillars.noise(x, z, 0.5, 0.5, true);
        double sync = height.noise(x * 0.35, z * 0.35, 0.5, 0.5, true);

        double centralMask = 1.0 - smoothstep(42.0, 74.0, dist);
        double centralDensity = centralMask + noise * 0.08 - 0.10;
        boolean central = centralDensity > 0.0;

        // Six Nullscape-like rings: [inner, outer, thickness factor]
        double[][] rings = {
                {140, 260, 1.0},
                {340, 500, 0.95},
                {620, 820, 0.90},
                {980, 1220, 0.85},
                {1450, 1750, 0.80},
                {2100, 2500, 0.75}
        };
        int bestRing = -1;
        double bestDensity = -1.0;
        for (int i = 0; i < rings.length; i++) {
            double inner = rings[i][0];
            double outer = rings[i][1];
            double window = smoothstep(inner, inner + 40.0, dist)
                    * (1.0 - smoothstep(outer - 40.0, outer, dist));
            if (window <= 0.0) {
                continue;
            }
            // ring sync/offset: angular + radial modulation
            double offset = Math.abs(ringNoise) * 0.12 + Math.abs(sync) * 0.08;
            double density = (noise - lerp(0.70, 0.30, window) - offset) * rings[i][2] * window;
            if (density > bestDensity) {
                bestDensity = density;
                bestRing = i;
            }
        }

        boolean midRing = !central && bestRing >= 0 && bestRing <= 2 && bestDensity > 0.0;
        boolean farIsle = !central && bestRing >= 3 && bestDensity > 0.0;
        // Sparse void debris beyond last ring
        if (!central && bestDensity <= 0.0 && dist > 2600) {
            double dust = noise - 0.82 - Math.min(0.15, (dist - 2600) / 40000.0);
            if (dust > 0.0) {
                farIsle = true;
                bestDensity = dust;
                bestRing = 5;
            }
        }
        if (!central && !midRing && !farIsle) {
            return new EndShape(false, false, false, minY, minY,
                    dist, noise, detailNoise, pillarNoise);
        }

        int top;
        int bottom;
        if (central) {
            int base = 58;
            top = base + 10 + (int) Math.round(detailNoise * 6.0);
            bottom = base - 18;
        } else if (midRing) {
            int base = 48 + (int) Math.round(sync * 28.0);
            double thickness = 6.0 + Math.max(0.0, bestDensity) * 42.0;
            top = base + (int) Math.round(thickness);
            bottom = base - 8 - (int) Math.round(Math.abs(detailNoise) * 10.0);
            // Crystal-peak cue: taller mid rings
            if (bestRing == 1 && noise > 0.35) {
                top += 8;
            }
        } else {
            int base = 52 + (int) Math.round(sync * 22.0);
            top = base + 5 + (int) Math.round(Math.max(0.0, bestDensity) * 56.0);
            bottom = base - 12;
            // Shadowlands: lower, thinner far rings
            if (bestRing >= 4) {
                top -= 6;
                bottom += 2;
            }
        }
        top = Math.min(maxY - 2, Math.max(minY + 2, top));
        bottom = Math.max(minY + 1, Math.min(top, bottom));
        return new EndShape(true, central, midRing, bottom, top,
                dist, noise, detailNoise, pillarNoise);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private record EndShape(
            boolean solid,
            boolean central,
            boolean midRing,
            int bottom,
            int top,
            double distance,
            double islandNoise,
            double detailNoise,
            double pillarNoise
    ) {
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return true;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        // Bundled structure datapack + vanilla JigsawPlacement / StructureSet pipeline.
        return true;
    }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0.5, 100.0, 0.5);
    }

    public String getWorldName() {
        return worldName;
    }

    public @Nullable String getGeneratorId() {
        return generatorId;
    }

    @Override
    public TerrainGeneratorFactory.@NotNull Kind kind() {
        return TerrainGeneratorFactory.Kind.END;
    }

    @Override
    public @NotNull String generatorId() {
        return generatorId;
    }

    static final class EndBiomeProvider extends BiomeProvider {
        private final SimplexOctaveGenerator climate;
        private List<Biome> cachedBiomes;

        EndBiomeProvider(long seed) {
            climate = new SimplexOctaveGenerator(seed + 99L, 3);
            climate.setScale(1.0 / 200.0);
        }

        @Override
        public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
            double dist = Math.hypot(x, z);
            if (dist < 52.0) {
                return Biome.THE_END;
            }
            if (dist < 180.0) {
                return Biome.SMALL_END_ISLANDS;
            }
            double c = climate.noise(x, z, 0.5, 0.5, true);
            if (c > 0.28) {
                return Biome.END_HIGHLANDS;
            }
            if (c < -0.28) {
                return Biome.END_BARRENS;
            }
            return Biome.END_MIDLANDS;
        }

        @Override
        public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
            if (cachedBiomes == null) {
                cachedBiomes = List.of(
                        Biome.THE_END, Biome.END_HIGHLANDS, Biome.END_MIDLANDS,
                        Biome.END_BARRENS, Biome.SMALL_END_ISLANDS);
            }
            return cachedBiomes;
        }
    }
}
