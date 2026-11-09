package com.tlmformation.network;

import com.tlmformation.vehicle.ArmorReinforcementManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: vehicle AI command. 0=FOLLOW, 1=PATROL, 2=PROTECT(no target yet), 3=RECALL. */
public record C2SVehicleCommand(int action) {
    public static void encode(C2SVehicleCommand message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.action);
    }

    public static C2SVehicleCommand decode(FriendlyByteBuf buffer) {
        return new C2SVehicleCommand(buffer.readVarInt());
    }

    public static void handle(C2SVehicleCommand message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                ArmorReinforcementManager.handleVehicleCommand(context.getSender(), message.action);
            }
        });
        context.setPacketHandled(true);
    }
}
