package com.embedize.structure;

import io.papermc.paper.event.world.StructuresLocateEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.AsyncStructureSpawnEvent;
import org.bukkit.generator.structure.Structure;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Blocks natural generation / locate of bundled structures (catalog ids + custom
 * namespaces) outside Embedize-generator worlds. Dual insurance alongside
 * {@link BundledDatapackSync}. Does not affect {@code /place structure} or
 * {@code /embedize place} ({@code AsyncStructureGenerateEvent}).
 */
public final class StructureWorldGateListener implements Listener {

    private final StructureWorldGate gate;

    public StructureWorldGateListener(@NotNull StructureWorldGate gate) {
        this.gate = gate;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureSpawn(AsyncStructureSpawnEvent event) {
        if (!gate.allowNaturalStructure(event.getWorld(), event.getStructure())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructuresLocate(StructuresLocateEvent event) {
        if (StructureWorldGate.isEmbedizeWorld(event.getWorld())) {
            return;
        }
        List<Structure> allowed = new ArrayList<>();
        for (Structure structure : event.getStructures()) {
            if (gate.allowNaturalStructure(event.getWorld(), structure)) {
                allowed.add(structure);
            }
        }
        if (allowed.size() == event.getStructures().size()) {
            return;
        }
        if (allowed.isEmpty()) {
            event.setCancelled(true);
            return;
        }
        event.setStructures(allowed);
    }
}
