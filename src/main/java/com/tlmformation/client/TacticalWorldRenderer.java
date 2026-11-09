package com.tlmformation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.tactical.TacticalCommand;
import com.tlmformation.tactical.TacticalState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Small billboard icons above maids with temporary tactical states, plus the
 * lock/crosshair icon at a marked station.
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TacticalWorldRenderer {
    private TacticalWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();

        BlockPos marker = TacticalClientState.marker();
        if (marker != null) {
            renderIcon(event, cameraPos, Vec3.atCenterOf(marker).add(0, 1.6, 0),
                    TacticalCommand.LOCK, 0.8F);
        }

        int lockedTargetId = TacticalClientState.lockedTargetEntityId();
        if (lockedTargetId >= 0) {
            Entity lockedTarget = minecraft.level.getEntity(lockedTargetId);
            if (lockedTarget != null && lockedTarget.isAlive()) {
                renderIcon(event, cameraPos,
                        lockedTarget.position().add(0,
                                lockedTarget.getBbHeight() + 1.1D, 0),
                        TacticalCommand.LOCK, 0.75F);
            }
        }

        for (int entityId : TacticalClientState.entityIds()) {
            Entity entity = minecraft.level.getEntity(entityId);
            if (entity == null) {
                continue;
            }
            TacticalState state = TacticalClientState.get(entityId);
            if (state == null) {
                continue;
            }
            TacticalCommand icon = switch (state) {
                case STATION -> TacticalCommand.MARK;
                case GUARD -> TacticalCommand.GUARD;
                case RETREAT -> TacticalCommand.RETREAT;
                case HOLD -> TacticalCommand.HOLD;
                default -> null;
            };
            if (icon != null) {
                renderIcon(event, cameraPos,
                        entity.position().add(0, entity.getBbHeight() + 0.9, 0), icon, 0.55F);
            }
        }
    }

    private static void renderIcon(RenderLevelStageEvent event, Vec3 cameraPos,
                                   Vec3 worldPos, TacticalCommand command, float scale) {
        ResourceLocation texture = command.texture();
        if (texture == null) {
            return;
        }
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(worldPos.x - cameraPos.x,
                worldPos.y - cameraPos.y,
                worldPos.z - cameraPos.z);
        pose.mulPose(event.getCamera().rotation());
        pose.scale(scale, -scale, scale);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);

        Matrix4f matrix = pose.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, -0.5F, -0.5F, 0.0F).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(matrix, 0.5F, -0.5F, 0.0F).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(matrix, 0.5F, 0.5F, 0.0F).uv(1.0F, 0.0F).endVertex();
        buffer.vertex(matrix, -0.5F, 0.5F, 0.0F).uv(0.0F, 0.0F).endVertex();
        Tesselator.getInstance().end();

        RenderSystem.disableBlend();
        pose.popPose();
    }
}
