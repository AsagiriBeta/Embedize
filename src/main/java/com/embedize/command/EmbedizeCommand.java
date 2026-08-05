package com.embedize.command;

import com.embedize.EmbedizePlugin;
import com.embedize.compat.LuckPermsHook;
import com.embedize.compat.MultiverseHook;
import com.embedize.config.PluginConfig;
import com.embedize.group.GroupManager;
import com.embedize.group.StructureGroup;
import com.embedize.structure.StructureIsolationListener;
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
            case "group" -> handleGroup(sender, label, args);
            case "help" -> sendHelp(sender, label);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    /**
     * Syntax:
     *   /embedize group list|create|delete ...
     *   /embedize group <id> info|add|remove|allowlist ...
     */
    private void handleGroup(CommandSender sender, String label, String[] args) {
        LuckPermsHook lp = plugin.getLuckPermsHook();
        GroupManager gm = plugin.getGroupManager();
        if (args.length < 2) {
            sendGroupHelp(sender, label);
            return;
        }

        String second = args[1].toLowerCase(Locale.ROOT);

        // Global group ops (no group id yet)
        if (second.equals("list")) {
            listGroups(sender, lp, gm);
            return;
        }
        if (second.equals("create")) {
            if (!lp.canManageGroups(sender)) {
                deny(sender);
                return;
            }
            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: /" + label + " group create <id> [display]", NamedTextColor.RED));
                return;
            }
            String id = args[2];
            String display = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : id;
            if (gm.create(id, display)) {
                sender.sendMessage(Component.text("Created group '" + id + "'.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Cannot create group (invalid id or already exists).", NamedTextColor.RED));
            }
            return;
        }
        if (second.equals("delete")) {
            if (!lp.canManageGroups(sender)) {
                deny(sender);
                return;
            }
            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: /" + label + " group delete <id>", NamedTextColor.RED));
                return;
            }
            if (gm.delete(args[2])) {
                sender.sendMessage(Component.text("Deleted group '" + args[2] + "'.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Group not found.", NamedTextColor.RED));
            }
            return;
        }

        // /embedize group <id> <action> ...
        String groupId = args[1];
        Optional<StructureGroup> opt = gm.get(groupId);
        if (opt.isEmpty()) {
            sender.sendMessage(Component.text("Unknown group '" + groupId + "'. Use create/list/delete, or /" + label + " group <id> ...", NamedTextColor.RED));
            return;
        }
        StructureGroup group = opt.get();

        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /" + label + " group " + group.getId()
                    + " <info|add|remove|allowlist>", NamedTextColor.YELLOW));
            return;
        }

        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "info" -> {
                if (!lp.canViewGroup(sender, group.getId())) {
                    deny(sender);
                    return;
                }
                sender.sendMessage(Component.text("--- Group " + group.getId() + " ---", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("display: " + group.getDisplayName(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("packs: " + group.getPacks(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("namespaces: " + group.getNamespaces(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("allowed-worlds: " + group.getAllowedWorlds(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("perm: " + LuckPermsHook.groupManagePermission(group.getId()), NamedTextColor.DARK_AQUA));
            }
            case "add" -> {
                if (!lp.canManageGroup(sender, group.getId())) {
                    deny(sender);
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " group " + group.getId() + " add <pack>", NamedTextColor.RED));
                    return;
                }
                String pack = args[3];
                String result = gm.addPackToGroup(group.getId(), pack);
                handlePackResult(sender, result, group.getId(), pack, true);
                plugin.getPluginConfig().rebuildIsolationPolicy();
            }
            case "remove" -> {
                if (!lp.canManageGroup(sender, group.getId())) {
                    deny(sender);
                    return;
                }
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " group " + group.getId() + " remove <pack>", NamedTextColor.RED));
                    return;
                }
                String pack = args[3];
                String result = gm.removePackFromGroup(group.getId(), pack);
                handlePackResult(sender, result, group.getId(), pack, false);
                plugin.getPluginConfig().rebuildIsolationPolicy();
            }
            case "allowlist" -> handleAllowlist(sender, label, lp, gm, group, args);
            default -> sender.sendMessage(Component.text("Usage: /" + label + " group " + group.getId()
                    + " <info|add|remove|allowlist>", NamedTextColor.YELLOW));
        }
    }

    private void handleAllowlist(
            CommandSender sender,
            String label,
            LuckPermsHook lp,
            GroupManager gm,
            StructureGroup group,
            String[] args
    ) {
        if (!lp.canManageGroup(sender, group.getId())) {
            deny(sender);
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(Component.text(
                    "Usage: /" + label + " group " + group.getId() + " allowlist add|remove <world>",
                    NamedTextColor.RED));
            return;
        }
        String op = args[3].toLowerCase(Locale.ROOT);
        String world = args[4];
        boolean changed;
        switch (op) {
            case "add" -> changed = group.addWorld(world);
            case "remove" -> changed = group.removeWorld(world);
            default -> {
                sender.sendMessage(Component.text(
                        "Usage: /" + label + " group " + group.getId() + " allowlist add|remove <world>",
                        NamedTextColor.RED));
                return;
            }
        }
        if (changed) {
            gm.save();
            plugin.getPluginConfig().rebuildIsolationPolicy();
        }
        sender.sendMessage(Component.text(
                group.getId() + " allowlist " + (changed ? (op.equals("add") ? "added" : "removed") : "unchanged")
                        + " '" + world + "': " + group.getAllowedWorlds(),
                changed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
    }

    private void handlePackResult(CommandSender sender, String result, String groupId, String pack, boolean adding) {
        switch (result) {
            case "ok" -> {
                Optional<StructureGroup> g = plugin.getGroupManager().get(groupId);
                sender.sendMessage(Component.text(
                        (adding ? "Added" : "Removed") + " pack '" + pack + "' "
                                + (adding ? "→" : "from") + " group " + groupId
                                + (g.map(gr -> " (packs=" + gr.getPacks() + ", ns=" + gr.getNamespaces() + ")").orElse("")),
                        NamedTextColor.GREEN));
            }
            case "noop" -> sender.sendMessage(Component.text("No change.", NamedTextColor.YELLOW));
            case "not-found" -> sender.sendMessage(Component.text("Group not found.", NamedTextColor.RED));
            case "vanilla" -> sender.sendMessage(Component.text("Cannot manage vanilla minecraft structures.", NamedTextColor.RED));
            default -> {
                if (result.startsWith("ns-taken:")) {
                    String[] parts = result.split(":", 3);
                    sender.sendMessage(Component.text(
                            "Namespace '" + (parts.length > 2 ? parts[2] : "?") + "' already in group '"
                                    + (parts.length > 1 ? parts[1] : "?") + "'.",
                            NamedTextColor.RED));
                } else {
                    sender.sendMessage(Component.text("Failed: " + result, NamedTextColor.RED));
                }
            }
        }
    }

    private void listGroups(CommandSender sender, LuckPermsHook lp, GroupManager gm) {
        if (!lp.canManageGroups(sender) && !lp.hasAdmin(sender)) {
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
                    " - " + g.getId() + " packs=" + g.getPacks() + " worlds=" + g.getAllowedWorlds(),
                    NamedTextColor.GRAY));
        }
    }

    private void sendStatus(CommandSender sender) {
        PluginConfig cfg = plugin.getPluginConfig();
        StructureIsolationListener listener = plugin.getIsolationListener();
        LuckPermsHook lp = plugin.getLuckPermsHook();
        sender.sendMessage(Component.text("--- Embedize ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("enabled: " + cfg.isEnabled()
                + "  manage-ungrouped: " + cfg.isManageUngrouped(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("groups: " + plugin.getGroupManager().ids(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("allowed: " + listener.getAllowedCount()
                + "  denied: " + listener.getCancelledCount()
                + "  passed: " + listener.getPassedCount(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("LuckPerms: " + (lp.isPresent() ? "yes" : "no")
                + "  Multiverse: " + (listener.getMultiverseHook().isPresent() ? "yes" : "no")
                + "  TFG: " + (listener.getMultiverseHook().isTerraformGeneratorPresent() ? "yes" : "no"),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(plugin.getDatapackService().statusSummary(), NamedTextColor.DARK_AQUA));
    }

    private void sendWorldsOverview(CommandSender sender) {
        GroupManager gm = plugin.getGroupManager();
        MultiverseHook hook = plugin.getIsolationListener().getMultiverseHook();
        boolean resolve = plugin.getPluginConfig().isResolveAliases();
        sender.sendMessage(Component.text("World × group matrix:", NamedTextColor.GOLD));
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
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("/" + label + " reload|status|worlds|group ...", NamedTextColor.YELLOW));
        sendGroupHelp(sender, label);
    }

    private void sendGroupHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("/" + label + " group list|create <id>|delete <id>", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("/" + label + " group <id> info", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("/" + label + " group <id> add|remove <pack>", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("/" + label + " group <id> allowlist add|remove <world>", NamedTextColor.DARK_GRAY));
    }

    private void deny(CommandSender sender) {
        sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("reload", "status", "worlds", "group", "help"));
        }
        if (!args[0].equalsIgnoreCase("group")) {
            return List.of();
        }
        List<String> groupIds = new ArrayList<>(plugin.getGroupManager().ids());
        List<String> packIds = new ArrayList<>(Arrays.asList(
                "dungeons-and-taverns", "towns-and-towers", "nova_structures", "towns_and_towers"));

        if (args.length == 2) {
            List<String> opts = new ArrayList<>(Arrays.asList("list", "create", "delete"));
            opts.addAll(groupIds);
            return filter(args[1], opts);
        }
        String second = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3 && second.equals("delete")) {
            return filter(args[2], groupIds);
        }
        // group <id> ...
        if (plugin.getGroupManager().get(args[1]).isPresent()) {
            if (args.length == 3) {
                return filter(args[2], Arrays.asList("info", "add", "remove", "allowlist"));
            }
            String action = args[2].toLowerCase(Locale.ROOT);
            if (args.length == 4 && (action.equals("add") || action.equals("remove"))) {
                return filter(args[3], packIds);
            }
            if (action.equals("allowlist")) {
                if (args.length == 4) {
                    return filter(args[3], Arrays.asList("add", "remove"));
                }
                if (args.length == 5) {
                    String op = args[3].toLowerCase(Locale.ROOT);
                    if (op.equals("add") || op.equals("remove")) {
                        return filter(args[4], Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList()));
                    }
                }
            }
        }
        return List.of();
    }

    private static List<String> filter(String token, List<String> options) {
        String t = token.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).toList();
    }
}
