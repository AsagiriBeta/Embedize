package com.embedize.compat;

import com.embedize.EmbedizePlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Factory for Multiverse soft-depend access.
 * Implementation class is loaded only when Multiverse-Core is present so missing
 * Multiverse classes never break servers without it.
 */
public final class MultiverseHook implements Listener {

    private final EmbedizePlugin plugin;
    private volatile MultiverseAccess access = MultiverseAccess.NOOP;

    public MultiverseHook(EmbedizePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        bindIfPresent();
    }

    private synchronized void bindIfPresent() {
        if (access != MultiverseAccess.NOOP) {
            return;
        }
        this.access = createAccess(plugin);
        if (access.isAvailable()) {
            plugin.getLogger().info("Multiverse-Core integration enabled (typed API v"
                    + access.getVersion() + ").");
        } else if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
            plugin.getLogger().info("Multiverse-Core detected; API will bind when Multiverse finishes loading.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("Multiverse-Core")) {
            bindIfPresent();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public synchronized void onPluginDisable(PluginDisableEvent event) {
        if (!event.getPlugin().getName().equals("Multiverse-Core")) {
            return;
        }
        detachAccessListener();
        access = MultiverseAccess.NOOP;
        plugin.getLogger().info("Multiverse-Core integration unbound.");
    }

    public synchronized void shutdown() {
        detachAccessListener();
        access = MultiverseAccess.NOOP;
        HandlerList.unregisterAll(this);
    }

    private void detachAccessListener() {
        MultiverseAccess current = access;
        if (current instanceof Listener listener) {
            HandlerList.unregisterAll(listener);
        }
    }

    private static MultiverseAccess createAccess(EmbedizePlugin plugin) {
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mv == null) {
            return MultiverseAccess.NOOP;
        }
        try {
            Class<?> impl = Class.forName("com.embedize.compat.multiverse.MultiverseAccessImpl");
            return (MultiverseAccess) impl.getConstructor(EmbedizePlugin.class).newInstance(plugin);
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Multiverse-Core present but API binding failed: " + ex.getMessage(), ex);
            return MultiverseAccess.NOOP;
        }
    }

    public MultiverseAccess access() {
        return access;
    }

    public boolean isPresent() {
        return access.isAvailable();
    }
}
