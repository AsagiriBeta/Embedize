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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Whitelist isolation for datapack structure natural generation.
 * Only worlds in {@code allowed-worlds} may receive managed structures.
 */
public final class StructureIsolationListener implements Listener {

    private final EmbedizePlugin plugin;
    private final PluginConfig config;
    private final MultiverseHook multiverseHook;
    private final AtomicLong cancelled = new AtomicLong();
    private final AtomicLong allowed = new AtomicLong();
    private final AtomicLong passed = new AtomicLong();

    public StructureIsolationListener(EmbedizePlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.multiverseHook = new MultiverseHook(plugin.getLogger());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onStructureSpawn(AsyncStructureSpawnEvent event) {
        IsolationPolicy policy = config.getIsolationPolicy();
        if (policy == null || !config.isEnabled()) {
            return;
        }

        NamespacedKey key = resolveKey(event.getStructure());
        String namespace = key == null ? null : key.getNamespace();
        String path = key == null ? null : key.getKey();
        String worldName = event.getWorld().getName();
        boolean listed = isListedAllowed(worldName, policy);
        IsolationPolicy.Decision decision = policy.decideWithFlags(namespace, path, listed);

        switch (decision) {
            case PASS -> passed.incrementAndGet();
            case ALLOW -> {
                allowed.incrementAndGet();
                if (config.isDebugCancellations()) {
                    plugin.getLogger().info("[allow] " + formatKey(key) + " in '" + worldName + "'");
                }
            }
            case DENY -> {
                event.setCancelled(true);
                long total = cancelled.incrementAndGet();
                if (config.isDebugCancellations()) {
                    plugin.getLogger().info("[deny #" + total + "] " + formatKey(key) + " in '" + worldName + "'");
                }
            }
        }
    }

    private boolean isListedAllowed(String worldName, IsolationPolicy policy) {
        if (policy.isAllowedWorldListed(worldName)) {
            return true;
        }
        return config.isResolveAliases()
                && multiverseHook.matchesConfiguredWorld(worldName, policy.getAllowedWorlds(), true);
    }

    private static String formatKey(NamespacedKey key) {
        return key == null ? "<unresolved>" : key.toString();
    }

    @SuppressWarnings("removal")
    private static NamespacedKey resolveKey(Structure structure) {
        try {
            Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
            NamespacedKey resolved = registry.getKey(structure);
            if (resolved != null) {
                return resolved;
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

    public long getCancelledCount() {
        return cancelled.get();
    }

    public long getAllowedCount() {
        return allowed.get();
    }

    public long getPassedCount() {
        return passed.get();
    }

    public MultiverseHook getMultiverseHook() {
        return multiverseHook;
    }
}
