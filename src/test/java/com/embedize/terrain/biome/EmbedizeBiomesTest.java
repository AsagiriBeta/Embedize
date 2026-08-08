package com.embedize.terrain.biome;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbedizeBiomesTest {

    @Test
    void catalogKeepsOnlyDistinctBiomes() {
        assertEquals(1, EmbedizeBiomes.catalogSize());
        assertTrue(EmbedizeBiomes.customToVanilla().containsKey("embedize:abyssal_deep"));
        assertEquals("minecraft:deep_ocean", EmbedizeBiomes.customToVanilla().get("embedize:abyssal_deep"));
        // No 1:1 rename overlays
        assertFalse(EmbedizeBiomes.customToVanilla().containsKey("embedize:highland_steppe"));
        assertFalse(EmbedizeBiomes.customToVanilla().containsKey("embedize:scarlet_woodland"));
        assertFalse(EmbedizeBiomes.customToVanilla().containsKey("embedize:weeping_crimson"));
    }
}
