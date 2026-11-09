package com.tlmformation.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tlmformation.config.HudConfig;
import com.tlmformation.vehicle.EntityHelicopter;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.ModList;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 直升机上的女仆渲染修正（客户端）。
 *
 * <p>两个问题都在这里解决：</p>
 * <ol>
 *   <li><b>坐姿偏高</b>：TLM 的坐姿高矮由模型包自带的 {@code vehicle$<实体id>} 条件动画决定，
 *       酒狐 / YSM 等自定义模型没有给本模组直升机写动画，会回退成站立待机。
 *       这里对"自定义模型"整体下沉 {@link HudConfig#heliMaidSeatDrop()} 格。</li>
 *   <li><b>机身漏出</b>：卓越前线的直升机横滚/俯仰是动态的，而 TLM 渲染女仆时不带载具旋转，
 *       急转时女仆保持竖直就穿出机身。这里把载具四元数里的<b>倾斜分量</b>（不含朝向）
 *       套到渲染矩阵上，让女仆跟随机身一起滚转/俯仰。
 *       朝向仍由 TLM 自己的逻辑处理，避免和载具 yaw 叠加成双旋转。</li>
 * </ol>
 */
@Mixin(EntityMaidRenderer.class)
public abstract class EntityMaidHeliSeatMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void tlmf$heliMaidPose(Mob entity, float entityYaw, float partialTick,
                                   PoseStack poseStack, MultiBufferSource bufferSource,
                                   int packedLight, CallbackInfo ci) {
        // 卓越前线是软依赖：没装时本模组不会有直升机实体
        if (!ModList.get().isLoaded("superbwarfare")) {
            return;
        }
        if (!(entity.getVehicle() instanceof EntityHelicopter helicopter)) {
            return;
        }
        // 1) 跟随载具横滚 / 俯仰（只取倾斜，朝向交给 TLM）
        if (HudConfig.heliMaidFollowTilt()) {
            tlmf$applyVehicleTilt(helicopter, partialTick, poseStack);
        }
        // 1.5) 两侧舱门座位朝外坐：按座位 Orientation 绕竖轴补旋转
        tlmf$applySeatOrientation(helicopter, entity, poseStack);
        // 2) 坐姿下沉：只针对自定义模型（GeckoLib / YSM），TLM 内置模型不动
        if (tlmf$isCustomModel(entity)) {
            double drop = HudConfig.heliMaidSeatDrop();
            if (drop != 0.0D) {
                // 在下沉前已经套了倾斜，所以这里沿"机身的向下"平移
                poseStack.translate(0.0D, -drop, 0.0D);
            }
        }
    }

    /** 座位 Orientation（度，0 = 朝前，±90 = 两侧朝外）。 */
    @Unique
    private static void tlmf$applySeatOrientation(EntityHelicopter helicopter, Mob entity,
                                                  PoseStack poseStack) {
        float orientation = 0.0F;
        try {
            int seat = helicopter.getSeatIndex(entity);
            var seats = helicopter.computed().seats();
            if (seat >= 0 && seat < seats.size()) {
                var info = seats.get(seat);
                if (info != null) {
                    orientation = info.getOrientation();
                }
            }
        } catch (Throwable ignored) {
            return;
        }
        if (Math.abs(orientation) > 1.0E-3F) {
            // 先朝外（座位的朝向），再由上一层车身倾斜统一摆正
            poseStack.mulPose(Axis.YP.rotationDegrees(-orientation));
        }
    }

    /**
     * 从载具四元数里取出"把世界向上向量掰到机身向上向量"的最短旋转，
     * 即只有横滚/俯仰、不含 yaw 的倾斜量。
     */
    @Unique
    private static void tlmf$applyVehicleTilt(EntityHelicopter helicopter, float partialTick,
                                              PoseStack poseStack) {
        Quaterniond rotation;
        try {
            rotation = helicopter.getRotationFromString("Vehicle", partialTick);
        } catch (Throwable ignored) {
            return;
        }
        if (rotation == null) {
            return;
        }
        Vector3d up = new Vector3d(0.0D, 1.0D, 0.0D).rotate(rotation);
        if (up.lengthSquared() < 1.0E-8D) {
            return;
        }
        up.normalize();

        double dot = Math.max(-1.0D, Math.min(1.0D, up.y));
        double angle = Math.acos(dot);
        if (angle < 1.0E-4D) {
            return;   // 近乎水平，不需要倾斜
        }
        // 旋转轴 = worldUp × vehicleUp = (up.z, 0, -up.x)
        double axisX = up.z;
        double axisZ = -up.x;
        double axisLen = Math.hypot(axisX, axisZ);
        if (axisLen < 1.0E-6D) {
            // 完全倒扣：任选一个水平轴
            axisX = 1.0D;
            axisZ = 0.0D;
            axisLen = 1.0D;
        }
        axisX /= axisLen;
        axisZ /= axisLen;
        poseStack.mulPose(new Quaternionf().rotationAxis(
                (float) angle, (float) axisX, 0.0F, (float) axisZ));
    }

    /** 只处理自定义模型（GeckoLib 或 YSM）；TLM 内置模型不碰。 */
    @Unique
    private static boolean tlmf$isCustomModel(Mob entity) {
        IMaid maid = IMaid.convert(entity);
        if (maid == null) {
            return false;
        }
        if (maid.isYsmModel()) {
            return true;
        }
        return CustomPackLoader.MAID_MODELS.getInfo(maid.getModelId())
                .map(info -> info.isGeckoModel())
                .orElse(false);
    }
}
