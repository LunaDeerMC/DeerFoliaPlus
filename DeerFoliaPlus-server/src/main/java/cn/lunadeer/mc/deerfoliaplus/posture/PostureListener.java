package cn.lunadeer.mc.deerfoliaplus.posture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;

public class PostureListener implements Listener {

    private final PostureManager postureManager = PostureManager.getInstance();

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }

        if (hand == EquipmentSlot.OFF_HAND && player.getInventory().getItemInOffHand().getType() != Material.AIR) {
            return;
        }

        if (PostureService.getInstance().trySitOnBlock(player, event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Player player = event.getPlayer();
        PostureService postureService = PostureService.getInstance();
        if (!postureService.isMovementLocked(player) || player.isInsideVehicle()) {
            return;
        }

        Location lockedLocation = postureService.getLockedLocation(player);
        if (lockedLocation == null || samePosition(to, lockedLocation)) {
            return;
        }

        lockedLocation.setYaw(to.getYaw());
        lockedLocation.setPitch(to.getPitch());
        event.setTo(lockedLocation);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.postureManager.clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        this.postureManager.clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        this.postureManager.clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        this.postureManager.clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        this.postureManager.clear(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) {
            return;
        }

        PostureSession session = this.postureManager.getSession(player);
        if (session == null || session.anchorEntityId() == null) {
            return;
        }

        if (session.anchorEntityId().equals(event.getVehicle().getUniqueId())) {
            PostureService.getInstance().getUp(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != org.leavesmc.leaves.plugin.MinecraftInternalPlugin.INSTANCE) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            this.postureManager.clear(player);
        }
    }

    private static boolean samePosition(Location first, Location second) {
        return first.getWorld() == second.getWorld()
                && Math.abs(first.getX() - second.getX()) <= 1.0E-4
                && Math.abs(first.getY() - second.getY()) <= 1.0E-4
                && Math.abs(first.getZ() - second.getZ()) <= 1.0E-4;
    }
}
