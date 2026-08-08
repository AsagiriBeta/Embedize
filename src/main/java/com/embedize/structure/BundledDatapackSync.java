package com.embedize.structure;

import com.embedize.EmbedizePlugin;
import com.embedize.bootstrap.EmbedizeBootstrap;
import com.embedize.terrain.EmbedizeGenerator;
import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.datapack.DatapackManager;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Best-effort global enable/disable for Embedize bundled datapacks.
 * <p>
 * Paper/Leaves have <em>no</em> per-world structure registry. {@link Datapack#setEnabled(boolean)}
 * is server-global and triggers a resource reload. This sync approximates TFG's
 * “structures follow the generator” product rule:
 * <ul>
 *   <li>No Embedize-generator world expected → keep {@code Embedize/struct-*} (+ biomes) disabled
 *       so the shared registry stays vanilla.</li>
 *   <li>≥1 Embedize world (or config/MV says one is coming) → enable packs so the vanilla
 *       jigsaw engine can place DnT/etc. in those worlds.</li>
 * </ul>
 * Once packs are enabled, default {@code world} shares the same registry; 
 * {@link StructureWorldGateListener} remains the spawn/locate firewall.
 */
public final class BundledDatapackSync implements Listener {

    public enum Mode {
        /** Enable whenever an Embedize world exists or is configured (default). */
        EMBEDIZE_WORLDS_ONLY,
        /** Always enable after discovery (old behaviour). */
        ALWAYS,
        /** Never enable bundled structure/biome packs. */
        NEVER;

        static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return EMBEDIZE_WORLDS_ONLY;
            }
            String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            return switch (key) {
                case "always", "on", "true" -> ALWAYS;
                case "never", "off", "false" -> NEVER;
                case "embedize_worlds_only", "embedize_only", "with_embedize", "lazy" -> EMBEDIZE_WORLDS_ONLY;
                default -> EMBEDIZE_WORLDS_ONLY;
            };
        }
    }

    private final EmbedizePlugin plugin;
    private volatile boolean lastDesiredEnable;
    private volatile boolean appliedOnce;

    public BundledDatapackSync(@NotNull EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Call early in {@code onEnable} (before Multiverse loads worlds when load-order allows).
     */
    public void syncNow(@NotNull String reason) {
        Mode mode = Mode.parse(plugin.getConfig().getString("structures.bundled-pack-mode", "embedize-worlds-only"));
        boolean want = switch (mode) {
            case ALWAYS -> true;
            case NEVER -> false;
            case EMBEDIZE_WORLDS_ONLY -> anyEmbedizeWorldLoaded() || embedizeWorldExpectedFromConfig();
        };
        apply(want, reason + " mode=" + mode.name().toLowerCase(Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP
                || event.getType() == ServerLoadEvent.LoadType.RELOAD) {
            syncNow("ServerLoadEvent/" + event.getType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        if (event.getWorld().getGenerator() instanceof EmbedizeGenerator) {
            syncNow("WorldLoad/" + event.getWorld().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        // Do not disable mid-session (reload thrash). Next cold start with
        // autoEnableOnServerStart(false) keeps packs off if no Embedize worlds.
        if (event.getWorld().getGenerator() instanceof EmbedizeGenerator) {
            plugin.getLogger().fine(
                    "Embedize world unloaded '" + event.getWorld().getName()
                            + "'; bundled packs stay as-is until next sync/restart.");
        }
    }

    public boolean lastDesiredEnable() {
        return lastDesiredEnable;
    }

    public boolean anyBundledPackEnabled() {
        DatapackManager manager = plugin.getServer().getDatapackManager();
        for (Datapack pack : manager.getPacks()) {
            if (isManagedPack(pack.getName()) && pack.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    public @NotNull List<String> managedPackStatuses() {
        List<String> out = new ArrayList<>();
        DatapackManager manager = plugin.getServer().getDatapackManager();
        for (Datapack pack : manager.getPacks()) {
            String name = pack.getName();
            if (!isManagedPack(name)) {
                continue;
            }
            out.add(name + "=" + (pack.isEnabled() ? "enabled" : "disabled"));
        }
        return out;
    }

    private void apply(boolean wantEnabled, @NotNull String reason) {
        lastDesiredEnable = wantEnabled;
        DatapackManager manager = plugin.getServer().getDatapackManager();
        manager.refreshPacks();

        List<Datapack> targets = new ArrayList<>();
        for (Datapack pack : manager.getPacks()) {
            if (isManagedPack(pack.getName())) {
                targets.add(pack);
            }
        }
        if (targets.isEmpty()) {
            plugin.getLogger().warning(
                    "Bundled datapack sync: no Embedize/struct-* or Embedize/biomes packs found ("
                            + reason + ").");
            return;
        }

        int changed = 0;
        for (Datapack pack : targets) {
            if (pack.isEnabled() != wantEnabled) {
                try {
                    pack.setEnabled(wantEnabled);
                    changed++;
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to setEnabled(" + wantEnabled + ") on " + pack.getName(), ex);
                }
            }
        }

        if (changed > 0) {
            plugin.getLogger().info(
                    "Bundled datapacks " + (wantEnabled ? "ENABLED" : "DISABLED")
                            + " (" + changed + "/" + targets.size() + ") — " + reason
                            + ". Paper packs are server-global; StructureWorldGate still gates non-Embedize spawn.");
        } else if (!appliedOnce) {
            plugin.getLogger().info(
                    "Bundled datapacks already " + (wantEnabled ? "enabled" : "disabled")
                            + " (" + targets.size() + " packs) — " + reason);
        }
        appliedOnce = true;
    }

    private boolean anyEmbedizeWorldLoaded() {
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getGenerator() instanceof EmbedizeGenerator) {
                return true;
            }
        }
        return false;
    }

    /**
     * Heuristics used before Multiverse finishes loading worlds (Embedize enables first).
     */
    private boolean embedizeWorldExpectedFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getBoolean("resource-reset.use-plugin-generator", true)
                && looksLikeEmbedizeGenerator(cfg.getString("resource-reset.generator", "Embedize"))) {
            return true;
        }
        return multiverseConfigRequestsEmbedize();
    }

    private boolean multiverseConfigRequestsEmbedize() {
        // Standard layout: <server>/plugins/Multiverse-Core/worlds.yml
        File pluginsDir = plugin.getDataFolder().getParentFile();
        if (pluginsDir == null) {
            return false;
        }
        File file = new File(pluginsDir, "Multiverse-Core/worlds.yml");
        if (!file.isFile()) {
            return false;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String worldKey : yaml.getKeys(false)) {
                String gen = yaml.getString(worldKey + ".generator");
                if (gen == null) {
                    gen = yaml.getString(worldKey + ".world.generator");
                }
                if (looksLikeEmbedizeGenerator(gen)) {
                    plugin.getLogger().info(
                            "MV worlds.yml requests Embedize generator for '" + worldKey
                                    + "' — will enable bundled packs.");
                    return true;
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "Unable to peek Multiverse worlds.yml", ex);
        }
        return false;
    }

    static boolean looksLikeEmbedizeGenerator(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String g = raw.trim().toLowerCase(Locale.ROOT);
        // Multiverse: "Embedize", "Embedize:nether", "Embedize:Embedize", "plugin:Embedize", etc.
        return g.equals("embedize")
                || g.startsWith("embedize:")
                || g.endsWith(":embedize")
                || g.contains("embedize:nether")
                || g.contains("embedize:end");
    }

    /**
     * Pack names from Bootstrap {@code discoverPack}: {@code <plugin>/<id>}.
     */
    public static boolean isManagedPack(@NotNull String packName) {
        String n = packName.toLowerCase(Locale.ROOT);
        // paper-plugin.yml name is Embedize → Embedize/biomes, Embedize/struct-<slug>
        return n.equals("embedize/" + EmbedizeBootstrap.BIOME_PACK_ID)
                || n.startsWith("embedize/struct-");
    }
}
