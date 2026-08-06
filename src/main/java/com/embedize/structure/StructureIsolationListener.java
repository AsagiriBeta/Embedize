package com.embedize.structure;

import com.embedize.EmbedizePlugin;
import com.embedize.compat.MultiverseHook;
import com.embedize.config.PluginConfig;
import com.embedize.group.GroupManager;
import com.embedize.group.StructureGroup;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.AsyncStructureSpawnEvent;
import org.bukkit.generator.structure.Structure;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-group whitelist isolation for datapack structure natural generation.
 * Vanilla minecraft: structures are never cancelled.
 */
public final class StructureIsolationListener implements Listener {

    private final EmbedizePlugin plugin;
    private final PluginConfig config;
    private final MultiverseHook multiverseHook;
    private final AtomicLong cancelled = new AtomicLong();
    private final AtomicLong allowed = new AtomicLong();
    private final AtomicLong passed = new AtomicLong();

    public StructureIsolationListener(EmbedizePlugin plugin, PluginConfig config, MultiverseHook multiverseHook) {
        this.plugin = plugin;
        this.config = config;
        this.multiverseHook = multiverseHook;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onStructureSpawn(AsyncStructureSpawnEvent event) {
        IsolationPolicy policy = config.getIsolationPolicy();
        GroupManager groups = config.getGroupManager();
        if (policy == null || !config.isEnabled()) {
            return;
        }

        NamespacedKey key = resolveKey(event.getStructure());
        String namespace = key == null ? null : key.getNamespace();
        String worldName = event.getWorld().getName();

        if (IsolationPolicy.isVanillaNamespace(namespace)) {
            passed.incrementAndGet();
            return;
        }

        Optional<StructureGroup> groupOpt = groups == null
                ? Optional.empty()
                : groups.findByNamespace(namespace == null ? "" : namespace);

        boolean hasGroup = groupOpt.isPresent();
        boolean worldAllowed = false;
        if (hasGroup) {
            StructureGroup group = groupOpt.get();
            worldAllowed = group.allowsWorld(worldName)
                    || (config.isResolveAliases() && multiverseHook.matchesConfiguredWorld(
                    worldName, group.getAllowedWorlds(), true));
        }

        IsolationPolicy.Decision decision = policy.decideWithFlags(namespace, worldAllowed, hasGroup);

        switch (decision) {
            case PASS -> passed.incrementAndGet();
            case ALLOW -> {
                allowed.incrementAndGet();
                if (config.isDebugCancellations()) {
                    plugin.getLogger().info("[allow] " + formatKey(key) + " in '" + worldName
                            + "' group=" + groupOpt.map(StructureGroup::getId).orElse("-"));
                }
            }
            case DENY -> {
                event.setCancelled(true);
                long total = cancelled.incrementAndGet();
                if (config.isDebugCancellations()) {
                    plugin.getLogger().info("[deny #" + total + "] " + formatKey(key) + " in '" + worldName
                            + "' group=" + groupOpt.map(StructureGroup::getId).orElse("-"));
                }
            }
        }
    }

    private static String formatKey(NamespacedKey key) {
        return key == null ? "<unresolved>" : key.toString();
    }

    @SuppressWarnings("removal")
    private static NamespacedKey resolveKey(Structure structure) {
        try {
            Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
            NamespacedKey resolved = registry.getKey(structure);
            if (resolved != null) {
                return resolved;
            }
        } catch (Throwable ignored) {
            // ignore
        }
        try {
            return structure.getKey();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public long getCancelledCount() {
        return cancelled.get();
    }

    public long getAllowedCount() {
        return allowed.get();
    }

    public long getPassedCount() {
        return passed.get();
    }

    public MultiverseHook getMultiverseHook() {
        return multiverseHook;
    }
}
