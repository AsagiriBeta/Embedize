package com.embedize.compat;

import com.embedize.EmbedizePlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Factory for Multiverse soft-depend access.
 * Implementation class is loaded only when Multiverse-Core is present so missing
 * Multiverse classes never break servers without it.
 */
public final class MultiverseHook {

    private final MultiverseAccess access;
    private final boolean terraformGeneratorPresent;

    public MultiverseHook(EmbedizePlugin plugin) {
        this.access = createAccess(plugin);
        Plugin tfg = Bukkit.getPluginManager().getPlugin("TerraformGenerator");
        this.terraformGeneratorPresent = tfg != null && tfg.isEnabled();
        if (access.isAvailable()) {
            plugin.getLogger().info("Multiverse-Core integration enabled (typed API v"
                    + access.getVersion() + ").");
        } else if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
            plugin.getLogger().info("Multiverse-Core detected; API will bind when Multiverse finishes loading.");
        }
        if (terraformGeneratorPresent) {
            plugin.getLogger().info("TerraformGenerator detected — optional biome-tag bridge only "
                    + "(TFG has no public plugin API).");
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

    public boolean isTerraformGeneratorPresent() {
        return terraformGeneratorPresent;
    }

    public java.util.Optional<String> resolveWorldName(String token) {
        return access.resolveWorldName(token);
    }

    public boolean matchesConfiguredWorld(String bukkitWorldName, Iterable<String> configured, boolean resolveAliases) {
        if (!resolveAliases) {
            if (bukkitWorldName == null) {
                return false;
            }
            for (String configuredWorld : configured) {
                if (configuredWorld != null && configuredWorld.equalsIgnoreCase(bukkitWorldName)) {
                    return true;
                }
            }
            return false;
        }
        return access.matchesConfiguredWorld(bukkitWorldName, configured);
    }
}
