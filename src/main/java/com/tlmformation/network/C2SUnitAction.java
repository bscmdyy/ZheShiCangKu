package com.tlmformation.network;

import com.tlmformation.vehicle.ArmorReinforcementManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：F10 无人载具终端的单位指令。
 *
 * <p>action：1 = 设置基地，2 = 前往基地（玩家传送），3 = 撤回单位（返回基地）。</p>
 */
public record C2SUnitAction(int slot, int action) {
    public static void encode(C2SUnitAction message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.slot);
        buffer.writeVarInt(message.action);
    }

    public static C2SUnitAction decode(FriendlyByteBuf buffer) {
        return new C2SUnitAction(buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(C2SUnitAction message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                ArmorReinforcementManager.handleUnitAction(
                        context.getSender(), message.slot, message.action);
            }
        });
        context.setPacketHandled(true);
    }
}
