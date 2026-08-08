package com.embedize.terrain;

import org.bukkit.util.noise.SimplexOctaveGenerator;

/**
 * Nether shape + climate (Incendium / Amplified Nether inspired, clean-room).
 * Multi-shelf floors, cheese caverns, and tunnel/web carve.
 */
final class NetherNoiseModel {

    static final int LAVA_LEVEL = 31;
    static final int DEFAULT_FLOOR = 32;
    static final int DEFAULT_CEILING = 176;

    private final SimplexOctaveGenerator floor;
    private final SimplexOctaveGenerator ceiling;
    private final SimplexOctaveGenerator pillars;
    private final SimplexOctaveGenerator climateA;
    private final SimplexOctaveGenerator climateB;
    private final SimplexOctaveGenerator climateC;
    private final SimplexOctaveGenerator detail;
    private final SimplexOctaveGenerator cheese;
    private final SimplexOctaveGenerator terrace;
    private final SimplexOctaveGenerator tunnels;
    private final SimplexOctaveGenerator webs;

    NetherNoiseModel(long seed) {
        floor = octave(seed, 4, 1.0 / 140.0);
        ceiling = octave(seed + 41L, 3, 1.0 / 160.0);
        pillars = octave(seed + 83L, 2, 1.0 / 36.0);
        climateA = octave(seed + 173L, 2, 1.0 / 280.0);
        climateB = octave(seed + 251L, 2, 1.0 / 240.0);
        climateC = octave(seed + 307L, 2, 1.0 / 320.0);
        detail = octave(seed + 331L, 2, 1.0 / 48.0);
        cheese = octave(seed + 419L, 2, 0.03);
        terrace = octave(seed + 521L, 2, 1.0 / 90.0);
        tunnels = octave(seed + 601L, 2, 0.018);
        webs = octave(seed + 677L, 2, 0.04);
    }

    private static SimplexOctaveGenerator octave(long seed, int octaves, double scale) {
        SimplexOctaveGenerator g = new SimplexOctaveGenerator(seed, octaves);
        g.setScale(scale);
        return g;
    }

    Sample sample(int x, int z) {
        double f = floor.noise(x, z, 0.5, 0.5, true);
        double c = ceiling.noise(x, z, 0.5, 0.5, true);
        double p = pillars.noise(x, z, 0.5, 0.5, true);
        double a = climateA.noise(x, z, 0.5, 0.5, true);
        double b = climateB.noise(x, z, 0.5, 0.5, true);
        double d3 = climateC.noise(x, z, 0.5, 0.5, true);
        double d = detail.noise(x, z, 0.5, 0.5, true);
        double terr = terrace.noise(x, z, 0.5, 0.5, true);

        int floorY = (int) Math.round(DEFAULT_FLOOR + f * 16.0 + d * 3.0 + terr * 6.0);
        int ceilingY = (int) Math.round(DEFAULT_CEILING + c * 22.0 - d * 3.0);

        // Incendium-like climate shelves (multiple terrace lifts)
        if (a > 0.35 && b < 0.0) {
            floorY += 14;
        }
        if (a > 0.55 && d3 > 0.2) {
            floorY += 16; // secondary high shelf
        }
        if (b > 0.45 && a < 0.1) {
            floorY += 8; // warped/weeping mid shelf
        }
        if (a < -0.45) {
            floorY = Math.min(floorY, LAVA_LEVEL + 1);
        }
        // Toxic/ash lowlands
        if (d3 < -0.4 && a < 0.0) {
            floorY = Math.min(floorY, LAVA_LEVEL + 6);
            ceilingY = Math.min(ceilingY, 140);
        }

        floorY = Math.max(4, Math.min(96, floorY));
        ceilingY = Math.max(floorY + 36, Math.min(220, ceilingY));

        boolean lavaSea = floorY <= LAVA_LEVEL + 3 && (a < -0.2 || b < -0.35);
        boolean pillar = p > 0.58 && Math.abs(b) < 0.6;
        boolean volcanic = a < -0.15 && b > 0.4;

        return new Sample(floorY, ceilingY, lavaSea, pillar, volcanic, a, b, d3, p);
    }

    /**
     * Cheese / tunnel / web carve. Values &lt;= -0.28 mean air in mid-volume.
     */
    double cheeseAt(int x, int y, int z) {
        double cheeseV = cheese.noise(x * 0.5, y, z * 0.5, 0.5, 0.5, true);
        double tunnelV = tunnels.noise(x, y * 0.6, z, 0.5, 0.5, true);
        double webV = webs.noise(x * 0.7, y * 0.5, z * 0.7, 0.5, 0.5, true);
        // Swiss cheese primary; tunnels deepen corridors; webs cut thin sheets
        double combined = cheeseV;
        if (tunnelV < -0.35) {
            combined = Math.min(combined, tunnelV + 0.05);
        }
        if (webV < -0.55 && Math.abs(tunnelV) < 0.25) {
            combined = Math.min(combined, webV + 0.15);
        }
        return combined;
    }

    record Sample(
            int floorY,
            int ceilingY,
            boolean lavaSea,
            boolean pillar,
            boolean volcanic,
            double climateA,
            double climateB,
            double climateC,
            double pillarNoise
    ) {
    }
}
