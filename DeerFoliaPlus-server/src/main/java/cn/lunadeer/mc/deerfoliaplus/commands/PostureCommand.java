package cn.lunadeer.mc.deerfoliaplus.commands;

import cn.lunadeer.mc.deerfoliaplus.configurations.DeerFoliaPlusConfiguration;
import cn.lunadeer.mc.deerfoliaplus.posture.PostureService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PostureCommand {

    private PostureCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("sit")
                        .requires(source -> source.getBukkitSender().hasPermission(PostureCommandPermissions.SIT))
                        .executes(ctx -> sit(ctx.getSource()))
        );
        dispatcher.register(
                Commands.literal("lay")
                        .requires(source -> source.getBukkitSender().hasPermission(PostureCommandPermissions.LAY))
                        .executes(ctx -> lay(ctx.getSource()))
        );
        dispatcher.register(
                Commands.literal("crawl")
                        .requires(source -> source.getBukkitSender().hasPermission(PostureCommandPermissions.CRAWL))
                        .executes(ctx -> crawl(ctx.getSource()))
        );
        dispatcher.register(
                Commands.literal("get-up")
                        .requires(source -> source.getBukkitSender().hasPermission(PostureCommandPermissions.GET_UP))
                        .executes(ctx -> getUp(ctx.getSource()))
        );
    }

    private static int sit(CommandSourceStack source) throws CommandSyntaxException {
        if (!DeerFoliaPlusConfiguration.posture.enabled) {
            source.sendFailure(Component.literal("Posture feature is disabled"));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        player.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> {
            ServerPlayer scheduledPlayer = (ServerPlayer) nmsEntity;
            boolean success = PostureService.getInstance().sitAtCurrentPosition(scheduledPlayer.getBukkitEntity());
            if (!success) {
                scheduledPlayer.sendSystemMessage(Component.literal("Unable to sit down here"));
            }
        }, null, 1L);
        source.sendSuccess(() -> Component.literal("Sitting down..."), false);
        return 1;
    }

    private static int lay(CommandSourceStack source) throws CommandSyntaxException {
        if (!DeerFoliaPlusConfiguration.posture.enabled) {
            source.sendFailure(Component.literal("Posture feature is disabled"));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        player.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> {
            ServerPlayer scheduledPlayer = (ServerPlayer) nmsEntity;
            boolean success = PostureService.getInstance().startLay(scheduledPlayer.getBukkitEntity());
            if (!success) {
                scheduledPlayer.sendSystemMessage(Component.literal("Unable to lie down right now"));
            }
        }, null, 1L);
        source.sendSuccess(() -> Component.literal("Lying down..."), false);
        return 1;
    }

    private static int crawl(CommandSourceStack source) throws CommandSyntaxException {
        if (!DeerFoliaPlusConfiguration.posture.enabled) {
            source.sendFailure(Component.literal("Posture feature is disabled"));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        player.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> {
            ServerPlayer scheduledPlayer = (ServerPlayer) nmsEntity;
            boolean success = PostureService.getInstance().startCrawl(scheduledPlayer.getBukkitEntity());
            if (!success) {
                scheduledPlayer.sendSystemMessage(Component.literal("Unable to crawl right now"));
            }
        }, null, 1L);
        source.sendSuccess(() -> Component.literal("Crawling..."), false);
        return 1;
    }

    private static int getUp(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> {
            ServerPlayer scheduledPlayer = (ServerPlayer) nmsEntity;
            PostureService.getInstance().getUp(scheduledPlayer.getBukkitEntity());
        }, null, 1L);
        source.sendSuccess(() -> Component.literal("Standing up..."), false);
        return 1;
    }
}
