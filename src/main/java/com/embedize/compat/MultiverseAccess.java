package com.embedize.compat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.List;
import java.util.Optional;

/**
 * Soft Multiverse-Core 5 integration surface.
 *
 * <p>No Multiverse types leak through this interface so servers without
 * Multiverse-Core never load MV classes. Implementation:
 * {@code com.embedize.compat.multiverse.MultiverseAccessImpl}.
 *
 * <p>Docs: <a href="https://mvplugins.org/core/developers/">MV5 Developers</a>
 */
public interface MultiverseAccess {

    MultiverseAccess NOOP = new MultiverseAccess() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String getVersion() {
            return "unavailable";
        }

        @Override
        public Optional<String> resolveWorldName(String token) {
            if (token == null || token.isBlank()) {
                return Optional.empty();
            }
            World direct = org.bukkit.Bukkit.getWorld(token);
            return Optional.of(direct != null ? direct.getName() : token.trim());
        }

        @Override
        public Optional<String> getWorldAlias(String worldName) {
            return Optional.empty();
        }

        @Override
        public boolean matchesConfiguredWorld(String bukkitWorldName, Iterable<String> configured) {
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

        @Override
        public List<String> listManagedWorldNames() {
            return List.of();
        }

        @Override
        public boolean isManagedWorld(String worldName) {
            return false;
        }

        @Override
        public Optional<Location> findSafeLocation(Location around) {
            return Optional.empty();
        }

        @Override
        public boolean teleportSafely(Entity entity, Location destination) {
            return entity != null && destination != null && entity.teleport(destination);
        }
    };

    boolean isAvailable();

    /** Multiverse-Core plugin version string, or {@code unavailable}. */
    String getVersion();

    /**
     * Resolve a configured token (folder name or Multiverse alias) to the
     * canonical Multiverse / Bukkit world name.
     */
    Optional<String> resolveWorldName(String token);

    /** Multiverse alias for a world, if set. */
    Optional<String> getWorldAlias(String worldName);

    /**
     * Whether {@code bukkitWorldName} matches any configured entry
     * (exact name or via Multiverse alias / {@code getWorldByNameOrAlias}).
     */
    boolean matchesConfiguredWorld(String bukkitWorldName, Iterable<String> configured);

    /** Names of all worlds Multiverse knows about (loaded + unloaded). */
    List<String> listManagedWorldNames();

    boolean isManagedWorld(String worldName);

    /**
     * Optional Multiverse {@code BlockSafety.findSafeSpawnLocation} helper
     * for border knockback destinations.
     */
    Optional<Location> findSafeLocation(Location around);

    /**
     * Teleport using Multiverse safety checking when available.
     */
    boolean teleportSafely(Entity entity, Location destination);
}
