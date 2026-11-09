package com.tlmformation.network;

import com.tlmformation.client.LogicClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CLogicData(int expandRange, int followRange, boolean formationEnabled) {
    public static void encode(S2CLogicData msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.expandRange);
        buf.writeVarInt(msg.followRange);
        buf.writeBoolean(msg.formationEnabled);
    }

    public static S2CLogicData decode(FriendlyByteBuf buf) {
        return new S2CLogicData(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(S2CLogicData msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> LogicClientData.update(msg));
        ctx.setPacketHandled(true);
    }
}
