package cn.lunadeer.mc.deerfoliaplus.posture;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class PostureService {

    private static final String ANCHOR_TAG = "deerfoliaplus_posture_anchor";
    private static final double SIT_ANCHOR_Y_OFFSET = -0.65D;
    private static final PostureService INSTANCE = new PostureService();

    private final PostureManager postureManager = PostureManager.getInstance();

    private PostureService() {
    }

    public static PostureService getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return DeerFoliaPlusConfiguration.posture.enabled;
    }

    public boolean isChairInteractionEnabled() {
        return this.isEnabled() && DeerFoliaPlusConfiguration.posture.chairInteraction;
    }

    public boolean isMovementLocked(Player player) {
        PostureSession session = this.postureManager.getSession(player);
        return session != null && session.type() != PostureType.CRAWLING;
    }

    public boolean isCrawling(Player player) {
        PostureSession session = this.postureManager.getSession(player);
        return session != null && session.type() == PostureType.CRAWLING;
    }

    public boolean isLying(Player player) {
        PostureSession session = this.postureManager.getSession(player);
        return session != null && session.type() == PostureType.LYING;
    }

    public boolean isSitting(Player player) {
        PostureSession session = this.postureManager.getSession(player);
        return session != null && session.type() == PostureType.SITTING;
    }

    public Location getLockedLocation(Player player) {
        PostureSession session = this.postureManager.getSession(player);
        return session == null ? null : session.lockLocation().clone();
    }

    public boolean sitAtCurrentPosition(Player player) {
        if (!this.isEnabled()) {
            return false;
        }
        return this.seatPlayer(player, player.getLocation().clone());
    }

    public boolean trySitOnBlock(Player player, Block clickedBlock) {
        if (!this.isChairInteractionEnabled()) {
            return false;
        }

        Location seatLocation = ChairDetector.detectSeat(player, clickedBlock);
        return seatLocation != null && this.seatPlayer(player, seatLocation);
    }

    public boolean startLay(Player player) {
        if (!this.isEnabled()) {
            return false;
        }

        this.getUp(player);
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        player.setVelocity(new Vector());
        this.postureManager.track(player, PostureType.LYING, player.getLocation().clone());
        return true;
    }

    public boolean startCrawl(Player player) {
        if (!this.isEnabled()) {
            return false;
        }

        this.getUp(player);
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        this.postureManager.track(player, PostureType.CRAWLING, player.getLocation().clone());
        return true;
    }

    public void getUp(Player player) {
        PostureSession session = this.postureManager.getSession(player);
        if (session == null) {
            return;
        }

        this.postureManager.clear(player);

        Location standLocation = session.lockLocation().clone();
        standLocation.setYaw(session.yaw());
        standLocation.setPitch(session.pitch());
        player.setVelocity(new Vector());
        player.teleportAsync(standLocation);
    }

    private boolean seatPlayer(Player player, Location seatLocation) {
        World world = seatLocation.getWorld();
        if (world == null) {
            return false;
        }

        this.getUp(player);

        Location anchorLocation = seatLocation.clone().add(0.0D, SIT_ANCHOR_Y_OFFSET, 0.0D);
        if (this.isSeatOccupied(world, anchorLocation, player)) {
            return false;
        }

        ArmorStand anchor = world.spawn(anchorLocation, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setCanPickupItems(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.addScoreboardTag(ANCHOR_TAG);
        });

        if (!anchor.addPassenger(player)) {
            anchor.remove();
            return false;
        }

        this.postureManager.track(player, PostureType.SITTING, seatLocation, anchor);
        return true;
    }

    private boolean isSeatOccupied(World world, Location anchorLocation, Player player) {
        return !world.getNearbyEntities(anchorLocation, 0.35D, 1.0D, 0.35D, entity -> this.isConflictingAnchor(entity, player)).isEmpty();
    }

    private boolean isConflictingAnchor(Entity entity, Player player) {
        if (!(entity instanceof ArmorStand armorStand)) {
            return false;
        }

        return armorStand.getScoreboardTags().contains(ANCHOR_TAG)
                && armorStand.getPassengers().stream().anyMatch(passenger -> !passenger.getUniqueId().equals(player.getUniqueId()));
    }
}
