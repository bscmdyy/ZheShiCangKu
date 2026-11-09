package com.tlmformation.network;

import com.tlmformation.client.GrandModeClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CGrandMode(boolean active) {
    public static void encode(S2CGrandMode msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
    }

    public static S2CGrandMode decode(FriendlyByteBuf buf) {
        return new S2CGrandMode(buf.readBoolean());
    }

    public static void handle(S2CGrandMode msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> GrandModeClient.setActive(msg.active));
        ctx.setPacketHandled(true);
    }
}
