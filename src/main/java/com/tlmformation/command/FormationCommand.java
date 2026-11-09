package com.tlmformation.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.chunk.ChunkManager;
import com.tlmformation.formation.FormationManager;
import com.tlmformation.formation.FormationSettings;
import com.tlmformation.menu.FormationMenuProvider;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.Comparator;

public final class FormationCommand {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tlmformation")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("register").executes(context -> register(context.getSource())))
                .then(Commands.literal("unregister").executes(context -> unregister(context.getSource())))
                .then(Commands.literal("recall").executes(context -> recall(context.getSource())))
                .then(Commands.literal("menu").executes(context -> menu(context.getSource()))));
    }

    private static int status(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        FormationSettings settings = FormationSettings.get(player.serverLevel());
        source.sendSuccess(() -> Component.literal(
                "阵型: " + (settings.isFormationEnabled(player) ? "开" : "关")
                        + " | 编队: " + FormationManager.getRosterSize(player)
                        + " | 常加载: " + ChunkManager.registeredCount(player)
                        + " | 跟随: " + settings.getFollowRange(player)
                        + " | 扩圈: " + settings.getExpandRange(player)), false);
        return 1;
    }

    private static int register(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        EntityMaid maid = nearest(player);
        if (maid == null) {
            source.sendFailure(Component.translatable("message.tlmformation.command.no_maid"));
            return 0;
        }
        boolean ok = ChunkManager.register(player, maid);
        source.sendSuccess(() -> Component.translatable(ok ? "message.tlmformation.command.registered" : "message.tlmformation.command.register_failed"), false);
        return ok ? 1 : 0;
    }

    private static int unregister(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        EntityMaid maid = nearest(player);
        if (maid == null) {
            source.sendFailure(Component.translatable("message.tlmformation.command.no_maid"));
            return 0;
        }
        boolean ok = ChunkManager.unregister(player, maid);
        source.sendSuccess(() -> Component.translatable(ok ? "message.tlmformation.command.unregistered" : "message.tlmformation.command.not_registered"), false);
        return ok ? 1 : 0;
    }

    private static int recall(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        int count = FormationManager.recall(player);
        source.sendSuccess(() -> Component.translatable("message.tlmformation.command.recalled", count), false);
        return count;
    }

    private static int menu(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        NetworkHooks.openScreen(player, new FormationMenuProvider(), buffer -> {
        });
        return 1;
    }

    private static EntityMaid nearest(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(EntityMaid.class,
                        player.getBoundingBox().inflate(16.0D),
                        maid -> player.getUUID().equals(maid.getOwnerUUID()))
                .stream()
                .min(Comparator.comparingDouble(maid -> maid.distanceToSqr(player)))
                .orElse(null);
    }

    public FormationCommand() {
    }
}
