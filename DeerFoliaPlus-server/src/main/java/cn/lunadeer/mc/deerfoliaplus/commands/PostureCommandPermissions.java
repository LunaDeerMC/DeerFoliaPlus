package cn.lunadeer.mc.deerfoliaplus.commands;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.List;

public final class PostureCommandPermissions {

    public static final String ROOT = "deerfoliaplus.command.posture";
    public static final String SIT = "deerfoliaplus.command.sit";
    public static final String LAY = "deerfoliaplus.command.lay";
    public static final String CRAWL = "deerfoliaplus.command.crawl";
    public static final String GET_UP = "deerfoliaplus.command.get-up";

    private static final List<PermissionNode> POSTURE_PERMISSIONS = List.of(
            new PermissionNode(SIT, "Allows the user to sit down with /sit"),
            new PermissionNode(LAY, "Allows the user to lie down with /lay"),
            new PermissionNode(CRAWL, "Allows the user to crawl with /crawl"),
            new PermissionNode(GET_UP, "Allows the user to stand back up with /get-up")
    );

    private PostureCommandPermissions() {
    }

    public static void register() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        Permission root = ensurePermission(pluginManager, ROOT, "Gives the user access to DeerFoliaPlus posture commands", PermissionDefault.TRUE);

        for (PermissionNode permissionNode : POSTURE_PERMISSIONS) {
            Permission permission = ensurePermission(pluginManager, permissionNode.name(), permissionNode.description(), PermissionDefault.TRUE);
            permission.addParent(root, true);
            permission.recalculatePermissibles();
        }

        root.recalculatePermissibles();
    }

    private static Permission ensurePermission(PluginManager pluginManager, String name, String description, PermissionDefault permissionDefault) {
        Permission permission = pluginManager.getPermission(name);
        if (permission == null) {
            permission = new Permission(name, description, permissionDefault);
            pluginManager.addPermission(permission);
        }
        return permission;
    }

    private record PermissionNode(String name, String description) {
    }
}
