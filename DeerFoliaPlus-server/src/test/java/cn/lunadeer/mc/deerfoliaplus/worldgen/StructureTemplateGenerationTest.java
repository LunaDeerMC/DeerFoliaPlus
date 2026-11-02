package cn.lunadeer.mc.deerfoliaplus.worldgen;

import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@VanillaFeature
class StructureTemplateGenerationTest {
    static Stream<Arguments> placements() {
        return StructureGenerationFixture.cases().stream().flatMap(fixture ->
                Stream.of(Rotation.values()).map(rotation -> Arguments.of(fixture, rotation)));
    }

    @ParameterizedTest(name = "actual child placement: {0}, {1}")
    @MethodSource("placements")
    void repairedConnectionGeneratesAndVanillaConnectionDoesNot(StructureGenerationFixture.Case fixture, Rotation rotation) throws Exception {
        for (long seed : new long[]{1L, 719225L, 89744913L}) {
            var vanilla = StructureGenerationFixture.generate(fixture, false, rotation, seed);
            var repaired = StructureGenerationFixture.generate(fixture, true, rotation, seed);
            assertTrue(vanilla.children().isEmpty(), fixture.name() + ": vanilla must reproduce the missing connection");
            assertEquals(1, repaired.children().size(), fixture.name() + ": fixed template must pass actual placement and collision checks");
            PoolElementStructurePiece child = repaired.children().getFirst();
            assertEquals(1, repaired.parent().getJunctions().size(), "One real parent/child junction must be recorded");
            assertEquals(1, child.getJunctions().size(), "The child must retain the reverse junction");
            BoundingBox childBounds = child.getBoundingBox();
            assertTrue(childBounds.minY() >= -64 && childBounds.maxY() < 320, "The repair must fit actual world height limits");
        }
    }

    @Test
    void trialBridgeAlignsWithBothChamberFloorsAndFitsInsideRoom() throws Exception {
        var fixture = StructureGenerationFixture.cases().stream()
                .filter(value -> value.repair() == StructureRepair.TRIAL_WALKWAY_BRIDGE).findFirst().orElseThrow();
        var repaired = StructureGenerationFixture.generate(fixture, true, Rotation.NONE, 719225L);
        var child = repaired.children().getFirst();
        assertEquals(Rotation.NONE, child.getRotation());
        assertEquals(new BlockPos(11, 66, 2), child.getPosition(), "Bridge origin must preserve the existing lower and middle floor elevations");
        assertEquals(66, child.getBoundingBox().minY());
        assertTrue(repaired.parent().getBoundingBox().isInside(child.getBoundingBox().minX(), child.getBoundingBox().minY(), child.getBoundingBox().minZ()));
        assertTrue(repaired.parent().getBoundingBox().isInside(child.getBoundingBox().maxX(), child.getBoundingBox().maxY(), child.getBoundingBox().maxZ()));
        assertFalse(repaired.childTemplate().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.TUFF_BRICKS).isEmpty());
        assertTrue(repaired.childTemplate().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.STRUCTURE_BLOCK).isEmpty(),
                "The old SAVE-mode development block must not leak into generated terrain");
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void newAnchorsPlaceAsTerrainAndNeverLeaveDevelopmentBlocks(Rotation rotation) throws Exception {
        record Anchor(BlockPos position, Block finalBlock) {
        }
        Map<StructureRepair, Anchor> anchors = Map.of(
                StructureRepair.TRIAL_CORRIDOR_WALL, new Anchor(new BlockPos(6, 0, 1), Blocks.TUFF_BRICKS),
                StructureRepair.TRIAL_WALKWAY_BRIDGE, new Anchor(new BlockPos(10, 6, 12), Blocks.AIR),
                StructureRepair.PALE_GARDEN_CAMPSITE_2, new Anchor(new BlockPos(9, 0, 4), Blocks.DIRT_PATH),
                StructureRepair.ANCIENT_CITY_WALL_BRIDGE, new Anchor(new BlockPos(17, 0, 10), Blocks.DEEPSLATE_TILES));
        for (var fixture : StructureGenerationFixture.cases()) {
            Anchor anchor = anchors.get(fixture.repair());
            if (anchor == null) {
                continue;
            }
            var generated = StructureGenerationFixture.generate(fixture, true, rotation, 719225L);
            var piece = fixture.repairParent() ? generated.parent() : generated.children().getFirst();
            var blocks = StructureGenerationFixture.placePiece(generated, piece);
            BlockPos worldAnchor = anchor.position().rotate(piece.getRotation()).offset(piece.getPosition());
            assertNotNull(blocks.get(worldAnchor), fixture.name() + ": anchor must be materialized by the real pool element");
            assertTrue(blocks.get(worldAnchor).is(anchor.finalBlock()), fixture.name() + ": final_state must preserve the intended terrain");
            assertFalse(blocks.values().stream().anyMatch(state -> state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_BLOCK)),
                    fixture.name() + ": generated world blocks must contain neither jigsaws nor development markers");
            if (fixture.repair() == StructureRepair.TRIAL_WALKWAY_BRIDGE) {
                BlockPos saved = new BlockPos(7, 1, 4).rotate(piece.getRotation()).offset(piece.getPosition());
                assertTrue(blocks.get(saved).isAir(), "The old SAVE marker must materialize as air");
            }
        }
    }

    @Test
    void candidateGenerationDoesNotMutateOriginalTemplateResources() throws Exception {
        for (var fixture : StructureGenerationFixture.cases()) {
            String affected = fixture.repairParent() ? fixture.parent() : fixture.child();
            CompoundTag original = StructureGenerationFixture.original(affected);
            var before = original.copy();
            CompoundTag repaired = StructureTemplateRepairs.apply(Identifier.withDefaultNamespace(affected), original,
                    value -> value == fixture.repair());
            assertNotSame(original, repaired, "Enabled repair must produce a separate template");
            StructureGenerationFixture.load(repaired);
            assertEquals(before, original, affected);
            assertEquals(before, StructureGenerationFixture.original(affected), "Original resource fixture must remain reusable for OFF mode");
        }
    }
}
