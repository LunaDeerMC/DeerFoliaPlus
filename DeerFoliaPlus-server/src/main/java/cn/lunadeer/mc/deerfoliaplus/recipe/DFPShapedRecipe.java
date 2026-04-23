package cn.lunadeer.mc.deerfoliaplus.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A shaped crafting recipe that additionally checks hidden custom IDs on input items.
 */
public class DFPShapedRecipe extends ShapedRecipe {

    private final Map<Integer, String> ingredientCustomIds;
    private final int cachedIngredientCount;

    public DFPShapedRecipe(String group, CraftingBookCategory category,
                           ShapedRecipePattern pattern, ItemStack result,
                           Map<Integer, String> ingredientCustomIds) {
        super(
                new Recipe.CommonInfo(true),
                new CraftingRecipe.CraftingBookInfo(category, group),
                pattern,
                ItemStackTemplate.fromNonEmptyStack(result)
        );
        this.ingredientCustomIds = ingredientCustomIds;
        this.cachedIngredientCount = (int) getIngredients().stream().filter(Optional::isPresent).count();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != this.cachedIngredientCount) return false;
        if (input.width() != getWidth() || input.height() != getHeight()) return false;

        // Try normal orientation first, then mirrored
        if (matchesWithCustomId(input, false)) return true;
        return matchesWithCustomId(input, true);
    }

    private boolean matchesWithCustomId(CraftingInput input, boolean mirrored) {
        List<Optional<Ingredient>> ingredients = getIngredients();
        int width = getWidth();
        int height = getHeight();

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int patternIdx = row * width + (mirrored ? (width - 1 - col) : col);
                Optional<Ingredient> ingredient = ingredients.get(patternIdx);
                ItemStack stack = input.getItem(col, row);

                // Check vanilla ingredient match
                if (!Ingredient.testOptionalIngredient(ingredient, stack)) return false;

                // Check custom ID requirement
                String requiredId = ingredientCustomIds.get(patternIdx);
                if (!CustomItemHelper.matchesCustomId(stack, requiredId)) return false;
            }
        }
        return true;
    }

    @Override
    public boolean isSpecial() {
        return !ingredientCustomIds.isEmpty();
    }
}
