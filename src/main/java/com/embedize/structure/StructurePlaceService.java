package com.embedize.structure;

import com.embedize.EmbedizePlugin;
import com.embedize.structure.nms.StructurePlacementBridge;
import com.embedize.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * Debug helper: places a registered structure via the vanilla {@code /place structure}
 * engine, with chunk force-load + optional place-only hollow pre-carve for
 * {@code beard_box} (see {@code structures.place-hollow-carve}).
 */
public final class StructurePlaceService {

    private static final Set<Material> VISIBLE_MARKERS = EnumSet.of(
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
            Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
            Material.CHISELED_STONE_BRICKS, Material.STONE_BRICK_STAIRS, Material.STONE_BRICK_SLAB,
            Material.DEEPSLATE, Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES,
            Material.CHISELED_DEEPSLATE, Material.REINFORCED_DEEPSLATE, Material.GRAY_WOOL, Material.WHITE_WOOL,
            Material.HAY_BLOCK, Material.BELL, Material.CHEST, Material.CRAFTING_TABLE, Material.BOOKSHELF,
            Material.IRON_BARS, Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_WOOD, Material.POLISHED_ANDESITE,
            Material.CUT_SANDSTONE, Material.SANDSTONE, Material.SMOOTH_STONE, Material.BRICKS,
            Material.CAMPFIRE, Material.LANTERN, Material.COBBLESTONE_STAIRS, Material.OAK_STAIRS,
            Material.END_PORTAL_FRAME, Material.END_PORTAL, Material.INFESTED_STONE_BRICKS,
            Material.INFESTED_MOSSY_STONE_BRICKS, Material.INFESTED_CRACKED_STONE_BRICKS
    );

    private StructurePlaceService() {
    }

    /**
     * @return empty on async queue success; otherwise an immediate error message
     */
    public static Optional<String> placeTemplate(
            EmbedizePlugin plugin,
            CommandSender feedback,
            Location loc,
            String keyString,
            boolean includeEntities
    ) {
        if (keyString == null || keyString.isBlank()) {
            return Optional.of("Missing structure id");
        }
        if (loc == null || loc.getWorld() == null) {
            return Optional.of("No world/location for place");
        }
        String key = keyString.trim();
        StructureCatalog catalog = plugin.getStructureCatalog();
        Optional<StructureCatalog.Entry> entry = catalog.find(key);
        if (entry.isEmpty() && !key.contains(":")) {
            return Optional.of("Not found in structure catalog: " + key
                    + " (try namespace:path, e.g. minecraft:village_plains / explorify:campsite)");
        }

        String structureId = entry.map(StructureCatalog.Entry::id).orElse(key);
        boolean ignored = includeEntities;
        boolean ignoreAir = StructureAirPolicy.get().shouldIgnoreAir(structureId, loc.getWorld());

        feedback.sendMessage(Component.text(
                "Placing " + structureId + " at "
                        + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                        + " in " + loc.getWorld().getName()
                        + (ignoreAir ? " [surface-ignore-air]" : " [keep-air]")
                        + "…",
                NamedTextColor.YELLOW));

        SchedulerUtil.runForLocation(plugin, loc, () -> doPlace(plugin, feedback, loc, structureId, ignored, ignoreAir));
        return Optional.empty();
    }

    private static void doPlace(
            EmbedizePlugin plugin,
            CommandSender feedback,
            Location loc,
            String structureId,
            boolean ignored,
            boolean ignoreAir
    ) {
        try {
            World world = loc.getWorld();
            if (world == null) {
                feedback.sendMessage(Component.text("World unloaded during place.", NamedTextColor.RED));
                return;
            }
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            // Paper /place requires loaded chunks — otherwise: "That position is not loaded".
            int loaded = forceLoadRadius(world, x, z, 8);
            feedback.sendMessage(Component.text(
                    "Force-loaded " + loaded + " chunks (r=8) around place origin.",
                    NamedTextColor.GRAY));

            // Surface jigsaw uses heightmaps. Never pre-carve surface first — carving
            // collapses WORLD_SURFACE and structures vanish or bury. Place-only soft
            // ellipsoid carve is for beard_box (ancient_city) when pasting into solid rock.
            // Natural generation uses per-piece SoftBeardAdaptation. Stronghold = encapsulate.
            String idLower = structureId.toLowerCase(Locale.ROOT);
            boolean hollow = StructureAirPolicy.get().placeHollowCarveEnabled()
                    && (idLower.equals("minecraft:ancient_city")
                    || idLower.endsWith(":ancient_city"));
            int carved = 0;
            if (!hollow) {
                int surface = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                // Underground ids (stronghold etc.): keep the caller Y; do not snap to surface.
                boolean underground = idLower.contains("stronghold")
                        || idLower.contains("ancient_city")
                        || idLower.contains("trial_chamber")
                        || idLower.contains("mineshaft")
                        || idLower.contains("buried_treasure")
                        || y < surface - 8;
                if (!underground && Math.abs(surface - y) > 4) {
                    feedback.sendMessage(Component.text(
                            "Snapping place Y " + y + " → surface " + surface
                                    + " (WORLD_SURFACE).",
                            NamedTextColor.AQUA));
                    y = surface;
                }
            } else {
                carved = StructurePlacementBridge.preCarveForPlace(
                        world, x, y, z, structureId, 48, plugin.getLogger());
                if (carved > 0) {
                    feedback.sendMessage(Component.text(
                            "Place-only soft ellipsoid carve: " + carved + " terrain → cave_air "
                                    + "(beard_box stand-in; natural soft-beard is off by default)",
                            NamedTextColor.AQUA));
                }
            }

            // Absolute coords — more reliable than execute positioned ~ ~ ~ via console.
            String worldKey = world.getKey().asString();
            String cmd = String.format(
                    Locale.ROOT,
                    "execute in %s run place structure %s %d %d %d",
                    worldKey, structureId, x, y, z
            );
            boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            plugin.getLogger().info("[place] " + cmd + " dispatched=" + dispatched);

            if (!dispatched) {
                feedback.sendMessage(Component.text(
                        "Command dispatch FAILED for " + structureId
                                + " — id may be unknown to the command map.",
                        NamedTextColor.RED));
                return;
            }

            int hits = countMarkers(world, x, y, z, 28);
            int air = countAir(world, x, y, z, 16);
            NamedTextColor color = hits > 0 ? NamedTextColor.GREEN : NamedTextColor.GOLD;
            feedback.sendMessage(Component.text(
                    "Dispatched place for " + structureId
                            + (ignored ? " [entities flag unused]" : "")
                            + (ignoreAir ? " +ignore-air" : " +keep-air")
                            + (carved > 0 ? " +hollow-carve" : "")
                            + " | markerHits=" + hits
                            + " airNear=" + air,
                    color));
            if (hits <= 0) {
                feedback.sendMessage(Component.text(
                        "No marker blocks nearby. Check server log for \"Generated structure\" "
                                + "or \"There is no structure\". Wrong id / biome-only packs are common.",
                        NamedTextColor.RED));
                feedback.sendMessage(Component.text(
                        "Try tab-complete ids from /embedize place <tab> or /embedize packs.",
                        NamedTextColor.DARK_GRAY));
            } else {
                feedback.sendMessage(Component.text(
                        "Visible structure material near " + x + "," + y + "," + z + " — OK."
                                + (ignoreAir
                                ? " Surface air was not written over terrain/fluids."
                                : " Air placement kept (underground/cavity)."),
                        NamedTextColor.GREEN));
            }
        } catch (Exception ex) {
            feedback.sendMessage(Component.text(
                    "Place failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    NamedTextColor.RED));
            plugin.getLogger().log(Level.WARNING, "embedize place failed for " + structureId, ex);
        }
    }

    private static int forceLoadRadius(World world, int blockX, int blockZ, int radiusChunks) {
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        int n = 0;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                world.setChunkForceLoaded(cx + dx, cz + dz, true);
                world.getChunkAt(cx + dx, cz + dz);
                n++;
            }
        }
        return n;
    }

    private static int countMarkers(World world, int ox, int oy, int oz, int radius) {
        int hits = 0;
        int minY = Math.max(world.getMinHeight(), oy - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, oy + radius);
        for (int x = ox - radius; x <= ox + radius; x += 2) {
            for (int z = oz - radius; z <= oz + radius; z += 2) {
                for (int y = minY; y <= maxY; y += 2) {
                    if (VISIBLE_MARKERS.contains(world.getBlockAt(x, y, z).getType())) {
                        hits++;
                    }
                }
            }
        }
        return hits;
    }

    private static int countAir(World world, int ox, int oy, int oz, int radius) {
        int hits = 0;
        int minY = Math.max(world.getMinHeight(), oy - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, oy + radius);
        for (int x = ox - radius; x <= ox + radius; x += 3) {
            for (int z = oz - radius; z <= oz + radius; z += 3) {
                for (int y = minY; y <= maxY; y += 3) {
                    Material t = world.getBlockAt(x, y, z).getType();
                    if (t.isAir() || t == Material.CAVE_AIR) {
                        hits++;
                    }
                }
            }
        }
        return hits;
    }

    /** Player convenience overload. */
    public static Optional<String> placeTemplate(
            EmbedizePlugin plugin,
            Player player,
            String keyString,
            boolean includeEntities
    ) {
        return placeTemplate(plugin, player, player.getLocation(), keyString, includeEntities);
    }

    public static List<String> suggestIds(EmbedizePlugin plugin, String prefix) {
        return plugin.getStructureCatalog().suggest(prefix, 40);
    }

    public static void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text(
                "Usage: /" + label + " place <structure-id> [entities]",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(
                "       /" + label + " place <structure-id> <world> <x> <y> <z> [entities]",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(
                "Examples: minecraft:village_plains | explorify:campsite | ati_structures:ancient_temple",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Chunks are force-loaded first. Manual place of beard_box (ancient_city) may "
                        + "soft-ellipsoid carve when structures.place-hollow-carve=true (default).",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Natural soft-beard defaults off (watchdog-safe). Stronghold = encapsulate (no carve).",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Surface place uses surface-ignore-air (config): template air does not dig terrain.",
                NamedTextColor.GRAY));
    }
}
