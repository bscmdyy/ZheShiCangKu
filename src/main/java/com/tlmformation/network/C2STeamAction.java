package com.tlmformation.network;

import com.tlmformation.team.MaidTeamManager;
import com.tlmformation.team.TeamAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

public record C2STeamAction(TeamAction action, int team, @Nullable UUID maid) {
    public static void encode(C2STeamAction message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.action.ordinal());
        buffer.writeVarInt(message.team);
        buffer.writeBoolean(message.maid != null);
        if (message.maid != null) {
            buffer.writeUUID(message.maid);
        }
    }

    public static C2STeamAction decode(FriendlyByteBuf buffer) {
        TeamAction action = TeamAction.values()[Math.max(0,
                Math.min(buffer.readVarInt(), TeamAction.values().length - 1))];
        int team = buffer.readVarInt();
        UUID maid = buffer.readBoolean() ? buffer.readUUID() : null;
        return new C2STeamAction(action, team, maid);
    }

    public static void handle(C2STeamAction message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                MaidTeamManager.handle(context.getSender(), message.action,
                        message.team, message.maid);
            }
        });
        context.setPacketHandled(true);
    }
}
