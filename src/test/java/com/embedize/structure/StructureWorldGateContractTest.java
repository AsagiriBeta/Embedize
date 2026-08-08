package com.embedize.structure;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract: non-Embedize worlds gate catalog exact ids (including overhauled
 * {@code minecraft:*}) and custom namespaces from the catalog. Untouched vanilla
 * ids that never appear in {@code catalog.json} stay ungated.
 * <p>
 * Progression-critical {@code minecraft:stronghold} is ungated everywhere so
 * ender eyes / locate work in default {@code world} after DnT overhaul.
 */
class StructureWorldGateContractTest {

    private static final Set<String> CUSTOM_NS = Set.of("nova_structures", "dungeons_plus", "trek");
    private static final Set<String> CATALOG_IDS = Set.of(
            "minecraft:stronghold",
            "minecraft:ancient_city",
            "minecraft:monument",
            "nova_structures:illager_camp",
            "dungeons_plus:tower"
    );

    @Test
    void customNamespaceCatalogHitsAreBundled() {
        assertTrue(StructureCatalog.matchesBundled("nova_structures:illager_camp", CUSTOM_NS, CATALOG_IDS));
        assertTrue(StructureCatalog.matchesBundled("dungeons_plus:tower", CUSTOM_NS, CATALOG_IDS));
    }

    @Test
    void customNamespaceUnknownIdStillBundled() {
        assertTrue(StructureCatalog.matchesBundled("nova_structures:never_listed_but_same_ns", CUSTOM_NS, CATALOG_IDS));
    }

    @Test
    void cataloguedMinecraftOverhaulsAreGated() {
        assertTrue(StructureCatalog.matchesBundled("minecraft:stronghold", CUSTOM_NS, CATALOG_IDS));
        assertTrue(StructureCatalog.matchesBundled("Minecraft:Ancient_City", CUSTOM_NS, CATALOG_IDS));
        assertTrue(StructureCatalog.matchesBundled("minecraft:monument", CUSTOM_NS, CATALOG_IDS));
    }

    @Test
    void strongholdIsUngatedEverywhereDespiteCatalog() {
        assertTrue(StructureWorldGate.isUngatedEverywhere("minecraft:stronghold"));
        assertTrue(StructureWorldGate.isUngatedEverywhere("Minecraft:Stronghold"));
        assertFalse(StructureWorldGate.isUngatedEverywhere("minecraft:ancient_city"));
        assertFalse(StructureWorldGate.isUngatedEverywhere("nova_structures:end_castle"));
    }

    @Test
    void uncataloguedMinecraftStaysUngated() {
        assertFalse(StructureCatalog.matchesBundled("minecraft:village_plains", CUSTOM_NS, CATALOG_IDS));
        assertFalse(StructureCatalog.matchesBundled("minecraft:desert_pyramid", CUSTOM_NS, CATALOG_IDS));
        assertFalse(StructureCatalog.matchesBundled("minecraft:stronghold_big", CUSTOM_NS, CATALOG_IDS));
    }

    @Test
    void blankAndUnknownAreNotBundled() {
        assertFalse(StructureCatalog.matchesBundled(null, CUSTOM_NS, CATALOG_IDS));
        assertFalse(StructureCatalog.matchesBundled("  ", CUSTOM_NS, CATALOG_IDS));
        assertFalse(StructureCatalog.matchesBundled("other_mod:thing", CUSTOM_NS, CATALOG_IDS));
    }

    @Test
    void embedizeGeneratorHeuristic() {
        assertTrue(BundledDatapackSync.looksLikeEmbedizeGenerator("Embedize"));
        assertTrue(BundledDatapackSync.looksLikeEmbedizeGenerator("Embedize:nether"));
        assertTrue(BundledDatapackSync.looksLikeEmbedizeGenerator("Embedize:end"));
        assertFalse(BundledDatapackSync.looksLikeEmbedizeGenerator(null));
        assertFalse(BundledDatapackSync.looksLikeEmbedizeGenerator("TerraformGenerator"));
        assertTrue(BundledDatapackSync.isManagedPack("Embedize/biomes"));
        assertTrue(BundledDatapackSync.isManagedPack("Embedize/struct-dungeons_and_taverns_v5.1.0"));
        assertFalse(BundledDatapackSync.isManagedPack("file/bukkit"));
        assertFalse(BundledDatapackSync.isManagedPack("vanilla"));
    }
}
