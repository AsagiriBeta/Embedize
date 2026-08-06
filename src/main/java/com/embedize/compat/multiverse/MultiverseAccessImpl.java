package com.embedize.compat.multiverse;

import com.embedize.EmbedizePlugin;
import com.embedize.compat.MultiverseAccess;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.event.world.MVWorldCreatedEvent;
import org.mvplugins.multiverse.core.event.world.MVWorldLoadedEvent;
import org.mvplugins.multiverse.core.event.world.MVWorldRegeneratedEvent;
import org.mvplugins.multiverse.core.world.MultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.external.vavr.control.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Multiverse-Core 5 typed integration.
 *
 * <p>Loaded only via {@link Class#forName} when Multiverse-Core is present,
 * so servers without MV never load this class.
 *
 * <p>Official API (see
 * <a href="https://mvplugins.org/core/developers/developer-api-starter/">API Starter</a>
 * and <a href="https://mvplugins.org/core/developers/api-usage/">API Usage</a>):
 * <ul>
 *   <li>{@link MultiverseCoreApi#whenLoaded} / {@link MultiverseCoreApi#get} / ServicesManager</li>
 *   <li>{@link WorldManager#getWorldByNameOrAlias(String)}</li>
 *   <li>{@link MultiverseWorld#getName()} / {@link MultiverseWorld#getAlias()} / {@link MultiverseWorld#getAliasOrName()}</li>
 *   <li>{@code BlockSafety.findSafeSpawnLocation(Location)}</li>
 *   <li>{@code AsyncSafetyTeleporter} for safe teleports</li>
 *   <li>{@link MVWorldLoadedEvent} / {@link MVWorldCreatedEvent} / {@link MVWorldRegeneratedEvent}</li>
 * </ul>
 */
public final class MultiverseAccessImpl implements MultiverseAccess, Listener {

    private final EmbedizePlugin plugin;
    private volatile MultiverseCoreApi api;

    public MultiverseAccessImpl(EmbedizePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        bindApi();
    }

    private void bindApi() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
            plugin.getLogger().info("Multiverse-Core not enabled yet; waiting for PluginEnableEvent.");
            return;
        }
        tryBind("startup");
    }

    private void tryBind(String reason) {
        if (api != null) {
            return;
        }
        try {
            MultiverseCoreApi.whenLoaded(loaded -> {
                this.api = loaded;
                plugin.getLogger().info("Multiverse-Core API bound via whenLoaded() ("
                        + reason + ", v" + describeVersion() + ").");
                syncKnownWorlds("whenLoaded");
            });
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "MultiverseCoreApi.whenLoaded failed; trying fallbacks.", ex);
        }

        if (api != null) {
            return;
        }

        // Fallback: ServicesManager (documented alternative)
        try {
            RegisteredServiceProvider<MultiverseCoreApi> rsp =
                    Bukkit.getServicesManager().getRegistration(MultiverseCoreApi.class);
            if (rsp != null) {
                this.api = rsp.getProvider();
                plugin.getLogger().info("Multiverse-Core API bound via ServicesManager (v" + describeVersion() + ").");
                syncKnownWorlds("services");
                return;
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.FINE, "ServicesManager MultiverseCoreApi lookup failed", ex);
        }

        // Fallback: singleton (throws if not ready)
        try {
            if (MultiverseCoreApi.isLoaded()) {
                this.api = MultiverseCoreApi.get();
                plugin.getLogger().info("Multiverse-Core API bound via MultiverseCoreApi.get() (v"
                        + describeVersion() + ").");
                syncKnownWorlds("singleton");
            }
        } catch (IllegalStateException ex) {
            plugin.getLogger().fine("MultiverseCoreApi.get() not ready yet: " + ex.getMessage());
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to bind MultiverseCoreApi", ex);
        }
    }

    private String describeVersion() {
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        return mv != null ? mv.getDescription().getVersion() : "unknown";
    }

    private Optional<WorldManager> worldManager() {
        MultiverseCoreApi a = api;
        if (a == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(a.getWorldManager());
    }

    private Optional<MultiverseWorld> findMvWorld(String worldNameOrAlias) {
        if (worldNameOrAlias == null || worldNameOrAlias.isBlank()) {
            return Optional.empty();
        }
        return worldManager().flatMap(mgr -> {
            Option<MultiverseWorld> byAlias = mgr.getWorldByNameOrAlias(worldNameOrAlias);
            if (byAlias.isDefined()) {
                return Optional.of(byAlias.get());
            }
            Option<MultiverseWorld> byName = mgr.getWorld(worldNameOrAlias);
            return byName.isDefined() ? Optional.of(byName.get()) : Optional.empty();
        });
    }

    @Override
    public boolean isAvailable() {
        return api != null && worldManager().isPresent();
    }

    @Override
    public String getVersion() {
        return describeVersion();
    }

    @Override
    public Optional<String> resolveWorldName(String token) {
        Optional<MultiverseWorld> mv = findMvWorld(token);
        if (mv.isPresent()) {
            return Optional.of(mv.get().getName());
        }
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        World direct = Bukkit.getWorld(token);
        return Optional.of(direct != null ? direct.getName() : token.trim());
    }

    @Override
    public Optional<String> getWorldAlias(String worldName) {
        return findMvWorld(worldName)
                .map(MultiverseWorld::getAlias)
                .filter(alias -> alias != null && !alias.isBlank());
    }

    @Override
    public boolean matchesConfiguredWorld(String bukkitWorldName, Iterable<String> configured) {
        if (bukkitWorldName == null) {
            return false;
        }
        String needle = bukkitWorldName.toLowerCase(Locale.ROOT);
        Optional<String> alias = getWorldAlias(bukkitWorldName).map(a -> a.toLowerCase(Locale.ROOT));

        for (String configuredWorld : configured) {
            if (configuredWorld == null || configuredWorld.isBlank()) {
                continue;
            }
            if (configuredWorld.equalsIgnoreCase(bukkitWorldName)) {
                return true;
            }
            if (alias.isPresent() && configuredWorld.equalsIgnoreCase(alias.get())) {
                return true;
            }
            // Config may store an alias; resolve to canonical name
            Optional<String> resolved = resolveWorldName(configuredWorld);
            if (resolved.isPresent() && resolved.get().equalsIgnoreCase(bukkitWorldName)) {
                return true;
            }
            // Also accept getAliasOrName equality
            Optional<MultiverseWorld> mv = findMvWorld(configuredWorld);
            if (mv.isPresent()) {
                String aliasOrName = mv.get().getAliasOrName();
                if (aliasOrName != null && aliasOrName.toLowerCase(Locale.ROOT).equals(needle)) {
                    return true;
                }
                if (mv.get().getName().equalsIgnoreCase(bukkitWorldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<String> listManagedWorldNames() {
        return worldManager()
                .map(mgr -> {
                    List<String> names = new ArrayList<>();
                    for (MultiverseWorld world : mgr.getWorlds()) {
                        names.add(world.getName());
                    }
                    names.sort(String.CASE_INSENSITIVE_ORDER);
                    return names;
                })
                .orElseGet(List::of);
    }

    @Override
    public boolean isManagedWorld(String worldName) {
        return findMvWorld(worldName).isPresent();
    }

    @Override
    public Optional<Location> findSafeLocation(Location around) {
        if (around == null || around.getWorld() == null || api == null) {
            return Optional.empty();
        }
        try {
            Location safe = api.getBlockSafety().findSafeSpawnLocation(around);
            return Optional.ofNullable(safe);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "BlockSafety.findSafeSpawnLocation failed", t);
            return Optional.empty();
        }
    }

    @Override
    public boolean teleportSafely(Entity entity, Location destination) {
        if (entity == null || destination == null || destination.getWorld() == null) {
            return false;
        }
        // Prefer sync BlockSafety for knockback / command paths; AsyncSafetyTeleporter is async.
        Location target = findSafeLocation(destination).orElse(destination);
        MultiverseCoreApi a = api;
        if (a != null) {
            try {
                a.getSafetyTeleporter()
                        .to(target)
                        .checkSafety(true)
                        .teleportSingle(entity);
                return true;
            } catch (Throwable t) {
                plugin.getLogger().log(Level.FINE, "AsyncSafetyTeleporter failed; falling back to Bukkit teleport", t);
            }
        }
        return entity.teleport(target);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPluginEnable(PluginEnableEvent event) {
        if ("Multiverse-Core".equalsIgnoreCase(event.getPlugin().getName())) {
            tryBind("PluginEnableEvent");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMvWorldLoaded(MVWorldLoadedEvent event) {
        onWorldReady(event.getWorld(), "MVWorldLoadedEvent");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMvWorldCreated(MVWorldCreatedEvent event) {
        onWorldReady(event.getWorld(), "MVWorldCreatedEvent");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMvWorldRegenerated(MVWorldRegeneratedEvent event) {
        onWorldReady(event.getWorld(), "MVWorldRegeneratedEvent");
    }

    private void onWorldReady(MultiverseWorld world, String reason) {
        if (world == null) {
            return;
        }
        String name = world.getName();
        plugin.getLogger().fine("MV world ready: " + name + " alias=" + world.getAliasOrName() + " (" + reason + ")");
        if (plugin.getBorderManager() != null) {
            plugin.getBorderManager().onWorldReady(name);
        }
    }

    private void syncKnownWorlds(String reason) {
        for (String name : listManagedWorldNames()) {
            plugin.getLogger().fine("MV sync world '" + name + "' (" + reason + ")");
            if (plugin.getBorderManager() != null) {
                plugin.getBorderManager().onWorldReady(name);
            }
        }
    }
}
