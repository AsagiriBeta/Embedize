package com.embedize.compat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

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
            return Optional.of(token.trim());
        }

        @Override
        public Optional<String> getWorldAlias(String worldName) {
            return Optional.empty();
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

        @Override
        public boolean detachWorldForReset(String worldName) {
            return false;
        }

        @Override
        public boolean registerLoadedWorld(World world, @Nullable String generator) {
            return false;
        }

        @Override
        public boolean configureResourceWorld(String worldName, String alias) {
            return false;
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

    /**
     * Unload the Bukkit world <em>without saving</em> and remove it from Multiverse
     * config. Resource reset deletes the folder next, so flushing chunks only stalls
     * the server thread (minutes on large resource worlds).
     *
     * @return {@code true} if Multiverse no longer manages the world name
     */
    boolean detachWorldForReset(String worldName);

    /**
     * Register an already-loaded Bukkit world with Multiverse (no spawn adjust,
     * no folder check). Prefer this over console {@code mv import}, which can
     * block for tens of seconds when it first touches Embedize spawn chunks.
     */
    boolean registerLoadedWorld(World world, @Nullable String generator);

    /**
     * Set alias / adjust-spawn / keep-spawn-in-memory via Multiverse API.
     */
    boolean configureResourceWorld(String worldName, String alias);
}
