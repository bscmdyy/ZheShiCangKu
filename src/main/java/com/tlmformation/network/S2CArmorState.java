package com.tlmformation.network;

import com.tlmformation.client.ArmorClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: deployed-slot bitmask + current follow distance.
 */
public record S2CArmorState(int deployedMask, int followDistance, boolean unlocked, int ammo) {
    public static void encode(S2CArmorState message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.deployedMask);
        buffer.writeVarInt(message.followDistance);
        buffer.writeBoolean(message.unlocked);
        buffer.writeVarInt(message.ammo);
    }

    public static S2CArmorState decode(FriendlyByteBuf buffer) {
        return new S2CArmorState(buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readVarInt());
    }

    public static void handle(S2CArmorState message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> {
                com.tlmformation.TLMFormationMod.LOGGER.info(
                        "[Armor] S2C state mask {} followDistance {}",
                        message.deployedMask, message.followDistance);
                ArmorClientData.update(message.deployedMask, message.followDistance,
                        message.unlocked, message.ammo);
            });
        }
        context.setPacketHandled(true);
    }
}
