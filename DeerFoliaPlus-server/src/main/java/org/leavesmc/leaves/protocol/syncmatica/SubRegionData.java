package org.leavesmc.leaves.protocol.syncmatica;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;

public class SubRegionData {

    private final String name;
    private final BlockPos position;
    private final BlockPos size;

    public SubRegionData(final String name, final BlockPos position, final BlockPos size) {
        this.name = name;
        this.position = position;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public BlockPos getPosition() {
        return position;
    }

    public BlockPos getSize() {
        return size;
    }

    public JsonObject toJson() {
        final JsonObject obj = new JsonObject();
        obj.add("name", new JsonPrimitive(name));
        obj.add("x", new JsonPrimitive(position.getX()));
        obj.add("y", new JsonPrimitive(position.getY()));
        obj.add("z", new JsonPrimitive(position.getZ()));
        obj.add("sx", new JsonPrimitive(size.getX()));
        obj.add("sy", new JsonPrimitive(size.getY()));
        obj.add("sz", new JsonPrimitive(size.getZ()));
        return obj;
    }

    public static SubRegionData fromJson(final JsonObject obj) {
        final String name = obj.get("name").getAsString();
        final BlockPos position = new BlockPos(
            obj.get("x").getAsInt(),
            obj.get("y").getAsInt(),
            obj.get("z").getAsInt()
        );
        final BlockPos size = new BlockPos(
            obj.get("sx").getAsInt(),
            obj.get("sy").getAsInt(),
            obj.get("sz").getAsInt()
        );
        return new SubRegionData(name, position, size);
    }
}
