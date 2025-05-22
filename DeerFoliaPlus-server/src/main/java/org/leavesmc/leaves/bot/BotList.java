package org.leavesmc.leaves.bot;

import cn.lunadeer.mc.deerfoliaplus.DeerFoliaPlusConfiguration;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.logging.LogUtils;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.event.bot.*;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class BotList {

    public static BotList INSTANCE;

    private static final Logger LOGGER = LogUtils.getLogger();

    private final MinecraftServer server;

    public final List<ServerBot> bots = new CopyOnWriteArrayList<>();
    private final BotDataStorage dataStorage;

    private final Map<UUID, ServerBot> botsByUUID = new ConcurrentHashMap();
    private final Map<String, ServerBot> botsByName = new ConcurrentHashMap();

    public BotList(MinecraftServer server) {
        this.server = server;
        this.dataStorage = new BotDataStorage(server.storageSource);
        INSTANCE = this;
    }

    public ServerBot createNewBot(BotCreateState state) {
        BotCreateEvent event = new BotCreateEvent(state.name(), state.skinName(), state.location(), state.createReason(), state.creator());
        event.setCancelled(!isCreateLegal(state.name()));
        this.server.server.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return null;
        }

        Location location = event.getCreateLocation();
        ServerLevel world = ((CraftWorld) location.getWorld()).getHandle();

        CustomGameProfile profile = new CustomGameProfile(BotUtil.getBotUUID(state), state.name(), state.skin());
        ServerBot bot = new ServerBot(this.server, world, profile);
        bot.createState = state;
        if (event.getCreator() instanceof org.bukkit.entity.Player player) {
            bot.createPlayer = player.getUniqueId();
        }

        return this.placeNewBot(bot, world, location, null);
    }

    public ServerBot loadNewBot(String realName) {
        return this.loadNewBot(realName, this.dataStorage);
    }

    public ServerBot loadNewBot(String realName, IPlayerDataStorage playerIO) {
        UUID uuid = BotUtil.getBotUUID(realName);

        BotLoadEvent event = new BotLoadEvent(realName, uuid);
        this.server.server.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return null;
        }

        ServerBot bot = new ServerBot(this.server, this.server.getLevel(Level.OVERWORLD), new GameProfile(uuid, realName));
        bot.connection = new ServerBotPacketListenerImpl(this.server, bot);
        Optional<CompoundTag> optional = playerIO.load(bot);

        if (optional.isEmpty()) {
            return null;
        }

        ResourceKey<Level> resourcekey = null;
        if (optional.get().contains("WorldUUIDMost") && optional.get().contains("WorldUUIDLeast")) {
            org.bukkit.World bWorld = Bukkit.getServer().getWorld(new UUID(optional.get().getLong("WorldUUIDMost"), optional.get().getLong("WorldUUIDLeast")));
            if (bWorld != null) {
                resourcekey = ((CraftWorld) bWorld).getHandle().dimension();
            }
        }
        if (resourcekey == null) {
            return null;
        }

        ServerLevel world = this.server.getLevel(resourcekey);
        return this.placeNewBot(bot, world, bot.getLocation(), optional.get());
    }

    public ServerBot placeNewBot(ServerBot bot, ServerLevel world, Location location, @Nullable CompoundTag nbt) {
        Optional<CompoundTag> optional = Optional.ofNullable(nbt);

        bot.isRealPlayer = true;
        bot.connection = new ServerBotPacketListenerImpl(this.server, bot);
        bot.setServerLevel(world);

        BotSpawnLocationEvent event = new BotSpawnLocationEvent(bot.getBukkitEntity(), location);
        this.server.server.getPluginManager().callEvent(event);
        location = event.getSpawnLocation();

        bot.spawnIn(world);
        bot.gameMode.setLevel((ServerLevel) bot.level());

        bot.setPosRaw(location.getX(), location.getY(), location.getZ());
        bot.setRot(location.getYaw(), location.getPitch());

        this.bots.add(bot);
        this.botsByName.put(bot.getScoreboardName().toLowerCase(Locale.ROOT), bot);
        this.botsByUUID.put(bot.getUUID(), bot);

        bot.supressTrackerForLogin = true;
        world.addNewPlayer(bot);
        bot.loadAndSpawnEnderpearls(optional);
        bot.loadAndSpawnParentVehicle(optional);

        BotJoinEvent event1 = new BotJoinEvent(bot.getBukkitEntity(), PaperAdventure.asAdventure(Component.translatable("multiplayer.player.joined", bot.getDisplayName())).style(Style.style(NamedTextColor.YELLOW)));
        this.server.server.getPluginManager().callEvent(event1);

        net.kyori.adventure.text.Component joinMessage = event1.joinMessage();
        if (joinMessage != null && !joinMessage.equals(net.kyori.adventure.text.Component.empty())) {
            this.server.getPlayerList().broadcastSystemMessage(PaperAdventure.asVanilla(joinMessage), false);
        }

        bot.renderAll();
        bot.supressTrackerForLogin = false;
        bot.serverLevel().getChunkSource().chunkMap.addEntity(bot);
        BotList.LOGGER.info("{}[{}] logged in with entity id {} at ([{}]{}, {}, {})", bot.getName().getString(), "Local", bot.getId(), bot.serverLevel().serverLevelData.getLevelName(), bot.getX(), bot.getY(), bot.getZ());
        return bot;
    }

    public boolean removeBot(@NotNull ServerBot bot, @NotNull BotRemoveEvent.RemoveReason reason, @Nullable CommandSender remover, boolean saved) {
        return this.removeBot(bot, reason, remover, saved, this.dataStorage);
    }

    public boolean removeBot(@NotNull final ServerBot bot, @NotNull BotRemoveEvent.RemoveReason reason, @Nullable CommandSender remover, boolean saved, IPlayerDataStorage playerIO) {
        BotRemoveEvent event = new BotRemoveEvent(bot.getBukkitEntity(), reason, remover, PaperAdventure.asAdventure(Component.translatable("multiplayer.player.left", bot.getDisplayName())).style(Style.style(NamedTextColor.YELLOW)), saved);
        this.server.server.getPluginManager().callEvent(event);

        if (event.isCancelled() && event.getReason() != BotRemoveEvent.RemoveReason.INTERNAL) {
            return event.isCancelled();
        }

        if (bot.removeTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bot.removeTaskId);
            bot.removeTaskId = -1;
        }

        bot.getBukkitEntity().taskScheduler.schedule((e) -> {
            if (this.server.isSameThread()) {
                bot.doTick();
            }

            if (event.shouldSave()) {
                Bukkit.getAsyncScheduler().runNow(MinecraftInternalPlugin.INSTANCE, (t) -> playerIO.save(bot));
            } else {
                bot.dropAll();
            }

            if (bot.isPassenger()) {
                Entity entity = bot.getRootVehicle();
                if (entity.hasExactlyOnePlayerPassenger()) {
                    bot.stopRiding();
                    entity.getPassengersAndSelf().forEach((entity1) -> {
                        if (entity1 instanceof net.minecraft.world.entity.npc.AbstractVillager villager) {
                            final net.minecraft.world.entity.player.Player human = villager.getTradingPlayer();
                            if (human != null) {
                                villager.setTradingPlayer(null);
                            }
                        }
                        entity1.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
                    });
                }
            }

            bot.unRide();
            bot.serverLevel().removePlayerImmediately(bot, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
            this.bots.remove(bot);
            this.botsByName.remove(bot.getScoreboardName().toLowerCase(Locale.ROOT));

            UUID uuid = bot.getUUID();
            ServerBot bot1 = this.botsByUUID.get(uuid);
            if (bot1 == bot) {
                this.botsByUUID.remove(uuid);
            }
            bot.removeTab();
            for (ServerPlayer player : bot.serverLevel().players()) {
                if (!(player instanceof ServerBot) && !bot.needSendFakeData(player)) {
                    player.getBukkitEntity().taskScheduler.schedule(
                            (entity) -> player.connection.send(new ClientboundRemoveEntitiesPacket(bot.getId())),
                            null,
                            1L
                    );
                }
            }

            this.server.server.getGlobalRegionScheduler().execute(MinecraftInternalPlugin.INSTANCE, () -> {
                net.kyori.adventure.text.Component removeMessage = event.removeMessage();
                if (removeMessage != null && !removeMessage.equals(net.kyori.adventure.text.Component.empty())) {
                    this.server.getPlayerList().broadcastSystemMessage(PaperAdventure.asVanilla(removeMessage), false);
                }
            });
        }, null, 1L);
        return true;
    }

    public void removeAll() {
        for (ServerBot bot : this.bots) {
            bot.resume = DeerFoliaPlusConfiguration.fakePlayer.residentBot;
            this.removeBot(bot, BotRemoveEvent.RemoveReason.INTERNAL, null, bot.resume);
        }
    }

    public void loadResume() {
        if (DeerFoliaPlusConfiguration.fakePlayer.enable && DeerFoliaPlusConfiguration.fakePlayer.residentBot) {
            for (String realName : this.getSavedBotList().getAllKeys()) {
                CompoundTag nbt = this.getSavedBotList().getCompound(realName);
                if (nbt.getBoolean("resume")) {
                    this.loadNewBot(realName);
                }
            }
        }
    }

    public void networkTick() {
        for (ServerBot bot : this.bots) {
            bot.getBukkitEntity().taskScheduler.schedule((entity) -> bot.doTick(), (entity) -> {
            }, 1L);
        }
    }

    @Nullable
    public ServerBot getBot(@NotNull UUID uuid) {
        return this.botsByUUID.get(uuid);
    }

    @Nullable
    public ServerBot getBotByName(@NotNull String name) {
        return this.botsByName.get(name.toLowerCase(Locale.ROOT));
    }

    public CompoundTag getSavedBotList() {
        return this.dataStorage.getSavedBotList();
    }

    public boolean isCreateLegal(@NotNull String name) {
        if (!name.matches("^[a-zA-Z0-9_]{4,16}$")) {
            return false;
        }

        return Bukkit.getPlayerExact(name) == null && this.getBotByName(name) == null;
    }

    public static class CustomGameProfile extends GameProfile {

        public CustomGameProfile(UUID uuid, String name, String[] skin) {
            super(uuid, name);
            this.setSkin(skin);
        }

        public void setSkin(String[] skin) {
            if (skin != null) {
                this.getProperties().put("textures", new Property("textures", skin[0], skin[1]));
            }
        }
    }
}
