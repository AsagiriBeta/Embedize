package com.embedize.terrain;

import com.embedize.terrain.biome.EmbedizeBiomes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * Self-developed nether terrain: {@code --generator Embedize:nether} / {@code Embedize:Embedize-Nether}.
 * Floor/ceiling + cheese mid-carves + lava seas; keeps StructureSet for fortresses/bastions.
 */
public final class NetherTerrainGenerator extends ChunkGenerator implements EmbedizeGenerator {

    private static final int DENSITY_STEP = 4;

    private final String worldName;
    private final String generatorId;

    private volatile long boundSeed = Long.MIN_VALUE;
    private NetherNoiseModel model;
    private NetherBiomeProvider biomeProvider;

    public NetherTerrainGenerator(String worldName, @Nullable String id) {
        this.worldName = worldName == null ? "world_nether" : worldName;
        this.generatorId = id == null || id.isBlank() ? "nether" : id;
    }

    private synchronized void bindSeed(long seed) {
        if (boundSeed == seed && model != null) {
            return;
        }
        boundSeed = seed;
        model = new NetherNoiseModel(seed);
        biomeProvider = new NetherBiomeProvider(model);
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
        int minY = Math.max(worldInfo.getMinHeight(), 0);
        int maxY = Math.min(worldInfo.getMaxHeight(), 192);
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        double[][][] cheeseGrid = sampleCheeseGrid(baseX, baseZ, minY, maxY);

        for (int lx = 0; lx < 16; lx++) {
            int x = baseX + lx;
            for (int lz = 0; lz < 16; lz++) {
                int z = baseZ + lz;
                NetherNoiseModel.Sample sample = model.sample(x, z);
                Biome biome = biomeProvider.getBiome(worldInfo, x, sample.floorY(), z);

                int floorY = sample.floorY();
                int ceilingY = sample.ceilingY();

                chunkData.setBlock(lx, minY, lz, Material.BEDROCK);
                if (maxY - 1 >= minY) {
                    chunkData.setBlock(lx, maxY - 1, lz, Material.BEDROCK);
                }

                Material filler = filler(biome);
                Material surface = surface(biome);

                for (int y = minY + 1; y <= floorY; y++) {
                    chunkData.setBlock(lx, y, lz, y == floorY ? surface : filler);
                }
                for (int y = ceilingY; y < maxY - 1; y++) {
                    chunkData.setBlock(lx, y, lz, filler);
                }

                // True 3D mid-volume density: positive cells become terrain, negative cells
                // remain caverns. The previous implementation carved an already-empty volume.
                for (int y = floorY + 1; y < ceilingY; y++) {
                    double cheese = interpolateCheese(cheeseGrid, lx, y - minY, lz);
                    boolean nearShell = y < floorY + 8 || y > ceilingY - 8;
                    double threshold = nearShell ? -0.22 : 0.18;
                    boolean pillarDensity = sample.pillar() && cheese > -0.16;
                    if (cheese > threshold || pillarDensity) {
                        Material densityMaterial = filler;
                        if (EmbedizeBiomes.isLike(biome, Biome.BASALT_DELTAS)) {
                            densityMaterial = cheese > 0.45 ? Material.BLACKSTONE : Material.BASALT;
                        } else if (EmbedizeBiomes.isLike(biome, Biome.SOUL_SAND_VALLEY)
                                && cheese > 0.38) {
                            densityMaterial = Material.SOUL_SOIL;
                        }
                        chunkData.setBlock(lx, y, lz, densityMaterial);
                    }
                    // Glowstone / shroomlight pockets near ceiling
                    if (y > ceilingY - 5 && chunkData.getType(lx, y, lz).isAir()
                            && cheese < -0.40 && random.nextInt(18) == 0) {
                        chunkData.setBlock(lx, y, lz,
                                EmbedizeBiomes.isLikeAny(biome, Biome.CRIMSON_FOREST, Biome.WARPED_FOREST)
                                        ? Material.SHROOMLIGHT : Material.GLOWSTONE);
                    }
                }

                if (sample.lavaSea() || floorY <= NetherNoiseModel.LAVA_LEVEL) {
                    int lavaTop = Math.max(floorY, NetherNoiseModel.LAVA_LEVEL);
                    for (int y = floorY + 1; y <= lavaTop && y < ceilingY; y++) {
                        chunkData.setBlock(lx, y, lz, Material.LAVA);
                    }
                } else if (sample.volcanic() && random.nextInt(12) == 0) {
                    // Magma vents
                    for (int y = floorY; y <= floorY + 2 && y < ceilingY; y++) {
                        chunkData.setBlock(lx, y, lz, Material.MAGMA_BLOCK);
                    }
                }

                // Crimson/warped “roots” hanging from ceiling
                if (EmbedizeBiomes.isLikeAny(biome, Biome.CRIMSON_FOREST, Biome.WARPED_FOREST)
                        && random.nextInt(10) == 0) {
                    Material vine = EmbedizeBiomes.isLike(biome, Biome.CRIMSON_FOREST)
                            ? Material.WEEPING_VINES : Material.TWISTING_VINES;
                    int len = 2 + random.nextInt(8);
                    for (int i = 1; i <= len && ceilingY - i > floorY; i++) {
                        int y = ceilingY - i;
                        if (!chunkData.getType(lx, y, lz).isAir()) {
                            break;
                        }
                        chunkData.setBlock(lx, y, lz, vine);
                    }
                }

                // Floor fungi / roots (Incendium forest floor spirit)
                if (chunkData.getType(lx, floorY + 1, lz).isAir()
                        && EmbedizeBiomes.isLikeAny(biome, Biome.CRIMSON_FOREST, Biome.WARPED_FOREST)
                        && random.nextInt(7) == 0) {
                    boolean crimson = EmbedizeBiomes.isLike(biome, Biome.CRIMSON_FOREST);
                    Material fungus = crimson ? Material.CRIMSON_FUNGUS : Material.WARPED_FUNGUS;
                    Material roots = crimson ? Material.CRIMSON_ROOTS : Material.WARPED_ROOTS;
                    chunkData.setBlock(lx, floorY + 1, lz, random.nextBoolean() ? fungus : roots);
                } else if (chunkData.getType(lx, floorY + 1, lz).isAir()
                        && EmbedizeBiomes.isLike(biome, Biome.SOUL_SAND_VALLEY)
                        && random.nextInt(14) == 0) {
                    chunkData.setBlock(lx, floorY + 1, lz, Material.SOUL_FIRE);
                }
            }
        }
    }

    private double[][][] sampleCheeseGrid(int baseX, int baseZ, int minY, int maxY) {
        int horizontalNodes = 16 / DENSITY_STEP + 1;
        int verticalNodes = (maxY - minY) / DENSITY_STEP + 1;
        double[][][] grid = new double[horizontalNodes][verticalNodes][horizontalNodes];
        for (int gx = 0; gx < horizontalNodes; gx++) {
            int x = baseX + gx * DENSITY_STEP;
            for (int gy = 0; gy < verticalNodes; gy++) {
                int y = minY + gy * DENSITY_STEP;
                for (int gz = 0; gz < horizontalNodes; gz++) {
                    grid[gx][gy][gz] = model.cheeseAt(x, y, baseZ + gz * DENSITY_STEP);
                }
            }
        }
        return grid;
    }

    static double interpolateCheese(double[][][] grid, int localX, int relativeY, int localZ) {
        int gx = Math.min(grid.length - 2, Math.max(0, localX / DENSITY_STEP));
        int gy = Math.min(grid[0].length - 2, Math.max(0, relativeY / DENSITY_STEP));
        int gz = Math.min(grid[0][0].length - 2, Math.max(0, localZ / DENSITY_STEP));
        double tx = Math.floorMod(localX, DENSITY_STEP) / (double) DENSITY_STEP;
        double ty = Math.floorMod(relativeY, DENSITY_STEP) / (double) DENSITY_STEP;
        double tz = Math.floorMod(localZ, DENSITY_STEP) / (double) DENSITY_STEP;

        double x00 = lerp(grid[gx][gy][gz], grid[gx + 1][gy][gz], tx);
        double x10 = lerp(grid[gx][gy + 1][gz], grid[gx + 1][gy + 1][gz], tx);
        double x01 = lerp(grid[gx][gy][gz + 1], grid[gx + 1][gy][gz + 1], tx);
        double x11 = lerp(grid[gx][gy + 1][gz + 1], grid[gx + 1][gy + 1][gz + 1], tx);
        return lerp(lerp(x00, x10, ty), lerp(x01, x11, ty), tz);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static Material filler(Biome biome) {
        if (EmbedizeBiomes.isLike(biome, Biome.SOUL_SAND_VALLEY)) {
            return Material.SOUL_SAND;
        }
        if (EmbedizeBiomes.isLike(biome, Biome.BASALT_DELTAS)) {
            return Material.BASALT;
        }
        return Material.NETHERRACK;
    }

    private static Material surface(Biome biome) {
        if (EmbedizeBiomes.isLike(biome, Biome.SOUL_SAND_VALLEY)) {
            return Material.SOUL_SOIL;
        }
        if (EmbedizeBiomes.isLike(biome, Biome.BASALT_DELTAS)) {
            return Material.BLACKSTONE;
        }
        if (EmbedizeBiomes.isLike(biome, Biome.CRIMSON_FOREST)) {
            return Material.CRIMSON_NYLIUM;
        }
        if (EmbedizeBiomes.isLike(biome, Biome.WARPED_FOREST)) {
            return Material.WARPED_NYLIUM;
        }
        return Material.NETHERRACK;
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
        return model.sample(x, z).floorY();
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
        return new Location(world, 0.5, 64.0, 0.5);
    }

    public String getWorldName() {
        return worldName;
    }

    public @Nullable String getGeneratorId() {
        return generatorId;
    }

    @Override
    public TerrainGeneratorFactory.@NotNull Kind kind() {
        return TerrainGeneratorFactory.Kind.NETHER;
    }

    @Override
    public @NotNull String generatorId() {
        return generatorId;
    }
}
