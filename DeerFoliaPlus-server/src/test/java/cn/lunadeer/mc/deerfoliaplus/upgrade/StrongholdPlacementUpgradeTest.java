package cn.lunadeer.mc.deerfoliaplus.upgrade;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spigotmc.SpigotWorldConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Covers the stronghold patch's conflict with the 26.3 structure-seed changes. */
@VanillaFeature
@ResourceLock("DeerFoliaPlusConfiguration.bedrockStrongholdGeneration")
class StrongholdPlacementUpgradeTest {
    private static final long LEVEL_SEED = 719225L;
    private static final List<ChunkPos> SOURCE_CHUNKS = List.of(
        new ChunkPos(0, 0), new ChunkPos(917, -563), new ChunkPos(-2048, 3072)
    );

    private DeerFoliaPlusConfiguration.BedrockStrongholdGeneration previousSettings;
    private Registry<StructureSet> vanillaSets;
    private BiomeSource allBiomes;
    private RandomState randomState;
    private SpigotWorldConfig spigot;

    @BeforeEach
    void prepare() {
        this.previousSettings = DeerFoliaPlusConfiguration.bedrockStrongholdGeneration;
        var settings = new DeerFoliaPlusConfiguration.BedrockStrongholdGeneration();
        settings.spacing = 71;
        settings.separation = 19;
        DeerFoliaPlusConfiguration.bedrockStrongholdGeneration = settings;

        this.vanillaSets = RegistryHelper.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        Set<Holder<Biome>> biomes = RegistryHelper.registryAccess().lookupOrThrow(Registries.BIOME)
            .listElements().collect(Collectors.toSet());
        // Only biome availability is needed; the public factories must see every real structure set.
        this.allBiomes = mock(BiomeSource.class);
        when(this.allBiomes.possibleBiomes()).thenReturn(biomes);
        this.randomState = mock(RandomState.class);
        // Avoid initializing or changing the process-wide Spigot YAML configuration.
        this.spigot = mock(SpigotWorldConfig.class);
        this.spigot.villageSeed = 117071;
        this.spigot.trialChambersSeed = 219071;
        this.spigot.abandonedCampSeed = 319071;
    }

    @AfterEach
    void restoreSettings() {
        DeerFoliaPlusConfiguration.bedrockStrongholdGeneration = this.previousSettings;
    }

    @ParameterizedTest(name = "only vanilla strongholds change, flat={0}")
    @ValueSource(booleans = {false, true})
    void onlyVanillaStrongholdsChangeWhenEnabled(boolean flat) {
        var disabled = this.create(this.vanillaSets, false, flat);
        var enabled = this.create(this.vanillaSets, true, flat);
        var originals = this.vanillaSets.listElements().toList();
        assertEquals(originals.size(), disabled.possibleStructureSets().size());
        assertEquals(originals.size(), enabled.possibleStructureSets().size());

        for (int index = 0; index < originals.size(); index++) {
            var original = originals.get(index);
            var before = disabled.possibleStructureSets().get(index);
            var after = enabled.possibleStructureSets().get(index);
            String name = original.key().identifier().toString();
            assertEquals(original.value().structures(), before.value().structures(), name);
            assertEquals(original.value().structures(), after.value().structures(), name);

            if (original.is(BuiltinStructureSets.STRONGHOLDS)) {
                assertSame(original, before, "Disabled mode must keep the keyed ring holder");
                assertInstanceOf(ConcentricRingsStructurePlacement.class, before.value().placement());
                var spread = assertInstanceOf(RandomSpreadStructurePlacement.class, after.value().placement());
                assertEquals(71, spread.spacing());
                assertEquals(19, spread.separation());
                assertEquals(RandomSpreadType.LINEAR, spread.spreadType());
                assertEquals(10387319, spread.salt);
            } else {
                assertEquals(placementData(before.value().placement()), placementData(after.value().placement()), name);
                if (before.value().placement() instanceof RandomSpreadStructurePlacement oldSpread) {
                    var newSpread = assertInstanceOf(RandomSpreadStructurePlacement.class, after.value().placement());
                    assertSameCandidateChunks(oldSpread, newSpread, name);
                }
            }
        }
    }

    @ParameterizedTest(name = "Spigot seeds survive stronghold option, flat={0}")
    @ValueSource(booleans = {false, true})
    void retainsConfiguredSeedsIncludingNewAbandonedCamp(boolean flat) {
        // Representative old, recent, and newly added 26.3 mappings, rather than duplicating the switch.
        var cases = List.of(
            new SeedCase(BuiltinStructureSets.VILLAGES, this.spigot.villageSeed),
            new SeedCase(BuiltinStructureSets.TRIAL_CHAMBERS, this.spigot.trialChambersSeed),
            new SeedCase(BuiltinStructureSets.ABANDONED_CAMP, this.spigot.abandonedCampSeed)
        );
        var originals = this.vanillaSets.listElements().toList();
        for (boolean enabled : List.of(false, true)) {
            var state = this.create(this.vanillaSets, enabled, flat);
            for (var testCase : cases) {
                var original = this.vanillaSets.getOrThrow(testCase.key());
                int index = originals.indexOf(original);
                var originalSpread = assertInstanceOf(RandomSpreadStructurePlacement.class, original.value().placement());
                var actual = assertInstanceOf(ChunkGeneratorStructureState.KeyedRandomSpreadStructurePlacement.class,
                    state.possibleStructureSets().get(index).value().placement());
                assertNotEquals(testCase.seed(), originalSpread.salt, "Fixture must exercise a non-default seed");
                assertEquals(testCase.seed(), actual.salt, testCase.key().toString());
                assertEquals(testCase.key(), actual.key, "Paper must retain the structure-set key");
                var expected = new RandomSpreadStructurePlacement(
                    originalSpread.locateOffset, originalSpread.frequencyReductionMethod, originalSpread.frequency,
                    testCase.seed(), originalSpread.exclusionZone,
                    originalSpread.spacing(), originalSpread.separation(), originalSpread.spreadType()
                );
                assertEquals(placementData(expected), placementData(actual), testCase.key().toString());
                assertSameCandidateChunks(expected, actual, testCase.key().toString());
            }
        }
    }

    @ParameterizedTest(name = "custom namespace preserves same-name structures, flat={0}")
    @ValueSource(booleans = {false, true})
    void customNamespaceDoesNotInheritVanillaOverrides(boolean flat) {
        var customSets = new MappedRegistry<StructureSet>(Registries.STRUCTURE_SET, Lifecycle.stable());
        for (var originalKey : List.of(BuiltinStructureSets.STRONGHOLDS, BuiltinStructureSets.VILLAGES, BuiltinStructureSets.ABANDONED_CAMP)) {
            var original = this.vanillaSets.getOrThrow(originalKey).value();
            var key = ResourceKey.create(Registries.STRUCTURE_SET,
                Identifier.fromNamespaceAndPath("upgrade_test", originalKey.identifier().getPath()));
            customSets.register(key, new StructureSet(original.structures(), original.placement()), RegistrationInfo.BUILT_IN);
        }
        customSets.freeze();
        var originals = customSets.listElements().toList();
        for (boolean enabled : List.of(false, true)) {
            var actual = this.create(customSets, enabled, flat).possibleStructureSets();
            assertEquals(originals.size(), actual.size());
            for (int index = 0; index < originals.size(); index++) {
                assertSame(originals.get(index), actual.get(index), "Custom namespaces must keep their keyed holder and placement");
            }
        }
    }

    private ChunkGeneratorStructureState create(Registry<StructureSet> registry, boolean enabled, boolean flat) {
        DeerFoliaPlusConfiguration.bedrockStrongholdGeneration.enabled = enabled;
        if (flat) {
            return ChunkGeneratorStructureState.createForFlat(this.randomState, LEVEL_SEED, new ChunkPos(0, 0),
                this.allBiomes, registry.listElements().map(holder -> (Holder<StructureSet>) holder), this.spigot);
        }
        return ChunkGeneratorStructureState.createForNormal(this.randomState, LEVEL_SEED, new ChunkPos(0, 0),
            this.allBiomes, registry, this.spigot);
    }

    private static JsonElement placementData(StructurePlacement placement) {
        return StructurePlacement.CODEC.encodeStart(
            RegistryOps.create(JsonOps.INSTANCE, RegistryHelper.registryAccess()), placement
        ).getOrThrow();
    }

    private static void assertSameCandidateChunks(RandomSpreadStructurePlacement expected, RandomSpreadStructurePlacement actual, String name) {
        for (var source : SOURCE_CHUNKS) {
            assertEquals(expected.getPotentialStructureChunk(LEVEL_SEED, source.x(), source.z()),
                actual.getPotentialStructureChunk(LEVEL_SEED, source.x(), source.z()), name + " at " + source);
        }
    }

    private record SeedCase(ResourceKey<StructureSet> key, int seed) {
    }
}
