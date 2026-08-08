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
 * Overworld terrain.
 * <p>
 * Biome intent (Embedize names are aesthetic roles, not Terralith 1:1 copies):
 * <ul>
 *   <li>{@code abyssal_deep} — far-ocean trench (Alex Abyssal Chasm spirit). Seafloor
 *       materials are the surface; never used for rivers or near-shore freshwater.</li>
 *   <li>vanilla {@code beach} — thin coastal sand ribbon only (no crystal gimmicks).</li>
 *   <li>{@code mangrove_basin} — mud swamp; never sand+calcite.</li>
 *   <li>Cave biomes ({@code lush_caves}/{@code dripstone_caves}/{@code deep_dark}) come from
 *       the 3D {@link OverworldBiomeProvider}; column surface materials still use
 *       {@link OverworldBiomeProvider#biomeAt}.</li>
 * </ul>
 * Floating stone over water is treated as a column integrity bug (height/biome mismatch
 * or carver undercut), not a sea-level off-by-one.
 */
public final class OverworldTerrainGenerator extends ChunkGenerator implements EmbedizeGenerator {

    private final String worldName;
    private final String generatorId;

    private volatile long boundSeed = Long.MIN_VALUE;
    private OverworldNoiseModel model;
    private OverworldBiomeProvider biomeProvider;

    public OverworldTerrainGenerator(String worldName, @Nullable String id) {
        this.worldName = worldName == null ? "world" : worldName;
        this.generatorId = id == null || id.isBlank() ? "overworld" : id;
    }

    private synchronized void bindSeed(long seed) {
        if (boundSeed == seed && model != null) {
            return;
        }
        boundSeed = seed;
        model = new OverworldNoiseModel(seed);
        biomeProvider = new OverworldBiomeProvider(model);
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
        if (ResetSpawnFastPath.isSpawnChunk(chunkX, chunkZ)) {
            fillResetSpawnPlatform(worldInfo, chunkData);
            return;
        }
        ensure(worldInfo);
        int minY = worldInfo.getMinHeight();
        int maxY = worldInfo.getMaxHeight();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int sea = OverworldNoiseModel.SEA_LEVEL;
        final int waterTop = sea - 1;

        OverworldNoiseModel.Sample[][] samples = new OverworldNoiseModel.Sample[16][16];
        int[][] heights = new int[16][16];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                OverworldNoiseModel.Sample s = model.sample(baseX + lx, baseZ + lz);
                samples[lx][lz] = s;
                heights[lx][lz] = clamp(s.surfaceY(), minY + 2, maxY - 2);
            }
        }
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                OverworldNoiseModel.Sample sample = samples[lx][lz];
                int surface = heights[lx][lz];
                // Height, biome and getBaseHeight all use the same global-coordinate sample.
                Biome biome = biomeProvider.biomeAt(sample, surface);
                // Rivers always fill to the shared waterline (sea-1). Using surface+1 when
                // surface==62 produced an empty fill loop and dry sand "rivers".
                boolean riverWater = sample.river() && surface < sea;

                chunkData.setBlock(lx, minY, lz, Material.BEDROCK);
                fillColumn(chunkData, lx, lz, minY, surface, biome, sample);

                if (surface < sea || riverWater) {
                    int fluidTop = waterTop;
                    for (int y = surface + 1; y <= fluidTop && y < maxY; y++) {
                        chunkData.setBlock(lx, y, lz, Material.WATER);
                    }
                    boolean inlandLake = OverworldNoiseModel.isInlandLake(sample);
                    Material floor;
                    if (riverWater || inlandLake) {
                        floor = riverbed(model, baseX + lx, baseZ + lz);
                    } else {
                        floor = seafloor(model, biome, baseX + lx, baseZ + lz, sample);
                    }
                    chunkData.setBlock(lx, surface, lz, floor);
                    for (int d = 1; d <= 3 && surface - d > minY; d++) {
                        chunkData.setBlock(lx, surface - d, lz, floorUnder(floor, d));
                    }
                    if (riverWater && EmbedizeBiomes.isLike(biome, Biome.FROZEN_RIVER)
                            && fluidTop < maxY) {
                        chunkData.setBlock(lx, fluidTop, lz, Material.ICE);
                    }
                } else {
                    boolean nearShore = isActualShore(surface, sample);
                    int slope = neighborSlope(heights, lx, lz);
                    boolean steep = slope >= 5 || sample.peaks();
                    applySurfaceStack(
                            chunkData, lx, lz, surface, biome, sample,
                            steep && !nearShore, maxY, minY, random, nearShore
                    );
                }
            }
        }
    }

    /** Thin ocean↔land waterline only — not a continental sand apron. */
    private static boolean isActualShore(int surface, OverworldNoiseModel.Sample sample) {
        int sea = OverworldNoiseModel.SEA_LEVEL;
        double c = sample.continentalness();
        return surface <= sea + 1
                && surface >= sea
                && sample.land() >= 0.46
                && sample.land() < 0.49
                && c >= -0.36
                && c < -0.22
                && !sample.river();
    }

    private static int neighborSlope(int[][] heights, int lx, int lz) {
        int h = heights[lx][lz];
        int s = 0;
        if (lx > 0) {
            s = Math.max(s, Math.abs(heights[lx - 1][lz] - h));
        }
        if (lx < 15) {
            s = Math.max(s, Math.abs(heights[lx + 1][lz] - h));
        }
        if (lz > 0) {
            s = Math.max(s, Math.abs(heights[lx][lz - 1] - h));
        }
        if (lz < 15) {
            s = Math.max(s, Math.abs(heights[lx][lz + 1] - h));
        }
        return s;
    }

    private static void fillColumn(
            ChunkData chunkData,
            int lx,
            int lz,
            int minY,
            int surface,
            Biome biome,
            OverworldNoiseModel.Sample sample
    ) {
        Material nearTop = nearSurfaceFill(biome);
        boolean badlands = isBadlands(biome);
        boolean peakStone = sample.peaks() && surface > 100;
        boolean trench = sample.oceanTrench() || EmbedizeBiomes.isAbyssalDeep(biome);
        for (int y = minY + 1; y <= surface; y++) {
            Material mat;
            if (trench) {
                int fromFloor = surface - y;
                if (y < 0 || fromFloor <= 3) {
                    mat = Material.DEEPSLATE;
                } else if (fromFloor > 14) {
                    // Abyssmarine-like trench wall (vanilla stand-in)
                    mat = fromFloor % 5 == 0 ? Material.PRISMARINE : Material.DARK_PRISMARINE;
                } else {
                    mat = Material.TUFF;
                }
            } else if (y < 0) {
                mat = Material.DEEPSLATE;
            } else if (badlands && y >= surface - 28) {
                mat = terracottaBand(y);
            } else if (!peakStone && y >= surface - 3 && y < surface) {
                mat = nearTop;
            } else {
                mat = y < 16 ? Material.DEEPSLATE : Material.STONE;
            }
            chunkData.setBlock(lx, y, lz, mat);
        }
    }

    private static Material terracottaBand(int y) {
        return switch (Math.floorMod(y, 6)) {
            case 0 -> Material.TERRACOTTA;
            case 1 -> Material.ORANGE_TERRACOTTA;
            case 2 -> Material.YELLOW_TERRACOTTA;
            case 3 -> Material.BROWN_TERRACOTTA;
            case 4 -> Material.RED_TERRACOTTA;
            default -> Material.WHITE_TERRACOTTA;
        };
    }

    private static Material nearSurfaceFill(Biome biome) {
        if (isDesertLike(biome)) {
            return Material.SANDSTONE;
        }
        if (isBeach(biome)) {
            return Material.SAND;
        }
        if (EmbedizeBiomes.isAbyssalDeep(biome)) {
            return Material.DEEPSLATE;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.MANGROVE_SWAMP, Biome.SWAMP)) {
            return Material.MUD;
        }
        return Material.DIRT;
    }

    private static void applySurfaceStack(
            ChunkData data,
            int lx,
            int lz,
            int surface,
            Biome biome,
            OverworldNoiseModel.Sample sample,
            boolean steep,
            int maxY,
            int minY,
            Random random,
            boolean nearShore
    ) {
        if (nearShore
                || EmbedizeBiomes.isLike(biome, Biome.BEACH)
                || EmbedizeBiomes.isLike(biome, Biome.SNOWY_BEACH)
                || EmbedizeBiomes.isLike(biome, Biome.STONY_SHORE)) {
            Material top = Material.SAND;
            if (EmbedizeBiomes.isLike(biome, Biome.STONY_SHORE)) {
                top = random.nextBoolean() ? Material.STONE : Material.GRAVEL;
            } else if (EmbedizeBiomes.isLike(biome, Biome.SNOWY_BEACH)) {
                top = Material.SNOW_BLOCK;
            }
            data.setBlock(lx, surface, lz, top);
            for (int d = 1; d <= 3 && surface - d > minY; d++) {
                Material under = Material.SAND;
                if (top == Material.STONE || top == Material.GRAVEL) {
                    under = Material.STONE;
                }
                if (top == Material.SNOW_BLOCK) {
                    under = Material.DIRT;
                }
                data.setBlock(lx, surface - d, lz, under);
            }
            return;
        }

        if (isDesertLike(biome)) {
            data.setBlock(lx, surface, lz, Material.SAND);
            for (int d = 1; d <= 3 && surface - d > minY; d++) {
                data.setBlock(lx, surface - d, lz, d <= 2 ? Material.SAND : Material.SANDSTONE);
            }
            return;
        }
        if (isBadlands(biome)) {
            data.setBlock(lx, surface, lz, Material.RED_SAND);
            for (int d = 1; d <= 2 && surface - d > minY; d++) {
                data.setBlock(lx, surface - d, lz, Material.RED_SAND);
            }
            if (EmbedizeBiomes.isLike(biome, Biome.ERODED_BADLANDS) && sample.ridges() > 0.55 && surface + 6 < maxY) {
                int h = 3 + random.nextInt(8);
                for (int i = 1; i <= h && surface + i < maxY; i++) {
                    data.setBlock(lx, surface + i, lz, Material.TERRACOTTA);
                }
            }
            return;
        }
        if (EmbedizeBiomes.isLike(biome, Biome.ICE_SPIKES) && surface + 1 < maxY && random.nextInt(3) == 0) {
            data.setBlock(lx, surface, lz, Material.PACKED_ICE);
            int spike = 4 + random.nextInt(10);
            for (int i = 1; i <= spike && surface + i < maxY; i++) {
                data.setBlock(lx, surface + i, lz, Material.PACKED_ICE);
            }
            return;
        }
        if (isSnowy(biome)) {
            Material top;
            if (steep || EmbedizeBiomes.isLikeAny(biome, Biome.FROZEN_PEAKS, Biome.JAGGED_PEAKS)) {
                top = Material.STONE;
            } else if (EmbedizeBiomes.isLikeAny(biome, Biome.SNOWY_SLOPES, Biome.GROVE)) {
                top = surface > 95 ? Material.SNOW_BLOCK : Material.POWDER_SNOW;
                if (EmbedizeBiomes.isLike(biome, Biome.GROVE) && surface < 100) {
                    top = Material.SNOW_BLOCK;
                }
            } else {
                top = surface > 110 ? Material.SNOW_BLOCK : Material.GRASS_BLOCK;
            }
            data.setBlock(lx, surface, lz, top);
            if (surface + 1 < maxY && (top == Material.GRASS_BLOCK || top == Material.SNOW_BLOCK)
                    && random.nextInt(3) != 0) {
                data.setBlock(lx, surface + 1, lz, Material.SNOW);
            }
            if (!steep) {
                for (int d = 1; d <= 2 && surface - d > minY; d++) {
                    data.setBlock(lx, surface - d, lz, Material.DIRT);
                }
            }
            return;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.MANGROVE_SWAMP, Biome.SWAMP)) {
            data.setBlock(lx, surface, lz, Material.MUD);
            for (int d = 1; d <= 2 && surface - d > minY; d++) {
                data.setBlock(lx, surface - d, lz, Material.MUD);
            }
            return;
        }
        if (steep
                || EmbedizeBiomes.isLikeAny(biome, Biome.STONY_PEAKS, Biome.JAGGED_PEAKS, Biome.WINDSWEPT_GRAVELLY_HILLS)
                || sample.peaks()) {
            data.setBlock(lx, surface, lz, random.nextBoolean() ? Material.STONE : Material.GRAVEL);
            return;
        }
        if (EmbedizeBiomes.isLike(biome, Biome.MUSHROOM_FIELDS)) {
            data.setBlock(lx, surface, lz, Material.MYCELIUM);
            return;
        }
        data.setBlock(lx, surface, lz, Material.GRASS_BLOCK);
        int dirt = sample.mountainous() ? 2 : 3;
        for (int d = 1; d <= dirt && surface - d > minY; d++) {
            data.setBlock(lx, surface - d, lz, Material.DIRT);
        }
    }

    /**
     * Abyssal Deep seafloor palette — this IS the biome surface (underwater).
     * Uses smooth patch noise so accents form blobs, not ax+bz hash stripes.
     */
    private static Material seafloor(
            OverworldNoiseModel model,
            Biome biome,
            int lx,
            int lz,
            OverworldNoiseModel.Sample sample
    ) {
        if (EmbedizeBiomes.isAbyssalDeep(biome) || sample.oceanTrench()) {
            // 渊海陷窟 floor: dark prismarine / deepslate / magma vents (vanilla stand-ins)
            double n = model.surfacePatch(lx, lz);
            double n2 = model.surfacePatch(lx + 4096, lz - 2048);
            if (n > 0.55) {
                return Material.MAGMA_BLOCK;
            }
            if (n > 0.28) {
                return n2 > 0.0 ? Material.DARK_PRISMARINE : Material.PRISMARINE;
            }
            if (n2 > 0.42) {
                return Material.TUFF;
            }
            if (n < -0.40) {
                return Material.GRAVEL;
            }
            return Material.DEEPSLATE;
        }
        if (EmbedizeBiomes.isLike(biome, Biome.DEEP_OCEAN)
                || EmbedizeBiomes.isLikeAny(biome,
                Biome.DEEP_COLD_OCEAN, Biome.DEEP_LUKEWARM_OCEAN, Biome.DEEP_FROZEN_OCEAN)) {
            double n = model.surfacePatch(lx, lz);
            if (n > 0.35) {
                return Material.GRAVEL;
            }
            if (n < -0.4) {
                return Material.CLAY;
            }
            return Material.DEEPSLATE;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.WARM_OCEAN, Biome.LUKEWARM_OCEAN, Biome.BEACH)) {
            return Material.SAND;
        }
        if (EmbedizeBiomes.isLikeAny(biome, Biome.FROZEN_OCEAN, Biome.COLD_OCEAN)) {
            return Material.GRAVEL;
        }
        return Material.GRAVEL;
    }

    private static Material riverbed(OverworldNoiseModel model, int x, int z) {
        double n = model.surfacePatch(x, z);
        if (n < -0.35) {
            return Material.CLAY;
        }
        if (n < 0.12) {
            return Material.SAND;
        }
        return Material.GRAVEL;
    }

    private static Material floorUnder(Material floor, int depth) {
        if (floor == Material.DARK_PRISMARINE || floor == Material.PRISMARINE) {
            return depth == 1 ? floor : Material.DEEPSLATE;
        }
        if (floor == Material.SAND) {
            return Material.SAND;
        }
        return floor == Material.GRAVEL ? Material.GRAVEL : Material.DEEPSLATE;
    }

    private static boolean isDesertLike(Biome b) {
        return EmbedizeBiomes.isLike(b, Biome.DESERT);
    }

    private static boolean isBadlands(Biome b) {
        return EmbedizeBiomes.isLikeAny(b, Biome.BADLANDS, Biome.WOODED_BADLANDS, Biome.ERODED_BADLANDS);
    }

    private static boolean isBeach(Biome b) {
        return EmbedizeBiomes.isLikeAny(b, Biome.BEACH, Biome.SNOWY_BEACH, Biome.STONY_SHORE);
    }

    private static boolean isSnowy(Biome b) {
        return EmbedizeBiomes.isLikeAny(b,
                Biome.SNOWY_PLAINS, Biome.SNOWY_TAIGA, Biome.SNOWY_SLOPES,
                Biome.FROZEN_PEAKS, Biome.ICE_SPIKES, Biome.GROVE);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public int getBaseHeight(
            @NotNull WorldInfo worldInfo,
            @NotNull Random random,
            int x,
            int z,
            @NotNull HeightMap heightMap
    ) {
        // Must never throw — Paper permanently disables CustomChunkGenerator.implementBaseHeight
        // after UnsupportedOperationException and then jigsaw uses NoiseBased heights (~Y=0).
        // WORLD_SURFACE_WG / OCEAN_FLOOR_WG must stay on noise surface (no leaves/logs): village
        // terrain_matching GravityProcessor reads WORLD_SURFACE_WG after our placeInChunk bridge.
        try {
            ensure(worldInfo);
            int surface = model.sample(x, z).surfaceY();
            return switch (heightMap) {
                case OCEAN_FLOOR, OCEAN_FLOOR_WG -> surface;
                case MOTION_BLOCKING_NO_LEAVES -> surface;
                default -> Math.max(surface, OverworldNoiseModel.SEA_LEVEL);
            };
        } catch (RuntimeException ex) {
            return OverworldNoiseModel.SEA_LEVEL;
        }
    }

    /**
     * Minimal solid platform at spawn so Multiverse {@code getBlock()} during
     * import stays cheap and spawn safety checks pass without biome scans.
     */
    private static void fillResetSpawnPlatform(@NotNull WorldInfo worldInfo, @NotNull ChunkData chunkData) {
        int minY = worldInfo.getMinHeight();
        int platformY = Math.min(worldInfo.getMaxHeight() - 8, Math.max(minY + 16, 95));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunkData.setBlock(x, minY, z, Material.BEDROCK);
                for (int y = minY + 1; y <= platformY; y++) {
                    chunkData.setBlock(x, y, z, Material.STONE);
                }
            }
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        // This generator writes the complete density column itself. Delegating
        // vanilla noise first only doubles CPU time before those blocks are replaced.
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
        return !ResetSpawnFastPath.isActive();
    }

    @Override
    public boolean shouldGenerateCaves(
            @NotNull WorldInfo worldInfo,
            @NotNull Random random,
            int chunkX,
            int chunkZ
    ) {
        if (ResetSpawnFastPath.isSpawnChunk(chunkX, chunkZ)) {
            return false;
        }
        ensure(worldInfo);
        // Allow vanilla noodle/cheese carvers on any chunk that isn't open ocean.
        int cx = (chunkX << 4) + 8;
        int cz = (chunkZ << 4) + 8;
        OverworldNoiseModel.Sample s = model.sample(cx, cz);
        return s.surfaceY() >= OverworldNoiseModel.SEA_LEVEL - 2 && s.land() > 0.38;
    }

    @Override
    public void generateCaves(
            @NotNull WorldInfo worldInfo,
            @NotNull Random random,
            int chunkX,
            int chunkZ,
            @NotNull ChunkData chunkData
    ) {
        ensure(worldInfo);
        int minY = worldInfo.getMinHeight();
        int maxY = worldInfo.getMaxHeight();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int sea = OverworldNoiseModel.SEA_LEVEL;
        final int waterTop = sea - 1;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                OverworldNoiseModel.Sample s = model.sample(baseX + lx, baseZ + lz);
                int surface = clamp(s.surfaceY(), minY + 2, maxY - 2);
                int wx = baseX + lx;
                int wz = baseZ + lz;

                if (surface < sea) {
                    // Keep ocean water column intact; still allow rare seafloor cheese below bed.
                    for (int y = surface + 1; y <= waterTop && y < maxY; y++) {
                        Material t = chunkData.getType(lx, y, lz);
                        if (t.isAir() || t == Material.CAVE_AIR) {
                            chunkData.setBlock(lx, y, lz, Material.WATER);
                        }
                    }
                    continue;
                }

                // Only the thin beach ribbon: refill aquifers that would open to the sea.
                // Do NOT seal inland caves (previous land<0.85 refill wiped noodle caves).
                boolean beachSeal = s.land() < 0.58 && surface <= sea + 6;
                if (beachSeal) {
                    int lo = Math.max(minY + 1, sea - 8);
                    int hi = Math.min(surface, sea + 2);
                    for (int y = lo; y <= hi; y++) {
                        Material t = chunkData.getType(lx, y, lz);
                        if (t.isAir() || t == Material.WATER || t == Material.CAVE_AIR) {
                            chunkData.setBlock(lx, y, lz, y < 16 ? Material.DEEPSLATE : Material.STONE);
                        }
                    }
                }

                // Sparse cheese/noodle pass (vanilla carvers already ran). Step by 2 to cut MSPT.
                for (int y = minY + 6; y < surface - 8; y += 2) {
                    if (!model.isCaveAir(wx, y, wz, surface)) {
                        continue;
                    }
                    Material cur = chunkData.getType(lx, y, lz);
                    if (!cur.isSolid()) {
                        continue;
                    }
                    chunkData.setBlock(lx, y, lz, Material.CAVE_AIR);
                    if (y + 1 < surface - 3 && chunkData.getType(lx, y + 1, lz).isSolid()
                            && model.isCaveAir(wx, y + 1, wz, surface)) {
                        chunkData.setBlock(lx, y + 1, lz, Material.CAVE_AIR);
                    }
                }
            }
        }
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return !ResetSpawnFastPath.isActive();
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        // Bundled structure datapack + vanilla JigsawPlacement / StructureSet pipeline.
        // Disabled briefly during reset registration so Multiverse spawn touch stays cheap.
        return !ResetSpawnFastPath.isActive();
    }

    /**
     * Skip Paper/vanilla "Selecting spawn point" biome scans on world create.
     * That scan sync-generates many Embedize chunks on the Server thread (30–60s+)
     * and keepalive-timeouts every online player, including those in the main world.
     */
    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0.5, 96.0, 0.5);
    }

    public String getWorldName() {
        return worldName;
    }

    public @Nullable String getGeneratorId() {
        return generatorId;
    }

    @Override
    public TerrainGeneratorFactory.@NotNull Kind kind() {
        return TerrainGeneratorFactory.Kind.OVERWORLD;
    }

    @Override
    public @NotNull String generatorId() {
        return generatorId;
    }
}
