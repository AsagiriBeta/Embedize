package com.embedize.reset;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.logging.Level;

/**
 * Persists last-reset metadata (not the in-memory running lock).
 */
public final class ResourceWorldResetStateStore {

    private final Plugin plugin;
    private final File file;

    private Instant lastAt;
    private Instant lastAutoAt;
    private String lastAutoKey = "";
    private String lastReason = "";
    private String lastGenerator = "";
    private String warnKey = "";

    public ResourceWorldResetStateStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "resource-reset-state.yml");
    }

    public synchronized void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        lastAt = parseInstant(yaml.getString("last-at"));
        lastAutoAt = parseInstant(yaml.getString("last-auto-at"));
        lastAutoKey = yaml.getString("last-auto-key", "");
        lastReason = yaml.getString("last-reason", "");
        lastGenerator = yaml.getString("last-generator", "");
        warnKey = yaml.getString("warn-key", "");
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (lastAt != null) {
            yaml.set("last-at", lastAt.toString());
        }
        if (lastAutoAt != null) {
            yaml.set("last-auto-at", lastAutoAt.toString());
        }
        yaml.set("last-auto-key", lastAutoKey == null ? "" : lastAutoKey);
        yaml.set("last-reason", lastReason == null ? "" : lastReason);
        yaml.set("last-generator", lastGenerator == null ? "" : lastGenerator);
        yaml.set("warn-key", warnKey == null ? "" : warnKey);
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save resource-reset-state.yml", ex);
        }
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    public synchronized Instant lastAt() {
        return lastAt;
    }

    public synchronized Instant lastAutoAt() {
        return lastAutoAt;
    }

    public synchronized String lastAutoKey() {
        return lastAutoKey == null ? "" : lastAutoKey;
    }

    public synchronized String lastReason() {
        return lastReason == null ? "" : lastReason;
    }

    public synchronized String lastGenerator() {
        return lastGenerator == null ? "" : lastGenerator;
    }

    public synchronized String warnKey() {
        return warnKey == null ? "" : warnKey;
    }

    public synchronized void setWarnKey(String key) {
        this.warnKey = key == null ? "" : key;
        save();
    }

    public synchronized void recordSuccess(String reason, String generator, String autoKey) {
        Instant now = Instant.now();
        this.lastAt = now;
        this.lastReason = reason == null ? "" : reason;
        this.lastGenerator = generator == null ? "" : generator;
        if (autoKey != null && !autoKey.isBlank()) {
            this.lastAutoAt = now;
            this.lastAutoKey = autoKey;
        }
        save();
    }

    public synchronized void markAutoKey(String autoKey) {
        this.lastAutoKey = autoKey == null ? "" : autoKey;
        save();
    }
}
