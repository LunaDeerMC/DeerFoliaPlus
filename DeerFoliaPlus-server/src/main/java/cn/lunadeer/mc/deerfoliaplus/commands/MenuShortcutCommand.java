package cn.lunadeer.mc.deerfoliaplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;

public final class MenuShortcutCommand {

    private MenuShortcutCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, "enderchest", MenuCommandPermissions.ENDERCHEST, MenuShortcutCommand::openEnderChest);
        register(dispatcher, "workbench", MenuCommandPermissions.WORKBENCH, MenuShortcutCommand::openCraftingTable);
        register(dispatcher, "craftingtable", MenuCommandPermissions.CRAFTING_TABLE, MenuShortcutCommand::openCraftingTable);
        register(dispatcher, "stonecutter", MenuCommandPermissions.STONECUTTER, MenuShortcutCommand::openStonecutter);
        register(dispatcher, "loom", MenuCommandPermissions.LOOM, MenuShortcutCommand::openLoom);
        register(dispatcher, "grindstone", MenuCommandPermissions.GRINDSTONE, MenuShortcutCommand::openGrindstone);
        register(dispatcher, "anvil", MenuCommandPermissions.ANVIL, MenuShortcutCommand::openAnvil);
        register(dispatcher, "cartographytable", MenuCommandPermissions.CARTOGRAPHY_TABLE, MenuShortcutCommand::openCartographyTable);
        register(dispatcher, "smithingtable", MenuCommandPermissions.SMITHING_TABLE, MenuShortcutCommand::openSmithingTable);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String literal, String permission, CommandExecutor executor) {
        dispatcher.register(
                Commands.literal(literal)
                        .requires(source -> source.getBukkitSender().hasPermission(permission))
                        .executes(ctx -> executor.run(ctx.getSource()))
        );
    }

    private static int openEnderChest(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEnderChestContainer enderChestInventory = player.getEnderChestInventory();

        if (player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, menuPlayer) -> ChestMenu.threeRows(containerId, inventory, enderChestInventory),
                        Component.translatable("container.enderchest")
                )
        ).isPresent()) {
            player.awardStat(Stats.OPEN_ENDERCHEST);
            return 1;
        }

        return 0;
    }

    private static int openCraftingTable(CommandSourceStack source) throws CommandSyntaxException {
        return openContainerMenu(
                source,
                Component.translatable("container.crafting"),
                Stats.INTERACT_WITH_CRAFTING_TABLE,
                (containerId, inventory, access) -> new CraftingMenu(containerId, inventory, access)
        );
    }

    private static int openStonecutter(CommandSourceStack source) throws CommandSyntaxException {
        return openContainerMenu(
                source,
                Component.translatable("container.stonecutter"),
                Stats.INTERACT_WITH_STONECUTTER,
                (containerId, inventory, access) -> new StonecutterMenu(containerId, inventory, access)
        );
    }

    private static int openLoom(CommandSourceStack source) throws CommandSyntaxException {
        return openContainerMenu(
                source,
                Component.translatable("container.loom"),
                Stats.INTERACT_WITH_LOOM,
                (containerId, inventory, access) -> new LoomMenu(containerId, inventory, access)
        );
    }

    private static int openGrindstone(CommandSourceStack source) throws CommandSyntaxException {
        return openContainerMenu(
                source,
                Component.translatable("container.grindstone_title"),
                Stats.INTERACT_WITH_GRINDSTONE,
                (containerId, inventory, access) -> new GrindstoneMenu(containerId, inventory, access)
        );
    }

    private static int openAnvil(CommandSourceStack source) throws CommandSyntaxException {
        return openContainerMenu(
                source,
                Component.translatable("container.repair"),
                Stats.INTERACT_WITH_ANVIL,
                (containerId, inventory, access) -> new AnvilMenu(containerId, inventory, access)
        );
    }

    private static int openCartographyTable(CommandSourceStack source) throws CommandSyntaxException {
        return openContainerMenu(
                source,
                Component.translatable("container.cartography_table"),
                Stats.INTERACT_WITH_CARTOGRAPHY_TABLE,
                (containerId, inventory, access) -> new CartographyTableMenu(containerId, inventory, access)
        );
    }

    private static int openSmithingTable(CommandSourceStack source) throws CommandSyntaxException {
        return openContainerMenu(
                source,
                Component.translatable("container.upgrade"),
                Stats.INTERACT_WITH_SMITHING_TABLE,
                (containerId, inventory, access) -> new SmithingMenu(containerId, inventory, access)
        );
    }

    private static int openContainerMenu(
            CommandSourceStack source,
            Component title,
            net.minecraft.resources.Identifier stat,
            MenuFactory factory
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos blockPos = player.blockPosition();
        ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), blockPos);

        if (player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> factory.create(containerId, inventory, access),
                title
        )).isPresent()) {
            player.awardStat(stat);
            return 1;
        }

        return 0;
    }

    @FunctionalInterface
    private interface CommandExecutor {
        int run(CommandSourceStack source) throws CommandSyntaxException;
    }

    @FunctionalInterface
    private interface MenuFactory {
        net.minecraft.world.inventory.AbstractContainerMenu create(int containerId, net.minecraft.world.entity.player.Inventory inventory, ContainerLevelAccess access);
    }
}