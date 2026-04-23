package cn.lunadeer.mc.deerfoliaplus.recipe;

import com.mojang.logging.LogUtils;
import io.papermc.paper.configuration.PaperConfigurations;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CustomRecipeManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAMESPACE = "deerfoliaplus";
    private static final List<ResourceKey<Recipe<?>>> registeredRecipeKeys = new ArrayList<>();

    public static void registerAllRecipes() {
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null) {
            LOGGER.error("Cannot register custom recipes: server is null");
            return;
        }

        RecipeManager recipeManager = server.getRecipeManager();

        // Remove previously registered DFP recipes to support reload
        for (ResourceKey<Recipe<?>> key : registeredRecipeKeys) {
            recipeManager.removeRecipe(key);
        }
        registeredRecipeKeys.clear();

        File configDir = new File(PaperConfigurations.CONFIG_DIR);
        File recipesFile = new File(configDir, "custom-recipes.yml");

        if (!recipesFile.exists()) {
            saveDefaultRecipesFile(recipesFile);
            LOGGER.info("Created default custom-recipes.yml");
            return; // Default file has no active recipes
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(recipesFile);
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
        if (recipesSection == null) {
            LOGGER.info("No custom recipes defined");
            return;
        }

        HolderLookup.Provider registries = server.registries().compositeAccess();

        // First pass: collect all recipes and sort by specificity
        // (recipes with more custom ID requirements should be registered later for priority)
        List<Map.Entry<String, RecipeHolder<?>>> recipeEntries = new ArrayList<>();

        for (String recipeId : recipesSection.getKeys(false)) {
            ConfigurationSection recipeSection = recipesSection.getConfigurationSection(recipeId);
            if (recipeSection == null) continue;

            try {
                RecipeHolder<?> holder = parseRecipe(recipeId, recipeSection, registries);
                if (holder != null) {
                    recipeEntries.add(Map.entry(recipeId, holder));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to parse custom recipe '{}': {}", recipeId, e.getMessage());
            }
        }

        // Sort: recipes without custom IDs first, recipes with custom IDs later (higher priority)
        recipeEntries.sort((a, b) -> {
            int aSpecificity = getCustomIdCount(a.getValue());
            int bSpecificity = getCustomIdCount(b.getValue());
            return Integer.compare(aSpecificity, bSpecificity);
        });

        // Register all recipes in batch, then finalize once
        int count = 0;
        for (Map.Entry<String, RecipeHolder<?>> entry : recipeEntries) {
            try {
                recipeManager.recipes.addRecipe(entry.getValue());
                registeredRecipeKeys.add(entry.getValue().id());
                count++;
            } catch (Exception e) {
                LOGGER.error("Failed to register custom recipe '{}': {}", entry.getKey(), e.getMessage());
            }
        }

        if (count > 0) {
            recipeManager.finalizeRecipeLoading();
            LOGGER.info("Registered {} custom recipes", count);
        }
    }

    private static int getCustomIdCount(RecipeHolder<?> holder) {
        return holder.value().isSpecial() ? 1 : 0;
    }

    private static RecipeHolder<?> parseRecipe(String recipeId, ConfigurationSection section,
                                               HolderLookup.Provider registries) {
        String type = section.getString("type", "shaped");
        ResourceKey<Recipe<?>> key = ResourceKey.create(
                Registries.RECIPE, Identifier.parse(NAMESPACE + ":" + recipeId));

        Recipe<?> recipe = switch (type.toLowerCase()) {
            case "shaped" -> parseShaped(section, registries);
            case "shapeless" -> parseShapeless(section, registries);
            case "smelting" -> parseCooking(section, registries, "smelting");
            case "blasting" -> parseCooking(section, registries, "blasting");
            case "smoking" -> parseCooking(section, registries, "smoking");
            case "campfire_cooking" -> parseCooking(section, registries, "campfire_cooking");
            case "stonecutting" -> parseStonecutting(section, registries);
            default -> {
                LOGGER.warn("Unknown recipe type '{}' for recipe '{}'", type, recipeId);
                yield null;
            }
        };

        if (recipe == null) return null;
        return new RecipeHolder<>(key, recipe);
    }

    // ======================== Shaped Recipe ========================

    private static Recipe<?> parseShaped(ConfigurationSection section,
                                         HolderLookup.Provider registries) {
        List<String> pattern = section.getStringList("pattern");
        if (pattern.isEmpty()) {
            LOGGER.warn("Shaped recipe missing 'pattern'");
            return null;
        }

        ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
        if (ingredientsSection == null) {
            LOGGER.warn("Shaped recipe missing 'ingredients'");
            return null;
        }

        // Parse ingredient definitions (char -> item + optional custom-id)
        Map<Character, Ingredient> ingredientMap = new LinkedHashMap<>();
        Map<Character, String> charCustomIds = new HashMap<>();

        for (String charKey : ingredientsSection.getKeys(false)) {
            if (charKey.length() != 1) {
                LOGGER.warn("Ingredient key must be a single character, got '{}'", charKey);
                continue;
            }
            char c = charKey.charAt(0);
            ConfigurationSection ingredientDef = ingredientsSection.getConfigurationSection(charKey);
            if (ingredientDef == null) {
                // Simple string format: "S: minecraft:stone"
                String itemId = ingredientsSection.getString(charKey);
                Item item = lookupItem(itemId);
                if (item == null) continue;
                ingredientMap.put(c, Ingredient.of(item));
                continue;
            }

            String itemId = ingredientDef.getString("item");
            Item item = lookupItem(itemId);
            if (item == null) continue;
            ingredientMap.put(c, Ingredient.of(item));

            String customId = ingredientDef.getString("custom-id");
            if (customId != null && !customId.isEmpty()) {
                charCustomIds.put(c, customId);
            }
        }

        // Build pattern and compute custom ID requirements by position
        ShapedRecipePattern recipePattern;
        try {
            recipePattern = ShapedRecipePattern.of(ingredientMap, pattern);
        } catch (Exception e) {
            LOGGER.warn("Invalid shaped recipe pattern: {}", e.getMessage());
            return null;
        }

        // Map pattern characters to position indices for custom ID tracking
        Map<Integer, String> positionCustomIds = new HashMap<>();
        // Re-parse the pattern to get position-to-char mapping
        // ShapedRecipePattern.of() shrinks the pattern, so we need to match the shrunk version
        String[] shrunkPattern = shrinkPattern(pattern);
        int width = shrunkPattern.length > 0 ? shrunkPattern[0].length() : 0;
        for (int row = 0; row < shrunkPattern.length; row++) {
            for (int col = 0; col < shrunkPattern[row].length(); col++) {
                char c = shrunkPattern[row].charAt(col);
                if (c != ' ' && charCustomIds.containsKey(c)) {
                    positionCustomIds.put(row * width + col, charCustomIds.get(c));
                }
            }
        }

        // Build result
        ItemStack result = buildResult(section.getConfigurationSection("result"), registries);
        if (result == null) return null;

        CraftingBookCategory category = CraftingBookCategory.MISC;
        String categoryStr = section.getString("category", "misc");
        try {
            category = CraftingBookCategory.valueOf(categoryStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }

        String group = section.getString("group", "");

        return new DFPShapedRecipe(group, category, recipePattern, result, positionCustomIds);
    }

    // ======================== Shapeless Recipe ========================

    private static Recipe<?> parseShapeless(ConfigurationSection section,
                                             HolderLookup.Provider registries) {
        List<?> ingredientsList = section.getList("ingredients");
        if (ingredientsList == null || ingredientsList.isEmpty()) {
            LOGGER.warn("Shapeless recipe missing 'ingredients'");
            return null;
        }

        List<Ingredient> ingredients = new ArrayList<>();
        Map<Integer, String> customIds = new HashMap<>();

        ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
        if (ingredientsSection != null) {
            // Indexed format under section
            for (String key : ingredientsSection.getKeys(false)) {
                parseShapelessIngredient(ingredientsSection, key, ingredients, customIds);
            }
        } else {
            // List format
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) ingredientsList;
            for (int i = 0; i < list.size(); i++) {
                Object entry = list.get(i);
                if (entry instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) entry;
                    String itemId = (String) map.get("item");
                    Item item = lookupItem(itemId);
                    if (item == null) continue;
                    ingredients.add(Ingredient.of(item));
                    String customId = (String) map.get("custom-id");
                    if (customId != null && !customId.isEmpty()) {
                        customIds.put(ingredients.size() - 1, customId);
                    }
                } else if (entry instanceof String itemId) {
                    Item item = lookupItem(itemId);
                    if (item == null) continue;
                    ingredients.add(Ingredient.of(item));
                }
            }
        }

        if (ingredients.isEmpty()) {
            LOGGER.warn("Shapeless recipe has no valid ingredients");
            return null;
        }

        ItemStack result = buildResult(section.getConfigurationSection("result"), registries);
        if (result == null) return null;

        CraftingBookCategory category = CraftingBookCategory.MISC;
        String categoryStr = section.getString("category", "misc");
        try {
            category = CraftingBookCategory.valueOf(categoryStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }

        String group = section.getString("group", "");
        return new DFPShapelessRecipe(group, category, result, ingredients, customIds);
    }

    private static void parseShapelessIngredient(ConfigurationSection section, String key,
                                                  List<Ingredient> ingredients,
                                                  Map<Integer, String> customIds) {
        ConfigurationSection ingredientDef = section.getConfigurationSection(key);
        if (ingredientDef != null) {
            String itemId = ingredientDef.getString("item");
            Item item = lookupItem(itemId);
            if (item == null) return;
            ingredients.add(Ingredient.of(item));
            String customId = ingredientDef.getString("custom-id");
            if (customId != null && !customId.isEmpty()) {
                customIds.put(ingredients.size() - 1, customId);
            }
        } else {
            String itemId = section.getString(key);
            Item item = lookupItem(itemId);
            if (item == null) return;
            ingredients.add(Ingredient.of(item));
        }
    }

    // ======================== Cooking Recipes ========================

    private static Recipe<?> parseCooking(ConfigurationSection section,
                                           HolderLookup.Provider registries,
                                           String cookingType) {
        ConfigurationSection ingredientSection = section.getConfigurationSection("ingredient");
        String ingredientItemId;
        String requiredCustomId = null;

        if (ingredientSection != null) {
            ingredientItemId = ingredientSection.getString("item");
            requiredCustomId = ingredientSection.getString("custom-id");
        } else {
            ingredientItemId = section.getString("ingredient");
        }

        Item ingredientItem = lookupItem(ingredientItemId);
        if (ingredientItem == null) return null;
        Ingredient ingredient = Ingredient.of(ingredientItem);

        ItemStack result = buildResult(section.getConfigurationSection("result"), registries);
        if (result == null) return null;

        float experience = (float) section.getDouble("experience", 0.0);
        int cookingTime = section.getInt("cooking-time", 200);

        CookingBookCategory category = CookingBookCategory.MISC;
        String categoryStr = section.getString("category", "misc");
        try {
            category = CookingBookCategory.valueOf(categoryStr.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }

        String group = section.getString("group", "");

        return switch (cookingType) {
            case "smelting" -> new DFPSmeltingRecipe(group, category, ingredient, result, experience, cookingTime, requiredCustomId);
            case "blasting" -> new DFPBlastingRecipe(group, category, ingredient, result, experience, cookingTime, requiredCustomId);
            case "smoking" -> new DFPSmokingRecipe(group, category, ingredient, result, experience, cookingTime, requiredCustomId);
            case "campfire_cooking" -> new DFPCampfireCookingRecipe(group, category, ingredient, result, experience, cookingTime, requiredCustomId);
            default -> null;
        };
    }

    // ======================== Stonecutting Recipe ========================

    private static Recipe<?> parseStonecutting(ConfigurationSection section,
                                                HolderLookup.Provider registries) {
        ConfigurationSection ingredientSection = section.getConfigurationSection("ingredient");
        String ingredientItemId;
        String requiredCustomId = null;

        if (ingredientSection != null) {
            ingredientItemId = ingredientSection.getString("item");
            requiredCustomId = ingredientSection.getString("custom-id");
        } else {
            ingredientItemId = section.getString("ingredient");
        }

        Item ingredientItem = lookupItem(ingredientItemId);
        if (ingredientItem == null) return null;
        Ingredient ingredient = Ingredient.of(ingredientItem);

        ItemStack result = buildResult(section.getConfigurationSection("result"), registries);
        if (result == null) return null;

        String group = section.getString("group", "");
        return new DFPStonecuttingRecipe(group, ingredient, result, requiredCustomId);
    }

    // ======================== Helper Methods ========================

    private static Item lookupItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            LOGGER.warn("Missing item ID in recipe definition");
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        if (item == Items.AIR && !itemId.equals("minecraft:air")) {
            LOGGER.warn("Unknown item '{}' in recipe definition", itemId);
            return null;
        }
        return item;
    }

    private static ItemStack buildResult(ConfigurationSection resultSection,
                                          HolderLookup.Provider registries) {
        if (resultSection == null) {
            LOGGER.warn("Recipe missing 'result' section");
            return null;
        }

        String itemId = resultSection.getString("item");
        Item item = lookupItem(itemId);
        if (item == null) return null;

        int amount = resultSection.getInt("amount", 1);
        ItemStack result = new ItemStack(item, amount);

        // Set custom name
        String name = resultSection.getString("name");
        if (name != null && !name.isEmpty()) {
            // Support both & and § color codes
            String colored = name.replace('&', '\u00a7');
            net.kyori.adventure.text.Component adventureComponent =
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacySection().deserialize(colored);
            net.minecraft.network.chat.Component nmsComponent =
                    io.papermc.paper.adventure.PaperAdventure.asVanilla(adventureComponent);
            result.set(DataComponents.CUSTOM_NAME, nmsComponent);
        }

        // Set enchantments
        ConfigurationSection enchantmentsSection = resultSection.getConfigurationSection("enchantments");
        if (enchantmentsSection != null) {
            HolderLookup.RegistryLookup<Enchantment> enchantmentRegistry =
                    registries.lookupOrThrow(Registries.ENCHANTMENT);
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

            for (String enchantName : enchantmentsSection.getKeys(false)) {
                int level = enchantmentsSection.getInt(enchantName, 1);
                ResourceKey<Enchantment> enchantKey = ResourceKey.create(
                        Registries.ENCHANTMENT, Identifier.withDefaultNamespace(enchantName));
                Optional<Holder.Reference<Enchantment>> holder = enchantmentRegistry.get(enchantKey);
                if (holder.isPresent()) {
                    mutable.set(holder.get(), level);
                } else {
                    LOGGER.warn("Unknown enchantment '{}' in recipe result", enchantName);
                }
            }

            result.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        }

        // Set custom ID (hidden attribute)
        String customId = resultSection.getString("custom-id");
        if (customId != null && !customId.isEmpty()) {
            CustomItemHelper.setCustomId(result, customId);
        }

        // Set additional custom NBT
        ConfigurationSection nbtSection = resultSection.getConfigurationSection("nbt");
        if (nbtSection != null) {
            CompoundTag tag;
            CustomData existing = result.get(DataComponents.CUSTOM_DATA);
            if (existing != null) {
                tag = existing.copyTag();
            } else {
                tag = new CompoundTag();
            }
            // Merge NBT from config
            for (String nbtKey : nbtSection.getKeys(false)) {
                Object value = nbtSection.get(nbtKey);
                if (value instanceof String s) {
                    tag.putString(nbtKey, s);
                } else if (value instanceof Integer i) {
                    tag.putInt(nbtKey, i);
                } else if (value instanceof Double d) {
                    tag.putDouble(nbtKey, d);
                } else if (value instanceof Boolean b) {
                    tag.putBoolean(nbtKey, b);
                } else if (value instanceof Long l) {
                    tag.putLong(nbtKey, l);
                } else if (value instanceof Float f) {
                    tag.putFloat(nbtKey, f);
                }
            }
            result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }

        return result;
    }

    /**
     * Shrink pattern by removing empty rows/columns (same logic as ShapedRecipePattern).
     */
    private static String[] shrinkPattern(List<String> pattern) {
        int minCol = Integer.MAX_VALUE;
        int maxCol = 0;
        int topRow = 0;
        int bottomPadding = 0;

        for (int row = 0; row < pattern.size(); row++) {
            String line = pattern.get(row);
            int first = firstNonSpace(line);
            int last = lastNonSpace(line);
            if (last < 0) {
                if (topRow == row) topRow++;
                bottomPadding++;
            } else {
                bottomPadding = 0;
                minCol = Math.min(minCol, first);
                maxCol = Math.max(maxCol, last);
            }
        }

        if (pattern.size() == bottomPadding + topRow) {
            return new String[0];
        }

        String[] result = new String[pattern.size() - bottomPadding - topRow];
        for (int i = 0; i < result.length; i++) {
            result[i] = pattern.get(i + topRow).substring(minCol, maxCol + 1);
        }
        return result;
    }

    private static int firstNonSpace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != ' ') return i;
        }
        return s.length();
    }

    private static int lastNonSpace(String s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) != ' ') return i;
        }
        return -1;
    }

    private static void saveDefaultRecipesFile(File file) {
        try {
            file.getParentFile().mkdirs();
            String defaultContent = """
                    # DeerFoliaPlus Custom Recipes Configuration
                    # ==========================================
                    #
                    # Supported recipe types (type field):
                    #   shaped          - Crafting Table / Inventory 2x2 crafting (shaped)
                    #   shapeless       - Crafting Table / Inventory 2x2 crafting (shapeless)
                    #   smelting        - Furnace
                    #   blasting        - Blast Furnace
                    #   smoking         - Smoker
                    #   campfire_cooking - Campfire
                    #   stonecutting    - Stonecutter
                    #
                    # Result options:
                    #   item:           Minecraft item ID (e.g., minecraft:diamond_sword)
                    #   amount:         Stack size (default: 1)
                    #   name:           Custom display name with color codes (& or §, e.g., "&6Golden Blade")
                    #   custom-id:      Hidden identifier for distinguishing custom items from vanilla
                    #   enchantments:   Vanilla enchantments (e.g., sharpness: 5, unbreaking: 3)
                    #   nbt:            Custom NBT data as key-value pairs (string, int, double, boolean)
                    #
                    # Ingredient options:
                    #   item:           Minecraft item ID
                    #   custom-id:      Require a specific custom item (only matches items with this hidden ID)
                    #
                    # Example recipes (uncomment to enable):
                    #
                    # recipes:
                    #
                    #   # ============ Crafting Table (Shaped) ============
                    #   # 4 stones -> custom "Stubborn Stone"
                    #   stub_stone:
                    #     type: shaped
                    #     pattern:
                    #       - "SS"
                    #       - "SS"
                    #     ingredients:
                    #       S:
                    #         item: minecraft:stone
                    #     result:
                    #       item: minecraft:stone
                    #       amount: 1
                    #       name: "&6Stubborn Stone"
                    #       custom-id: stub_stone
                    #
                    #   # 4 stub stones -> "Super Stone" (requires custom-id input)
                    #   super_stone:
                    #     type: shaped
                    #     pattern:
                    #       - "SS"
                    #       - "SS"
                    #     ingredients:
                    #       S:
                    #         item: minecraft:stone
                    #         custom-id: stub_stone
                    #     result:
                    #       item: minecraft:stone
                    #       amount: 1
                    #       name: "&cSuper Stone"
                    #       custom-id: super_stone
                    #       enchantments:
                    #         unbreaking: 3
                    #
                    #   # Result with custom NBT data
                    #   tracking_compass:
                    #     type: shaped
                    #     pattern:
                    #       - " E "
                    #       - "ECE"
                    #       - " E "
                    #     ingredients:
                    #       E:
                    #         item: minecraft:ender_eye
                    #       C:
                    #         item: minecraft:compass
                    #     result:
                    #       item: minecraft:compass
                    #       amount: 1
                    #       name: "&bTracking Compass"
                    #       custom-id: tracking_compass
                    #       nbt:
                    #         TrackingType: "player"
                    #         TrackingRange: 256
                    #         IsCustomItem: true
                    #
                    #   # ============ Crafting Table (Shapeless) ============
                    #   # diamond sword + nether star -> magic sword
                    #   magic_sword:
                    #     type: shapeless
                    #     ingredients:
                    #       - item: minecraft:diamond_sword
                    #       - item: minecraft:nether_star
                    #     result:
                    #       item: minecraft:diamond_sword
                    #       amount: 1
                    #       name: "&dMagic Sword"
                    #       custom-id: magic_sword
                    #       enchantments:
                    #         sharpness: 5
                    #         unbreaking: 3
                    #         looting: 3
                    #
                    #   # ============ Furnace (Smelting) ============
                    #   # coal -> diamond in furnace
                    #   diamond_from_coal:
                    #     type: smelting
                    #     ingredient:
                    #       item: minecraft:coal
                    #     result:
                    #       item: minecraft:diamond
                    #       amount: 1
                    #     experience: 1.0
                    #     cooking-time: 200
                    #
                    #   # ============ Blast Furnace (Blasting) ============
                    #   # raw copper -> custom "Refined Copper" in blast furnace (faster)
                    #   refined_copper:
                    #     type: blasting
                    #     ingredient:
                    #       item: minecraft:raw_copper
                    #     result:
                    #       item: minecraft:copper_ingot
                    #       amount: 2
                    #       name: "&6Refined Copper"
                    #       custom-id: refined_copper
                    #     experience: 0.7
                    #     cooking-time: 100
                    #
                    #   # ============ Smoker (Smoking) ============
                    #   # golden apple + custom id -> super golden apple in smoker
                    #   super_golden_apple:
                    #     type: smoking
                    #     ingredient:
                    #       item: minecraft:golden_apple
                    #     result:
                    #       item: minecraft:enchanted_golden_apple
                    #       amount: 1
                    #       name: "&eSuper Golden Apple"
                    #       custom-id: super_golden_apple
                    #     experience: 2.0
                    #     cooking-time: 400
                    #
                    #   # ============ Campfire (Campfire Cooking) ============
                    #   # porkchop -> special cooked porkchop on campfire
                    #   gourmet_porkchop:
                    #     type: campfire_cooking
                    #     ingredient:
                    #       item: minecraft:porkchop
                    #     result:
                    #       item: minecraft:cooked_porkchop
                    #       amount: 1
                    #       name: "&6Gourmet Porkchop"
                    #       custom-id: gourmet_porkchop
                    #       nbt:
                    #         Quality: "premium"
                    #     experience: 0.35
                    #     cooking-time: 600
                    #
                    #   # ============ Stonecutter ============
                    #   # custom stub stone -> stone bricks in stonecutter
                    #   bricks_from_stub_stone:
                    #     type: stonecutting
                    #     ingredient:
                    #       item: minecraft:stone
                    #       custom-id: stub_stone
                    #     result:
                    #       item: minecraft:stone_bricks
                    #       amount: 4
                    """;
            Files.writeString(file.toPath(), defaultContent);
        } catch (IOException e) {
            LOGGER.error("Failed to create default custom-recipes.yml: {}", e.getMessage());
        }
    }
}
