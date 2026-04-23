package cn.lunadeer.mc.deerfoliaplus.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

public class DFPStonecuttingRecipe extends StonecutterRecipe {

    private final String group;
    private final String requiredCustomId;

    public DFPStonecuttingRecipe(String group, Ingredient ingredient, ItemStack result,
                                 String requiredCustomId) {
        super(new Recipe.CommonInfo(true), ingredient, ItemStackTemplate.fromNonEmptyStack(result));
        this.group = group;
        this.requiredCustomId = requiredCustomId;
    }

    @Override
    public String group() {
        return this.group;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return super.matches(input, level) && CustomItemHelper.matchesCustomId(input.item(), requiredCustomId);
    }

    @Override
    public boolean isSpecial() {
        return requiredCustomId != null;
    }
}
