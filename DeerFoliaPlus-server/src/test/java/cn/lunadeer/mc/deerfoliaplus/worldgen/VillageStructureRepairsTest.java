package cn.lunadeer.mc.deerfoliaplus.worldgen;

import com.mojang.datafixers.util.Either;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageStructureRepairsTest {
    private static final Identifier STREET = id("plains/streets/straight_06");
    private static final Identifier HOUSE = id("plains/houses/plains_small_house_8");
    private static final BoundingBox ROAD_BOX = new BoundingBox(0, 4, 0, 20, 17, 17);
    private static final BoundingBox HOUSE_BOX = new BoundingBox(2, 3, 4, 9, 11, 12);
    private static final VoxelShape ROAD_SPACE = Shapes.create(AABB.of(ROAD_BOX));
    private static final VoxelShape OUTER_SPACE = Shapes.join(Shapes.create(new AABB(-80, 0, -80, 81, 100, 81)),
        ROAD_SPACE, BooleanOp.ONLY_FIRST);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final class RuntimeElement extends SinglePoolElement {
        RuntimeElement(StructureTemplatePool.Projection projection) {
            super(Either.right(new StructureTemplate()), Holder.direct(new StructureProcessorList(List.of())),
                projection, Optional.empty());
        }
    }

    @Test
    void inMemoryTemplatesAreIgnoredWithoutAttemptingResourceLookup() {
        var runtimeSource = new RuntimeElement(StructureTemplatePool.Projection.TERRAIN_MATCHING);
        var runtimeTarget = new RuntimeElement(StructureTemplatePool.Projection.RIGID);
        var street = StructurePoolElement.single(STREET.toString()).apply(StructureTemplatePool.Projection.TERRAIN_MATCHING);
        var house = StructurePoolElement.single(HOUSE.toString()).apply(StructureTemplatePool.Projection.RIGID);
        assertSame(ROAD_SPACE, VillageStructureRepairs.candidateSpace(null, runtimeSource, house, true,
            ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, 319));
        assertSame(ROAD_SPACE, VillageStructureRepairs.candidateSpace(null, street, runtimeTarget, true,
            ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, 319));
    }

    @Test
    void modifiedTargetProjectionIsIgnoredBeforeResourceLookup() {
        var street = StructurePoolElement.single(STREET.toString()).apply(StructureTemplatePool.Projection.TERRAIN_MATCHING);
        var terrainHouse = StructurePoolElement.single(HOUSE.toString()).apply(StructureTemplatePool.Projection.TERRAIN_MATCHING);
        assertSame(ROAD_SPACE, VillageStructureRepairs.candidateSpace(null, street, terrainHouse, true,
            ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, 319));
    }

    @Test
    void restoresOnlyMissingFoundationWithoutMovingOrMutatingEitherBox() {
        assertFalse(fits(ROAD_SPACE, HOUSE_BOX));
        VoxelShape candidate = candidate(STREET, HOUSE, ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, r -> true, i -> true);
        assertTrue(fits(candidate, HOUSE_BOX));
        assertFalse(fits(ROAD_SPACE, HOUSE_BOX));
        assertEquals(4, ROAD_BOX.minY());
        assertEquals(3, HOUSE_BOX.minY());
        assertTrue(Shapes.joinIsNotEmpty(candidate, ROAD_SPACE, BooleanOp.NOT_SAME));
    }

    @Test
    void outerObstacleStillRejectsBuildingAndAcceptedFoundationCannotBeReused() {
        VoxelShape occupied = Shapes.create(new AABB(2, 3, 4, 10, 4, 13));
        VoxelShape blocked = Shapes.join(OUTER_SPACE, occupied, BooleanOp.ONLY_FIRST);
        assertFalse(fits(candidate(STREET, HOUSE, ROAD_BOX, HOUSE_BOX, ROAD_SPACE, blocked, 0, r -> true, i -> true), HOUSE_BOX));

        // This is the second reservation performed by the JigsawPlacement hook on success.
        VoxelShape afterPlacement = Shapes.join(OUTER_SPACE, Shapes.create(AABB.of(HOUSE_BOX)), BooleanOp.ONLY_FIRST);
        assertFalse(fits(candidate(STREET, HOUSE, ROAD_BOX, HOUSE_BOX, ROAD_SPACE, afterPlacement, 0, r -> true, i -> true), HOUSE_BOX));
    }

    @Test
    void keepsInternalCollisionsAndHorizontalRoadBoundary() {
        VoxelShape blockedChildren = Shapes.join(ROAD_SPACE, Shapes.create(new AABB(3, 5, 5, 4, 6, 6)), BooleanOp.ONLY_FIRST);
        assertFalse(fits(candidate(STREET, HOUSE, ROAD_BOX, HOUSE_BOX, blockedChildren, OUTER_SPACE, 0, r -> true, i -> true), HOUSE_BOX));
        BoundingBox outsideRoad = new BoundingBox(-1, 3, 4, 9, 11, 12);
        assertFalse(fits(candidate(STREET, HOUSE, ROAD_BOX, outsideRoad, ROAD_SPACE, OUTER_SPACE, 0, r -> true, i -> true), outsideRoad));
    }

    @Test
    void neverExtendsMoreThanOneLayerOrPastWorldBottom() {
        BoundingBox twoLayers = new BoundingBox(2, 2, 4, 9, 11, 12);
        assertSame(ROAD_SPACE, candidate(STREET, HOUSE, ROAD_BOX, twoLayers, ROAD_SPACE, OUTER_SPACE, 0, r -> true, i -> true));
        assertSame(ROAD_SPACE, candidate(STREET, HOUSE, ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 4, r -> true, i -> true));
    }

    @Test
    void respectsInclusiveWorldTopEvenWhenParentVolumeExtendsPastIt() {
        VoxelShape exactlyAtTop = VillageStructureRepairs.candidateSpace(STREET, HOUSE,
            StructureTemplatePool.Projection.TERRAIN_MATCHING, true, ROAD_BOX, HOUSE_BOX,
            ROAD_SPACE, OUTER_SPACE, 0, HOUSE_BOX.maxY(), r -> true, i -> true);
        assertTrue(fits(exactlyAtTop, HOUSE_BOX));
        assertSame(ROAD_SPACE, VillageStructureRepairs.candidateSpace(STREET, HOUSE,
            StructureTemplatePool.Projection.TERRAIN_MATCHING, true, ROAD_BOX, HOUSE_BOX,
            ROAD_SPACE, OUTER_SPACE, 0, HOUSE_BOX.maxY() - 1, r -> true, i -> true));
    }

    @Test
    void disablingRepairAndEitherDataPackOverridePreserveOriginalShape() {
        assertSame(ROAD_SPACE, candidate(STREET, HOUSE, ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, r -> false, i -> true));
        assertSame(ROAD_SPACE, candidate(STREET, HOUSE, ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, r -> true, i -> !i.equals(STREET)));
        assertSame(ROAD_SPACE, candidate(STREET, HOUSE, ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, r -> true, i -> !i.equals(HOUSE)));
    }

    @Test
    void doesNotChangeUnrelatedHousesPoolsProjectionsOrOutsideConnections() {
        assertSame(ROAD_SPACE, candidate(STREET, id("plains/houses/plains_small_house_1"), ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, r -> true, i -> true));
        assertSame(ROAD_SPACE, candidate(id("savanna/streets/straight_06"), id("savanna/houses/savanna_medium_house_1"), ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, r -> true, i -> true));
        assertSame(ROAD_SPACE, candidate(id("plains/town_centers/plains_fountain_01"), HOUSE, ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, r -> true, i -> true));
        assertSame(ROAD_SPACE, candidate(id("desert/streets/straight_01"), HOUSE, ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, r -> true, i -> true));
        assertSame(ROAD_SPACE, VillageStructureRepairs.candidateSpace(STREET, HOUSE, StructureTemplatePool.Projection.RIGID,
            true, ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, 319, r -> true, i -> true));
        assertSame(ROAD_SPACE, VillageStructureRepairs.candidateSpace(STREET, HOUSE, StructureTemplatePool.Projection.TERRAIN_MATCHING,
            false, ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0, 319, r -> true, i -> true));
    }

    @Test
    void allTwelveTemplatesHaveNineIndependentSwitchesIncludingZombieVariants() {
        Set<StructureRepair> observed = new java.util.HashSet<>();
        assertEquals(12, VillageStructureRepairs.templates().size());
        for (Identifier template : VillageStructureRepairs.templates()) {
            String biome = template.getPath().split("/")[1];
            String zombie = template.getPath().contains("/zombie/") ? "zombie/" : "";
            Identifier street = id(biome + "/" + zombie + "streets/straight_01");
            VoxelShape candidate = candidate(street, template, ROAD_BOX, HOUSE_BOX, ROAD_SPACE, OUTER_SPACE, 0,
                repair -> { observed.add(repair); return true; }, i -> true);
            assertTrue(fits(candidate, HOUSE_BOX), template.toString());
        }
        assertEquals(9, observed.size());
        assertEquals(Set.of("VILLAGE_PLAINS_SMALL_HOUSE_8", "VILLAGE_PLAINS_FISHER_COTTAGE_1", "VILLAGE_DESERT_TANNERY_1",
            "VILLAGE_DESERT_LARGE_FARM_1", "VILLAGE_SAVANNA_FISHER_COTTAGE_1", "VILLAGE_SAVANNA_SHEPHERD_1",
            "VILLAGE_SAVANNA_ANIMAL_PEN_2", "VILLAGE_SAVANNA_LARGE_FARM_2", "VILLAGE_SAVANNA_SMALL_FARM"),
            observed.stream().map(Enum::name).collect(Collectors.toSet()));
    }

    private static VoxelShape candidate(Identifier street, Identifier house, BoundingBox roadBox, BoundingBox houseBox,
                                        VoxelShape children, VoxelShape outer, int minY,
                                        Predicate<StructureRepair> enabled, Predicate<Identifier> original) {
        return VillageStructureRepairs.candidateSpace(street, house, StructureTemplatePool.Projection.TERRAIN_MATCHING,
            true, roadBox, houseBox, children, outer, minY, 319, enabled, original);
    }

    private static boolean fits(VoxelShape available, BoundingBox box) {
        return !Shapes.joinIsNotEmpty(available, Shapes.create(AABB.of(box).deflate(0.25)), BooleanOp.ONLY_SECOND);
    }

    private static Identifier id(String path) {
        return Identifier.withDefaultNamespace("village/" + path);
    }
}
