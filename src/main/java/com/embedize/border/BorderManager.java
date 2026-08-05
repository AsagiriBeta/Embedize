package com.embedize.border;

import com.embedize.EmbedizePlugin;
import com.embedize.util.SchedulerUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Persists invisible borders by <b>world name</b> under {@code plugins/Embedize/borders.yml}.
 * Recreating a world with the same name (e.g. resource resets) keeps the border active.
 */
public final class BorderManager {

    private final EmbedizePlugin plugin;
    private final File file;
    private final Map<String, WorldBorderData> borders = new LinkedHashMap<>();

    private boolean enabled = true;
    private WorldBorderData.Shape defaultShape = WorldBorderData.Shape.SQUARE;
    private double knockback = 3.0;
    private long timerTicks = 5L;
    private String message = "&cYou reached the world border.";
    private ScheduledTask timerTask;

    public BorderManager(EmbedizePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "borders.yml");
    }

    public synchronized void load() {
        if (!file.exists()) {
            plugin.saveResource("borders.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        enabled = cfg.getBoolean("enabled", true);
        defaultShape = WorldBorderData.Shape.parse(cfg.getString("shape", "square"), WorldBorderData.Shape.SQUARE);
        knockback = cfg.getDouble("knockback", 3.0);
        timerTicks = Math.max(1L, cfg.getLong("timer-ticks", 5L));
        message = cfg.getString("message", "&cYou reached the world border.");

        borders.clear();
        ConfigurationSection worlds = cfg.getConfigurationSection("worlds");
        if (worlds != null) {
            for (String worldName : worlds.getKeys(false)) {
                ConfigurationSection sec = worlds.getConfigurationSection(worldName);
                if (sec == null) {
                    continue;
                }
                double x = sec.getDouble("x", 0.5);
                double z = sec.getDouble("z", 0.5);
                int rx = sec.getInt("radius-x", sec.getInt("radius", 0));
                int rz = sec.getInt("radius-z", rx);
                if (rx <= 0 || rz <= 0) {
                    continue;
                }
                WorldBorderData.Shape override = null;
                if (sec.contains("shape")) {
                    override = WorldBorderData.Shape.parse(sec.getString("shape"), defaultShape);
                }
                borders.put(normalizeWorld(worldName), new WorldBorderData(x, z, rx, rz, override));
            }
        }
        restartTimer();
        plugin.getLogger().info("Loaded " + borders.size() + " world border(s).");
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(ListHeader.HEADER);
        yaml.set("enabled", enabled);
        yaml.set("shape", defaultShape.name().toLowerCase(Locale.ROOT));
        yaml.set("knockback", knockback);
        yaml.set("timer-ticks", timerTicks);
        yaml.set("message", message);
        for (Map.Entry<String, WorldBorderData> e : borders.entrySet()) {
            String path = "worlds." + e.getKey();
            WorldBorderData b = e.getValue();
            yaml.set(path + ".x", b.getX());
            yaml.set(path + ".z", b.getZ());
            yaml.set(path + ".radius-x", b.getRadiusX());
            yaml.set(path + ".radius-z", b.getRadiusZ());
            if (b.getShapeOverride() != null) {
                yaml.set(path + ".shape", b.getShapeOverride().name().toLowerCase(Locale.ROOT));
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save borders.yml: " + ex.getMessage());
        }
    }

    public synchronized void setBorder(String worldName, WorldBorderData border) {
        borders.put(normalizeWorld(worldName), border);
        save();
    }

    public synchronized boolean clearBorder(String worldName) {
        boolean removed = borders.remove(normalizeWorld(worldName)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized Optional<WorldBorderData> getBorder(String worldName) {
        return Optional.ofNullable(borders.get(normalizeWorld(worldName)));
    }

    public synchronized Map<String, WorldBorderData> all() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(borders));
    }

    public synchronized Set<String> worldNames() {
        return Set.copyOf(borders.keySet());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
        restartTimer();
    }

    public WorldBorderData.Shape getDefaultShape() {
        return defaultShape;
    }

    public void setDefaultShape(WorldBorderData.Shape defaultShape) {
        this.defaultShape = defaultShape;
        save();
    }

    public double getKnockback() {
        return knockback;
    }

    public void setKnockback(double knockback) {
        this.knockback = Math.max(0.0, knockback);
        save();
    }

    public long getTimerTicks() {
        return timerTicks;
    }

    public String getMessage() {
        return message;
    }

    public net.kyori.adventure.text.Component messageComponent() {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message == null ? "" : message);
    }

    public void restartTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (!enabled || knockback <= 0.0) {
            return;
        }
        timerTask = SchedulerUtil.runGlobalTimer(plugin, new BorderCheckTask(plugin), timerTicks, timerTicks);
    }

    public void shutdown() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    static String normalizeWorld(String name) {
        return name == null ? "" : name.trim();
    }

    private static final class ListHeader {
        static final java.util.List<String> HEADER = java.util.List.of(
                "Embedize invisible world borders (plugin knockback, not vanilla blue border).",
                "Keyed by world NAME — recreating a world with the same name keeps the border."
        );
    }
}
