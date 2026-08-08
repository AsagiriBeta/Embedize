package com.embedize.terrain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contract: Embedize overworld biome provider exposes all natural vanilla overworld
 * biome keys and actually samples cave biomes in 3D (string keys — no Paper registry).
 */
class OverworldBiomeProviderTest {

    @Test
    void catalogListsAllVanillaOverworldBiomeKeys() {
        Set<String> keys = new HashSet<>(OverworldBiomeProvider.VANILLA_OVERWORLD_BIOME_KEYS);
        assertTrue(keys.contains("minecraft:deep_dark"));
        assertTrue(keys.contains("minecraft:lush_caves"));
        assertTrue(keys.contains("minecraft:dripstone_caves"));
        assertTrue(keys.contains("minecraft:old_growth_birch_forest"));
        assertTrue(keys.contains("minecraft:old_growth_pine_taiga"));
        assertTrue(keys.contains("minecraft:old_growth_spruce_taiga"));
        assertTrue(keys.contains("minecraft:eroded_badlands"));
        assertTrue(keys.contains("minecraft:wooded_badlands"));
        assertTrue(keys.contains("minecraft:windswept_savanna"));
        assertTrue(keys.contains("minecraft:mushroom_fields"));
        assertEquals(
                OverworldBiomeProvider.VANILLA_OVERWORLD_BIOME_KEYS.size(),
                OverworldBiomeProvider.VANILLA_OVERWORLD_BIOME_KEYS.stream().distinct().count()
        );
        // 53 natural overworld biomes in 1.21.x
        assertEquals(53, keys.size());
    }

    @Test
    void caveBiomesAppearUndergroundAcrossSeeds() {
        Set<String> found = new HashSet<>();
        for (long seed : new long[]{42L, 987654321L, -17L, 7L}) {
            OverworldBiomeProvider provider = new OverworldBiomeProvider(seed);
            for (int x = -2000; x <= 2000; x += 37) {
                for (int z = -2000; z <= 2000; z += 41) {
                    found.add(provider.pickKey3d(x, -20, z));
                    found.add(provider.pickKey3d(x, -40, z));
                    found.add(provider.pickKey3d(x, 8, z));
                }
            }
        }
        assertTrue(found.contains("minecraft:deep_dark"), "expected deep_dark samples, got " + found);
        assertTrue(found.contains("minecraft:lush_caves"), "expected lush_caves samples, got " + found);
        assertTrue(found.contains("minecraft:dripstone_caves"),
                "expected dripstone_caves samples, got " + found);
    }

    @Test
    void surfaceColumnKeepsSurfaceBiomeNotCave() {
        OverworldBiomeProvider provider = new OverworldBiomeProvider(42L);
        OverworldNoiseModel model = new OverworldNoiseModel(42L);
        int checked = 0;
        for (int x = -500; x <= 500; x += 23) {
            for (int z = -500; z <= 500; z += 29) {
                OverworldNoiseModel.Sample s = model.sample(x, z);
                if (s.surfaceY() < OverworldNoiseModel.SEA_LEVEL) {
                    continue;
                }
                String atSurface = provider.pickKey3d(x, s.surfaceY(), z);
                assertFalse(
                        atSurface.equals("minecraft:deep_dark")
                                || atSurface.equals("minecraft:lush_caves")
                                || atSurface.equals("minecraft:dripstone_caves"),
                        "surface Y should not be cave biome at " + x + "," + z + " got " + atSurface
                );
                checked++;
            }
        }
        assertTrue(checked > 50, "expected land columns to check");
    }

    @Test
    void missingSurfaceVariantsAreReachable() {
        Set<String> found = new HashSet<>();
        for (long seed : new long[]{42L, 99L, 1234567L, -991L}) {
            OverworldBiomeProvider provider = new OverworldBiomeProvider(seed);
            for (int x = -4000; x <= 4000; x += 31) {
                for (int z = -4000; z <= 4000; z += 37) {
                    found.add(provider.pickKey3d(x, 80, z));
                }
            }
        }
        assertTrue(found.contains("minecraft:old_growth_birch_forest")
                        || found.contains("minecraft:old_growth_pine_taiga")
                        || found.contains("minecraft:old_growth_spruce_taiga"),
                "expected at least one old_growth* biome, got size=" + found.size());
        assertTrue(found.contains("minecraft:eroded_badlands")
                        || found.contains("minecraft:wooded_badlands")
                        || found.contains("minecraft:windswept_savanna")
                        || found.contains("minecraft:mushroom_fields"),
                "expected rare warm/island variants somewhere, got " + summarize(found));
    }

    private static String summarize(Set<String> found) {
        return found.stream().sorted().limit(40).toList().toString();
    }
}
