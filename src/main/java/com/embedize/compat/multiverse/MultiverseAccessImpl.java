package com.embedize.compat.multiverse;

import com.embedize.EmbedizePlugin;
import com.embedize.compat.MultiverseAccess;
import com.embedize.terrain.EmbedizeGenerator;
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
import org.mvplugins.multiverse.core.world.options.ImportWorldOptions;
import org.mvplugins.multiverse.core.world.options.RemoveWorldOptions;
import org.mvplugins.multiverse.external.vavr.control.Option;

import java.util.ArrayList;
import java.util.List;
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
    public boolean detachWorldForReset(String worldName) {
        if (worldName == null || worldName.isBlank() || api == null) {
            return false;
        }
        Optional<WorldManager> mgrOpt = worldManager();
        Optional<MultiverseWorld> mvOpt = findMvWorld(worldName);
        if (mgrOpt.isEmpty() || mvOpt.isEmpty()) {
            return false;
        }
        WorldManager mgr = mgrOpt.get();
        MultiverseWorld mvWorld = mvOpt.get();
        try {
            // removeWorld: unload (save=false) then drop config — never re-load to delete.
            RemoveWorldOptions options = RemoveWorldOptions.world(mvWorld)
                    .saveBukkitWorld(false)
                    .unloadBukkitWorld(true);
            var result = mgr.removeWorld(options);
            boolean ok = result.isSuccess();
            plugin.getLogger().info("[resource-reset] Multiverse removeWorld('" + worldName
                    + "', save=false) => " + (ok ? "ok" : String.valueOf(result)));
            return ok;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[resource-reset] Multiverse detachWorldForReset failed for '" + worldName + "'", t);
            return false;
        }
    }

    @Override
    public boolean registerLoadedWorld(World world, String generator) {
        if (world == null || api == null) {
            return false;
        }
        if (isManagedWorld(world.getName())) {
            return true;
        }
        Optional<WorldManager> mgrOpt = worldManager();
        if (mgrOpt.isEmpty()) {
            return false;
        }
        try {
            ImportWorldOptions options = ImportWorldOptions.worldName(world.getName())
                    .environment(world.getEnvironment())
                    .useSpawnAdjust(false)
                    .doFolderCheck(false);
            if (generator != null && !generator.isBlank()) {
                options = options.generator(generator);
            }
            var result = mgrOpt.get().importWorld(options);
            boolean ok = result.isSuccess();
            plugin.getLogger().info("[resource-reset] Multiverse importWorld(already-loaded '"
                    + world.getName() + "') => " + (ok ? "ok" : String.valueOf(result)));
            return ok;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[resource-reset] Multiverse registerLoadedWorld failed for '"
                            + world.getName() + "'", t);
            return false;
        }
    }

    @Override
    public boolean configureResourceWorld(String worldName, String alias) {
        Optional<MultiverseWorld> mvOpt = findMvWorld(worldName);
        if (mvOpt.isEmpty()) {
            return false;
        }
        MultiverseWorld mv = mvOpt.get();
        try {
            if (alias != null && !alias.isBlank()) {
                mv.setAlias(alias);
            }
            mv.setAdjustSpawn(false);
            mv.setKeepSpawnInMemory(false);
            worldManager().ifPresent(mgr -> mgr.saveWorldsConfig());
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[resource-reset] Multiverse configureResourceWorld failed for '"
                            + worldName + "'", t);
            return false;
        }
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
        World bukkitWorld = Bukkit.getWorld(name);
        if (bukkitWorld == null) {
            return;
        }
        if (bukkitWorld.getGenerator() instanceof EmbedizeGenerator generator) {
            if (plugin.getTerrainWorldListener() != null) {
                plugin.getTerrainWorldListener().refreshWorld(bukkitWorld);
            }
            plugin.getLogger().info("Verified MV Embedize world '" + name + "' generator="
                    + generator.kind() + " id=" + generator.generatorId()
                    + " (" + reason + ")");
        } else if (bukkitWorld.getGenerator() != null) {
            plugin.getLogger().fine("MV world '" + name + "' uses non-Embedize generator "
                    + bukkitWorld.getGenerator().getClass().getSimpleName() + " (" + reason + ")");
        }
    }

    private void syncKnownWorlds(String reason) {
        for (String name : listManagedWorldNames()) {
            plugin.getLogger().fine("MV sync world '" + name + "' (" + reason + ")");
            if (plugin.getBorderManager() != null) {
                plugin.getBorderManager().onWorldReady(name);
            }
            World bukkitWorld = Bukkit.getWorld(name);
            if (bukkitWorld != null
                    && bukkitWorld.getGenerator() instanceof EmbedizeGenerator
                    && plugin.getTerrainWorldListener() != null) {
                plugin.getTerrainWorldListener().refreshWorld(bukkitWorld);
            }
        }
    }
}
