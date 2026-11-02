package cn.lunadeer.mc.deerfoliaplus.worldgen;

import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationPart;
import cn.lunadeer.mc.deerfoliaplus.configurations.StructureGenerationFixesConfiguration;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Real packaged vanilla data plus independent resource-origin, reload and switch guards. */
class StructureGenerationFixesTest {
    private static ResourceManager vanilla;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FallbackResourceManager resources = new FallbackResourceManager(PackType.SERVER_DATA, "minecraft");
        resources.push(ServerPacksSource.createVanillaPackSource().fullResources());
        vanilla = resources;
    }

    @Test
    void capturesEveryIndependentSwitchAndDoesNotRetainMutableConfiguration() throws Exception {
        var config = new StructureGenerationFixesConfiguration();
        Set<StructureRepair> all = StructureGenerationFixes.snapshot(config);
        assertEquals(EnumSet.allOf(StructureRepair.class), all);
        assertThrows(UnsupportedOperationException.class, () -> all.clear());

        Set<StructureRepair> affected = new HashSet<>();
        toggleEach(config, config, affected);
        assertEquals(all, affected, "Each repair must have exactly one independent configuration switch");

        config.trialChambers.corridorWall = false;
        assertFalse(StructureGenerationFixes.snapshot(config).contains(StructureRepair.TRIAL_CORRIDOR_WALL));
        config.abandonedCamps.trees.tentDappledForest6 = false;
        assertFalse(StructureGenerationFixes.snapshot(config).contains(StructureRepair.TENT_DAPPLED_FOREST_6_TREE));
        config.villages.savannaAnimalPen2 = false;
        assertFalse(StructureGenerationFixes.snapshot(config).contains(StructureRepair.VILLAGE_SAVANNA_ANIMAL_PEN_2));
        config.ancientCities.camp2 = false;
        assertFalse(StructureGenerationFixes.snapshot(config).contains(StructureRepair.ANCIENT_CITY_CAMP_2));
        assertEquals(EnumSet.allOf(StructureRepair.class), all, "Existing snapshot must not track later edits");
        config.enabled = false;
        assertTrue(StructureGenerationFixes.snapshot(config).isEmpty());
    }

    private static void toggleEach(StructureGenerationFixesConfiguration root, ConfigurationPart node,
                                   Set<StructureRepair> affected) throws IllegalAccessException {
        for (Field field : node.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.getType() == boolean.class && !(node == root && field.getName().equals("enabled"))) {
                field.setBoolean(node, false);
                Set<StructureRepair> missing = EnumSet.allOf(StructureRepair.class);
                missing.removeAll(StructureGenerationFixes.snapshot(root));
                assertEquals(1, missing.size(), field.toString());
                assertTrue(affected.add(missing.iterator().next()), "Duplicate configuration mapping: " + field);
                field.setBoolean(node, true);
            } else if (ConfigurationPart.class.isAssignableFrom(field.getType())) {
                toggleEach(root, (ConfigurationPart) field.get(node), affected);
            }
        }
    }

    @Test
    void catalogMatchesAllRealVanillaResourcesIncludingTreeDependenciesAndVillageStreets() throws Exception {
        JsonObject catalog = catalog();
        assertEquals(27, catalog.getAsJsonObject("templates").size());
        for (String path : catalog.getAsJsonObject("resources").keySet()) {
            assertTrue(StructureGenerationFixes.matchesVanillaResource(vanilla, Identifier.parse(path)), path);
        }
        for (Identifier tree : TreeStructureRepairs.templates()) {
            assertFalse(TreeStructureRepairs.dependencies(tree).isEmpty(), tree.toString());
            for (Identifier dependency : TreeStructureRepairs.dependencies(tree)) {
                assertTrue(StructureGenerationFixes.matchesVanillaResource(vanilla, dependency), dependency.toString());
            }
        }
    }

    @Test
    void everyTemplateChangesOnlyWhenItsOwnSwitchIsEnabled() throws Exception {
        for (var entry : catalog().getAsJsonObject("templates").entrySet()) {
            Identifier id = Identifier.parse(entry.getKey());
            StructureRepair repair = StructureRepair.valueOf(entry.getValue().getAsString());
            Resource source = vanilla.getResourceOrThrow(template(id));
            byte[] original = bytes(source);
            byte[] saved = original.clone();
            byte[] disabled = opened(id, source, vanilla, ignored -> false);
            assertArrayEquals(original, disabled, "Master off must preserve compressed bytes: " + id);
            assertArrayEquals(original, opened(id, source, vanilla, value -> value != repair), "Individual off: " + id);
            byte[] fixed = opened(id, source, vanilla, value -> value == repair);
            assertFalse(Arrays.equals(original, fixed), "Repair must run: " + id);
            assertNotEquals(NbtIo.readCompressed(new ByteArrayInputStream(original), NbtAccounter.unlimitedHeap()),
                    NbtIo.readCompressed(new ByteArrayInputStream(fixed), NbtAccounter.unlimitedHeap()), id.toString());
            assertArrayEquals(saved, original);
            assertArrayEquals(saved, bytes(source), "The packaged resource must not change: " + id);
        }
    }

    @Test
    void disabledUnrelatedAndExternalResourcesKeepTheirOriginalStream() throws Exception {
        Identifier id = Identifier.withDefaultNamespace("trial_chambers/corridor/addon/wall");
        byte[] original = bytes(vanilla.getResourceOrThrow(template(id)));
        InputStream unchanged = new ByteArrayInputStream(original);
        Resource resource = new Resource(pack("vanilla", Optional.of(new KnownPack("minecraft", "core", "26.3"))), () -> unchanged);
        ResourceManager neverRead = manager(Map.of());
        assertSame(unchanged, StructureGenerationFixes.openTemplate(id, resource, neverRead, ignored -> false));
        assertSame(unchanged, StructureGenerationFixes.openTemplate(Identifier.withDefaultNamespace("unrelated"), resource,
                neverRead, ignored -> true));
        Resource external = new Resource(pack("file/custom.zip", Optional.empty()), () -> unchanged);
        assertSame(unchanged, StructureGenerationFixes.openTemplate(id, external, neverRead, ignored -> true));
        unchanged.close();
    }

    @Test
    void trustedPackRequiresBothBuiltInIdAndCoreKnownPack() {
        byte[] bytes = new byte[0];
        assertTrue(StructureGenerationFixes.isVanillaResource(resource(bytes, "vanilla", Optional.of(new KnownPack("minecraft", "core", "26.3")))));
        assertFalse(StructureGenerationFixes.isVanillaResource(resource(bytes, "vanilla", Optional.empty())));
        assertFalse(StructureGenerationFixes.isVanillaResource(resource(bytes, "file/custom.zip", Optional.of(new KnownPack("minecraft", "core", "26.3")))));
        assertFalse(StructureGenerationFixes.isVanillaResource(resource(bytes, "vanilla", Optional.of(new KnownPack("other", "core", "26.3")))));
        assertFalse(StructureGenerationFixes.isVanillaResource(resource(bytes, "vanilla", Optional.of(new KnownPack("minecraft", "extra", "26.3")))));
    }

    @Test
    void changedTemplateBytesArePreservedWithoutAttemptingToParseOrRepair() throws Exception {
        Identifier id = Identifier.withDefaultNamespace("trial_chambers/corridor/addon/wall");
        byte[] original = bytes(vanilla.getResourceOrThrow(template(id)));
        original[original.length - 1] ^= 1;
        Resource modified = resource(original);
        assertArrayEquals(original, opened(id, modified, manager(Map.of()), ignored -> true));
        assertFalse(StructureGenerationFixes.matchesVanillaResource(manager(Map.of(template(id), modified)), template(id)));
        byte[] oversized = new byte[4 * 1024 * 1024 + 7];
        assertArrayEquals(oversized, opened(id, resource(oversized), manager(Map.of()), ignored -> true));
    }

    @Test
    void jsonUsesSemanticEqualityAndReflectsDependencyChangesWithoutCaching() throws Exception {
        Identifier id = Identifier.withDefaultNamespace("worldgen/template_pool/ancient_city/structures.json");
        JsonObject json = JsonParser.parseString(new String(bytes(vanilla.getResourceOrThrow(id)), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject reordered = new JsonObject();
        json.entrySet().stream().sorted(Map.Entry.<String, com.google.gson.JsonElement>comparingByKey().reversed())
                .forEach(entry -> reordered.add(entry.getKey(), entry.getValue()));
        byte[] reformatted = ("\n\t" + reordered + "\n").getBytes(StandardCharsets.UTF_8);
        Map<Identifier, Resource> current = new HashMap<>();
        current.put(id, resource(reformatted));
        ResourceManager resources = manager(current);
        assertTrue(StructureGenerationFixes.matchesVanillaResource(resources, id));
        reordered.addProperty("fallback", "minecraft:changed_by_data_pack");
        current.put(id, resource(reordered.toString().getBytes(StandardCharsets.UTF_8)));
        assertFalse(StructureGenerationFixes.matchesVanillaResource(resources, id));
        current.put(id, resource(reformatted, "file/overrides.zip", Optional.empty()));
        assertFalse(StructureGenerationFixes.matchesVanillaResource(resources, id), "Matching bytes alone must not override an external pack");
        current.put(id, resource("not JSON".getBytes(StandardCharsets.UTF_8)));
        assertFalse(StructureGenerationFixes.matchesVanillaResource(resources, id));
        current.remove(id);
        assertFalse(StructureGenerationFixes.matchesVanillaResource(resources, id));
        current.put(id, resource(reformatted));
        assertTrue(StructureGenerationFixes.matchesVanillaResource(resources, id), "A restored resource must not retain a stale rejection");
        assertFalse(StructureGenerationFixes.matchesVanillaResource(resources, Identifier.withDefaultNamespace("unlisted.json")));
    }

    @Test
    void treeRepairsRespectEveryOriginalDependencyAndItsPackOrigin() throws Exception {
        for (Identifier id : TreeStructureRepairs.templates()) {
            Resource template = vanilla.getResourceOrThrow(template(id));
            byte[] original = bytes(template);
            for (Identifier dependency : TreeStructureRepairs.dependencies(id)) {
                byte[] dependencyBytes = bytes(vanilla.getResourceOrThrow(dependency));
                for (Resource replacement : new Resource[]{
                        resource("{}".getBytes(StandardCharsets.UTF_8)),
                        resource(dependencyBytes, "file/custom.zip", Optional.empty())}) {
                    ResourceManager overridden = overlay(Map.of(dependency, replacement));
                    assertArrayEquals(original, opened(id, template, overridden, ignored -> true), id + " depends on " + dependency);
                }
            }
        }
    }

    @Test
    void closesOriginalStreamWhenProducingARepairedResource() throws Exception {
        Identifier id = Identifier.withDefaultNamespace("trail_ruins/roads/road_end_1");
        byte[] original = bytes(vanilla.getResourceOrThrow(template(id)));
        AtomicBoolean closed = new AtomicBoolean();
        Resource tracked = new Resource(pack("vanilla", Optional.of(new KnownPack("minecraft", "core", "26.3"))), () -> new ByteArrayInputStream(original) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        });
        byte[] repaired = opened(id, tracked, vanilla, ignored -> true);
        assertTrue(closed.get());
        assertFalse(Arrays.equals(original, repaired));
    }

    private static JsonObject catalog() throws IOException {
        try (InputStream input = StructureGenerationFixesTest.class.getResourceAsStream("/deerfoliaplus/structure-generation-fixes-baseline.json")) {
            assertNotNull(input);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Identifier template(Identifier id) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
    }

    private static byte[] opened(Identifier id, Resource resource, ResourceManager manager,
                                 java.util.function.Predicate<StructureRepair> enabled) throws IOException {
        try (InputStream input = StructureGenerationFixes.openTemplate(id, resource, manager, enabled)) {
            return input.readAllBytes();
        }
    }

    private static byte[] bytes(Resource resource) throws IOException {
        try (InputStream input = resource.open()) {
            return input.readAllBytes();
        }
    }

    private static Resource resource(byte[] bytes) {
        return resource(bytes, "vanilla", Optional.of(new KnownPack("minecraft", "core", "26.3")));
    }

    private static Resource resource(byte[] bytes, String packId, Optional<KnownPack> known) {
        return new Resource(pack(packId, known), () -> new ByteArrayInputStream(bytes));
    }

    private static PackResources pack(String id, Optional<KnownPack> known) {
        return (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(), new Class<?>[]{PackResources.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "packId" -> id;
                    case "knownPackInfo" -> known;
                    case "toString" -> id;
                    default -> throw new AssertionError("Unexpected pack method " + method);
                });
    }

    private static ResourceManager manager(Map<Identifier, Resource> resources) {
        return resourceManager(id -> Optional.ofNullable(resources.get(id)));
    }

    private static ResourceManager overlay(Map<Identifier, Resource> resources) {
        return resourceManager(id -> resources.containsKey(id) ? Optional.of(resources.get(id)) : vanilla.getResource(id));
    }

    private static ResourceManager resourceManager(java.util.function.Function<Identifier, Optional<Resource>> lookup) {
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getResource")) {
                        return lookup.apply((Identifier) args[0]);
                    }
                    throw new AssertionError("Unexpected resource-manager method " + method);
                });
    }
}
