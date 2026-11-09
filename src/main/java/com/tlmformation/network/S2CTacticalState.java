package com.tlmformation.network;

import com.tlmformation.client.TacticalClientState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public record S2CTacticalState(Map<Integer, Integer> states, @Nullable BlockPos marker,
                               int lockedTargetEntityId, int commandOrdinal) {
    public static void encode(S2CTacticalState message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.states.size());
        for (Map.Entry<Integer, Integer> entry : message.states.entrySet()) {
            buffer.writeVarInt(entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
        buffer.writeBoolean(message.marker != null);
        if (message.marker != null) {
            buffer.writeBlockPos(message.marker);
        }
        buffer.writeVarInt(message.lockedTargetEntityId);
        buffer.writeVarInt(message.commandOrdinal);
    }

    public static S2CTacticalState decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<Integer, Integer> states = new HashMap<>();
        for (int i = 0; i < size; i++) {
            states.put(buffer.readVarInt(), buffer.readVarInt());
        }
        BlockPos marker = buffer.readBoolean() ? buffer.readBlockPos() : null;
        int lockedTarget = buffer.readVarInt();
        int commandOrdinal = buffer.readVarInt();
        return new S2CTacticalState(states, marker, lockedTarget, commandOrdinal);
    }

    public static void handle(S2CTacticalState message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> TacticalClientState.update(
                    message.states, message.marker, message.lockedTargetEntityId,
                    message.commandOrdinal));
        }
        context.setPacketHandled(true);
    }
}
