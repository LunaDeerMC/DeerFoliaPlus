package cn.lunadeer.mc.deerfoliaplus.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Utility for reading and writing hidden custom IDs on items.
 * The custom ID is stored in the item's CustomData component under a specific key,
 * invisible to players but usable for distinguishing custom items from regular ones.
 */
public class CustomItemHelper {

    public static final String CUSTOM_ID_KEY = "DeerFoliaPlus.CustomItemId";

    public static String getCustomId(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(CUSTOM_ID_KEY)) return null;
        String value = tag.getStringOr(CUSTOM_ID_KEY, "");
        return value.isEmpty() ? null : value;
    }

    public static void setCustomId(ItemStack stack, String customId) {
        CompoundTag tag;
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing != null) {
            tag = existing.copyTag();
        } else {
            tag = new CompoundTag();
        }
        tag.putString(CUSTOM_ID_KEY, customId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean matchesCustomId(ItemStack stack, String requiredCustomId) {
        if (requiredCustomId == null) return true;
        return requiredCustomId.equals(getCustomId(stack));
    }
}
