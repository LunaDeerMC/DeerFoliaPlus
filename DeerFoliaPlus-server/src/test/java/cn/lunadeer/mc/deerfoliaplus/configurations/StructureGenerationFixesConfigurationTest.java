package cn.lunadeer.mc.deerfoliaplus.configurations;

import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationFile;
import cn.lunadeer.mc.deerfolia.utils.configuration.ConfigurationManager;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureGenerationFixesConfigurationTest {

    public static class Fixture extends ConfigurationFile {
        public StructureGenerationFixesConfiguration structureGenerationFixes = new StructureGenerationFixesConfiguration();
    }

    @Test
    void missingFileAndMissingSectionPreserveDefaultEnabledSettings(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("deer-folia-plus.yml");
        assertDoesNotThrow(() -> StructureGenerationFixesConfiguration.validate(file.toFile()));
        assertFalse(Files.exists(file));

        Fixture created = (Fixture) ConfigurationManager.load(Fixture.class, file.toFile());
        assertTrue(created.structureGenerationFixes.enabled);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        Set<String> switches = booleanKeys(yaml);
        assertEquals(45, switches.size()); // Master plus 44 independent switches.
        for (String key : switches) {
            assertTrue(yaml.getBoolean(key), key);
        }
        assertTrue(switches.contains("structure-generation-fixes.trial-chambers.chamber-disposal8"));
        assertTrue(switches.contains("structure-generation-fixes.abandoned-camps.trees.tent-dappled-forest6"));
        assertTrue(switches.contains("structure-generation-fixes.villages.melon-pile"));
        assertTrue(switches.contains("structure-generation-fixes.villages.snow-pile"));
        assertDoesNotThrow(() -> StructureGenerationFixesConfiguration.validate(file.toFile()));

        Files.writeString(file, "unrelated-feature:\n  value: retained\n");
        assertDoesNotThrow(() -> StructureGenerationFixesConfiguration.validate(file.toFile()));
        Fixture upgraded = (Fixture) ConfigurationManager.load(Fixture.class, file.toFile());
        assertTrue(upgraded.structureGenerationFixes.enabled);
        yaml.load(file.toFile());
        assertEquals(switches, booleanKeys(yaml));
        assertEquals("retained", yaml.getString("unrelated-feature.value"));
    }

    @Test
    void explicitFalseSurvivesLoadAndSaveIndependently(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("deer-folia-plus.yml");
        Files.writeString(file, """
                structure-generation-fixes:
                  enabled: false
                  trail-ruins:
                    group-upper3: false
                  abandoned-camps:
                    trees:
                      tent-dappled-forest6: false
                """);
        StructureGenerationFixesConfiguration.validate(file.toFile());
        Fixture loaded = (Fixture) ConfigurationManager.load(Fixture.class, file.toFile());
        var config = loaded.structureGenerationFixes;
        assertFalse(config.enabled);
        assertFalse(config.trailRuins.groupUpper3);
        assertFalse(config.abandonedCamps.trees.tentDappledForest6);
        assertTrue(config.trailRuins.groupUpper2);
        assertTrue(config.abandonedCamps.trees.tentDappledForest4);

        StructureGenerationFixesConfiguration.validate(file.toFile());
        Fixture reloaded = (Fixture) ConfigurationManager.load(Fixture.class, file.toFile());
        assertFalse(reloaded.structureGenerationFixes.enabled);
        assertFalse(reloaded.structureGenerationFixes.trailRuins.groupUpper3);
        assertFalse(reloaded.structureGenerationFixes.abandonedCamps.trees.tentDappledForest6);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "plains-small-house8", "plains-fisher-cottage1", "desert-tannery1", "desert-large-farm1",
            "savanna-fisher-cottage1", "savanna-shepherd1", "savanna-animal-pen2", "savanna-large-farm2", "savanna-small-farm"
    })
    void villagePlacementSwitchesRoundTripAndRejectInvalidType(String suffix, @TempDir Path directory) throws Exception {
        Path file = directory.resolve("deer-folia-plus.yml");
        String key = "structure-generation-fixes.villages." + suffix;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(key, false);
        yaml.save(file.toFile());
        StructureGenerationFixesConfiguration.validate(file.toFile());
        Fixture loaded = (Fixture) ConfigurationManager.load(Fixture.class, file.toFile());
        assertTrue(loaded.structureGenerationFixes.enabled);
        yaml.load(file.toFile());
        assertEquals(45, booleanKeys(yaml).size());
        assertFalse(yaml.getBoolean(key));
        for (String other : booleanKeys(yaml)) {
            if (!other.equals(key)) {
                assertTrue(yaml.getBoolean(other), other);
            }
        }

        yaml.set(key, "false");
        String invalid = yaml.saveToString();
        Files.writeString(file, invalid);
        assertThrows(InvalidConfigurationException.class, () -> StructureGenerationFixesConfiguration.validate(file.toFile()));
        assertEquals(invalid, Files.readString(file));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "structure-generation-fixes: [\n",
            "structure-generation-fixes: false\n",
            "structure-generation-fixes: null\n",
            "structure-generation-fixes:\n  enabled: 'false'\n",
            "structure-generation-fixes:\n  enabled: null\n",
            "structure-generation-fixes:\n  enabled: 0\n",
            "structure-generation-fixes:\n  villages: false\n",
            "structure-generation-fixes:\n  trail-ruins:\n    group-upper3: []\n",
            "structure-generation-fixes:\n  abandoned-camps:\n    trees:\n      tent-dappled-forest6: 'true'\n"
    })
    void malformedConfigurationIsRejectedWithoutRewritingFile(String contents, @TempDir Path directory) throws Exception {
        Path file = directory.resolve("deer-folia-plus.yml");
        Files.writeString(file, contents);
        assertThrows(InvalidConfigurationException.class, () -> StructureGenerationFixesConfiguration.validate(file.toFile()));
        assertEquals(contents, Files.readString(file));
    }

    @Test
    void validationDoesNotAddTypeRulesForOtherFeatures(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("deer-folia-plus.yml");
        String contents = """
                unrelated-feature:
                  enabled: 'not-a-boolean'
                structure-generation-fixes:
                  enabled: false
                  abandoned-camps:
                    trees:
                      tent-dappled-forest6: false
                """;
        Files.writeString(file, contents);
        assertDoesNotThrow(() -> StructureGenerationFixesConfiguration.validate(file.toFile()));
        assertEquals(contents, Files.readString(file));
    }

    private static Set<String> booleanKeys(YamlConfiguration yaml) {
        Set<String> result = new TreeSet<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isBoolean(key)) {
                result.add(key);
            }
        }
        return result;
    }
}
