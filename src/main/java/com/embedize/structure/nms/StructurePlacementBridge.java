package com.embedize.structure.nms;

import com.embedize.structure.SoftBeardAdaptation;
import com.embedize.structure.StructureAirPolicy;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.LimitedRegion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Forces vanilla {@code StructureStart#placeInChunk} during Bukkit decoration.
 * <p>
 * Paper's {@code CustomChunkGenerator} creates StructureStarts when
 * {@code shouldGenerateStructures()} is true, but piece placement lives inside
 * {@code ChunkGenerator#addVanillaDecorations}. Fully custom terrain can leave
 * ghost starts ({@code /locate} works, no blocks). TerraformGenerator solves this
 * by calling {@code placeInChunk} from NMS; we mirror that via reflection against
 * Paper's Mojang-mapped runtime (1.20.5+).
 * <p>
 * Critical: pass the Paper {@code CustomChunkGenerator} into {@code placeInChunk},
 * <em>not</em> {@code getDelegate()}. The delegate is NoiseBased and returns wrong
 * heights when {@code shouldGenerateNoise=false}, which buries jigsaw villages near Y=0.
 * TFG passes {@code this} (its NMS generator with correct {@code getBaseHeight}).
 * <p>
 * Vanilla {@code terrain_adaptation: beard_box} runs in {@code fillFromNoise} via
 * {@code Beardifier} (density subtraction per <em>piece</em> box). With
 * {@code shouldGenerateNoise=false}, that pass never runs — building interiors still
 * clear via template AIR, but streets/plazas between pieces stay solid. Natural gen
 * approximates Beardifier <em>before</em> {@code placeInChunk}:
 * <ul>
 *   <li>{@code structures.density-adapt=true} (default) — column-wise density falloff
 *       via {@link SoftBeardAdaptation} on the current chunk only.</li>
 *   <li>{@code structures.soft-beard=true} (default false) — heavier per-voxel carve;
 *       used only when density-adapt is off.</li>
 * </ul>
 * Both paths write only through {@code WorldGenLevel} for the current populate chunk
 * AABB — never {@code CraftBlock.getType} / {@code Level.getChunk} syncLoad (that
 * deadlocked Leaves/Paper features workers against the Server thread). Do not expand
 * scans into neighbour columns. Manual {@code /embedize place} uses a soft ellipsoid
 * via {@link #preCarveForPlace} (main thread / forceload — separate from natural gen).
 * <p>
 * {@code encapsulate} (stronghold) must never be hollow-carved — it solidifies the exterior.
 * <p>
 * Surface structures optionally skip template AIR writes (see {@link StructureAirPolicy})
 * via a {@link WorldGenLevel} proxy around {@code placeInChunk}, matching Paper's
 * {@code AsyncStructureGenerateEvent} transformer used by {@code /place structure}.
 * <p>
 * Village roads use jigsaw {@code terrain_matching} → {@code GravityProcessor(WORLD_SURFACE_WG)}.
 * This bridge runs in a Bukkit {@link org.bukkit.generator.BlockPopulator} after vanilla
 * decorations and Embedize trees, so a naive {@code Heightmap.primeHeightmaps(ALL)} would
 * bake leaves/logs into {@code WORLD_SURFACE_WG} and climb cobble/gravel into the canopy.
 * After priming, WG heightmaps are restored from {@code ChunkGenerator#getBaseHeight}
 * (noise / pre-feature surface), leaving SoftBeard + surface-ignore-air untouched.
 */
public final class StructurePlacementBridge {

    private static final AtomicBoolean INIT = new AtomicBoolean(false);
    private static final AtomicBoolean AVAILABLE = new AtomicBoolean(false);

    /** When false (default), per-chunk placeInChunk / density-adapt / soft-beard INFO logs are suppressed. */
    private static volatile boolean debug = false;

    /** Terrain materials replaced when simulating beard_box before /place. */
    private static final Set<Material> CARVABLE_TERRAIN = EnumSet.of(
            Material.STONE, Material.DEEPSLATE, Material.TUFF, Material.GRANITE, Material.DIORITE,
            Material.ANDESITE, Material.CALCITE, Material.SMOOTH_BASALT, Material.DRIPSTONE_BLOCK,
            Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.GRASS_BLOCK,
            Material.GRAVEL, Material.SAND, Material.RED_SAND, Material.CLAY, Material.MUD,
            Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE, Material.END_STONE,
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE,
            Material.DEEPSLATE_LAPIS_ORE, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE
    );

    private static Method limitedRegionGetHandle;
    private static Method worldGenLevelGetChunk;
    private static Method worldGenLevelGetSeed;
    private static Method worldGenLevelGetLevel;
    private static Method worldGenLevelGetBlockState;
    private static Method worldGenLevelSetBlock;
    private static Method blockStateGetBlock;
    private static Constructor<?> blockPosCtor;
    private static Object caveAirBlockState;
    /** NMS {@code Block} instances matching {@link #CARVABLE_TERRAIN}; empty if unbound. */
    private static Set<Object> carvableNmsBlocks = Set.of();
    private static Method chunkAccessGetPos;
    private static Method chunkAccessGetMinY;
    private static Method chunkAccessGetMaxY;
    private static Method chunkAccessGetAllStarts;
    private static Method chunkAccessGetAllReferences;
    private static Method structureStartIsValid;
    private static Method structureStartPlaceInChunk;
    private static Method structureStartGetPieces;
    private static Method structureStartGetBoundingBox;
    private static Method structurePieceGetBoundingBox;
    private static Method structureTerrainAdaptation;
    private static Method structureStep;
    private static Method terrainAdjustmentSerializedName;
    private static Method generationStepSerializedName;
    private static Method boundingBoxMinX;
    private static Method boundingBoxMinY;
    private static Method boundingBoxMinZ;
    private static Method boundingBoxMaxX;
    private static Method boundingBoxMaxY;
    private static Method boundingBoxMaxZ;
    private static Method serverLevelStructureManager;
    private static Method serverLevelGetChunkSource;
    private static Method chunkSourceGetGenerator;
    private static Method chunkPosGetMinBlockX;
    private static Method chunkPosGetMinBlockZ;
    private static Method sectionPosBottomOf;
    private static Method structureManagerStartsForStructure;
    private static Method heightmapPrime;
    private static Method chunkAccessGetOrCreateHeightmap;
    private static Method heightmapSetHeight;
    private static Method nmsGetBaseHeight;
    private static Method chunkSourceRandomState;
    private static Object heightmapWorldSurfaceWg;
    private static Object heightmapOceanFloorWg;
    private static Constructor<?> boundingBoxCtor;
    private static Constructor<?> worldgenRandomCtor;
    private static Constructor<?> legacyRandomCtor;
    private static Method worldgenRandomSetDecorationSeed;
    private static Method worldgenRandomSetFeatureSeed;
    private static Class<?> worldGenLevelClass;
    private static Class<?> structureClass;
    private static Class<?> heightmapTypesClass;
    private static Class<?> customChunkGeneratorClass;
    private static Field implementBaseHeightField;
    private static Method blockStateIsAir;
    private static Class<?> blockStateClass;

    private StructurePlacementBridge() {
    }

    public static boolean available() {
        ensureInit(null);
        return AVAILABLE.get();
    }

    /**
     * Reload global {@code debug} from config (default {@code false}).
     * Gates high-volume structure-bridge INFO lines only; WARN/SEVERE and one-shot
     * startup readiness stay visible.
     */
    public static void reloadDebug(@Nullable FileConfiguration config) {
        debug = config != null && config.getBoolean("debug", false);
    }

    public static boolean isDebug() {
        return debug;
    }

    /** Force (re)bind and log diagnostics to {@code logger}. */
    public static boolean initialize(@Nullable Logger logger) {
        INIT.set(false);
        AVAILABLE.set(false);
        ensureInit(logger);
        return AVAILABLE.get();
    }

    /**
     * Place structure pieces intersecting this chunk into the WorldGenRegion
     * backing {@code region}.
     *
     * @return number of StructureStarts asked to place
     */
    public static int placeStartsInRegion(
            @NotNull LimitedRegion region,
            int chunkX,
            int chunkZ,
            @Nullable Logger logger
    ) {
        ensureInit(logger);
        if (!AVAILABLE.get()) {
            return 0;
        }
        try {
            Object worldGenLevel = limitedRegionGetHandle.invoke(region);
            if (worldGenLevel == null || !worldGenLevelClass.isInstance(worldGenLevel)) {
                return 0;
            }
            Object chunkAccess = worldGenLevelGetChunk.invoke(worldGenLevel, chunkX, chunkZ);
            if (chunkAccess == null) {
                return 0;
            }
            return placeIntersectingStarts(worldGenLevel, chunkAccess, logger);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (logger != null) {
                logger.log(Level.WARNING, "[structure-bridge] placeStartsInRegion failed at "
                        + chunkX + "," + chunkZ + ": " + ex.getMessage(), ex);
            }
            return 0;
        }
    }

    /**
     * Post-gen fallback for already-loaded chunks (e.g. after forceload of ghosts).
     */
    public static int placeStartsInWorld(
            @NotNull World world,
            int chunkX,
            int chunkZ,
            @Nullable Logger logger
    ) {
        ensureInit(logger);
        if (!AVAILABLE.get()) {
            return 0;
        }
        try {
            Method getHandle = world.getClass().getMethod("getHandle");
            Object serverLevel = getHandle.invoke(world);
            ensureBukkitBaseHeight(serverLevel, logger);
            Object chunkAccess = worldGenLevelGetChunk.invoke(serverLevel, chunkX, chunkZ);
            if (chunkAccess == null) {
                return 0;
            }
            return placeIntersectingStarts(serverLevel, chunkAccess, logger);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (logger != null) {
                logger.log(Level.WARNING, "[structure-bridge] placeStartsInWorld failed at "
                        + world.getName() + " " + chunkX + "," + chunkZ + ": " + ex.getMessage(), ex);
            }
            return 0;
        }
    }

    /** Re-enable Paper Bukkit getBaseHeight wiring for this world (best-effort). */
    public static void ensureWorld(@NotNull World world, @Nullable Logger logger) {
        ensureInit(logger);
        if (!AVAILABLE.get()) {
            return;
        }
        try {
            Method getHandle = world.getClass().getMethod("getHandle");
            ensureBukkitBaseHeight(getHandle.invoke(world), logger);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (logger != null) {
                logger.log(Level.WARNING, "[structure-bridge] ensureWorld failed for "
                        + world.getName() + ": " + ex.getMessage());
            }
        }
    }

    /**
     * Diagnose NMS height wiring + structure starts at a chunk (for RCON acceptance).
     */
    public static @NotNull String diagnose(@NotNull World world, int blockX, int blockZ) {
        ensureInit(null);
        if (!AVAILABLE.get()) {
            return "bridge unavailable";
        }
        StringBuilder sb = new StringBuilder();
        try {
            Method getHandle = world.getClass().getMethod("getHandle");
            Object serverLevel = getHandle.invoke(world);
            Object chunkSource = serverLevelGetChunkSource.invoke(serverLevel);
            Object generator = chunkSourceGetGenerator.invoke(chunkSource);
            boolean custom = customChunkGeneratorClass != null
                    && customChunkGeneratorClass.isInstance(generator);
            sb.append("gen=").append(generator == null ? "null" : generator.getClass().getSimpleName());
            sb.append(" custom=").append(custom);
            if (custom && implementBaseHeightField != null) {
                implementBaseHeightField.setAccessible(true);
                boolean flag = implementBaseHeightField.getBoolean(generator);
                sb.append(" implementBaseHeight=").append(flag);
                if (!flag) {
                    implementBaseHeightField.setBoolean(generator, true);
                    sb.append("->forcedTrue");
                }
            }
            Object randomState = null;
            try {
                Method rs = chunkSource.getClass().getMethod("randomState");
                randomState = rs.invoke(chunkSource);
            } catch (NoSuchMethodException ignored) {
                // older mapping
            }
            if (nmsGetBaseHeight != null && randomState != null) {
                Object wg = Enum.valueOf(
                        heightmapTypesClass.asSubclass(Enum.class), "WORLD_SURFACE_WG");
                int nmsY = (Integer) nmsGetBaseHeight.invoke(
                        generator, blockX, blockZ, wg, serverLevel, randomState);
                sb.append(" nmsBaseY=").append(nmsY);
            }
            int bukkitY = world.getHighestBlockYAt(blockX, blockZ);
            sb.append(" bukkitHighest=").append(bukkitY);
            try {
                int noLeaves = world.getHighestBlockYAt(
                        blockX, blockZ, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
                sb.append(" noLeaves=").append(noLeaves);
            } catch (Throwable ignored) {
                // older API
            }
            int cx = blockX >> 4;
            int cz = blockZ >> 4;
            Object chunkAccess = worldGenLevelGetChunk.invoke(serverLevel, cx, cz);
            primeHeightmapsForStructurePlacement(serverLevel, chunkAccess, generator, chunkSource, null);
            @SuppressWarnings("unchecked")
            Map<?, ?> starts = (Map<?, ?>) chunkAccessGetAllStarts.invoke(chunkAccess);
            @SuppressWarnings("unchecked")
            Map<?, ?> refs = (Map<?, ?>) chunkAccessGetAllReferences.invoke(chunkAccess);
            sb.append(" starts=").append(starts == null ? 0 : starts.size());
            sb.append(" refs=").append(refs == null ? 0 : refs.size());
            if (starts != null) {
                int i = 0;
                for (Map.Entry<?, ?> e : starts.entrySet()) {
                    if (i++ >= 6) {
                        break;
                    }
                    Object start = e.getValue();
                    Collection<?> pieces = (Collection<?>) structureStartGetPieces.invoke(start);
                    Object bb = structureStartGetBoundingBox.invoke(start);
                    int minY = bb == null ? -1 : (Integer) boundingBoxMinY.invoke(bb);
                    int maxY = bb == null ? -1 : (Integer) boundingBoxMaxY.invoke(bb);
                    sb.append(" | ").append(shortStruct(e.getKey()))
                            .append(" pieces=").append(pieces == null ? 0 : pieces.size())
                            .append(" y=").append(minY).append("..").append(maxY);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            sb.append(" ERR:").append(ex.getClass().getSimpleName()).append(':').append(ex.getMessage());
        }
        return sb.toString();
    }

    /**
     * Bukkit-side material scan (reliable; {@code fill … replace} is a false-negative on Paper).
     */
    public static int scanMaterials(
            @NotNull World world,
            int x1,
            int y1,
            int z1,
            int x2,
            int y2,
            int z2,
            @NotNull Set<Material> materials,
            @Nullable Map<Material, Integer> outCounts
    ) {
        int hits = 0;
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        Map<Material, Integer> local = outCounts != null ? outCounts : new LinkedHashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.getChunkAt(x >> 4, z >> 4);
                for (int y = minY; y <= maxY; y++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (materials.contains(type)) {
                        hits++;
                        local.merge(type, 1, Integer::sum);
                    }
                }
            }
        }
        if (outCounts == null) {
            // discard
        }
        return hits;
    }

    private static String shortStruct(Object structure) {
        if (structure == null) {
            return "?";
        }
        String s = structure.toString();
        int slash = s.lastIndexOf('/');
        int brace = s.lastIndexOf('}');
        if (slash >= 0 && brace > slash) {
            return s.substring(slash + 1, brace);
        }
        return structure.getClass().getSimpleName();
    }

    private static void ensureBukkitBaseHeight(Object serverLevel, @Nullable Logger logger) {
        if (customChunkGeneratorClass == null || implementBaseHeightField == null) {
            return;
        }
        try {
            Object chunkSource = serverLevelGetChunkSource.invoke(serverLevel);
            Object generator = chunkSourceGetGenerator.invoke(chunkSource);
            if (!customChunkGeneratorClass.isInstance(generator)) {
                return;
            }
            implementBaseHeightField.setAccessible(true);
            if (!implementBaseHeightField.getBoolean(generator)) {
                implementBaseHeightField.setBoolean(generator, true);
                if (logger != null) {
                    logger.warning("[structure-bridge] re-enabled CustomChunkGenerator.implementBaseHeight "
                            + "(was false — vanilla noise heights bury jigsaw near Y=0)");
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // best-effort
        }
    }

    /**
     * Ensure heightmaps exist, then restore worldgen ({@code *_WG}) maps from
     * {@code getBaseHeight} so foliage planted before {@code placeInChunk} cannot
     * lift {@code terrain_matching} roads.
     */
    private static void primeHeightmapsForStructurePlacement(
            Object serverLevel,
            Object chunkAccess,
            Object generator,
            Object chunkSource,
            @Nullable Logger logger
    ) throws ReflectiveOperationException {
        if (heightmapPrime == null || heightmapTypesClass == null || chunkAccess == null) {
            return;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        EnumSet types = EnumSet.allOf((Class<? extends Enum>) heightmapTypesClass.asSubclass(Enum.class));
        heightmapPrime.invoke(null, chunkAccess, types);
        restoreWorldgenHeightmaps(serverLevel, chunkAccess, generator, chunkSource, logger);
    }

    /**
     * Overwrite {@code WORLD_SURFACE_WG} / {@code OCEAN_FLOOR_WG} with generator base
     * heights (pre-feature). Non-WG maps stay foliage-inclusive for gameplay.
     */
    private static void restoreWorldgenHeightmaps(
            Object serverLevel,
            Object chunkAccess,
            Object generator,
            Object chunkSource,
            @Nullable Logger logger
    ) {
        if (nmsGetBaseHeight == null
                || chunkAccessGetOrCreateHeightmap == null
                || heightmapSetHeight == null
                || generator == null
                || serverLevel == null) {
            return;
        }
        try {
            Object randomState = chunkSourceRandomState != null && chunkSource != null
                    ? chunkSourceRandomState.invoke(chunkSource)
                    : null;
            if (randomState == null) {
                return;
            }
            Object chunkPos = chunkAccessGetPos.invoke(chunkAccess);
            int minX = (Integer) chunkPosGetMinBlockX.invoke(chunkPos);
            int minZ = (Integer) chunkPosGetMinBlockZ.invoke(chunkPos);

            rewriteWgColumnHeights(
                    chunkAccess, generator, serverLevel, randomState,
                    heightmapWorldSurfaceWg, minX, minZ);
            rewriteWgColumnHeights(
                    chunkAccess, generator, serverLevel, randomState,
                    heightmapOceanFloorWg, minX, minZ);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (logger != null) {
                logger.log(Level.WARNING,
                        "[structure-bridge] failed to restore WORLD_SURFACE_WG from getBaseHeight: "
                                + ex.getMessage());
            }
        }
    }

    private static void rewriteWgColumnHeights(
            Object chunkAccess,
            Object generator,
            Object serverLevel,
            Object randomState,
            @Nullable Object heightmapType,
            int minX,
            int minZ
    ) throws ReflectiveOperationException {
        if (heightmapType == null) {
            return;
        }
        Object heightmap = chunkAccessGetOrCreateHeightmap.invoke(chunkAccess, heightmapType);
        if (heightmap == null) {
            return;
        }
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int y = (Integer) nmsGetBaseHeight.invoke(
                        generator, minX + lx, minZ + lz, heightmapType, serverLevel, randomState);
                heightmapSetHeight.invoke(heightmap, lx, lz, y);
            }
        }
    }

    private static int placeIntersectingStarts(
            Object worldGenLevel,
            Object chunkAccess,
            @Nullable Logger logger
    ) throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        java.util.Map<?, ?> localStarts =
                (java.util.Map<?, ?>) chunkAccessGetAllStarts.invoke(chunkAccess);
        @SuppressWarnings("unchecked")
        java.util.Map<?, ?> refs =
                (java.util.Map<?, ?>) chunkAccessGetAllReferences.invoke(chunkAccess);
        boolean hasRefs = refs != null && !refs.isEmpty();
        if ((localStarts == null || localStarts.isEmpty()) && !hasRefs) {
            return 0;
        }

        Object chunkPos = chunkAccessGetPos.invoke(chunkAccess);
        int minX = (Integer) chunkPosGetMinBlockX.invoke(chunkPos);
        int minZ = (Integer) chunkPosGetMinBlockZ.invoke(chunkPos);
        int minY = (Integer) chunkAccessGetMinY.invoke(chunkAccess);
        int maxY = (Integer) chunkAccessGetMaxY.invoke(chunkAccess);
        Object boundingBox = boundingBoxCtor.newInstance(minX, minY, minZ, minX + 15, maxY, minZ + 15);

        Object serverLevel = worldGenLevelGetLevel.invoke(worldGenLevel);
        ensureBukkitBaseHeight(serverLevel, logger);
        Object structureManager = serverLevelStructureManager.invoke(serverLevel);
        Object chunkSource = serverLevelGetChunkSource.invoke(serverLevel);
        // Keep CustomChunkGenerator — do NOT unwrap to NoiseBased delegate.
        Object generator = chunkSourceGetGenerator.invoke(chunkSource);

        primeHeightmapsForStructurePlacement(serverLevel, chunkAccess, generator, chunkSource, logger);

        long seed = (Long) worldGenLevelGetSeed.invoke(worldGenLevel);
        Object random = worldgenRandomCtor.newInstance(legacyRandomCtor.newInstance(seed));
        long decorationSeed = (Long) worldgenRandomSetDecorationSeed.invoke(random, seed, minX, minZ);
        Object sectionPos = sectionPosBottomOf.invoke(null, chunkAccess);

        // Avoid Registry.stream() — Paper's reflection rewriter breaks IdMap.stream lookup.
        java.util.LinkedHashSet<Object> structures = new java.util.LinkedHashSet<>();
        if (localStarts != null) {
            structures.addAll(localStarts.keySet());
        }
        if (refs != null) {
            structures.addAll(refs.keySet());
        }

        int placed = 0;
        int featureIndex = 0;
        for (Object structure : structures) {
            if (structure == null || !structureClass.isInstance(structure)) {
                continue;
            }
            worldgenRandomSetFeatureSeed.invoke(random, decorationSeed, featureIndex++, 0);
            @SuppressWarnings("unchecked")
            List<Object> starts = (List<Object>) structureManagerStartsForStructure.invoke(
                    structureManager, sectionPos, structure);
            if (starts == null || starts.isEmpty()) {
                if (localStarts != null && localStarts.get(structure) != null) {
                    starts = List.of(localStarts.get(structure));
                } else {
                    continue;
                }
            }
            for (Object start : starts) {
                placed += placeOne(
                        start, structure, worldGenLevel, structureManager, generator, random,
                        boundingBox, chunkPos, logger);
            }
        }
        if (placed > 0 && logger != null && debug) {
            logger.info("[structure-bridge] placeInChunk x" + placed
                    + " at chunk " + (minX >> 4) + "," + (minZ >> 4));
        }
        return placed;
    }

    private static int placeOne(
            Object start,
            Object structure,
            Object worldGenLevel,
            Object structureManager,
            Object generator,
            Object random,
            Object boundingBox,
            Object chunkPos,
            @Nullable Logger logger
    ) throws ReflectiveOperationException {
        if (start == null) {
            return 0;
        }
        Boolean valid = (Boolean) structureStartIsValid.invoke(start);
        if (valid == null || !valid) {
            return 0;
        }
        Collection<?> pieces = (Collection<?>) structureStartGetPieces.invoke(start);
        if (pieces == null || pieces.isEmpty()) {
            return 0;
        }

        String structureId = resolveStructureId(structure, worldGenLevel);
        World bukkitWorld = bukkitWorldOf(worldGenLevel);
        // Beard_box street clearance before placeInChunk. Prefer density-adapt (default);
        // soft-beard only when density-adapt is off — never both (duplicate heavy work).
        // ASYNC SAFETY: WorldGenLevel getBlockState/setBlock on current-chunk coords only.
        // FORBIDDEN on this worker path: CraftBlock.getType, World#getBlockAt, syncLoad,
        // forceload neighbour chunks.
        if (needsSoftBeardAdaptation(bukkitWorld, structure, structureId)) {
            StructureAirPolicy policy = StructureAirPolicy.get();
            if (policy.densityAdaptEnabled()) {
                densityAdaptBeardColumns(worldGenLevel, pieces, boundingBox, structureId, logger);
            } else if (policy.softBeardEnabled()) {
                softAdaptBeardPieces(worldGenLevel, pieces, boundingBox, structureId, logger);
            }
        }

        Object placeLevel = worldGenLevel;
        if (structureId != null && StructureAirPolicy.get().shouldIgnoreAir(structureId, bukkitWorld)) {
            placeLevel = wrapSkippingStructureAir(worldGenLevel);
        }
        structureStartPlaceInChunk.invoke(
                start,
                placeLevel,
                structureManager,
                generator,
                random,
                boundingBox,
                chunkPos
        );
        return 1;
    }

    /**
     * True for {@code beard_box} structures (vanilla or datapack), including
     * {@code *:ancient_city} overrides that keep the adaptation.
     */
    private static boolean needsSoftBeardAdaptation(
            @Nullable World world,
            @Nullable Object structure,
            @Nullable String structureId
    ) {
        if (structureId != null) {
            String id = structureId.toLowerCase(java.util.Locale.ROOT);
            if (id.equals("minecraft:ancient_city") || id.endsWith(":ancient_city")) {
                return true;
            }
        }
        try {
            if (structure != null && structureTerrainAdaptation != null) {
                String adaptation = adaptationName(structureTerrainAdaptation.invoke(structure));
                if (isHollowAdaptation(adaptation)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        if (world == null || structureId == null || structureId.isBlank()) {
            return false;
        }
        StructureMeta meta = lookupStructureMeta(world, structureId);
        return meta != null && isHollowAdaptation(meta.adaptation);
    }

    /**
     * Column-wise density clearance for beard_box / ancient_city (default path).
     * Closer to Beardifier density falloff than a full voxel soft carve: skips
     * columns outside the piece XZ influence, then clears Y with a density threshold.
     * <p>
     * <strong>Watchdog-critical / async-safe:</strong> only {@code WorldGenLevel}
     * get/set on coordinates clamped to the current populate chunk AABB.
     * Never Bukkit {@code getType}/{@code getBlockAt} (CraftBlock → syncLoad deadlock).
     * Never expand into neighbour columns (no O(piece×kernel³) cross-chunk access).
     */
    private static int densityAdaptBeardColumns(
            @NotNull Object worldGenLevel,
            @NotNull Collection<?> pieces,
            @NotNull Object chunkBoundingBox,
            @Nullable String structureId,
            @Nullable Logger logger
    ) throws ReflectiveOperationException {
        if (!beardCarveBound()) {
            return 0;
        }
        int chunkMinX = (Integer) boundingBoxMinX.invoke(chunkBoundingBox);
        int chunkMinY = (Integer) boundingBoxMinY.invoke(chunkBoundingBox);
        int chunkMinZ = (Integer) boundingBoxMinZ.invoke(chunkBoundingBox);
        int chunkMaxX = (Integer) boundingBoxMaxX.invoke(chunkBoundingBox);
        int chunkMaxY = (Integer) boundingBoxMaxY.invoke(chunkBoundingBox);
        int chunkMaxZ = (Integer) boundingBoxMaxZ.invoke(chunkBoundingBox);
        int kernel = SoftBeardAdaptation.KERNEL_RADIUS;
        int carved = 0;
        for (Object piece : pieces) {
            if (piece == null) {
                continue;
            }
            Object pieceBox = structurePieceGetBoundingBox.invoke(piece);
            if (pieceBox == null) {
                continue;
            }
            int minX = (Integer) boundingBoxMinX.invoke(pieceBox);
            int minY = (Integer) boundingBoxMinY.invoke(pieceBox);
            int minZ = (Integer) boundingBoxMinZ.invoke(pieceBox);
            int maxX = (Integer) boundingBoxMaxX.invoke(pieceBox);
            int maxY = (Integer) boundingBoxMaxY.invoke(pieceBox);
            int maxZ = (Integer) boundingBoxMaxZ.invoke(pieceBox);
            // Strictly current-chunk AABB — never expand into neighbour columns.
            int scanMinX = Math.max(chunkMinX, minX - kernel);
            int scanMaxX = Math.min(chunkMaxX, maxX + kernel);
            int scanMinY = Math.max(chunkMinY, minY - kernel);
            int scanMaxY = Math.min(chunkMaxY, maxY + kernel);
            int scanMinZ = Math.max(chunkMinZ, minZ - kernel);
            int scanMaxZ = Math.min(chunkMaxZ, maxZ + kernel);
            if (scanMinX > scanMaxX || scanMinY > scanMaxY || scanMinZ > scanMaxZ) {
                continue;
            }
            for (int x = scanMinX; x <= scanMaxX; x++) {
                for (int z = scanMinZ; z <= scanMaxZ; z++) {
                    if (!SoftBeardAdaptation.influencesColumn(
                            x, z, minX, minZ, maxX, maxZ, kernel)) {
                        continue;
                    }
                    for (int y = scanMinY; y <= scanMaxY; y++) {
                        if (!SoftBeardAdaptation.shouldClearByDensity(
                                x, y, z, minX, minY, minZ, maxX, maxY, maxZ, kernel)) {
                            continue;
                        }
                        carved += carveCarvableAt(worldGenLevel, x, y, z);
                    }
                }
            }
        }
        if (carved > 0 && logger != null && debug) {
            logger.info("[structure-bridge] density-adapt " + carved + " blocks for "
                    + (structureId == null ? "?" : structureId)
                    + " (" + SoftBeardAdaptation.describeDensity() + ")");
        }
        return carved;
    }

    /**
     * Per-piece soft cave_air carve intersecting this chunk (opt-in when
     * density-adapt is off). Opens beard_box street volumes without excavating
     * the StructureStart rectangular hull.
     * <p>
     * <strong>Watchdog-critical:</strong> uses only {@code WorldGenLevel#getBlockState}/
     * {@code setBlock} on coordinates already clamped to the current populate chunk
     * AABB. Must never call Bukkit {@code World#getBlockAt}/{@code getType} (those
     * hit {@code CraftBlock} → {@code Level#getChunk} syncLoad and deadlock async
     * features workers against the Server thread).
     */
    private static int softAdaptBeardPieces(
            @NotNull Object worldGenLevel,
            @NotNull Collection<?> pieces,
            @NotNull Object chunkBoundingBox,
            @Nullable String structureId,
            @Nullable Logger logger
    ) throws ReflectiveOperationException {
        if (!beardCarveBound()) {
            return 0;
        }
        int chunkMinX = (Integer) boundingBoxMinX.invoke(chunkBoundingBox);
        int chunkMinY = (Integer) boundingBoxMinY.invoke(chunkBoundingBox);
        int chunkMinZ = (Integer) boundingBoxMinZ.invoke(chunkBoundingBox);
        int chunkMaxX = (Integer) boundingBoxMaxX.invoke(chunkBoundingBox);
        int chunkMaxY = (Integer) boundingBoxMaxY.invoke(chunkBoundingBox);
        int chunkMaxZ = (Integer) boundingBoxMaxZ.invoke(chunkBoundingBox);
        int kernel = SoftBeardAdaptation.KERNEL_RADIUS;
        int carved = 0;
        for (Object piece : pieces) {
            if (piece == null) {
                continue;
            }
            Object pieceBox = structurePieceGetBoundingBox.invoke(piece);
            if (pieceBox == null) {
                continue;
            }
            int minX = (Integer) boundingBoxMinX.invoke(pieceBox);
            int minY = (Integer) boundingBoxMinY.invoke(pieceBox);
            int minZ = (Integer) boundingBoxMinZ.invoke(pieceBox);
            int maxX = (Integer) boundingBoxMaxX.invoke(pieceBox);
            int maxY = (Integer) boundingBoxMaxY.invoke(pieceBox);
            int maxZ = (Integer) boundingBoxMaxZ.invoke(pieceBox);
            // Strictly current-chunk AABB — never expand into neighbour columns.
            int scanMinX = Math.max(chunkMinX, minX - kernel);
            int scanMaxX = Math.min(chunkMaxX, maxX + kernel);
            int scanMinY = Math.max(chunkMinY, minY - kernel);
            int scanMaxY = Math.min(chunkMaxY, maxY + kernel);
            int scanMinZ = Math.max(chunkMinZ, minZ - kernel);
            int scanMaxZ = Math.min(chunkMaxZ, maxZ + kernel);
            if (scanMinX > scanMaxX || scanMinY > scanMaxY || scanMinZ > scanMaxZ) {
                continue;
            }
            for (int x = scanMinX; x <= scanMaxX; x++) {
                for (int z = scanMinZ; z <= scanMaxZ; z++) {
                    if (!SoftBeardAdaptation.influencesColumn(
                            x, z, minX, minZ, maxX, maxZ, kernel)) {
                        continue;
                    }
                    for (int y = scanMinY; y <= scanMaxY; y++) {
                        if (!SoftBeardAdaptation.shouldCarve(
                                x, y, z, minX, minY, minZ, maxX, maxY, maxZ, kernel)) {
                            continue;
                        }
                        carved += carveCarvableAt(worldGenLevel, x, y, z);
                    }
                }
            }
        }
        if (carved > 0 && logger != null && debug) {
            logger.info("[structure-bridge] soft-beard " + carved + " blocks for "
                    + (structureId == null ? "?" : structureId)
                    + " (" + SoftBeardAdaptation.describe() + ")");
        }
        return carved;
    }

    private static boolean beardCarveBound() {
        return structurePieceGetBoundingBox != null
                && worldGenLevelGetBlockState != null
                && worldGenLevelSetBlock != null
                && blockPosCtor != null
                && caveAirBlockState != null
                && !carvableNmsBlocks.isEmpty();
    }

    /**
     * Replace one carvable NMS block with cave_air via WorldGenLevel only.
     *
     * @return 1 if carved, 0 otherwise
     */
    private static int carveCarvableAt(@NotNull Object worldGenLevel, int x, int y, int z)
            throws ReflectiveOperationException {
        Object pos = blockPosCtor.newInstance(x, y, z);
        Object state = worldGenLevelGetBlockState.invoke(worldGenLevel, pos);
        if (!isCarvableNmsState(state)) {
            return 0;
        }
        // Flag 2 = UPDATE_CLIENTS-ish during gen; avoid neighbour updates.
        worldGenLevelSetBlock.invoke(worldGenLevel, pos, caveAirBlockState, 2);
        return 1;
    }

    private static boolean isCarvableNmsState(@Nullable Object blockState)
            throws ReflectiveOperationException {
        if (blockState == null || blockStateGetBlock == null || carvableNmsBlocks.isEmpty()) {
            return false;
        }
        Object block = blockStateGetBlock.invoke(blockState);
        return block != null && carvableNmsBlocks.contains(block);
    }

    private static @Nullable World bukkitWorldOf(Object worldGenLevel) {
        try {
            Object serverLevel = worldGenLevelGetLevel.invoke(worldGenLevel);
            Method getWorld = serverLevel.getClass().getMethod("getWorld");
            Object world = getWorld.invoke(serverLevel);
            return world instanceof World w ? w : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Proxy {@link WorldGenLevel} that no-ops {@code setBlock} when the new state is air,
     * so structure-template air does not overwrite terrain/fluids.
     */
    private static Object wrapSkippingStructureAir(Object worldGenLevel) {
        if (worldGenLevel == null || worldGenLevelClass == null || blockStateIsAir == null) {
            return worldGenLevel;
        }
        ClassLoader cl = worldGenLevelClass.getClassLoader();
        InvocationHandler handler = (proxy, method, args) -> {
            if ("setBlock".equals(method.getName()) && args != null && args.length >= 2
                    && blockStateClass != null && blockStateClass.isInstance(args[1])) {
                Boolean air = (Boolean) blockStateIsAir.invoke(args[1]);
                if (Boolean.TRUE.equals(air)) {
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class || ret == Boolean.class) {
                        return Boolean.TRUE;
                    }
                    return null;
                }
            }
            try {
                return method.invoke(worldGenLevel, args);
            } catch (java.lang.reflect.InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                if (cause instanceof Error err) {
                    throw err;
                }
                throw ex;
            }
        };
        return Proxy.newProxyInstance(cl, new Class<?>[]{worldGenLevelClass}, handler);
    }

    private static @Nullable String resolveStructureId(@Nullable Object structure, Object worldGenLevel) {
        if (structure == null) {
            return null;
        }
        try {
            Object serverLevel = worldGenLevelGetLevel.invoke(worldGenLevel);
            Method registryAccess = serverLevel.getClass().getMethod("registryAccess");
            Object access = registryAccess.invoke(serverLevel);
            Class<?> registries = Class.forName("net.minecraft.core.registries.Registries");
            Object registryKey = registries.getField("STRUCTURE").get(null);
            Method lookupOrThrow = access.getClass().getMethod(
                    "lookupOrThrow", Class.forName("net.minecraft.resources.ResourceKey"));
            Object registry = lookupOrThrow.invoke(access, registryKey);
            try {
                Method getResourceKey = registry.getClass().getMethod("getResourceKey", Object.class);
                @SuppressWarnings("unchecked")
                java.util.Optional<Object> keyOpt =
                        (java.util.Optional<Object>) getResourceKey.invoke(registry, structure);
                if (keyOpt != null && keyOpt.isPresent()) {
                    Object resourceKey = keyOpt.get();
                    Method location = resourceKey.getClass().getMethod("location");
                    Object loc = location.invoke(resourceKey);
                    if (loc != null) {
                        return loc.toString();
                    }
                }
            } catch (NoSuchMethodException ignored) {
                Method getKey = registry.getClass().getMethod("getKey", Object.class);
                Object loc = getKey.invoke(registry, structure);
                if (loc != null) {
                    return loc.toString();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // fall through
        }
        return shortStruct(structure);
    }

    /**
     * Surface vs underground for template-air policy. Based on structure properties
     * ({@code terrain_adaptation}, generation {@code step}, heightmap) — not vanilla id lists.
     * Datapack-added and datapack-overhauled underground structures must classify as
     * {@link AirPlacement#UNDERGROUND} so air clears solid terrain.
     */
    public enum AirPlacement {
        /** Land/surface buildings: template air should not dig terrain. */
        SURFACE,
        /** Caves / buried / underwater cavities: template air must overwrite solids. */
        UNDERGROUND,
        /** Could not classify — callers should keep air (fail open). */
        UNKNOWN
    }

    /**
     * Classify whether structure template air should clear terrain.
     * Underground step / bury|beard_box|encapsulate / ocean-floor / missing heightmap
     * → {@link AirPlacement#UNDERGROUND}. Surface step + surface heightmap + none|beard_thin
     * → {@link AirPlacement#SURFACE}. Conflicts prefer UNDERGROUND.
     */
    public static @NotNull AirPlacement classifyAirPlacement(
            @Nullable World world,
            @Nullable String structureId
    ) {
        ensureInit(null);
        if (structureId == null || structureId.isBlank()) {
            return AirPlacement.UNKNOWN;
        }
        String id = structureId.toLowerCase(java.util.Locale.ROOT);
        String path = pathOf(id);
        if (pathLooksUnderground(path)) {
            return AirPlacement.UNDERGROUND;
        }

        StructureMeta meta = world != null ? lookupStructureMeta(world, id) : null;
        if (meta != null) {
            if (isUndergroundAdaptation(meta.adaptation) || isUndergroundStep(meta.step)) {
                return AirPlacement.UNDERGROUND;
            }
            if (isOceanHeightmap(meta.heightmap)) {
                return AirPlacement.UNDERGROUND;
            }
            // No start heightmap + not a clear surface step → treat as buried/cavity.
            if ((meta.heightmap == null || meta.heightmap.isBlank())
                    && !isSurfaceStep(meta.step)) {
                return AirPlacement.UNDERGROUND;
            }
            if (isSurfaceStep(meta.step) && isSurfaceAdaptation(meta.adaptation)
                    && (isSurfaceHeightmap(meta.heightmap)
                    || meta.heightmap == null || meta.heightmap.isBlank())) {
                return AirPlacement.SURFACE;
            }
            if (isSurfaceStep(meta.step) && isSurfaceAdaptation(meta.adaptation)
                    && isSurfaceHeightmap(meta.heightmap)) {
                return AirPlacement.SURFACE;
            }
        }

        if (pathLooksSurface(path)) {
            return AirPlacement.SURFACE;
        }
        return AirPlacement.UNKNOWN;
    }

    /**
     * Whether {@code structureId} uses {@code beard_box} (manual place may pre-carve).
     * <p>
     * Stronghold uses {@code encapsulate} (solidify exterior) — never hollow-carve it.
     * Natural generation may optionally use per-piece {@link SoftBeardAdaptation}
     * when {@code structures.soft-beard=true}; this flag is for
     * {@link #preCarveForPlace} only.
     */
    public static boolean needsHollowCarve(@NotNull World world, @NotNull String structureId) {
        ensureInit(null);
        String id = structureId == null ? "" : structureId.toLowerCase(java.util.Locale.ROOT);
        // Exact ancient_city only. Do NOT substring-match "ancient" (ati ancient_temple)
        // and do NOT include stronghold (encapsulate ≠ beard_box).
        if (id.equals("minecraft:ancient_city") || id.endsWith(":ancient_city")) {
            return true;
        }
        if (!AVAILABLE.get()) {
            return false;
        }
        StructureMeta meta = lookupStructureMeta(world, structureId);
        return meta != null && isHollowAdaptation(meta.adaptation);
    }

    /**
     * Bukkit-side soft pre-carve for manual {@code /embedize place} only: ellipsoid
     * around the origin (not a hard cube) so jigsaw solids land into hollow space when
     * pasting into already-generated solid rock. Natural gen uses per-piece soft beard.
     *
     * @return blocks carved, or 0 if adaptation does not need hollowing
     */
    public static int preCarveForPlace(
            @NotNull World world,
            int originX,
            int originY,
            int originZ,
            @NotNull String structureId,
            int radius,
            @Nullable Logger logger
    ) {
        ensureInit(logger);
        if (!needsHollowCarve(world, structureId)) {
            return 0;
        }
        int r = Math.max(8, Math.min(96, radius));
        int yRad = Math.max(8, Math.min(48, r / 2 + 8));
        int minY = Math.max(world.getMinHeight(), originY - yRad);
        int maxY = Math.min(world.getMaxHeight() - 1, originY + yRad);
        int carved = 0;
        for (int x = originX - r; x <= originX + r; x++) {
            for (int z = originZ - r; z <= originZ + r; z++) {
                world.getChunkAt(x >> 4, z >> 4);
                for (int y = minY; y <= maxY; y++) {
                    if (!SoftBeardAdaptation.shouldCarveEllipsoid(
                            x, y, z, originX, originY, originZ, r, yRad)) {
                        continue;
                    }
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (CARVABLE_TERRAIN.contains(type)) {
                        world.getBlockAt(x, y, z).setType(Material.CAVE_AIR, false);
                        carved++;
                    }
                }
            }
        }
        if (logger != null && debug) {
            logger.info("[structure-bridge] place-only soft ellipsoid carve " + carved
                    + " blocks for " + structureId
                    + " at " + originX + "," + originY + "," + originZ + " r=" + r
                    + " (manual place stand-in; natural gen uses per-piece soft beard)");
        }
        return carved;
    }

    private static boolean isHollowAdaptation(@Nullable String adaptation) {
        if (adaptation == null) {
            return false;
        }
        String a = adaptation.toLowerCase(java.util.Locale.ROOT);
        // encapsulate must NOT be treated as hollow (stronghold / similar).
        return "beard_box".equals(a);
    }

    private static boolean isUndergroundAdaptation(@Nullable String adaptation) {
        if (adaptation == null) {
            return false;
        }
        String a = adaptation.toLowerCase(java.util.Locale.ROOT);
        return "bury".equals(a) || "beard_box".equals(a) || "encapsulate".equals(a);
    }

    private static boolean isSurfaceAdaptation(@Nullable String adaptation) {
        if (adaptation == null || adaptation.isBlank()) {
            return true;
        }
        String a = adaptation.toLowerCase(java.util.Locale.ROOT);
        return "none".equals(a) || "beard_thin".equals(a);
    }

    private static boolean isUndergroundStep(@Nullable String step) {
        if (step == null) {
            return false;
        }
        String s = step.toLowerCase(java.util.Locale.ROOT);
        return s.contains("underground") || "strongholds".equals(s) || s.contains("fluid_spring");
    }

    private static boolean isSurfaceStep(@Nullable String step) {
        if (step == null) {
            return false;
        }
        String s = step.toLowerCase(java.util.Locale.ROOT);
        return "surface_structures".equals(s) || s.contains("surface_structure");
    }

    private static boolean isSurfaceHeightmap(@Nullable String heightmap) {
        if (heightmap == null || heightmap.isBlank()) {
            return false;
        }
        String h = heightmap.toUpperCase(java.util.Locale.ROOT);
        return h.contains("WORLD_SURFACE") || h.contains("MOTION_BLOCKING");
    }

    private static boolean isOceanHeightmap(@Nullable String heightmap) {
        if (heightmap == null || heightmap.isBlank()) {
            return false;
        }
        String h = heightmap.toUpperCase(java.util.Locale.ROOT);
        return h.contains("OCEAN_FLOOR");
    }

    private static boolean pathLooksUnderground(@NotNull String path) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        // Token match — covers hopo:mineshaft/..., nova cave_chamber, dungeons_plus, DnT, etc.
        String[] tokens = {
                "mineshaft", "stronghold", "ancient_city", "trial_chamber", "monument",
                "buried_treasure", "fortress", "bastion", "end_city", "dungeon", "catacomb",
                "crypt", "cave", "underground", "tomb", "sewer", "tunnel", "bunker",
                "mineshaft/", "/mine", "spawner", "deepslate_camp", "undead_crypt",
                "creeping_crypt", "wither_cavern", "trial_dungeon"
        };
        for (String t : tokens) {
            if (p.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean pathLooksSurface(@NotNull String path) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        if (pathLooksUnderground(p)) {
            return false;
        }
        // Prefer specific surface names. Broad tokens (camp/house/tower) are intentionally
        // omitted — NMS step/heightmap classifies those at runtime; fail-open keeps air.
        String[] tokens = {
                "village", "pillager_outpost", "outpost", "mansion", "campsite",
                "swamp_hut", "witch_hut", "igloo", "temple", "pyramid", "trail_ruins",
                "ruined_portal", "firewatch", "hamlet", "manor", "chateau",
                "settlement", "capital", "lighthouse", "dojo", "mosque", "homestead"
        };
        for (String t : tokens) {
            if (p.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static String pathOf(@NotNull String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static String adaptationName(@Nullable Object adjustment) {
        if (adjustment == null) {
            return "none";
        }
        try {
            if (terrainAdjustmentSerializedName != null) {
                Object name = terrainAdjustmentSerializedName.invoke(adjustment);
                if (name != null) {
                    return name.toString();
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        if (adjustment instanceof Enum<?> e) {
            return e.name();
        }
        return adjustment.toString();
    }

    private static String enumName(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        try {
            if (generationStepSerializedName != null
                    && generationStepSerializedName.getDeclaringClass().isInstance(value)) {
                Object name = generationStepSerializedName.invoke(value);
                if (name != null) {
                    return name.toString();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // fall through
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        return value.toString();
    }

    private record StructureMeta(
            @Nullable String adaptation,
            @Nullable String step,
            @Nullable String heightmap
    ) {
    }

    private static @Nullable StructureMeta lookupStructureMeta(
            @NotNull World world,
            @NotNull String structureId
    ) {
        try {
            Object structure = lookupStructure(world, structureId);
            if (structure == null) {
                return null;
            }
            String adaptation = "none";
            if (structureTerrainAdaptation != null) {
                adaptation = adaptationName(structureTerrainAdaptation.invoke(structure));
            }
            String step = "";
            if (structureStep != null) {
                step = enumName(structureStep.invoke(structure));
            }
            String heightmap = readProjectHeightmap(structure);
            return new StructureMeta(adaptation, step, heightmap);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static @Nullable Object lookupStructure(@NotNull World world, @NotNull String structureId)
            throws ReflectiveOperationException {
        Method getHandle = world.getClass().getMethod("getHandle");
        Object serverLevel = getHandle.invoke(world);
        Method registryAccess = serverLevel.getClass().getMethod("registryAccess");
        Object access = registryAccess.invoke(serverLevel);
        Class<?> registries = Class.forName("net.minecraft.core.registries.Registries");
        Field structureRegistryKey = registries.getField("STRUCTURE");
        Object registryKey = structureRegistryKey.get(null);
        Method lookupOrThrow = access.getClass().getMethod(
                "lookupOrThrow", Class.forName("net.minecraft.resources.ResourceKey"));
        Object registry = lookupOrThrow.invoke(access, registryKey);
        Class<?> resourceLocation = Class.forName("net.minecraft.resources.ResourceLocation");
        Method parse = resourceLocation.getMethod("parse", String.class);
        Object id = parse.invoke(null, structureId.contains(":") ? structureId : "minecraft:" + structureId);
        @SuppressWarnings("unchecked")
        java.util.Optional<Object> holderOpt = invokeOptionalGet(registry, resourceLocation, id);
        if (holderOpt == null || holderOpt.isEmpty()) {
            return null;
        }
        Object holder = holderOpt.get();
        Method value = holder.getClass().getMethod("value");
        return value.invoke(holder);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Optional<Object> invokeOptionalGet(
            Object registry,
            Class<?> resourceLocation,
            Object id
    ) throws ReflectiveOperationException {
        try {
            Method get = registry.getClass().getMethod("get", resourceLocation);
            return (java.util.Optional<Object>) get.invoke(registry, id);
        } catch (NoSuchMethodException ignored) {
            // continue
        }
        try {
            Method getOptional = registry.getClass().getMethod("getOptional", resourceLocation);
            return (java.util.Optional<Object>) getOptional.invoke(registry, id);
        } catch (NoSuchMethodException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static @Nullable String readProjectHeightmap(Object structure) {
        if (structure == null) {
            return null;
        }
        try {
            Method m;
            try {
                m = structure.getClass().getMethod("projectStartToHeightmap");
            } catch (NoSuchMethodException ex) {
                m = structure.getClass().getMethod("getProjectStartToHeightmap");
            }
            Object opt = m.invoke(structure);
            if (opt instanceof java.util.Optional<?> optional) {
                if (optional.isEmpty()) {
                    return "";
                }
                return enumName(optional.get());
            }
            return opt == null ? "" : enumName(opt);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static void ensureInit(@Nullable Logger logger) {
        if (INIT.get()) {
            return;
        }
        synchronized (StructurePlacementBridge.class) {
            if (INIT.get()) {
                return;
            }
            try {
                resolve();
                AVAILABLE.set(true);
                if (logger != null) {
                    if (heightmapSetHeight != null
                            && chunkAccessGetOrCreateHeightmap != null
                            && nmsGetBaseHeight != null) {
                        logger.info("[structure-bridge] NMS placeInChunk bridge ready"
                                + " (WG heightmap rewrite bound via "
                                + heightmapSetHeight.getName() + ").");
                    } else {
                        logger.warning("[structure-bridge] NMS placeInChunk bridge ready,"
                                + " but WG heightmap rewrite unbound — village roads /"
                                + " terrain_matching may climb foliage.");
                    }
                }
            } catch (Throwable ex) {
                AVAILABLE.set(false);
                if (logger != null) {
                    logger.log(Level.SEVERE, "[structure-bridge] Unable to bind NMS placeInChunk: "
                            + ex.getClass().getSimpleName() + ": " + ex.getMessage(), ex);
                } else {
                    System.err.println("[structure-bridge] Unable to bind NMS placeInChunk: " + ex);
                    ex.printStackTrace(System.err);
                }
            } finally {
                INIT.set(true);
            }
        }
    }

    private static void resolve() throws ReflectiveOperationException {
        Class<?> craftLimitedRegion = Class.forName("org.bukkit.craftbukkit.generator.CraftLimitedRegion");
        limitedRegionGetHandle = craftLimitedRegion.getMethod("getHandle");

        worldGenLevelClass = Class.forName("net.minecraft.world.level.WorldGenLevel");
        Class<?> levelReader = Class.forName("net.minecraft.world.level.LevelReader");
        worldGenLevelGetChunk = levelReader.getMethod("getChunk", int.class, int.class);
        worldGenLevelGetSeed = worldGenLevelClass.getMethod("getSeed");

        Class<?> serverLevelAccessor = Class.forName("net.minecraft.world.level.ServerLevelAccessor");
        worldGenLevelGetLevel = serverLevelAccessor.getMethod("getLevel");

        Class<?> chunkAccess = Class.forName("net.minecraft.world.level.chunk.ChunkAccess");
        chunkAccessGetPos = chunkAccess.getMethod("getPos");
        chunkAccessGetAllStarts = chunkAccess.getMethod("getAllStarts");
        chunkAccessGetAllReferences = chunkAccess.getMethod("getAllReferences");
        Class<?> heightAccessor = Class.forName("net.minecraft.world.level.LevelHeightAccessor");
        chunkAccessGetMinY = heightAccessor.getMethod("getMinY");
        chunkAccessGetMaxY = heightAccessor.getMethod("getMaxY");

        Class<?> chunkPos = Class.forName("net.minecraft.world.level.ChunkPos");
        chunkPosGetMinBlockX = chunkPos.getMethod("getMinBlockX");
        chunkPosGetMinBlockZ = chunkPos.getMethod("getMinBlockZ");

        Class<?> sectionPos = Class.forName("net.minecraft.core.SectionPos");
        sectionPosBottomOf = sectionPos.getMethod("bottomOf", chunkAccess);

        try {
            customChunkGeneratorClass = Class.forName("org.bukkit.craftbukkit.generator.CustomChunkGenerator");
            implementBaseHeightField = customChunkGeneratorClass.getDeclaredField("implementBaseHeight");
        } catch (ClassNotFoundException | NoSuchFieldException ignored) {
            customChunkGeneratorClass = null;
            implementBaseHeightField = null;
        }

        structureClass = Class.forName("net.minecraft.world.level.levelgen.structure.Structure");
        Class<?> structureManager = Class.forName("net.minecraft.world.level.StructureManager");
        structureManagerStartsForStructure = structureManager.getMethod(
                "startsForStructure", sectionPos, structureClass);

        Class<?> structureStart = Class.forName("net.minecraft.world.level.levelgen.structure.StructureStart");
        structureStartIsValid = structureStart.getMethod("isValid");
        structureStartGetPieces = structureStart.getMethod("getPieces");
        structureStartGetBoundingBox = structureStart.getMethod("getBoundingBox");
        structureStartPlaceInChunk = structureStart.getMethod(
                "placeInChunk",
                worldGenLevelClass,
                structureManager,
                Class.forName("net.minecraft.world.level.chunk.ChunkGenerator"),
                Class.forName("net.minecraft.util.RandomSource"),
                Class.forName("net.minecraft.world.level.levelgen.structure.BoundingBox"),
                chunkPos
        );

        Class<?> structurePiece = Class.forName("net.minecraft.world.level.levelgen.structure.StructurePiece");
        try {
            structurePieceGetBoundingBox = structurePiece.getMethod("getBoundingBox");
        } catch (NoSuchMethodException ex) {
            structurePieceGetBoundingBox = structurePiece.getMethod("boundingBox");
        }

        structureTerrainAdaptation = structureClass.getMethod("terrainAdaptation");
        structureStep = structureClass.getMethod("step");
        Class<?> terrainAdjustment = Class.forName(
                "net.minecraft.world.level.levelgen.structure.TerrainAdjustment");
        try {
            terrainAdjustmentSerializedName = terrainAdjustment.getMethod("getSerializedName");
        } catch (NoSuchMethodException ex) {
            terrainAdjustmentSerializedName = null;
        }
        Class<?> generationStep = Class.forName(
                "net.minecraft.world.level.levelgen.GenerationStep$Decoration");
        try {
            generationStepSerializedName = generationStep.getMethod("getSerializedName");
        } catch (NoSuchMethodException ex) {
            generationStepSerializedName = null;
        }

        Class<?> boundingBox = Class.forName("net.minecraft.world.level.levelgen.structure.BoundingBox");
        boundingBoxCtor = boundingBox.getConstructor(
                int.class, int.class, int.class, int.class, int.class, int.class);
        boundingBoxMinX = boundingBox.getMethod("minX");
        boundingBoxMinY = boundingBox.getMethod("minY");
        boundingBoxMinZ = boundingBox.getMethod("minZ");
        boundingBoxMaxX = boundingBox.getMethod("maxX");
        boundingBoxMaxY = boundingBox.getMethod("maxY");
        boundingBoxMaxZ = boundingBox.getMethod("maxZ");

        Class<?> blockState = Class.forName("net.minecraft.world.level.block.state.BlockState");
        blockStateClass = blockState;
        blockStateIsAir = blockState.getMethod("isAir");
        blockStateGetBlock = blockState.getMethod("getBlock");

        Class<?> blockPos = Class.forName("net.minecraft.core.BlockPos");
        blockPosCtor = blockPos.getConstructor(int.class, int.class, int.class);
        Class<?> blockGetter = Class.forName("net.minecraft.world.level.BlockGetter");
        worldGenLevelGetBlockState = blockGetter.getMethod("getBlockState", blockPos);
        Class<?> levelWriter = Class.forName("net.minecraft.world.level.LevelWriter");
        worldGenLevelSetBlock = levelWriter.getMethod("setBlock", blockPos, blockState, int.class);
        bindSoftBeardNmsBlocks();

        Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel");
        serverLevelStructureManager = serverLevel.getMethod("structureManager");
        serverLevelGetChunkSource = serverLevel.getMethod("getChunkSource");
        Class<?> chunkSource = Class.forName("net.minecraft.server.level.ServerChunkCache");
        chunkSourceGetGenerator = chunkSource.getMethod("getGenerator");

        Class<?> legacyRandom = Class.forName("net.minecraft.world.level.levelgen.LegacyRandomSource");
        legacyRandomCtor = legacyRandom.getConstructor(long.class);
        Class<?> worldgenRandom = Class.forName("net.minecraft.world.level.levelgen.WorldgenRandom");
        worldgenRandomCtor = worldgenRandom.getConstructor(Class.forName("net.minecraft.util.RandomSource"));
        worldgenRandomSetDecorationSeed = worldgenRandom.getMethod(
                "setDecorationSeed", long.class, int.class, int.class);
        worldgenRandomSetFeatureSeed = worldgenRandom.getMethod(
                "setFeatureSeed", long.class, int.class, int.class);

        // Heightmap WG rewrite is an enhancement for village roads / terrain_matching.
        // Never let a missing private Heightmap mutator kill placeInChunk.
        bindHeightmapRewriteHelpers(chunkAccess);
    }

    /**
     * Resolve NMS {@code Blocks.*} used by soft beard carve. Soft-beard is opt-in;
     * failure here leaves {@link #carvableNmsBlocks} empty so carve becomes a no-op.
     */
    private static void bindSoftBeardNmsBlocks() {
        caveAirBlockState = null;
        carvableNmsBlocks = Set.of();
        try {
            Class<?> blocks = Class.forName("net.minecraft.world.level.block.Blocks");
            Class<?> block = Class.forName("net.minecraft.world.level.block.Block");
            Method defaultState = block.getMethod("defaultBlockState");
            Object caveAir = blocks.getField("CAVE_AIR").get(null);
            caveAirBlockState = defaultState.invoke(caveAir);

            String[] names = {
                    "STONE", "DEEPSLATE", "TUFF", "GRANITE", "DIORITE", "ANDESITE", "CALCITE",
                    "SMOOTH_BASALT", "DRIPSTONE_BLOCK", "DIRT", "COARSE_DIRT", "ROOTED_DIRT",
                    "GRASS_BLOCK", "GRAVEL", "SAND", "RED_SAND", "CLAY", "MUD",
                    "NETHERRACK", "BASALT", "BLACKSTONE", "END_STONE",
                    "COAL_ORE", "DEEPSLATE_COAL_ORE", "IRON_ORE", "DEEPSLATE_IRON_ORE",
                    "COPPER_ORE", "DEEPSLATE_COPPER_ORE", "GOLD_ORE", "DEEPSLATE_GOLD_ORE",
                    "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE", "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE",
                    "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE", "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE"
            };
            Set<Object> carved = new HashSet<>(names.length * 2);
            for (String name : names) {
                try {
                    Object b = blocks.getField(name).get(null);
                    if (b != null) {
                        carved.add(b);
                    }
                } catch (NoSuchFieldException ignored) {
                    // version skew — skip missing block
                }
            }
            carvableNmsBlocks = Set.copyOf(carved);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            caveAirBlockState = null;
            carvableNmsBlocks = Set.of();
        }
    }

    /**
     * Bind helpers used to restore {@code WORLD_SURFACE_WG} / {@code OCEAN_FLOOR_WG}
     * from {@code ChunkGenerator#getBaseHeight} after {@code primeHeightmaps}.
     * <p>
     * Mojang 1.21.x maps the column mutator as {@code setHeight(III)}; Yarn calls it
     * {@code set}. Both are <strong>private</strong> — {@link Class#getMethod} cannot
     * see them (Paper/Leaves remap leaves Mojang names at runtime). Use declared lookup
     * + {@code setAccessible}. Failure here must not abort {@link #resolve()}.
     */
    private static void bindHeightmapRewriteHelpers(Class<?> chunkAccess) {
        heightmapPrime = null;
        heightmapSetHeight = null;
        chunkAccessGetOrCreateHeightmap = null;
        nmsGetBaseHeight = null;
        chunkSourceRandomState = null;
        heightmapWorldSurfaceWg = null;
        heightmapOceanFloorWg = null;
        try {
            heightmapTypesClass = Class.forName("net.minecraft.world.level.levelgen.Heightmap$Types");
            Class<?> heightmap = Class.forName("net.minecraft.world.level.levelgen.Heightmap");
            heightmapPrime = firstMethod(
                    heightmap,
                    new String[]{"primeHeightmaps", "populateHeightmaps"},
                    chunkAccess,
                    java.util.Set.class
            );
            // private void setHeight/set(int x, int z, int height) on 1.21.11
            heightmapSetHeight = firstMethod(
                    heightmap,
                    new String[]{"setHeight", "set", "m_64245_", "method_12602"},
                    int.class,
                    int.class,
                    int.class
            );
            chunkAccessGetOrCreateHeightmap = firstMethod(
                    chunkAccess,
                    new String[]{"getOrCreateHeightmapUnprimed", "getOrCreateHeightmap"},
                    heightmapTypesClass
            );
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<? extends Enum> hmEnum = (Class<? extends Enum>) heightmapTypesClass.asSubclass(Enum.class);
            heightmapWorldSurfaceWg = Enum.valueOf(hmEnum, "WORLD_SURFACE_WG");
            heightmapOceanFloorWg = Enum.valueOf(hmEnum, "OCEAN_FLOOR_WG");

            Class<?> chunkGenerator = Class.forName("net.minecraft.world.level.chunk.ChunkGenerator");
            nmsGetBaseHeight = firstMethod(
                    chunkGenerator,
                    new String[]{"getBaseHeight"},
                    int.class,
                    int.class,
                    heightmapTypesClass,
                    Class.forName("net.minecraft.world.level.LevelHeightAccessor"),
                    Class.forName("net.minecraft.world.level.levelgen.RandomState")
            );
            chunkSourceRandomState = firstMethod(
                    Class.forName("net.minecraft.server.level.ServerChunkCache"),
                    new String[]{"randomState"}
            );
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // leave nulls; restoreWorldgenHeightmaps no-ops when unbound
        }
    }

    /**
     * Resolve a method by candidate Mojang / Yarn / intermediary / SRG names.
     * Tries {@link Class#getMethod} then {@link Class#getDeclaredMethod} (for private
     * members such as {@code Heightmap#setHeight}).
     */
    private static @Nullable Method firstMethod(
            Class<?> owner,
            String[] names,
            Class<?>... params
    ) {
        for (String name : names) {
            try {
                Method m = owner.getMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // try declared / next candidate
            }
            try {
                Method m = owner.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // next candidate
            }
        }
        return null;
    }
}
