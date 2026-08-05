package com.embedize.structure;

import com.embedize.EmbedizePlugin;
import com.embedize.compat.MultiverseHook;
import com.embedize.config.PluginConfig;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.AsyncStructureSpawnEvent;
import org.bukkit.generator.structure.Structure;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cancels managed datapack structures outside (or inside) configured worlds.
 * This isolates global datapack structure generation to selected Multiverse dimensions.
 */
public final class StructureIsolationListener implements Listener {

    private final EmbedizePlugin plugin;
    private final PluginConfig config;
    private final MultiverseHook multiverseHook;
    private final AtomicLong cancelled = new AtomicLong();
    private final AtomicLong allowed = new AtomicLong();

    public StructureIsolationListener(EmbedizePlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.multiverseHook = new MultiverseHook(plugin.getLogger());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureSpawn(AsyncStructureSpawnEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        Structure structure = event.getStructure();
        NamespacedKey key = resolveKey(structure);
        if (key == null || !isManaged(key)) {
            return;
        }

        String worldName = event.getWorld().getName();
        boolean worldMatches = multiverseHook.matchesConfiguredWorld(
                worldName,
                config.getConfiguredWorlds(),
                config.isResolveAliases()
        );

        boolean allow = config.getMode() == PluginConfig.Mode.ALLOWLIST ? worldMatches : !worldMatches;
        if (allow) {
            allowed.incrementAndGet();
            return;
        }

        event.setCancelled(true);
        long total = cancelled.incrementAndGet();
        if (config.isDebugCancellations()) {
            plugin.getLogger().info("[cancel #" + total + "] " + key + " in world '" + worldName + "'");
        }
    }

    @SuppressWarnings("removal")
    private static NamespacedKey resolveKey(Structure structure) {
        try {
            Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
            NamespacedKey key = registry.getKey(structure);
            if (key != null) {
                return key;
            }
        } catch (Throwable ignored) {
            // Older runtimes / unexpected registry state
        }
        try {
            return structure.getKey();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isManaged(NamespacedKey key) {
        return config.getManagedNamespaces().contains(key.getNamespace().toLowerCase(Locale.ROOT));
    }

    public long getCancelledCount() {
        return cancelled.get();
    }

    public long getAllowedCount() {
        return allowed.get();
    }

    public MultiverseHook getMultiverseHook() {
        return multiverseHook;
    }
}
