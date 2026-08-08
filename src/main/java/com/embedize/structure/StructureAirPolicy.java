package com.embedize.structure;

import com.embedize.structure.nms.StructurePlacementBridge;
import com.embedize.structure.nms.StructurePlacementBridge.AirPlacement;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controls whether structure template AIR may overwrite existing world blocks,
 * and whether manual {@code /embedize place} may pre-carve beard_box cavities.
 * <p>
 * When {@code surface-ignore-air} is enabled, air is skipped <em>only</em> for
 * structures classified as surface (generation step / terrain_adaptation /
 * heightmap, plus path heuristics). Underground and unknown structures keep air
 * so datapack cavities (hopo mineshafts, nova cave chambers, dungeons+, DnT
 * overhauls, custom namespaces, and {@code minecraft:*} overrides) are not
 * filled with solid terrain.
 * <p>
 * {@code keep-air-ids} remains an explicit force-keep override, not the primary
 * Natural generation applies per-piece soft beard for {@code beard_box} streets;
 * {@code place-hollow-carve} applies only to the manual place command (soft ellipsoid).
 */
public final class StructureAirPolicy {

    private static final List<String> DEFAULT_KEEP_AIR = List.of(
            "minecraft:ancient_city",
            "minecraft:stronghold",
            "minecraft:trial_chambers",
            "minecraft:mineshaft",
            "minecraft:mineshaft_mesa",
            "minecraft:monument",
            "minecraft:buried_treasure",
            "minecraft:fortress",
            "minecraft:bastion_remnant",
            "minecraft:end_city"
    );

    private static volatile StructureAirPolicy INSTANCE = defaults();

    private final boolean surfaceIgnoreAir;
    private final boolean placeHollowCarve;
    private final List<String> keepAirIds;

    StructureAirPolicy(boolean surfaceIgnoreAir, boolean placeHollowCarve, List<String> keepAirIds) {
        this.surfaceIgnoreAir = surfaceIgnoreAir;
        this.placeHollowCarve = placeHollowCarve;
        this.keepAirIds = List.copyOf(keepAirIds);
    }

    public static @NotNull StructureAirPolicy get() {
        return INSTANCE;
    }

    public static void reload(@Nullable FileConfiguration config) {
        INSTANCE = config == null ? defaults() : fromConfig(config);
    }

    public static @NotNull StructureAirPolicy defaults() {
        return new StructureAirPolicy(true, true, DEFAULT_KEEP_AIR);
    }

    public static @NotNull StructureAirPolicy fromConfig(@NotNull FileConfiguration config) {
        ConfigurationSection root = config.getConfigurationSection("structures");
        if (root == null) {
            return defaults();
        }
        boolean ignore = root.getBoolean("surface-ignore-air", true);
        boolean placeCarve = root.getBoolean("place-hollow-carve", true);
        List<String> keep = root.getStringList("keep-air-ids");
        if (keep == null || keep.isEmpty()) {
            keep = DEFAULT_KEEP_AIR;
        } else {
            List<String> cleaned = new ArrayList<>(keep.size());
            for (String id : keep) {
                if (id != null && !id.isBlank()) {
                    cleaned.add(id.trim());
                }
            }
            keep = cleaned.isEmpty() ? DEFAULT_KEEP_AIR : cleaned;
        }
        return new StructureAirPolicy(ignore, placeCarve, keep);
    }

    public boolean surfaceIgnoreAirEnabled() {
        return surfaceIgnoreAir;
    }

    /**
     * When true, {@code /embedize place} of beard_box structures (ancient_city) may
     * pre-carve cave_air into solid terrain. Natural generation never uses this.
     */
    public boolean placeHollowCarveEnabled() {
        return placeHollowCarve;
    }

    public @NotNull List<String> keepAirIds() {
        return keepAirIds;
    }

    /**
     * @return true when structure template air must not overwrite the world
     */
    public boolean shouldIgnoreAir(@Nullable String structureId) {
        return shouldIgnoreAir(structureId, null);
    }

    /**
     * Ignore template air only for positively classified surface structures.
     * Underground / unknown / blank ids keep air (fail open for cavities).
     */
    public boolean shouldIgnoreAir(@Nullable String structureId, @Nullable World world) {
        if (!surfaceIgnoreAir) {
            return false;
        }
        if (structureId == null || structureId.isBlank()) {
            // Unknown id → keep air. Never fill datapack cavities by accident.
            return false;
        }
        if (matchesKeepAir(structureId)) {
            return false;
        }
        AirPlacement kind = StructurePlacementBridge.classifyAirPlacement(world, structureId);
        return kind == AirPlacement.SURFACE;
    }

    /** Public for unit tests — exact id, path, or {@code :path} suffix. */
    public boolean matchesKeepAir(@NotNull String structureId) {
        String lower = structureId.trim().toLowerCase(Locale.ROOT);
        String path = pathOf(lower);
        for (String raw : keepAirIds) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String keep = raw.trim().toLowerCase(Locale.ROOT);
            String keepPath = pathOf(keep);
            if (lower.equals(keep) || path.equals(keep) || path.equals(keepPath)
                    || lower.endsWith(":" + keepPath)) {
                return true;
            }
        }
        return false;
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
