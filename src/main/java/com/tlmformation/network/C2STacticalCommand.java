package com.tlmformation.network;

import com.tlmformation.tactical.TacticalCommand;
import com.tlmformation.tactical.TacticalManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public record C2STacticalCommand(TacticalCommand command, @Nullable BlockPos target) {
    public static void encode(C2STacticalCommand message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.command.ordinal());
        buffer.writeBoolean(message.target != null);
        if (message.target != null) {
            buffer.writeBlockPos(message.target);
        }
    }

    public static C2STacticalCommand decode(FriendlyByteBuf buffer) {
        TacticalCommand command = TacticalCommand.byOrdinal(buffer.readVarInt());
        BlockPos target = buffer.readBoolean() ? buffer.readBlockPos() : null;
        return new C2STacticalCommand(command, target);
    }

    public static void handle(C2STacticalCommand message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                TacticalManager.handle(context.getSender(), message.command, message.target);
            }
        });
        context.setPacketHandled(true);
    }
}
