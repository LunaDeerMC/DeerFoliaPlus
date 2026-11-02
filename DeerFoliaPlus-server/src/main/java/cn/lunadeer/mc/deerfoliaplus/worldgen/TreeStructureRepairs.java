package cn.lunadeer.mc.deerfoliaplus.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Template-local tree clearance fixes; unrelated and disabled templates are returned untouched. */
public final class TreeStructureRepairs {
    private TreeStructureRepairs() {
    }

    private record Spec(StructureRepair setting, Identifier pool, @Nullable Identifier replacement,
                        BlockPos connector, List<Identifier> dependencies) {
    }

    private record Move(BlockPos source, BlockPos destination, String block) {
    }

    private static final List<Move> SNOWY_CAMP_MOVES = List.of(
        new Move(new BlockPos(4, 1, 6), new BlockPos(4, 1, 8), "minecraft:sweet_berry_bush"),
        new Move(new BlockPos(5, 1, 6), new BlockPos(5, 1, 8), "minecraft:sweet_berry_bush"),
        new Move(new BlockPos(6, 1, 6), new BlockPos(6, 1, 8), "minecraft:sweet_berry_bush"),
        new Move(new BlockPos(6, 1, 4), new BlockPos(7, 1, 4), "minecraft:barrel")
    );

    private static final Map<Identifier, Spec> SPECS = Map.ofEntries(
        spec("abandoned_camp/camp/old_growth_pine_taiga/campsite_old_growth_pine_taiga_1", StructureRepair.CAMP_OLD_GROWTH_PINE_TAIGA_1_TREE,
            "minecraft:abandoned_camp/trees/mega_pine", "minecraft:deerfoliaplus/structure_fixes/trees/campsite_old_growth_pine_taiga_1", new BlockPos(8, 0, 4),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/mega_pine.json"),
                resource("worldgen/placed_feature/mega_pine_checked.json"),
                resource("worldgen/feature/mega_pine.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"),
                resource("worldgen/block_state_provider/podzol_beneath_tree.json"))),
        spec("abandoned_camp/camp/old_growth_spruce_taiga/campsite_old_growth_spruce_taiga_4", StructureRepair.CAMP_OLD_GROWTH_SPRUCE_TAIGA_4_TREE,
            "minecraft:abandoned_camp/trees/thick_spruce", "minecraft:deerfoliaplus/structure_fixes/trees/campsite_old_growth_spruce_taiga_4", new BlockPos(2, 0, 7),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/thick_spruce.json"),
                resource("worldgen/placed_feature/mega_spruce_checked.json"),
                resource("worldgen/feature/mega_spruce.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"),
                resource("worldgen/block_state_provider/podzol_beneath_tree.json"))),
        spec("abandoned_camp/camp/snowy_taiga/campsite_snowy_taiga_3", StructureRepair.CAMP_SNOWY_TAIGA_3_TREE,
            "minecraft:abandoned_camp/trees/mega_pine", null, new BlockPos(5, 0, 5),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/mega_pine.json"),
                resource("worldgen/placed_feature/mega_pine_checked.json"),
                resource("worldgen/feature/mega_pine.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"),
                resource("worldgen/block_state_provider/podzol_beneath_tree.json"))),
        spec("abandoned_camp/tent/cherry_grove/tent_cherry_grove_1", StructureRepair.TENT_CHERRY_GROVE_1_TREE,
            "minecraft:abandoned_camp/trees/cherry", "minecraft:deerfoliaplus/structure_fixes/trees/tent_cherry_grove_1", new BlockPos(3, 0, 3),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/cherry.json"),
                resource("worldgen/placed_feature/cherry_checked.json"),
                resource("worldgen/feature/cherry.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"))),
        spec("abandoned_camp/tent/dappled_forest/tent_dappled_forest_1", StructureRepair.TENT_DAPPLED_FOREST_1_TREE,
            "minecraft:abandoned_camp/trees/red_poplar", "minecraft:deerfoliaplus/structure_fixes/trees/tent_dappled_forest_1", new BlockPos(4, 0, 3),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/red_poplar.json"),
                resource("worldgen/placed_feature/red_poplar.json"),
                resource("worldgen/feature/red_poplar.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"))),
        spec("abandoned_camp/tent/dappled_forest/tent_dappled_forest_2", StructureRepair.TENT_DAPPLED_FOREST_2_TREE,
            "minecraft:abandoned_camp/trees/orange_poplar", "minecraft:deerfoliaplus/structure_fixes/trees/tent_dappled_forest_2", new BlockPos(4, 0, 3),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/orange_poplar.json"),
                resource("worldgen/placed_feature/orange_poplar.json"),
                resource("worldgen/feature/orange_poplar.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"))),
        spec("abandoned_camp/tent/dappled_forest/tent_dappled_forest_3", StructureRepair.TENT_DAPPLED_FOREST_3_TREE,
            "minecraft:abandoned_camp/trees/orange_poplar", "minecraft:deerfoliaplus/structure_fixes/trees/tent_dappled_forest_3", new BlockPos(2, 0, 1),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/orange_poplar.json"),
                resource("worldgen/placed_feature/orange_poplar.json"),
                resource("worldgen/feature/orange_poplar.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"))),
        spec("abandoned_camp/tent/dappled_forest/tent_dappled_forest_4", StructureRepair.TENT_DAPPLED_FOREST_4_TREE,
            "minecraft:abandoned_camp/trees/yellow_poplar", "minecraft:deerfoliaplus/structure_fixes/trees/tent_dappled_forest_4", new BlockPos(3, 0, 4),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/yellow_poplar.json"),
                resource("worldgen/placed_feature/yellow_poplar.json"),
                resource("worldgen/feature/yellow_poplar.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"))),
        spec("abandoned_camp/tent/dappled_forest/tent_dappled_forest_6", StructureRepair.TENT_DAPPLED_FOREST_6_TREE,
            "minecraft:abandoned_camp/trees/red_poplar", "minecraft:deerfoliaplus/structure_fixes/trees/tent_dappled_forest_6", new BlockPos(4, 0, 6),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/red_poplar.json"),
                resource("worldgen/placed_feature/red_poplar.json"),
                resource("worldgen/feature/red_poplar.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"))),
        spec("abandoned_camp/tent/savanna/tent_savanna_1", StructureRepair.TENT_SAVANNA_1_TREE,
            "minecraft:abandoned_camp/trees/acacia", "minecraft:deerfoliaplus/structure_fixes/trees/tent_savanna_1", new BlockPos(3, 0, 3),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/acacia.json"),
                resource("worldgen/placed_feature/acacia_checked.json"),
                resource("worldgen/feature/acacia.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"))),
        spec("abandoned_camp/tent/snowy_taiga/tent_snowy_taiga_10", StructureRepair.TENT_SNOWY_TAIGA_10_TREE,
            "minecraft:abandoned_camp/trees/pine", "minecraft:deerfoliaplus/structure_fixes/trees/tent_snowy_taiga_10", new BlockPos(3, 0, 4),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/pine.json"),
                resource("worldgen/placed_feature/pine_checked.json"),
                resource("worldgen/feature/pine.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"))),
        spec("abandoned_camp/tent/taiga/tent_taiga_2", StructureRepair.TENT_TAIGA_2_TREE,
            "minecraft:abandoned_camp/trees/pine", "minecraft:deerfoliaplus/structure_fixes/trees/tent_taiga_2", new BlockPos(3, 0, 3),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/pine.json"),
                resource("worldgen/placed_feature/pine_checked.json"),
                resource("worldgen/feature/pine.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"))),
        spec("abandoned_camp/tent/taiga/tent_taiga_7", StructureRepair.TENT_TAIGA_7_TREE,
            "minecraft:abandoned_camp/trees/pine", "minecraft:deerfoliaplus/structure_fixes/trees/tent_taiga_7", new BlockPos(4, 0, 5),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/pine.json"),
                resource("worldgen/placed_feature/pine_checked.json"),
                resource("worldgen/feature/pine.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json"))),
        spec("abandoned_camp/tent/windswept_forest/tent_windswept_forest_6", StructureRepair.TENT_WINDSWEPT_FOREST_6_TREE,
            "minecraft:abandoned_camp/trees/spruce", "minecraft:deerfoliaplus/structure_fixes/trees/tent_windswept_forest_6", new BlockPos(4, 0, 3),
            List.of(resource("worldgen/template_pool/abandoned_camp/trees/spruce.json"),
                resource("worldgen/placed_feature/spruce_checked.json"),
                resource("worldgen/feature/spruce.json"),
                resource("worldgen/block_state_provider/soil_beneath_tree.json")))
    );

    public static Set<Identifier> templates() {
        return SPECS.keySet();
    }

    public static @Nullable StructureRepair setting(Identifier id) {
        Spec spec = SPECS.get(id);
        return spec == null ? null : spec.setting();
    }

    /** Original resources whose data-pack replacements must take precedence over this repair. */
    public static List<Identifier> dependencies(Identifier id) {
        Spec spec = SPECS.get(id);
        return spec == null ? List.of() : spec.dependencies();
    }

    public static CompoundTag apply(Identifier id, CompoundTag input, Predicate<StructureRepair> enabled) {
        Spec spec = SPECS.get(id);
        if (spec == null || !enabled.test(spec.setting())) {
            return input;
        }
        ListTag palette = input.getListOrEmpty("palette");
        Map<BlockPos, CompoundTag> blocks = index(input.getListOrEmpty("blocks"));
        if (palette.isEmpty() || blocks == null) {
            return input;
        }
        CompoundTag connector = blocks.get(spec.connector());
        if (connector == null || !blockName(connector, palette).equals("minecraft:jigsaw")
            || !connector.getCompoundOrEmpty("nbt").getStringOr("pool", "").equals(spec.pool().toString())) {
            return input;
        }
        if (spec.setting() == StructureRepair.CAMP_SNOWY_TAIGA_3_TREE) {
            if (!canMoveSnowyDecorations(blocks, palette)) {
                return input;
            }
            return moveSnowyDecorations(input);
        }
        if (spec.replacement() == null) {
            return input;
        }
        CompoundTag result = input.copy();
        Map<BlockPos, CompoundTag> copiedBlocks = index(result.getListOrEmpty("blocks"));
        if (copiedBlocks == null) {
            return input;
        }
        copiedBlocks.get(spec.connector()).getCompoundOrEmpty("nbt").putString("pool", spec.replacement().toString());
        return result;
    }

    private static boolean canMoveSnowyDecorations(Map<BlockPos, CompoundTag> blocks, ListTag palette) {
        for (Move move : SNOWY_CAMP_MOVES) {
            CompoundTag source = blocks.get(move.source());
            if (source == null || !blockName(source, palette).equals(move.block())) {
                return false;
            }
            CompoundTag destination = blocks.get(move.destination());
            if (destination != null) {
                String destinationName = blockName(destination, palette);
                if ((!destinationName.equals("minecraft:air") && !destinationName.equals("minecraft:snow"))
                    || destination.contains("nbt")) {
                    return false;
                }
            }
            CompoundTag ground = blocks.get(move.destination().below());
            if (ground == null) {
                return false;
            }
            String groundName = blockName(ground, palette);
            if (!groundName.equals("minecraft:dirt") && !groundName.equals("minecraft:grass_block")
                && !groundName.equals("minecraft:dirt_path")) {
                return false;
            }
        }
        return true;
    }

    private static CompoundTag moveSnowyDecorations(CompoundTag input) {
        CompoundTag result = input.copy();
        ListTag palette = result.getListOrEmpty("palette");
        ListTag blocks = result.getListOrEmpty("blocks");
        Map<BlockPos, CompoundTag> indexed = index(blocks);
        if (indexed == null) {
            return input;
        }
        int airIndex = -1;
        for (int i = 0; i < palette.size(); i++) {
            if (stateName(palette.getCompoundOrEmpty(i)).equals("minecraft:air")) {
                airIndex = i;
                break;
            }
        }
        if (airIndex < 0) {
            CompoundTag air = new CompoundTag();
            air.putString(palette.getCompoundOrEmpty(0).contains("Name") ? "Name" : "id", "minecraft:air");
            airIndex = palette.size();
            palette.add(air);
        }
        for (Move move : SNOWY_CAMP_MOVES) {
            CompoundTag source = indexed.get(move.source());
            CompoundTag destination = indexed.get(move.destination());
            if (destination != null) {
                blocks.remove(destination);
            }
            // Moving the entire block record retains berry age, barrel facing and all loot NBT.
            source.put("pos", positionTag(move.destination()));
            CompoundTag air = new CompoundTag();
            air.put("pos", positionTag(move.source()));
            air.putInt("state", airIndex);
            blocks.add(air);
        }
        return result;
    }

    private static @Nullable Map<BlockPos, CompoundTag> index(ListTag blocks) {
        Map<BlockPos, CompoundTag> result = new HashMap<>();
        for (Tag tag : blocks) {
            if (!(tag instanceof CompoundTag block)) {
                return null;
            }
            ListTag pos = block.getListOrEmpty("pos");
            if (pos.size() != 3 || pos.getInt(0).isEmpty() || pos.getInt(1).isEmpty() || pos.getInt(2).isEmpty()) {
                return null;
            }
            BlockPos position = new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
            if (result.put(position, block) != null) {
                return null;
            }
        }
        return result;
    }

    private static ListTag positionTag(BlockPos pos) {
        ListTag result = new ListTag();
        result.add(IntTag.valueOf(pos.getX()));
        result.add(IntTag.valueOf(pos.getY()));
        result.add(IntTag.valueOf(pos.getZ()));
        return result;
    }

    private static String blockName(CompoundTag block, ListTag palette) {
        int state = block.getIntOr("state", -1);
        return state < 0 || state >= palette.size() ? "" : stateName(palette.getCompoundOrEmpty(state));
    }

    private static String stateName(CompoundTag state) {
        return state.getStringOr("id", state.getStringOr("Name", ""));
    }

    private static Identifier resource(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    private static Map.Entry<Identifier, Spec> spec(String template, StructureRepair setting, String pool,
                                                   @Nullable String replacement, BlockPos connector,
                                                   List<Identifier> dependencies) {
        return Map.entry(resource(template), new Spec(setting, Identifier.parse(pool),
            replacement == null ? null : Identifier.parse(replacement), connector, dependencies));
    }
}
