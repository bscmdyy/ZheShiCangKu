package com.tlmformation.client.helicopter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tlmformation.vehicle.EntityHelicopter;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * UH-60 模型渲染。
 *
 * <p>关键点：<b>不再用裸四元数摆模型</b>，而是直接用卓越前线自己的载具矩阵
 * {@code getTransformFromString("Vehicle", partialTick)} —— 这与乘客座位、OBB
 * 用的是同一套变换（包含绕抬高点的横滚/俯仰补偿）。
 *
 * <p>之前只用四元数时，机身绕实体原点滚，而乘客/OBB 绕卓越前线的抬高点滚，
 * 于是翻滚时机身会"甩离"原位、女仆露到机身外。现在模型与乘客同源，翻滚时不会错位。</p>
 */
public class HelicopterGeoRenderer extends GeoEntityRenderer<EntityHelicopter> {
    public HelicopterGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new HelicopterGeoModel());
        // 缩放改到 applyRotations 末尾手动做：必须保证“载具矩阵 -> 朝向修正 -> 缩放”的顺序
        this.withScale(1.0F);
    }

    @Override
    protected void applyRotations(EntityHelicopter entity, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        boolean applied = false;
        try {
            Matrix4d vehicleTransform = entity.getTransformFromString("Vehicle", partialTick);
            if (vehicleTransform != null) {
                Vec3 pos = entity.lerpedRenderPos(partialTick);
                Matrix4f relative = new Matrix4f().translation(
                        (float) -pos.x, (float) -pos.y, (float) -pos.z);
                relative.mul(new Matrix4f(vehicleTransform));   // T(-pos) * vehicleWorld
                poseStack.mulPoseMatrix(relative);
                applied = true;
            }
        } catch (Throwable ignored) {
            // 数据未初始化时退回四元数
        }
        if (!applied) {
            poseStack.mulPose(new Quaternionf(entity.modelToWorldQuat(partialTick)));
        }

        // 模型朝向修正：绕旧 main_no_magnet 中枢点旋转 180°
        // 注意：缩放放在这一层之后，所以中枢点位移必须先用 MODEL_PIXEL（含 0.84）换算，
        // 否则机身会整体偏移约 0.4 格（表现为女仆相对座椅被拉前）。
        float px = (float) (EntityHelicopter.MODEL_ROOT_PIVOT[0] * EntityHelicopter.MODEL_PIXEL);
        float py = (float) (EntityHelicopter.MODEL_ROOT_PIVOT[1] * EntityHelicopter.MODEL_PIXEL);
        float pz = (float) (EntityHelicopter.MODEL_ROOT_PIVOT[2] * EntityHelicopter.MODEL_PIXEL);
        poseStack.translate(px, py, pz);
        poseStack.mulPose(Axis.YP.rotationDegrees((float) EntityHelicopter.MODEL_YAW_OFFSET_DEG));
        poseStack.translate(-px, -py, -pz);

        // 模型缩放放最后：载具矩阵的平移单位是“格”，不能被缩放
        float scale = (float) EntityHelicopter.RENDER_SCALE;
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public void render(EntityHelicopter entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight) {
        // 渲染前补一次同步：舱门开/关动画完全依赖索降阶段
        RappelClientState.syncToEntity(entity);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
