package com.embedize.command;

import com.embedize.EmbedizePlugin;
import com.embedize.compat.MultiverseHook;
import com.embedize.config.PluginConfig;
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
import java.util.stream.Collectors;

public final class EmbedizeCommand implements CommandExecutor, TabCompleter {

    private final EmbedizePlugin plugin;

    public EmbedizeCommand(EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("embedize.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(Component.text("Embedize config reloaded.", NamedTextColor.GREEN));
            }
            case "status" -> sendStatus(sender);
            case "worlds" -> sendWorlds(sender);
            case "install" -> {
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
                                "Install failed: " + e.getMessage(), NamedTextColor.RED
                        )));
                        plugin.getLogger().severe("Install failed: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
            case "allow" -> mutateWorldList(sender, args, true);
            case "deny" -> mutateWorldList(sender, args, false);
            case "help" -> sendHelp(sender, label);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void mutateWorldList(CommandSender sender, String[] args, boolean allowSubcommand) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /embedize " + (allowSubcommand ? "allow" : "deny") + " <world>", NamedTextColor.RED));
            return;
        }
        String world = args[1];
        PluginConfig.Mode mode = plugin.getPluginConfig().getMode();
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList("allowed-worlds"));

        if (allowSubcommand) {
            // Ensure world is allowed under current mode
            if (mode == PluginConfig.Mode.ALLOWLIST) {
                if (list.stream().noneMatch(w -> w.equalsIgnoreCase(world))) {
                    list.add(world);
                }
            } else {
                list.removeIf(w -> w.equalsIgnoreCase(world));
            }
        } else {
            if (mode == PluginConfig.Mode.ALLOWLIST) {
                list.removeIf(w -> w.equalsIgnoreCase(world));
            } else {
                if (list.stream().noneMatch(w -> w.equalsIgnoreCase(world))) {
                    list.add(world);
                }
            }
        }

        plugin.getConfig().set("allowed-worlds", list);
        plugin.saveConfig();
        plugin.reloadPlugin();
        sender.sendMessage(Component.text("Updated world list: " + list, NamedTextColor.GREEN));
    }

    private void sendStatus(CommandSender sender) {
        PluginConfig cfg = plugin.getPluginConfig();
        StructureIsolationListener listener = plugin.getIsolationListener();
        MultiverseHook hook = listener.getMultiverseHook();

        sender.sendMessage(Component.text("--- Embedize ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("enabled: " + cfg.isEnabled() + "  mode: " + cfg.getMode(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("namespaces: " + cfg.getManagedNamespaces(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("configured worlds: " + cfg.getConfiguredWorlds(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("allowed spawns: " + listener.getAllowedCount()
                + "  cancelled: " + listener.getCancelledCount(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Multiverse-Core: " + (hook.isPresent() ? "yes" : "no")
                + "  TerraformGenerator: " + (hook.isTerraformGeneratorPresent() ? "yes" : "no"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(plugin.getDatapackService().statusSummary(), NamedTextColor.DARK_AQUA));
    }

    private void sendWorlds(CommandSender sender) {
        PluginConfig cfg = plugin.getPluginConfig();
        MultiverseHook hook = plugin.getIsolationListener().getMultiverseHook();
        sender.sendMessage(Component.text("Loaded worlds vs isolation:", NamedTextColor.GOLD));
        for (World world : Bukkit.getWorlds()) {
            boolean listed = hook.matchesConfiguredWorld(
                    world.getName(), cfg.getConfiguredWorlds(), cfg.isResolveAliases());
            boolean willGenerate = cfg.getMode() == PluginConfig.Mode.ALLOWLIST ? listed : !listed;
            String gen = world.getGenerator() == null ? "vanilla" : world.getGenerator().getClass().getSimpleName();
            sender.sendMessage(Component.text(
                    " - " + world.getName() + " [" + world.getEnvironment() + "] gen=" + gen
                            + " → managed structures: " + (willGenerate ? "YES" : "no"),
                    willGenerate ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY
            ));
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("/" + label + " reload|status|worlds|install|allow <world>|deny <world>", NamedTextColor.YELLOW));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("embedize.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("reload", "status", "worlds", "install", "allow", "deny", "help"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("allow") || args[0].equalsIgnoreCase("deny"))) {
            return filter(args[1], Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList()));
        }
        return List.of();
    }

    private static List<String> filter(String token, List<String> options) {
        String t = token.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).toList();
    }
}
