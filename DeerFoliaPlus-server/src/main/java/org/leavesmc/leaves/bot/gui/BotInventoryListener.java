package org.leavesmc.leaves.bot.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Bukkit event listener that manages BotInventoryGUI interactions.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Prevents moving placeholder buttons.</li>
 *   <li>Allows free transfer between the player's inventory and bot GUI slots.</li>
 *   <li>Syncs GUI contents back to the bot when the inventory is closed.</li>
 * </ul>
 */
public class BotInventoryListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        BotInventoryGUI gui = BotInventoryGUI.getView(topInv);
        if (gui == null) return;

        // Bot was removed while GUI is open
        if (gui.getBot().isRemoved()) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();

        // Click is in the top (bot) inventory
        if (rawSlot >= 0 && rawSlot < topInv.getSize()) {
            // Block clicks on placeholder slots
            if (gui.isPlaceholderSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }

            // For shift-click from top inventory: allow — Bukkit moves item to player's inventory
            // For normal click in top inventory: allow — standard chest interaction
        }

        // Shift-click from bottom (player's own) inventory into the top inventory
        if (rawSlot >= topInv.getSize() && event.isShiftClick()) {
            // We need to prevent items from landing in placeholder slots.
            // The simplest approach: let the event fire normally, then clean up placeholders
            // But a cleaner approach: cancel and handle manually
            // For simplicity, we allow it — Bukkit won't put items in occupied (placeholder) slots
            // since placeholders occupy those slots. So shift-click naturally goes to empty non-placeholder slots.
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        BotInventoryGUI gui = BotInventoryGUI.getView(topInv);
        if (gui == null) return;

        if (gui.getBot().isRemoved()) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }

        // If any of the dragged slots hit a placeholder slot in the top inventory, cancel the drag
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topInv.getSize() && gui.isPlaceholderSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        BotInventoryGUI gui = BotInventoryGUI.getView(topInv);
        if (gui == null) return;

        // Sync GUI contents back to the bot's real inventory
        gui.syncToBot();

        // Clean up tracking
        gui.removeTracking();

        if (event.getPlayer() instanceof Player player) {
            player.updateInventory();
        }
    }
}
