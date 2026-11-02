package cn.lunadeer.mc.deerfoliaplus.worldgen;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Isolates one real vanilla connection while exercising Minecraft's actual child-piece placer.
 * Unrelated connectors are directed to the empty pool; this is not a natural-frequency fixture.
 * Parent and child dimensions, blocks, selected connector, rotation and collision checks stay real.
 */
final class StructureGenerationFixture {
    static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private static final Map<String, CompoundTag> ORIGINALS = new HashMap<>();

    record Case(String name, StructureRepair repair, String parent, String child, BlockPos connector,
                String pool, boolean repairParent, Projection projection, boolean expansionHack) {
        @Override
        public String toString() {
            return this.name;
        }
    }

    record Generated(Case fixture, Rotation parentRotation, StructureTemplate parentTemplate,
                     StructureTemplate childTemplate, StructureTemplateManager manager,
                     PoolElementStructurePiece parent, List<PoolElementStructurePiece> children) {
    }

    static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        cases.add(new Case("snowy shepherd", StructureRepair.SNOWY_SHEPHERDS_HOUSE,
                "village/snowy/streets/corner_02", "village/snowy/houses/snowy_shepherds_house_1",
                new BlockPos(9, 1, 12), "village/snowy/houses", false, Projection.TERRAIN_MATCHING, true));
        for (int i = 1; i <= 5; i++) {
            cases.add(new Case("trail upper " + i, StructureRepair.valueOf("TRAIL_RUINS_GROUP_UPPER_" + i),
                    "trail_ruins/buildings/group_hall_5", "trail_ruins/buildings/group_upper_" + i,
                    new BlockPos(5, 5, 3), "trail_ruins/buildings/grouped", false, Projection.RIGID, false));
        }
        cases.add(new Case("trail road end", StructureRepair.TRAIL_RUINS_ROAD_END,
                "trail_ruins/roads/road_spacer_1", "trail_ruins/roads/road_end_1",
                new BlockPos(4, 1, 2), "trail_ruins/roads", false, Projection.RIGID, false));
        cases.add(new Case("trial corridor wall", StructureRepair.TRIAL_CORRIDOR_WALL,
                "trial_chambers/corridor/straight_7", "trial_chambers/corridor/addon/wall",
                new BlockPos(11, 1, 1), "trial_chambers/corridors/addon/lower", false, Projection.RIGID, false));
        cases.add(new Case("trial walkway bridge", StructureRepair.TRIAL_WALKWAY_BRIDGE,
                "trial_chambers/chamber/chamber_1", "trial_chambers/chamber/addon/walkway_with_bridge_1",
                new BlockPos(22, 8, 14), "trial_chambers/chamber/addon", false, Projection.RIGID, false));
        cases.add(new Case("pale garden camp 2", StructureRepair.PALE_GARDEN_CAMPSITE_2,
                "abandoned_camp/tent/pale_garden/tent_pale_garden_10", "abandoned_camp/camp/pale_garden/campsite_pale_garden_2",
                new BlockPos(0, 0, 3), "abandoned_camp/camp/pale_garden", false, Projection.RIGID, true));
        cases.add(new Case("upper-floor disposal", StructureRepair.TRIAL_CHAMBER_8_DISPOSAL,
                "trial_chambers/chamber/chamber_8", "trial_chambers/decor/disposal",
                new BlockPos(13, 12, 12), "trial_chambers/decor/disposal", true, Projection.RIGID, false));
        cases.add(new Case("bastion terminal wall", StructureRepair.BASTION_PATHWAY_WALL,
                "bastion/units/pathways/pathway_wall_0", "bastion/units/wall_units/unit_0",
                new BlockPos(1, 1, 1), "bastion/units/wall_units", true, Projection.RIGID, false));
        cases.add(new Case("ancient city bridge east continuation", StructureRepair.ANCIENT_CITY_WALL_BRIDGE,
                "ancient_city/walls/intact_horizontal_wall_bridge", "ancient_city/walls/intact_horizontal_wall_1",
                new BlockPos(17, 0, 10), "ancient_city/walls", true, Projection.RIGID, false));
        return List.copyOf(cases);
    }

    static CompoundTag original(String path) throws IOException {
        synchronized (ORIGINALS) {
            CompoundTag cached = ORIGINALS.get(path);
            if (cached == null) {
                try (InputStream input = StructureGenerationFixture.class.getResourceAsStream("/data/minecraft/structure/" + path + ".nbt")) {
                    if (input == null) {
                        throw new IOException("Missing real vanilla template: " + path);
                    }
                    cached = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
                    ORIGINALS.put(path, cached);
                }
            }
            return cached.copy();
        }
    }

    static StructureTemplate load(CompoundTag data) {
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK, data);
        return template;
    }

    private static Identifier id(String name) {
        return Identifier.withDefaultNamespace(name);
    }

    private static ResourceKey<StructureTemplatePool> key(String name) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, id(name));
    }

    /** OFF uses the original bytes; ON enables only the one repair in the fixture. */
    static Generated generate(Case fixture, boolean enabled, Rotation rotation, long seed) throws Exception {
        CompoundTag parentData = original(fixture.parent());
        CompoundTag childData = original(fixture.child());
        if (fixture.repairParent()) {
            parentData = StructureTemplateRepairs.apply(id(fixture.parent()), parentData, value -> enabled && value == fixture.repair());
        } else {
            childData = StructureTemplateRepairs.apply(id(fixture.child()), childData, value -> enabled && value == fixture.repair());
        }
        // Keep only the real connector being examined. In particular, a road's occupied parent
        // entry must not masquerade as an unused outgoing connection in an isolated fixture.
        for (CompoundTag block : parentData.getListOrEmpty("blocks").compoundStream().toList()) {
            Optional<CompoundTag> metadata = block.getCompound("nbt");
            if (metadata.isPresent() && "minecraft:jigsaw".equals(metadata.get().getStringOr("id", ""))
                    && !position(block.getListOrEmpty("pos")).equals(fixture.connector())) {
                metadata.get().putString("pool", "minecraft:empty");
            }
        }
        StructureTemplate parentTemplate = load(parentData);
        StructureTemplate childTemplate = load(childData);
        StructureTemplateManager manager = mock(StructureTemplateManager.class);
        Map<Identifier, StructureTemplate> templates = Map.of(id(fixture.parent()), parentTemplate, id(fixture.child()), childTemplate);
        when(manager.getOrCreate(any(Identifier.class))).thenAnswer(call -> {
            StructureTemplate template = templates.get(call.getArgument(0));
            if (template == null) {
                throw new IllegalStateException("Unexpected template in bounded fixture: " + call.getArgument(0));
            }
            return template;
        });

        StructurePoolElement parentElement = StructurePoolElement.single(fixture.parent()).apply(fixture.projection());
        StructurePoolElement childElement = StructurePoolElement.single(fixture.child()).apply(Projection.RIGID);
        MappedRegistry<StructureTemplatePool> pools = new MappedRegistry<>(Registries.TEMPLATE_POOL, Lifecycle.stable());
        Holder.Reference<StructureTemplatePool> empty = pools.createRegistrationLookup().getOrThrow(Pools.EMPTY);
        pools.register(Pools.EMPTY, new StructureTemplatePool(empty, List.of()), RegistrationInfo.BUILT_IN);
        Set<ResourceKey<StructureTemplatePool>> referencedPools = new LinkedHashSet<>();
        for (StructurePoolElement element : List.of(parentElement, childElement)) {
            for (StructureTemplate.JigsawBlockInfo jigsaw : element.getShuffledJigsawBlocks(manager, BlockPos.ZERO, Rotation.NONE, RandomSource.create(1))) {
                referencedPools.add(jigsaw.pool());
            }
        }
        referencedPools.add(key(fixture.pool()));
        for (ResourceKey<StructureTemplatePool> referenced : referencedPools) {
            if (!referenced.equals(Pools.EMPTY)) {
                StructurePoolElement candidate = referenced.equals(key(fixture.pool())) ? childElement : EmptyPoolElement.INSTANCE;
                int weight = candidate == childElement ? originalCandidateWeight(fixture) : 1;
                pools.register(referenced, new StructureTemplatePool(empty, List.of(Pair.of(candidate, weight))), RegistrationInfo.BUILT_IN);
            }
        }
        pools.freeze();

        BoundingBox parentBox = parentElement.getBoundingBox(manager, ORIGIN, rotation);
        // A street is reached as a child during real village generation. Its existing expansion
        // hack reserves vertical room for its interior house sockets before this step runs.
        // Preserve that reservation here; do not disable or loosen the actual child collision test.
        if (fixture.projection() == Projection.TERRAIN_MATCHING && fixture.expansionHack()) {
            int childHeight = childElement.getBoundingBox(manager, BlockPos.ZERO, Rotation.NONE).getYSpan();
            parentBox.encapsulate(new BlockPos(parentBox.minX(), parentBox.minY() + Math.max(childHeight + 1, parentBox.getYSpan() - 1), parentBox.minZ()));
        }
        PoolElementStructurePiece parent = new PoolElementStructurePiece(manager, parentElement, ORIGIN,
                parentElement.getGroundLevelDelta(), rotation, parentBox, LiquidSettings.IGNORE_WATERLOGGING);
        List<PoolElementStructurePiece> pieces = new ArrayList<>();
        pieces.add(parent);
        VoxelShape free = Shapes.join(Shapes.create(new AABB(-128, -64, -128, 128, 320, 128)),
                Shapes.create(AABB.of(parentBox)), BooleanOp.ONLY_FIRST);
        ChunkGenerator generator = mock(ChunkGenerator.class);
        when(generator.getFirstFreeHeight(anyInt(), anyInt(), any(Heightmap.Types.class), any(LevelHeightAccessor.class), any(RandomState.class)))
                .thenReturn(64);
        Method method = JigsawPlacement.class.getDeclaredMethod("addPieces", RandomState.class, int.class,
                boolean.class, ChunkGenerator.class, StructureTemplateManager.class, LevelHeightAccessor.class,
                RandomSource.class, Registry.class, PoolElementStructurePiece.class, List.class, VoxelShape.class,
                PoolAliasLookup.class, LiquidSettings.class);
        method.setAccessible(true);
        try {
            method.invoke(null, mock(RandomState.class), 1, fixture.expansionHack(), generator, manager,
                    LevelHeightAccessor.create(-64, 384), RandomSource.create(seed), pools, parent, pieces,
                    free, PoolAliasLookup.EMPTY, LiquidSettings.IGNORE_WATERLOGGING);
        } catch (InvocationTargetException failure) {
            throw new AssertionError("Actual JigsawPlacement failed for " + fixture.name(), failure.getCause());
        }
        return new Generated(fixture, rotation, parentTemplate, childTemplate, manager, parent,
                List.copyOf(pieces.subList(1, pieces.size())));
    }

    private static BlockPos position(ListTag values) {
        return new BlockPos(values.getIntOr(0, 0), values.getIntOr(1, 0), values.getIntOr(2, 0));
    }

    /** Retain the actual candidate weight; candidate isolation changes no selected element weight. */
    private static int originalCandidateWeight(Case fixture) throws IOException {
        try (InputStream input = StructureGenerationFixture.class.getResourceAsStream(
                "/data/minecraft/worldgen/template_pool/" + fixture.pool() + ".json")) {
            if (input == null) {
                throw new IOException("Missing real vanilla pool: " + fixture.pool());
            }
            var pool = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : pool.getAsJsonArray("elements")) {
                var candidate = entry.getAsJsonObject();
                var location = candidate.getAsJsonObject("element").get("location");
                if (location != null && id(fixture.child()).equals(Identifier.parse(location.getAsString()))) {
                    return candidate.get("weight").getAsInt();
                }
            }
            throw new IOException("Child is absent from the vanilla candidate pool: " + fixture.child());
        }
    }

    /**
     * Run the selected pool element's actual block placement and default processors into a sink.
     * This fixture only models blocks: entity creation needs a live ServerLevel and is covered by
     * the isolated startup harness. Keep entity NBT intact, but explicitly skip its placement here.
     */
    static Map<BlockPos, BlockState> placePiece(Generated generated, PoolElementStructurePiece piece) throws ReflectiveOperationException {
        WorldGenLevel level = mock(WorldGenLevel.class);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        when(level.holderLookup(Registries.BLOCK)).thenReturn(BuiltInRegistries.BLOCK);
        when(level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(call -> {
            blocks.put(((BlockPos) call.getArgument(0)).immutable(), call.getArgument(1));
            return true;
        });
        SinglePoolElement element = (SinglePoolElement) piece.getElement();
        Method getSettings = SinglePoolElement.class.getDeclaredMethod("getSettings", Rotation.class,
                BoundingBox.class, LiquidSettings.class, boolean.class);
        getSettings.setAccessible(true);
        StructurePlaceSettings settings = (StructurePlaceSettings) getSettings.invoke(element,
                piece.getRotation(), piece.getBoundingBox(), LiquidSettings.IGNORE_WATERLOGGING, false);
        settings.setIgnoreEntities(true);
        StructureTemplate template = piece == generated.parent() ? generated.parentTemplate() : generated.childTemplate();
        boolean placed = template.placeInWorld(
                level, piece.getPosition(), piece.getPosition(), settings, RandomSource.create(671L),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (!placed) {
            throw new AssertionError("Selected piece did not place blocks: " + generated.fixture().name());
        }
        return Map.copyOf(blocks);
    }
}
