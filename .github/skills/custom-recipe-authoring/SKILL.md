---
name: custom-recipe-authoring
description: Guide for defining, reviewing, and generating DeerFoliaPlus custom recipe YAML entries in config/custom-recipes.yml. Use when creating new shaped, shapeless, furnace, campfire, or stonecutter recipes, designing custom-id chains, validating result metadata, or checking recipe conflicts and reload expectations.
---

# Custom Recipe Authoring

Workflow for creating or reviewing DeerFoliaPlus custom recipes in `config/custom-recipes.yml`.

Use this skill when the user wants to:
- add a new custom recipe
- convert a gameplay idea into YAML
- design chained recipes with `custom-id`
- review whether a recipe definition is valid
- document or explain how a recipe should be written

## Outcome

Produce one or more valid DeerFoliaPlus recipe entries plus a short validation summary covering:
- recipe type and target workstation
- required and optional fields
- whether `custom-id` matching is intentional and consistent
- whether the recipe needs server restart to take effect
- whether the design may conflict with existing vanilla or custom recipes

## Step 1: Confirm Feature Scope

Before writing a recipe, confirm the feature assumptions:

1. `config/deer-folia-plus.yml` has:
   ```yaml
   custom-recipe:
     enabled: true
   ```
2. The actual recipe definitions live in `config/custom-recipes.yml`
3. Recipe changes require a **server restart** to take effect

If the user only asks for a recipe snippet, still mention the enable switch and restart requirement in the final answer.

## Step 2: Choose Recipe Type

Map the gameplay intent to the correct `type`:

| Type | Workstation | Use when |
|---|---|---|
| `shaped` | crafting table / 2x2 inventory grid | layout matters |
| `shapeless` | crafting table / 2x2 inventory grid | order does not matter |
| `smelting` | furnace | standard smelting |
| `blasting` | blast furnace | fast ore-like smelting |
| `smoking` | smoker | food cooking |
| `campfire_cooking` | campfire | fuel-free slow cooking |
| `stonecutting` | stonecutter | one-input cutting conversion |

Decision rules:
- Use `shaped` when slot position matters or the user describes a pattern.
- Use `shapeless` when only ingredient membership matters.
- Use furnace-family recipes only when there is exactly one input ingredient.
- Use `stonecutting` for single-input block conversion without experience or cooking time.

## Step 3: Define Recipe ID

Each recipe lives under:

```yaml
recipes:
  recipe_id:
```

Rules:
- Use unique lowercase English identifiers with underscores.
- The runtime namespace becomes `deerfoliaplus:recipe_id`.
- Prefer IDs that describe output or progression stage, such as `magic_pickaxe` or `refined_copper`.

## Step 4: Build the Result First

All recipe types share the same `result` structure:

```yaml
result:
  item: minecraft:diamond_sword
  amount: 1
  name: "&6Golden Sword"
  custom-id: golden_sword
  enchantments:
    sharpness: 5
  nbt:
    Tier: 3
```

Validation rules:
- `item` is required and should use the full namespaced item ID.
- `amount` is optional and defaults to `1`.
- `name` is optional and supports `&` or `§` formatting codes.
- `custom-id` is optional but should be added intentionally when the output must participate in later recipes.
- `enchantments` uses vanilla enchantment IDs without the `minecraft:` prefix.
- `nbt` stores values in `minecraft:custom_data` and can coexist with `custom-id`.

## Step 5: Define Inputs by Recipe Family

### A. `shaped`

Required fields:
- `pattern`
- `ingredients`

Template:

```yaml
recipe_id:
  type: shaped
  group: ""
  category: misc
  pattern:
    - "ABA"
    - " C "
    - "ABA"
  ingredients:
    A:
      item: minecraft:iron_ingot
    B:
      item: minecraft:diamond
      custom-id: refined_diamond
    C: minecraft:stick
  result:
    item: minecraft:diamond_pickaxe
```

Checks:
- Pattern must be at most `3x3`.
- Each non-space symbol in `pattern` must exist in `ingredients`.
- Spaces represent empty slots.
- `2x2` patterns can work in player inventory crafting.
- Mirrored patterns are supported.

### B. `shapeless`

Required fields:
- `ingredients`

Template:

```yaml
recipe_id:
  type: shapeless
  group: ""
  category: misc
  ingredients:
    - item: minecraft:diamond_sword
      custom-id: magic_blade
    - minecraft:nether_star
  result:
    item: minecraft:diamond_sword
```

Checks:
- Ingredient order does not matter.
- Short and full ingredient forms may be mixed.
- If the recipe is expected to work in the 2x2 inventory grid, keep ingredient count within four.

### C. Furnace-family recipes

Applies to:
- `smelting`
- `blasting`
- `smoking`
- `campfire_cooking`

Required fields:
- `ingredient`
- `result`

Template:

```yaml
recipe_id:
  type: smelting
  group: ""
  category: misc
  ingredient:
    item: minecraft:coal
    custom-id: magic_coal
  result:
    item: minecraft:diamond
  experience: 1.0
  cooking-time: 200
```

Checks:
- Use a single `ingredient`, not an `ingredients` list.
- `experience` is optional and defaults to `0`.
- `cooking-time` is optional.
- Sensible defaults by type:
  - `smelting`: `200`
  - `blasting`: often `100`
  - `smoking`: often `100` to `200`
  - `campfire_cooking`: often `600`

### D. `stonecutting`

Required fields:
- `ingredient`
- `result`

Template:

```yaml
recipe_id:
  type: stonecutting
  group: ""
  ingredient:
    item: minecraft:stone
    custom-id: stub_stone
  result:
    item: minecraft:stone_bricks
    amount: 4
```

Checks:
- Do not add `experience`.
- Do not add `cooking-time`.

## Step 6: Decide Whether `custom-id` Is Needed

Use `custom-id` only when the gameplay requires item identity beyond visible appearance.

Good uses:
- chained crafting progression
- preventing normal vanilla items from substituting a custom intermediate
- preserving invisible recipe-only state on items

Design rules:
- If the output must be consumed by a later recipe, assign a `result.custom-id`.
- If an input must only accept a prior custom item, require the matching `ingredient.custom-id` or `ingredients[*].custom-id`.
- Keep IDs stable and semantically named.

Chain example:

```yaml
recipes:
  stub_stone:
    type: shaped
    pattern:
      - "SS"
      - "SS"
    ingredients:
      S: minecraft:stone
    result:
      item: minecraft:stone
      custom-id: stub_stone

  super_stone:
    type: shaped
    pattern:
      - "SS"
      - "SS"
    ingredients:
      S:
        item: minecraft:stone
        custom-id: stub_stone
    result:
      item: minecraft:stone
      custom-id: super_stone
```

## Step 7: Run a Validation Pass

Before presenting the YAML, check the following:

1. The recipe ID is unique and descriptive.
2. The `type` matches the workstation and number of inputs.
3. `result.item` is a full Minecraft item ID.
4. All required fields for that recipe family are present.
5. `custom-id` appears on both producer and consumer sides when chaining is intended.
6. Furnace-family recipes use `ingredient`, not `ingredients`.
7. `stonecutting` does not include furnace-only fields.
8. `shaped.pattern` symbols and `ingredients` keys match.
9. Any backpack-crafting claim is valid only for `2x2` shaped recipes or shapeless recipes with up to four ingredients.
10. The answer mentions restart behavior.

## Step 8: Check Conflicts and Player Experience

When reviewing or designing recipes, explicitly call out:
- whether the custom recipe may override or out-prioritize a vanilla recipe
- whether `custom-id` is preventing accidental matches correctly
- whether naming, enchantments, and NBT make the output understandable to players
- whether the recipe is too close to an existing custom recipe and may confuse progression

Important behavior notes:
- Recipes with `custom-id` constraints have higher priority than broader matches.
- Custom recipes can override vanilla recipes when materials and shape overlap.

## Response Pattern

When using this skill, structure the answer in this order:

1. State the chosen recipe type and why.
2. Provide the final YAML snippet.
3. List any optional fields that were intentionally used.
4. Call out `custom-id` behavior if present.
5. Mention restart requirement and any conflict risks.

## Example Prompts

- Create a `shaped` custom recipe that turns 4 stone into a named custom stone intermediate.
- Design a two-step recipe chain using `custom-id` so only the crafted intermediate can be upgraded.
- Review this `custom-recipes.yml` entry and tell me whether the `shaped` pattern and ingredients are valid.
- Convert this gameplay idea into a `smoking` recipe with a renamed result and custom NBT.
- Write a `stonecutting` recipe that only accepts a custom intermediate item.