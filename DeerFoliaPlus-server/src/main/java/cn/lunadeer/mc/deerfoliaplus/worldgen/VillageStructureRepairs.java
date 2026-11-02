package cn.lunadeer.mc.deerfoliaplus.worldgen;

import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.loader.TemplatePathFactory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Restores the one-block foundation space omitted by village roads' upward-only expansion. */
public final class VillageStructureRepairs {
    private static final Map<Identifier, StructureRepair> REPAIRS = Map.ofEntries(
        entry("plains/houses/plains_small_house_8", StructureRepair.VILLAGE_PLAINS_SMALL_HOUSE_8),
        entry("plains/zombie/houses/plains_small_house_8", StructureRepair.VILLAGE_PLAINS_SMALL_HOUSE_8),
        entry("plains/houses/plains_fisher_cottage_1", StructureRepair.VILLAGE_PLAINS_FISHER_COTTAGE_1),
        entry("desert/houses/desert_tannery_1", StructureRepair.VILLAGE_DESERT_TANNERY_1),
        entry("desert/houses/desert_large_farm_1", StructureRepair.VILLAGE_DESERT_LARGE_FARM_1),
        entry("savanna/houses/savanna_fisher_cottage_1", StructureRepair.VILLAGE_SAVANNA_FISHER_COTTAGE_1),
        entry("savanna/houses/savanna_shepherd_1", StructureRepair.VILLAGE_SAVANNA_SHEPHERD_1),
        entry("savanna/houses/savanna_animal_pen_2", StructureRepair.VILLAGE_SAVANNA_ANIMAL_PEN_2),
        entry("savanna/zombie/houses/savanna_animal_pen_2", StructureRepair.VILLAGE_SAVANNA_ANIMAL_PEN_2),
        entry("savanna/houses/savanna_large_farm_2", StructureRepair.VILLAGE_SAVANNA_LARGE_FARM_2),
        entry("savanna/zombie/houses/savanna_large_farm_2", StructureRepair.VILLAGE_SAVANNA_LARGE_FARM_2),
        entry("savanna/houses/savanna_small_farm", StructureRepair.VILLAGE_SAVANNA_SMALL_FARM)
    );

    private VillageStructureRepairs() {
    }

    public static Set<Identifier> templates() {
        return REPAIRS.keySet();
    }

    /**
     * Returns the original shape when inapplicable. The caller must reserve accepted extra space in
     * the outer context as well as its normal child context, so separate roads cannot reuse it.
     */
    public static VoxelShape candidateSpace(StructureTemplateManager manager,
                                           StructurePoolElement source, StructurePoolElement target,
                                           boolean attachInsideSource, BoundingBox sourceBox, BoundingBox targetBox,
                                           VoxelShape childrenFree, VoxelShape contextFree, int minBuildHeight, int maxBuildHeight) {
        if (!(source instanceof SinglePoolElement sourceSingle) || !(target instanceof SinglePoolElement targetSingle)
            || target.getProjection() != StructureTemplatePool.Projection.RIGID) {
            return childrenFree;
        }
        Identifier sourceId = templateId(sourceSingle);
        Identifier targetId = templateId(targetSingle);
        if (sourceId == null || targetId == null) {
            return childrenFree;
        }
        return candidateSpace(sourceId, targetId, source.getProjection(), attachInsideSource, sourceBox, targetBox,
            childrenFree, contextFree, minBuildHeight, maxBuildHeight, StructureGenerationFixes::enabled,
            id -> isOriginalTemplate(manager, id));
    }

    static VoxelShape candidateSpace(Identifier sourceId, Identifier targetId,
                                    StructureTemplatePool.Projection sourceProjection, boolean attachInsideSource,
                                    BoundingBox sourceBox, BoundingBox targetBox, VoxelShape childrenFree,
                                    VoxelShape contextFree, int minBuildHeight, int maxBuildHeight, Predicate<StructureRepair> enabled,
                                    Predicate<Identifier> originalTemplate) {
        StructureRepair repair = REPAIRS.get(targetId);
        if (repair == null || !enabled.test(repair) || !attachInsideSource
            || sourceProjection != StructureTemplatePool.Projection.TERRAIN_MATCHING
            || sourceBox.minY() - targetBox.minY() != 1 || targetBox.minY() < minBuildHeight || targetBox.maxY() > maxBuildHeight
            || !isMatchingStreet(sourceId, targetId)
            || !originalTemplate.test(sourceId) || !originalTemplate.test(targetId)) {
            return childrenFree;
        }

        // Do not move the building or its entrance. Only its original bottom layer is missing from
        // the parent's volume. Intersect it with the outer free space to retain all real collisions
        // and generation limits, including reservations made by previously accepted buildings.
        VoxelShape bottomLayer = Shapes.create(new AABB(sourceBox.minX(), targetBox.minY(), sourceBox.minZ(),
            sourceBox.maxX() + 1, sourceBox.minY(), sourceBox.maxZ() + 1));
        VoxelShape available = Shapes.join(bottomLayer, contextFree, BooleanOp.AND);
        return available.isEmpty() ? childrenFree : Shapes.join(childrenFree, available, BooleanOp.OR);
    }

    private static boolean isOriginalTemplate(StructureTemplateManager manager, Identifier id) {
        // Generated structures precede data-pack resources in StructureTemplateManager's search.
        // A matching packaged resource must not authorize changes to such an overriding template.
        try {
            if (Files.isRegularFile(manager.worldTemplates().createAndValidatePathToStructure(id,
                StructureTemplateManager.WORLD_STRUCTURE_LISTER))) {
                return false;
            }
            if (StructureUtils.testStructuresSourceDir != null && Files.isRegularFile(
                new TemplatePathFactory(StructureUtils.testStructuresSourceDir, PackType.SERVER_DATA)
                    .createAndValidatePathToStructure(id, StructureTemplateManager.RESOURCE_TEXT_STRUCTURE_LISTER))) {
                return false;
            }
            return StructureGenerationFixes.matchesVanillaResource(
                manager.resourceManagerSource.deerFoliaPlusResourceManager(),
                Identifier.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt"));
        } catch (RuntimeException unavailableTemplate) {
            // A custom source or invalid/inaccessible path must disable this optional repair.
            return false;
        }
    }

    private static @Nullable Identifier templateId(SinglePoolElement element) {
        try {
            return element.getTemplateLocation();
        } catch (RuntimeException runtimeTemplate) {
            // SinglePoolElement also accepts an in-memory StructureTemplate. Either.orThrow()
            // throws plain RuntimeException for that supported case, which has no resource ID.
            return null;
        }
    }

    private static boolean isMatchingStreet(Identifier source, Identifier target) {
        if (!source.getNamespace().equals("minecraft")) {
            return false;
        }
        String biome = target.getPath().split("/", 3)[1];
        return source.getPath().startsWith("village/" + biome + "/streets/")
            || source.getPath().startsWith("village/" + biome + "/zombie/streets/");
    }

    private static Map.Entry<Identifier, StructureRepair> entry(String template, StructureRepair repair) {
        return Map.entry(Identifier.withDefaultNamespace("village/" + template), repair);
    }
}
