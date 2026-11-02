package cn.lunadeer.mc.deerfoliaplus.worldgen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Small, guarded edits to known vanilla 26.3 templates. The caller verifies the resource origin
 * and baseline before invoking this class; these shape/value checks additionally fail closed.
 * The input is never mutated, and an unrelated or disabled template is returned by identity.
 */
public final class StructureTemplateRepairs {
    private static final String EMPTY = "minecraft:empty";
    private static final String AIR = "minecraft:air";
    private static final String JIGSAW = "minecraft:jigsaw";

    private record Rule(StructureRepair repair, int width, int height, int depth,
                        Predicate<CompoundTag> edit) {
    }

    private static final Map<String, Rule> RULES = Map.ofEntries(
            Map.entry("village/snowy/houses/snowy_shepherds_house_1",
                    new Rule(StructureRepair.SNOWY_SHEPHERDS_HOUSE, 9, 5, 10, tag ->
                            changeJigsaw(tag, 0, 0, 6, "west_up",
                                    Map.of("name", "minecraft:buidling_entrance", "target", "minecraft:buidling_entrance", "pool", EMPTY),
                                    Map.of("name", "minecraft:building_entrance", "target", "minecraft:building_entrance")))),
            Map.entry("trail_ruins/buildings/group_upper_1", upperRoom(StructureRepair.TRAIL_RUINS_GROUP_UPPER_1)),
            Map.entry("trail_ruins/buildings/group_upper_2", upperRoom(StructureRepair.TRAIL_RUINS_GROUP_UPPER_2)),
            Map.entry("trail_ruins/buildings/group_upper_3", upperRoom(StructureRepair.TRAIL_RUINS_GROUP_UPPER_3)),
            Map.entry("trail_ruins/buildings/group_upper_4", upperRoom(StructureRepair.TRAIL_RUINS_GROUP_UPPER_4)),
            Map.entry("trail_ruins/buildings/group_upper_5", upperRoom(StructureRepair.TRAIL_RUINS_GROUP_UPPER_5)),
            Map.entry("trail_ruins/roads/road_end_1",
                    new Rule(StructureRepair.TRAIL_RUINS_ROAD_END, 5, 5, 5, tag ->
                            changeJigsaw(tag, 4, 1, 2, "east_up",
                                    Map.of("name", "minecraft:road_out", "target", "minecraft:road_in", "pool", "minecraft:trail_ruins/roads"),
                                    Map.of("name", "minecraft:road_in", "target", EMPTY, "pool", EMPTY)))),
            Map.entry("trial_chambers/corridor/addon/wall",
                    new Rule(StructureRepair.TRIAL_CORRIDOR_WALL, 9, 6, 5, tag ->
                            addJigsaw(tag, 6, 0, 1, "minecraft:tuff_bricks", "down_west", "aligned",
                                    "minecraft:addon", EMPTY, EMPTY, "minecraft:tuff_bricks"))),
            Map.entry("trial_chambers/chamber/addon/walkway_with_bridge_1",
                    new Rule(StructureRepair.TRIAL_WALKWAY_BRIDGE, 11, 12, 15,
                            StructureTemplateRepairs::repairWalkway)),
            Map.entry("abandoned_camp/camp/pale_garden/campsite_pale_garden_2",
                    new Rule(StructureRepair.PALE_GARDEN_CAMPSITE_2, 10, 10, 11, tag ->
                            addJigsaw(tag, 9, 0, 4, "minecraft:dirt_path", "east_up", "rollable",
                                    "minecraft:end_connector", "minecraft:connector", EMPTY, "minecraft:dirt_path"))),
            Map.entry("trial_chambers/chamber/chamber_8",
                    new Rule(StructureRepair.TRIAL_CHAMBER_8_DISPOSAL, 19, 24, 14, tag ->
                            changeJigsaw(tag, 13, 12, 12, "up_south",
                                    Map.of("target", "minecraft:disposal", "pool", "minecraft:trial_chambers/decor"),
                                    Map.of("pool", "minecraft:trial_chambers/decor/disposal")))),
            Map.entry("bastion/units/pathways/pathway_wall_0",
                    new Rule(StructureRepair.BASTION_PATHWAY_WALL, 3, 2, 2, tag ->
                            changeJigsaw(tag, 1, 1, 1, "south_up",
                                    Map.of("target", "minecraft:wall_entry", "pool", "minecraft:bastion/units/wall_units"),
                                    Map.of("target", "minecraft:edge_wall_entry")))),
            Map.entry("ancient_city/walls/intact_horizontal_wall_bridge",
                    new Rule(StructureRepair.ANCIENT_CITY_WALL_BRIDGE, 18, 15, 21, tag ->
                            addJigsaw(tag, 17, 0, 10, "minecraft:deepslate_tiles", "east_up", "rollable",
                                    "minecraft:connect_wall", "minecraft:connect_wall",
                                    "minecraft:ancient_city/walls", "minecraft:deepslate_tiles")))
    );

    private StructureTemplateRepairs() {
    }

    public static CompoundTag apply(Identifier template, CompoundTag original,
                                    Predicate<StructureRepair> enabled) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(enabled, "enabled");
        if (!"minecraft".equals(template.getNamespace())) {
            return original;
        }
        Rule rule = RULES.get(template.getPath());
        if (rule == null || !enabled.test(rule.repair())
                || !hasSize(original, rule.width(), rule.height(), rule.depth())
                || !(original.get("palette") instanceof ListTag)
                || !(original.get("blocks") instanceof ListTag)) {
            return original;
        }
        CompoundTag copy = original.copy();
        return rule.edit().test(copy) ? copy : original;
    }

    private static Rule upperRoom(StructureRepair repair) {
        return new Rule(repair, 5, 12, 5, tag ->
                changeJigsaw(tag, 0, 2, 2, "west_up",
                        Map.of("name", EMPTY, "target", EMPTY, "pool", EMPTY),
                        Map.of("name", "minecraft:room", "target", "minecraft:hall",
                                "pool", "minecraft:trail_ruins/buildings/grouped", "final_state", "minecraft:dirt")));
    }

    private static boolean repairWalkway(CompoundTag template) {
        CompoundTag saved = blockAt(template, 7, 1, 4);
        if (saved == null || !hasBlockState(template, saved, "minecraft:structure_block", "mode", "save")
                || !(saved.get("nbt") instanceof CompoundTag metadata)
                || !"SAVE".equals(metadata.getStringOr("mode", ""))
                || !"minecraft:trial_chambers/chamber/addon/walkway_with_bridge_1".equals(metadata.getStringOr("name", ""))) {
            return false;
        }
        // The bridge deck is at local y=5. This entry aligns it with the chamber's middle floor,
        // and the lower deck at local y=0 with its lower floor, without bypassing placement checks.
        if (!addJigsaw(template, 10, 6, 12, AIR, "east_up", "rollable",
                "minecraft:middle_start", EMPTY, EMPTY, AIR)) {
            return false;
        }
        CompoundTag airState = new CompoundTag();
        airState.putString("id", AIR);
        saved.putInt("state", paletteIndex(template, airState));
        saved.remove("nbt");
        return true;
    }

    private static boolean changeJigsaw(CompoundTag template, int x, int y, int z, String orientation,
                                        Map<String, String> expected, Map<String, String> replacements) {
        CompoundTag block = blockAt(template, x, y, z);
        if (block == null || !hasBlockState(template, block, JIGSAW, "orientation", orientation)
                || !(block.get("nbt") instanceof CompoundTag metadata)
                || !JIGSAW.equals(metadata.getStringOr("id", ""))) {
            return false;
        }
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!entry.getValue().equals(metadata.getStringOr(entry.getKey(), ""))) {
                return false;
            }
        }
        replacements.forEach(metadata::putString);
        return true;
    }

    private static boolean addJigsaw(CompoundTag template, int x, int y, int z, String expectedBlock,
                                    String orientation, String joint, String name, String target,
                                    String pool, String finalState) {
        CompoundTag block = blockAt(template, x, y, z);
        if (block == null || !hasBlockState(template, block, expectedBlock, null, null) || block.contains("nbt")) {
            return false;
        }
        CompoundTag properties = new CompoundTag();
        properties.putString("orientation", orientation);
        CompoundTag state = new CompoundTag();
        state.putString("id", JIGSAW);
        state.put("properties", properties);
        block.putInt("state", paletteIndex(template, state));

        CompoundTag metadata = new CompoundTag();
        metadata.putString("id", JIGSAW);
        metadata.putString("joint", joint);
        metadata.putString("name", name);
        metadata.putString("target", target);
        metadata.putString("pool", pool);
        metadata.putString("final_state", finalState);
        metadata.putInt("placement_priority", 0);
        metadata.putInt("selection_priority", 0);
        block.put("nbt", metadata);
        return true;
    }

    private static CompoundTag blockAt(CompoundTag template, int x, int y, int z) {
        CompoundTag result = null;
        for (Tag element : template.getListOrEmpty("blocks")) {
            if (element instanceof CompoundTag block && hasTriple(block.getListOrEmpty("pos"), x, y, z)) {
                // Duplicate coordinates are not the known vanilla layout and must not be edited.
                if (result != null) {
                    return null;
                }
                result = block;
            }
        }
        return result;
    }

    private static boolean hasBlockState(CompoundTag template, CompoundTag block, String id,
                                        String property, String propertyValue) {
        ListTag palette = template.getListOrEmpty("palette");
        int index = block.getIntOr("state", -1);
        if (index < 0 || index >= palette.size() || !(palette.get(index) instanceof CompoundTag state)
                || !id.equals(state.getStringOr("id", ""))) {
            return false;
        }
        return property == null || propertyValue.equals(state.getCompoundOrEmpty("properties").getStringOr(property, ""));
    }

    private static int paletteIndex(CompoundTag template, CompoundTag state) {
        ListTag palette = template.getListOrEmpty("palette");
        for (int i = 0; i < palette.size(); i++) {
            if (state.equals(palette.get(i))) {
                return i;
            }
        }
        int index = palette.size();
        palette.add(state);
        return index;
    }

    private static boolean hasSize(CompoundTag template, int x, int y, int z) {
        return hasTriple(template.getListOrEmpty("size"), x, y, z);
    }

    private static boolean hasTriple(ListTag values, int x, int y, int z) {
        return values.size() == 3 && values.getIntOr(0, Integer.MIN_VALUE) == x
                && values.getIntOr(1, Integer.MIN_VALUE) == y
                && values.getIntOr(2, Integer.MIN_VALUE) == z;
    }
}
