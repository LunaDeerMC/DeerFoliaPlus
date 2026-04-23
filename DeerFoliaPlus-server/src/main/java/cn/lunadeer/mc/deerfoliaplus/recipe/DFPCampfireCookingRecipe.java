package cn.lunadeer.mc.deerfoliaplus.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public class DFPCampfireCookingRecipe extends CampfireCookingRecipe {

    private final String requiredCustomId;

    public DFPCampfireCookingRecipe(String group, CookingBookCategory category, Ingredient input,
                                    ItemStack result, float experience, int cookingTime,
                                    String requiredCustomId) {
        super(
                new Recipe.CommonInfo(true),
                new AbstractCookingRecipe.CookingBookInfo(category, group),
                input,
                ItemStackTemplate.fromNonEmptyStack(result),
                experience,
                cookingTime
        );
        this.requiredCustomId = requiredCustomId;
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
