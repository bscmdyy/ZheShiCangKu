package com.tlmformation.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.chunk.ChunkManager;
import com.tlmformation.formation.FormationManager;
import com.tlmformation.formation.FormationSettings;
import com.tlmformation.menu.FormationMenu;
import com.tlmformation.menu.FormationMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Comparator;
import java.util.function.Supplier;

public class C2SAction {
    private final int action;

    public C2SAction(int action) {
        this.action = action;
    }

    public static void encode(C2SAction message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.action);
    }

    public static C2SAction decode(FriendlyByteBuf buffer) {
        return new C2SAction(buffer.readVarInt());
    }

    public static void handle(C2SAction message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            handle(player, message.action);
        });
        context.setPacketHandled(true);
    }

    private static void handle(ServerPlayer player, int action) {
        FormationSettings settings = FormationSettings.get(player.serverLevel());
        switch (action) {
            case 0 -> {
                boolean enabled = !settings.isFormationEnabled(player);
                settings.setFormationEnabled(player, enabled);
                player.displayClientMessage(Component.translatable("gui.tlmformation.formation_state", Component.translatable(enabled ? "gui.tlmformation.on" : "gui.tlmformation.off")), true);
            }
            case 1 -> {
                int range = settings.getExpandRange(player) - 10;
                settings.setExpandRange(player, range);
                player.displayClientMessage(Component.translatable("gui.tlmformation.expand_short", settings.getExpandRange(player)), true);
            }
            case 2 -> {
                int range = settings.getExpandRange(player) + 10;
                settings.setExpandRange(player, range);
                player.displayClientMessage(Component.translatable("gui.tlmformation.expand_short", settings.getExpandRange(player)), true);
            }
            case 3 -> {
                EntityMaid maid = nearestMaid(player);
                if (maid != null) {
                    boolean ok = ChunkManager.register(player, maid);
                    player.displayClientMessage(Component.literal(ok
                            ? "已登记女仆区块常加载"
                            : "登记失败（已登记/已达上限/未开启）"), true);
                }
            }
            case 4 -> {
                EntityMaid maid = nearestMaid(player);
                if (maid != null) {
                    boolean ok = ChunkManager.unregister(player, maid);
                    player.displayClientMessage(Component.literal(ok
                            ? "已取消女仆区块常加载"
                            : "该女仆未登记"), true);
                }
            }
            case 5 -> {
                int count = FormationManager.recall(player);
                player.displayClientMessage(Component.translatable("message.tlmformation.command.recalled", count), true);
            }
            case 6 -> {
                settings.setFollowRange(player, settings.getFollowRange(player) - 8);
                player.displayClientMessage(Component.literal(
                        "跟随半径: " + settings.getFollowRange(player) + " 格"), true);
            }
            case 7 -> {
                settings.setFollowRange(player, settings.getFollowRange(player) + 8);
                player.displayClientMessage(Component.literal(
                        "跟随半径: " + settings.getFollowRange(player) + " 格"), true);
            }
            case 8 -> NetworkHooks.openScreen(player, new FormationMenuProvider());
            case 9 -> NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new S2CLogicData(settings.getExpandRange(player),
                            settings.getFollowRange(player),
                            settings.isFormationEnabled(player)));
            case 10 -> com.tlmformation.team.GrandAssembleManager.sync(player);
            default -> {
            }
        }

        if (player.containerMenu instanceof FormationMenu menu) {
            menu.refreshAndBroadcast(player);
        }

        NetworkHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new S2CLogicData(settings.getExpandRange(player),
                        settings.getFollowRange(player),
                        settings.isFormationEnabled(player)));
    }

    private static EntityMaid nearestMaid(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(EntityMaid.class,
                        player.getBoundingBox().inflate(16.0D),
                        maid -> player.getUUID().equals(maid.getOwnerUUID()))
                .stream()
                .min(Comparator.comparingDouble(maid -> maid.distanceToSqr(player)))
                .orElse(null);
    }
}
