package cn.lunadeer.mc.deerfoliaplus.worldgen;

import java.nio.file.Files;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Select one camp only after the original, weight-one compound candidate has been reached. */
public final class AncientCityCampRepairs {
    private static final Identifier POOL = Identifier.withDefaultNamespace("ancient_city/structures");
    private static final List<StructureRepair> REPAIRS = List.of(
            StructureRepair.ANCIENT_CITY_CAMP_1, StructureRepair.ANCIENT_CITY_CAMP_2, StructureRepair.ANCIENT_CITY_CAMP_3);

    private AncientCityCampRepairs() {
    }

    public static StructurePoolElement select(StructurePoolElement candidate, ResourceKey<StructureTemplatePool> pool,
                                               StructureTemplateManager templates, RandomSource random) {
        return select(candidate, pool, templates, random, StructureGenerationFixes::enabled);
    }

    static StructurePoolElement select(StructurePoolElement candidate, ResourceKey<StructureTemplatePool> pool,
                                        StructureTemplateManager templates, RandomSource random, Predicate<StructureRepair> enabled) {
        if (!POOL.equals(pool.identifier()) || REPAIRS.stream().noneMatch(enabled)
                || !(candidate instanceof ListPoolElement list)
                || candidate.getProjection() != StructureTemplatePool.Projection.RIGID
                || list.getElements().size() != 3) {
            return candidate;
        }
        var resources = templates.resourceManagerSource.deerFoliaPlusResourceManager();
        if (!StructureGenerationFixes.matchesVanillaResource(resources,
                Identifier.withDefaultNamespace("worldgen/template_pool/ancient_city/structures.json"))) {
            return candidate;
        }
        // Preserve explicit /structure save and test overrides as well as resource-pack overrides.
        if (net.minecraft.gametest.framework.StructureUtils.testStructuresSourceDir != null) {
            return candidate;
        }
        for (int i = 0; i < 3; i++) {
            StructurePoolElement element = list.getElements().get(i);
            Identifier id = Identifier.withDefaultNamespace("ancient_city/structures/camp_" + (i + 1));
            if (!(element instanceof SinglePoolElement single)
                    || element.getType() != StructurePoolElementType.SINGLE
                    || element.getProjection() != StructureTemplatePool.Projection.RIGID) {
                return candidate;
            }
            try {
                if (!id.equals(single.getTemplateLocation())
                        || Files.isRegularFile(templates.worldTemplates().createAndValidatePathToStructure(
                                id, StructureTemplateManager.WORLD_STRUCTURE_LISTER))
                        || !StructureGenerationFixes.matchesVanillaResource(resources,
                                Identifier.withDefaultNamespace("structure/" + id.getPath() + ".nbt"))) {
                    return candidate;
                }
            } catch (Exception invalidOrRuntimeTemplate) {
                return candidate;
            }
        }
        int variant = random.nextInt(3);
        return enabled.test(REPAIRS.get(variant)) ? list.getElements().get(variant) : candidate;
    }
}
