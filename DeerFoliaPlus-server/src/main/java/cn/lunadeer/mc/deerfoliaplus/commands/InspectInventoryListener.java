package cn.lunadeer.mc.deerfoliaplus.commands;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class InspectInventoryListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        InspectInventoryGUI gui = InspectInventoryGUI.getView(topInv);
        if (gui == null) return;

        if (gui.getTarget().isRemoved()) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < topInv.getSize()) {
            if (gui.isPlaceholderSlot(rawSlot)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        InspectInventoryGUI gui = InspectInventoryGUI.getView(topInv);
        if (gui == null) return;

        if (gui.getTarget().isRemoved()) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }

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
        InspectInventoryGUI gui = InspectInventoryGUI.getView(topInv);
        if (gui == null) return;

        gui.syncToTarget();
        gui.removeTracking();

        if (event.getPlayer() instanceof Player player) {
            player.updateInventory();
        }
    }
}
