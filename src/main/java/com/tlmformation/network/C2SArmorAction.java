package com.tlmformation.network;

import com.tlmformation.vehicle.ArmorReinforcementManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: activate an armored-reinforcement wheel slot.
 * Slot 0 deploys (or recalls) the HAVOC; slots 1..3 are reserved.
 */
public record C2SArmorAction(int slot) {
    public static void encode(C2SArmorAction message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.slot);
    }

    public static C2SArmorAction decode(FriendlyByteBuf buffer) {
        return new C2SArmorAction(buffer.readVarInt());
    }

    public static void handle(C2SArmorAction message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            System.out.println("[Debug] 服务端收到召唤 slot=" + message.slot
                    + " sender=" + context.getSender());
            com.tlmformation.TLMFormationMod.LOGGER.info(
                    "[Armor] C2S received slot {} sender {}", message.slot, context.getSender());
            if (context.getSender() != null) {
                int slot = message.slot;
                if (slot >= 100) {
                    int action = (slot - 100) / 10;
                    int unitSlot = (slot - 100) % 10;
                    ArmorReinforcementManager.handleUnitAction(
                            context.getSender(), unitSlot, action);
                } else {
                    ArmorReinforcementManager.handle(context.getSender(), slot);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
