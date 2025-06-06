package org.leavesmc.leaves.bot;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import com.google.common.collect.ImmutableMap;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.leavesmc.leaves.bot.agent.Actions;
import org.leavesmc.leaves.bot.agent.BotAction;
import org.leavesmc.leaves.bot.agent.BotConfig;
import org.leavesmc.leaves.bot.agent.Configs;
import org.leavesmc.leaves.entity.CraftBot;
import org.leavesmc.leaves.event.bot.BotActionScheduleEvent;
import org.leavesmc.leaves.event.bot.BotCreateEvent;
import org.leavesmc.leaves.event.bot.BotDeathEvent;
import org.leavesmc.leaves.event.bot.BotRemoveEvent;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;
import org.leavesmc.leaves.util.MathUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Predicate;

// TODO test
public class ServerBot extends ServerPlayer {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private final Map<Configs<?>, BotConfig<?>> configs;
    private final List<BotAction<?>> actions;

    public boolean resume = false;
    public BotCreateState createState;
    public UUID createPlayer;

    private final int tracingRange;
    private final ServerStatsCounter stats;
    private final BotInventoryContainer container;

    public int notSleepTicks;

    public int removeTaskId = -1;

    private Vec3 knockback = Vec3.ZERO;

    private long botTickCount = 0;

    public ServerBot(MinecraftServer server, ServerLevel world, GameProfile profile) {
        super(server, world, profile, ClientInformation.createDefault());
        this.entityData.set(Player.DATA_PLAYER_MODE_CUSTOMISATION, (byte) -2);

        this.gameMode = new ServerBotGameMode(this);
        this.actions = new ArrayList<>();

        ImmutableMap.Builder<Configs<?>, BotConfig<?>> configBuilder = ImmutableMap.builder();
        for (Configs<?> config : Configs.getConfigs()) {
            configBuilder.put(config, config.config.create(this));
        }
        this.configs = configBuilder.build();

        this.stats = new BotStatsCounter(server);
        this.container = new BotInventoryContainer(this);
        this.tracingRange = world.spigotConfig.playerTrackingRange * world.spigotConfig.playerTrackingRange;

        this.notSleepTicks = 0;
        this.fauxSleeping = DeerFoliaPlusConfiguration.fakePlayer.skipSleepCheck;

        this.setInvulnerable(DeerFoliaPlusConfiguration.fakePlayer.invulnerable);
    }

    public void sendPlayerInfo(ServerPlayer player) {
        player.connection.send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(this)));
    }

    public boolean needSendFakeData(ServerPlayer player) {
        return this.getConfigValue(Configs.ALWAYS_SEND_DATA) && (player.level() == this.level() && player.position().distanceToSqr(this.position()) > this.tracingRange);
    }

    public void sendFakeDataIfNeed(ServerPlayer player, boolean login) {
        if (needSendFakeData(player)) {
            this.sendFakeData(player.connection, login);
        }
    }

    public void sendFakeData(ServerPlayerConnection playerConnection, boolean login) {
        ChunkMap.TrackedEntity entityTracker = null; // TODO
        // this.serverLevel().getChunkSource().chunkMap.entityMap.get(this.getId());

        if (entityTracker == null) {
            LOGGER.warn("Fakeplayer cant get entity tracker for " + this.getId());
            return;
        }

        playerConnection.send(this.getAddEntityPacket(entityTracker.serverEntity));
        if (login) {
            Bukkit.getScheduler().runTaskLater(MinecraftInternalPlugin.INSTANCE, () -> playerConnection.send(new ClientboundRotateHeadPacket(this, (byte) ((getYRot() * 256f) / 360f))), 10);
        } else {
            playerConnection.send(new ClientboundRotateHeadPacket(this, (byte) ((getYRot() * 256f) / 360f)));
        }
    }

    public void renderAll() {
        this.server.getPlayerList().getPlayers().forEach(
                player -> {
                    this.sendPlayerInfo(player);
                    this.sendFakeDataIfNeed(player, false);
                }
        );
    }

    private void sendPacket(Packet<?> packet) {
        this.server.getPlayerList().getPlayers().forEach(player -> player.connection.send(packet));
    }

    @Override
    public void die(@NotNull DamageSource damageSource) {
        boolean flag = this.serverLevel().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES);
        Component defaultMessage = this.getCombatTracker().getDeathMessage();

        BotDeathEvent event = new BotDeathEvent(this.getBukkitEntity(), PaperAdventure.asAdventure(defaultMessage), flag);
        this.server.server.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            if (this.getHealth() <= 0) {
                this.setHealth(0.1f);
            }
            return;
        }

        this.gameEvent(GameEvent.ENTITY_DIE);

        net.kyori.adventure.text.Component deathMessage = event.deathMessage();
        if (event.isSendDeathMessage() && deathMessage != null && !deathMessage.equals(net.kyori.adventure.text.Component.empty())) {
            this.server.getPlayerList().broadcastSystemMessage(PaperAdventure.asVanilla(deathMessage), false);
        }

        this.server.getBotList().removeBot(this, BotRemoveEvent.RemoveReason.DEATH, null, false);
    }

    public void removeTab() {
        this.sendPacket(new ClientboundPlayerInfoRemovePacket(List.of(this.getUUID())));
    }

    @Override
    public void tick() {
        if (!this.isAlive()) {
            return;
        }
        super.tick();
        this.botTickCount++;

        if (this.getConfigValue(Configs.SPAWN_PHANTOM)) {
            notSleepTicks++;
        }

        if (DeerFoliaPlusConfiguration.fakePlayer.regenAmount > 0.0 && this.botTickCount % 20 == 0) {
            float health = getHealth();
            float maxHealth = getMaxHealth();
            float regenAmount = (float) (DeerFoliaPlusConfiguration.fakePlayer.regenAmount * 20);
            float amount;

            if (health < maxHealth - regenAmount) {
                amount = health + regenAmount;
            } else {
                amount = maxHealth;
            }

            this.setHealth(amount);
        }
    }

    @Override
    public void onItemPickup(@NotNull ItemEntity item) {
        super.onItemPickup(item);
        this.updateItemInHand(InteractionHand.MAIN_HAND);
    }

    public void updateItemInHand(InteractionHand hand) {
        ItemStack item = this.getItemInHand(hand);

        if (!item.isEmpty()) {
            BotUtil.replenishment(item, getInventory().items);
            if (BotUtil.isDamage(item, 10)) {
                BotUtil.replaceTool(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, this);
            }
        }
        this.detectEquipmentUpdatesPublic();
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        return super.interact(player, hand);
    }

    @Override
    public void checkFallDamage(double heightDifference, boolean onGround, @NotNull BlockState state, @NotNull BlockPos landedPosition) {
        if (onGround && this.fallDistance > 0.0F) {
            this.onChangedBlock(this.serverLevel(), landedPosition);
            double d1 = this.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);

            if ((double) this.fallDistance > d1 && !state.isAir()) {
                double d2 = this.getX();
                double d3 = this.getY();
                double d4 = this.getZ();
                BlockPos blockposition = this.blockPosition();

                if (landedPosition.getX() != blockposition.getX() || landedPosition.getZ() != blockposition.getZ()) {
                    double d5 = d2 - (double) landedPosition.getX() - 0.5D;
                    double d6 = d4 - (double) landedPosition.getZ() - 0.5D;
                    double d7 = Math.max(Math.abs(d5), Math.abs(d6));

                    d2 = (double) landedPosition.getX() + 0.5D + d5 / d7 * 0.5D;
                    d4 = (double) landedPosition.getZ() + 0.5D + d6 / d7 * 0.5D;
                }

                float f = (float) Mth.ceil((double) this.fallDistance - d1);
                double d8 = Math.min(0.2F + f / 15.0F, 2.5D);
                int i = (int) (150.0D * d8);

                // this.serverLevel().sendParticles(this, new BlockParticleOption(ParticleTypes.BLOCK, state), d2, d3, d4, i, 0.0D, 0.0D, 0.0D, 0.15000000596046448D, false); // TODO
            }
        }

        if (onGround) {
            if (this.fallDistance > 0.0F) {
                state.getBlock().fallOn(this.level(), state, landedPosition, this, this.fallDistance);
                this.level().gameEvent(GameEvent.HIT_GROUND, this.position(), GameEvent.Context.of(this, this.mainSupportingBlockPos.map((blockposition1) -> {
                    return this.level().getBlockState(blockposition1);
                }).orElse(state)));
            }

            this.resetFallDistance();
        } else if (heightDifference < 0.0D) {
            this.fallDistance -= (float) heightDifference;
        }
    }

    @Override
    public void doTick() {
        this.absMoveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());

        if (this.takeXpDelay > 0) {
            --this.takeXpDelay;
        }

        if (this.isSleeping()) {
            ++this.sleepCounter;
            if (this.sleepCounter > 100) {
                this.sleepCounter = 100;
                this.notSleepTicks = 0;
            }

            if (!this.level().isClientSide && this.level().isDay()) {
                this.stopSleepInBed(false, true);
            }
        } else if (this.sleepCounter > 0) {
            ++this.sleepCounter;
            if (this.sleepCounter >= 110) {
                this.sleepCounter = 0;
            }
        }

        this.updateIsUnderwater();

        this.addDeltaMovement(knockback);
        this.knockback = Vec3.ZERO;

        this.getBukkitEntity().taskScheduler.schedule((entity) -> this.runAction(), (entity) -> {
        }, 1L);

        this.livingEntityTick();

        this.foodData.tick(this);

        ++this.attackStrengthTicker;
        ItemStack itemstack = this.getMainHandItem();
        if (!ItemStack.matches(this.lastItemInMainHand, itemstack)) {
            if (!ItemStack.isSameItem(this.lastItemInMainHand, itemstack)) {
                this.resetAttackStrengthTicker();
            }

            this.lastItemInMainHand = itemstack.copy();
        }

        this.getCooldowns().tick();
        this.updatePlayerPose();

        if (this.hurtTime > 0) {
            this.hurtTime -= 1;
        }
    }

    @Override
    public void knockback(double strength, double x, double z, @Nullable Entity attacker, @NotNull EntityKnockbackEvent.Cause cause) {
        strength *= 1.0D - this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (strength > 0.0D) {
            Vec3 vec3d = this.getDeltaMovement();
            Vec3 vec3d1 = (new Vec3(x, 0.0D, z)).normalize().scale(strength);
            this.hasImpulse = true;
            this.knockback = new Vec3(vec3d.x / 2.0D - vec3d1.x, this.onGround() ? Math.min(0.4D, vec3d.y / 2.0D + strength) : vec3d.y, vec3d.z / 2.0D - vec3d1.z).subtract(vec3d);
        }
    }

    @Override
    public void setRot(float yaw, float pitch) {
        this.getBukkitEntity().setRotation(yaw, pitch);
    }

    @Override
    public void attack(@NotNull Entity target) {
        super.attack(target);
        this.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("isShiftKeyDown", this.isShiftKeyDown());

        CompoundTag createNbt = new CompoundTag();
        createNbt.putString("realName", this.createState.realName());
        createNbt.putString("name", this.createState.name());

        createNbt.putString("skinName", this.createState.skinName());
        if (this.createState.skin() != null) {
            ListTag skin = new ListTag();
            for (String s : this.createState.skin()) {
                skin.add(StringTag.valueOf(s));
            }
            createNbt.put("skin", skin);
        }

        nbt.put("createStatus", createNbt);

        if (!this.actions.isEmpty()) {
            ListTag actionNbt = new ListTag();
            for (BotAction<?> action : this.actions) {
                actionNbt.add(action.save(new CompoundTag()));
            }
            nbt.put("actions", actionNbt);
        }

        if (!this.configs.isEmpty()) {
            ListTag configNbt = new ListTag();
            for (BotConfig<?> config : this.configs.values()) {
                configNbt.add(config.save(new CompoundTag()));
            }
            nbt.put("configs", configNbt);
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setShiftKeyDown(nbt.getBoolean("isShiftKeyDown"));

        CompoundTag createNbt = nbt.getCompound("createStatus");
        BotCreateState.Builder createBuilder = BotCreateState.builder(createNbt.getString("realName"), null).name(createNbt.getString("name"));

        String[] skin = null;
        if (createNbt.contains("skin")) {
            ListTag skinTag = createNbt.getList("skin", 8);
            skin = new String[skinTag.size()];
            for (int i = 0; i < skinTag.size(); i++) {
                skin[i] = skinTag.getString(i);
            }
        }

        createBuilder.skinName(createNbt.getString("skinName")).skin(skin);
        createBuilder.createReason(BotCreateEvent.CreateReason.INTERNAL).creator(null);

        this.createState = createBuilder.build();
        this.gameProfile = new BotList.CustomGameProfile(this.getUUID(), this.createState.name(), this.createState.skin());


        if (nbt.contains("actions")) {
            ListTag actionNbt = nbt.getList("actions", 10);
            for (int i = 0; i < actionNbt.size(); i++) {
                CompoundTag actionTag = actionNbt.getCompound(i);
                BotAction<?> action = Actions.getForName(actionTag.getString("actionName"));
                if (action != null) {
                    BotAction<?> newAction = action.create();
                    newAction.load(actionTag);
                    this.actions.add(newAction);
                }
            }
        }

        if (nbt.contains("configs")) {
            ListTag configNbt = nbt.getList("configs", 10);
            for (int i = 0; i < configNbt.size(); i++) {
                CompoundTag configTag = configNbt.getCompound(i);
                Configs<?> configKey = Configs.getConfig(configTag.getString("configName"));
                if (configKey != null) {
                    this.configs.get(configKey).load(configTag);
                }
            }
        }
    }

    public void faceLocation(@NotNull Location loc) {
        this.look(loc.toVector().subtract(getLocation().toVector()), false);
    }

    public void look(Vector dir, boolean keepYaw) {
        float yaw, pitch;

        if (keepYaw) {
            yaw = this.getYHeadRot();
            pitch = MathUtils.fetchPitch(dir);
        } else {
            float[] vals = MathUtils.fetchYawPitch(dir);
            yaw = vals[0];
            pitch = vals[1];

            this.sendPacket(new ClientboundRotateHeadPacket(this, (byte) (yaw * 256 / 360f)));
        }

        this.setRot(yaw, pitch);
    }

    public Location getLocation() {
        return this.getBukkitEntity().getLocation();
    }

    public Entity getTargetEntity(int maxDistance, Predicate<? super Entity> predicate) {
        List<Entity> entities = this.level().getEntities((Entity) null, this.getBoundingBox(), (e -> e != this && (predicate == null || predicate.test(e))));
        if (!entities.isEmpty()) {
            return entities.getFirst();
        } else {
            EntityHitResult result = this.getBukkitEntity().rayTraceEntity(maxDistance, false);
            if (result != null && (predicate == null || predicate.test(result.getEntity()))) {
                return result.getEntity();
            }
        }
        return null;
    }

    public void dropAll() {
        this.getInventory().dropAll();
        this.detectEquipmentUpdatesPublic();
    }

    private void runAction() {
        this.actions.forEach(action -> action.tryTick(this));
        this.actions.removeIf(BotAction::isCancelled);
    }

    public boolean addBotAction(BotAction<?> action, CommandSender sender) {
        if (!new BotActionScheduleEvent(this.getBukkitEntity(), action.getName(), action.getUUID(), sender).callEvent()) {
            return false;
        }

        action.init();
        this.actions.add(action);
        return true;
    }

    public List<BotAction<?>> getBotActions() {
        return actions;
    }

    @Override
    public @NotNull ServerStatsCounter getStats() {
        return stats;
    }

    @SuppressWarnings("unchecked")
    public <E> BotConfig<E> getConfig(Configs<E> config) {
        return (BotConfig<E>) Objects.requireNonNull(this.configs.get(config));
    }

    public <E> E getConfigValue(Configs<E> config) {
        return this.getConfig(config).getValue();
    }

    @Override
    @NotNull
    public CraftBot getBukkitEntity() {
        return (CraftBot) super.getBukkitEntity();
    }
}