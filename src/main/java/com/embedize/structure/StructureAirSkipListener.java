package com.embedize.structure;

import com.embedize.EmbedizePlugin;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.AsyncStructureGenerateEvent;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.BlockTransformer;
import org.jetbrains.annotations.NotNull;

/**
 * Skips structure-template air writes for <em>surface</em> structures via Paper's
 * {@link AsyncStructureGenerateEvent} transformers.
 * <p>
 * Covers vanilla {@code /place structure} (used by {@code /embedize place}) and
 * natural structure generation. Classification is by structure properties
 * (step / terrain_adaptation / heightmap), not a vanilla-id whitelist — so
 * datapack underground structures keep air and clear solid terrain.
 */
public final class StructureAirSkipListener implements Listener {

    private final NamespacedKey transformerKey;

    public StructureAirSkipListener(@NotNull EmbedizePlugin plugin) {
        this.transformerKey = new NamespacedKey(plugin, "surface-ignore-air");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGenerate(AsyncStructureGenerateEvent event) {
        Structure structure = event.getStructure();
        if (structure == null) {
            return;
        }
        Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        NamespacedKey key = registry == null ? null : registry.getKey(structure);
        if (key == null) {
            // Unresolvable id → do not attach ignore-air (fail open / keep air).
            return;
        }
        String id = key.asString();
        if (!StructureAirPolicy.get().shouldIgnoreAir(id, event.getWorld())) {
            return;
        }
        event.setBlockTransformer(transformerKey, IGNORE_STRUCTURE_AIR);
    }

    /**
     * Structure air → keep the pre-existing world block (solids and fluids).
     * Non-air structure blocks pass through unchanged.
     */
    private static final BlockTransformer IGNORE_STRUCTURE_AIR =
            (region, x, y, z, current, state) -> {
                Material type = current.getType();
                if (type.isAir()) {
                    return state.getWorld();
                }
                return current;
            };
}
