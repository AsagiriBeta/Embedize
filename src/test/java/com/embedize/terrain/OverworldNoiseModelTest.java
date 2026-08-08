package com.embedize.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverworldNoiseModelTest {

    @Test
    void samplingIsDeterministicForWorldCoordinates() {
        OverworldNoiseModel first = new OverworldNoiseModel(987654321L);
        OverworldNoiseModel second = new OverworldNoiseModel(987654321L);

        for (int x = -256; x <= 256; x += 17) {
            for (int z = -256; z <= 256; z += 19) {
                assertEquals(first.sample(x, z), second.sample(x, z));
            }
        }
    }

    @Test
    void chunkBoundariesDoNotIntroduceHeightSeams() {
        for (long seed : new long[]{0L, 42L, -9918273L}) {
            OverworldNoiseModel model = new OverworldNoiseModel(seed);
            int largestStep = 0;
            for (int chunk = -16; chunk <= 16; chunk++) {
                int boundary = chunk << 4;
                for (int offset = -256; offset <= 256; offset += 8) {
                    largestStep = Math.max(largestStep, Math.abs(
                            model.sample(boundary - 1, offset).surfaceY()
                                    - model.sample(boundary, offset).surfaceY()));
                    largestStep = Math.max(largestStep, Math.abs(
                            model.sample(offset, boundary - 1).surfaceY()
                                    - model.sample(offset, boundary).surfaceY()));
                }
            }
            // Soft valleys / ridge needles may step a bit; forbid sheet canyon walls.
            assertTrue(largestStep <= 18, "seed=" + seed + ", largestStep=" + largestStep);
        }
    }

    @Test
    void alpineTransitionsStaySlopedNotSheerWalls() {
        for (long seed : new long[]{42L, 987654321L, -17L}) {
            OverworldNoiseModel model = new OverworldNoiseModel(seed);
            int alpineSteps = 0;
            int harshAlpine = 0;
            int tallPeaks = 0;
            for (int x = -3000; x <= 3000; x += 11) {
                for (int z = -3000; z <= 3000; z += 13) {
                    OverworldNoiseModel.Sample s = model.sample(x, z);
                    if (s.surfaceY() >= 170 || s.peakWeight() > 0.35) {
                        tallPeaks++;
                    }
                    if (s.mountainWeight() < 0.15 && s.peakWeight() < 0.10) {
                        continue;
                    }
                    OverworldNoiseModel.Sample e = model.sample(x + 1, z);
                    int step = Math.abs(e.surfaceY() - s.surfaceY());
                    alpineSteps++;
                    if (step > 12) {
                        harshAlpine++;
                    }
                }
            }
            assertTrue(tallPeaks > 20, "seed=" + seed + " expected tall/peak samples, got " + tallPeaks);
            assertTrue(alpineSteps > 50, "seed=" + seed + " expected alpine samples");
            // Allow a few steep ridge needles, but not sheet cliffs.
            assertTrue(harshAlpine * 12 <= alpineSteps,
                    "seed=" + seed + " too many sheer alpine steps: " + harshAlpine + "/" + alpineSteps);
        }
    }

    @Test
    void jaggedPeakCoresCanReachHighAltitude() {
        OverworldNoiseModel model = new OverworldNoiseModel(42L);
        int high = 0;
        int maxY = Integer.MIN_VALUE;
        for (int x = -4000; x <= 4000; x += 9) {
            for (int z = -4000; z <= 4000; z += 11) {
                OverworldNoiseModel.Sample s = model.sample(x, z);
                maxY = Math.max(maxY, s.surfaceY());
                if (s.peaks() && s.surfaceY() >= 200) {
                    high++;
                }
            }
        }
        assertTrue(maxY >= 210, "expected alpine max height >= 210, got " + maxY);
        assertTrue(high > 5, "expected some jagged peak cores >= 200, got " + high);
    }

    @Test
    void caveNoiseCarvesInteriorButNotSurfaceSkin() {
        OverworldNoiseModel model = new OverworldNoiseModel(99L);
        int caves = 0;
        int surfaceLeaks = 0;
        int solidSamples = 0;
        for (int x = 0; x < 128; x += 3) {
            for (int z = 0; z < 128; z += 3) {
                int surface = model.sample(x, z).surfaceY();
                if (surface < OverworldNoiseModel.SEA_LEVEL) {
                    continue;
                }
                for (int y = surface - 40; y < surface; y += 2) {
                    solidSamples++;
                    if (!model.isCaveAir(x, y, z, surface)) {
                        continue;
                    }
                    caves++;
                    if (y >= surface - 3) {
                        surfaceLeaks++;
                    }
                }
            }
        }
        // Sparse but present (pre-tune floor was >30 on this same grid).
        assertTrue(caves > 8, "expected some noodle/cheese caves, got " + caves);
        assertTrue(caves * 20 < solidSamples,
                "custom cave density still high: " + caves + "/" + solidSamples);
        assertEquals(0, surfaceLeaks);
    }

    @Test
    void oceanTrenchesStayFarFromLandAndRivers() {
        OverworldNoiseModel model = new OverworldNoiseModel(42L);
        int trenches = 0;
        for (int x = -2500; x <= 2500; x += 11) {
            for (int z = -2500; z <= 2500; z += 17) {
                OverworldNoiseModel.Sample s = model.sample(x, z);
                if (!s.oceanTrench()) {
                    continue;
                }
                trenches++;
                assertTrue(s.continentalness() < -0.55, "trench needs oceanic continentalness");
                assertTrue(s.land() < 0.18, "trench must be far ocean, land=" + s.land());
                assertTrue(!s.river(), "trench must never be river");
                assertTrue(s.surfaceY() < OverworldNoiseModel.SEA_LEVEL - 18,
                        "trench floor should be deep sea");
            }
        }
        assertTrue(trenches > 5, "expected some ocean trenches, got " + trenches);
    }

    @Test
    void riverBedsStayBelowWaterlineWithSoftBanks() {
        OverworldNoiseModel model = new OverworldNoiseModel(42L);
        int rivers = 0;
        int harshBank = 0;
        int sea = OverworldNoiseModel.SEA_LEVEL;
        for (int x = -1200; x <= 1200; x += 9) {
            for (int z = -1200; z <= 1200; z += 13) {
                OverworldNoiseModel.Sample s = model.sample(x, z);
                if (!s.river()) {
                    continue;
                }
                rivers++;
                // Must leave room for water up to sea-1 (waterTop).
                assertTrue(s.surfaceY() < sea - 1,
                        "river bed must be below waterline at " + x + "," + z + " y=" + s.surfaceY());
                assertTrue(s.surfaceY() >= sea - 6,
                        "river too deep at " + x + "," + z + " y=" + s.surfaceY());
                // Soft valley: check a few blocks away, not only the adjacent column.
                OverworldNoiseModel.Sample n = model.sample(x + 3, z);
                if (!n.river()) {
                    int step = n.surfaceY() - s.surfaceY();
                    if (step > 16) {
                        harshBank++;
                    }
                }
            }
        }
        assertTrue(rivers > 10, "expected some river samples, got " + rivers);
        assertTrue(harshBank * 8 <= rivers, "too many cliff banks: " + harshBank + "/" + rivers);
    }

    @Test
    void humidBasinsStayLowAndFlat() {
        OverworldNoiseModel model = new OverworldNoiseModel(42L);
        int sea = OverworldNoiseModel.SEA_LEVEL;
        int humid = 0;
        int tallHumid = 0;
        int steepHumid = 0;
        for (int x = -2500; x <= 2500; x += 13) {
            for (int z = -2500; z <= 2500; z += 17) {
                OverworldNoiseModel.Sample s = model.sample(x, z);
                if (s.humidity() < 0.45 || s.land() < 0.52 || s.mountainWeight() > 0.25) {
                    continue;
                }
                humid++;
                if (s.surfaceY() > sea + 8) {
                    tallHumid++;
                }
                OverworldNoiseModel.Sample n = model.sample(x + 2, z);
                if (Math.abs(n.surfaceY() - s.surfaceY()) > 6) {
                    steepHumid++;
                }
            }
        }
        assertTrue(humid > 30, "expected humid basin samples, got " + humid);
        assertTrue(tallHumid * 8 <= humid, "humid basins too tall: " + tallHumid + "/" + humid);
        assertTrue(steepHumid * 6 <= humid, "humid basins too steep: " + steepHumid + "/" + humid);
    }

    @Test
    void deepInlandWaterIsLakeNotOcean() {
        OverworldNoiseModel model = new OverworldNoiseModel(42L);
        int sea = OverworldNoiseModel.SEA_LEVEL;
        int inlandWet = 0;
        int falseOcean = 0;
        for (int x = -2000; x <= 2000; x += 15) {
            for (int z = -2000; z <= 2000; z += 17) {
                OverworldNoiseModel.Sample s = model.sample(x, z);
                if (s.surfaceY() >= sea || s.river()) {
                    continue;
                }
                if (s.continentalness() > 0.0 && s.land() > 0.55) {
                    inlandWet++;
                    if (OverworldNoiseModel.isTrueOcean(s)) {
                        falseOcean++;
                    }
                }
            }
        }
        assertEquals(0, falseOcean, "inland wet columns must not be true ocean");
        // Micro potholes should mostly be filled; remaining inland lakes are OK.
        assertTrue(inlandWet < 40, "too many deep-inland wet potholes: " + inlandWet);
    }

    @Test
    void beachRibbonDoesNotPaintInlandContinents() {
        OverworldNoiseModel model = new OverworldNoiseModel(42L);
        int sea = OverworldNoiseModel.SEA_LEVEL;
        int inland = 0;
        int inlandBeach = 0;
        int beachCandidates = 0;
        for (int x = -2000; x <= 2000; x += 17) {
            for (int z = -2000; z <= 2000; z += 19) {
                OverworldNoiseModel.Sample s = model.sample(x, z);
                if (s.surfaceY() < sea || s.river()) {
                    continue;
                }
                double c = s.continentalness();
                // Mirrors OverworldBiomeProvider shore ribbon thresholds.
                boolean ribbon = s.surfaceY() >= sea
                        && s.surfaceY() <= sea + 1
                        && s.land() >= 0.46
                        && s.land() < 0.49
                        && c >= -0.36
                        && c < -0.22;
                if (ribbon) {
                    beachCandidates++;
                }
                // Deep inland continents must not be sand beaches.
                if (c > 0.05 && s.land() > 0.70) {
                    inland++;
                    if (ribbon) {
                        inlandBeach++;
                    }
                }
            }
        }
        assertTrue(beachCandidates > 0, "expected some coastal beach ribbon");
        assertTrue(inland > 50, "expected inland samples");
        assertEquals(0, inlandBeach, "inland continents must not be beach");
    }
}
