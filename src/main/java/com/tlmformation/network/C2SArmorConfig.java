package com.tlmformation.network;

import com.tlmformation.vehicle.ArmorReinforcementManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: set the armored reinforcement "follow distance" (10~50).
 */
public record C2SArmorConfig(int followDistance) {
    public static void encode(C2SArmorConfig message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.followDistance);
    }

    public static C2SArmorConfig decode(FriendlyByteBuf buffer) {
        return new C2SArmorConfig(buffer.readVarInt());
    }

    public static void handle(C2SArmorConfig message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                ArmorReinforcementManager.handleConfig(context.getSender(),
                        message.followDistance);
            }
        });
        context.setPacketHandled(true);
    }
}
