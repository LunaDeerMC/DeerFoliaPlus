package cn.lunadeer.mc.deerfoliaplus.posture;

import org.bukkit.Location;

import java.util.UUID;

public record PostureSession(PostureType type, Location lockLocation, UUID anchorEntityId, float yaw, float pitch) {

    public PostureSession {
        lockLocation = lockLocation.clone();
    }
}
