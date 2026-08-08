package com.embedize.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Paper/Folia-safe scheduling helpers. Prefer Folia region schedulers which also work on Paper.
 */
public final class SchedulerUtil {

    private SchedulerUtil() {
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    public static void runGlobal(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public static void runGlobalLater(Plugin plugin, Runnable task, long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(), delay);
    }

    public static void runAsyncLater(Plugin plugin, Runnable task, long delayMs) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, scheduled -> task.run(), Math.max(1L, delayMs), TimeUnit.MILLISECONDS);
    }

    public static void runForEntity(Plugin plugin, org.bukkit.entity.Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    public static void runForLocation(Plugin plugin, Location location, Runnable task) {
        if (location.getWorld() == null) {
            runGlobal(plugin, task);
            return;
        }
        Bukkit.getRegionScheduler().execute(plugin, location, task);
    }

    public static io.papermc.paper.threadedregions.scheduler.ScheduledTask runGlobalTimer(
            Plugin plugin,
            Runnable task,
            long initialDelayTicks,
            long periodTicks
    ) {
        long delay = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduled -> task.run(), delay, period);
    }
}
