package com.embedize.command;

import com.embedize.EmbedizePlugin;
import com.embedize.border.BorderManager;
import com.embedize.border.WorldBorderData;
import com.embedize.compat.LuckPermsHook;
import com.embedize.compat.MultiverseHook;
import com.embedize.reset.ResourceWorldResetService;
import com.embedize.structure.StructureCatalog;
import com.embedize.structure.StructurePlaceService;
import com.embedize.terrain.TerrainGeneratorFactory;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class EmbedizeCommand implements CommandExecutor, TabCompleter, BasicCommand {

    private final EmbedizePlugin plugin;
    private final ThreadLocal<org.bukkit.entity.Entity> brigadierExecutor = new ThreadLocal<>();

    public EmbedizeCommand(EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        brigadierExecutor.set(stack.getExecutor());
        try {
            onCommand(stack.getSender(), null, "embedize", args);
        } finally {
            brigadierExecutor.remove();
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        String[] normalized = args.length == 0 ? new String[]{""} : args;
        List<String> suggestions = onTabComplete(stack.getSender(), null, "embedize", normalized);
        return suggestions != null ? suggestions : List.of();
    }

    @Override
    public boolean canUse(@NotNull CommandSender sender) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        return lp.hasAdmin(sender)
                || sender.hasPermission("embedize.command.reload")
                || sender.hasPermission("embedize.command.status")
                || sender.hasPermission("embedize.command.place")
                || sender.hasPermission("embedize.command.reset")
                || sender.hasPermission("embedize.border.admin");
    }

    @Override
    public @Nullable String permission() {
        // Subcommands enforce their own nodes; a blanket embedize.admin here made
        // /embedize silently unusable for OPs when LuckPerms does not inherit OP perms.
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @Nullable Command command, @NotNull String label, @NotNull String[] args) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!lp.hasAdmin(sender) && !sender.hasPermission("embedize.command.reload")) {
                    deny(sender);
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(Component.text("Embedize reloaded.", NamedTextColor.GREEN));
            }
            case "status" -> {
                if (!lp.hasAdmin(sender) && !sender.hasPermission("embedize.command.status")) {
                    deny(sender);
                    return true;
                }
                sendStatus(sender);
            }
            case "worlds" -> {
                if (!lp.hasAdmin(sender)) {
                    deny(sender);
                    return true;
                }
                sendWorlds(sender);
            }
            case "packs" -> handlePacks(sender);
            case "gens", "generators" -> sendGenerators(sender);
            case "border" -> handleBorder(sender, label, args);
            case "place" -> handlePlace(sender, label, args);
            case "loadchunks", "forcegen" -> handleLoadChunks(sender, label, args);
            case "diagnose", "diag" -> handleDiagnose(sender, label, args);
            case "scan" -> handleScan(sender, label, args);
            case "reset" -> handleReset(sender, args);
            case "resetstatus" -> handleResetStatus(sender);
            case "resetunlock" -> handleResetUnlock(sender);
            case "help" -> sendHelp(sender, label);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void handleReset(CommandSender sender, String[] args) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        if (!lp.hasAdmin(sender) && !sender.hasPermission("embedize.command.reset")) {
            deny(sender);
            return;
        }
        ResourceWorldResetService service = plugin.getResourceWorldResetService();
        if (service == null) {
            sender.sendMessage(Component.text("Resource reset service unavailable.", NamedTextColor.RED));
            return;
        }
        String reason = args.length < 2 ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        sender.sendMessage(Component.text("已提交资源世界重置流程，完成结果会通过聊天广播提示。", NamedTextColor.GREEN));
        service.requestManualReset(reason);
    }

    private void handleResetStatus(CommandSender sender) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        if (!lp.hasAdmin(sender) && !sender.hasPermission("embedize.command.reset")) {
            deny(sender);
            return;
        }
        ResourceWorldResetService service = plugin.getResourceWorldResetService();
        if (service == null) {
            sender.sendMessage(Component.text("Resource reset service unavailable.", NamedTextColor.RED));
            return;
        }
        ResourceResetCommand.sendStatus(sender, service);
    }

    private void handleResetUnlock(CommandSender sender) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        if (!lp.hasAdmin(sender) && !sender.hasPermission("embedize.command.reset")) {
            deny(sender);
            return;
        }
        ResourceWorldResetService service = plugin.getResourceWorldResetService();
        if (service == null) {
            sender.sendMessage(Component.text("Resource reset service unavailable.", NamedTextColor.RED));
            return;
        }
        service.forceUnlock();
        sender.sendMessage(Component.text("已强制清除资源世界重置运行锁。", NamedTextColor.GREEN));
    }

    private void handlePlace(CommandSender sender, String label, String[] args) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        if (!lp.hasAdmin(sender) && !sender.hasPermission("embedize.command.place")) {
            deny(sender);
            return;
        }
        if (args.length < 2) {
            StructurePlaceService.sendUsage(sender, label);
            return;
        }
        Player player = resolvePlayer(sender);
        boolean entities = false;
        Location loc;
        if (args.length >= 6) {
            // /embedize place <id> <world> <x> <y> <z> [entities]
            World world = Bukkit.getWorld(args[2]);
            if (world == null) {
                sender.sendMessage(Component.text("World not found: " + args[2], NamedTextColor.RED));
                return;
            }
            try {
                loc = new Location(
                        world,
                        Integer.parseInt(args[3]) + 0.5,
                        Integer.parseInt(args[4]),
                        Integer.parseInt(args[5]) + 0.5
                );
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("Invalid coordinates.", NamedTextColor.RED));
                return;
            }
            entities = args.length >= 7 && args[6].equalsIgnoreCase("entities");
        } else if (player != null) {
            loc = player.getLocation();
            entities = args.length >= 3 && args[2].equalsIgnoreCase("entities");
        } else {
            sender.sendMessage(Component.text(
                    "Console must use: /" + label + " place <id> <world> <x> <y> <z>",
                    NamedTextColor.RED));
            StructurePlaceService.sendUsage(sender, label);
            return;
        }
        StructurePlaceService.placeTemplate(plugin, sender, loc, args[1], entities)
                .ifPresent(err -> sender.sendMessage(Component.text(err, NamedTextColor.RED)));
    }

    private @Nullable Player resolvePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        org.bukkit.entity.Entity exec = brigadierExecutor.get();
        if (exec instanceof Player player) {
            return player;
        }
        return null;
    }

    private void handlePacks(CommandSender sender) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        if (!lp.hasAdmin(sender) && !sender.hasPermission("embedize.command.status")) {
            deny(sender);
            return;
        }
        sender.sendMessage(Component.text("--- Embedize content ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
                "Biome pack: " + com.embedize.bootstrap.EmbedizeBootstrap.BIOME_PACK_ID
                        + " (" + com.embedize.terrain.biome.EmbedizeBiomes.catalogSize()
                        + " custom biomes, plugin-owned)",
                NamedTextColor.AQUA));
        StructureCatalog catalog = plugin.getStructureCatalog();
        sender.sendMessage(Component.text(
                "Structure packs: " + com.embedize.bootstrap.EmbedizeBootstrap.STRUCTURE_PACKS_ROOT
                        + " (" + catalog.packNames().size() + " sources, vanilla jigsaw)",
                NamedTextColor.AQUA));
        sender.sendMessage(Component.text("--- Reference packs (one datapack each) ---", NamedTextColor.GOLD));
        for (String pack : catalog.packNames()) {
            sender.sendMessage(Component.text(" - " + pack, NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text(
                "catalog: structures=" + catalog.structureCount()
                        + " nbt≈" + catalog.nbtCount()
                        + " tags=" + catalog.tagCount()
                        + " (OW " + catalog.forWorld(World.Environment.NORMAL).size()
                        + " / Nether " + catalog.forWorld(World.Environment.NETHER).size()
                        + " / End " + catalog.forWorld(World.Environment.THE_END).size() + ")",
                NamedTextColor.DARK_AQUA));
    }

    private void sendGenerators(CommandSender sender) {
        sender.sendMessage(Component.text("--- MV generators ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("mv create resource normal --generator Embedize", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("mv create resource_nether nether --generator Embedize:nether", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("mv create resource_end the_end --generator Embedize:end", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "Aliases: Embedize:Embedize-Nether | Embedize:Embedize-End",
                NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text(
                "Structures are dimension-locked (normal/nether/end never cross).",
                NamedTextColor.DARK_AQUA));
    }

    private void sendStatus(CommandSender sender) {
        MultiverseHook mv = plugin.getMultiverseHook();
        StructureCatalog catalog = plugin.getStructureCatalog();
        sender.sendMessage(Component.text("--- Embedize ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
                "Java terrain + custom biomes + vanilla structure/jigsaw engine",
                NamedTextColor.DARK_AQUA));
        sender.sendMessage(Component.text(
                "custom biomes: " + com.embedize.terrain.biome.EmbedizeBiomes.catalogSize()
                        + " (embedize:* via bundled biome pack)",
                NamedTextColor.AQUA));
        sender.sendMessage(Component.text(
                "gens: " + TerrainGeneratorFactory.describe(null)
                        + " | " + TerrainGeneratorFactory.describe("nether")
                        + " | " + TerrainGeneratorFactory.describe("end"),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "structures: " + catalog.structureCount()
                        + " (OW " + catalog.forWorld(World.Environment.NORMAL).size()
                        + " / Nether " + catalog.forWorld(World.Environment.NETHER).size()
                        + " / End " + catalog.forWorld(World.Environment.THE_END).size() + ")"
                        + " nbt≈" + catalog.nbtCount()
                        + " tags=" + catalog.tagCount()
                        + " packs=" + catalog.packNames().size()
                        + " | engine=vanilla (shouldGenerateStructures)",
                NamedTextColor.AQUA));
        sender.sendMessage(Component.text("borders: " + plugin.getBorderManager().worldNames(), NamedTextColor.GRAY));
        ResourceWorldResetService reset = plugin.getResourceWorldResetService();
        if (reset != null) {
            sender.sendMessage(Component.text("resource-reset: " + reset.describeStatusLines(), NamedTextColor.GRAY));
        }
        var papi = plugin.getPlaceholderApiHook();
        sender.sendMessage(Component.text(
                "PlaceholderAPI: " + (papi != null && papi.isRegistered()
                        ? "yes (%resource_reset_*)"
                        : "no"),
                NamedTextColor.GRAY));
        String mvLine = mv.isPresent()
                ? "yes v" + mv.access().getVersion()
                : "no";
        boolean tfg = Bukkit.getPluginManager().getPlugin("TerraformGenerator") != null;
        sender.sendMessage(Component.text(
                "Multiverse: " + mvLine + "  TFG: " + (tfg ? "REMOVE IT" : "absent OK"),
                tfg ? NamedTextColor.RED : NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/embedize gens  — copy-paste MV create lines", NamedTextColor.DARK_GRAY));
    }

    private void sendWorlds(CommandSender sender) {
        sender.sendMessage(Component.text("Loaded worlds:", NamedTextColor.GOLD));
        StructureCatalog catalog = plugin.getStructureCatalog();
        for (World world : Bukkit.getWorlds()) {
            String gen = world.getGenerator() == null ? "vanilla" : world.getGenerator().getClass().getSimpleName();
            int structs = catalog.forWorld(world.getEnvironment()).size();
            boolean vanillaStructures = world.getGenerator() instanceof com.embedize.terrain.EmbedizeGenerator;
            sender.sendMessage(Component.text(
                    " - " + world.getName() + " env=" + world.getEnvironment()
                            + " gen=" + gen
                            + " catalog=" + structs
                            + (vanillaStructures ? " structures=vanilla-engine" : ""),
                    NamedTextColor.GRAY));
        }
    }

    private void handleBorder(CommandSender sender, String label, String[] args) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        if (!lp.hasAdmin(sender) && !sender.hasPermission("embedize.border.admin")) {
            deny(sender);
            return;
        }
        BorderManager bm = plugin.getBorderManager();
        if (args.length < 2) {
            sendBorderHelp(sender, label);
            return;
        }
        if (args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(Component.text("Borders: " + bm.worldNames(), NamedTextColor.GOLD));
            return;
        }
        String worldName = args[1];
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /" + label + " border <world> info|clear|set|shape", NamedTextColor.RED));
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "info" -> {
                Optional<WorldBorderData> border = bm.getBorder(worldName);
                sender.sendMessage(Component.text(border.map(Object::toString).orElse("No border."), NamedTextColor.GRAY));
            }
            case "clear" -> {
                bm.clearBorder(worldName);
                sender.sendMessage(Component.text("Cleared border for " + worldName, NamedTextColor.GREEN));
            }
            case "shape" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: shape square|round", NamedTextColor.RED));
                    return;
                }
                WorldBorderData.Shape shape = args[3].equalsIgnoreCase("round")
                        ? WorldBorderData.Shape.ROUND : WorldBorderData.Shape.SQUARE;
                Optional<WorldBorderData> existingShape = bm.getBorder(worldName);
                if (existingShape.isEmpty()) {
                    sender.sendMessage(Component.text("No border for " + worldName + " - set one first.", NamedTextColor.RED));
                    return;
                }
                WorldBorderData cur = existingShape.get();
                bm.setBorder(worldName, new WorldBorderData(
                        cur.getX(), cur.getZ(), cur.getRadiusX(), cur.getRadiusZ(), shape));
                sender.sendMessage(Component.text("Shape set to " + shape, NamedTextColor.GREEN));
            }
            case "set" -> handleBorderSet(sender, label, worldName, args);
            default -> sendBorderHelp(sender, label);
        }
    }

    private void handleBorderSet(CommandSender sender, String label, String worldName, String[] args) {
        BorderManager bm = plugin.getBorderManager();
        try {
            if (args.length < 4) {
                sender.sendMessage(Component.text(
                        "Usage: /" + label + " border " + worldName + " set <radius> [x z|spawn]",
                        NamedTextColor.RED));
                return;
            }
            double radiusX;
            double radiusZ;
            double x;
            double z;
            if (args.length == 4) {
                radiusX = radiusZ = Double.parseDouble(args[3]);
                World w = Bukkit.getWorld(worldName);
                if (w != null) {
                    Location spawn = w.getSpawnLocation();
                    x = spawn.getX();
                    z = spawn.getZ();
                } else {
                    x = z = 0;
                }
            } else if (args.length == 5 && args[4].equalsIgnoreCase("spawn")) {
                radiusX = radiusZ = Double.parseDouble(args[3]);
                World w = Bukkit.getWorld(worldName);
                Location spawn = w != null ? w.getSpawnLocation() : new Location(null, 0, 64, 0);
                x = spawn.getX();
                z = spawn.getZ();
            } else if (args.length == 6) {
                radiusX = radiusZ = Double.parseDouble(args[3]);
                x = Double.parseDouble(args[4]);
                z = Double.parseDouble(args[5]);
            } else if (args.length >= 7) {
                radiusX = Double.parseDouble(args[3]);
                radiusZ = Double.parseDouble(args[4]);
                x = Double.parseDouble(args[5]);
                z = Double.parseDouble(args[6]);
            } else {
                sender.sendMessage(Component.text("Invalid set syntax.", NamedTextColor.RED));
                return;
            }
            if (radiusX <= 0 || radiusZ <= 0) {
                sender.sendMessage(Component.text("Radius must be positive.", NamedTextColor.RED));
                return;
            }
            Optional<WorldBorderData> existing = bm.getBorder(worldName);
            WorldBorderData.Shape shapeOverride = existing.map(WorldBorderData::getShapeOverride).orElse(null);
            WorldBorderData border = new WorldBorderData(
                    x, z, (int) Math.round(radiusX), (int) Math.round(radiusZ), shapeOverride);
            bm.setBorder(worldName, border);
            sender.sendMessage(Component.text("Border set for '" + worldName + "': " + border, NamedTextColor.GREEN));
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
        } catch (Exception ex) {
            sender.sendMessage(Component.text("Border set failed: " + ex.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleLoadChunks(CommandSender sender, String label, String[] args) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        if (!lp.hasAdmin(sender) && !sender.hasPermission("embedize.command.place")) {
            deny(sender);
            return;
        }
        // /embedize loadchunks <world> <blockX> <blockZ> [radiusChunks]
        if (args.length < 4) {
            sender.sendMessage(Component.text(
                    "/" + label + " loadchunks <world> <blockX> <blockZ> [radiusChunks]",
                    NamedTextColor.YELLOW));
            return;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage(Component.text("World not found: " + args[1], NamedTextColor.RED));
            return;
        }
        final int blockX;
        final int blockZ;
        final int radius;
        try {
            blockX = Integer.parseInt(args[2]);
            blockZ = Integer.parseInt(args[3]);
            radius = args.length >= 5 ? Math.max(0, Math.min(8, Integer.parseInt(args[4]))) : 3;
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
            return;
        }
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        sender.sendMessage(Component.text(
                "Loading chunks around " + world.getName() + " " + cx + "," + cz
                        + " r=" + radius + " (Bukkit getChunk + placeInChunk)…",
                NamedTextColor.GRAY));

        Runnable work = () -> {
            int placed = 0;
            int loaded = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int chunkX = cx + dx;
                    int chunkZ = cz + dz;
                    // Keep chunks resident for RCON verification — getChunkAt alone can
                    // unload immediately when no players / no vanilla forceload.
                    world.setChunkForceLoaded(chunkX, chunkZ, true);
                    world.getChunkAt(chunkX, chunkZ);
                    loaded++;
                    placed += com.embedize.structure.nms.StructurePlacementBridge.placeStartsInWorld(
                            world, chunkX, chunkZ, plugin.getLogger());
                }
            }
            String msg = "loadchunks done: loaded=" + loaded + " placeInChunkCalls=" + placed
                    + " bridge=" + com.embedize.structure.nms.StructurePlacementBridge.available();
            plugin.getLogger().info("[loadchunks] " + msg);
            sender.sendMessage(Component.text(msg, NamedTextColor.GREEN));
        };

        // Console/RCON runs on the main thread on Paper — run inline so RCON waits.
        // Folia must use the region scheduler (never block the global region).
        if (com.embedize.util.SchedulerUtil.isFolia()) {
            com.embedize.util.SchedulerUtil.runForLocation(
                    plugin, new Location(world, blockX, 80, blockZ), work);
        } else {
            work.run();
        }
    }

    private void handleDiagnose(CommandSender sender, String label, String[] args) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        if (!lp.hasAdmin(sender) && !sender.hasPermission("embedize.command.place")) {
            deny(sender);
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text(
                    "/" + label + " diagnose <world> <blockX> <blockZ>",
                    NamedTextColor.YELLOW));
            return;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage(Component.text("World not found: " + args[1], NamedTextColor.RED));
            return;
        }
        final int blockX;
        final int blockZ;
        try {
            blockX = Integer.parseInt(args[2]);
            blockZ = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
            return;
        }
        Runnable work = () -> {
            world.setChunkForceLoaded(blockX >> 4, blockZ >> 4, true);
            world.getChunkAt(blockX >> 4, blockZ >> 4);
            String diag = com.embedize.structure.nms.StructurePlacementBridge.diagnose(
                    world, blockX, blockZ);
            plugin.getLogger().info("[diagnose] " + diag);
            sender.sendMessage(Component.text(diag, NamedTextColor.AQUA));
        };
        if (com.embedize.util.SchedulerUtil.isFolia()) {
            com.embedize.util.SchedulerUtil.runForLocation(
                    plugin, new Location(world, blockX, 80, blockZ), work);
        } else {
            work.run();
        }
    }

    private void handleScan(CommandSender sender, String label, String[] args) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        if (!lp.hasAdmin(sender) && !sender.hasPermission("embedize.command.place")) {
            deny(sender);
            return;
        }
        // /embedize scan <world> <x> <z> [radiusBlocks] [ymin] [ymax]
        if (args.length < 4) {
            sender.sendMessage(Component.text(
                    "/" + label + " scan <world> <blockX> <blockZ> [radiusBlocks] [ymin] [ymax]",
                    NamedTextColor.YELLOW));
            return;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage(Component.text("World not found: " + args[1], NamedTextColor.RED));
            return;
        }
        final int blockX;
        final int blockZ;
        final int radius;
        final int ymin;
        final int ymax;
        try {
            blockX = Integer.parseInt(args[2]);
            blockZ = Integer.parseInt(args[3]);
            radius = args.length >= 5 ? Math.max(1, Math.min(64, Integer.parseInt(args[4]))) : 32;
            ymin = args.length >= 6 ? Integer.parseInt(args[5]) : 40;
            ymax = args.length >= 7 ? Integer.parseInt(args[6]) : 120;
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
            return;
        }
        Runnable work = () -> {
            java.util.Set<org.bukkit.Material> mats = java.util.EnumSet.of(
                    org.bukkit.Material.OAK_PLANKS,
                    org.bukkit.Material.COBBLESTONE,
                    org.bukkit.Material.HAY_BLOCK,
                    org.bukkit.Material.DIRT_PATH,
                    org.bukkit.Material.WHITE_BED,
                    org.bukkit.Material.BELL,
                    org.bukkit.Material.GLASS_PANE,
                    org.bukkit.Material.OAK_LOG,
                    org.bukkit.Material.STRIPPED_OAK_WOOD,
                    org.bukkit.Material.SPRUCE_PLANKS,
                    org.bukkit.Material.SMOOTH_STONE,
                    org.bukkit.Material.OAK_STAIRS,
                    org.bukkit.Material.COBBLESTONE_STAIRS,
                    org.bukkit.Material.CRAFTING_TABLE,
                    org.bukkit.Material.FURNACE,
                    org.bukkit.Material.CHEST,
                    org.bukkit.Material.BOOKSHELF,
                    // third-party / underground markers
                    org.bukkit.Material.STONE_BRICKS,
                    org.bukkit.Material.MOSSY_STONE_BRICKS,
                    org.bukkit.Material.CRACKED_STONE_BRICKS,
                    org.bukkit.Material.CHISELED_DEEPSLATE,
                    org.bukkit.Material.REINFORCED_DEEPSLATE,
                    org.bukkit.Material.DEEPSLATE_BRICKS,
                    org.bukkit.Material.DEEPSLATE_TILES,
                    org.bukkit.Material.GRAY_WOOL,
                    org.bukkit.Material.CAMPFIRE,
                    org.bukkit.Material.SOUL_CAMPFIRE,
                    org.bukkit.Material.LANTERN,
                    org.bukkit.Material.SANDSTONE,
                    org.bukkit.Material.CUT_SANDSTONE,
                    org.bukkit.Material.SMOOTH_SANDSTONE,
                    org.bukkit.Material.SANDSTONE_STAIRS,
                    org.bukkit.Material.RED_SANDSTONE,
                    org.bukkit.Material.CUT_RED_SANDSTONE,
                    org.bukkit.Material.SMOOTH_RED_SANDSTONE,
                    org.bukkit.Material.TERRACOTTA,
                    org.bukkit.Material.ORANGE_TERRACOTTA,
                    org.bukkit.Material.YELLOW_TERRACOTTA,
                    org.bukkit.Material.RED_TERRACOTTA,
                    org.bukkit.Material.BROWN_TERRACOTTA,
                    org.bukkit.Material.WHITE_TERRACOTTA,
                    org.bukkit.Material.WHITE_WOOL,
                    org.bukkit.Material.BARREL,
                    org.bukkit.Material.IRON_BARS,
                    org.bukkit.Material.END_PORTAL_FRAME,
                    org.bukkit.Material.END_PORTAL
                    // Note: do NOT include CAVE_AIR / generic DEEPSLATE here — they inflate
                    // HITS from natural caves/deepslate layers and hide empty place failures.
            );
            java.util.Map<org.bukkit.Material, Integer> counts = new java.util.LinkedHashMap<>();
            // Keep scanned chunks resident.
            int cMinX = (blockX - radius) >> 4;
            int cMaxX = (blockX + radius) >> 4;
            int cMinZ = (blockZ - radius) >> 4;
            int cMaxZ = (blockZ + radius) >> 4;
            for (int cx = cMinX; cx <= cMaxX; cx++) {
                for (int cz = cMinZ; cz <= cMaxZ; cz++) {
                    world.setChunkForceLoaded(cx, cz, true);
                    world.getChunkAt(cx, cz);
                    com.embedize.structure.nms.StructurePlacementBridge.placeStartsInWorld(
                            world, cx, cz, null);
                }
            }
            int hits = com.embedize.structure.nms.StructurePlacementBridge.scanMaterials(
                    world,
                    blockX - radius,
                    ymin,
                    blockZ - radius,
                    blockX + radius,
                    ymax,
                    blockZ + radius,
                    mats,
                    counts
            );
            StringBuilder detail = new StringBuilder("scan HITS=").append(hits);
            counts.forEach((m, n) -> detail.append(' ').append(m.name().toLowerCase(Locale.ROOT))
                    .append('=').append(n));
            plugin.getLogger().info("[scan] " + detail);
            sender.sendMessage(Component.text(detail.toString(), NamedTextColor.GREEN));
        };
        if (com.embedize.util.SchedulerUtil.isFolia()) {
            com.embedize.util.SchedulerUtil.runForLocation(
                    plugin, new Location(world, blockX, 80, blockZ), work);
        } else {
            work.run();
        }
    }

    private void sendBorderHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("/" + label + " border list", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("/" + label + " border <world> info|clear|set|shape", NamedTextColor.DARK_GRAY));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text(
                "/" + label + " reload|status|worlds|packs|gens|border|place|loadchunks|reset|resetstatus|resetunlock|help",
                NamedTextColor.YELLOW));
        sendBorderHelp(sender, label);
        StructurePlaceService.sendUsage(sender, label);
        sender.sendMessage(Component.text(
                "/" + label + " loadchunks <world> <x> <z> [r]  — force-gen + placeInChunk",
                NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text(
                "/" + label + " diagnose <world> <x> <z>  — NMS height + StructureStarts",
                NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text(
                "/" + label + " scan <world> <x> <z> [r] [ymin] [ymax]  — Bukkit material HITS",
                NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text(
                "/" + label + " reset [reason] | resetstatus | resetunlock  (also /resreset*)",
                NamedTextColor.DARK_GRAY));
    }

    private void deny(CommandSender sender) {
        sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @Nullable Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length <= 1) {
            return filter(args.length == 0 ? "" : args[0],
                    Arrays.asList(
                            "reload", "status", "worlds", "packs", "gens", "generators",
                            "border", "place", "loadchunks", "forcegen", "diagnose", "diag", "scan",
                            "reset", "resetstatus", "resetunlock", "help"));
        }
        if (args[0].equalsIgnoreCase("loadchunks") || args[0].equalsIgnoreCase("forcegen")
                || args[0].equalsIgnoreCase("diagnose") || args[0].equalsIgnoreCase("diag")
                || args[0].equalsIgnoreCase("scan")) {
            if (args.length == 2) {
                return filter(args[1], knownWorldNames());
            }
        }
        if (args[0].equalsIgnoreCase("border")) {
            if (args.length == 2) {
                List<String> opts = new ArrayList<>();
                opts.add("list");
                opts.addAll(knownWorldNames());
                return filter(args[1], opts);
            }
            if (args.length == 3) {
                return filter(args[2], Arrays.asList("info", "clear", "set", "shape"));
            }
        }
        if (args[0].equalsIgnoreCase("place") && args.length == 2) {
            return filter(args[1], StructurePlaceService.suggestIds(plugin, args[1]));
        }
        return List.of();
    }

    private List<String> knownWorldNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (World world : Bukkit.getWorlds()) {
            names.add(world.getName());
        }
        names.addAll(plugin.getBorderManager().worldNames());
        return new ArrayList<>(names);
    }

    private static List<String> filter(String token, List<String> options) {
        String t = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).toList();
    }
}
