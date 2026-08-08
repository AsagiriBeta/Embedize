package com.embedize.structure;

import org.jetbrains.annotations.NotNull;

/**
 * Post-noise stand-in for vanilla {@code terrain_adaptation: beard_box}.
 * <p>
 * Vanilla clears streets / plazas between ancient-city pieces during
 * {@code NoiseBasedChunkGenerator#fillFromNoise} via {@code Beardifier}, which
 * subtracts density inside each <em>piece</em> bounding box (with a soft kernel).
 * Paper {@code CustomChunkGenerator} with {@code shouldGenerateNoise=false}
 * never runs that pass.
 * <p>
 * Embedize offers two approximations (both write only the current populate chunk
 * via {@code WorldGenLevel} — never Bukkit {@code getType} / syncLoad):
 * <ul>
 *   <li>{@code structures.density-adapt} (default true) — column-wise density
 *       falloff: lower solid fill inside piece BB + soft shell, closer in spirit
 *       to Beardifier without an O(piece×kernel³) neighbour scan.</li>
 *   <li>{@code structures.soft-beard} (default false) — heavier per-voxel carve;
 *       kept as opt-in polish when density-adapt is off.</li>
 * </ul>
 * Template AIR still clears building interiors; these only open the inter-piece
 * volumes beard_box would have excavated.
 * <p>
 * <strong>Not 1:1 with vanilla:</strong> no continuous density router, no
 * cross-chunk Beardifier contribution from unloaded neighbours, streets may be
 * slightly tighter or harder-edged. Prefer uptime over pixel parity.
 */
public final class SoftBeardAdaptation {

    /** Matches vanilla {@code Beardifier.BEARD_KERNEL_RADIUS} spirit; keep modest. */
    public static final int KERNEL_RADIUS = 6;

    /**
     * Minimum density factor (0..1) to clear a solid block under density-adapt.
     * Inside the piece box factor is 1; at kernel edge it approaches 0.
     */
    public static final double DENSITY_CLEAR_THRESHOLD = 0.35;

    private SoftBeardAdaptation() {
    }

    /**
     * Whether a world block should be cleared to cave_air for soft beard_box.
     *
     * @param kernel soft shell outside the piece AABB (0 = hard piece box only)
     */
    public static boolean shouldCarve(
            int x,
            int y,
            int z,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            int kernel
    ) {
        int dx = distOutside(x, minX, maxX);
        int dy = distOutside(y, minY, maxY);
        int dz = distOutside(z, minZ, maxZ);
        if (dx == 0 && dy == 0 && dz == 0) {
            return true;
        }
        int k = Math.max(0, kernel);
        if (k == 0) {
            return false;
        }
        // Euclidean distance outside the box ≈ Beardifier kernel falloff region.
        double dist = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
        return dist <= k;
    }

    /**
     * Beardifier-like contribution in {@code [0,1]}: 1 inside the piece AABB,
     * linear Euclidean falloff through the soft kernel, 0 beyond.
     */
    public static double densityFactor(
            int x,
            int y,
            int z,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            int kernel
    ) {
        int dx = distOutside(x, minX, maxX);
        int dy = distOutside(y, minY, maxY);
        int dz = distOutside(z, minZ, maxZ);
        if (dx == 0 && dy == 0 && dz == 0) {
            return 1.0;
        }
        int k = Math.max(0, kernel);
        if (k == 0) {
            return 0.0;
        }
        double dist = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
        if (dist >= k) {
            return 0.0;
        }
        return 1.0 - (dist / (double) k);
    }

    /** Clear when {@link #densityFactor} meets {@link #DENSITY_CLEAR_THRESHOLD}. */
    public static boolean shouldClearByDensity(
            int x,
            int y,
            int z,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            int kernel
    ) {
        return densityFactor(x, y, z, minX, minY, minZ, maxX, maxY, maxZ, kernel)
                >= DENSITY_CLEAR_THRESHOLD;
    }

    /**
     * Cheap XZ gate for column-wise passes: skip entire columns outside the
     * piece box + kernel in the horizontal plane (no Y work, no block reads).
     */
    public static boolean influencesColumn(
            int x,
            int z,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int kernel
    ) {
        int dx = distOutside(x, minX, maxX);
        int dz = distOutside(z, minZ, maxZ);
        int k = Math.max(0, kernel);
        if (dx == 0 && dz == 0) {
            return true;
        }
        if (k == 0) {
            return false;
        }
        return Math.sqrt((double) dx * dx + (double) dz * dz) <= k;
    }

    /** Chebyshev outside distance component along one axis (0 if inside). */
    public static int distOutside(int v, int min, int max) {
        if (v < min) {
            return min - v;
        }
        if (v > max) {
            return v - max;
        }
        return 0;
    }

    /**
     * Manual {@code /embedize place} stand-in when piece boxes are unknown:
     * soft ellipsoid around origin (not a hard cube).
     */
    public static boolean shouldCarveEllipsoid(
            int x,
            int y,
            int z,
            int originX,
            int originY,
            int originZ,
            int radiusXZ,
            int radiusY
    ) {
        int rx = Math.max(1, radiusXZ);
        int ry = Math.max(1, radiusY);
        double nx = (x - originX) / (double) rx;
        double ny = (y - originY) / (double) ry;
        double nz = (z - originZ) / (double) rx;
        return (nx * nx + ny * ny + nz * nz) <= 1.0;
    }

    public static @NotNull String describe() {
        return "soft beard_box stand-in: per-piece AABB + kernel=" + KERNEL_RADIUS
                + " (not StructureStart hull)";
    }

    public static @NotNull String describeDensity() {
        return "density-adapt column stand-in: per-piece AABB + kernel=" + KERNEL_RADIUS
                + " threshold=" + DENSITY_CLEAR_THRESHOLD
                + " (not vanilla Beardifier / not StructureStart hull)";
    }
}
