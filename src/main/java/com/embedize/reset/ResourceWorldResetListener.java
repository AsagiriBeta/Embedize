package com.embedize.reset;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Blocks entry into the resource world while a reset is in progress.
 */
public final class ResourceWorldResetListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ResourceWorldResetService service;

    public ResourceWorldResetListener(ResourceWorldResetService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!service.isRunning()) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || !service.isResourceWorld(to.getWorld())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(LEGACY.deserialize("&c[资源世界] 正在重置中，暂时无法进入，请稍候。"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!service.isRunning()) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || !service.isResourceWorld(to.getWorld())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(LEGACY.deserialize("&c[资源世界] 正在重置中，传送门已临时关闭。"));
    }
}
