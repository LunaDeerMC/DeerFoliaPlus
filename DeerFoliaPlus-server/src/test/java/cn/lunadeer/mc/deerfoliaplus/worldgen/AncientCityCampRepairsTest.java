package cn.lunadeer.mc.deerfoliaplus.worldgen;

import com.google.gson.JsonElement;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.bukkit.support.RegistryHelper;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@VanillaFeature
class AncientCityCampRepairsTest {
    private static final ResourceKey<StructureTemplatePool> POOL = ResourceKey.create(
            Registries.TEMPLATE_POOL, Identifier.withDefaultNamespace("ancient_city/structures"));
    private static final Identifier POOL_RESOURCE = Identifier.withDefaultNamespace(
            "worldgen/template_pool/ancient_city/structures.json");
    private static final List<StructureRepair> REPAIRS = List.of(StructureRepair.ANCIENT_CITY_CAMP_1,
            StructureRepair.ANCIENT_CITY_CAMP_2, StructureRepair.ANCIENT_CITY_CAMP_3);

    @TempDir
    Path generated;
    private VanillaRepairResources resources;
    private StructureTemplateManager templates;
    private StructureTemplatePool pool;
    private ListPoolElement camps;

    @BeforeEach
    void prepare() {
        this.resources = new VanillaRepairResources();
        var storage = mock(LevelStorageSource.LevelStorageAccess.class);
        when(storage.getLevelPath(LevelResource.GENERATED_DIR)).thenReturn(this.generated);
        this.templates = new StructureTemplateManager(this.resources.manager, storage, mock(DataFixer.class),
                RegistryHelper.registryAccess().lookupOrThrow(Registries.BLOCK));
        this.pool = RegistryHelper.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getOrThrow(POOL).value();
        this.camps = this.pool.getTemplates().stream().map(pair -> pair.getFirst())
                .filter(ListPoolElement.class::isInstance).map(ListPoolElement.class::cast)
                .filter(element -> element.getElements().size() == 3)
                .filter(element -> element.getElements().getFirst() instanceof SinglePoolElement single
                        && single.getTemplateLocation().equals(campId(1)))
                .findFirst().orElseThrow();
    }

    @Test
    void disabledKeepsOriginalWeightAndDoesNotConsumeRandomness() {
        RandomSource random = mock(RandomSource.class);
        assertSame(this.camps, AncientCityCampRepairs.select(this.camps, POOL, this.templates, random, repair -> false));
        verifyNoInteractions(random);
        assertEquals(1, this.pool.getTemplates().stream().filter(pair -> pair.getFirst() == this.camps)
                .mapToInt(pair -> pair.getSecond()).sum());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void selectsOnlyAfterOriginalCandidateAndPreservesPoolWeights(int variant) {
        JsonElement before = poolData();
        int poolSize = this.pool.size();
        RandomSource random = mock(RandomSource.class);
        when(random.nextInt(3)).thenReturn(variant);
        StructurePoolElement selected = AncientCityCampRepairs.select(this.camps, POOL, this.templates, random, repair -> true);
        assertSame(this.camps.getElements().get(variant), selected);
        assertEquals(campId(variant + 1), assertInstanceOf(SinglePoolElement.class, selected).getTemplateLocation());
        verify(random).nextInt(3);
        verifyNoMoreInteractions(random);
        assertEquals(before, poolData(), "Selection must not rewrite the registry or its weight-one list");
        assertEquals(poolSize, this.pool.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void disabledSelectedVariantKeepsOriginalComposite(int variant) {
        RandomSource random = mock(RandomSource.class);
        when(random.nextInt(3)).thenReturn(variant);
        assertSame(this.camps, AncientCityCampRepairs.select(this.camps, POOL, this.templates, random,
                repair -> repair != REPAIRS.get(variant)));
        verify(random).nextInt(3);
        verifyNoMoreInteractions(random);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void selectedSingleElementSurvivesSerialization(int variant) {
        RandomSource random = mock(RandomSource.class);
        when(random.nextInt(3)).thenReturn(variant);
        var selected = AncientCityCampRepairs.select(this.camps, POOL, this.templates, random, repair -> true);
        var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryHelper.registryAccess());
        JsonElement saved = StructurePoolElement.CODEC.encodeStart(ops, selected).getOrThrow();
        StructurePoolElement restored = StructurePoolElement.CODEC.parse(ops, saved).getOrThrow();
        assertEquals(campId(variant + 1), assertInstanceOf(SinglePoolElement.class, restored).getTemplateLocation());
        assertEquals(saved, StructurePoolElement.CODEC.encodeStart(ops, restored).getOrThrow());
    }

    @Test
    void unrelatedPoolsAndIndividualCandidatesAreUntouched() {
        RandomSource random = mock(RandomSource.class);
        var custom = ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.fromNamespaceAndPath("custom", "ancient_city/structures"));
        assertSame(this.camps, AncientCityCampRepairs.select(this.camps, custom, this.templates, random, repair -> true));
        var single = this.camps.getElements().getFirst();
        assertSame(single, AncientCityCampRepairs.select(single, POOL, this.templates, random, repair -> true));
        verifyNoInteractions(random);
    }

    @Test
    void customPoolResourceIsUntouchedEvenIfBytesAreIdentical() throws Exception {
        this.resources.replace(POOL_RESOURCE, VanillaRepairResources.bytes(POOL_RESOURCE), false);
        assertUnchangedWithoutRandomDraw();
    }

    @Test
    void modifiedPoolCannotMasqueradeAsVanilla() throws Exception {
        this.resources.replace(POOL_RESOURCE, "{}".getBytes(StandardCharsets.UTF_8), true);
        assertUnchangedWithoutRandomDraw();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void overriddenCampTemplatePreventsSelection(int variant) throws Exception {
        Identifier id = templateResource(variant);
        this.resources.replace(id, VanillaRepairResources.bytes(id), false);
        assertUnchangedWithoutRandomDraw();
    }

    @Test
    void modifiedTemplateCannotMasqueradeAsVanilla() throws Exception {
        this.resources.replace(templateResource(2), new byte[] {1, 2, 3}, true);
        assertUnchangedWithoutRandomDraw();
    }

    @Test
    void missingTemplatePreservesOriginalCandidate() {
        this.resources.remove(templateResource(2));
        assertUnchangedWithoutRandomDraw();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void worldSavedTemplateTakesPriority(int variant) throws Exception {
        Path path = this.templates.worldTemplates().createAndValidatePathToStructure(campId(variant),
                StructureTemplateManager.WORLD_STRUCTURE_LISTER);
        Files.createDirectories(path.getParent());
        Files.write(path, VanillaRepairResources.bytes(templateResource(variant)));
        assertUnchangedWithoutRandomDraw();
    }

    private void assertUnchangedWithoutRandomDraw() {
        RandomSource random = mock(RandomSource.class);
        assertSame(this.camps, AncientCityCampRepairs.select(this.camps, POOL, this.templates, random, repair -> true));
        verifyNoInteractions(random);
    }

    private JsonElement poolData() {
        return StructureTemplatePool.DIRECT_CODEC.encodeStart(
                RegistryOps.create(JsonOps.INSTANCE, RegistryHelper.registryAccess()), this.pool).getOrThrow();
    }

    private static Identifier campId(int variant) {
        return Identifier.withDefaultNamespace("ancient_city/structures/camp_" + variant);
    }

    private static Identifier templateResource(int variant) {
        return Identifier.withDefaultNamespace("structure/ancient_city/structures/camp_" + variant + ".nbt");
    }
}
