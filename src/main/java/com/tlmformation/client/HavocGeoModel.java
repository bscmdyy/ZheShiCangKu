package com.tlmformation.client;

import com.tlmformation.vehicle.EntityHavoc;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayList;
import java.util.List;

/**
 * GeckoLib 模型包装。
 *
 * <p><b>骨骼旋转全部由代码强制驱动</b>，不依赖 animation.json：</p>
 * <ul>
 *   <li>炮塔：{@code turret_base}（相对车体 Y 轴）</li>
 *   <li>炮管俯仰：{@code clamp_L} / {@code clamp_R} / {@code gun}（相对炮塔 X 轴）</li>
 *   <li>前轮转向：{@code steer_FL} / {@code steer_FR}（Y 轴）</li>
 *   <li>六轮滚动：{@code wheel_FL/FR/ML/MR/RL/RR}（X 轴，按速度累积角度）</li>
 * </ul>
 *
 * <p>关键点：必须写在 {@link #setCustomAnimations} 里。GeckoLib 4.8.4 的
 * {@code GeoModel#handleAnimations} 调用顺序是：
 * <pre>preAnimationSetup(applyMolangQueries) → tickAnimation(动画文件) → setCustomAnimations</pre>
 * 所以写在 applyMolangQueries 里的旋转会被动画文件覆盖，导致“轮子不转、炮塔不转”。</p>
 */
public class HavocGeoModel extends GeoModel<EntityHavoc> {
    // ===== 资源路径（换模型/贴图只需改这三行，或用外部发来的同名文件覆盖 assets 即可） =====
    public static final ResourceLocation MODEL =
            new ResourceLocation("tlmformation", "geo/havoc_vehicle.geo.json");
    public static final ResourceLocation TEXTURE =
            new ResourceLocation("tlmformation", "textures/entity/havoc_vehicle.png");
    public static final ResourceLocation ANIM =
            new ResourceLocation("tlmformation", "animations/havoc_vehicle.animation.json");

    // 尺寸校准入口：换模型后若比例不对，只改 EntityHavoc 里这两个常量即可
    public static final double MODEL_SCALE = EntityHavoc.MODEL_SCALE;
    public static final double VISUAL_SCALE = EntityHavoc.VISUAL_SCALE;
    public static final double RENDER_SCALE = EntityHavoc.RENDER_SCALE;

    /** 模型里的真实骨骼名（不是 turret/barrel/wheel 这种简写）。 */
    private static final String TURRET_BONE = "turret_base";
    private static final String[] PITCH_BONES = {"clamp_L", "clamp_R", "gun"};
    private static final String[] STEER_BONES = {"steer_FL", "steer_FR"};
    private static final String[] WHEEL_BONES = {
            "wheel_FL", "wheel_FR", "wheel_ML", "wheel_MR", "wheel_RL", "wheel_RR"
    };

    private boolean bonesLogged;
    /** 每实体平滑后的炮塔角度（模型骨骼空间），避免多辆车互相干扰。 */
    private final java.util.Map<Integer, Float> smoothTurretYaw = new java.util.HashMap<>();
    private final java.util.Map<Integer, Float> smoothTurretPitch = new java.util.HashMap<>();

    @Override
    public ResourceLocation getModelResource(EntityHavoc animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(EntityHavoc animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(EntityHavoc animatable) {
        return ANIM;
    }

    /**
     * 每帧在动画文件之后调用 —— 这里是唯一能“强制”压过动画的钩子。
     */
    @Override
    public void setCustomAnimations(EntityHavoc animatable, long instanceId,
                                    AnimationState<EntityHavoc> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        logBonesOnce();
        int entityId = animatable.getId();

        // 1) 前轮转向（Y 轴，相对车体；符号与左侧转向一致）
        float steerRad = (float) Math.toRadians(-animatable.getSteerDeg());
        for (String name : STEER_BONES) {
            this.getBone(name).ifPresent(bone -> bone.setRotY(steerRad));
        }

        // 2) 炮塔 360° 平滑锁定：turret_base 是 body 的子骨骼 => 相对车体
        //    骨骼空间 yaw = -同步yaw（模型 -Z 车头 + GeckoLib(180-yaw) 渲染约定）
        float targetYaw = (float) -animatable.getTurretYaw();
        float currentYaw = this.smoothTurretYaw.getOrDefault(entityId, targetYaw);
        float smoothYaw = Mth.rotLerp(0.45F, currentYaw, targetYaw); // 360° wrap 安全
        if (this.smoothTurretYaw.size() > 64) {
            this.smoothTurretYaw.clear();
        }
        this.smoothTurretYaw.put(entityId, smoothYaw);
        float turretRad = (float) Math.toRadians(smoothYaw);
        this.getBone(TURRET_BONE).ifPresent(bone -> bone.setRotY(turretRad));

        // 3) 炮管/夹板俯仰（+ = 抬高），Mth.rotLerp 平滑
        float targetPitch = (float) animatable.getTurretPitch();
        float currentPitch = this.smoothTurretPitch.getOrDefault(entityId, targetPitch);
        float smoothPitch = Mth.rotLerp(0.45F, currentPitch, targetPitch);
        if (this.smoothTurretPitch.size() > 64) {
            this.smoothTurretPitch.clear();
        }
        this.smoothTurretPitch.put(entityId, smoothPitch);
        float turretPitchRad = (float) Math.toRadians(smoothPitch);
        for (String name : PITCH_BONES) {
            this.getBone(name).ifPresent(bone -> bone.setRotX(turretPitchRad));
        }

        // 4) 六轮持续滚动（X 轴；角度由实体按速度累积，见 EntityHavoc#getWheelAngle）
        float wheelRad = (float) Math.toRadians(animatable.getWheelAngle());
        for (String name : WHEEL_BONES) {
            this.getBone(name).ifPresent(bone -> bone.setRotX(wheelRad));
        }
    }

    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }

    private void logBonesOnce() {
        if (bonesLogged) {
            return;
        }
        var bones = this.getAnimationProcessor().getRegisteredBones();
        if (bones == null || bones.isEmpty()) {
            return;
        }
        bonesLogged = true;
        List<String> names = new ArrayList<>();
        for (CoreGeoBone bone : bones) {
            names.add(bone.getName());
        }
        System.out.println("[Debug] 模型骨骼列表(" + names.size() + "): " + names);
        for (String required : new String[]{TURRET_BONE, "gun", "clamp_L", "clamp_R",
                "steer_FL", "steer_FR", "wheel_FL", "wheel_FR", "wheel_ML",
                "wheel_MR", "wheel_RL", "wheel_RR"}) {
            System.out.println("[Debug] 骨骼 " + required + " 存在="
                    + this.getBone(required).isPresent());
        }
    }

    /** 供其它代码按名字检查骨骼（调试用）。 */
    public java.util.Optional<GeoBone> bone(String name) {
        return this.getBone(name);
    }
}
