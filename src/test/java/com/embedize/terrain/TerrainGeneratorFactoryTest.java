package com.embedize.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainGeneratorFactoryTest {

    @Test
    void resolvesOverworldAliases() {
        assertTrue(TerrainGeneratorFactory.resolveKind(null) == TerrainGeneratorFactory.Kind.OVERWORLD);
        assertTrue(TerrainGeneratorFactory.resolveKind("") == TerrainGeneratorFactory.Kind.OVERWORLD);
        assertTrue(TerrainGeneratorFactory.resolveKind("overworld") == TerrainGeneratorFactory.Kind.OVERWORLD);
        assertTrue(TerrainGeneratorFactory.resolveKind("Embedize") == TerrainGeneratorFactory.Kind.OVERWORLD);
    }

    @Test
    void resolvesNetherAliases() {
        assertTrue(TerrainGeneratorFactory.resolveKind("nether") == TerrainGeneratorFactory.Kind.NETHER);
        assertTrue(TerrainGeneratorFactory.resolveKind("Embedize-Nether") == TerrainGeneratorFactory.Kind.NETHER);
        assertTrue(TerrainGeneratorFactory.resolveKind("embedize_nether") == TerrainGeneratorFactory.Kind.NETHER);
    }

    @Test
    void resolvesEndAliases() {
        assertTrue(TerrainGeneratorFactory.resolveKind("end") == TerrainGeneratorFactory.Kind.END);
        assertTrue(TerrainGeneratorFactory.resolveKind("Embedize-End") == TerrainGeneratorFactory.Kind.END);
        assertTrue(TerrainGeneratorFactory.resolveKind("nullscape") == TerrainGeneratorFactory.Kind.END);
        assertTrue(TerrainGeneratorFactory.resolveKind("embedize_end") == TerrainGeneratorFactory.Kind.END);
    }

    @Test
    void createsMatchingGenerators() {
        assertInstanceOf(OverworldTerrainGenerator.class, TerrainGeneratorFactory.create("resource", null));
        assertInstanceOf(NetherTerrainGenerator.class, TerrainGeneratorFactory.create("resource_nether", "nether"));
        assertInstanceOf(EndTerrainGenerator.class, TerrainGeneratorFactory.create("resource_end", "end"));
    }

    @Test
    void keepsStructureGenerationFlags() {
        OverworldTerrainGenerator ow = new OverworldTerrainGenerator("resource", null);
        NetherTerrainGenerator nether = new NetherTerrainGenerator("resource_nether", "nether");
        EndTerrainGenerator end = new EndTerrainGenerator("resource_end", "end");
        // Structures use the vanilla engine via the bundled datapack.
        assertTrue(ow.shouldGenerateStructures());
        assertTrue(nether.shouldGenerateStructures());
        assertTrue(end.shouldGenerateStructures());
        assertFalse(ow.shouldGenerateNoise());
        assertFalse(nether.shouldGenerateNoise());
        assertFalse(end.shouldGenerateNoise());
        assertTrue(ow.shouldGenerateCaves());
        assertFalse(nether.shouldGenerateCaves());
        assertFalse(end.shouldGenerateCaves());
        assertTrue(ow.kind() == TerrainGeneratorFactory.Kind.OVERWORLD);
        assertTrue(nether.kind() == TerrainGeneratorFactory.Kind.NETHER);
        assertTrue(end.kind() == TerrainGeneratorFactory.Kind.END);
    }
}
