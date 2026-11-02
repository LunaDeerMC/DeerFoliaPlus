package cn.lunadeer.mc.deerfoliaplus.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** Real placed features and tree generators, with an in-memory block world and no running server. */
class TreeStructureRepairsTest {
    private static HolderLookup.Provider lookup;
    private static ChunkGenerator generator;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var resources = new FallbackResourceManager(PackType.SERVER_DATA, "minecraft");
        resources.push(ServerPacksSource.createVanillaPackSource().fullResources());
        TagLoader.loadTagsForExistingRegistries(resources,
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)).forEach(pending -> pending.apply());
        lookup = VanillaRegistries.createWorldLookup();
        assertTrue(Blocks.DIRT.defaultBlockState().is(BlockTags.DIRT), "Vanilla block tags were not loaded");
        generator = new FlatLevelSource(FlatLevelGeneratorSettings.getDefault(
            lookup.lookupOrThrow(Registries.BIOME), lookup.lookupOrThrow(Registries.STRUCTURE_SET),
            lookup.lookupOrThrow(Registries.PLACED_FEATURE)));
    }

    @Test
    void fixesFourteenTreesAcrossAllRotationsAndEightSeedsWithoutOverwritingDecorations() throws Exception {
        assertEquals(14, TreeStructureRepairs.templates().size());
        for (Identifier id : TreeStructureRepairs.templates()) {
            CompoundTag original = template(id);
            CompoundTag snapshot = original.copy();
            assertSame(original, TreeStructureRepairs.apply(id, original, setting -> false));
            StructureRepair selected = TreeStructureRepairs.setting(id);
            assertSame(original, TreeStructureRepairs.apply(id, original, setting -> setting != selected));
            CompoundTag fixed = TreeStructureRepairs.apply(id, original, setting -> true);
            assertNotSame(original, fixed, id.toString());
            assertSame(fixed, TreeStructureRepairs.apply(id, fixed, setting -> true));
            assertEquals(snapshot, original, "Source template changed: " + id);
            PlacedFeature vanillaFeature = feature(original);
            PlacedFeature fixedFeature = feature(fixed);
            for (Rotation rotation : Rotation.values()) {
                BlockPos origin = connector(original).above().rotate(rotation);
                for (int seed = 0; seed < 8; seed++) {
                    String label = id + " / " + rotation + " / " + seed;
                    Fixture vanilla = new Fixture(original, rotation);
                    assertFalse(vanillaFeature.place(vanilla.level, generator, RandomSource.create(seed), origin), label);
                    Fixture repaired = new Fixture(fixed, rotation);
                    assertTrue(fixedFeature.place(repaired.level, generator, RandomSource.create(seed), origin), label);
                    repaired.assertDecorationsPreserved(label);
                    if (id.getPath().contains("/camp/")) {
                        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) for (int y = 0; y < 8; y++) {
                            assertTrue(repaired.get(origin.offset(x, y, z)).is(BlockTags.LOGS), "Incomplete giant trunk: " + label);
                        }
                    }
                }
            }
            Fixture obstruction = new Fixture(fixed, Rotation.NONE);
            BlockPos origin = connector(original).above();
            obstruction.blocks.put(origin.above(), Blocks.STONE.defaultBlockState());
            assertFalse(fixedFeature.place(obstruction.level, generator, RandomSource.create(0), origin), "Trunk obstruction ignored: " + id);
            if (id.getPath().contains("/camp/")) {
                Fixture secondColumn = new Fixture(fixed, Rotation.NONE);
                secondColumn.blocks.put(origin.offset(1, 1, 1), Blocks.STONE.defaultBlockState());
                assertFalse(fixedFeature.place(secondColumn.level, generator, RandomSource.create(0), origin), "Giant trunk obstruction ignored: " + id);
            }
        }
    }

    @Test
    void workingControlTemplatesRemainUnchanged() throws Exception {
        for (String path : List.of("abandoned_camp/camp/old_growth_pine_taiga/campsite_old_growth_pine_taiga_4",
            "abandoned_camp/tent/dappled_forest/tent_dappled_forest_7")) {
            Identifier id = Identifier.withDefaultNamespace(path);
            CompoundTag original = template(id);
            assertSame(original, TreeStructureRepairs.apply(id, original, setting -> true));
            for (Rotation rotation : Rotation.values()) {
                Fixture world = new Fixture(original, rotation);
                assertTrue(feature(original).place(world.level, generator, RandomSource.create(0),
                    connector(original).above().rotate(rotation)), path);
            }
        }
    }

    @Test
    void snowyCampRetainsBerryStateBarrelNbtAndSizeAndIsIdempotent() throws Exception {
        Identifier id = Identifier.withDefaultNamespace("abandoned_camp/camp/snowy_taiga/campsite_snowy_taiga_3");
        CompoundTag original = template(id);
        CompoundTag fixed = TreeStructureRepairs.apply(id, original, setting -> true);
        for (int x = 4; x <= 6; x++) assertMoved(original, fixed, new BlockPos(x, 1, 6), new BlockPos(x, 1, 8));
        assertMoved(original, fixed, new BlockPos(6, 1, 4), new BlockPos(7, 1, 4));
        assertEquals(original.getListOrEmpty("size"), fixed.getListOrEmpty("size"));
        assertEquals(pool(original), pool(fixed));
        assertSame(fixed, TreeStructureRepairs.apply(id, fixed, setting -> true));
    }

    @Test
    void unexpectedPoolOrOccupiedMoveTargetFailsClosed() throws Exception {
        Identifier id = Identifier.withDefaultNamespace("abandoned_camp/tent/dappled_forest/tent_dappled_forest_1");
        CompoundTag original = template(id);
        at(original, connector(original)).getCompoundOrEmpty("nbt").putString("pool", "example:custom_tree");
        CompoundTag snapshot = original.copy();
        assertSame(original, TreeStructureRepairs.apply(id, original, setting -> true));
        assertEquals(snapshot, original);
        Identifier snowy = Identifier.withDefaultNamespace("abandoned_camp/camp/snowy_taiga/campsite_snowy_taiga_3");
        CompoundTag changed = template(snowy);
        at(changed, new BlockPos(4, 1, 8)).put("nbt", new CompoundTag());
        assertSame(changed, TreeStructureRepairs.apply(snowy, changed, setting -> true));
    }

    private static void assertMoved(CompoundTag before, CompoundTag after, BlockPos source, BlockPos target) {
        CompoundTag a = at(before, source).copy(), b = at(after, target).copy();
        a.remove("pos"); b.remove("pos");
        assertEquals(a, b, source + " -> " + target);
        assertTrue(state(after, at(after, source)).isAir());
    }

    private static CompoundTag template(Identifier id) throws IOException {
        try (InputStream in = resource("structure/" + id.getPath() + ".nbt")) {
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }
    }

    private static InputStream resource(String path) {
        return Objects.requireNonNull(TreeStructureRepairsTest.class.getResourceAsStream("/data/minecraft/" + path), path);
    }

    private static JsonObject json(String path) throws IOException {
        try (var reader = new InputStreamReader(resource(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static PlacedFeature feature(CompoundTag data) throws IOException {
        JsonObject element = json("worldgen/template_pool/" + pool(data).getPath() + ".json")
            .getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("element");
        Identifier placedId = Identifier.parse(element.get("feature").getAsString());
        JsonObject placed = json("worldgen/placed_feature/" + placedId.getPath() + ".json");
        Identifier featureId = Identifier.parse(placed.get("feature").getAsString());
        placed.add("feature", json("worldgen/feature/" + featureId.getPath() + ".json"));
        return PlacedFeature.DIRECT_CODEC.parse(lookup.createSerializationContext(JsonOps.INSTANCE), placed).getOrThrow();
    }

    private static Identifier pool(CompoundTag data) {
        return Identifier.parse(at(data, connector(data)).getCompoundOrEmpty("nbt").getStringOr("pool", ""));
    }

    private static BlockPos connector(CompoundTag data) {
        for (Tag tag : data.getListOrEmpty("blocks")) {
            CompoundTag block = (CompoundTag) tag;
            if (block.getCompoundOrEmpty("nbt").getStringOr("pool", "").contains("/trees/")) return pos(block);
        }
        throw new AssertionError("Missing tree connector");
    }

    private static CompoundTag at(CompoundTag data, BlockPos pos) {
        for (Tag tag : data.getListOrEmpty("blocks")) {
            CompoundTag block = (CompoundTag) tag;
            if (pos(block).equals(pos)) return block;
        }
        throw new AssertionError("Missing block " + pos);
    }

    private static BlockPos pos(CompoundTag block) {
        ListTag p = block.getListOrEmpty("pos");
        return new BlockPos(p.getIntOr(0, 0), p.getIntOr(1, 0), p.getIntOr(2, 0));
    }

    private static BlockState state(CompoundTag template, CompoundTag block) {
        CompoundTag state = template.getListOrEmpty("palette").getCompoundOrEmpty(block.getIntOr("state", -1));
        if (state.contains("Name")) {
            // The shipped templates predate the block-state field rename performed by the data fixer.
            state = state.copy();
            state.putString("id", state.getStringOr("Name", ""));
            state.put("properties", state.getCompoundOrEmpty("Properties"));
        }
        return NbtUtils.readBlockState(BuiltInRegistries.BLOCK, state);
    }

    private static final class Fixture implements InvocationHandler {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final Map<BlockPos, BlockState> initial = new HashMap<>();
        private final WorldGenLevel level;

        Fixture(CompoundTag data, Rotation rotation) {
            for (Tag tag : data.getListOrEmpty("blocks")) {
                CompoundTag block = (CompoundTag) tag;
                BlockState state = state(data, block);
                if (state.is(Blocks.JIGSAW)) {
                    String id = block.getCompoundOrEmpty("nbt").getStringOr("final_state", "minecraft:air");
                    state = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id.split("\\[")[0])).defaultBlockState();
                }
                blocks.put(pos(block).rotate(rotation), state.rotate(rotation));
            }
            initial.putAll(blocks);
            level = (WorldGenLevel) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {WorldGenLevel.class}, this);
        }

        BlockState get(BlockPos pos) {
            return blocks.getOrDefault(pos, pos.getY() < 0 ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }

        void assertDecorationsPreserved(String label) {
            for (var entry : initial.entrySet()) {
                BlockState state = entry.getValue();
                if (!state.isAir() && !state.is(BlockTags.REPLACEABLE_BY_TREES) && !state.is(BlockTags.DIRT)
                    && !state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.DIRT_PATH) && !state.is(Blocks.SNOW)) {
                    assertTrue(get(entry.getKey()).is(state.getBlock()), "Overwritten decoration " + entry.getKey() + " / " + label);
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "getBlockState" -> get((BlockPos) args[0]);
                case "getFluidState" -> get((BlockPos) args[0]).getFluidState();
                case "isStateAtPosition" -> ((Predicate<BlockState>) args[1]).test(get((BlockPos) args[0]));
                case "isFluidAtPosition" -> ((Predicate<FluidState>) args[1]).test(get((BlockPos) args[0]).getFluidState());
                case "setBlock" -> { blocks.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]); yield true; }
                case "getMinY" -> -64;
                case "getHeight" -> 384;
                case "getMaxY" -> 319;
                case "getSeed" -> 0L;
                case "getRandom" -> RandomSource.create(0);
                // Paper retains this optional Level for flat-bedrock modifiers; these trees do not use it.
                case "getLevel" -> null;
                case "getBlockEntity" -> args.length > 1 ? Optional.empty() : null;
                case "neighborShapeChanged", "blockUpdated", "levelEvent", "gameEvent", "scheduleTick" -> null;
                case "isClientSide" -> false;
                case "toString" -> "TreeRepairFixture";
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> proxy == args[0];
                default -> {
                    if (method.isDefault()) yield InvocationHandler.invokeDefault(proxy, method, args);
                    throw new UnsupportedOperationException(method.toString());
                }
            };
        }
    }
}
