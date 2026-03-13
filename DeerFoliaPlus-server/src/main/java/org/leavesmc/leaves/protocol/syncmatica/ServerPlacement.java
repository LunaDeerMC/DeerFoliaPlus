package org.leavesmc.leaves.protocol.syncmatica;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public class ServerPlacement {

    private final UUID id;
    private String fileName;
    private String hash;
    private ServerPosition origin;
    private LocalLitematicState litematicState;
    private PlayerIdentifier owner;
    private PlayerIdentifier lastModifiedBy;
    private final Collection<SubRegionData> subRegionData = new ArrayList<>();
    private final Collection<SubRegionPlacementModification> subRegionPlacementModifications = new ArrayList<>();

    public ServerPlacement(final UUID id, final String fileName, final String hash, final ServerPosition origin) {
        this.id = id;
        this.fileName = fileName;
        this.hash = hash;
        this.origin = origin;
        this.litematicState = LocalLitematicState.NO_LOCAL_LITEMATIC;
        this.owner = PlayerIdentifier.MISSING_PLAYER;
        this.lastModifiedBy = PlayerIdentifier.MISSING_PLAYER;
    }

    public UUID getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getHash() {
        return hash;
    }

    public ServerPosition getOrigin() {
        return origin;
    }

    public void setOrigin(final ServerPosition origin) {
        this.origin = origin;
    }

    public LocalLitematicState getLocalState() {
        return litematicState;
    }

    public void setLocalState(final LocalLitematicState state) {
        this.litematicState = state;
    }

    public PlayerIdentifier getOwner() {
        return owner;
    }

    public void setOwner(final PlayerIdentifier owner) {
        this.owner = owner;
    }

    public PlayerIdentifier getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(final PlayerIdentifier lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public Collection<SubRegionData> getSubRegionData() {
        return Collections.unmodifiableCollection(subRegionData);
    }

    public void setSubRegionData(final Collection<SubRegionData> data) {
        subRegionData.clear();
        subRegionData.addAll(data);
    }

    public Collection<SubRegionPlacementModification> getSubRegionPlacementModifications() {
        return Collections.unmodifiableCollection(subRegionPlacementModifications);
    }

    public void setSubRegionPlacementModifications(final Collection<SubRegionPlacementModification> modifications) {
        subRegionPlacementModifications.clear();
        subRegionPlacementModifications.addAll(modifications);
    }

    public void move(final String dimensionId, final net.minecraft.core.BlockPos blockPosition) {
        origin = new ServerPosition(blockPosition, dimensionId);
    }

    public JsonObject toJson() {
        final JsonObject obj = new JsonObject();
        obj.add("id", new JsonPrimitive(id.toString()));
        obj.add("name", new JsonPrimitive(fileName));
        obj.add("hash", new JsonPrimitive(hash));
        obj.add("origin", origin.toJson());
        obj.add("owner", owner.toJson());
        obj.add("lastModifiedBy", lastModifiedBy.toJson());

        final JsonArray regionArray = new JsonArray();
        for (final SubRegionData region : subRegionData) {
            regionArray.add(region.toJson());
        }
        obj.add("subRegions", regionArray);

        return obj;
    }

    public static ServerPlacement fromJson(final JsonObject obj, final PlayerIdentifierProvider provider) {
        final UUID id = UUID.fromString(obj.get("id").getAsString());
        final String name = obj.get("name").getAsString();
        final String hash = obj.get("hash").getAsString();
        final ServerPosition origin = ServerPosition.fromJson(obj.get("origin").getAsJsonObject());

        final ServerPlacement placement = new ServerPlacement(id, name, hash, origin);

        if (obj.has("owner")) {
            placement.setOwner(provider.fromJson(obj.get("owner").getAsJsonObject()));
        }
        if (obj.has("lastModifiedBy")) {
            placement.setLastModifiedBy(provider.fromJson(obj.get("lastModifiedBy").getAsJsonObject()));
        }
        if (obj.has("subRegions")) {
            final JsonArray regions = obj.get("subRegions").getAsJsonArray();
            final Collection<SubRegionData> regionData = new ArrayList<>();
            for (final JsonElement element : regions) {
                regionData.add(SubRegionData.fromJson(element.getAsJsonObject()));
            }
            placement.setSubRegionData(regionData);
        }

        return placement;
    }
}
