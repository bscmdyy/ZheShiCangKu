package com.tlmformation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client：黑鹰索降状态。
 *
 * <p>同步「阶段」+「剩余下滑人数」+「悬停锁点」。</p>
 *
 * <p>锁点必须一起同步：索降期间机身位置由服务端每 tick 钉死在锁点上，
 * 而客户端卓越前线的 {@code lerpTo} 会把位置当作插值目标、且它的悬停/盘旋补偿
 * 仍在客户端运行 —— 只靠位置包的话，客户端会自己上下漂，视觉上就是
 * 「放下绳索时直升机从高到低、从低到高来回抽触」。客户端拿到锁点后同样钉死即可。</p>
 */
public record S2CRappelState(int entityId, int phase, int remain,
                             double lockX, double lockY, double lockZ) {
    public static void encode(S2CRappelState message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
        buffer.writeVarInt(message.phase);
        buffer.writeVarInt(message.remain);
        buffer.writeDouble(message.lockX);
        buffer.writeDouble(message.lockY);
        buffer.writeDouble(message.lockZ);
    }

    public static S2CRappelState decode(FriendlyByteBuf buffer) {
        return new S2CRappelState(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static void handle(S2CRappelState message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.tlmformation.client.helicopter.RappelClientState.update(
                            message.entityId, message.phase, message.remain,
                            message.lockX, message.lockY, message.lockZ)));
        }
        context.setPacketHandled(true);
    }
}
