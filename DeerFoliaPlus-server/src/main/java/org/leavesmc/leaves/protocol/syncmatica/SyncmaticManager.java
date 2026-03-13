package org.leavesmc.leaves.protocol.syncmatica;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SyncmaticManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Syncmatica");

    private final Map<UUID, ServerPlacement> placements = new HashMap<>();
    private final PlayerIdentifierProvider playerIdentifierProvider = new PlayerIdentifierProvider();
    private final File saveDir;

    public SyncmaticManager(final File saveDir) {
        this.saveDir = saveDir;
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
    }

    public PlayerIdentifierProvider getPlayerIdentifierProvider() {
        return playerIdentifierProvider;
    }

    public Collection<ServerPlacement> getAll() {
        return Collections.unmodifiableCollection(placements.values());
    }

    public ServerPlacement getPlacement(final UUID id) {
        return placements.get(id);
    }

    public void addPlacement(final ServerPlacement placement) {
        placements.put(placement.getId(), placement);
        save();
    }

    public void removePlacement(final ServerPlacement placement) {
        placements.remove(placement.getId());
        save();
    }

    public void updatePlacement(final ServerPlacement placement) {
        save();
    }

    public void load() {
        final File saveFile = new File(saveDir, "placements.json");
        if (!saveFile.exists()) {
            return;
        }

        try (final FileReader reader = new FileReader(saveFile)) {
            final JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("placements")) {
                final JsonArray array = root.get("placements").getAsJsonArray();
                for (final JsonElement element : array) {
                    try {
                        final ServerPlacement placement = ServerPlacement.fromJson(
                            element.getAsJsonObject(), playerIdentifierProvider);
                        if (placement != null) {
                            placements.put(placement.getId(), placement);
                        }
                    } catch (final Exception e) {
                        LOGGER.error("Failed to load placement", e);
                    }
                }
            }
        } catch (final IOException e) {
            LOGGER.error("Failed to load syncmatica placements", e);
        }
    }

    public void save() {
        final File saveFile = new File(saveDir, "placements.json");
        final JsonObject root = new JsonObject();
        final JsonArray array = new JsonArray();

        for (final ServerPlacement placement : placements.values()) {
            array.add(placement.toJson());
        }

        root.add("placements", array);

        try (final FileWriter writer = new FileWriter(saveFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        } catch (final IOException e) {
            LOGGER.error("Failed to save syncmatica placements", e);
        }
    }
}
