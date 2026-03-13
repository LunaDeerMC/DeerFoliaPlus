package org.leavesmc.leaves.protocol.syncmatica;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;

public class ServerPosition {

    private final BlockPos position;
    private final String dimensionId;

    public ServerPosition(final BlockPos pos, final String dim) {
        position = pos;
        dimensionId = dim;
    }

    public static ServerPosition fromJson(final JsonObject obj) {
        if (obj.has("position") && obj.has("dimension")) {
            final JsonArray arr = obj.get("position").getAsJsonArray();
            final int x = arr.get(0).getAsInt();
            final int y = arr.get(1).getAsInt();
            final int z = arr.get(2).getAsInt();
            final BlockPos pos = new BlockPos(x, y, z);
            return new ServerPosition(pos, obj.get("dimension").getAsString());
        }
        return null;
    }

    public BlockPos getBlockPosition() {
        return position;
    }

    public String getDimensionId() {
        return dimensionId;
    }

    public JsonObject toJson() {
        final JsonObject obj = new JsonObject();
        final JsonArray arr = new JsonArray();
        arr.add(new JsonPrimitive(position.getX()));
        arr.add(new JsonPrimitive(position.getY()));
        arr.add(new JsonPrimitive(position.getZ()));
        obj.add("position", arr);
        obj.add("dimension", new JsonPrimitive(dimensionId));
        return obj;
    }
}
