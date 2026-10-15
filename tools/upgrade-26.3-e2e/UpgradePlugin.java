package upgrade;

import cn.lunadeer.mc.deerfolia.DeerFoliaConfiguration;
import cn.lunadeer.mc.deerfolia.async.path.AsyncPath;
import cn.lunadeer.mc.deerfolia.async.path.AsyncPathProcessor;
import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import cn.lunadeer.mc.deerfoliaplus.posture.PostureManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftMob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.leavesmc.leaves.bot.BotList;

/** Local integration assertions. Entity/world operations always execute on the owner's region. */
public final class UpgradePlugin extends JavaPlugin implements CommandExecutor {
    private void pass(String name) {getLogger().info("PLUS_PASS " + name);}
    private void check(boolean value,String name) {if(!value) throw new AssertionError(name);pass(name);}
    private void guarded(Runnable runnable) {try {runnable.run();} catch(Throwable t) {getLogger().log(java.util.logging.Level.SEVERE,"PLUS_FAIL",t);}}
    private void later(Player player,long ticks,Runnable action) {player.getScheduler().runDelayed(this,t -> guarded(action),() -> getLogger().severe("PLUS_FAIL retired-player"),ticks);}
    private static Object field(Object object,String name) {
        try {for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass()) {try {var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);} catch(NoSuchFieldException ignored) {}}throw new NoSuchFieldException(name);} catch(Exception e) {throw new RuntimeException(e);}
    }
    @Override public void onEnable() {
        getCommand("pluse2e").setExecutor(this);
        guarded(() -> {
            check(Bukkit.getName().equals("DeerFoliaPlus"),"branding");
            check(Bukkit.getMinecraftVersion().equals("26.3"),"minecraft-26.3");
            check(DeerFoliaPlusConfiguration.fakePlayer.enable && DeerFoliaPlusConfiguration.fakePlayer.residentBot,"fakeplayer-config");
            check(DeerFoliaPlusConfiguration.posture.enabled && DeerFoliaPlusConfiguration.customRecipe.enabled,"plus-enabled-config");
            check(DeerFoliaConfiguration.lithiumOptimize.enabled && DeerFoliaConfiguration.lithiumOptimize.blockEntitySleeping,"inherited-lithium-config");
            pass("plugin-enabled");
        });
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(args.length<2)return false;
        Player player=Bukkit.getPlayerExact(args[1]);
        if(player==null) {getLogger().severe("PLUS_FAIL missing-player");return true;}
        later(player,4,() -> {
            check(Bukkit.isOwnedByCurrentRegion(player),"owner-"+args[0]);
            switch(args[0]) {
                case "setup" -> setup(player);
                case "commands" -> {
                    check(player.getAllowFlight(),"fly-command");
                    check(player.isInvulnerable(),"god-command");
                    check(player.getInventory().getItemInMainHand().getAmount()==17,"more-command");
                }
                case "god-temporary-setup" -> {
                    var nms=((org.bukkit.craftbukkit.entity.CraftPlayer)player).getHandle();
                    nms.setPermanentlyInvulnerable(false);nms.setInvulnerableTime(200);
                    check(nms.isInvulnerable()&&!nms.isPermanentlyInvulnerable(),"god-temporary-fixture");
                }
                case "god-permanent" -> {
                    var nms=((org.bukkit.craftbukkit.entity.CraftPlayer)player).getHandle();
                    check(nms.isPermanentlyInvulnerable()==Boolean.parseBoolean(args[2]) && nms.getInvulnerableTime()>0,"god-toggle-during-temporary-"+args[2]);
                }
                case "god-cleanup" -> {
                    var nms=((org.bukkit.craftbukkit.entity.CraftPlayer)player).getHandle();
                    nms.setInvulnerableTime(0);nms.setPermanentlyInvulnerable(true);
                }
                case "bot-face-down" -> {
                    var bot=BotList.INSTANCE.getBotByName("UpgradeBot");
                    check(bot!=null && Bukkit.isOwnedByCurrentRegion(bot.getBukkitEntity()),"bot-face-owner");
                    bot.setRot(0,90);
                }
                case "bot-knockback" -> {
                    var bot=BotList.INSTANCE.getBotByName("UpgradeBot");
                    check(bot!=null && Bukkit.isOwnedByCurrentRegion(bot.getBukkitEntity()),"bot-knockback-owner");
                    bot.syncVelocity=false;
                    bot.knockback(.2,1,0,bot.damageSources().generic(),1,false,null,io.papermc.paper.event.entity.EntityKnockbackEvent.Cause.ENTITY_ATTACK);
                    check(bot.syncVelocity,"bot-knockback-marks-velocity-sync");
                }
                case "hat" -> check(player.getInventory().getHelmet()!=null && player.getInventory().getHelmet().getType()==Material.DIAMOND,"hat-command");
                case "menu" -> {check(player.getOpenInventory().getType().name().equals(args[2]),"menu-"+args[2]);player.closeInventory();}
                case "posture" -> {
                    var session=PostureManager.getInstance().getSession(player);
                    check(args[2].equals("NONE")?session==null:session!=null&&session.type().name().equals(args[2]),"posture-"+args[2]);
                }
                case "position" -> {
                    var at=player.getLocation();
                    check(Math.abs(at.getX()-Double.parseDouble(args[2]))<1.0E-4 && Math.abs(at.getY()-Double.parseDouble(args[3]))<1.0E-4 && Math.abs(at.getZ()-Double.parseDouble(args[4]))<1.0E-4,"tcp-position-"+String.join("-",args));
                }
                case "rotation" -> {
                    var at=player.getLocation();
                    check(Math.abs(at.getYaw()-Float.parseFloat(args[2]))<.01 && Math.abs(at.getPitch()-Float.parseFloat(args[3]))<.01,"tcp-look-only-rotation");
                }
                case "time" -> check(Math.abs(player.getWorld().getTime()-Long.parseLong(args[2]))<100,"time-"+args[2]);
                case "weather" -> check(player.getWorld().hasStorm()==Boolean.parseBoolean(args[2]),"weather-"+args[2]);
                case "bot-created" -> botCreated(player);
                case "bot-action" -> {
                    var bot=BotList.INSTANCE.getBotByName("UpgradeBot");
                    check(bot!=null && Bukkit.isOwnedByCurrentRegion(bot.getBukkitEntity()),"bot-action-owner");
                    check(bot.isShiftKeyDown(),"bot-sneak-action");
                }
                case "bot-saved" -> {awaitBotSaved(player,40);return;}
                case "bot-restored" -> botRestored(player);
                case "bot-removed" -> check(BotList.INSTANCE.getBotByName("UpgradeBot")==null,"bot-remove");
                case "restart" -> {botRestored(player);check(player.getWorld().getBlockAt(8,95,8).getType()==Material.DIAMOND_BLOCK,"world-restart-persistence");}
                default -> throw new IllegalArgumentException(args[0]);
            }
            if(!args[0].equals("setup"))pass("complete-"+String.join("-",args));
        });
        return true;
    }
    private void setup(Player player) {
        player.setGameMode(GameMode.SURVIVAL);player.setGravity(false);player.setInvulnerable(false);player.setAllowFlight(false);
        player.teleportAsync(new Location(player.getWorld(),8.5,96,8.5)).thenAccept(ok -> later(player,30,() -> {
            check(ok,"setup-teleport");
            var level=((CraftWorld)player.getWorld()).getHandle();
            for(int x=-16;x<=31;x+=16)for(int z=-16;z<=31;z+=16)check(Bukkit.isOwnedByCurrentRegion(new Location(player.getWorld(),x,95,z)),"platform-owner-"+x+"-"+z);
            for(int x=-16;x<=31;x++)for(int z=-16;z<=31;z++)level.setBlock(new BlockPos(x,95,z),Blocks.STONE.defaultBlockState(),18);
            level.setBlock(new BlockPos(8,95,8),Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
            player.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.DIAMOND));
            recipes(player);
            inherited(player);
            pass("complete-setup-"+player.getName());
        }));
    }
    private void recipes(Player player) {
        var manager=MinecraftServer.getServer().getRecipeManager();
        for(String type:List.of("shaped","shapeless","smelting","blasting","smoking","campfire_cooking","stonecutting")) {
            var holder=manager.byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse("deerfoliaplus:upgrade_"+type)));
            check(holder.isPresent(),"recipe-registered-"+type);
            var buffer=org.leavesmc.leaves.protocol.core.ProtocolUtils.decorate(io.netty.buffer.Unpooled.buffer());
            try {
                net.minecraft.world.item.crafting.RecipeHolder.STREAM_CODEC.encode(buffer,holder.orElseThrow());
                var decoded=net.minecraft.world.item.crafting.RecipeHolder.STREAM_CODEC.decode(buffer);
                check(decoded.id().equals(holder.orElseThrow().id()) && decoded.value().getType()==holder.orElseThrow().value().getType() && !buffer.isReadable(),"recipe-holder-codec-"+type);
                buffer.clear();
                @SuppressWarnings("rawtypes") var codec=(net.minecraft.network.codec.StreamCodec)holder.orElseThrow().value().getSerializer().streamCodec();
                codec.encode(buffer,holder.orElseThrow().value());
                var decodedRecipe=(net.minecraft.world.item.crafting.Recipe<?>)codec.decode(buffer);
                check(decodedRecipe.getType()==holder.orElseThrow().value().getType() && !buffer.isReadable(),"recipe-serializer-codec-"+type);
            } finally {buffer.release();}
            if(!type.equals("shaped") && !type.equals("shapeless")) {
                @SuppressWarnings("unchecked") var single=(net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.SingleRecipeInput>)holder.orElseThrow().value();
                var singleInput=new net.minecraft.world.item.crafting.SingleRecipeInput(new net.minecraft.world.item.ItemStack(Items.DIRT));
                check(single.matches(singleInput,((CraftWorld)player.getWorld()).getHandle()),"recipe-single-input-matches-"+type);
                check(single.assemble(singleInput).is(Items.EMERALD),"recipe-single-output-"+type);
            }
        }
        var recipe=(CraftingRecipe)manager.byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse("deerfoliaplus:upgrade_shaped"))).orElseThrow().value();
        var input=CraftingInput.of(1,1,List.of(new net.minecraft.world.item.ItemStack(Items.DIRT)));
        var level=((CraftWorld)player.getWorld()).getHandle();
        check(recipe.matches(input,level),"custom-shaped-matches");
        var result=recipe.assemble(input);
        check(result.is(Items.DIAMOND)&&result.getCount()==2,"custom-shaped-output");
        var chained=(CraftingRecipe)manager.byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse("deerfoliaplus:upgrade_shapeless"))).orElseThrow().value();
        check(chained.matches(CraftingInput.of(1,1,List.of(result)),level),"custom-id-chain-matches");
        check(!chained.matches(CraftingInput.of(1,1,List.of(new net.minecraft.world.item.ItemStack(Items.DIAMOND))),level),"custom-id-rejects-vanilla");
    }
    private void inherited(Player player) {
        var level=((CraftWorld)player.getWorld()).getHandle();
        var collector=new net.minecraft.world.entity.InsideBlockEffectApplier.StepBasedCollector();
        collector.advanceStep(1,new BlockPos(8,96,8));
        collector.applyAndClear(((org.bukkit.craftbukkit.entity.CraftPlayer)player).getHandle());
        check(field(collector,"effectsInStep")==null,"lithium-empty-effects-lazy");
        BlockPos crafterPos=new BlockPos(12,96,12);
        level.setBlock(crafterPos,Blocks.CRAFTER.defaultBlockState(),3);
        later(player,25,() -> {
            var blockEntity=level.getBlockEntity(crafterPos);
            check(blockEntity!=null && blockEntity.deerfoliaIsSleeping(),"lithium-crafter-sleeps");
            var crafter=(org.bukkit.block.Crafter)player.getWorld().getBlockAt(12,96,12).getState(false);
            crafter.setCraftingTicks(30);
            check(!blockEntity.deerfoliaIsSleeping(),"lithium-crafter-api-wakes");
            later(player,40,() -> {check(crafter.getCraftingTicks()==0 && blockEntity.deerfoliaIsSleeping(),"lithium-crafter-ticks-resleep");pass("inherited-sleep-complete");});
        });
        var cow=player.getWorld().spawn(new Location(player.getWorld(),-7.5,96,-7.5),org.bukkit.entity.Cow.class);cow.setPersistent(true);
        check(Bukkit.isOwnedByCurrentRegion(cow),"spawned-cow-owner");
        var initialMob=((CraftMob)cow).getHandle();
        ((net.minecraft.world.entity.ai.goal.GoalSelector)field(initialMob,"goalSelector")).removeAllGoals(goal -> true);
        initialMob.targetSelector.removeAllGoals(goal -> true);
        cow.getScheduler().runDelayed(this,t -> guarded(() -> {
            check(Bukkit.isOwnedByCurrentRegion(cow),"path-request-owner");
            var mob=((CraftMob)cow).getHandle();
            var start=cow.getLocation();
            var destination=new Location(cow.getWorld(),-4.5,96,-7.5);
            var path=mob.getNavigation().createPath(new BlockPos(-5,96,-8),0,4);
            check(path instanceof AsyncPath,"inherited-safe-async-path");
            AsyncPathProcessor.awaitProcessing(mob,path,processed -> guarded(() -> {
                check(Bukkit.isOwnedByCurrentRegion(cow)&&processed!=null&&processed.isProcessed()&&processed.getNodeCount()>0,"async-owner-completion");
                mob.getNavigation().moveTo(processed,1);
                cow.getScheduler().runDelayed(this,task -> guarded(() -> {
                    check(Bukkit.isOwnedByCurrentRegion(cow),"path-movement-owner");
                    check(cow.isValid()&&cow.getLocation().distanceSquared(start)>.1 && cow.getLocation().distanceSquared(destination)<start.distanceSquared(destination)-.1,"async-navigation-moves");
                    cow.remove();pass("inherited-path-complete");
                }),() -> getLogger().severe("PLUS_FAIL retired-cow"),50);
            }));
        }),() -> getLogger().severe("PLUS_FAIL retired-cow"),10);
    }
    private void awaitBotSaved(Player player,int attempts) {
        if(BotList.INSTANCE.getBotByName("UpgradeBot")==null && BotList.INSTANCE.getSavedBotList().contains("UpgradeBot")) {
            pass("bot-save-unloads");pass("complete-bot-saved-"+player.getName());return;
        }
        if(attempts<=0)throw new AssertionError("bot-save-timeout");
        later(player,5,() -> awaitBotSaved(player,attempts-1));
    }
    private void botCreated(Player player) {
        var bot=BotList.INSTANCE.getBotByName("UpgradeBot");
        check(bot!=null,"bot-create-command");
        check(Bukkit.isOwnedByCurrentRegion(bot.getBukkitEntity()),"bot-owner");
        check(player.getUniqueId().equals(bot.createPlayer),"bot-creator");
        bot.getBukkitEntity().getInventory().setItem(0,new org.bukkit.inventory.ItemStack(Material.EMERALD,7));
        bot.getBukkitEntity().getInventory().setItemInOffHand(new org.bukkit.inventory.ItemStack(Material.STICK));
        bot.getMainHandItem().set(net.minecraft.core.component.DataComponents.ATTACK_ANIMATION,new net.minecraft.world.item.component.SwingAnimation(net.minecraft.world.item.SwingAnimationType.STAB,9));
        bot.getMainHandItem().set(net.minecraft.core.component.DataComponents.INTERACT_ANIMATION,new net.minecraft.world.item.component.SwingAnimation(net.minecraft.world.item.SwingAnimationType.WHACK,4));
        bot.getOffhandItem().set(net.minecraft.core.component.DataComponents.INTERACT_ANIMATION,new net.minecraft.world.item.component.SwingAnimation(net.minecraft.world.item.SwingAnimationType.STAB,3));
        pass("bot-entity-id-"+bot.getId());
    }
    private void botRestored(Player player) {
        var bot=BotList.INSTANCE.getBotByName("UpgradeBot");
        check(bot!=null,"bot-present-after-load");
        check(Bukkit.isOwnedByCurrentRegion(bot.getBukkitEntity()),"restored-bot-owner");
        check(player.getUniqueId().equals(bot.createPlayer),"bot-creator-persistence");
        check(bot.isShiftKeyDown(),"bot-sneak-persistence");
        var item=bot.getBukkitEntity().getInventory().getItem(0);
        check(item!=null&&item.getType()==Material.EMERALD&&item.getAmount()==7,"bot-inventory-persistence");
    }
}
