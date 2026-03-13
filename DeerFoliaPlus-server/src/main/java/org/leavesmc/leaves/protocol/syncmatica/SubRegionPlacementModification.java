package org.leavesmc.leaves.protocol.syncmatica;

import net.minecraft.core.BlockPos;

public class SubRegionPlacementModification {

    private final String name;
    private final BlockPos position;
    private final String rotation;
    private final String mirror;

    public SubRegionPlacementModification(final String name, final BlockPos position, final String rotation, final String mirror) {
        this.name = name;
        this.position = position;
        this.rotation = rotation;
        this.mirror = mirror;
    }

    public String getName() {
        return name;
    }

    public BlockPos getPosition() {
        return position;
    }

    public String getRotation() {
        return rotation;
    }

    public String getMirror() {
        return mirror;
    }
}
