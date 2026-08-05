package com.embedize.command;

import com.embedize.EmbedizePlugin;
import com.embedize.compat.LuckPermsHook;
import com.embedize.compat.MultiverseHook;
import com.embedize.config.PluginConfig;
import com.embedize.group.GroupManager;
import com.embedize.group.StructureGroup;
import com.embedize.structure.IsolationPolicy;
import com.embedize.structure.StructureIsolationListener;
import com.embedize.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class EmbedizeCommand implements CommandExecutor, TabCompleter {

    private final EmbedizePlugin plugin;

    public EmbedizeCommand(EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
                if (!lp.hasAdmin(sender) && !lp.canManageGroups(sender)) {
                    deny(sender);
                    return true;
                }
                sendWorldsOverview(sender);
            }
            case "install" -> {
                if (!lp.hasAdmin(sender) && !sender.hasPermission("embedize.command.install")) {
                    deny(sender);
                    return true;
                }
                runInstall(sender);
            }
            case "group" -> handleGroup(sender, args);
            case "help" -> sendHelp(sender, label);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void handleGroup(CommandSender sender, String[] args) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        GroupManager gm = plugin.getGroupManager();
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "/embedize group <create|delete|list|info|ns|world> ...", NamedTextColor.YELLOW));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                if (!lp.canManageGroups(sender) && !lp.hasAdmin(sender)) {
                    // allow view if any group view perm — still show list of ids they can see
                    boolean any = gm.all().stream().anyMatch(g -> lp.canViewGroup(sender, g.getId()));
                    if (!any) {
                        deny(sender);
                        return;
                    }
                }
                sender.sendMessage(Component.text("Groups:", NamedTextColor.GOLD));
                for (StructureGroup g : gm.all()) {
                    if (!lp.canViewGroup(sender, g.getId()) && !lp.canManageGroups(sender)) {
                        continue;
                    }
                    sender.sendMessage(Component.text(
                            " - " + g.getId() + " (" + g.getDisplayName() + ") ns=" + g.getNamespaces().size()
                                    + " worlds=" + g.getAllowedWorlds(),
                            NamedTextColor.GRAY));
                }
            }
            case "create" -> {
                if (!lp.canManageGroups(sender)) {
                    deny(sender);
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /embedize group create <id> [display]", NamedTextColor.RED));
                    return;
                }
                String id = args[2];
                String display = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : id;
                if (gm.create(id, display)) {
                    sender.sendMessage(Component.text("Created group '" + id + "'.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Cannot create group (invalid id or already exists).", NamedTextColor.RED));
                }
            }
            case "delete" -> {
                if (!lp.canManageGroups(sender)) {
                    deny(sender);
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /embedize group delete <id>", NamedTextColor.RED));
                    return;
                }
                if (gm.delete(args[2])) {
                    sender.sendMessage(Component.text("Deleted group '" + args[2] + "'.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Group not found.", NamedTextColor.RED));
                }
            }
            case "info" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /embedize group info <id>", NamedTextColor.RED));
                    return;
                }
                Optional<StructureGroup> opt = gm.get(args[2]);
                if (opt.isEmpty()) {
                    sender.sendMessage(Component.text("Group not found.", NamedTextColor.RED));
                    return;
                }
                StructureGroup g = opt.get();
                if (!lp.canViewGroup(sender, g.getId())) {
                    deny(sender);
                    return;
                }
                sender.sendMessage(Component.text("--- Group " + g.getId() + " ---", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("display: " + g.getDisplayName(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("namespaces: " + g.getNamespaces(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("allowed-worlds: " + g.getAllowedWorlds(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("perm manage: " + LuckPermsHook.groupManagePermission(g.getId()), NamedTextColor.DARK_AQUA));
            }
            case "ns", "namespace" -> handleNamespace(sender, args, lp, gm);
            case "world", "worlds" -> handleGroupWorld(sender, args, lp, gm);
            default -> sender.sendMessage(Component.text(
                    "/embedize group <create|delete|list|info|ns|world> ...", NamedTextColor.YELLOW));
        }
    }

    private void handleNamespace(CommandSender sender, String[] args, LuckPermsHook lp, GroupManager gm) {
        // /embedize group ns add|remove <group> <namespace>
        if (args.length < 5) {
            sender.sendMessage(Component.text("Usage: /embedize group ns <add|remove> <group> <namespace>", NamedTextColor.RED));
            return;
        }
        String op = args[2].toLowerCase(Locale.ROOT);
        String groupId = args[3];
        String ns = args[4];
        if (!lp.canManageGroup(sender, groupId)) {
            deny(sender);
            return;
        }
        Optional<StructureGroup> opt = gm.get(groupId);
        if (opt.isEmpty()) {
            sender.sendMessage(Component.text("Group not found.", NamedTextColor.RED));
            return;
        }
        StructureGroup g = opt.get();
        if ("add".equals(op)) {
            if ("minecraft".equalsIgnoreCase(ns)) {
                sender.sendMessage(Component.text("Cannot manage vanilla minecraft: namespace.", NamedTextColor.RED));
                return;
            }
            // Ensure namespace not already in another group (first-match policy)
            Optional<StructureGroup> existing = gm.findByNamespace(ns);
            if (existing.isPresent() && !existing.get().getId().equals(g.getId())) {
                sender.sendMessage(Component.text("Namespace already in group '" + existing.get().getId() + "'.", NamedTextColor.RED));
                return;
            }
            if (g.addNamespace(ns)) {
                gm.save();
                plugin.getPluginConfig().rebuildIsolationPolicy();
                sender.sendMessage(Component.text("Added namespace '" + ns + "' to " + g.getId(), NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Namespace already present or invalid.", NamedTextColor.YELLOW));
            }
        } else if ("remove".equals(op)) {
            if (g.removeNamespace(ns)) {
                gm.save();
                plugin.getPluginConfig().rebuildIsolationPolicy();
                sender.sendMessage(Component.text("Removed namespace '" + ns + "' from " + g.getId(), NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Namespace not in group.", NamedTextColor.YELLOW));
            }
        } else {
            sender.sendMessage(Component.text("Usage: /embedize group ns <add|remove> <group> <namespace>", NamedTextColor.RED));
        }
    }

    private void handleGroupWorld(CommandSender sender, String[] args, LuckPermsHook lp, GroupManager gm) {
        // /embedize group world allow|deny <group> <world>
        if (args.length < 5) {
            sender.sendMessage(Component.text("Usage: /embedize group world <allow|deny> <group> <world>", NamedTextColor.RED));
            return;
        }
        String op = args[2].toLowerCase(Locale.ROOT);
        String groupId = args[3];
        String world = args[4];
        if (!lp.canManageGroup(sender, groupId)) {
            deny(sender);
            return;
        }
        Optional<StructureGroup> opt = gm.get(groupId);
        if (opt.isEmpty()) {
            sender.sendMessage(Component.text("Group not found.", NamedTextColor.RED));
            return;
        }
        StructureGroup g = opt.get();
        if ("allow".equals(op)) {
            g.addWorld(world);
            gm.save();
            plugin.getPluginConfig().rebuildIsolationPolicy();
            sender.sendMessage(Component.text(g.getId() + " allowed-worlds: " + g.getAllowedWorlds(), NamedTextColor.GREEN));
        } else if ("deny".equals(op)) {
            g.removeWorld(world);
            gm.save();
            plugin.getPluginConfig().rebuildIsolationPolicy();
            sender.sendMessage(Component.text(g.getId() + " allowed-worlds: " + g.getAllowedWorlds(), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Usage: /embedize group world <allow|deny> <group> <world>", NamedTextColor.RED));
        }
    }

    private void runInstall(CommandSender sender) {
        sender.sendMessage(Component.text("Installing datapacks...", NamedTextColor.YELLOW));
        SchedulerUtil.runAsync(plugin, () -> {
            try {
                boolean changed = plugin.getDatapackService().forceReinstall();
                SchedulerUtil.runGlobal(plugin, () -> sender.sendMessage(Component.text(
                        changed ? "Datapacks installed/updated. Restart or /minecraft:reload recommended."
                                : "Datapacks already up to date.",
                        NamedTextColor.GREEN
                )));
            } catch (Exception e) {
                SchedulerUtil.runGlobal(plugin, () -> sender.sendMessage(Component.text(
                        "Install failed: " + e.getMessage(), NamedTextColor.RED)));
                plugin.getLogger().severe("Install failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void sendStatus(CommandSender sender) {
        PluginConfig cfg = plugin.getPluginConfig();
        StructureIsolationListener listener = plugin.getIsolationListener();
        LuckPermsHook lp = plugin.getLuckPermsHook();
        sender.sendMessage(Component.text("--- Embedize ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("enabled: " + cfg.isEnabled()
                + "  manage-ungrouped: " + cfg.isManageUngrouped(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("groups: " + plugin.getGroupManager().ids(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("datapack sources: " + cfg.getDatapackSources().stream()
                .map(PluginConfig.DatapackSource::id).toList(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("allowed: " + listener.getAllowedCount()
                + "  denied: " + listener.getCancelledCount()
                + "  passed: " + listener.getPassedCount(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("LuckPerms: " + (lp.isPresent() ? "yes" : "no (Bukkit perms still work)")
                + "  Multiverse: " + (listener.getMultiverseHook().isPresent() ? "yes" : "no")
                + "  TFG: " + (listener.getMultiverseHook().isTerraformGeneratorPresent() ? "yes" : "no"),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(plugin.getDatapackService().statusSummary(), NamedTextColor.DARK_AQUA));
    }

    private void sendWorldsOverview(CommandSender sender) {
        GroupManager gm = plugin.getGroupManager();
        MultiverseHook hook = plugin.getIsolationListener().getMultiverseHook();
        boolean resolve = plugin.getPluginConfig().isResolveAliases();
        sender.sendMessage(Component.text("World × group matrix (managed structures):", NamedTextColor.GOLD));
        for (World world : Bukkit.getWorlds()) {
            List<String> allowedGroups = new ArrayList<>();
            for (StructureGroup g : gm.all()) {
                boolean listed = g.allowsWorld(world.getName())
                        || (resolve && hook.matchesConfiguredWorld(world.getName(), g.getAllowedWorlds(), true));
                if (listed && !g.getNamespaces().isEmpty()) {
                    allowedGroups.add(g.getId());
                }
            }
            sender.sendMessage(Component.text(
                    " - " + world.getName() + " → " + (allowedGroups.isEmpty() ? "(none)" : allowedGroups),
                    allowedGroups.isEmpty() ? NamedTextColor.DARK_GRAY : NamedTextColor.GREEN
            ));
        }
        sender.sendMessage(Component.text("Vanilla minecraft: structures always generate in all worlds.", NamedTextColor.DARK_AQUA));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("/" + label + " reload|status|worlds|install|group ...", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " group create|delete|list|info <id>", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("/" + label + " group ns add|remove <group> <namespace>", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("/" + label + " group world allow|deny <group> <world>", NamedTextColor.DARK_GRAY));
    }

    private void deny(CommandSender sender) {
        sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("reload", "status", "worlds", "install", "group", "help"));
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("group")) {
            if (args.length == 2) {
                return filter(args[1], Arrays.asList("create", "delete", "list", "info", "ns", "world"));
            }
            String action = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 3 && List.of("delete", "info").contains(action)) {
                return filter(args[2], new ArrayList<>(plugin.getGroupManager().ids()));
            }
            if (args.length == 3 && (action.equals("ns") || action.equals("namespace"))) {
                return filter(args[2], Arrays.asList("add", "remove"));
            }
            if (args.length == 3 && (action.equals("world") || action.equals("worlds"))) {
                return filter(args[2], Arrays.asList("allow", "deny"));
            }
            if (args.length == 4 && (action.equals("ns") || action.equals("world") || action.equals("worlds"))) {
                return filter(args[3], new ArrayList<>(plugin.getGroupManager().ids()));
            }
            if (args.length == 5 && (action.equals("world") || action.equals("worlds"))) {
                return filter(args[4], Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList()));
            }
            if (args.length == 5 && (action.equals("ns") || action.equals("namespace"))) {
                return filter(args[4], Arrays.asList("nova_structures"));
            }
        }
        return List.of();
    }

    private static List<String> filter(String token, List<String> options) {
        String t = token.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).toList();
    }
}
