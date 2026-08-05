package com.embedize.border;

import com.embedize.EmbedizePlugin;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Intercept teleports/portals that would land outside an invisible border.
 * WorldLoadEvent is a no-op for config (borders are keyed by name), but logged at fine level.
 */
public final class BorderListener implements Listener {

    private final EmbedizePlugin plugin;

    public BorderListener(EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        BorderManager manager = plugin.getBorderManager();
        if (!manager.isEnabled() || manager.getKnockback() <= 0.0) {
            return;
        }
        Location corrected = BorderCheckTask.checkPlayer(plugin, event.getPlayer(), event.getTo(), true, true);
        if (corrected != null) {
            event.setTo(corrected);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        BorderManager manager = plugin.getBorderManager();
        if (!manager.isEnabled() || manager.getKnockback() <= 0.0) {
            return;
        }
        Location corrected = BorderCheckTask.checkPlayer(plugin, event.getPlayer(), event.getTo(), true, false);
        if (corrected != null) {
            event.setTo(corrected);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.getBorderManager().onWorldReady(event.getWorld().getName());
    }
}
