package com.embedize.reset;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Immutable settings for resource-world monthly / manual reset.
 */
public final class ResourceWorldResetSettings {

    private final boolean enabled;
    private final String world;
    private final String alias;
    private final String fallbackWorld;
    private final String generator;
    private final boolean usePluginGenerator;
    private final int radius;
    private final int settleSeconds;
    private final int lockTimeoutMinutes;
    private final boolean scheduleEnabled;
    private final int dayOfMonth;
    private final int hour;
    private final int minute;

    public ResourceWorldResetSettings(
            boolean enabled,
            String world,
            String alias,
            String fallbackWorld,
            String generator,
            boolean usePluginGenerator,
            int radius,
            int settleSeconds,
            int lockTimeoutMinutes,
            boolean scheduleEnabled,
            int dayOfMonth,
            int hour,
            int minute
    ) {
        this.enabled = enabled;
        this.world = world;
        this.alias = alias;
        this.fallbackWorld = fallbackWorld;
        this.generator = generator;
        this.usePluginGenerator = usePluginGenerator;
        this.radius = radius;
        this.settleSeconds = settleSeconds;
        this.lockTimeoutMinutes = lockTimeoutMinutes;
        this.scheduleEnabled = scheduleEnabled;
        this.dayOfMonth = dayOfMonth;
        this.hour = hour;
        this.minute = minute;
    }

    public static ResourceWorldResetSettings fromConfig(FileConfiguration config) {
        ConfigurationSection root = config.getConfigurationSection("resource-reset");
        if (root == null) {
            return defaults();
        }
        ConfigurationSection schedule = root.getConfigurationSection("schedule");
        return new ResourceWorldResetSettings(
                root.getBoolean("enabled", true),
                root.getString("world", "resource"),
                root.getString("alias", "资源世界"),
                root.getString("fallback-world", "world"),
                root.getString("generator", "Embedize"),
                root.getBoolean("use-plugin-generator", true),
                Math.max(1, root.getInt("radius", 10000)),
                Math.max(0, root.getInt("settle-seconds", 5)),
                Math.max(1, root.getInt("lock-timeout-minutes", 20)),
                schedule == null || schedule.getBoolean("enabled", true),
                clamp(schedule == null ? 1 : schedule.getInt("day-of-month", 1), 1, 28),
                clamp(schedule == null ? 4 : schedule.getInt("hour", 4), 0, 23),
                clamp(schedule == null ? 0 : schedule.getInt("minute", 0), 0, 59)
        );
    }

    public static ResourceWorldResetSettings defaults() {
        return new ResourceWorldResetSettings(
                true, "resource", "资源世界", "world", "Embedize", true,
                10000, 5, 20, true, 1, 4, 0
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public boolean enabled() {
        return enabled;
    }

    public String world() {
        return world;
    }

    public String alias() {
        return alias;
    }

    public String fallbackWorld() {
        return fallbackWorld;
    }

    public String generator() {
        return generator;
    }

    public boolean usePluginGenerator() {
        return usePluginGenerator;
    }

    public int radius() {
        return radius;
    }

    public int settleSeconds() {
        return settleSeconds;
    }

    public int lockTimeoutMinutes() {
        return lockTimeoutMinutes;
    }

    public boolean scheduleEnabled() {
        return scheduleEnabled;
    }

    public int dayOfMonth() {
        return dayOfMonth;
    }

    public int hour() {
        return hour;
    }

    public int minute() {
        return minute;
    }

    public String timeLabel() {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute);
    }
}
