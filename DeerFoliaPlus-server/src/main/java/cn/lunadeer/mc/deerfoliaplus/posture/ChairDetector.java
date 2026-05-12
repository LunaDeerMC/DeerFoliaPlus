package cn.lunadeer.mc.deerfoliaplus.posture;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;

final class ChairDetector {

    private static final double STAIR_BACK_OFFSET = 0.12D;

    private ChairDetector() {
    }

    public static Location detectSeat(Player player, Block clickedBlock) {
        SeatDefinition clickedSeat = SeatDefinition.from(player, clickedBlock);
        if (clickedSeat == null) {
            return null;
        }

        ChairChain chain = scanChain(clickedBlock, clickedSeat.axis());
        if (chain.length() > DeerFoliaPlusConfiguration.posture.maxChairChainLength) {
            return null;
        }

        if (DeerFoliaPlusConfiguration.posture.requireSideSigns && !hasEndSigns(chain)) {
            return null;
        }

        return clickedSeat.seatLocation(clickedBlock);
    }

    private static ChairChain scanChain(Block origin, SeatAxis axis) {
        Block first = origin;
        Block current = origin;

        while (true) {
            Block next = current.getRelative(axis.negative());
            if (!isSeatBlockForAxis(next, axis)) {
                break;
            }
            first = next;
            current = next;
        }

        Block last = origin;
        current = origin;
        while (true) {
            Block next = current.getRelative(axis.positive());
            if (!isSeatBlockForAxis(next, axis)) {
                break;
            }
            last = next;
            current = next;
        }

        int length = 1;
        current = first;
        while (!current.equals(last)) {
            current = current.getRelative(axis.positive());
            length++;
        }

        return new ChairChain(first, last, axis, length);
    }

    private static boolean hasEndSigns(ChairChain chain) {
        return isSeatSign(chain.first().getRelative(chain.axis().negative()))
                && isSeatSign(chain.last().getRelative(chain.axis().positive()));
    }

    private static boolean isSeatSign(Block block) {
        Material material = block.getType();
        String name = material.name();
        return (name.endsWith("_SIGN") || name.endsWith("_WALL_SIGN")) && !name.contains("HANGING");
    }

    private static boolean isSeatBlockForAxis(Block block, SeatAxis axis) {
        BlockData data = block.getBlockData();
        if (data instanceof Slab slab) {
            return slab.getType() == Slab.Type.BOTTOM;
        }
        if (data instanceof Stairs stairs) {
            return stairs.getHalf() == Bisected.Half.BOTTOM && SeatAxis.fromFacing(stairs.getFacing()) == axis;
        }
        return false;
    }

    private enum SeatAxis {
        X(BlockFace.WEST, BlockFace.EAST),
        Z(BlockFace.NORTH, BlockFace.SOUTH);

        private final BlockFace negative;
        private final BlockFace positive;

        SeatAxis(BlockFace negative, BlockFace positive) {
            this.negative = negative;
            this.positive = positive;
        }

        public static SeatAxis fromFacing(BlockFace facing) {
            return facing.getModX() != 0 ? Z : X;
        }

        public BlockFace negative() {
            return this.negative;
        }

        public BlockFace positive() {
            return this.positive;
        }
    }

    private record ChairChain(Block first, Block last, SeatAxis axis, int length) {
    }

    private record SeatDefinition(SeatAxis axis, BlockFace facing, double xOffset, double zOffset) {

        public static SeatDefinition from(Player player, Block block) {
            BlockData data = block.getBlockData();
            if (data instanceof Stairs stairs) {
                if (stairs.getHalf() != Bisected.Half.BOTTOM) {
                    return null;
                }

                BlockFace facing = stairs.getFacing();
                return new SeatDefinition(
                        SeatAxis.fromFacing(facing),
                        facing,
                        -facing.getModX() * STAIR_BACK_OFFSET,
                        -facing.getModZ() * STAIR_BACK_OFFSET
                );
            }

            if (data instanceof Slab slab) {
                if (slab.getType() != Slab.Type.BOTTOM) {
                    return null;
                }

                int xLength = scanChain(block, SeatAxis.X).length();
                int zLength = scanChain(block, SeatAxis.Z).length();
                SeatAxis axis;
                if (xLength == zLength) {
                    axis = player.getFacing().getModX() != 0 ? SeatAxis.Z : SeatAxis.X;
                } else {
                    axis = xLength > zLength ? SeatAxis.X : SeatAxis.Z;
                }

                return new SeatDefinition(axis, null, 0.0D, 0.0D);
            }

            return null;
        }

        public Location seatLocation(Block block) {
            Location seatLocation = block.getLocation().add(0.5D + this.xOffset, 0.5D, 0.5D + this.zOffset);
            if (this.facing != null) {
                seatLocation.setDirection(this.facing.getDirection());
            }
            return seatLocation;
        }
    }
}
