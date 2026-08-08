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
 * This helper carves <strong>per piece box + soft shell</strong>, not the
 * StructureStart AABB hull (that produces the fake floating cavern).
 * Template AIR still clears building interiors; this only opens the
 * inter-piece volumes beard_box would have excavated.
 */
public final class SoftBeardAdaptation {

    /** Matches vanilla {@code Beardifier.BEARD_KERNEL_RADIUS} spirit; keep modest. */
    public static final int KERNEL_RADIUS = 6;

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
}
