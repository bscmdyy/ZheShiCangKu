package com.tlmformation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tlmformation.vehicle.EntityHavoc;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * HAVOC / 沉米级无人战车渲染器。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>整体缩放 = MODEL_SCALE × VISUAL_SCALE(0.75)（模型原点在轮底，缩放后仍贴地）</li>
 *   <li>坡度俯仰/侧倾：角度由实体每 tick 计算并平滑（限幅 ±15°、最大 1°/tick、Mth.lerp 0.1F），
 *       渲染器只负责应用并按倾斜量下压防悬空</li>
 *   <li>机械单位：强制白色着色 + NO_OVERLAY，彻底屏蔽受击红光</li>
 * </ul>
 */
public class HavocRenderer extends GeoEntityRenderer<EntityHavoc> {
    public HavocRenderer(EntityRendererProvider.Context context) {
        super(context, new HavocGeoModel());
        // 等价于渲染时 poseStack.scale(0.75F,0.75F,0.75F)，GeckoLib 官方缩放接口
        this.withScale((float) EntityHavoc.RENDER_SCALE);
        this.shadowRadius = 0.0F;   // F10 战术小车：删除脚下阴影
        System.out.println("[Debug] HavocRenderer 初始化: 渲染缩放=" + EntityHavoc.RENDER_SCALE
                + " 阴影半径=" + this.shadowRadius
                + " 模型=" + HavocGeoModel.MODEL
                + " 贴图=" + HavocGeoModel.TEXTURE
                + " 动画=" + HavocGeoModel.ANIM);
    }

    /** 在实体平移 + 车体 yaw 旋转之后，应用实体已经平滑好的坡度角。 */
    @Override
    protected void applyRotations(EntityHavoc animatable, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick);

        float pitch = animatable.getRenderPitch();   // 已限幅 ±15°、1°/tick
        float roll = animatable.getRenderRoll();

        // 防悬空：按倾斜量把模型往下压，抵消倾斜造成的车轮抬升
        double lift = Math.abs(Math.sin(Math.toRadians(pitch))) * EntityHavoc.HULL_HALF_L
                + Math.abs(Math.sin(Math.toRadians(roll))) * EntityHavoc.HULL_HALF_W;
        double sink = Math.min(0.85D, lift * 0.9D);
        poseStack.translate(0.0D, -sink, 0.0D);

        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));

        // 机械单位：强制纯白着色，覆盖任何受击染色
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 机械单位：渲染颜色永远是白色（屏蔽红闪/受伤染色）。 */
    @Override
    public Color getRenderColor(EntityHavoc animatable, float partialTick, int packedLight) {
        return Color.WHITE;
    }

    /** 机械单位：永远不使用 hurt/death overlay（红色叠加层的来源）。 */
    @Override
    public int getPackedOverlay(EntityHavoc animatable, float u) {
        return OverlayTexture.NO_OVERLAY;
    }

    @Override
    public int getPackedOverlay(EntityHavoc animatable, float u, float v) {
        return OverlayTexture.NO_OVERLAY;
    }
}
