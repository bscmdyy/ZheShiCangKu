package com.tlmformation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.vehicle.EntityHavoc;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 战车自定义特效渲染：
 *  - 曳光弹 3D 线段（2~3 tick）
 *  - 枪口球状膨胀光效（3 tick）
 *  - 命中点多层膨胀发光球（8 tick）
 *  - 水面立体人字形水浪尾流（0.3~0.5s）
 * 全部使用自绘三角形/线段，不用原版 EXPLOSION/level.explode。
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HavocEffectRenderer {
    public static final int TYPE_TRACER = 0;
    public static final int TYPE_FLASH = 1;
    public static final int TYPE_EXPLOSION = 2;

    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final List<Wake> WAKES = new ArrayList<>();

    private HavocEffectRenderer() {
    }

    public static void add(int type, Vec3 a, Vec3 b) {
        int life = type == TYPE_TRACER ? 3 : type == TYPE_FLASH ? 3 : 9;
        EFFECTS.add(new Effect(type, a, b, life));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 特效寿命
        for (Iterator<Effect> it = EFFECTS.iterator(); it.hasNext(); ) {
            Effect effect = it.next();
            if (++effect.age > effect.maxAge) {
                it.remove();
            }
        }
        // 水浪寿命 + 更新
        for (Iterator<Wake> it = WAKES.iterator(); it.hasNext(); ) {
            Wake wake = it.next();
            wake.pos = wake.pos.add(wake.velocity);
            wake.velocity = wake.velocity.scale(0.86D);
            if (++wake.age > wake.maxAge) {
                it.remove();
            }
        }
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        // 水面行驶：每 2 tick 在车尾左右生成立体水浪；叠加低沉破浪声
        for (EntityHavoc vehicle : minecraft.level.getEntitiesOfClass(EntityHavoc.class,
                minecraft.player.getBoundingBox().inflate(128.0D), EntityHavoc::isAlive)) {
            if (!vehicle.isInWater() || Math.abs(vehicle.getVehicleSpeed()) <= 0.05D) {
                continue;
            }
            if (vehicle.tickCount % 2 == 0) {
                Vec3 fwd = new Vec3(0, 0, 1)
                        .yRot(-vehicle.getYRot() * Mth.DEG_TO_RAD);
                Vec3 right = new Vec3(-fwd.z, 0.0D, fwd.x);   // 垂直车头
                Vec3 rear = vehicle.position().subtract(fwd.scale(1.9D));
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 spawn = rear.add(right.scale(0.85D * side)).add(0, 0.15D, 0);
                    Vec3 velocity = right.scale(0.055D * side).subtract(fwd.scale(0.075D));
                    WAKES.add(new Wake(spawn, velocity, right, fwd,
                            8 + minecraft.level.random.nextInt(4)));
                }
            }
            if (vehicle.tickCount % 10 == 0) {
                minecraft.level.playLocalSound(vehicle.getX(), vehicle.getY() + 0.2D,
                        vehicle.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_SPLASH,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.1F, 0.55F, false);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || (EFFECTS.isEmpty() && WAKES.isEmpty())) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);   // 世界坐标 -> 相机空间

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(3.0F);

        Tesselator tesselator = Tesselator.getInstance();

        // ---- 1) 曳光弹线段 ----
        boolean hasTracer = EFFECTS.stream().anyMatch(e -> e.type == TYPE_TRACER);
        if (hasTracer) {
            BufferBuilder lines = tesselator.getBuilder();
            lines.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            for (Effect effect : EFFECTS) {
                if (effect.type != TYPE_TRACER) continue;
                float alpha = 1.0F - (float) effect.age / effect.maxAge;
                line(lines, effect.a, effect.b,
                        1.0F, 0.95F, 0.55F, alpha);
            }
            tesselator.end();
        }

        // ---- 2) 枪口闪光 / 爆炸 / 水浪（三角形） ----
        BufferBuilder triangles = tesselator.getBuilder();
        triangles.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (Effect effect : EFFECTS) {
            float progress = (float) effect.age / effect.maxAge;
            if (effect.type == TYPE_FLASH) {
                float radius = 0.18F + progress * 0.55F;
                sphere(triangles, effect.a, radius,
                        1.0F, 0.92F, 0.55F, (1.0F - progress) * 0.85F);
            } else if (effect.type == TYPE_EXPLOSION) {
                Vec3 center = effect.a;
                sphere(triangles, center, 0.35F + progress * 2.6F,
                        1.0F, 0.55F, 0.12F, (1.0F - progress) * 0.55F);
                sphere(triangles, center, 0.25F + progress * 1.6F,
                        1.0F, 0.85F, 0.45F, (1.0F - progress) * 0.75F);
                sphere(triangles, center, 0.20F + progress * 0.8F,
                        1.0F, 1.0F, 0.85F, (1.0F - progress) * 0.9F);
            }
        }

        for (Wake wake : WAKES) {
            float progress = (float) wake.age / wake.maxAge;
            float width = 0.35F + progress * 1.35F;
            float length = 0.8F + progress * 1.9F;
            float alpha = (1.0F - progress) * 0.38F;
            Vec3 p = wake.pos;
            Vec3 right = wake.right.scale(width * 0.5D);
            Vec3 back = wake.fwd.scale(-length);
            Vec3 v0 = p.subtract(right);
            Vec3 v1 = p.add(right);
            Vec3 v2 = p.add(right).add(back);
            Vec3 v3 = p.subtract(right).add(back);
            triangle(triangles, v0, v1, v2, 0.75F, 0.92F, 1.0F, alpha);
            triangle(triangles, v0, v2, v3, 0.75F, 0.92F, 1.0F, alpha);
        }
        tesselator.end();

        RenderSystem.lineWidth(1.0F);
        poseStack.popPose();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void line(BufferBuilder buffer, Vec3 a, Vec3 b,
                             float r, float g, float bl, float alpha) {
        buffer.vertex(a.x, a.y, a.z).color(r, g, bl, alpha).endVertex();
        buffer.vertex(b.x, b.y, b.z).color(r, g, bl, alpha).endVertex();
    }

    private static void triangle(BufferBuilder buffer, Vec3 a, Vec3 b, Vec3 c,
                                 float r, float g, float bl, float alpha) {
        buffer.vertex(a.x, a.y, a.z).color(r, g, bl, alpha).endVertex();
        buffer.vertex(b.x, b.y, b.z).color(r, g, bl, alpha).endVertex();
        buffer.vertex(c.x, c.y, c.z).color(r, g, bl, alpha).endVertex();
    }

    /** 用经纬线三角形拼一个发光球（半径以格为单位）。 */
    private static void sphere(BufferBuilder buffer, Vec3 center, float radius,
                               float r, float g, float b, float alpha) {
        int rings = 6;
        int segments = 10;
        for (int i = 0; i < rings; i++) {
            double t0 = Math.PI * i / rings;
            double t1 = Math.PI * (i + 1) / rings;
            for (int j = 0; j < segments; j++) {
                double p0 = Math.PI * 2.0D * j / segments;
                double p1 = Math.PI * 2.0D * (j + 1) / segments;
                Vec3 v00 = point(center, radius, t0, p0);
                Vec3 v01 = point(center, radius, t0, p1);
                Vec3 v10 = point(center, radius, t1, p0);
                Vec3 v11 = point(center, radius, t1, p1);
                triangle(buffer, v00, v01, v11, r, g, b, alpha);
                triangle(buffer, v00, v11, v10, r, g, b, alpha);
            }
        }
    }

    private static Vec3 point(Vec3 center, float radius, double theta, double phi) {
        double sin = Math.sin(theta);
        return center.add(
                radius * sin * Math.cos(phi),
                radius * Math.cos(theta),
                radius * sin * Math.sin(phi));
    }

    private static final class Effect {
        private final int type;
        private final Vec3 a;
        private final Vec3 b;
        private final int maxAge;
        private int age;

        private Effect(int type, Vec3 a, Vec3 b, int maxAge) {
            this.type = type;
            this.a = a;
            this.b = b;
            this.maxAge = maxAge;
        }
    }

    private static final class Wake {
        private Vec3 pos;
        private Vec3 velocity;
        private final Vec3 right;
        private final Vec3 fwd;
        private final int maxAge;
        private int age;

        private Wake(Vec3 pos, Vec3 velocity, Vec3 right, Vec3 fwd, int maxAge) {
            this.pos = pos;
            this.velocity = velocity;
            this.right = right;
            this.fwd = fwd;
            this.maxAge = maxAge;
        }
    }
}
