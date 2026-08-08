package com.embedize.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract: only worlds that ask for an Embedize generator receive Embedize
 * generation via {@link ChunkGenerator}. Vanilla worlds are untouched.
 */
class WorldIsolationContractTest {

    @Test
    void factoryReturnsEmbedizeGeneratorsForRequestedIds() {
        assertInstanceOf(EmbedizeGenerator.class, TerrainGeneratorFactory.create("resource", null));
        assertInstanceOf(EmbedizeGenerator.class, TerrainGeneratorFactory.create("resource_nether", "nether"));
        assertInstanceOf(EmbedizeGenerator.class, TerrainGeneratorFactory.create("resource_end", "end"));
    }

    @Test
    void embedizeGeneratorsDisableVanillaNoiseDelegation() {
        assertFalse(new OverworldTerrainGenerator("resource", null).shouldGenerateNoise());
        assertFalse(new NetherTerrainGenerator("resource_nether", "nether").shouldGenerateNoise());
        assertFalse(new EndTerrainGenerator("resource_end", "end").shouldGenerateNoise());
        // Structure sets stay on the vanilla pipeline; only noise/surface stay custom.
        assertTrue(new OverworldTerrainGenerator("resource", null).shouldGenerateStructures());
        assertTrue(new OverworldTerrainGenerator("resource", null) instanceof EmbedizeGenerator);
    }
}
