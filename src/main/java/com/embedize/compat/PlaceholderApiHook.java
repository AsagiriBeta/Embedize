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

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Soft-depend binding for PlaceholderAPI. Expansion classes reference PAPI types and are
 * loaded reflectively only when PlaceholderAPI is present.
 */
public final class PlaceholderApiHook implements Listener {

    private final EmbedizePlugin plugin;
    private volatile boolean registered;

    public PlaceholderApiHook(EmbedizePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tryRegister();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if ("PlaceholderAPI".equals(event.getPlugin().getName())) {
            tryRegister();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if ("PlaceholderAPI".equals(event.getPlugin().getName())) {
            registered = false;
            plugin.getLogger().info("PlaceholderAPI unbound — resource placeholders inactive.");
        }
    }

    public synchronized void tryRegister() {
        if (registered) {
            return;
        }
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            return;
        }
        try {
            Class<?> expansionClass = Class.forName("com.embedize.compat.papi.ResourceWorldExpansion");
            Object expansion = expansionClass.getConstructor(EmbedizePlugin.class).newInstance(plugin);
            Method register = expansionClass.getMethod("register");
            Object result = register.invoke(expansion);
            boolean ok = result instanceof Boolean b ? b : true;
            if (ok) {
                registered = true;
                plugin.getLogger().info("PlaceholderAPI expansion registered: %resource_reset_*%");
            } else {
                plugin.getLogger().warning("PlaceholderAPI expansion register() returned false.");
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to register PlaceholderAPI expansion", ex);
        }
    }

    public boolean isRegistered() {
        return registered;
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        registered = false;
    }
}
