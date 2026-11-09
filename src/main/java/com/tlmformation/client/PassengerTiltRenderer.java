package com.tlmformation.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.vehicle.EntityHavoc;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 乘客/伪挂载女仆渲染时继承战车坡度倾斜角。
 *
 * <p>拦截 {@link RenderPlayerEvent.Pre} 与 {@link RenderLivingEvent.Pre}：
 * 玩家/真正乘客按 vehicle 取战车；伪挂载女仆（只锁 UUID、不是 passenger）
 * 则在附近寻找 attachedMaidUUID 匹配的战车，再叠加同一套 Pitch/Roll。</p>
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PassengerTiltRenderer {
    private PassengerTiltRenderer() {
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity().getVehicle() instanceof EntityHavoc havoc) {
            applyVehicleTilt(havoc, event.getPoseStack());
        }
    }

    @SubscribeEvent
    @SuppressWarnings("rawtypes")
    public static void onRenderLiving(RenderLivingEvent.Pre event) {
        if (event.getEntity() instanceof Player) {
            return;                       // 玩家由 RenderPlayerEvent 单独处理
        }
        // 1) 真正坐在战车上的乘客
        if (event.getEntity().getVehicle() instanceof EntityHavoc havoc) {
            applyVehicleTilt(havoc, event.getPoseStack());
            return;
        }
        // 2) 伪挂载女仆：UUID 匹配 attachedMaidUUID
        if (event.getEntity() instanceof EntityMaid maid) {
            EntityHavoc attached = findAttachedVehicle(maid);
            if (attached != null) {
                applyVehicleTilt(attached, event.getPoseStack());
            }
        }
    }

    @Nullable
    private static EntityHavoc findAttachedVehicle(EntityMaid maid) {
        if (maid.level() == null) {
            return null;
        }
        List<EntityHavoc> vehicles = maid.level().getEntitiesOfClass(EntityHavoc.class,
                maid.getBoundingBox().inflate(10.0D),
                vehicle -> vehicle.isAlive()
                        && maid.getUUID().toString().equals(vehicle.getAttachedMaidString()));
        return vehicles.isEmpty() ? null : vehicles.get(0);
    }

    /** 把战车 Pitch/Roll 应用到实体渲染矩阵（局部轴随车体 yaw 共轭）。 */
    private static void applyVehicleTilt(EntityHavoc havoc, PoseStack poseStack) {
        float pitch = havoc.getRenderPitch();
        float roll = havoc.getRenderRoll();
        if (Math.abs(pitch) < 0.01F && Math.abs(roll) < 0.01F) {
            return;
        }

        float angle = (float) Math.toRadians(180.0D - havoc.getYRot());
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        // 车体局部 X 轴（右）-> 世界方向 (cos, 0, -sin)
        poseStack.mulPose(new Quaternionf().rotationAxis(
                (float) Math.toRadians(pitch), cos, 0.0F, -sin));
        // 车体局部 Z 轴（前）-> 世界方向 (sin, 0, cos)
        poseStack.mulPose(new Quaternionf().rotationAxis(
                (float) Math.toRadians(roll), sin, 0.0F, cos));
    }
}
