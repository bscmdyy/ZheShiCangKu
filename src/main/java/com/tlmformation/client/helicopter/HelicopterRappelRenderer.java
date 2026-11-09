package com.tlmformation.client.helicopter;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.vehicle.EntityHelicopter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 索降绳索渲染器（世界空间线段）。
 *
 * <p>只依赖实体同步状态 + 模型骨骼常量，不依赖 GeckoLib 内部骨骼矩阵：</p>
 * <ul>
 *   <li>锚点：骨骼 117 / 118 的模型 pivot 经载具四元数旋转到世界坐标；</li>
 *   <li>长度：从锚点向下 RayTrace 到第一个方块碰撞面（无碰撞时退化为地表高度）；</li>
 *   <li>摆动：正弦函数（两根绳相位不同），越靠近末端摆幅越大，模拟微风。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HelicopterRappelRenderer {
    /** 绳索分段数（越大越顺滑，但顶点更多）。 */
    private static final int ROPE_SEGMENTS = 16;
    /** 微风摆动基础频率。 */
    private static final double SWAY_SPEED_X = 0.55D;
    private static final double SWAY_SPEED_Z = 0.42D;
    /** 末端最大摆幅（格）。 */
    private static final double SWAY_AMPLITUDE = 0.35D;

    private HelicopterRappelRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (!ModList.get().isLoaded("superbwarfare")) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();

        boolean drew = false;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof EntityHelicopter heli)) {
                continue;
            }
            int phase = RappelClientState.phase(heli.getId());
            if (phase != EntityHelicopter.RAPPEL_ROPES_OUT
                    && phase != EntityHelicopter.RAPPEL_DESCENDING
                    && phase != EntityHelicopter.RAPPEL_ASCEND_ROPES
                    && phase != EntityHelicopter.RAPPEL_ASCENDING) {
                continue;
            }
            float time = heli.tickCount + partialTick;
            for (int i = 0; i < EntityHelicopter.ROPE_COUNT; i++) {
                drew |= drawRope(consumer, matrix, heli, i, partialTick, time);
            }
        }

        poseStack.popPose();

        if (drew) {
            RenderSystem.lineWidth(2.0F);
            buffers.endBatch(RenderType.lines());
            RenderSystem.lineWidth(1.0F);
        }
    }

    /**
     * 画一根绳索。
     *
     * @return 是否真的画了（没有长度时不画）
     */
    private static boolean drawRope(VertexConsumer consumer, Matrix4f matrix,
                                    EntityHelicopter heli, int index,
                                    float partialTick, float time) {
        Vec3 anchor = heli.rappelAnchorWorld(index, partialTick);
        Vec3 ground = heli.rappelGroundPoint(anchor);
        double length = anchor.y - ground.y;
        if (length <= 0.1D) {
            return false;
        }
        // 限制单根绳的渲染长度，防止高空绳索刷爆顶点
        length = Math.min(length, 192.0D);

        double seed = heli.getId() * 0.37D + index * 1.91D;
        float prevX = 0.0F;
        float prevY = 0.0F;
        float prevZ = 0.0F;
        for (int segment = 0; segment <= ROPE_SEGMENTS; segment++) {
            double t = segment / (double) ROPE_SEGMENTS;
            double y = anchor.y - length * t;
            // 越靠下摆幅越大，末端最晃
            double amplitude = SWAY_AMPLITUDE * t;
            double swayX = Math.sin(time * SWAY_SPEED_X + t * 2.6D + seed) * amplitude;
            double swayZ = Math.cos(time * SWAY_SPEED_Z + t * 1.9D + seed * 1.3D)
                    * amplitude * 0.8D;
            float x = (float) (anchor.x + swayX);
            float yy = (float) y;
            float z = (float) (anchor.z + swayZ);

            if (segment > 0) {
                consumer.vertex(matrix, prevX, prevY, prevZ)
                        .color(0.72F, 0.66F, 0.52F, 1.0F)
                        .normal(0.0F, 1.0F, 0.0F)
                        .endVertex();
                consumer.vertex(matrix, x, yy, z)
                        .color(0.72F, 0.66F, 0.52F, 1.0F)
                        .normal(0.0F, 1.0F, 0.0F)
                        .endVertex();
            }
            prevX = x;
            prevY = yy;
            prevZ = z;
        }
        return true;
    }
}
