package com.embedize.compat;

import com.embedize.EmbedizePlugin;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * LuckPerms compatibility via Bukkit permissions (LP injects into hasPermission)
 * plus optional API detection for diagnostics / dynamic permission registration.
 *
 * @see <a href="https://luckperms.net/wiki/Developer-API">LuckPerms Developer API</a>
 */
public final class LuckPermsHook {

    public static final String PERM_ADMIN = "embedize.admin";
    public static final String PERM_GROUP_ADMIN = "embedize.group.admin";
    public static final String PERM_GROUP_PREFIX = "embedize.group.";

    private final EmbedizePlugin plugin;
    private final Logger logger;
    private final boolean present;
    private LuckPerms api;

    public LuckPermsHook(EmbedizePlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        Plugin lpPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
        boolean found = lpPlugin != null && lpPlugin.isEnabled();
        LuckPerms resolved = null;
        if (found) {
            try {
                RegisteredServiceProvider<LuckPerms> rsp =
                        Bukkit.getServicesManager().getRegistration(LuckPerms.class);
                if (rsp != null) {
                    resolved = rsp.getProvider();
                }
            } catch (NoClassDefFoundError | Exception ex) {
                logger.warning("LuckPerms present but API could not be bound: " + ex.getMessage());
                found = false;
            }
        }
        this.present = found && resolved != null;
        this.api = resolved;
        if (this.present) {
            logger.info("LuckPerms detected — Embedize permissions are LP-compatible "
                    + "(use /lp to assign embedize.* nodes).");
        }
    }

    public boolean isPresent() {
        return present;
    }

    public LuckPerms getApi() {
        return api;
    }

    /**
     * Register base + per-group permission nodes so LuckPerms can autocomplete / suggest them.
     */
    public void registerBasePermissions() {
        PluginManager pm = Bukkit.getPluginManager();
        ensurePermission(pm, PERM_ADMIN, "Full Embedize administration", PermissionDefault.OP);
        ensurePermission(pm, PERM_GROUP_ADMIN, "Manage all Embedize structure groups", PermissionDefault.OP);
        ensurePermission(pm, "embedize.command.reload", "Reload Embedize", PermissionDefault.OP);
        ensurePermission(pm, "embedize.command.status", "View Embedize status", PermissionDefault.OP);
        ensurePermission(pm, "embedize.command.install", "Install datapacks", PermissionDefault.OP);
    }

    public void registerGroupPermissions(Set<String> groupIds) {
        PluginManager pm = Bukkit.getPluginManager();
        for (String id : groupIds) {
            String node = groupManagePermission(id);
            ensurePermission(pm, node, "Manage Embedize group '" + id + "'", PermissionDefault.OP);
            ensurePermission(pm, groupViewPermission(id), "View Embedize group '" + id + "'", PermissionDefault.OP);
        }
    }

    private static void ensurePermission(PluginManager pm, String name, String description, PermissionDefault def) {
        Permission existing = pm.getPermission(name);
        if (existing == null) {
            pm.addPermission(new Permission(name, description, def));
        }
    }

    public static String groupManagePermission(String groupId) {
        return PERM_GROUP_PREFIX + groupId.toLowerCase(Locale.ROOT) + ".manage";
    }

    public static String groupViewPermission(String groupId) {
        return PERM_GROUP_PREFIX + groupId.toLowerCase(Locale.ROOT) + ".view";
    }

    public boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission(PERM_ADMIN);
    }

    public boolean canManageGroups(CommandSender sender) {
        return hasAdmin(sender) || sender.hasPermission(PERM_GROUP_ADMIN);
    }

    public boolean canManageGroup(CommandSender sender, String groupId) {
        return canManageGroups(sender) || sender.hasPermission(groupManagePermission(groupId));
    }

    public boolean canViewGroup(CommandSender sender, String groupId) {
        return canManageGroup(sender, groupId) || sender.hasPermission(groupViewPermission(groupId));
    }

    /**
     * Online player permission check — works with LuckPerms through Bukkit bridge.
     * Safe to call sync for online players.
     */
    public boolean playerHas(Player player, String permission) {
        return player.hasPermission(permission);
    }
}
