package cn.lunadeer.mc.deerfoliaplus.worldgen;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.BlockPileFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@VanillaFeature
class VillagePileRepairsTest {
    private static final int MIN_Y = -64;
    private static final long SEED = 99412L;
    private static final Map<String, StructureRepair> SWITCHES = Map.of(
            "pile_hay", StructureRepair.VILLAGE_HAY_PILE,
            "pile_pumpkin", StructureRepair.VILLAGE_PUMPKIN_PILE,
            "pile_ice", StructureRepair.VILLAGE_ICE_PILE,
            "pile_melon", StructureRepair.VILLAGE_MELON_PILE,
            "pile_snow", StructureRepair.VILLAGE_SNOW_PILE);

    @ParameterizedTest
    @ValueSource(strings = {"pile_hay", "pile_pumpkin", "pile_ice", "pile_melon", "pile_snow"})
    void realVanillaFeaturesPlaceAtOneToFourLayersWithoutCrossingWorldBounds(String name) {
        var feature = vanilla(name);
        var pile = assertInstanceOf(BlockPileFeature.class, feature.value().feature().value());
        var resources = new VanillaRepairResources();
        for (int layers : new int[] {0, 1, 2, 3, 4, 5, 6, 64}) {
            World original = new World(layers);
            RandomSource originalRandom = RandomSource.create(SEED);
            boolean vanillaResult = feature.value().place(original.level, original.generator, originalRandom, original.origin());
            assertEquals(layers >= 5, vanillaResult, name + " original layers=" + layers);
            for (boolean enabled : new boolean[] {false, true}) {
                World actual = new World(layers);
                RandomSource random = RandomSource.create(SEED);
                boolean result = VillagePileRepairs.place(feature, resources.manager, actual.level, actual.generator, random,
                        actual.origin(), repair -> enabled && repair == SWITCHES.get(name));
                boolean expected = layers >= (enabled ? 1 : 5);
                assertEquals(expected, result, name + " layers=" + layers + " enabled=" + enabled);
                assertEquals(expected, !actual.changed.isEmpty());
                assertEquals(5, VillagePileRepairs.minimumClearance(pile), "Scope must end after placement");
                if (!enabled || layers >= 5 || layers == 0) {
                    assertEquals(original.changed, actual.changed, "Unchanged cases must produce exactly the same blocks");
                    // A fresh control avoids advancing the baseline stream for the next comparison.
                    World control = new World(layers);
                    RandomSource controlRandom = RandomSource.create(SEED);
                    feature.value().place(control.level, control.generator, controlRandom, control.origin());
                    assertEquals(controlRandom.nextLong(), random.nextLong(), "No extra RNG draws in unchanged cases");
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"pile_hay", "pile_pumpkin", "pile_ice", "pile_melon", "pile_snow"})
    void eachSwitchIsIndependent(String name) {
        var resources = new VanillaRepairResources();
        World world = new World(4);
        assertFalse(VillagePileRepairs.place(vanilla(name), resources.manager, world.level, world.generator,
                RandomSource.create(SEED), world.origin(), repair -> repair != SWITCHES.get(name)));
        assertTrue(world.changed.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"worldgen/feature/", "worldgen/placed_feature/"})
    void customPackResourcesPreserveVanillaHeightEvenWithIdenticalBytes(String directory) throws Exception {
        var resources = new VanillaRepairResources();
        Identifier id = Identifier.withDefaultNamespace(directory + "pile_hay.json");
        resources.replace(id, VanillaRepairResources.bytes(id), false);
        assertUnchangedAtFourLayers(vanilla("pile_hay"), resources);
    }

    @ParameterizedTest
    @ValueSource(strings = {"worldgen/feature/", "worldgen/placed_feature/"})
    void changedOrMissingResourceCannotMasqueradeAsVanilla(String directory) throws Exception {
        var resources = new VanillaRepairResources();
        Identifier id = Identifier.withDefaultNamespace(directory + "pile_hay.json");
        resources.replace(id, "{}".getBytes(StandardCharsets.UTF_8), true);
        assertUnchangedAtFourLayers(vanilla("pile_hay"), resources);
        resources.remove(id);
        assertUnchangedAtFourLayers(vanilla("pile_hay"), resources);
    }

    @Test
    void directCustomNamespaceAndUnrelatedFeaturesAreUnaffected() {
        var resources = new VanillaRepairResources();
        var actual = vanilla("pile_hay");
        assertUnchangedAtFourLayers(Holder.direct(actual.value()), resources);
        assertUnchangedAtFourLayers(keyed(Identifier.fromNamespaceAndPath("custom", "pile_hay"), actual.value()), resources);
        assertUnchangedAtFourLayers(keyed(Identifier.withDefaultNamespace("custom_pile"), actual.value()), resources);
    }

    @Test
    void exceptionRestoresScope() {
        var resources = new VanillaRepairResources();
        var instrumented = instrument("pile_hay");
        RuntimeException failure = new IllegalStateException("fixture placement failure");
        doAnswer(invocation -> {
            assertEquals(1, VillagePileRepairs.minimumClearance(instrumented.pile));
            throw failure;
        }).when(instrumented.placed).place(any(), any(ChunkGenerator.class), any(), any());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> place(instrumented, resources)));
        assertEquals(5, VillagePileRepairs.minimumClearance(instrumented.pile));
        assertUnchangedAtFourLayers(vanilla("pile_hay"), resourcesWithCustomFeature());
    }

    @Test
    void nestedExceptionRestoresOuterScopeThenClearsIt() {
        var resources = new VanillaRepairResources();
        var outer = instrument("pile_hay");
        var inner = instrument("pile_ice");
        doAnswer(invocation -> {
            assertEquals(1, VillagePileRepairs.minimumClearance(inner.pile));
            assertEquals(5, VillagePileRepairs.minimumClearance(outer.pile));
            throw new IllegalStateException("nested fixture failure");
        }).when(inner.placed).place(any(), any(ChunkGenerator.class), any(), any());
        doAnswer(invocation -> {
            assertEquals(1, VillagePileRepairs.minimumClearance(outer.pile));
            assertEquals(5, VillagePileRepairs.minimumClearance(inner.pile));
            assertThrows(IllegalStateException.class, () -> place(inner, resources));
            assertEquals(1, VillagePileRepairs.minimumClearance(outer.pile));
            assertEquals(5, VillagePileRepairs.minimumClearance(inner.pile));
            return true;
        }).when(outer.placed).place(any(), any(ChunkGenerator.class), any(), any());
        assertTrue(place(outer, resources));
        assertEquals(5, VillagePileRepairs.minimumClearance(outer.pile));
        assertEquals(5, VillagePileRepairs.minimumClearance(inner.pile));
    }

    @Test
    void concurrentRegionPlacementsCannotObserveEachOthersScope() throws Exception {
        var resources = new VanillaRepairResources();
        var first = instrument("pile_hay");
        var second = instrument("pile_ice");
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        for (var active : List.of(first, second)) {
            var other = active == first ? second : first;
            doAnswer(invocation -> {
                assertEquals(1, VillagePileRepairs.minimumClearance(active.pile));
                assertEquals(5, VillagePileRepairs.minimumClearance(other.pile));
                entered.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                assertEquals(1, VillagePileRepairs.minimumClearance(active.pile));
                return true;
            }).when(active.placed).place(any(), any(ChunkGenerator.class), any(), any());
        }
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstTask = executor.submit(() -> {
                assertTrue(place(first, resources));
                assertEquals(5, VillagePileRepairs.minimumClearance(first.pile));
            });
            var secondTask = executor.submit(() -> {
                assertTrue(place(second, resources));
                assertEquals(5, VillagePileRepairs.minimumClearance(second.pile));
            });
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS), "Both region threads must enter their placement scope");
                assertEquals(5, VillagePileRepairs.minimumClearance(first.pile));
                assertEquals(5, VillagePileRepairs.minimumClearance(second.pile));
            } finally {
                release.countDown();
            }
            firstTask.get(10, TimeUnit.SECONDS);
            secondTask.get(10, TimeUnit.SECONDS);
        }
    }

    private static VanillaRepairResources resourcesWithCustomFeature() {
        var resources = new VanillaRepairResources();
        resources.remove(Identifier.withDefaultNamespace("worldgen/feature/pile_hay.json"));
        return resources;
    }

    private static void assertUnchangedAtFourLayers(Holder<PlacedFeature> feature, VanillaRepairResources resources) {
        World world = new World(4);
        assertFalse(VillagePileRepairs.place(feature, resources.manager, world.level, world.generator,
                RandomSource.create(SEED), world.origin(), repair -> true));
        assertTrue(world.changed.isEmpty());
    }

    private static boolean place(Instrumented instrumented, VanillaRepairResources resources) {
        World world = new World(4);
        return VillagePileRepairs.place(instrumented.holder, resources.manager, world.level, world.generator,
                RandomSource.create(SEED), world.origin(), repair -> true);
    }

    private static Holder<PlacedFeature> vanilla(String name) {
        return RegistryHelper.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(
                ResourceKey.create(Registries.PLACED_FEATURE, Identifier.withDefaultNamespace(name)));
    }

    private static Instrumented instrument(String name) {
        var original = vanilla(name);
        PlacedFeature placed = mock(PlacedFeature.class);
        when(placed.feature()).thenReturn(original.value().feature());
        when(placed.placement()).thenReturn(List.of());
        return new Instrumented(keyed(Identifier.withDefaultNamespace(name), placed), placed,
                assertInstanceOf(BlockPileFeature.class, original.value().feature().value()));
    }

    @SuppressWarnings("unchecked")
    private static Holder<PlacedFeature> keyed(Identifier id, PlacedFeature placed) {
        Holder<PlacedFeature> holder = mock(Holder.Reference.class);
        when(holder.unwrapKey()).thenReturn(Optional.of(ResourceKey.create(Registries.PLACED_FEATURE, id)));
        when(holder.value()).thenReturn(placed);
        return holder;
    }

    private record Instrumented(Holder<PlacedFeature> holder, PlacedFeature placed, BlockPileFeature pile) {
    }

    /** An actual WorldGenLevel interface with solid support and bounds-checked writes. */
    private static final class World implements InvocationHandler {
        private final int layers;
        private final Map<BlockPos, BlockState> changed = new HashMap<>();
        private final ChunkGenerator generator = mock(ChunkGenerator.class);
        private final WorldGenLevel level = (WorldGenLevel) Proxy.newProxyInstance(
                VillagePileRepairsTest.class.getClassLoader(), new Class<?>[] {WorldGenLevel.class}, this);

        private World(int layers) {
            this.layers = layers;
            when(this.generator.getMinY()).thenReturn(MIN_Y);
            when(this.generator.getGenDepth()).thenReturn(384);
            when(this.generator.getSeaLevel()).thenReturn(63);
        }

        private BlockPos origin() {
            return new BlockPos(0, MIN_Y + this.layers, 0);
        }

        private BlockState state(BlockPos pos) {
            return this.changed.getOrDefault(pos,
                    pos.getY() < MIN_Y + this.layers ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getLevel": return null; // PlacementContext retains this nullable Level; piles do not access it.
                case "getMinY": return MIN_Y;
                case "getMaxY": return 319;
                case "getHeight": return 384;
                case "getBlockState": return state((BlockPos) args[0]);
                case "isEmptyBlock": return state((BlockPos) args[0]).isAir();
                case "getFluidState": return state((BlockPos) args[0]).getFluidState();
                case "setBlock": {
                    BlockPos pos = ((BlockPos) args[0]).immutable();
                    assertTrue(pos.getY() >= MIN_Y && pos.getY() <= 319, "Out of world write: " + pos);
                    this.changed.put(pos, (BlockState) args[1]);
                    return true;
                }
                case "getSeed": return SEED;
                case "isClientSide": return false;
                case "toString": return "VillagePileFixture";
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                default: {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    throw new UnsupportedOperationException(method.toString());
                }
            }
        }
    }
}
