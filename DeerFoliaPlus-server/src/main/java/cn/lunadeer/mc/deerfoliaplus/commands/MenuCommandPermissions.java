package cn.lunadeer.mc.deerfoliaplus.commands;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.List;

public final class MenuCommandPermissions {

    public static final String ROOT = "deerfoliaplus.command";
    public static final String ENDERCHEST = ROOT + ".enderchest";
    public static final String WORKBENCH = ROOT + ".workbench";
    public static final String CRAFTING_TABLE = ROOT + ".craftingtable";
    public static final String STONECUTTER = ROOT + ".stonecutter";
    public static final String LOOM = ROOT + ".loom";
    public static final String GRINDSTONE = ROOT + ".grindstone";
    public static final String ANVIL = ROOT + ".anvil";
    public static final String CARTOGRAPHY_TABLE = ROOT + ".cartographytable";
    public static final String SMITHING_TABLE = ROOT + ".smithingtable";

    private static final List<PermissionNode> MENU_PERMISSIONS = List.of(
            new PermissionNode(ENDERCHEST, "Allows the user to open their ender chest with /enderchest"),
            new PermissionNode(WORKBENCH, "Allows the user to open a crafting table with /workbench"),
            new PermissionNode(CRAFTING_TABLE, "Allows the user to open a crafting table with /craftingtable"),
            new PermissionNode(STONECUTTER, "Allows the user to open a stonecutter with /stonecutter"),
            new PermissionNode(LOOM, "Allows the user to open a loom with /loom"),
            new PermissionNode(GRINDSTONE, "Allows the user to open a grindstone with /grindstone"),
            new PermissionNode(ANVIL, "Allows the user to open an anvil with /anvil"),
            new PermissionNode(CARTOGRAPHY_TABLE, "Allows the user to open a cartography table with /cartographytable"),
            new PermissionNode(SMITHING_TABLE, "Allows the user to open a smithing table with /smithingtable")
    );

    private MenuCommandPermissions() {
    }

    public static void register() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        Permission root = ensurePermission(pluginManager, ROOT, "Gives the user access to DeerFoliaPlus utility commands");

        for (PermissionNode permissionNode : MENU_PERMISSIONS) {
            Permission permission = ensurePermission(pluginManager, permissionNode.name(), permissionNode.description());
            permission.addParent(root, true);
            permission.recalculatePermissibles();
        }

        root.recalculatePermissibles();
    }

    private static Permission ensurePermission(PluginManager pluginManager, String name, String description) {
        Permission permission = pluginManager.getPermission(name);
        if (permission == null) {
            permission = new Permission(name, description, PermissionDefault.OP);
            pluginManager.addPermission(permission);
        }
        return permission;
    }

    private record PermissionNode(String name, String description) {
    }
}