package com.tlmformation.vehicle;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * HAVOC 两栖 AI 战车实体 —— 快餐式物理 / 转向 / 炮塔 / 开火实现。
 *
 * 与模型约定（来自 HANDOFF_PHYSICS_AI.md）：
 *  - 模型缩放 k = 0.5565，车身高度 2.0 格；轮胎最低点 y = 0（模型原点=地面）
 *  - 车身 OBB：2.131 × 2.0 × 5.082 格，局部中心 z = -0.435，顶面 y = 2（玩家可站）
 *  - 动画：wheels_roll（六轮滚动）/ wheels_roll_reverse（倒车）/ wheels_steer /
 *          turret_yaw / turret_pitch / fire_recoil
 *  - 特效骨骼（局部 px）：fx_muzzle [0, 49.004, -73]、fx_mg [6.5, 49.004, -22]
 */
public class EntityHavoc extends PathfinderMob implements GeoEntity {
    // ===== 快餐参数（想调只改这里） =====
    /** 模型基础缩放（车身高度 2 格）。 */
    public static final double MODEL_SCALE = 0.5565;
    /** 整体尺寸倍率：视觉模型 + 物理碰撞箱统一 ×0.75。 */
    public static final double VISUAL_SCALE = 0.75;
    /** 碰撞箱在视觉缩放基础上再 ×0.85（减小空气墙）。 */
    public static final double COLLISION_SCALE = 0.85;
    /** 在碰撞缩放基础上再把宽/高各缩 0.85，窄处更好通过。 */
    public static final double HITBOX_SCALE = 0.85;
    /** 最终渲染缩放 = MODEL_SCALE × VISUAL_SCALE。 */
    public static final double RENDER_SCALE = MODEL_SCALE * VISUAL_SCALE;

    // 车身 OBB（已经乘过 0.75 × 0.85）
    public static final double HULL_HALF_W = 2.131 / 2.0 * VISUAL_SCALE * COLLISION_SCALE * HITBOX_SCALE;
    public static final double HULL_HALF_L = 5.082 / 2.0 * VISUAL_SCALE * COLLISION_SCALE;
    public static final double HULL_HEIGHT = 2.0 * VISUAL_SCALE * COLLISION_SCALE * HITBOX_SCALE;
    public static final double HULL_CENTER_Z = -0.435 * VISUAL_SCALE * COLLISION_SCALE;

    public static final double WHEEL_RADIUS = 0.348 * VISUAL_SCALE;
    public static final double WHEELBASE = 2.37 * VISUAL_SCALE;

    // ===== F10 左侧槽位：悍马已移除（保留 HAVOC 主战车） =====

    public static final double MAX_SPEED = 9.0;          // 格/s
    public static final double MAX_REVERSE = 3.5;
    public static final double ACCEL = 6.0;
    public static final double BRAKE = 10.0;
    public static final double MAX_STEER_DEG = 30.0;
    /** 炮塔 360° 无死角，不再有 yaw 限位。 */
    public static final double TURRET_PITCH_MIN = -10.0;
    public static final double TURRET_PITCH_MAX = 12.0;
    public static final double FIRE_COOLDOWN = 40;       // ticks
    /** 50 格超远索敌（扫描用），开火同样 50 格。 */
    public static final double TARGET_RANGE = 50.0;
    public static final double FIRE_RANGE = 50.0;
    /** 范围爆炸炮：3~5 格半径，只伤敌对生物。 */
    public static final double EXPLOSION_RADIUS_MIN = 3.0;
    public static final double EXPLOSION_RADIUS_MAX = 5.0;
    public static final float AOE_DAMAGE = 20.0F;
    /** 自主游走半径（以玩家为中心）。 */
    public static final double WANDER_MIN_RADIUS = 10.0;
    public static final double WANDER_MAX_RADIUS = 20.0;
    /** 索敌扫描间隔（tick）。 */
    public static final int SCAN_INTERVAL = 20;
    /** 跟随距离默认值 / 可调范围（F10 菜单）。 */
    public static final int DEFAULT_FOLLOW_DISTANCE = 20;
    public static final int MIN_FOLLOW_DISTANCE = 10;
    public static final int MAX_FOLLOW_DISTANCE = 50;
    /** 炮口对准误差阈值（度）：Yaw 与 Pitch 各自都要 < 10°。 */
    public static final double FIRE_ANGLE_TOLERANCE = 10.0;
    /** 轮子滚过 1 格对应的旋转角度（按缩放后的轮半径计算）。 */
    private static final double WHEEL_DEGREES_PER_BLOCK =
            360.0D / (2.0D * Math.PI * WHEEL_RADIUS);

    private static final double[] WHEEL_X = {
            -0.904 * VISUAL_SCALE, 0.904 * VISUAL_SCALE,
            -0.904 * VISUAL_SCALE, 0.904 * VISUAL_SCALE,
            -0.904 * VISUAL_SCALE, 0.904 * VISUAL_SCALE
    };
    private static final double[] WHEEL_Z = {
            -1.182 * VISUAL_SCALE, -1.182 * VISUAL_SCALE,
            0.0D, 0.0D,
            1.182 * VISUAL_SCALE, 1.182 * VISUAL_SCALE
    };

    // ===== synced =====
    private static final EntityDataAccessor<Float> DATA_SPEED =
            SynchedEntityData.defineId(EntityHavoc.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STEER =
            SynchedEntityData.defineId(EntityHavoc.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TURRET_YAW =
            SynchedEntityData.defineId(EntityHavoc.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TURRET_PITCH =
            SynchedEntityData.defineId(EntityHavoc.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FIRE =
            SynchedEntityData.defineId(EntityHavoc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AUTO =
            SynchedEntityData.defineId(EntityHavoc.class, EntityDataSerializers.BOOLEAN);
    /** 累计击败（战术 HUD 显示）。 */
    private static final EntityDataAccessor<Integer> DATA_KILLS =
            SynchedEntityData.defineId(EntityHavoc.class, EntityDataSerializers.INT);
    /** 跟随距离（格，10~50），F10 轮盘可调。 */
    private static final EntityDataAccessor<Integer> DATA_FOLLOW_DISTANCE =
            SynchedEntityData.defineId(EntityHavoc.class, EntityDataSerializers.INT);
    /** AI 状态机（VehicleState.ordinal）。 */
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(EntityHavoc.class, EntityDataSerializers.INT);
    /** 伪挂载女仆 UUID（空字符串 = 没有女仆坐后座）。 */
    private static final EntityDataAccessor<String> DATA_ATTACHED_MAID =
            SynchedEntityData.defineId(EntityHavoc.class, EntityDataSerializers.STRING);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private double speed = 0.0;           // 当前前向速度（负=倒车）
    private double steerDeg = 0.0;        // 前轮转角
    private double turretYaw = 0.0;       // 炮塔相对车身 yaw
    private double turretPitch = 0.0;     // 炮塔俯仰
    private int fireTicks = 0;
    private int fireCooldown = 0;
    private boolean autoMode = true;

    /** 客户端累积的轮子旋转角度（度），由服务器同步的速度驱动。 */
    private float wheelAngleDeg;

    /** 客户端平滑后的坡度（度），渲染器直接使用。 */
    private float renderPitch;
    private float renderRoll;

    /** 自主游走目标（玩家静止时围绕玩家随机游荡）。 */
    @Nullable
    private Vec3 wanderTarget;
    private int wanderCooldown;
    /** 50 格内是否存在可见敌人（每 20 tick 刷新，避免每 tick 全量扫描）。 */
    private boolean enemyNearby;
    /** 近距离拉扯时禁止开火。 */
    private boolean holdFire;
    /** 5 格危险：全速倒车状态。 */
    private boolean retreating;
    /** 卡死检测。 */
    private int stuckTicks;
    private double lastX;
    private double lastZ;
    private int escapeTicks;
    /** 环绕方向（周期翻转）。 */
    private int orbitSign = 1;
    /** 炮塔空闲警戒扫视。 */
    private double scanYawTarget;
    private double scanPitchTarget;
    private int scanTimer;

    // ===== owner / armored reinforcement =====
    @Nullable
    private UUID ownerUUID;
    /** 撤回后静态停机：true 时禁止跟随/寻路/AI 移动，只有重新部署才解除。 */
    private boolean recalled;
    private long lastHurtGameTime;
    private int regenTimer;

    public void setOwner(@Nullable Player player) {
        this.ownerUUID = player == null ? null : player.getUUID();
        if (this.ownerUUID != null) {
            this.setPersistenceRequired();
        }
    }

    @Nullable
    public boolean isRecalled() {
        return this.recalled;
    }

    public void setRecalled(boolean recalled) {
        this.recalled = recalled;
        if (recalled) {
            this.setDeltaMovement(0, 0, 0);
            this.setTarget(null);
            this.getNavigation().stop();
        }
    }

    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public boolean isOwnedBy(Player player) {
        return this.ownerUUID != null && this.ownerUUID.equals(player.getUUID());
    }

    @Nullable
    private Player ownerPlayer() {
        return this.ownerUUID == null ? null : this.level().getPlayerByUUID(this.ownerUUID);
    }

    // ===== animations =====
    // 轮子 / 炮塔 / 前轮转向全部由 HavocGeoModel#setCustomAnimations 代码驱动，
    // 这里只保留开火后座这一个真正的动画。

    public EntityHavoc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        // ★ 允许原版 move 自动爬上 1 格台阶（2 格墙仍会被 AABB 挡住）
        this.setMaxUpStep(1.2F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)   // 最大生命 200
                .add(Attributes.ARMOR, 20.0D)         // 护甲 20
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 50.0D);
    }

    @Override
    protected void registerGoals() {
        // 不用 vanilla 移动 AI：走路方向盘全部由本类物理/AI 控制
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SPEED, 0.0F);
        this.entityData.define(DATA_STEER, 0.0F);
        this.entityData.define(DATA_TURRET_YAW, 0.0F);
        this.entityData.define(DATA_TURRET_PITCH, 0.0F);
        this.entityData.define(DATA_FIRE, 0);
        this.entityData.define(DATA_AUTO, true);
        this.entityData.define(DATA_KILLS, 0);
        this.entityData.define(DATA_FOLLOW_DISTANCE, DEFAULT_FOLLOW_DISTANCE);
        this.entityData.define(DATA_STATE, VehicleState.FOLLOW.ordinal());
        this.entityData.define(DATA_ATTACHED_MAID, "");
    }

    // ===== GeckoLib =====
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 轮子/炮塔/转向全部由 HavocGeoModel#setCustomAnimations 代码驱动，
        // 不再注册 wheels_roll 等驱动动画（动画会覆盖代码写入的骨骼旋转）。
        controllers.add(new AnimationController<>(this, "fire", 0, state -> PlayState.STOP)
                .triggerableAnim("fire_recoil", RawAnimation.begin().thenPlay("fire_recoil")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ===== synced getters（渲染/AI 用） =====
    public double getVehicleSpeed() { return this.level().isClientSide ? this.entityData.get(DATA_SPEED) : this.speed; }
    public double getSteerDeg() { return this.level().isClientSide ? this.entityData.get(DATA_STEER) : this.steerDeg; }
    public double getTurretYaw() { return this.level().isClientSide ? this.entityData.get(DATA_TURRET_YAW) : this.turretYaw; }
    public double getTurretPitch() { return this.level().isClientSide ? this.entityData.get(DATA_TURRET_PITCH) : this.turretPitch; }
    public int getFireTicks() { return this.level().isClientSide ? this.entityData.get(DATA_FIRE) : this.fireTicks; }
    public boolean isAutoMode() { return this.level().isClientSide ? this.entityData.get(DATA_AUTO) : this.autoMode; }
    public int getKills() { return this.entityData.get(DATA_KILLS); }

    /** 跟随距离（10~50 格），F10 菜单可调。 */
    public int getFollowDistance() {
        return Mth.clamp(this.entityData.get(DATA_FOLLOW_DISTANCE),
                MIN_FOLLOW_DISTANCE, MAX_FOLLOW_DISTANCE);
    }

    public void setFollowDistance(int distance) {
        this.entityData.set(DATA_FOLLOW_DISTANCE,
                Mth.clamp(distance, MIN_FOLLOW_DISTANCE, MAX_FOLLOW_DISTANCE));
    }

    // ===== AI 状态机 / 伪挂载女仆 =====
    public VehicleState getVehicleState() {
        return VehicleState.byId(this.entityData.get(DATA_STATE));
    }

    public void setVehicleState(VehicleState state) {
        this.entityData.set(DATA_STATE, state.ordinal());
    }

    // ===== 伪挂载：女仆 UUID 绑定（完全不使用原版乘骑系统） =====
    public String getAttachedMaidString() {
        return this.entityData.get(DATA_ATTACHED_MAID);
    }

    public void attachMaid(@Nullable UUID maidUuid) {
        this.entityData.set(DATA_ATTACHED_MAID, maidUuid == null ? "" : maidUuid.toString());
    }

    @Nullable
    public UUID getAttachedMaidUuid() {
        String raw = this.getAttachedMaidString();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** 找到伪挂载的女仆（附近 96 格）。 */
    @Nullable
    public EntityMaid findAttachedMaid() {
        UUID uuid = this.getAttachedMaidUuid();
        if (uuid == null) {
            return null;
        }
        List<EntityMaid> maids = this.level().getEntitiesOfClass(EntityMaid.class,
                this.getBoundingBox().inflate(96.0D),
                maid -> maid.isAlive() && maid.getUUID().equals(uuid));
        return maids.isEmpty() ? null : maids.get(0);
    }

    /** 解除伪挂载，恢复女仆自由移动 AI。 */
    public void detachMaid() {
        EntityMaid maid = this.findAttachedMaid();
        if (maid != null) {
            maid.setNoAi(false);             // ★ 恢复 AI
            maid.setInSittingPose(false);    // ★ 解除坐姿
            maid.noPhysics = false;          // ★ 恢复物理碰撞箱
            maid.setNoGravity(false);        // ★ 恢复重力
            maid.refreshDimensions();        // ★ 恢复原始碰撞箱尺寸
            maid.getNavigation().stop();
            maid.setDeltaMovement(Vec3.ZERO);
        }
        this.attachMaid(null);
    }

    /** 轮子旋转角度（度，0~360），客户端每 tick 按速度累积，供渲染器直接写骨骼。 */
    public float getWheelAngle() {
        return this.wheelAngleDeg;
    }

    /** 客户端平滑后的俯仰/侧倾（度），渲染器直接应用（已做 1°/tick 限速）。 */
    public float getRenderPitch() {
        return this.renderPitch;
    }

    public float getRenderRoll() {
        return this.renderRoll;
    }

    // ===== tick =====
    @Override
    public void tick() {
        super.tick();
        // ★ 伪挂载：女仆是完全脱机的幽灵实体，只锁坐标，不碰任何乘骑/物理 API
        if (!this.level().isClientSide && this.getAttachedMaidUuid() != null) {
            EntityMaid maid = this.findAttachedMaid();
            if (maid == null) {
                this.attachMaid(null);
            } else {
                // 持续保持“脱机”状态：无 AI、无物理、无重力、坐姿
                maid.setNoAi(true);
                maid.noPhysics = true;
                maid.setNoGravity(true);
                maid.setInSittingPose(true);
                maid.getNavigation().stop();
                // 纯坐标锁定：绝不调用 setDeltaMovement
                this.attachMaidAtSeat(maid);
            }
        }
        if (this.level().isClientSide) {
            this.clientTick();
        } else {
            this.serverTick();
        }
    }

    /** 把伪挂载女仆锁定到车尾装甲板坐标（只对女仆 setPos，绝不对战车 setPos）。 */
    private void attachMaidAtSeat(EntityMaid maid) {
        Vec3 seat = this.localToWorld(MAID_SEAT.x, MAID_SEAT.y, MAID_SEAT.z);
        maid.setPos(this.getX() + seat.x, this.getY() + seat.y, this.getZ() + seat.z);
        maid.setYRot(this.getYRot());
        maid.setYHeadRot(this.getYRot());
    }

    private void clientTick() {
        int fire = this.getFireTicks();
        if (fire > 0) {
            // ★ 极简枪口气浪：1~2 个灰烟，顺枪管方向喷出，无明火、无爆炸
            Vec3 m = this.getMuzzleWorldPos();
            Vec3 dir = this.getBarrelDirection();
            this.level().addParticle(ParticleTypes.CLOUD,
                    m.x + dir.x * 0.35D, m.y + dir.y * 0.35D, m.z + dir.z * 0.35D,
                    dir.x * 0.05D, dir.y * 0.05D, dir.z * 0.05D);
            if (this.random.nextInt(2) == 0) {
                this.level().addParticle(ParticleTypes.SMOKE,
                        m.x + dir.x * 0.5D, m.y + dir.y * 0.5D, m.z + dir.z * 0.5D,
                        dir.x * 0.07D, dir.y * 0.07D, dir.z * 0.07D);
            }
        }

        // 轮子持续滚动：按服务器同步的前向速度累积旋转角度（不再依赖动画文件）。
        double vehicleSpeed = this.getVehicleSpeed();
        if (Math.abs(vehicleSpeed) > 0.001D) {
            this.wheelAngleDeg = (float) ((this.wheelAngleDeg
                    + vehicleSpeed * WHEEL_DEGREES_PER_BLOCK / 20.0D) % 360.0D);
            if (this.wheelAngleDeg < 0.0F) {
                this.wheelAngleDeg += 360.0F;
            }
        }

        // ★ 极简行驶扬尘：仅后轮、每 3~5 tick 一次、极小向后速度
        if (this.tickCount % 4 == 0 && this.random.nextInt(2) == 0
                && Math.abs(this.getVehicleSpeed()) > 0.05D) {
            Vec3 fwd = this.forward();
            for (int i = 4; i <= 5; i++) {          // 后轮 RL / RR
                double[] w = this.wheelWorld(i);
                this.level().addParticle(ParticleTypes.CLOUD,
                        w[0] + (this.random.nextDouble() - 0.5D) * 0.20D,
                        this.getY() + 0.15D,
                        w[2] + (this.random.nextDouble() - 0.5D) * 0.20D,
                        -fwd.x * 0.012D + (this.random.nextDouble() - 0.5D) * 0.008D,
                        0.006D + this.random.nextDouble() * 0.006D,
                        -fwd.z * 0.012D + (this.random.nextDouble() - 0.5D) * 0.008D);
            }
        }

        // 坡度俯仰/侧倾：每 tick 计算并做平滑（前瞻 1.5 格，0.8F / 6°/tick）
        this.updateSlopeTiltClient();
    }

    /**
     * 客户端坡度采样 + 平滑。
     * 车头方向额外前瞻 1.5 格，提前仰头；Mth.lerp 0.8F，每 tick 最多变化 6°。
     */
    private void updateSlopeTiltClient() {
        float targetPitch = 0.0F;
        float targetRoll = 0.0F;
        if (!this.isInWater() && this.isAlive()) {
            double halfLength = Math.max(0.35D, HULL_HALF_L * 0.6D);
            double halfWidth = Math.max(0.30D, HULL_HALF_W * 0.6D);
            double lookAhead = halfLength + 2.5D;                 // ★ 前方上坡检测延伸 2.5 格
            double sideProbe = halfWidth + 1.5D;                  // ★ 侧边延伸 1.5 格
            float front = this.groundYLocal(0.0D, -lookAhead);    // 车头 -Z
            float back = this.groundYLocal(0.0D, halfLength);
            float left = this.groundYLocal(-sideProbe, 0.0D);
            float right = this.groundYLocal(sideProbe, 0.0D);
            double diffPitch = Mth.clamp(front - back, -1.2D, 1.2D);
            double diffRoll = Mth.clamp(right - left, -0.6D, 0.6D);
            targetPitch = Mth.clamp((float) Math.toDegrees(
                    Math.atan2(diffPitch, lookAhead + halfLength)), -15.0F, 15.0F);
            targetRoll = Mth.clamp((float) Math.toDegrees(
                    Math.atan2(diffRoll, sideProbe * 2.0D)), -15.0F, 15.0F);
        }
        // ★ 齿轮多段步进：每 tick 固定 3°，不用 lerp、不瞬间赋值
        // ★ 上坡（目标更大）固定 2.0°/tick，抬头细密；下坡沿用原步长
        this.renderPitch = stepTowardsAsymmetric(this.renderPitch, targetPitch, 15.0F, 2.0F);
        this.renderRoll = stepTowards(this.renderRoll, targetRoll, 2.0F);
    }

    /** 非对称步进：上坡用 upStep，下坡用 downStep。 */
    private static float stepTowardsAsymmetric(float current, float target,
                                               float upStep, float downStep) {
        if (target > current) {
            return Math.min(target, current + upStep);
        }
        if (target < current) {
            return Math.max(target, current - downStep);
        }
        return current;
    }

    /** 固定步长逼近：每 tick 最多变化 step 度。 */
    private static float stepTowards(float current, float target, float step) {
        if (target > current) {
            return Math.min(target, current + step);
        }
        if (target < current) {
            return Math.max(target, current - step);
        }
        return current;
    }

    private static float limitStep(float next, float current, float maxStep) {
        return current + Mth.clamp(next - current, -maxStep, maxStep);
    }

    /**
     * 车体局部坐标下的真实地面高度（客户端坡度采样用）。
     * ★ 车轮悬空时向下深扫到 y-32，找到第一个实心方块；
     *   完全找不到（纯悬崖）则返回一个很低的虚拟高度，
     *   这样边缘轮会产生巨大高度差，车体才会真实低头/侧倾。
     */
    private float groundYLocal(double localX, double localZ) {
        Vec3 offset = this.localToWorld(localX, 0.0D, localZ);
        // ★ 起点抬高到 y+6：上坡时前方方块顶面可能高于 +3，避免采样被截断
        Vec3 from = new Vec3(this.getX() + offset.x, this.getY() + 6.0D,
                this.getZ() + offset.z);
        Vec3 to = new Vec3(from.x, this.getY() - 32.0D, from.z);
        BlockHitResult hit = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return (float) hit.getLocation().y;
        }
        // 下方全是空气：视为极深悬崖（触发明显的低头/侧倾）
        return (float) (this.getY() - 16.0D);
    }

    private void serverTick() {
        if (this.fireCooldown > 0) this.fireCooldown--;
        if (this.fireTicks > 0) this.fireTicks--;
        // 机械单位：强制清除受击红闪计时
        this.hurtTime = 0;

        // ===== 目标清理（死锁修复）+ 持续索敌（带视线检查）=====
        LivingEntity target = this.getTarget();
        if (target != null && (!target.isAlive() || target.isRemoved()
                || this.distanceTo(target) > TARGET_RANGE
                || !this.hasCombatSight(target))) {
            // 目标死亡/移除/跑远/被墙挡住：立刻清空，底盘继续跟随玩家
            this.setTarget(null);
            target = null;
        }
        if (this.tickCount % SCAN_INTERVAL == 0) {
            LivingEntity scanned = this.findReinforcementTarget();
            if (scanned != null) {
                this.setTarget(scanned);
                target = scanned;
            }
            this.enemyNearby = scanned != null;
        }

        // 底盘由 AI 状态机驱动
        this.autoMode = true;
        double[] ai = this.aiDrive();
        double throttle = ai[0];
        double steerInput = ai[1];
        this.updateSteering(steerInput, throttle);
        this.updateSpeed(throttle);
        this.updateSuspensionAndMove(false);
        this.updateTurret();

        // ★ 纯卡死判定：本 tick 位移 < 0.05 格 且 确实有移动意图，才累计卡死
        double movedX = this.getX() - this.lastX;
        double movedZ = this.getZ() - this.lastZ;
        double movedDistance = Math.sqrt(movedX * movedX + movedZ * movedZ);
        Vec3 movement = this.getDeltaMovement();
        boolean wantsToMove = movement.x * movement.x + movement.z * movement.z > 1.0E-6D;
        if (movedDistance < 0.05D && wantsToMove) {
            this.stuckTicks++;
        } else {
            this.stuckTicks = 0;
        }
        this.lastX = this.getX();
        this.lastZ = this.getZ();

        // 脱战回血：无粒子/无音效，仅数值恢复
        if (!this.level().isClientSide && this.isAlive()) {
            try {
                int wait = com.tlmformation.config.TankConfig.REGEN_WAIT_TICKS.get();
                int interval = com.tlmformation.config.TankConfig.REGEN_INTERVAL.get();
                double amount = com.tlmformation.config.TankConfig.REGEN_AMOUNT.get();
                if (this.level().getGameTime() - this.lastHurtGameTime >= wait
                        && this.getHealth() < this.getMaxHealth()) {
                    if (++this.regenTimer >= interval) {
                        this.regenTimer = 0;
                        this.setHealth(Math.min(this.getMaxHealth(),
                                this.getHealth() + (float) amount));
                    }
                } else {
                    this.regenTimer = 0;
                }
            } catch (Exception ignored) {
            }
        }

        if (this.autoMode) {
            this.aiCombat();
        }

        // 严禁原版生物扭头干扰：车体/头部 rot 永远等于车头 yaw
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();

        this.entityData.set(DATA_SPEED, (float) this.speed);
        this.entityData.set(DATA_STEER, (float) this.steerDeg);
        this.entityData.set(DATA_TURRET_YAW, (float) this.turretYaw);
        this.entityData.set(DATA_TURRET_PITCH, (float) this.turretPitch);
        this.entityData.set(DATA_FIRE, this.fireTicks);
        this.entityData.set(DATA_AUTO, this.autoMode);

        // 调试输出：每 3 秒打印一次速度/目标/炮塔角度，便于确认服务器端数值。
        if (this.tickCount % 60 == 0) {
            System.out.println("[Debug] HAVOC 速度=" + String.format("%.2f", this.speed)
                    + " 跟随距离=" + this.getFollowDistance()
                    + " 目标=" + (target == null ? "无" : target.getName().getString())
                    + " 炮塔Yaw=" + String.format("%.1f", this.turretYaw)
                    + " 炮塔Pitch=" + String.format("%.1f", this.turretPitch));
        }
    }

    // 机械单位：受击绝不闪红 —— 实体侧每 tick 清零 hurtTime，
    // 渲染侧由 HavocRenderer#getPackedOverlay / getRenderColor 强制 NO_OVERLAY + 纯白。

    // ===== 驾驶模型（自行车模型，只沿车头走） =====
    private void updateSteering(double steerInput, double throttle) {
        double maxSteer = MAX_STEER_DEG;
        // 高速自动收舵：δmax = atan(L * a_lat_max / v²)，a_lat_max ≈ 7
        if (Math.abs(this.speed) > 0.5) {
            double speedLimit = Math.toDegrees(Math.atan(WHEELBASE * 7.0 / (this.speed * this.speed + 1.0e-3)));
            maxSteer = Math.min(MAX_STEER_DEG, Math.max(6.0, speedLimit));
        }
        double target = Mth.clamp(steerInput * MAX_STEER_DEG, -maxSteer, maxSteer);
        // 汽车式平滑转向（Mth.rotLerp 最短角度插值）
        this.steerDeg = Mth.rotLerp(0.25F, (float) this.steerDeg, (float) target);
        // yawRate = v * tan(δ) / L ；倒车时 v<0 自动反向
        double yawRate = this.speed * Math.tan(Math.toRadians(this.steerDeg)) / WHEELBASE;
        double newYaw = this.getYRot() + (float) Math.toDegrees(yawRate * 0.05);
        this.setYRot((float) Mth.wrapDegrees(newYaw));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    private void updateSpeed(double throttle) {
        // throttle 直接作为“目标速度比例”：1.0=满速，0.3=30% 龟速
        // 5 格危险撤退时解除倒车限速，按最大前进速度全速倒车
        double reverseMax = this.retreating ? MAX_SPEED : MAX_REVERSE;
        double target = throttle >= 0.0D ? throttle * MAX_SPEED : throttle * reverseMax;
        if (target > this.speed) {
            this.speed = Math.min(target, this.speed + ACCEL * 0.05D);
        } else {
            this.speed = Math.max(target, this.speed - BRAKE * 0.05D);
        }
    }

    private Vec3 forward() {
        // 与渲染朝向一致：模型 -Z 车头经 GeckoLib(180° - yaw) 旋转后 = vanilla 前向
        return new Vec3(0, 0, 1).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
    }

    /**
     * 模型局部坐标(格) -> 世界偏移。
     * GeckoLib 实体渲染器实际做的是 Y 轴旋转 (180° - entityYaw)，所以模型 -Z 车头
     * 会对齐到 vanilla 前向 (-sin yaw, 0, cos yaw)。
     */
    private Vec3 localToWorld(double localX, double localY, double localZ) {
        double angle = Math.toRadians(180.0D - this.getYRot());
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(
                localX * cos + localZ * sin,
                localY,
                -localX * sin + localZ * cos);
    }

    /** 供渲染器做坡度采样用（公开版）。 */
    public Vec3 localToWorldOffset(double localX, double localY, double localZ) {
        return this.localToWorld(localX, localY, localZ);
    }

    private double[] wheelWorld(int i) {
        Vec3 offset = this.localToWorld(WHEEL_X[i], 0.0D, WHEEL_Z[i]);
        return new double[]{this.getX() + offset.x, this.getY(), this.getZ() + offset.z};
    }

    private void updateSuspensionAndMove(boolean freezeHorizontal) {
        // ★ 已卡进方块：立刻向上微调 0.5~1 格脱困（依然只用 move）
        this.unstuckIfInsideBlock();

        Vec3 fwd = this.forward();
        Vec3 delta = freezeHorizontal ? Vec3.ZERO : fwd.scale(this.speed * 0.05);

        // ===== 两栖浮渡：水里不沉底，车体吃水 0.7 格，炮塔露出水面 =====
        if (this.isInWater()) {
            double surface = this.waterSurfaceY();
            double targetY = surface - 0.70D;
            double vy = Mth.clamp((targetY - this.getY()) * 0.25D, -0.10D, 0.12D);
            this.moveWithCollision(new Vec3(delta.x * 0.7D, vy, delta.z * 0.7D));
            // 水阻
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.85D, 0.92D, 0.85D));
            return;
        }

        int grounded = 0;
        double groundSum = 0.0;
        for (int i = 0; i < 6; i++) {
            double[] w = this.wheelWorld(i);
            Vec3 from = new Vec3(w[0], w[1] + 1.2, w[2]);
            Vec3 to = new Vec3(w[0], w[1] - 1.2, w[2]);
            BlockHitResult hit = this.level().clip(new ClipContext(from, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.BLOCK) {
                grounded++;
                groundSum += hit.getLocation().y;
            }
        }
        double vy = this.getDeltaMovement().y;
        if (grounded > 0) {
            double targetY = groundSum / grounded;
            vy = Mth.clamp((targetY - this.getY()) * 0.45, -0.4, 0.4);
            if (this.getY() > targetY + 0.6) vy = -0.3; // 从高处落下
        } else {
            vy = Math.max(-0.6, vy - 0.08);
        }
        this.moveWithCollision(new Vec3(delta.x, vy, delta.z));
        if (grounded > 0) this.setOnGround(true);
    }

    /**
     * ★ 只改速度，不 setPos：先按 X/Z 轴分离做预碰撞检测，
     * 被墙挡住的轴清零（沿墙滑动），最终仍由原版 move + AABB 会话碰撞。
     */
    private void moveWithCollision(Vec3 desired) {
        double dx = desired.x;
        double dy = desired.y;
        double dz = desired.z;
        AABB box = this.getBoundingBox();
        if (dx != 0.0D || dz != 0.0D) {
            if (!this.level().noCollision(this, box.move(dx, 0.0D, dz))) {
                // ★ 高度差 <= 1.8 格视为可尝试攀爬的台阶/斜坡：放行，让原版 move 处理
                double stepTop = this.obstacleTopAt(this.getX() + dx, this.getZ() + dz);
                if (stepTop - this.getY() <= 1.8D) {
                    this.setDeltaMovement(dx, dy, dz);
                    this.move(MoverType.SELF, this.getDeltaMovement());
                    return;
                }
                // 2 格及以上：按轴分离，被挡的轴清零（沿墙滑动）
                boolean xFree = this.level().noCollision(this, box.move(dx, 0.0D, 0.0D));
                boolean zFree = this.level().noCollision(this, box.move(0.0D, 0.0D, dz));
                if (xFree) {
                    dz = 0.0D;
                } else if (zFree) {
                    dx = 0.0D;
                } else {
                    dx = 0.0D;
                    dz = 0.0D;
                }
            }
        }
        this.setDeltaMovement(dx, dy, dz);
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    /** 卡进方块时向上微调最多 1 格脱困（不使用 setPos）。 */
    private void unstuckIfInsideBlock() {
        if (this.level().noCollision(this, this.getBoundingBox())) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            this.move(MoverType.SELF, new Vec3(0.0D, 0.5D, 0.0D));
            if (this.level().noCollision(this, this.getBoundingBox())) {
                return;
            }
        }
    }

    /** 战车附近水面高度（找不到水面时按车体当前位置估算）。 */
    private double waterSurfaceY() {
        BlockPos base = this.blockPosition();
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos pos = base.offset(0, dy, 0);
            if (this.level().getFluidState(pos).is(FluidTags.WATER)) {
                return pos.getY() + 0.9D;
            }
        }
        return this.getY() + 0.4D;
    }

    // ===== 炮塔（360° 无死角 + 空闲警戒扫视） =====
    private void updateTurret() {
        LivingEntity target = this.getTarget();
        if (target != null) {
            double[] aim = this.aimAngles(target);
            this.turretYaw = this.approachDegrees(this.turretYaw, aim[0], 6.0);
            this.turretPitch = this.approachDegrees(this.turretPitch,
                    Mth.clamp(aim[1], TURRET_PITCH_MIN, TURRET_PITCH_MAX), 2.0);
        } else {
            // ★ 无敌人：无论 FOLLOW 还是 PATROL，炮塔随机扫视警戒
            if (this.scanTimer-- <= 0) {
                this.scanYawTarget = this.random.nextDouble() * 360.0D - 180.0D;
                this.scanPitchTarget = -4.0D + this.random.nextDouble() * 12.0D;
                this.scanTimer = 60 + this.random.nextInt(41);   // 60~100 tick
            }
            this.turretYaw = this.approachDegrees(this.turretYaw, this.scanYawTarget, 2.0);
            this.turretPitch = this.approachDegrees(this.turretPitch,
                    Mth.clamp(this.scanPitchTarget, TURRET_PITCH_MIN, TURRET_PITCH_MAX), 1.0);
        }
    }

    /** 瞄准/索敌视线：从炮口看向目标身体中心（不是脚底），高大体型怪也能准确锁定。 */
    private boolean hasCombatSight(LivingEntity target) {
        Vec3 from = this.getMuzzleWorldPos();
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        BlockHitResult hit = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(to) < 1.0E-6D;
    }

    private double[] aimAngles(LivingEntity target) {        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 to = targetPos.subtract(this.getMuzzleWorldPos());
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        // vanilla yaw 约定：朝向 (dx,dz) 的 yaw = atan2(-dx, dz)
        double worldYaw = Math.toDegrees(Math.atan2(-to.x, to.z));
        double relYaw = Mth.wrapDegrees(worldYaw - this.getYRot());
        double pitch = Math.toDegrees(Math.atan2(to.y, horizontal)); // 正=抬高
        return new double[]{relYaw, pitch};
    }

    /** wrapDegrees 最短路径逼近，支持 360° 连续旋转。 */
    private double approachDegrees(double current, double target, double step) {
        double delta = Mth.wrapDegrees(target - current);
        if (Math.abs(delta) <= step) {
            return Mth.wrapDegrees(target);
        }
        return Mth.wrapDegrees(current + Math.signum(delta) * step);
    }

    // ===== AI 驾驶：游击战（追击 / 环绕 / 拉扯）+ 跟随 / 巡逻 =====
    private double[] aiDrive() {
        if (this.recalled) {
            this.setDeltaMovement(0, 0, 0);
            this.getNavigation().stop();
            this.setTarget(null);
            return new double[]{0.0D, 0.0D};
        }
        Player owner = this.ownerPlayer();
        if (owner == null) {
            return new double[]{0.0D, 0.0D};
        }

        // 1) ★ 纯卡死脱困：整整 20 tick 原地动不了，才强制左/右偏转 90° 开出去
        if (this.stuckTicks >= 20) {
            this.stuckTicks = 0;
            this.escapeTicks = 25;
            this.tryUnstuckByMove();
            this.setYRot(Mth.wrapDegrees(this.getYRot()
                    + (this.random.nextBoolean() ? 90.0F : -90.0F)));
        }
        if (this.escapeTicks > 0) {
            this.escapeTicks--;
            return new double[]{1.0D, this.random.nextBoolean() ? 1.0D : -1.0D};
        }

        // 2) ★ 5 格极限拉扯：任何怪贴近 5 格 → 解除限速全速倒车，炮塔绝不停火
        LivingEntity closeThreat = this.nearestThreat(10.0D);
        if (closeThreat != null) {
            this.retreating = true;
            this.holdFire = false;

            Vec3 away = this.position().subtract(closeThreat.position());
            Vec3 escapePoint = this.position().add(away.scale(8.0D));
            return new double[]{-1.0D, this.steerTo(escapePoint)};
        }
        this.retreating = false;

        // 4) 有敌人：游击战（>20 追击 / 10~20 环绕 / <8 拉扯）
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            return this.combatDrive(target);
        }
        this.holdFire = false;

        // 5) 无敌人：状态机跟随 / 巡逻
        return switch (this.getVehicleState()) {
            case PATROL -> this.drivePatrol(owner);
            case FOLLOW -> this.driveFollow(owner);
        };
    }

    /** 游击战驾驶：远追击、中环绕；5 格内由 aiDrive 全速倒车处理。 */
    private double[] combatDrive(LivingEntity target) {
        double dist = this.distanceTo(target);
        this.holdFire = false;

        // >20 格：正常追击
        if (dist > 20.0D) {
            return new double[]{1.0D, this.steerTo(target.position())};
        }

        // 5~20 格：环绕模式（切线点 + 周期换向）
        if (this.tickCount % 100 == 0) {
            this.orbitSign = -this.orbitSign;
        }
        Vec3 to = target.position().subtract(this.position());
        Vec3 tangent = new Vec3(-to.z, 0.0D, to.x);
        if (tangent.lengthSqr() < 1.0E-6D) {
            tangent = new Vec3(1.0D, 0.0D, 0.0D);
        }
        tangent = tangent.normalize().scale(this.orbitSign);
        Vec3 orbitPoint = target.position().add(tangent.scale(15.0D));
        return new double[]{0.75D, this.steerTo(orbitPoint)};
    }

                /** 该水平坐标处最高实心方块的顶部 Y（用于区分 1 格台阶与 2 格墙）。 */
    private double obstacleTopAt(double x, double z) {
        double best = this.getY();
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos pos = BlockPos.containing(x, this.getY() + dy, z);
            var shape = this.level().getBlockState(pos)
                    .getCollisionShape(this.level(), pos);
            if (!shape.isEmpty()) {
                best = Math.max(best, pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y));
            }
        }
        return best;
    }

            /** 卡在方块里时用 move 向上微调脱困（绝不 setPos）。 */
    private void tryUnstuckByMove() {
        for (int i = 0; i < 4; i++) {
            if (this.level().noCollision(this, this.getBoundingBox())) {
                return;
            }
            this.move(MoverType.SELF, new Vec3(0.0D, 0.25D, 0.0D));
        }
    }

    /** FOLLOW：>20 格全速追赶，<=20 格在玩家附近龟速游荡。 */
    private double[] driveFollow(Player owner) {
        double distanceToPlayer = this.distanceTo(owner);
        double safe = 10.0D;
        double slow = 16.0D;
        try {
            safe = com.tlmformation.config.TankConfig.SAFE_FOLLOW_DISTANCE.get();
            slow = com.tlmformation.config.TankConfig.SLOW_FOLLOW_DISTANCE.get();
        } catch (Exception ignored) {
        }
        if (distanceToPlayer < safe) {
            Vec3 away = this.position().subtract(owner.position());
            return new double[]{distanceToPlayer < safe * 0.55D ? -0.45D : 0.0D,
                    this.steerTo(this.position().add(away))};
        }
        double chaseDistance = Math.max(20.0D, this.getFollowDistance());
        boolean playerSprinting = owner.isSprinting();
        boolean playerMoving = playerSprinting
                || owner.getDeltaMovement().horizontalDistanceSqr() > 0.0025D;

        if (playerMoving || distanceToPlayer > chaseDistance) {
            this.wanderTarget = null;
            double throttle = (playerSprinting || distanceToPlayer > 30.0D)
                    ? 1.0D
                    : Mth.clamp(0.30D + (distanceToPlayer - chaseDistance) / 20.0D,
                            0.30D, 0.65D);
            return new double[]{throttle, this.steerTo(owner.position())};
        }
        return this.driveWanderAround(owner);
    }

    /** PATROL：玩家 20 格内 0.3 倍速游走找怪，带防坠落。 */
    private double[] drivePatrol(Player owner) {
        LivingEntity target = this.getTarget();
        if (target != null) {
            return new double[]{0.30D, this.steerTo(target.position())};
        }
        if (this.position().distanceTo(owner.position()) > 20.0D) {
            return new double[]{0.30D, this.steerTo(owner.position())};
        }
        return this.driveWanderAround(owner);
    }

    /** 玩家附近 10~20 格随机游走（龟速 0.3）。 */
    private double[] driveWanderAround(Player owner) {
        if (this.getTarget() != null || this.enemyNearby) {
            return new double[]{0.0D, 0.0D};
        }
        if (this.wanderTarget == null || this.wanderCooldown-- <= 0
                || this.position().distanceTo(this.wanderTarget) < 3.0D) {
            this.pickWanderTarget(owner);
        }
        if (this.wanderTarget != null && this.position().distanceTo(this.wanderTarget) > 3.0D) {
            return new double[]{0.30D, this.steerTo(this.wanderTarget)};
        }
        return new double[]{0.0D, 0.0D};
    }

    /** 计算朝目标点的转向（-1..1）。 */
    private double steerTo(Vec3 target) {
        Vec3 to = target.subtract(this.position());
        double desiredYaw = Math.toDegrees(Math.atan2(-to.x, to.z));
        return Mth.clamp(Mth.wrapDegrees(desiredYaw - this.getYRot()) / 45.0D, -1.0D, 1.0D);
    }

    /** 以玩家为中心，在 10~20 格内随机挑一个游走点。 */
    private void pickWanderTarget(Player owner) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double radius = WANDER_MIN_RADIUS
                    + this.random.nextDouble() * (WANDER_MAX_RADIUS - WANDER_MIN_RADIUS);
            Vec3 candidate = owner.position().add(
                    Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            BlockPos pos = BlockPos.containing(candidate.x, candidate.y, candidate.z);
            // 目标点必须是可通行位置（脚部与头部都非实心），禁止钻进方块
            if (this.level().getBlockState(pos).isAir()
                    && this.level().getBlockState(pos.above()).isAir()) {
                this.wanderTarget = candidate;
                this.wanderCooldown = 100 + this.random.nextInt(60);
                return;
            }
        }
        this.wanderTarget = null;
        this.wanderCooldown = 40;
    }

    /** 前方 2 格内是否悬崖（脚下悬空 >3 格）或岩浆。 */
    private boolean dangerAhead() {
        Vec3 front = this.position().add(this.forward().scale(2.0D));
        BlockPos fluidPos = BlockPos.containing(front.x, this.getY() + 0.6D, front.z);
        net.minecraft.world.level.material.FluidState fluid =
                this.level().getFluidState(fluidPos);
        if (fluid.is(FluidTags.LAVA)) {
            return true;                       // 岩浆：绝不冲
        }
        if (fluid.is(FluidTags.WATER)) {
            return false;                      // 两栖：可以下水
        }
        Vec3 from = new Vec3(front.x, this.getY() + 1.0D, front.z);
        Vec3 to = new Vec3(front.x, this.getY() - 8.0D, front.z);
        BlockHitResult hit = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;                       // 前方完全是空的：悬崖
        }
        double groundY = hit.getLocation().y;
        if (groundY < this.getY() - 3.0D) {
            return true;                       // 落差 >3 格：勒马
        }
        // 密集上坡：前方地面比车身高出 >1.2 格，强制停车换方向（否则会撞进方块）
        return groundY > this.getY() + 1.2D;   // 落差 >3 格：勒马
    }

    /** 最近的“有视线”的敌对目标（防穿墙）。 */
    @Nullable
    private LivingEntity nearestThreat(double radius) {
        List<LivingEntity> threats = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                entity -> this.isValidCombatTarget(entity) && this.hasCombatSight(entity));
        return threats.stream()
                .min(java.util.Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    @Nullable
        private void aiCombat() {
        LivingEntity target = this.getTarget();

        // A player-owned reinforcement never fires on its owner or on players.
        if (target != null && !this.isValidCombatTarget(target)) {
            this.setTarget(null);
            target = null;
        }

        // 索敌确认：每 10 tick 检查，目标为空/死亡就主动索敌
        if (target == null || !target.isAlive()) {
            if (this.tickCount % 10 == 0) {
                LivingEntity found = this.findReinforcementTarget();
                if (found != null) {
                    this.setTarget(found);
                    target = found;
                }
            }
            if (target == null) {
                return;
            }
        }

        // 开火判定：距离 ≤ 50 格，炮塔 Yaw / Pitch 误差均 < 10°
        // ★ 战术撤退中也绝不停火（不再按距离/状态截断开火）
        double dist = this.distanceTo(target);
        if (dist > FIRE_RANGE || this.fireCooldown > 0) {
            return;
        }
        double[] aim = this.aimAngles(target);
        double yawErr = Math.abs(Mth.wrapDegrees(aim[0] - this.turretYaw));
        double pitchErr = Math.abs(aim[1] - this.turretPitch);
        if (yawErr <= FIRE_ANGLE_TOLERANCE && pitchErr <= FIRE_ANGLE_TOLERANCE) {
            this.fireWeapon();
        }
    }

    private boolean isValidCombatTarget(LivingEntity candidate) {
        if (!candidate.isAlive() || candidate == this) {
            return false;
        }
        if (this.ownerUUID == null) {
            return true;
        }
        if (candidate.getUUID().equals(this.ownerUUID)) {
            return false;
        }
        if (candidate instanceof Player) {
            return false;
        }
        return candidate instanceof Enemy;
    }

    @Nullable
    private LivingEntity findReinforcementTarget() {
        if (this.ownerUUID == null) {
            Player nearest = this.level().getNearestPlayer(this, 48.0D);
            return nearest != null && nearest.isAlive() && !nearest.isCreative() ? nearest : null;
        }
        // 50 格索敌 + 视线检查：只打能直接看到的怪（防穿墙/打地下）
        return this.level().getEntitiesOfClass(LivingEntity.class,
                        this.getBoundingBox().inflate(TARGET_RANGE),
                        entity -> this.isValidCombatTarget(entity)
                                && this.hasCombatSight(entity))
                .stream()
                .min(java.util.Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    public void fireWeapon() {
        if (this.level().isClientSide || this.fireCooldown > 0) {
            return; // 伤害只在服务端结算
        }
        if (TankData.requireAmmo()) {
            int cost = TankData.ammoPerShot();
            if (!TankData.consumeVehicleAmmo(this, cost)) {
                Player owner = this.ownerPlayer();
                if (owner != null) {
                    owner.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                    "§c弹药不足：请右键战术小车装填无人战车子弹"),
                            true);
                }
                return;
            }
            Player owner = this.ownerPlayer();
            if (owner != null) {
                // 持久化库存与车上弹药保持一致，召回时不会丢剩余子弹
                TankData.setStock(owner, TankData.vehicleAmmo(this));
                if (owner instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    ArmorReinforcementManager.sync(serverPlayer);
                }
            }
        }
        // ★ 连射炮：6~9 tick 一发（保留当前射速），伤害 9
        this.fireCooldown = 6 + this.random.nextInt(4);
        this.fireTicks = 2;
        this.triggerAnim("fire", "fire_recoil");
        Vec3 muzzle = this.getMuzzleWorldPos();

        // ★ 连射炮音效（降低音量防高频爆音）
        this.level().playSound(null, muzzle.x, muzzle.y, muzzle.z,
                com.tlmformation.sound.ModSounds.AUTOCANNON.get(),
                net.minecraft.sounds.SoundSource.HOSTILE, 0.1F, 1.0F);    // ★ 默认 0.1 音量
        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Vec3 dir = this.getBarrelDirection();
            // ★ count=0 时，后三个参数就是精确速度：顺着枪管喷出的淡气浪
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    muzzle.x + dir.x * 0.35D, muzzle.y + dir.y * 0.35D, muzzle.z + dir.z * 0.35D,
                    0, dir.x * 0.05D, dir.y * 0.05D, dir.z * 0.05D, 1.0D);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    muzzle.x + dir.x * 0.5D, muzzle.y + dir.y * 0.5D, muzzle.z + dir.z * 0.5D,
                    0, dir.x * 0.07D, dir.y * 0.07D, dir.z * 0.07D, 1.0D);
        }

        // ===== 范围爆炸炮弹：不再单点伤害，改为敌人脚下 3~5 格 AOE =====
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || this.distanceTo(target) > FIRE_RANGE) {
            return;
        }
        // ★ 自定义曳光/枪口闪光/爆炸：发包含义客户端渲染
        Vec3 impact = target.position();
        com.tlmformation.network.NetworkHandler.sendHavocEffect(this, 1, muzzle, Vec3.ZERO);
        com.tlmformation.network.NetworkHandler.sendHavocEffect(this, 0, muzzle, impact);
        com.tlmformation.network.NetworkHandler.sendHavocEffect(this, 2, impact, Vec3.ZERO);
        this.explodeAt(impact);              // 敌人脚下坐标（伤害仍服务端结算）
    }

    /**
     * 范围爆炸炮：3~5 格半径，仅伤害敌对生物（Monster/Enemy），
     * 不破坏方块、不伤害玩家/女仆/战车。
     */
    private void explodeAt(Vec3 impact) {
        double radius = EXPLOSION_RADIUS_MIN + this.random.nextDouble()
                * (EXPLOSION_RADIUS_MAX - EXPLOSION_RADIUS_MIN);
        Vec3 center = impact.add(0.0D, 0.4D, 0.0D);

        int kills = 0;
        List<LivingEntity> victims = this.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - radius, center.y - radius, center.z - radius,
                        center.x + radius, center.y + radius, center.z + radius),
                entity -> entity != this
                        && entity.isAlive()
                        && entity instanceof Enemy
                        && !(entity instanceof Player)
                        && !(entity instanceof EntityMaid)     // ★ 双向防误伤：绝不打女仆
                        && this.hasCombatSight(entity));
        for (LivingEntity victim : victims) {
            victim.hurt(this.damageSources().mobAttack(this), AOE_DAMAGE);
            if (victim.isDeadOrDying()) {
                kills++;
            }
        }
        if (kills > 0) {
            this.entityData.set(DATA_KILLS, this.entityData.get(DATA_KILLS) + kills);
        }

        // ★ 极简淡粉尘落点特效（无明火、无爆炸粒子）
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    center.x, center.y + 0.2D, center.z, 10,
                    radius * 0.5D, 0.2D, radius * 0.5D, 0.01D);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    center.x, center.y + 0.15D, center.z, 3,
                    radius * 0.35D, 0.15D, radius * 0.35D, 0.006D);
            serverLevel.playSound(null, center.x, center.y, center.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.1F, 0.8F);
        }
    }

    // ===== 炮口 / 炮管（模型空间 px -> 世界，含炮塔 yaw/pitch） =====
    private static final double[] MUZZLE_PX = {0, 49.004, -73};
    private static final double MUZZLE_GUN_PIVOT_PX = 49.324;
    private static final double MUZZLE_GUN_PIVOT_Z = -2.0;
    private static final double TURRET_PIVOT_Y = 51.324;
    private static final double TURRET_PIVOT_Z = 2.0;

    public Vec3 getMuzzleWorldPos() {
        return muzzleWorldPos(this.getTurretPitch());
    }

    private Vec3 muzzleWorldPos(double pitchDeg) {
        double y = MUZZLE_PX[1], z = MUZZLE_PX[2];
        // 1) 绕炮耳 X 轴俯仰（+pitch = 抬高）
        double pitch = Math.toRadians(pitchDeg);
        double dy = y - MUZZLE_GUN_PIVOT_PX;
        double dz = z - MUZZLE_GUN_PIVOT_Z;
        double ry = dy * Math.cos(pitch) - dz * Math.sin(pitch);
        double rz = dy * Math.sin(pitch) + dz * Math.cos(pitch);
        y = MUZZLE_GUN_PIVOT_PX + ry;
        z = MUZZLE_GUN_PIVOT_Z + rz;
        // 2) 绕炮塔 Y 轴（骨骼空间 yaw = -turretYaw，与模型骨骼一致）
        double yaw = Math.toRadians(-this.getTurretYaw());
        double x = MUZZLE_PX[0];
        dy = y - TURRET_PIVOT_Y;
        dz = z - TURRET_PIVOT_Z;
        double rx = x * Math.cos(yaw) + dz * Math.sin(yaw);
        rz = -x * Math.sin(yaw) + dz * Math.cos(yaw);
        y = TURRET_PIVOT_Y + dy;
        z = TURRET_PIVOT_Z + rz;
        // 3) 模型 px -> 模型格 -> 世界（含 0.75 视觉缩放）
        double scale = RENDER_SCALE;
        Vec3 local = new Vec3(rx / 16.0D * scale, y / 16.0D * scale, z / 16.0D * scale);
        Vec3 offset = this.localToWorld(local.x, local.y, local.z);
        return this.position().add(offset);
    }

    public Vec3 getBarrelDirection() {
        Vec3 a = this.muzzleWorldPos(this.getTurretPitch());
        Vec3 b = this.muzzleWorldPos(this.getTurretPitch() + 1.0);
        Vec3 d = b.subtract(a);
        return d.lengthSqr() > 1.0e-9 ? d.normalize() : this.forward();
    }

    // ===== 玩家交互 / 控制 =====
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.ownerUUID != null && !this.isOwnedBy(player)) {
            // Only the reinforcing player may drive / configure the vehicle.
            return InteractionResult.PASS;
        }
        // ===== 马鞍右键：把“周围最近的女仆”伪挂载到后座（纯坐标锁定） =====
        if (player.isHolding(net.minecraft.world.item.Items.SADDLE)) {
            if (this.level().isClientSide) {
                return InteractionResult.SUCCESS;
            }
            EntityMaid nearest = this.level().getEntitiesOfClass(EntityMaid.class,
                            this.getBoundingBox().inflate(6.0D),
                            maid -> maid.isAlive()
                                    && (this.ownerUUID == null
                                    || player.getUUID().equals(maid.getOwnerUUID())))
                    .stream()
                    .min(java.util.Comparator.comparingDouble(maid -> this.distanceToSqr(maid)))
                    .orElse(null);
            if (nearest != null) {
                // 只记录 UUID；女仆保持独立实体，Tick 里暴力锁坐标
                this.attachMaid(nearest.getUUID());
                nearest.setNoAi(true);                 // ★ AI 禁用标记
                nearest.setInSittingPose(true);
                nearest.noPhysics = true;              // ★ 关闭物理碰撞箱
                nearest.setNoGravity(true);            // ★ 关闭重力，避免下坠挤压
                nearest.getNavigation().stop();        // ★ 强制停止寻路
                this.attachMaidAtSeat(nearest);
                player.displayClientMessage(Component.translatable(
                        "message.tlmformation.armor.maid_boarded"), true);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        // 步战车不提供玩家驾驶
        return InteractionResult.PASS;
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        // ★ 不使用原版乘骑系统：永远没有控制者
        return null;
    }

    /** ★ 彻底废弃原版骑乘：任何实体都不允许成为 passenger。 */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
    }

    /** 后方装甲板乘坐点（局部坐标，单位：格）。 */
    private static final Vec3 MAID_SEAT = new Vec3(0.0D, 0.92D, 1.15D);

    // ===== 碰撞：OBB 8 角 -> AABB 包络（玩家可站车顶） =====
    public HavocObb hullObb() {
        double halfW = HULL_HALF_W;
        double halfL = HULL_HALF_L;
        double height = HULL_HEIGHT;
        double centerZ = HULL_CENTER_Z;
        Vec3 centerOffset = this.localToWorld(0.0D, 0.0D, centerZ);
        return new HavocObb(this.getX() + centerOffset.x, this.getY(),
                this.getZ() + centerOffset.z,
                halfW, height / 2.0D, halfL,
                Math.toRadians(this.getYRot()));
    }

    @Override
    public AABB makeBoundingBox() {
        return this.hullObb().envelope();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // 不参与 vanilla 移动（物理在本类里算）
    @Override
    public void travel(Vec3 travelVector) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("TlmfRecalled", this.recalled);
        tag.putDouble("Speed", this.speed);
        tag.putBoolean("Auto", this.autoMode);
        tag.putDouble("TurretYaw", this.turretYaw);
        tag.putDouble("TurretPitch", this.turretPitch);
        if (this.ownerUUID != null) {
            tag.putUUID("OwnerUUID", this.ownerUUID);
        }
        tag.putInt("FollowDistance", this.getFollowDistance());
        tag.putString("AttachedMaid", this.getAttachedMaidString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.recalled = tag.getBoolean("TlmfRecalled");
        this.speed = tag.getDouble("Speed");
        this.autoMode = tag.getBoolean("Auto");
        this.turretYaw = tag.getDouble("TurretYaw");
        this.turretPitch = tag.getDouble("TurretPitch");
        this.ownerUUID = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
        if (tag.contains("FollowDistance")) {
            this.setFollowDistance(tag.getInt("FollowDistance"));
        }
        this.entityData.set(DATA_ATTACHED_MAID, tag.getString("AttachedMaid"));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // ★ 双向防误伤：玩家 / 女仆 打不动战车
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (attacker instanceof Player || attacker instanceof EntityMaid
                || direct instanceof Player || direct instanceof EntityMaid) {
            return false;
        }
        boolean damaged = super.hurt(source, amount);
        if (damaged) {
            this.lastHurtGameTime = this.level().getGameTime();
            this.regenTimer = 0;
        }
        if (damaged && !this.level().isClientSide) {
            // ★ 装甲受击音效（音量 2.0）
            this.level().playSound(null, this.getX(), this.getY() + 0.8D, this.getZ(),
                    com.tlmformation.sound.ModSounds.HIT_METAL.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE,
                    1.5F, 1.0F + this.random.nextFloat() * 0.15F);
        }
        return damaged;
    }

    // ===== 壮烈死亡自爆（无伤友军，重创怪物） =====
    @Override
    public void die(DamageSource source) {
        Player owner = this.ownerPlayer();
        if (owner != null) {
            TankData.setStock(owner, TankData.vehicleAmmo(this));
            ArmorReinforcementManager.sync((net.minecraft.server.level.ServerPlayer) owner);
        }
        this.selfDestruct();
        super.die(source);
    }

    private void selfDestruct() {
        if (this.level().isClientSide) {
            return;
        }
        Vec3 center = this.position().add(0.0D, 0.6D, 0.0D);

        // ★ 禁止 level.explode：改用自定义爆炸特效包（服务端只做伤害）
        com.tlmformation.network.NetworkHandler.sendHavocEffect(this, 2, center, Vec3.ZERO);

        // 手动 6 格真实伤害：只打 Monster；Player / EntityMaid 直接跳过
        List<LivingEntity> victims = this.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - 6.0D, center.y - 6.0D, center.z - 6.0D,
                        center.x + 6.0D, center.y + 6.0D, center.z + 6.0D),
                entity -> entity != this
                        && entity.isAlive()
                        && entity instanceof Enemy
                        && !(entity instanceof Player)
                        && !(entity instanceof EntityMaid));
        for (LivingEntity victim : victims) {
            victim.hurt(this.damageSources().explosion(this, this), 30.0F);
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            // 只保留淡粉尘烟雾；爆炸本体由自定义发光球渲染
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    center.x, center.y, center.z, 24, 3.0D, 1.2D, 3.0D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    center.x, center.y + 0.4D, center.z, 12, 2.0D, 0.8D, 2.0D, 0.01D);
            serverLevel.playSound(null, center.x, center.y, center.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.1F, 0.7F);
        }
    }
}
