package com.dsi.isolator.compat;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Soft Multiverse-Core hook via reflection so we do not hard-depend on MV APIs / Vavr.
 */
public final class MultiverseHook {

    private final Logger logger;
    private final boolean present;
    private final Object worldManager;
    private final Method getWorldByNameOrAlias;

    public MultiverseHook(Logger logger) {
        this.logger = logger;
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mv == null || !mv.isEnabled()) {
            this.present = false;
            this.worldManager = null;
            this.getWorldByNameOrAlias = null;
            return;
        }

        boolean ok = false;
        Object manager = null;
        Method byAlias = null;
        try {
            Class<?> apiClass = Class.forName("org.mvplugins.multiverse.core.MultiverseCoreApi");
            Method get = apiClass.getMethod("get");
            Object api = get.invoke(null);
            Method getWorldManager = apiClass.getMethod("getWorldManager");
            manager = getWorldManager.invoke(api);
            byAlias = manager.getClass().getMethod("getWorldByNameOrAlias", String.class);
            ok = true;
        } catch (ReflectiveOperationException ignored) {
            try {
                Method getCore = mv.getClass().getMethod("getMVWorldManager");
                manager = getCore.invoke(mv);
                byAlias = manager.getClass().getMethod("getMVWorld", String.class);
                ok = true;
            } catch (ReflectiveOperationException ex) {
                logger.warning("Multiverse-Core detected but API could not be bound; alias resolution disabled.");
            }
        }
        this.present = ok;
        this.worldManager = manager;
        this.getWorldByNameOrAlias = byAlias;
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isTerraformGeneratorPresent() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("TerraformGenerator");
        return plugin != null && plugin.isEnabled();
    }

    public Optional<String> resolveWorldName(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        World direct = Bukkit.getWorld(token);
        if (direct != null) {
            return Optional.of(direct.getName());
        }
        if (!present || worldManager == null || getWorldByNameOrAlias == null) {
            return Optional.of(token);
        }
        try {
            Object optionOrWorld = getWorldByNameOrAlias.invoke(worldManager, token);
            Object mvWorld = unwrapOption(optionOrWorld);
            if (mvWorld == null) {
                return Optional.of(token);
            }
            Method name = mvWorld.getClass().getMethod("getName");
            Object value = name.invoke(mvWorld);
            if (value instanceof String s && !s.isBlank()) {
                return Optional.of(s);
            }
        } catch (ReflectiveOperationException ex) {
            logger.fine("MV resolve failed for '" + token + "': " + ex.getMessage());
        }
        return Optional.of(token);
    }

    public boolean matchesConfiguredWorld(String bukkitWorldName, Iterable<String> configured, boolean resolveAliases) {
        if (bukkitWorldName == null) {
            return false;
        }
        for (String configuredWorld : configured) {
            if (configuredWorld.equalsIgnoreCase(bukkitWorldName)) {
                return true;
            }
            if (!resolveAliases || !present) {
                continue;
            }
            Optional<String> resolved = resolveWorldName(configuredWorld);
            if (resolved.isPresent() && resolved.get().equalsIgnoreCase(bukkitWorldName)) {
                return true;
            }
            if (aliasEquals(bukkitWorldName, configuredWorld)) {
                return true;
            }
        }
        return false;
    }

    private boolean aliasEquals(String bukkitWorldName, String configuredToken) {
        if (worldManager == null || getWorldByNameOrAlias == null) {
            return false;
        }
        try {
            Object optionOrWorld = getWorldByNameOrAlias.invoke(worldManager, bukkitWorldName);
            Object mvWorld = unwrapOption(optionOrWorld);
            if (mvWorld == null) {
                return false;
            }
            Method alias;
            try {
                alias = mvWorld.getClass().getMethod("getAliasOrName");
            } catch (NoSuchMethodException ignored) {
                try {
                    alias = mvWorld.getClass().getMethod("getAlias");
                } catch (NoSuchMethodException ignored2) {
                    return false;
                }
            }
            Object value = alias.invoke(mvWorld);
            return value instanceof String s && s.equalsIgnoreCase(configuredToken);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private static Object unwrapOption(Object maybeOption) {
        if (maybeOption == null) {
            return null;
        }
        try {
            Method isDefined = maybeOption.getClass().getMethod("isDefined");
            Object defined = isDefined.invoke(maybeOption);
            if (defined instanceof Boolean b && !b) {
                return null;
            }
            Method get = maybeOption.getClass().getMethod("get");
            return get.invoke(maybeOption);
        } catch (ReflectiveOperationException ignored) {
            return maybeOption;
        }
    }
}
