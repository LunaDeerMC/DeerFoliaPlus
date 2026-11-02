package cn.lunadeer.mc.deerfoliaplus.configurations;

import cn.lunadeer.mc.deerfolia.utils.configuration.Comments;
import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationPart;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/** Configuration for the vanilla structure generation repairs. Applied once at startup. */
public class StructureGenerationFixesConfiguration extends ConfigurationPart {

    /**
     * Validate before the legacy loader can replace malformed input with defaults and save it.
     * Other feature sections retain their existing type handling.
     */
    public static void validate(File file) throws IOException, InvalidConfigurationException {
        if (!file.exists()) {
            return;
        }

        // Unlike loadConfiguration(File), this overload propagates YAML syntax errors.
        new YamlConfiguration().load(file);

        // Inspect the raw safe mapping as well: Bukkit drops explicit null values, which must
        // not silently turn a present, invalid switch into the default value true.
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Object document = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
            if (!(document instanceof Map<?, ?> root) || !root.containsKey("structure-generation-fixes")) {
                return;
            }
            Object section = root.get("structure-generation-fixes");
            if (!(section instanceof Map<?, ?> values)) {
                throw new InvalidConfigurationException("structure-generation-fixes must be a configuration section");
            }
            validateSection(values, StructureGenerationFixesConfiguration.class, "structure-generation-fixes");
        } catch (YAMLException exception) {
            throw new InvalidConfigurationException(exception);
        }
    }

    private static void validateSection(Map<?, ?> values, Class<? extends ConfigurationPart> type, String prefix)
            throws InvalidConfigurationException {
        for (Field field : type.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String key = field.getName().replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase(java.util.Locale.ROOT);
            if (!values.containsKey(key)) {
                continue;
            }
            String path = prefix + "." + key;
            Object value = values.get(key);
            if (field.getType() == boolean.class) {
                if (!(value instanceof Boolean)) {
                    throw new InvalidConfigurationException(path + " must be a boolean (true or false)");
                }
            } else if (ConfigurationPart.class.isAssignableFrom(field.getType())) {
                if (!(value instanceof Map<?, ?> child)) {
                    throw new InvalidConfigurationException(path + " must be a configuration section");
                }
                validateSection(child, field.getType().asSubclass(ConfigurationPart.class), path);
            }
        }
    }

    @Comments({
            "Enable vanilla structure generation repairs (default: true).",
            "Every individual option also requires this master switch. Restart after changing these settings.",
            "False preserves vanilla generation; existing chunks and structures are not rewritten."
    })
    public boolean enabled = true;

    @Comments("Village buildings and shallow-world decoration placement")
    public Villages villages = new Villages();

    @Comments("Trail ruins building and road connections")
    public TrailRuins trailRuins = new TrailRuins();

    @Comments("Trial chamber attachments and upper-floor disposal")
    public TrialChambers trialChambers = new TrialChambers();

    @Comments("Bastion remnant connections")
    public Bastions bastions = new Bastions();

    @Comments("Ancient city wall connections and independent camp variants")
    public AncientCities ancientCities = new AncientCities();

    @Comments("Abandoned camp connections and trees obstructed by their own templates")
    public AbandonedCamps abandonedCamps = new AbandonedCamps();

    public static class Villages extends ConfigurationPart {
        @Comments("MC-170550: repair the snowy_shepherds_house_1 entrance in normal and zombie villages")
        public boolean snowyShepherdsHouse1 = true;

        @Comments("MC-309529: repair plains_small_house_8 placement below an internal terrain-matching road connector")
        public boolean plainsSmallHouse8 = true;

        @Comments("MC-309529: repair plains_fisher_cottage_1 placement below an internal terrain-matching road connector")
        public boolean plainsFisherCottage1 = true;

        @Comments("MC-310673: repair desert_tannery_1 placement below an internal terrain-matching road connector")
        public boolean desertTannery1 = true;

        @Comments("MC-310673: repair desert_large_farm_1 placement below an internal terrain-matching road connector")
        public boolean desertLargeFarm1 = true;

        @Comments("MC-306596: repair savanna_fisher_cottage_1 placement below an internal terrain-matching road connector")
        public boolean savannaFisherCottage1 = true;

        @Comments("MC-306596: repair savanna_shepherd_1 placement below an internal terrain-matching road connector")
        public boolean savannaShepherd1 = true;

        @Comments("MC-306596: repair savanna_animal_pen_2 placement below an internal terrain-matching road connector")
        public boolean savannaAnimalPen2 = true;

        @Comments("MC-306596: repair savanna_large_farm_2 placement below an internal terrain-matching road connector")
        public boolean savannaLargeFarm2 = true;

        @Comments("MC-306596: repair savanna_small_farm placement below an internal terrain-matching road connector")
        public boolean savannaSmallFarm = true;

        @Comments("MC-309531: allow village hay piles to place at the bottom of shallow worlds")
        public boolean hayPile = true;

        @Comments("MC-309531: allow village pumpkin piles to place at the bottom of shallow worlds")
        public boolean pumpkinPile = true;

        @Comments("MC-309531: allow village ice piles to place at the bottom of shallow worlds")
        public boolean icePile = true;

        @Comments("Allow vanilla village melon piles affected by the same shallow-world height check")
        public boolean melonPile = true;

        @Comments("Allow vanilla village snow piles affected by the same shallow-world height check")
        public boolean snowPile = true;
    }

    public static class TrailRuins extends ConfigurationPart {
        @Comments("MC-264093: restore the group_upper_1 building connection")
        public boolean groupUpper1 = true;

        @Comments("MC-264093: restore the group_upper_2 building connection")
        public boolean groupUpper2 = true;

        @Comments("MC-264093: restore the group_upper_3 building connection")
        public boolean groupUpper3 = true;

        @Comments("MC-264093: restore the group_upper_4 building connection")
        public boolean groupUpper4 = true;

        @Comments("MC-264093: restore the group_upper_5 building connection")
        public boolean groupUpper5 = true;

        @Comments("Restore the road_end_1 connection to the unoccupied end of the road")
        public boolean roadEnd1 = true;
    }

    public static class TrialChambers extends ConfigurationPart {
        @Comments("MC-266778: restore the corridor/addon/wall attachment")
        public boolean corridorWall = true;

        @Comments("MC-266778: restore the chamber/addon/walkway_with_bridge_1 attachment")
        public boolean walkwayWithBridge1 = true;

        @Comments("MC-312911: restore the hopper and barrel on the upper floor of chamber_8")
        public boolean chamberDisposal8 = true;
    }

    public static class Bastions extends ConfigurationPart {
        @Comments("MC-312914: restore the terminal wall unit of units/pathways/pathway_wall_0")
        public boolean pathwayEndWall0 = true;
    }

    public static class AncientCities extends ConfigurationPart {
        @Comments("MC-262023: restore the east wall connection of intact_horizontal_wall_bridge")
        public boolean intactHorizontalWallBridge = true;

        @Comments({
                "MC-255639: allow camp_1 to replace the original overlaid camp when variant 1 is selected.",
                "A disabled variant preserves the vanilla overlaid camp. All three disabled preserves vanilla RNG."
        })
        public boolean camp1 = true;

        @Comments("MC-255639: allow camp_2 to replace the original overlaid camp when variant 2 is selected")
        public boolean camp2 = true;

        @Comments("MC-255639: allow camp_3 to replace the original overlaid camp when variant 3 is selected")
        public boolean camp3 = true;
    }

    public static class AbandonedCamps extends ConfigurationPart {
        @Comments("MC-312909: restore the campsite_pale_garden_2 connection to its tent")
        public boolean campsitePaleGarden2 = true;

        @Comments("Repair trees inside the selected camp/tent only; does not enable trees in unrelated templates")
        public Trees trees = new Trees();
    }

    public static class Trees extends ConfigurationPart {
        @Comments("MC-312912: restore the giant tree in campsite_old_growth_pine_taiga_1")
        public boolean campsiteOldGrowthPineTaiga1 = true;

        @Comments("MC-312912: restore the giant tree in campsite_old_growth_spruce_taiga_4")
        public boolean campsiteOldGrowthSpruceTaiga4 = true;

        @Comments("MC-312912: restore the giant tree in campsite_snowy_taiga_3")
        public boolean campsiteSnowyTaiga3 = true;

        @Comments("MC-312913: restore the tree in tent_cherry_grove_1")
        public boolean tentCherryGrove1 = true;

        @Comments("MC-312913: restore the tree in tent_dappled_forest_1")
        public boolean tentDappledForest1 = true;

        @Comments("MC-312913: restore the tree in tent_dappled_forest_2")
        public boolean tentDappledForest2 = true;

        @Comments("MC-312913: restore the tree in tent_dappled_forest_3")
        public boolean tentDappledForest3 = true;

        @Comments("MC-312913: restore the tree in tent_dappled_forest_4")
        public boolean tentDappledForest4 = true;

        @Comments("MC-312913: restore the tree in tent_dappled_forest_6")
        public boolean tentDappledForest6 = true;

        @Comments("MC-312913: restore the tree in tent_savanna_1")
        public boolean tentSavanna1 = true;

        @Comments("MC-312913: restore the tree in tent_snowy_taiga_10")
        public boolean tentSnowyTaiga10 = true;

        @Comments("MC-312913: restore the tree in tent_taiga_2")
        public boolean tentTaiga2 = true;

        @Comments("MC-312913: restore the tree in tent_taiga_7")
        public boolean tentTaiga7 = true;

        @Comments("MC-312913: restore the tree in tent_windswept_forest_6")
        public boolean tentWindsweptForest6 = true;
    }
}
