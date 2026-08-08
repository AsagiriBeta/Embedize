package com.embedize.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StructureAirPolicyTest {

    @Test
    void surfaceStructuresIgnoreAirByDefault() {
        StructureAirPolicy policy = StructureAirPolicy.defaults();
        assertTrue(policy.surfaceIgnoreAirEnabled());
        assertTrue(policy.shouldIgnoreAir("minecraft:village_plains"));
        assertTrue(policy.shouldIgnoreAir("explorify:campsite"));
        assertTrue(policy.shouldIgnoreAir("ati_structures:ancient_temple"));
    }

    @Test
    void undergroundKeepAirIdsDoNotIgnore() {
        StructureAirPolicy policy = StructureAirPolicy.defaults();
        assertFalse(policy.shouldIgnoreAir("minecraft:ancient_city"));
        assertFalse(policy.shouldIgnoreAir("minecraft:stronghold"));
        assertFalse(policy.shouldIgnoreAir("minecraft:trial_chambers"));
        assertFalse(policy.shouldIgnoreAir("minecraft:mineshaft"));
        assertFalse(policy.shouldIgnoreAir("minecraft:monument"));
        assertTrue(policy.matchesKeepAir("pack:ancient_city"));
        assertTrue(policy.matchesKeepAir("ancient_city"));
    }

    @Test
    void datapackUndergroundKeepsAirWithoutVanillaIdWhitelist() {
        StructureAirPolicy policy = StructureAirPolicy.defaults();
        // hopo Better Mineshafts (lush caves) — custom namespace, not in keep-air-ids
        assertFalse(policy.shouldIgnoreAir("hopo:mineshaft/biomes/lush_mineshaft"));
        assertFalse(policy.shouldIgnoreAir("hopo:mineshaft/oak_mineshaft"));
        // DnT / nova underground cavities
        assertFalse(policy.shouldIgnoreAir("nova_structures:cave_chamber"));
        assertFalse(policy.shouldIgnoreAir("nova_structures:catacomb"));
        assertFalse(policy.shouldIgnoreAir("nova_structures:undead_crypt"));
        // Dungeons+
        assertFalse(policy.shouldIgnoreAir("dungeons_plus:lush_dungeon"));
        assertFalse(policy.shouldIgnoreAir("dungeons_plus:webbed_dungeon"));
        // Path tokens / overhauled ids
        assertFalse(policy.shouldIgnoreAir("minecraft:stronghold_big"));
        assertFalse(policy.shouldIgnoreAir("ati_structures:rotting_temple_underground"));
    }

    @Test
    void blankOrUnknownIdFailsOpenKeepAir() {
        StructureAirPolicy policy = StructureAirPolicy.defaults();
        assertFalse(policy.shouldIgnoreAir(null));
        assertFalse(policy.shouldIgnoreAir(""));
        // No surface/underground heuristic → UNKNOWN → keep air
        assertFalse(policy.shouldIgnoreAir("some_pack:mysterious_feature_xyz"));
    }

    @Test
    void disabledFlagNeverIgnores() {
        StructureAirPolicy policy = new StructureAirPolicy(false, true, List.of("minecraft:ancient_city"));
        assertFalse(policy.shouldIgnoreAir("minecraft:village_plains"));
        assertFalse(policy.shouldIgnoreAir("minecraft:ancient_city"));
    }

    @Test
    void placeHollowCarveDefaultsOn() {
        StructureAirPolicy policy = StructureAirPolicy.defaults();
        assertTrue(policy.placeHollowCarveEnabled());
    }
}
