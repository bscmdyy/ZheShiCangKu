package com.tlmformation.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client：战车自定义特效。
 * type: 0 = 曳光弹（a=炮口, b=命中点）, 1 = 枪口闪光（a=炮口）, 2 = 命中爆炸（a=命中点）。
 */
public record S2CHavocEffect(int type,
                             double ax, double ay, double az,
                             double bx, double by, double bz) {
    public static void encode(S2CHavocEffect message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.type);
        buffer.writeDouble(message.ax);
        buffer.writeDouble(message.ay);
        buffer.writeDouble(message.az);
        buffer.writeDouble(message.bx);
        buffer.writeDouble(message.by);
        buffer.writeDouble(message.bz);
    }

    public static S2CHavocEffect decode(FriendlyByteBuf buffer) {
        return new S2CHavocEffect(buffer.readVarInt(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public static void handle(S2CHavocEffect message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.tlmformation.client.HavocEffectRenderer.add(
                            message.type,
                            new net.minecraft.world.phys.Vec3(message.ax, message.ay, message.az),
                            new net.minecraft.world.phys.Vec3(message.bx, message.by, message.bz))));
        }
        context.setPacketHandled(true);
    }
}
