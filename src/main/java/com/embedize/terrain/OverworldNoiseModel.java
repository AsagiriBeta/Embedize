package com.embedize.terrain;

import org.bukkit.util.noise.SimplexOctaveGenerator;

/**
 * Overworld climate/height model.
 * <p>
 * Clean-room recreation inspired by Tectonic continental splines + Terralith
 * ridge/peak shaping (not a datapack port). Heights are continuous — mountain
 * and peak weights fade over long foothills so biome edges never become 90° walls.
 */
final class OverworldNoiseModel {

    static final int SEA_LEVEL = 63;
    /** Climate grid for temperature/humidity only (not mountain masks). */
    private static final int CLIMATE_STEP = 48;
    /** Neighbor distance for slope softener (blocks). */
    private static final int SLOPE_RADIUS = 28;
    /** Soft max |ΔY| vs neighborhood average before pulling back. */
    private static final double SLOPE_SOFT_DELTA = 12.0;

    private final SimplexOctaveGenerator continents;
    private final SimplexOctaveGenerator erosion;
    private final SimplexOctaveGenerator ridges;
    private final SimplexOctaveGenerator peaks;
    private final SimplexOctaveGenerator detail;
    private final SimplexOctaveGenerator temperature;
    private final SimplexOctaveGenerator humidity;
    private final SimplexOctaveGenerator mountainous;
    private final SimplexOctaveGenerator oceanic;
    private final SimplexOctaveGenerator weirdness;
    private final SimplexOctaveGenerator jagged;
    private final SimplexOctaveGenerator dune;
    private final SimplexOctaveGenerator meander;
    private final SimplexOctaveGenerator patches;
    private final SimplexOctaveGenerator caveCheese;
    private final SimplexOctaveGenerator caveNoodle;
    /** Large-scale sparse pockets for deep_dark (ancient cities). */
    private final SimplexOctaveGenerator deepDark;

    OverworldNoiseModel(long seed) {
        continents = octave(seed, 6, 1.0 / 5200.0);
        erosion = octave(seed + 31L, 5, 1.0 / 2800.0);
        ridges = octave(seed + 71L, 4, 1.0 / 640.0);
        peaks = octave(seed + 97L, 3, 1.0 / 220.0);
        detail = octave(seed + 131L, 2, 1.0 / 96.0);
        temperature = octave(seed + 307L, 2, 1.0 / 920.0);
        humidity = octave(seed + 401L, 2, 1.0 / 840.0);
        // Broader mountain domains so alpine massifs fade into foothills.
        mountainous = octave(seed + 503L, 3, 1.0 / 520.0);
        oceanic = octave(seed + 607L, 2, 1.0 / 300.0);
        weirdness = octave(seed + 709L, 2, 1.0 / 400.0);
        jagged = octave(seed + 811L, 3, 1.0 / 140.0);
        dune = octave(seed + 913L, 2, 1.0 / 64.0);
        meander = octave(seed + 1019L, 2, 1.0 / 240.0);
        patches = octave(seed + 1123L, 3, 1.0 / 40.0);
        caveCheese = octave(seed + 1229L, 3, 1.0 / 96.0);
        caveNoodle = octave(seed + 1331L, 2, 1.0 / 28.0);
        // ~1.8k block scale → sparse deep_dark domains under continental land.
        deepDark = octave(seed + 1433L, 2, 1.0 / 1800.0);
    }

    /** Climate weirdness in [-1,1] (rivers / rare surface variants share this field). */
    double weirdnessAt(int x, int z) {
        return weirdness.noise(x, z, 0.5, 0.5, true);
    }

    /**
     * Sparse deep_dark affinity in [-1,1]. High values under continental land + deep Y
     * become {@code minecraft:deep_dark} (see {@link OverworldBiomeProvider}).
     */
    double deepDarkAffinity(int x, int z) {
        return deepDark.noise(x, z, 0.5, 0.5, true);
    }

    /** Smooth [-1,1] mix for seafloor / riverbed material blobs (not hash stripes). */
    double surfacePatch(int x, int z) {
        return patches.noise(x, z, 0.5, 0.5, true);
    }

    /**
     * Extra cheese/noodle caves (vanilla carvers + this). Never opens the last few surface blocks.
     */
    boolean isCaveAir(int x, int y, int z, int surfaceY) {
        if (y <= -54 || y >= surfaceY - 3 || surfaceY < SEA_LEVEL - 2) {
            return false;
        }
        double cy = y * 0.65;
        double cheese = caveCheese.noise(x, cy, z, 0.5, 0.5, true);
        double noodle = caveNoodle.noise(x, cy, z, 0.5, 0.5, true);
        if (cheese > 0.58 && y < surfaceY - 14 && y < SEA_LEVEL + 20) {
            return true;
        }
        return Math.abs(noodle) < 0.075 && cheese > -0.15 && y < surfaceY - 6;
    }

    private static SimplexOctaveGenerator octave(long seed, int octaves, double scale) {
        SimplexOctaveGenerator g = new SimplexOctaveGenerator(seed, octaves);
        g.setScale(scale);
        return g;
    }

    Sample sample(int x, int z) {
        ColumnProfile center = profile(x, z);
        double height = softenSlope(x, z, center);
        // Low-pass softener lifts basins toward neighboring hills — push swamps back down.
        if (center.swampFactor > 0.05) {
            double basin = clamp(
                    SEA_LEVEL + lerp(2.0, -1.2, center.swampFactor),
                    SEA_LEVEL - 2.0,
                    SEA_LEVEL + 3.5
            );
            height = lerp(height, Math.min(height, basin), Math.min(1.0, center.swampFactor * 1.25));
            height = Math.min(height, SEA_LEVEL + lerp(5.0, 2.5, center.swampFactor));
        }
        // Any humid non-alpine lowland stays near the water table (mangrove/swamp contract).
        if (center.humidity > 0.40 && center.land > 0.50
                && center.mountainWeight < 0.30 && center.peakWeight < 0.15) {
            height = Math.min(height, SEA_LEVEL + 5.0);
        }
        int surfaceY = (int) Math.round(height);
        surfaceY = Math.max(-54, Math.min(320, surfaceY));
        return new Sample(
                center.continentalness, center.erosion, center.ridges,
                center.temperature, center.humidity, center.land,
                center.river, center.riverDepth, surfaceY,
                center.mountainWeight > 0.32, center.peakWeight > 0.42, center.oceanTrench,
                center.mountainWeight, center.peakWeight
        );
    }

    /**
     * Multi-scale low-pass of raw column height. Linear filtering keeps the height field
     * Lipschitz-ish so adjacent columns cannot form 90° sheet cliffs, while peak cores
     * still retain enough raw energy to read as sharp summits.
     */
    private double softenSlope(int x, int z, ColumnProfile center) {
        double raw = center.height;
        double peakCore = center.peakWeight * center.peakWeight;
        double a6 = neighborhoodAvg(x, z, 6);
        double a14 = neighborhoodAvg(x, z, 14);
        double a28 = neighborhoodAvg(x, z, SLOPE_RADIUS);
        double a56 = neighborhoodAvg(x, z, SLOPE_RADIUS * 2);

        double wRaw = lerp(0.08, 0.18, peakCore);
        double w6 = 0.38;
        double w14 = 0.28;
        double w28 = 0.14;
        double w56 = 1.0 - wRaw - w6 - w14 - w28;
        double h = raw * wRaw + a6 * w6 + a14 * w14 + a28 * w28 + a56 * w56;
        if (center.river) {
            // Keep the wet channel after low-pass (rivers are lowland-only by now).
            h = Math.min(h, SEA_LEVEL - 2.5);
            h = Math.max(h, SEA_LEVEL - 5.5);
        }
        // Swamp columns: prefer raw low basin over neighborhood hill average.
        if (center.swampFactor > 0.08) {
            h = lerp(h, Math.min(h, raw), center.swampFactor * 0.80);
        }
        return h;
    }

    private double neighborhoodAvg(int x, int z, int radius) {
        return (
                profile(x + radius, z).height
                        + profile(x - radius, z).height
                        + profile(x, z + radius).height
                        + profile(x, z - radius).height
        ) * 0.25;
    }

    private ColumnProfile profile(int x, int z) {
        int sx = Math.floorDiv(x, CLIMATE_STEP) * CLIMATE_STEP;
        int sz = Math.floorDiv(z, CLIMATE_STEP) * CLIMATE_STEP;
        double tx = (x - sx) / (double) CLIMATE_STEP;
        double tz = (z - sz) / (double) CLIMATE_STEP;

        double c0 = continents.noise(x, z, 0.5, 0.5, true);
        double e0 = erosion.noise(x, z, 0.5, 0.5, true);
        double ridgeN = ridges.noise(x, z, 0.5, 0.5, true);
        double rAbs = Math.abs(ridgeN);
        double peakN = peaks.noise(x, z, 0.5, 0.5, true);
        double detailN = detail.noise(x, z, 0.5, 0.5, true);
        double weird = weirdness.noise(x, z, 0.5, 0.5, true);
        // Soften jagged field slightly so needles aren't one-column wide.
        double jag = (
                jagged.noise(x, z, 0.5, 0.5, true) * 2.0
                        + jagged.noise(x + 5, z, 0.5, 0.5, true)
                        + jagged.noise(x - 5, z, 0.5, 0.5, true)
                        + jagged.noise(x, z + 5, 0.5, 0.5, true)
                        + jagged.noise(x, z - 5, 0.5, 0.5, true)
        ) / 6.0;
        double duneN = dune.noise(x, z, 0.5, 0.5, true);

        double t = sampleClimateBilinear(temperature, sx, sz, tx, tz);
        double h = sampleClimateBilinear(humidity, sx, sz, tx, tz);
        // Mountain/ocean masks: full-res + light spatial blur (never 64-block steps).
        double mountainMask = blurredMountainField(x, z) * 50.0;
        double oceanMask = blurredOceanField(x, z) * 50.0;

        double oceanFade = smoothstep(12.0, 26.0, oceanMask);
        double c = c0 - 0.10 - 0.22 * oceanFade;
        double land = continentalLand(c) * lerp(1.0, 0.30, oceanFade);

        double base = continentalBaseHeight(c);
        base += detailN * 4.0 + weird * 1.8;

        // Wide foothill fade; peakWeight stays punchier so cores can tower.
        double mountainWeight = smoothstep(-10.0, 20.0, mountainMask)
                * smoothstep(0.48, 0.82, land);
        // Hermite only on mountain domain → long soft foothills.
        mountainWeight = mountainWeight * mountainWeight * (3.0 - 2.0 * mountainWeight);
        double peakWeight = smoothstep(-2.0, 26.0, mountainMask)
                * smoothstep(0.52, 0.88, land)
                * smoothstep(0.12, 0.72, mountainWeight);

        // Hills / mountains / true jagged cores — continuous ladder, no boolean cliffs.
        double hillBoost = ((1.0 - Math.abs(e0)) * 10.0 + rAbs * 6.0 + detailN * 3.5)
                * smoothstep(0.50, 0.74, land);

        double ridgeBoost = rAbs * lerp(16.0, 48.0, peakWeight)
                + Math.max(0.0, peakN) * lerp(12.0, 56.0, peakWeight)
                + smoothstep(0.25, -0.55, e0) * lerp(12.0, 46.0, mountainWeight);

        // Sharp peak cores: sparse high spikes, only where peakWeight is already high.
        double jag01 = Math.max(0.0, jag);
        double peakCore = peakWeight * peakWeight;
        double jaggedSpike = Math.pow(jag01, 1.25) * peakCore * lerp(70.0, 160.0, smoothstep(0.25, -0.55, e0));
        // Secondary needle from ridge zero-crossings (razor ridges).
        double razor = Math.pow(Math.max(0.0, 1.0 - Math.abs(ridgeN) * 1.8), 2.8)
                * peakCore * 64.0 * smoothstep(0.15, -0.45, e0);

        double mountainBoost = lerp(hillBoost, ridgeBoost + jaggedSpike * 0.50 + razor * 0.35, mountainWeight);
        mountainBoost += jaggedSpike + razor;
        // Soft coastal kill — long ramp, never a hard cliff at land≈0.5.
        mountainBoost *= lerp(0.05, 1.0, smoothstep(0.48, 0.80, land));

        // Humid basins must stay low & flat — kill alpine/hill energy before it stacks.
        double swampFactor = smoothstep(0.22, 0.50, h)
                * (1.0 - smoothstep(0.08, 0.38, mountainWeight))
                * (1.0 - smoothstep(0.05, 0.28, peakWeight))
                * smoothstep(0.50, 0.74, land)
                * smoothstep(-0.20, 0.55, e0);
        mountainBoost *= 1.0 - swampFactor * 0.98;

        double height = base + land * mountainBoost;
        // Extra alpine pedestal so peak biomes sit well above foothill forests.
        height += (peakWeight * 28.0 + peakCore * lerp(24.0, 70.0, smoothstep(0.2, -0.5, e0)))
                * (1.0 - swampFactor);

        // Desert dune ripples — warm dry inland only
        if (land > 0.62 && t > 0.4 && h < -0.2 && e0 > -0.1) {
            height += Math.abs(duneN) * 5.5;
        }

        // Coastal continuum
        double shelf = lerp(8.0, SEA_LEVEL - 12.0, smoothstep(0.02, 0.42, land));
        double beach = lerp(SEA_LEVEL - 2.0, SEA_LEVEL + 1.0, smoothstep(0.42, 0.48, land));
        double rise = lerp(SEA_LEVEL + 3.0, 78.0, smoothstep(0.48, 0.70, land));
        double coastalRamp;
        if (land < 0.42) {
            coastalRamp = shelf;
        } else if (land < 0.48) {
            coastalRamp = lerp(shelf, beach, smoothstep(0.42, 0.48, land));
        } else {
            coastalRamp = lerp(beach, rise, smoothstep(0.48, 0.70, land));
        }
        // Coastal continuum must not crush inland alpine massifs (old mix left Y≈78 plateaus).
        if (land < 0.72) {
            double landMix = smoothstep(0.48, 0.72, land);
            landMix = Math.max(landMix, Math.max(mountainWeight, peakWeight));
            height = lerp(coastalRamp, height, landMix);
        }
        if (land >= 0.48 && height >= SEA_LEVEL - 1.0 && height < SEA_LEVEL) {
            height = SEA_LEVEL;
        }
        double shoreSoft = (1.0 - smoothstep(0.42, 0.70, land))
                * (1.0 - Math.max(mountainWeight, peakWeight));
        if (shoreSoft > 0.0 && height > SEA_LEVEL) {
            double shoreCap = SEA_LEVEL + lerp(4.0, 52.0, smoothstep(0.42, 0.70, land));
            height = lerp(height, Math.min(height, shoreCap), shoreSoft * 0.90);
        }

        // Swamp / mangrove basins: low, flat, slightly sunken — never hillside cliffs at Y90.
        if (swampFactor > 0.05) {
            double bowl = patches.noise(x * 0.28, z * 0.28, 0.5, 0.5, true);
            double basin = SEA_LEVEL + lerp(2.0, -1.5, swampFactor) + bowl * 1.0 + detailN * 0.35;
            basin = clamp(basin, SEA_LEVEL - 2.0, SEA_LEVEL + 3.0);
            height = lerp(height, basin, Math.min(1.0, swampFactor * 1.15));
            height = lerp(height, SEA_LEVEL + 0.6, swampFactor * 0.50);
            height = Math.min(height, SEA_LEVEL + lerp(5.5, 2.5, swampFactor));
        }

        // Far-ocean trench
        double meanderN = meander.noise(x, z, 0.5, 0.5, true);
        double trenchField = weird + meanderN * 0.35;
        double trenchBand = 1.0 - Math.min(1.0, Math.abs(trenchField) / 0.18);
        double trenchOceanMask = smoothstep(-0.48, -0.75, c) * (1.0 - smoothstep(0.12, 0.22, land));
        double trenchStrength = Math.max(0.0, trenchBand) * trenchBand * trenchOceanMask;
        if (trenchStrength > 0.08) {
            double bed = lerp(SEA_LEVEL - 26.0, -38.0, smoothstep(0.08, 0.95, trenchStrength));
            double blend = smoothstep(0.08, 0.88, trenchStrength);
            height = lerp(height, Math.min(height, bed), blend);
        }
        boolean oceanTrench = trenchStrength > 0.40 && c < -0.55 && land < 0.18;

        if (!oceanTrench && land < 0.28 && height < SEA_LEVEL - 8 && c < -0.30) {
            double patch = patches.noise(x, z, 0.5, 0.5, true);
            height += patch * 3.0 + weird * 1.2;
            height = Math.min(height, SEA_LEVEL - 18.0);
        }

        // Rivers only on lowland/foothill — never carve sea-level channels into alpine massifs
        // (that produced 30+ block walls of jagged_peaks against a river column).
        double riverField = weird + meanderN * 0.28 + detailN * 0.05;
        double valleyBand = 1.0 - Math.min(1.0, Math.abs(riverField) / 0.40);
        double riverBand = 1.0 - Math.min(1.0, Math.abs(riverField) / 0.15);
        double riverFactor = Math.max(0.0, riverBand);
        double riverDepth = riverFactor * riverFactor * 4.0;
        double bedWiggle = patches.noise(x * 0.7, z * 0.7, 0.5, 0.5, true) * 1.4;
        double riverAmp = (1.0 - smoothstep(0.18, 0.50, peakWeight))
                * (1.0 - smoothstep(0.32, 0.70, mountainWeight));
        boolean riverCandidate = land > 0.50 && riverFactor > 0.55 && e0 > -0.40 && riverAmp > 0.50;
        if (land > 0.50 && valleyBand > 0.04 && e0 > -0.40 && riverAmp > 0.12) {
            double bedCenter = SEA_LEVEL - 3.0 - riverFactor * 1.4 + bedWiggle;
            bedCenter = clamp(bedCenter, SEA_LEVEL - 5.5, SEA_LEVEL - 2.5);
            double terrace = SEA_LEVEL + lerp(22.0, 2.5, smoothstep(0.05, 0.88, valleyBand));
            terrace = lerp(terrace, terrace + mountainWeight * 28.0, mountainWeight * 0.45);
            double valleyPull = smoothstep(0.04, 0.70, valleyBand);
            valleyPull = valleyPull * valleyPull * (3.0 - 2.0 * valleyPull) * riverAmp;
            height = lerp(height, Math.min(height, terrace), valleyPull);
            double wetBlend = smoothstep(0.20, 0.95, riverBand);
            wetBlend = wetBlend * wetBlend * (3.0 - 2.0 * wetBlend) * riverAmp;
            if (wetBlend > 0.0) {
                height = lerp(height, Math.min(height, bedCenter), wetBlend);
            }
            if (riverBand > 0.20 && mountainWeight < 0.35) {
                double bankCap = SEA_LEVEL + lerp(22.0, 4.0, smoothstep(0.20, 0.90, riverBand));
                double bankPull = (1.0 - mountainWeight) * smoothstep(0.20, 0.85, riverBand);
                height = lerp(height, Math.min(height, bankCap), bankPull);
            }
        }
        if (riverCandidate) {
            height = Math.min(height, SEA_LEVEL - 2.5);
            height = Math.max(height, SEA_LEVEL - 5.5);
            oceanTrench = false;
        }

        boolean trueOcean = land < 0.46 && c < -0.20;
        if (!trueOcean && !riverCandidate && height < SEA_LEVEL && land >= 0.48) {
            if (height > SEA_LEVEL - 3.5) {
                height = SEA_LEVEL;
            } else {
                height = Math.max(height, SEA_LEVEL - 5.0);
            }
        }

        return new ColumnProfile(
                c, e0, rAbs, t, h, land, riverCandidate, riverDepth, height,
                oceanTrench, mountainWeight, peakWeight, swampFactor
        );
    }

    /** Light spatial blur — keeps massifs continuous without flattening peak cores. */
    private double blurredMountainField(int x, int z) {
        double m0 = mountainous.noise(x, z, 0.5, 0.5, true);
        double m1 = mountainous.noise(x + 48, z, 0.5, 0.5, true);
        double m2 = mountainous.noise(x - 48, z, 0.5, 0.5, true);
        double m3 = mountainous.noise(x, z + 48, 0.5, 0.5, true);
        double m4 = mountainous.noise(x, z - 48, 0.5, 0.5, true);
        return (m0 * 4.0 + m1 + m2 + m3 + m4) / 8.0;
    }

    private double blurredOceanField(int x, int z) {
        double o0 = oceanic.noise(x, z, 0.5, 0.5, true);
        double o1 = oceanic.noise(x + 48, z, 0.5, 0.5, true);
        double o2 = oceanic.noise(x - 48, z, 0.5, 0.5, true);
        double o3 = oceanic.noise(x, z + 48, 0.5, 0.5, true);
        double o4 = oceanic.noise(x, z - 48, 0.5, 0.5, true);
        return (o0 * 2.0 + o1 + o2 + o3 + o4) / 6.0;
    }

    /** Ocean biome / deep seafloor only when continentalness is actually oceanic. */
    static boolean isTrueOcean(Sample sample) {
        return sample != null
                && !sample.river()
                && sample.land() < 0.46
                && sample.continentalness() < -0.20
                && sample.surfaceY() < SEA_LEVEL;
    }

    /** Freshwater basin inland (lake) — water column without ocean biomes. */
    static boolean isInlandLake(Sample sample) {
        return sample != null
                && !sample.river()
                && !isTrueOcean(sample)
                && sample.surfaceY() < SEA_LEVEL
                && sample.land() >= 0.46;
    }

    private static double continentalLand(double c) {
        if (c < -0.45) {
            return lerp(0.0, 0.12, smoothstep(-0.85, -0.45, c));
        }
        if (c < -0.35) {
            return lerp(0.12, 0.28, smoothstep(-0.45, -0.35, c));
        }
        if (c < -0.195) {
            return lerp(0.28, 0.52, smoothstep(-0.35, -0.195, c));
        }
        if (c < 0.05) {
            return lerp(0.52, 0.78, smoothstep(-0.195, 0.05, c));
        }
        return lerp(0.78, 1.0, smoothstep(0.05, 0.55, c));
    }

    private static double continentalBaseHeight(double c) {
        if (c < -0.45) {
            return lerp(-28.0, 8.0, smoothstep(-0.9, -0.45, c));
        }
        if (c < -0.35) {
            return lerp(8.0, 32.0, smoothstep(-0.45, -0.35, c));
        }
        if (c < -0.195) {
            return lerp(32.0, 58.0, smoothstep(-0.35, -0.195, c));
        }
        if (c < 0.05) {
            return lerp(58.0, 70.0, smoothstep(-0.195, 0.05, c));
        }
        if (c < 0.35) {
            return lerp(70.0, 88.0, smoothstep(0.05, 0.35, c));
        }
        return lerp(88.0, 102.0, smoothstep(0.35, 0.75, c));
    }

    private static double sampleClimateBilinear(
            SimplexOctaveGenerator noise,
            int sx,
            int sz,
            double tx,
            double tz
    ) {
        double v00 = noise.noise(sx, sz, 0.5, 0.5, true);
        double v10 = noise.noise(sx + CLIMATE_STEP, sz, 0.5, 0.5, true);
        double v01 = noise.noise(sx, sz + CLIMATE_STEP, 0.5, 0.5, true);
        double v11 = noise.noise(sx + CLIMATE_STEP, sz + CLIMATE_STEP, 0.5, 0.5, true);
        return bilinear(v00, v10, v01, v11, tx, tz);
    }

    private static double bilinear(double v00, double v10, double v01, double v11, double tx, double tz) {
        return lerp(lerp(v00, v10, tx), lerp(v01, v11, tx), tz);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp(t, 0.0, 1.0);
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private record ColumnProfile(
            double continentalness,
            double erosion,
            double ridges,
            double temperature,
            double humidity,
            double land,
            boolean river,
            double riverDepth,
            double height,
            boolean oceanTrench,
            double mountainWeight,
            double peakWeight,
            double swampFactor
    ) {
    }

    record Sample(
            double continentalness,
            double erosion,
            double ridges,
            double temperature,
            double humidity,
            double land,
            boolean river,
            double riverDepth,
            int surfaceY,
            boolean mountainous,
            boolean peaks,
            boolean oceanTrench,
            double mountainWeight,
            double peakWeight
    ) {
        /** Backward-compatible ctor used by older call sites / tests. */
        Sample(
                double continentalness,
                double erosion,
                double ridges,
                double temperature,
                double humidity,
                double land,
                boolean river,
                double riverDepth,
                int surfaceY,
                boolean mountainous,
                boolean peaks,
                boolean oceanTrench
        ) {
            this(continentalness, erosion, ridges, temperature, humidity, land, river, riverDepth,
                    surfaceY, mountainous, peaks, oceanTrench,
                    mountainous ? 0.7 : 0.0, peaks ? 0.8 : 0.0);
        }
    }
}
