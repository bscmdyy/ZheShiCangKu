package com.tlmformation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> Server：索降指令。
 *
 * <p>action：{@link #START} = 开始索降；{@link #ABORT} = 立即中止并收绳。</p>
 */
public record C2SRappelAction(int action) {
    public static final int START = 0;
    public static final int ABORT = 1;

    public static void encode(C2SRappelAction message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.action);
    }

    public static C2SRappelAction decode(FriendlyByteBuf buffer) {
        return new C2SRappelAction(buffer.readVarInt());
    }

    public static void handle(C2SRappelAction message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // 卓越前线是软依赖：未安装时协议包里的一切索降逻辑都直接忽略
            if (!ModList.get().isLoaded("superbwarfare")) {
                return;
            }
            if (context.getSender() == null) {
                return;
            }
            if (message.action == START) {
                com.tlmformation.vehicle.HelicopterRappelManager.requestStart(context.getSender());
            } else if (message.action == ABORT) {
                com.tlmformation.vehicle.HelicopterRappelManager.requestAbort(context.getSender());
            }
        });
        context.setPacketHandled(true);
    }
}
