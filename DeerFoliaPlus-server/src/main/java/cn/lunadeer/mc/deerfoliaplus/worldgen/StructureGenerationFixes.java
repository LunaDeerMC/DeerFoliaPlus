package cn.lunadeer.mc.deerfoliaplus.worldgen;

import cn.lunadeer.mc.deerfoliaplus.configurations.StructureGenerationFixesConfiguration;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Startup-scoped switches and conservative resource guards for vanilla structure repairs. */
public final class StructureGenerationFixes {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RESOURCE_BYTES = 4 * 1024 * 1024;
    private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;
    private static volatile Set<StructureRepair> active = Set.of();
    private static boolean initialized;

    private StructureGenerationFixes() {
    }

    /** Templates are cached by Minecraft, so changing generation rules requires a full restart. */
    public static synchronized void initialize(StructureGenerationFixesConfiguration configuration) {
        Set<StructureRepair> next = snapshot(configuration);
        if (initialized) {
            if (!active.equals(next)) {
                LOGGER.warn("Structure generation repair settings changed; restart the server to apply them. Keeping the startup settings.");
            }
            return;
        }
        if (!next.isEmpty()) {
            // A missing or malformed packaged catalog is a build error, not a reason to silently
            // start with only some repairs enabled.
            Catalog.DATA.templates.size();
        }
        active = next;
        initialized = true;
    }

    public static boolean enabled(StructureRepair repair) {
        return active.contains(repair);
    }

    /** Captures effective settings without keeping references to the mutable configuration. */
    public static Set<StructureRepair> snapshot(StructureGenerationFixesConfiguration config) {
        Objects.requireNonNull(config, "configuration");
        if (!config.enabled) {
            return Set.of();
        }
        EnumSet<StructureRepair> result = EnumSet.noneOf(StructureRepair.class);
        select(result, config.villages.snowyShepherdsHouse1, StructureRepair.SNOWY_SHEPHERDS_HOUSE);
        select(result, config.trailRuins.groupUpper1, StructureRepair.TRAIL_RUINS_GROUP_UPPER_1);
        select(result, config.trailRuins.groupUpper2, StructureRepair.TRAIL_RUINS_GROUP_UPPER_2);
        select(result, config.trailRuins.groupUpper3, StructureRepair.TRAIL_RUINS_GROUP_UPPER_3);
        select(result, config.trailRuins.groupUpper4, StructureRepair.TRAIL_RUINS_GROUP_UPPER_4);
        select(result, config.trailRuins.groupUpper5, StructureRepair.TRAIL_RUINS_GROUP_UPPER_5);
        select(result, config.trailRuins.roadEnd1, StructureRepair.TRAIL_RUINS_ROAD_END);
        select(result, config.trialChambers.corridorWall, StructureRepair.TRIAL_CORRIDOR_WALL);
        select(result, config.trialChambers.walkwayWithBridge1, StructureRepair.TRIAL_WALKWAY_BRIDGE);
        select(result, config.trialChambers.chamberDisposal8, StructureRepair.TRIAL_CHAMBER_8_DISPOSAL);
        select(result, config.bastions.pathwayEndWall0, StructureRepair.BASTION_PATHWAY_WALL);
        select(result, config.ancientCities.intactHorizontalWallBridge, StructureRepair.ANCIENT_CITY_WALL_BRIDGE);
        select(result, config.ancientCities.camp1, StructureRepair.ANCIENT_CITY_CAMP_1);
        select(result, config.ancientCities.camp2, StructureRepair.ANCIENT_CITY_CAMP_2);
        select(result, config.ancientCities.camp3, StructureRepair.ANCIENT_CITY_CAMP_3);
        select(result, config.abandonedCamps.campsitePaleGarden2, StructureRepair.PALE_GARDEN_CAMPSITE_2);
        var trees = config.abandonedCamps.trees;
        select(result, trees.campsiteOldGrowthPineTaiga1, StructureRepair.CAMP_OLD_GROWTH_PINE_TAIGA_1_TREE);
        select(result, trees.campsiteOldGrowthSpruceTaiga4, StructureRepair.CAMP_OLD_GROWTH_SPRUCE_TAIGA_4_TREE);
        select(result, trees.campsiteSnowyTaiga3, StructureRepair.CAMP_SNOWY_TAIGA_3_TREE);
        select(result, trees.tentCherryGrove1, StructureRepair.TENT_CHERRY_GROVE_1_TREE);
        select(result, trees.tentDappledForest1, StructureRepair.TENT_DAPPLED_FOREST_1_TREE);
        select(result, trees.tentDappledForest2, StructureRepair.TENT_DAPPLED_FOREST_2_TREE);
        select(result, trees.tentDappledForest3, StructureRepair.TENT_DAPPLED_FOREST_3_TREE);
        select(result, trees.tentDappledForest4, StructureRepair.TENT_DAPPLED_FOREST_4_TREE);
        select(result, trees.tentDappledForest6, StructureRepair.TENT_DAPPLED_FOREST_6_TREE);
        select(result, trees.tentSavanna1, StructureRepair.TENT_SAVANNA_1_TREE);
        select(result, trees.tentSnowyTaiga10, StructureRepair.TENT_SNOWY_TAIGA_10_TREE);
        select(result, trees.tentTaiga2, StructureRepair.TENT_TAIGA_2_TREE);
        select(result, trees.tentTaiga7, StructureRepair.TENT_TAIGA_7_TREE);
        select(result, trees.tentWindsweptForest6, StructureRepair.TENT_WINDSWEPT_FOREST_6_TREE);
        select(result, config.villages.hayPile, StructureRepair.VILLAGE_HAY_PILE);
        select(result, config.villages.pumpkinPile, StructureRepair.VILLAGE_PUMPKIN_PILE);
        select(result, config.villages.icePile, StructureRepair.VILLAGE_ICE_PILE);
        select(result, config.villages.melonPile, StructureRepair.VILLAGE_MELON_PILE);
        select(result, config.villages.snowPile, StructureRepair.VILLAGE_SNOW_PILE);
        select(result, config.villages.plainsSmallHouse8, StructureRepair.VILLAGE_PLAINS_SMALL_HOUSE_8);
        select(result, config.villages.plainsFisherCottage1, StructureRepair.VILLAGE_PLAINS_FISHER_COTTAGE_1);
        select(result, config.villages.desertTannery1, StructureRepair.VILLAGE_DESERT_TANNERY_1);
        select(result, config.villages.desertLargeFarm1, StructureRepair.VILLAGE_DESERT_LARGE_FARM_1);
        select(result, config.villages.savannaFisherCottage1, StructureRepair.VILLAGE_SAVANNA_FISHER_COTTAGE_1);
        select(result, config.villages.savannaShepherd1, StructureRepair.VILLAGE_SAVANNA_SHEPHERD_1);
        select(result, config.villages.savannaAnimalPen2, StructureRepair.VILLAGE_SAVANNA_ANIMAL_PEN_2);
        select(result, config.villages.savannaLargeFarm2, StructureRepair.VILLAGE_SAVANNA_LARGE_FARM_2);
        select(result, config.villages.savannaSmallFarm, StructureRepair.VILLAGE_SAVANNA_SMALL_FARM);
        return Set.copyOf(result);
    }

    private static void select(Set<StructureRepair> result, boolean enabled, StructureRepair repair) {
        if (enabled) {
            result.add(repair);
        }
    }

    public static InputStream openTemplate(Identifier id, Resource resource, ResourceManager resources) throws IOException {
        Set<StructureRepair> settings = active;
        if (settings.isEmpty()) {
            return resource.open();
        }
        return openTemplate(id, resource, resources, settings::contains);
    }

    static InputStream openTemplate(Identifier id, Resource resource, ResourceManager resources,
                                    Predicate<StructureRepair> enabled) throws IOException {
        StructureRepair repair = Catalog.DATA.templates.get(id);
        if (repair == null || !enabled.test(repair) || !isVanillaResource(resource)) {
            return resource.open();
        }
        byte[] original;
        try (InputStream input = resource.open()) {
            original = input.readNBytes(MAX_RESOURCE_BYTES + 1);
        }
        if (original.length > MAX_RESOURCE_BYTES) {
            return resource.open();
        }
        Baseline baseline = Catalog.DATA.resources.get(templateResource(id));
        if (baseline == null || !baseline.matches(original)) {
            return new ByteArrayInputStream(original);
        }
        // Original feature/placed-feature/provider resources are checked as well as the NBT.
        // A data pack can change tree semantics without replacing the structure itself.
        for (Identifier dependency : TreeStructureRepairs.dependencies(id)) {
            if (!matchesVanillaResource(resources, dependency)) {
                return new ByteArrayInputStream(original);
            }
        }
        try {
            CompoundTag input = NbtIo.readCompressed(new ByteArrayInputStream(original), NbtAccounter.create(MAX_NBT_BYTES));
            CompoundTag output = StructureTemplateRepairs.apply(id, input, enabled);
            output = TreeStructureRepairs.apply(id, output, enabled);
            if (output == input) {
                return new ByteArrayInputStream(original);
            }
            ByteArrayOutputStream result = new ByteArrayOutputStream(original.length);
            NbtIo.writeCompressed(output, result);
            return new ByteArrayInputStream(result.toByteArray());
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not apply structure generation repair to {}; using its original template", id, exception);
            return new ByteArrayInputStream(original);
        }
    }

    private static Identifier templateResource(Identifier id) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
    }

    /** A pack's display ID alone does not establish that it is the built-in Minecraft core. */
    public static boolean isVanillaResource(Resource resource) {
        return "vanilla".equals(resource.sourcePackId()) && resource.knownPackInfo()
                .filter(pack -> "minecraft".equals(pack.namespace()) && "core".equals(pack.id())).isPresent();
    }

    /** No cache: resource reloads must immediately invalidate changed data-pack dependencies. */
    public static boolean matchesVanillaResource(ResourceManager resources, Identifier id) {
        Baseline baseline = Catalog.DATA.resources.get(id);
        if (baseline == null) {
            return false;
        }
        Resource resource = resources.getResource(id).orElse(null);
        if (resource == null || !isVanillaResource(resource)) {
            return false;
        }
        try (InputStream input = resource.open()) {
            // NBT has an exact baseline size; JSON may be reformatted by the fork's resource build.
            int limit = baseline instanceof NbtBaseline nbt ? nbt.bytes : MAX_RESOURCE_BYTES;
            byte[] bytes = input.readNBytes(limit + 1);
            return bytes.length <= limit && baseline.matches(bytes);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private sealed interface Baseline permits NbtBaseline, JsonBaseline {
        boolean matches(byte[] input);
    }

    private record NbtBaseline(String sha256, int bytes) implements Baseline {
        @Override
        public boolean matches(byte[] input) {
            if (input.length != bytes) {
                return false;
            }
            try {
                return sha256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)));
            } catch (NoSuchAlgorithmException impossible) {
                throw new AssertionError("SHA-256 is required by the Java runtime", impossible);
            }
        }
    }

    private record JsonBaseline(JsonElement value) implements Baseline {
        @Override
        public boolean matches(byte[] input) {
            return value.equals(JsonParser.parseString(new String(input, StandardCharsets.UTF_8)));
        }
    }

    private record CatalogData(Map<Identifier, StructureRepair> templates, Map<Identifier, Baseline> resources) {
    }

    private static final class Catalog {
        private static final CatalogData DATA = load();

        private static CatalogData load() {
            String path = "/deerfoliaplus/structure-generation-fixes-baseline.json";
            try (InputStream input = StructureGenerationFixes.class.getResourceAsStream(path)) {
                if (input == null) {
                    throw new IOException("Missing " + path);
                }
                JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                Map<Identifier, StructureRepair> templates = new HashMap<>();
                root.getAsJsonObject("templates").entrySet().forEach(entry -> templates.put(
                        Identifier.parse(entry.getKey()), StructureRepair.valueOf(entry.getValue().getAsString())));
                Map<Identifier, Baseline> resources = new HashMap<>();
                root.getAsJsonObject("resources").entrySet().forEach(entry -> {
                    JsonObject value = entry.getValue().getAsJsonObject();
                    Baseline baseline = switch (value.get("kind").getAsString()) {
                        case "nbt" -> new NbtBaseline(value.get("sha256").getAsString(), value.get("bytes").getAsInt());
                        case "json" -> new JsonBaseline(value.get("value"));
                        default -> throw new IllegalArgumentException("Unknown structure baseline kind for " + entry.getKey());
                    };
                    resources.put(Identifier.parse(entry.getKey()), baseline);
                });
                for (Identifier id : templates.keySet()) {
                    if (!(resources.get(templateResource(id)) instanceof NbtBaseline)) {
                        throw new IllegalArgumentException("Missing NBT baseline for " + id);
                    }
                }
                return new CatalogData(Map.copyOf(templates), Map.copyOf(resources));
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("Could not load the structure generation repair baselines", exception);
            }
        }
    }
}
