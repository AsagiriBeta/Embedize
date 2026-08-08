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

import java.util.logging.Logger;

/**
 * LuckPerms compatibility via Bukkit permissions (LP injects into hasPermission)
 * plus optional API detection for diagnostics.
 *
 * @see <a href="https://luckperms.net/wiki/Developer-API">LuckPerms Developer API</a>
 */
public final class LuckPermsHook {

    public static final String PERM_ADMIN = "embedize.admin";

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

    public void registerBasePermissions() {
        PluginManager pm = Bukkit.getPluginManager();
        ensurePermission(pm, PERM_ADMIN, "Full Embedize administration", PermissionDefault.OP);
        ensurePermission(pm, "embedize.command.reload", "Reload Embedize", PermissionDefault.OP);
        ensurePermission(pm, "embedize.command.status", "View Embedize status", PermissionDefault.OP);
        ensurePermission(pm, "embedize.command.place", "Debug-place structure templates", PermissionDefault.OP);
        ensurePermission(pm, "embedize.command.reset", "Reset the resource world", PermissionDefault.OP);
        ensurePermission(pm, "embedize.border.admin", "Manage Embedize world borders", PermissionDefault.OP);
        ensurePermission(pm, "embedize.border.bypass", "Bypass Embedize world borders", PermissionDefault.FALSE);
    }

    private static void ensurePermission(PluginManager pm, String name, String desc, PermissionDefault def) {
        Permission existing = pm.getPermission(name);
        if (existing == null) {
            pm.addPermission(new Permission(name, desc, def));
        }
    }

    public boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission(PERM_ADMIN) || sender.isOp();
    }

    public boolean hasBorderBypass(Player player) {
        return player.hasPermission("embedize.border.bypass") || player.isOp();
    }

    public String describe() {
        return present ? "LuckPerms bound" : "LuckPerms absent (Bukkit perms only)";
    }
}
