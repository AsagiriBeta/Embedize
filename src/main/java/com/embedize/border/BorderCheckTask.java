package com.embedize.border;

import com.embedize.EmbedizePlugin;
import com.embedize.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Periodically (and on teleport) knocks players back inside configured borders.
 */
public final class BorderCheckTask implements Runnable {

    private static final Set<String> HANDLING = Collections.synchronizedSet(new LinkedHashSet<>());

    private final EmbedizePlugin plugin;

    public BorderCheckTask(EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        BorderManager borders = plugin.getBorderManager();
        if (!borders.isEnabled() || borders.getKnockback() <= 0.0) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            SchedulerUtil.runForEntity(plugin, player, () -> checkPlayer(player, null, false, true));
        }
    }

    public static Location checkPlayer(
            EmbedizePlugin plugin,
            Player player,
            Location targetLoc,
            boolean returnLocationOnly,
            boolean notify
    ) {
        if (player == null || !player.isOnline()) {
            return null;
        }
        BorderManager manager = plugin.getBorderManager();
        if (!manager.isEnabled() || manager.getKnockback() <= 0.0) {
            return null;
        }
        if (player.hasPermission("embedize.border.bypass")) {
            return null;
        }

        Location loc = targetLoc != null ? targetLoc.clone() : player.getLocation().clone();
        if (loc.getWorld() == null) {
            return null;
        }
        Optional<WorldBorderData> borderOpt = manager.getBorder(loc.getWorld().getName());
        if (borderOpt.isEmpty()) {
            return null;
        }
        WorldBorderData border = borderOpt.get();
        if (border.insideBorder(loc, manager.getDefaultShape())) {
            return null;
        }

        String key = player.getName().toLowerCase(Locale.ROOT);
        if (HANDLING.contains(key)) {
            return null;
        }
        HANDLING.add(key);

        Location newLoc = border.correctedPosition(loc, manager.getDefaultShape(), manager.getKnockback(), player.isFlying());
        if (newLoc == null) {
            newLoc = player.getWorld().getSpawnLocation();
        }

        final boolean handlingVehicle;
        if (player.isInsideVehicle()) {
            Entity ride = player.getVehicle();
            player.leaveVehicle();
            if (ride != null) {
                double vertOffset = ride instanceof LivingEntity ? 0.0 : ride.getLocation().getY() - loc.getY();
                Location rideLoc = newLoc.clone();
                rideLoc.setY(newLoc.getY() + vertOffset);
                ride.setVelocity(new Vector(0, 0, 0));
                ride.teleportAsync(rideLoc, TeleportCause.PLUGIN);
                handlingVehicle = true;
                SchedulerUtil.runGlobalLater(plugin, () -> HANDLING.remove(key), 10L);
            } else {
                handlingVehicle = false;
            }
        } else {
            handlingVehicle = false;
        }

        if (!player.getPassengers().isEmpty()) {
            player.eject();
        }

        if (notify && manager.getMessage() != null && !manager.getMessage().isBlank()) {
            player.sendMessage(manager.messageComponent());
        }

        if (returnLocationOnly) {
            if (!handlingVehicle) {
                HANDLING.remove(key);
            }
            return newLoc;
        }

        Location destination = newLoc;
        player.teleportAsync(destination, TeleportCause.PLUGIN).thenRun(() -> {
            if (!handlingVehicle) {
                HANDLING.remove(key);
            }
        });
        return null;
    }

    private void checkPlayer(Player player, Location targetLoc, boolean returnLocationOnly, boolean notify) {
        checkPlayer(plugin, player, targetLoc, returnLocationOnly, notify);
    }
}
