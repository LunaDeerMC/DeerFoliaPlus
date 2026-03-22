package cn.lunadeer.mc.deerfoliaplus.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

public class InspectInventoryGUI implements InventoryHolder {

    private static final Map<Inventory, InspectInventoryGUI> OPEN_VIEWS = new WeakHashMap<>();

    private final ServerPlayer target;
    private final Inventory inventory;

    private InspectInventoryGUI(ServerPlayer target) {
        this.target = target;
        net.kyori.adventure.text.Component title = net.kyori.adventure.text.Component.text(
                target.getScoreboardName() + " - Inventory",
                net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE
        );
        this.inventory = Bukkit.createInventory(this, 54, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public ServerPlayer getTarget() {
        return target;
    }

    @Nullable
    public static InspectInventoryGUI getView(Inventory inv) {
        return OPEN_VIEWS.get(inv);
    }

    public static void open(Player viewer, ServerPlayer target) {
        InspectInventoryGUI gui = new InspectInventoryGUI(target);
        gui.populateFromTarget();
        OPEN_VIEWS.put(gui.inventory, gui);
        viewer.openInventory(gui.inventory);
    }

    /**
     * Full inventory layout (54 slots, 6 rows of 9):
     * <pre>
     * Row 0:  HEAD  CHEST LEGS  BOOTS [btn] OFFHD [btn] [btn] [btn]
     * Row 1:  [btn] [btn] [btn] [btn] [btn] [btn] [btn] [btn] [btn]
     * Row 2:  inv9  inv10 inv11 inv12 inv13 inv14 inv15 inv16 inv17
     * Row 3:  inv18 inv19 inv20 inv21 inv22 inv23 inv24 inv25 inv26
     * Row 4:  inv27 inv28 inv29 inv30 inv31 inv32 inv33 inv34 inv35
     * Row 5:  inv0  inv1  inv2  inv3  inv4  inv5  inv6  inv7  inv8
     * </pre>
     * NMS slot mapping: 0-8 hotbar, 9-35 main, 36 feet, 37 legs, 38 chest, 39 head, 40 offhand.
     */
    private void populateFromTarget() {
        net.minecraft.world.entity.player.Inventory nmsInv = target.getInventory();

        // Row 0: equipment
        inventory.setItem(0, toBukkit(nmsInv.getItem(39)));  // head
        inventory.setItem(1, toBukkit(nmsInv.getItem(38)));  // chest
        inventory.setItem(2, toBukkit(nmsInv.getItem(37)));  // legs
        inventory.setItem(3, toBukkit(nmsInv.getItem(36)));  // boots
        inventory.setItem(4, createPlaceholder());
        inventory.setItem(5, toBukkit(nmsInv.getItem(40)));  // offhand
        inventory.setItem(6, createPlaceholder());
        inventory.setItem(7, createPlaceholder());
        inventory.setItem(8, createPlaceholder());

        // Row 1: separator
        for (int i = 9; i <= 17; i++) {
            inventory.setItem(i, createPlaceholder());
        }

        // Row 2-4: main inventory (NMS slot 9-35)
        for (int i = 9; i <= 35; i++) {
            inventory.setItem(i + 9, toBukkit(nmsInv.getItem(i)));
        }

        // Row 5: hotbar (NMS slot 0-8)
        for (int i = 0; i <= 8; i++) {
            inventory.setItem(45 + i, toBukkit(nmsInv.getItem(i)));
        }
    }

    public void syncToTarget() {
        if (target.isRemoved()) return;

        net.minecraft.world.entity.player.Inventory nmsInv = target.getInventory();

        // Row 0: equipment
        nmsInv.setItem(39, toNms(inventory.getItem(0)));  // head
        nmsInv.setItem(38, toNms(inventory.getItem(1)));  // chest
        nmsInv.setItem(37, toNms(inventory.getItem(2)));  // legs
        nmsInv.setItem(36, toNms(inventory.getItem(3)));  // boots
        nmsInv.setItem(40, toNms(inventory.getItem(5)));  // offhand

        // Row 2-4: main inventory (NMS slot 9-35)
        for (int i = 9; i <= 35; i++) {
            nmsInv.setItem(i, toNms(inventory.getItem(i + 9)));
        }

        // Row 5: hotbar (NMS slot 0-8)
        for (int i = 0; i <= 8; i++) {
            nmsInv.setItem(i, toNms(inventory.getItem(45 + i)));
        }

        target.detectEquipmentUpdates();
    }

    public void removeTracking() {
        OPEN_VIEWS.remove(this.inventory);
    }

    public boolean isPlaceholderSlot(int slot) {
        return slot == 4 || slot == 6 || slot == 7 || slot == 8
                || (slot >= 9 && slot <= 17);
    }

    public static boolean isPlaceholder(@Nullable ItemStack item) {
        if (item == null || item.getType() != Material.STRUCTURE_VOID) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return !meta.hasDisplayName() || meta.displayName() == null || meta.displayName().equals(net.kyori.adventure.text.Component.empty());
    }

    private static ItemStack toBukkit(net.minecraft.world.item.ItemStack nmsStack) {
        if (nmsStack == null || nmsStack.isEmpty()) return null;
        return CraftItemStack.asBukkitCopy(nmsStack);
    }

    private static net.minecraft.world.item.ItemStack toNms(@Nullable ItemStack bukkitStack) {
        if (bukkitStack == null || bukkitStack.getType() == Material.AIR) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        if (isPlaceholder(bukkitStack)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return CraftItemStack.asNMSCopy(bukkitStack);
    }

    private static ItemStack createPlaceholder() {
        ItemStack button = new ItemStack(Material.STRUCTURE_VOID);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.empty());
            button.setItemMeta(meta);
        }
        return button;
    }
}
