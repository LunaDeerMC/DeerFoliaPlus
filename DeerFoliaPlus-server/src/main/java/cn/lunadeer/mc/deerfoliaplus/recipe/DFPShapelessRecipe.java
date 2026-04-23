package cn.lunadeer.mc.deerfoliaplus.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A shapeless crafting recipe that additionally checks hidden custom IDs on input items.
 * Uses backtracking to match ingredients (including custom ID requirements) to items.
 */
public class DFPShapelessRecipe extends ShapelessRecipe {

    private final List<Ingredient> dfpIngredients;
    private final Map<Integer, String> ingredientCustomIds;

    public DFPShapelessRecipe(String group, CraftingBookCategory category,
                              ItemStack result, List<Ingredient> ingredients,
                              Map<Integer, String> ingredientCustomIds) {
        super(
                new Recipe.CommonInfo(true),
                new CraftingRecipe.CraftingBookInfo(category, group),
                ItemStackTemplate.fromNonEmptyStack(result),
                ingredients
        );
        this.dfpIngredients = ingredients;
        this.ingredientCustomIds = ingredientCustomIds;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != this.dfpIngredients.size()) return false;

        // Collect non-empty items from input
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) items.add(stack);
        }

        if (items.size() != this.dfpIngredients.size()) return false;

        // Use backtracking to find a valid assignment
        boolean[] used = new boolean[items.size()];
        return matchRecursive(items, used, 0);
    }

    private boolean matchRecursive(List<ItemStack> items, boolean[] used, int ingredientIndex) {
        if (ingredientIndex >= this.dfpIngredients.size()) return true;

        Ingredient ingredient = this.dfpIngredients.get(ingredientIndex);
        String requiredId = this.ingredientCustomIds.get(ingredientIndex);

        for (int j = 0; j < items.size(); j++) {
            if (used[j]) continue;
            ItemStack stack = items.get(j);

            if (ingredient.test(stack) && CustomItemHelper.matchesCustomId(stack, requiredId)) {
                used[j] = true;
                if (matchRecursive(items, used, ingredientIndex + 1)) return true;
                used[j] = false;
            }
        }
        return false;
    }

    @Override
    public boolean isSpecial() {
        return !ingredientCustomIds.isEmpty();
    }
}
