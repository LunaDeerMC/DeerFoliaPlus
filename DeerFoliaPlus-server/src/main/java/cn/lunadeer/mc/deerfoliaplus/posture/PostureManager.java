package cn.lunadeer.mc.deerfoliaplus.posture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PostureManager {

    private static final PostureManager INSTANCE = new PostureManager();

    private final Map<UUID, PostureSession> sessions = new ConcurrentHashMap<>();

    private PostureManager() {
    }

    public static PostureManager getInstance() {
        return INSTANCE;
    }

    public boolean hasSession(Player player) {
        return this.sessions.containsKey(player.getUniqueId());
    }

    public boolean hasSession(UUID playerId) {
        return this.sessions.containsKey(playerId);
    }

    public PostureSession getSession(Player player) {
        return this.sessions.get(player.getUniqueId());
    }

    public PostureSession getSession(UUID playerId) {
        return this.sessions.get(playerId);
    }

    public void track(Player player, PostureType type, Location lockLocation) {
        this.track(player, type, lockLocation, null);
    }

    public void track(Player player, PostureType type, Location lockLocation, Entity anchorEntity) {
        UUID anchorEntityId = anchorEntity == null ? null : anchorEntity.getUniqueId();
        this.sessions.put(player.getUniqueId(), new PostureSession(type, lockLocation, anchorEntityId, lockLocation.getYaw(), lockLocation.getPitch()));
    }

    public void clear(Player player) {
        this.clear(player, true);
    }

    public void clear(Player player, boolean removeAnchor) {
        PostureSession session = this.sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (player.isInsideVehicle()) {
            Entity vehicle = player.getVehicle();
            if (vehicle != null && session.anchorEntityId() != null && session.anchorEntityId().equals(vehicle.getUniqueId())) {
                player.leaveVehicle();
            }
        }

        if (removeAnchor && session.anchorEntityId() != null) {
            Entity anchor = Bukkit.getEntity(session.anchorEntityId());
            if (anchor != null && anchor.isValid()) {
                anchor.remove();
            }
        }
    }
}
