package org.leavesmc.leaves.bot.gui;

/**
 * Defines the three types of bot inventory views that can be opened as chest GUIs.
 */
public enum BotInventoryViewType {

    /**
     * Full inventory: armor + offhand + main inventory + hotbar (54 slots / 6 rows)
     */
    INVENTORY("Inventory", 54),

    /**
     * Equipment only: armor + offhand (9 slots / 1 row)
     */
    EQUIPMENT("Equipment", 9),

    /**
     * Backpack only: main inventory + hotbar (36 slots / 4 rows)
     */
    BACKPACK("Backpack", 36);

    private final String displayName;
    private final int size;

    BotInventoryViewType(String displayName, int size) {
        this.displayName = displayName;
        this.size = size;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSize() {
        return size;
    }
}
