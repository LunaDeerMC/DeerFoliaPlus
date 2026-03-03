package org.leavesmc.leaves.bot.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.leavesmc.leaves.bot.ServerBot;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Creates and manages chest-based GUI views for a {@link ServerBot}'s inventory.
 * <p>
 * Supports three view types:
 * <ul>
 *   <li>{@link BotInventoryViewType#INVENTORY} — full view (54 slots): equipment row, separator, main inventory, hotbar</li>
 *   <li>{@link BotInventoryViewType#EQUIPMENT} — equipment only (9 slots): helmet, chest, legs, boots, placeholder, offhand, placeholders</li>
 *   <li>{@link BotInventoryViewType#BACKPACK} — backpack only (36 slots): main inventory rows + hotbar row</li>
 * </ul>
 * <p>
 * Items are copied from the bot's real inventory when the GUI is opened,
 * and written back to the bot's real inventory when the GUI is closed.
 * Placeholder buttons cannot be moved by the player.
 */
public class BotInventoryGUI implements InventoryHolder {

    // ---- Tracking open views ----
    private static final Map<Inventory, BotInventoryGUI> OPEN_VIEWS = new WeakHashMap<>();

    private final ServerBot bot;
    private final BotInventoryViewType viewType;
    private final Inventory inventory;

    private BotInventoryGUI(ServerBot bot, BotInventoryViewType viewType) {
        this.bot = bot;
        this.viewType = viewType;
        Component title = Component.text(bot.getScoreboardName() + " - " + viewType.getDisplayName(), NamedTextColor.DARK_PURPLE);
        this.inventory = Bukkit.createInventory(this, viewType.getSize(), title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public ServerBot getBot() {
        return bot;
    }

    public BotInventoryViewType getViewType() {
        return viewType;
    }

    // ---- Static lookup ----

    /**
     * Returns the GUI instance associated with the given Bukkit inventory, or null.
     */
    @Nullable
    public static BotInventoryGUI getView(Inventory inv) {
        return OPEN_VIEWS.get(inv);
    }

    // ---- Open methods (called from BotCommand) ----

    public static void openInventory(Player player, ServerBot bot) {
        open(player, bot, BotInventoryViewType.INVENTORY);
    }

    public static void openEquipment(Player player, ServerBot bot) {
        open(player, bot, BotInventoryViewType.EQUIPMENT);
    }

    public static void openBackpack(Player player, ServerBot bot) {
        open(player, bot, BotInventoryViewType.BACKPACK);
    }

    private static void open(Player player, ServerBot bot, BotInventoryViewType type) {
        BotInventoryGUI gui = new BotInventoryGUI(bot, type);
        gui.populateFromBot();
        OPEN_VIEWS.put(gui.inventory, gui);
        player.openInventory(gui.inventory);
    }

    // ---- Populate GUI from bot's real inventory ----

    private void populateFromBot() {
        net.minecraft.world.entity.player.Inventory nmsInv = bot.getInventory();

        switch (viewType) {
            case INVENTORY -> populateFullInventory(nmsInv);
            case EQUIPMENT -> populateEquipment(nmsInv);
            case BACKPACK -> populateBackpack(nmsInv);
        }
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
     * inv0-8 = hotbar, inv9-35 = main inventory.
     * NMS slot mapping: 0-8 hotbar, 9-35 main, 36 feet, 37 legs, 38 chest, 39 head, 40 offhand.
     */
    private void populateFullInventory(net.minecraft.world.entity.player.Inventory nmsInv) {
        // Row 0: equipment
        inventory.setItem(0, toBukkit(nmsInv.getItem(39)));  // head
        inventory.setItem(1, toBukkit(nmsInv.getItem(38)));  // chest
        inventory.setItem(2, toBukkit(nmsInv.getItem(37)));  // legs
        inventory.setItem(3, toBukkit(nmsInv.getItem(36)));  // boots
        inventory.setItem(4, createPlaceholder());
        inventory.setItem(5, toBukkit(nmsInv.getItem(40))); // offhand
        inventory.setItem(6, createPlaceholder());
        inventory.setItem(7, createPlaceholder());
        inventory.setItem(8, createPlaceholder());

        // Row 1: separator buttons
        for (int i = 9; i <= 17; i++) {
            inventory.setItem(i, createPlaceholder());
        }

        // Row 2-4: main inventory (NMS slot 9 to 35)
        for (int i = 9; i <= 35; i++) {
            inventory.setItem(i + 9, toBukkit(nmsInv.getItem(i)));
        }

        // Row 5: hotbar (NMS slot 0 to 8)
        for (int i = 0; i <= 8; i++) {
            inventory.setItem(45 + i, toBukkit(nmsInv.getItem(i)));
        }
    }

    /**
     * Equipment layout (9 slots, 1 row):
     * <pre>
     * HEAD CHEST LEGS BOOTS [btn] OFFHD [btn] [btn] [btn]
     * </pre>
     */
    private void populateEquipment(net.minecraft.world.entity.player.Inventory nmsInv) {
        inventory.setItem(0, toBukkit(nmsInv.getItem(39)));  // head
        inventory.setItem(1, toBukkit(nmsInv.getItem(38)));  // chest
        inventory.setItem(2, toBukkit(nmsInv.getItem(37)));  // legs
        inventory.setItem(3, toBukkit(nmsInv.getItem(36)));  // boots
        inventory.setItem(4, createPlaceholder());
        inventory.setItem(5, toBukkit(nmsInv.getItem(40))); // offhand
        inventory.setItem(6, createPlaceholder());
        inventory.setItem(7, createPlaceholder());
        inventory.setItem(8, createPlaceholder());
    }

    /**
     * Backpack layout (36 slots, 4 rows):
     * <pre>
     * Row 0: inv9  inv10 inv11 inv12 inv13 inv14 inv15 inv16 inv17
     * Row 1: inv18 inv19 inv20 inv21 inv22 inv23 inv24 inv25 inv26
     * Row 2: inv27 inv28 inv29 inv30 inv31 inv32 inv33 inv34 inv35
     * Row 3: inv0  inv1  inv2  inv3  inv4  inv5  inv6  inv7  inv8
     * </pre>
     */
    private void populateBackpack(net.minecraft.world.entity.player.Inventory nmsInv) {
        // Row 0-2: main inventory (NMS slot 9-35)
        for (int i = 9; i <= 35; i++) {
            inventory.setItem(i - 9, toBukkit(nmsInv.getItem(i)));
        }
        // Row 3: hotbar (NMS slot 0-8)
        for (int i = 0; i <= 8; i++) {
            inventory.setItem(27 + i, toBukkit(nmsInv.getItem(i)));
        }
    }

    // ---- Write GUI contents back to bot's real inventory ----

    public void syncToBot() {
        if (bot.isRemoved()) return;

        net.minecraft.world.entity.player.Inventory nmsInv = bot.getInventory();

        switch (viewType) {
            case INVENTORY -> syncFullInventory(nmsInv);
            case EQUIPMENT -> syncEquipment(nmsInv);
            case BACKPACK -> syncBackpack(nmsInv);
        }

        bot.detectEquipmentUpdates();
    }

    private void syncFullInventory(net.minecraft.world.entity.player.Inventory nmsInv) {
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
    }

    private void syncEquipment(net.minecraft.world.entity.player.Inventory nmsInv) {
        nmsInv.setItem(39, toNms(inventory.getItem(0)));  // head
        nmsInv.setItem(38, toNms(inventory.getItem(1)));  // chest
        nmsInv.setItem(37, toNms(inventory.getItem(2)));  // legs
        nmsInv.setItem(36, toNms(inventory.getItem(3)));  // boots
        nmsInv.setItem(40, toNms(inventory.getItem(5)));  // offhand
    }

    private void syncBackpack(net.minecraft.world.entity.player.Inventory nmsInv) {
        // Row 0-2: main inventory (NMS slot 9-35)
        for (int i = 9; i <= 35; i++) {
            nmsInv.setItem(i, toNms(inventory.getItem(i - 9)));
        }
        // Row 3: hotbar (NMS slot 0-8)
        for (int i = 0; i <= 8; i++) {
            nmsInv.setItem(i, toNms(inventory.getItem(27 + i)));
        }
    }

    // ---- Remove tracking ----

    public void removeTracking() {
        OPEN_VIEWS.remove(this.inventory);
    }

    // ---- Placeholder detection ----

    /**
     * Checks if a GUI slot index corresponds to a non-interactive placeholder button.
     */
    public boolean isPlaceholderSlot(int slot) {
        return switch (viewType) {
            case INVENTORY -> slot == 4 || slot == 6 || slot == 7 || slot == 8
                    || (slot >= 9 && slot <= 17);
            case EQUIPMENT -> slot == 4 || slot == 6 || slot == 7 || slot == 8;
            case BACKPACK -> false; // no placeholders in backpack view
        };
    }

    /**
     * Checks if the given Bukkit ItemStack is a GUI placeholder button.
     */
    public static boolean isPlaceholder(@Nullable ItemStack item) {
        if (item == null || item.getType() != Material.STRUCTURE_VOID) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        // Check for our custom marker via PersistentDataContainer or display name
        // The existing code uses DataComponents "Leaves.Gui.Placeholder" — on Bukkit side,
        // we check by material + empty display name as a simple heuristic
        return !meta.hasDisplayName() || meta.displayName() == null || meta.displayName().equals(Component.empty());
    }

    // ---- NMS/Bukkit conversion helpers ----

    private static ItemStack toBukkit(net.minecraft.world.item.ItemStack nmsStack) {
        if (nmsStack == null || nmsStack.isEmpty()) {
            return null;
        }
        return CraftItemStack.asBukkitCopy(nmsStack);
    }

    private static net.minecraft.world.item.ItemStack toNms(@Nullable ItemStack bukkitStack) {
        if (bukkitStack == null || bukkitStack.getType() == Material.AIR) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        // Don't write placeholder buttons back to the real inventory
        if (isPlaceholder(bukkitStack)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return CraftItemStack.asNMSCopy(bukkitStack);
    }

    private static ItemStack createPlaceholder() {
        ItemStack button = new ItemStack(Material.STRUCTURE_VOID);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            button.setItemMeta(meta);
        }
        return button;
    }
}
