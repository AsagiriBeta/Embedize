package com.embedize.structure;

import com.embedize.terrain.EmbedizeGenerator;
import org.bukkit.World;
import org.bukkit.generator.structure.Structure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Product gate for bundled structure packs on non-{@link EmbedizeGenerator} worlds.
 * <p>
 * Paper has no per-world datapacks, so when packs are enabled they enter the
 * <em>server-global</em> registry. This gate cancels natural spawn / locate of
 * catalogued structures (exact id, including overhauled {@code minecraft:*}) and
 * any id under a custom catalog namespace, outside Embedize worlds.
 * <p>
 * Progression-critical ids (currently {@code minecraft:stronghold}) are never
 * gated: ender eyes / {@code /locate} must work in default {@code world} as well
 * as Embedize resource worlds. When packs overwrite that id, the overhauled
 * definition is what both worlds share (Paper cannot keep a second vanilla copy).
 * <p>
 * {@link com.embedize.structure.BundledDatapackSync} tries to keep packs disabled
 * until an Embedize world is expected (closer to TFG isolation).
 */
public final class StructureWorldGate {

    /**
     * Always allow natural spawn / locate even on non-Embedize worlds when packs
     * have overhauled these ids. Keep this list minimal — only progression blockers.
     */
    static final Set<String> UNGATED_EVERYWHERE = Set.of(
            "minecraft:stronghold"
    );

    private final StructureCatalog catalog;

    public StructureWorldGate(@NotNull StructureCatalog catalog) {
        this.catalog = catalog;
    }

    public static boolean isEmbedizeWorld(@Nullable World world) {
        return world != null && world.getGenerator() instanceof EmbedizeGenerator;
    }

    public static boolean isUngatedEverywhere(@Nullable String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return false;
        }
        return UNGATED_EVERYWHERE.contains(structureId.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Whether natural structure spawn / locate should proceed for this structure
     * in the given world.
     */
    public boolean allowNaturalStructure(@Nullable World world, @Nullable Structure structure) {
        if (isEmbedizeWorld(world)) {
            return true;
        }
        if (structure == null || catalog.isEmpty()) {
            // No catalog → cannot identify bundled ids; fail open for vanilla-only jars.
            return true;
        }
        String id = catalog.resolveStructureId(structure);
        if (id == null || id.isBlank()) {
            return true;
        }
        if (isUngatedEverywhere(id)) {
            return true;
        }
        return !catalog.isBundledStructure(id);
    }

    public boolean allowNaturalStructure(@Nullable World world, @Nullable String structureId) {
        if (isEmbedizeWorld(world)) {
            return true;
        }
        if (structureId == null || structureId.isBlank() || catalog.isEmpty()) {
            return true;
        }
        if (isUngatedEverywhere(structureId)) {
            return true;
        }
        return !catalog.isBundledStructure(structureId);
    }
}
