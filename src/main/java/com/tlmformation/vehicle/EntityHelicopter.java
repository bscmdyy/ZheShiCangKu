package com.tlmformation.vehicle;

import com.atsuishio.superbwarfare.data.vehicle.subdata.CameraPos;
import com.atsuishio.superbwarfare.data.vehicle.subdata.SeatInfo;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.tlmformation.network.NetworkHandler;
import com.tlmformation.network.S2CRappelState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector4d;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * UH-60 黑鹰：卓越前线 {@link VehicleEntity} 载具逻辑 + GeckoLib 模型 + 战术索降状态机。
 *
 * <p>索降状态机（{@link #getRappelPhase()}，通过 {@link S2CRappelState} 同步到客户端）：</p>
 * <pre>
 * IDLE(0) ──小键盘1──► DOOR_OPENING(1) 100 tick 开门悬停
 *                          │
 *                          ▼
 *                      ROPES_OUT(2)  绳索抛出（8 tick 抛绳缓冲）
 *                          │
 *                          ▼
 *                      DESCENDING(3) 乘客沿 117/118 骨骼绳索下滑
 *                          │  全部落地
 *                          ▼
 *                      CLOSING(4)    关舱门 30 tick ──► IDLE
 *
 * 任意阶段异常（被毁 / 下坠 / 飞走 / 卸载）──► ABORTING(5)（收回绳索、恢复 AI）──► IDLE
 * </pre>
 */
public class EntityHelicopter extends VehicleEntity implements GeoEntity {
    // ===================== 索降状态 =====================
    public static final int RAPPEL_IDLE = 0;
    public static final int RAPPEL_DOOR_OPENING = 1;
    public static final int RAPPEL_ROPES_OUT = 2;
    public static final int RAPPEL_DESCENDING = 3;
    public static final int RAPPEL_CLOSING = 4;
    public static final int RAPPEL_ABORTING = 5;
    /** 索升：舱门开 + 抛绳（等待女仆靠近）。 */
    public static final int RAPPEL_ASCEND_ROPES = 6;
    /** 索升：女仆沿绳向上爬升。 */
    public static final int RAPPEL_ASCENDING = 7;

    /** 模型整体渲染缩放（与 {@code HelicopterGeoRenderer} 保持一致）。 */
    public static final double RENDER_SCALE = 0.84D;
    /** 模型像素 -> 格：GeckoLib 默认 16 px = 1 格，再乘载具渲染缩放。 */
    public static final double MODEL_PIXEL = RENDER_SCALE / 16.0D;
    /** 绳索挂点数量（骨骼 117 / 118）。 */
    public static final int ROPE_COUNT = 2;

    /**
     * 模型自身朝向修正：模型机头在模型空间朝 -Z，卓越前线车辆前向为 +Z，需要整体转 180°。
     *
     * <p>旧模型是靠 {@code main_no_magnet} 骨骼自带的 [0,180,0] 旋转实现的；新版模型删掉了
     * 这个根骨旋转，但 117/118、zuo1–zuo8 都是根级骨骼，若只转机身就会出现“方块跑到机舱外”。
     * 所以在这里做<b>全局</b>修正：绕旧根骨中枢点 {@link #MODEL_ROOT_PIVOT} 旋转，
     * 让机身与所有挂点同框，同时保持机身相对实体原点/OBB 的位置不变。</p>
     */
    public static final double MODEL_YAW_OFFSET_DEG = 180.0D;
    /** 旧模型 main_no_magnet 骨骼的中枢点（模型像素），全局修正的旋转中心。 */
    public static final double[] MODEL_ROOT_PIVOT = {0.0D, 24.5D, -21.0D};

    /** 主驾第一人称相机相对座位骨骼的高度（格）：原版眼高 1.62 × 乘客渲染缩放 0.75。 */
    public static final double CAMERA_EYE_HEIGHT = 1.62D * 0.75D;
    /**
     * 主驾第一人称相机的额外抬高（格）。
     * zuo1 座位骨骼为了坐姿被整体下移了约 0.68 格，相机也会跟着变低，
     * 这里补回来；数值比上一版低 0.2，避免视角顶到驾驶舱顶棚。
     */
    public static final double DRIVER_CAMERA_UP = 0.45D;
    /** 主驾第一人称相机相对座位骨骼向后（-Z，即朝机尾）的偏移（格）。 */
    public static final double DRIVER_CAMERA_BACK = 0.10D;
    /**
     * 横向向驾驶舱中线收拢的比例：0 = 完全贴着 zuo1 座位（偏右），1 = 完全居中。
     * 0.7 约等于坐在驾驶舱正中、离右侧玻璃一拳的位置。
     */
    public static final double DRIVER_CAMERA_CENTER = 0.7D;

    /** 开/关舱门动画（定义于 helicopter_rotor.animation.json）。 */
    private static final RawAnimation DOOR_OPEN_ANIM =
            RawAnimation.begin().thenPlayAndHold("door_open");
    private static final RawAnimation DOOR_CLOSE_ANIM =
            RawAnimation.begin().thenPlayAndHold("door_close");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ===== 索降状态（服务端权威；客户端由 S2CRappelState 写入） =====
    private int rappelPhase = RAPPEL_IDLE;
    private int rappelTimer;
    private int rappelRemain;

    // ===== 仅客户端：索降期间的机身锁点（由 S2CRappelState 下发，见 applyClientRappelHold） =====
    private boolean clientHoldActive;
    private double clientHoldX;
    private double clientHoldY;
    private double clientHoldZ;

    // ===== 仅服务端：悬停锁定 & 索降乘客 =====
    @Nullable
    private Vec3 rappelLockPos;
    private float rappelLockYaw;
    private float rappelLockPitch;
    private final List<HelicopterRappelManager.Descender> rappelDescenders = new ArrayList<>();
    private final List<HelicopterRappelManager.Ascender> rappelAscenders = new ArrayList<>();
    /** 索降排队：还没轮到下机的乘客（仍在座位上等）。 */
    private final List<Entity> rappelQueue = new ArrayList<>();
    /** 上一次流水线放行/出发的 tick（控制 0.5 秒间隔）。 */
    private int rappelPipelineTick;
    /** 流水线已放行人数（左右绳交替用）。 */
    private int rappelSpawned;
    /**
     * 是否连驾驶位（座位 0）的乘客也一起索降。
     *
     * <p>玩家手动索降时驾驶员必须留机（false）；AI 空投只有 1 名女仆时她就是驾驶员，
     * 若仍按“驾驶员不下机”处理就永远不会有人落地，因此由
     * {@link HelicopterRappelManager#startAutonomousRappel(EntityHelicopter, boolean)} 临时置 true。</p>
     */
    private boolean rappelAllSeats;
    /**
     * 上一轮索降 / 索升是否被 {@link HelicopterRappelManager#forceAbort} <b>异常打断</b>，
     * 而不是正常走完。
     *
     * <p>{@link AirdropManager} 用它区分「索降完成 → 可以飞离」和「被打断 → 千万别把
     * 还挂在绳上的人当成已落地卸到地面」。每次 {@link #beginRappelHold()} 重新置 false。</p>
     */
    private boolean rappelAborted;

    // ===== AI 空投：归属 + 自动驾驶（仅服务端） =====
    /** 召唤这架直升机的玩家（AI 空投用；玩家手动驾驶的直升机保持 null）。 */
    @Nullable
    private UUID ownerUUID;
    /** 自动驾驶目标点；null = 不接管（玩家手动驾驶）。 */
    @Nullable
    private Vec3 autopilotTarget;
    private double autopilotSpeed = 0.45D;
    /** 自动驾驶固定朝向（null = 机头朝飞行方向；AI 空投固定 0° = 朝 +Z 飞）。 */
    @Nullable
    private Float autopilotYaw;
    /**
     * 无人驾驶索升：开门 100 tick 后进入 {@link #RAPPEL_ASCEND_ROPES} 而不是索降。
     * 由 {@link HelicopterRappelManager#startAutonomousAscend(EntityHelicopter)} 置位。
     */
    private boolean autoAscend;
    /** 无人驾驶索升时只接这些女仆（null = 不限，兼容玩家手动索升）。 */
    @Nullable
    private java.util.Set<UUID> ascendFilter;

    public EntityHelicopter(EntityType<?> type, Level level) {
        super(type, level);
    }

    // ===================== 交互 =====================
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        // 索降倒计时 / 抛绳阶段：禁止换座（把乘客误拽下绳），避免卡死
        if (this.isRappelling() && this.getRappelPhase() != RAPPEL_DESCENDING
                && player.getVehicle() == this) {
            return InteractionResult.SUCCESS;
        }
        InteractionResult result = super.interact(player, hand);
        if (!this.level().isClientSide && player.getVehicle() == this
                && this.getSeatIndex(player) != 0) {
            this.changeSeat(player, 0);
        }
        return result;
    }

    // ===================== GeckoLib =====================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 旋翼
        controllers.add(new AnimationController<>(this, "rotor", 0, state -> {
            boolean active = !this.onGround()
                    || this.getDeltaMovement().horizontalDistanceSqr() > 0.0025D
                    || this.getPower() > 0.01F;
            return active
                    ? state.setAndContinue(RawAnimation.begin().thenLoop("rotor_spin"))
                    : PlayState.STOP;
        }));
        // 舱门：完全由同步状态驱动（开->保持开；关->收起），无需服务端下发动画包
        controllers.add(new AnimationController<>(this, "door", 0, state -> {
            int phase = this.getRappelPhase();
            if (phase == RAPPEL_DOOR_OPENING || phase == RAPPEL_ROPES_OUT
                    || phase == RAPPEL_DESCENDING
                    || phase == RAPPEL_ASCEND_ROPES || phase == RAPPEL_ASCENDING) {
                return state.setAndContinue(DOOR_OPEN_ANIM);
            }
            if (phase == RAPPEL_CLOSING || phase == RAPPEL_ABORTING) {
                return state.setAndContinue(DOOR_CLOSE_ANIM);
            }
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ===================== 归属（AI 空投） =====================
    /** 记录召唤者；AI 空投用它判断“这架直升机归谁”。 */
    public void setOwner(@Nullable Player player) {
        this.ownerUUID = player == null ? null : player.getUUID();
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public boolean isOwnedBy(Player player) {
        return this.ownerUUID != null && this.ownerUUID.equals(player.getUUID());
    }

    @Nullable
    public Player getOwner() {
        return this.ownerUUID == null ? null : this.level().getPlayerByUUID(this.ownerUUID);
    }

    // ===================== 自动驾驶（AI 空投） =====================
    /**
     * 交给服务端自动驾驶：每 tick 朝目标点直线飞行 {@code blocksPerTick} 格。
     *
     * <p>卓越前线的直升机没有驾驶员时不会自己飞（物理只在有控制乘客时生效），
     * 所以这里像 {@link #applyRappelHold()} 一样直接接管位置与姿态：
     * 先屏蔽所有驾驶输入，再把实体位置朝目标推进，最后清零速度。
     * 这样既不需要真实驾驶员，也不会和卓越前线的悬停 / 盘旋逻辑打架。</p>
     *
     * @param target         目标点（世界坐标）
     * @param blocksPerTick  每 tick 位移格数
     */
    public void setAutopilotTarget(@Nullable Vec3 target, double blocksPerTick) {
        this.setAutopilotTarget(target, blocksPerTick, null);
    }

    /**
     * 自动驾驶（可锁定朝向）。
     *
     * @param fixedYaw 固定朝向（度）；null = 机头朝飞行方向。
     *                 AI 空投全程沿 +Z 轴飞，固定 0° 保证机身不会因为修正 x/y 而扭头。
     */
    public void setAutopilotTarget(@Nullable Vec3 target, double blocksPerTick,
                                   @Nullable Float fixedYaw) {
        this.autopilotTarget = target;
        this.autopilotSpeed = Math.max(0.05D, blocksPerTick);
        this.autopilotYaw = fixedYaw;
    }

    @Nullable
    public Vec3 getAutopilotTarget() {
        return this.autopilotTarget;
    }

    public void clearAutopilot() {
        this.autopilotTarget = null;
        this.autopilotYaw = null;
    }

    /** 无人驾驶索升模式（开门后走索升分支）。 */
    public boolean isAutoAscend() {
        return this.autoAscend;
    }

    public void setAutoAscend(boolean autoAscend) {
        this.autoAscend = autoAscend;
    }

    /** 索升白名单；null = 接附近的任何人（玩家手动索升）。 */
    @Nullable
    public java.util.Set<UUID> getAscendFilter() {
        return this.ascendFilter;
    }

    public void setAscendFilter(@Nullable java.util.Set<UUID> ascendFilter) {
        this.ascendFilter = ascendFilter;
    }

    /** 是否连驾驶位乘客一起索降（AI 空投单人时用）。 */
    public boolean isRappelAllSeats() {
        return this.rappelAllSeats;
    }

    public void setRappelAllSeats(boolean rappelAllSeats) {
        this.rappelAllSeats = rappelAllSeats;
    }

    /** 上一轮索降 / 索升是否被 forceAbort 异常打断（true = 没走完，不能当成“已落地”）。 */
    public boolean isRappelAborted() {
        return this.rappelAborted;
    }

    public void setRappelAborted(boolean rappelAborted) {
        this.rappelAborted = rappelAborted;
    }

    /** 仅服务端：由 {@link #tick()} 调用；索降期间不接管（悬停锁定优先级更高）。 */
    private void tickAutopilot() {
        Vec3 target = this.autopilotTarget;
        if (target == null || this.isRappelling()) {
            return;
        }
        // 有玩家坐进驾驶位时把控制权交还玩家（空投直升机也能被玩家接管）
        if (this.getControllingPassenger() instanceof Player) {
            return;
        }
        Vec3 pos = this.position();
        Vec3 delta = target.subtract(pos);
        double distance = delta.length();
        if (distance < 1.0E-3D) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 dir = delta.scale(1.0D / distance);
        double step = Math.min(this.autopilotSpeed, distance);
        Vec3 next = pos.add(dir.scale(step));

        // 1) 屏蔽卓越前线驾驶输入（无人驾驶时也不允许残留输入改变姿态）
        this.setForwardInputDown(false);
        this.setBackInputDown(false);
        this.setLeftInputDown(false);
        this.setRightInputDown(false);
        this.setUpInputDown(false);
        this.setDownInputDown(false);
        this.setFireInputDown(false);
        this.setSprintInputDown(false);
        this.setDecoyInputDown(false);

        // 2) 不要盘旋锁定：直线飞行由下面的坐标推进负责
        this.setLoiterActive(false);

        // 3) 机头朝向：AI 空投固定 0°（纯 +Z 飞行），否则朝飞行方向，机身保持水平
        Float fixedYaw = this.autopilotYaw;
        double horizontalSqr = dir.x * dir.x + dir.z * dir.z;
        if (fixedYaw != null) {
            this.setYRot(fixedYaw);
            this.yRotO = fixedYaw;
        } else if (horizontalSqr > 1.0E-6D) {
            float yaw = (float) (Mth.atan2(-dir.x, dir.z) * (180.0D / Math.PI));
            this.setYRot(yaw);
            this.yRotO = yaw;
        }
        this.setXRot(0.0F);
        this.xRotO = 0.0F;
        this.setRoll(0.0F);
        this.setPitchAngle(0.0F);
        this.setRollAngle(0.0F);
        this.setPitchVelocity(0.0F);
        this.setRollVelocity(0.0F);

        // 4) 直接推进位置并清零速度：不依赖卓越前线的飞行物理
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(next.x, next.y, next.z);
        // 5) 乘客（女仆）立刻对齐新座位，避免比机身慢一 tick 导致“坐姿漂在机舱外”
        if (this.isVehicle()) {
            for (Entity passenger : this.getPassengers()) {
                this.positionRider(passenger);
            }
        }
    }

    // ===================== tick =====================
    @Override
    public void tick() {
        // 座位/相机数据每 tick 对齐到模型骨骼（服务端与客户端都要，保证坐姿和第一人称视角一致）
        this.applyBoneSeats();
        super.tick();
        if (!this.level().isClientSide) {
            HelicopterRappelManager.tick(this);
            this.tickAutopilot();
        } else {
            this.applyClientRappelHold();
            // 客户端：维护旋翼/引擎循环声（AI 空投与玩家正常驾驶通用）
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.tlmformation.client.heli.HelicopterSoundManager.tick(this));
        }
    }

    /**
     * 客户端索降握手：把机身钉在服务端下发的锁点上。
     *
     * <p>服务端索降期间位置是恒定的（{@link #applyRappelHold()} 每 tick 拉回锁点），
     * 但卓越前线客户端会把位置包当作<b>插值目标</b>（{@code lerpTo} 固定 10 步），
     * 并且它的悬停 / 盘旋补偿仍在客户端跑 —— 两边叠加后机身会自己上下漂，
     * 视觉上就是“放下绳索时直升机从高到低、从低到高来回抽触”。
     * 客户端直接对齐锁点后，渲染位置与绳索锚点都不再抖动。</p>
     */
    private void applyClientRappelHold() {
        if (!this.clientHoldActive) {
            return;
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.clientHoldX, this.clientHoldY, this.clientHoldZ);
    }

    // ===================== 状态读写 =====================
    public int getRappelPhase() {
        return this.rappelPhase;
    }

    public void setRappelPhase(int phase) {
        if (this.rappelPhase == phase) {
            return;
        }
        int previous = this.rappelPhase;
        this.rappelPhase = phase;
        if (!this.level().isClientSide) {
            // 只在“阶段切换”时记一行。旧版每 tick 打印 PHASE-TRACE，20 条/秒把日志刷爆了。
            com.tlmformation.TLMFormationMod.LOGGER.info(
                    "[RAPPEL] phase {} -> {} pax={} remain={}",
                    previous, phase, this.getPassengers().size(), this.rappelRemain);
            this.syncRappelState();
        }
    }

    public int getRappelTimer() {
        return this.rappelTimer;
    }

    public void setRappelTimer(int timer) {
        this.rappelTimer = timer;
    }

    public int getRappelRemain() {
        return this.rappelRemain;
    }

    public void setRappelRemain(int remain) {
        if (this.rappelRemain == remain) {
            return;
        }
        this.rappelRemain = remain;
        if (!this.level().isClientSide) {
            this.syncRappelState();
        }
    }

    /** 客户端收到服务端状态包后写入（含索降期间的悬停锁点）。 */
    public void applySyncedRappelState(int phase, int remain,
                                       double lockX, double lockY, double lockZ) {
        this.rappelPhase = phase;
        this.rappelRemain = remain;
        if (!this.level().isClientSide) {
            return;
        }
        if (phase == RAPPEL_IDLE) {
            this.clientHoldActive = false;
            this.setNoGravity(false);
        } else {
            this.clientHoldActive = true;
            this.clientHoldX = lockX;
            this.clientHoldY = lockY;
            this.clientHoldZ = lockZ;
            this.setNoGravity(true);
        }
    }

    /** 序列化用的锁点：没有锁定时返回零点（phase=0 时客户端会忽略）。 */
    public Vec3 getSyncedLockPos() {
        return this.rappelLockPos != null ? this.rappelLockPos : Vec3.ZERO;
    }

    /** 是否处于任意索降阶段（含开门/抛绳/下滑/收尾）。 */
    public boolean isRappelling() {
        return this.rappelPhase != RAPPEL_IDLE;
    }

    private void syncRappelState() {
        Vec3 lock = this.getSyncedLockPos();
        NetworkHandler.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> this),
                new S2CRappelState(this.getId(), this.rappelPhase, this.rappelRemain,
                        lock.x, lock.y, lock.z));
    }

    // ===================== 骨骼 <- 座位 / 相机 =====================
    /**
     * 座位号 -> 骨骼名。
     *
     * <p>顺序 = 乘员优先级：主驾、副驾、两侧舱门炮手（朝外）、中排 3 人、后排 3 人。
     * 卓越前线按座位列表顺序分配空位，所以女仆会按这个顺序依次入座。</p>
     */
    public static final String[] SEAT_BONES = {
            "zuo1",   // 0 主驾
            "zuo7",   // 1 副驾
            "zuo9",   // 2 左侧舱门炮手（朝外）
            "zuo10",  // 3 右侧舱门炮手（朝外）
            "zuo2",   // 4 中排左
            "zuo3",   // 5 中排右
            "zuo6",   // 6 中排中
            "zuo4",   // 7 后排右
            "zuo5",   // 8 后排中
            "zuo8"    // 9 后排左
    };

    /** 座位号 -> 骨骼名；越界时回退到旧的 zuo{n+1} 命名。 */
    public static String seatBoneName(int seat) {
        if (seat >= 0 && seat < SEAT_BONES.length) {
            return SEAT_BONES[seat];
        }
        return "zuo" + (seat + 1);
    }

    /**
     * 骨骼 pivot（模型像素）-> 车辆局部坐标（格），并应用模型的 180° 全局朝向修正。
     * 骨骼数据每 tick 从 geo 资源读取（见 {@link HelicopterBoneData}），换模型自动跟随。
     */
    @Nullable
    public Vec3 boneVehiclePos(@Nullable String bone) {
        if (bone == null) {
            return null;
        }
        double[] px = HelicopterBoneData.pivot(bone);
        if (px == null) {
            return null;
        }
        Vector3d v = new Vector3d(px[0] * MODEL_PIXEL, px[1] * MODEL_PIXEL, px[2] * MODEL_PIXEL);
        Vec3 root = this.modelRootPivotLocal();
        v.sub(root.x, root.y, root.z)
                .rotateY((float) Math.toRadians(MODEL_YAW_OFFSET_DEG))
                .add(root.x, root.y, root.z);
        return new Vec3(v.x, v.y, v.z);
    }

    /** 全局修正的旋转中心：模型像素 -> 格（已乘渲染缩放）。 */
    public Vec3 modelRootPivotLocal() {
        return new Vec3(MODEL_ROOT_PIVOT[0] * MODEL_PIXEL,
                MODEL_ROOT_PIVOT[1] * MODEL_PIXEL,
                MODEL_ROOT_PIVOT[2] * MODEL_PIXEL);
    }

    /**
     * 把卓越前线原版座位数据改写为模型骨骼坐标（座位 0→zuo1 … 7→zuo8），
     * 并给有固定相机的座位（主驾/副驾）同步第一人称相机位置。
     *
     * <p>这样坐下、下机、救出、相机全部走卓前原有逻辑，只是坐标来源换成模型骨骼。</p>
     */
    private void applyBoneSeats() {
        List<SeatInfo> seats;
        try {
            seats = this.computed().seats();
        } catch (Throwable ignored) {
            return;   // 载具数据还没初始化好
        }
        if (seats == null || seats.isEmpty()) {
            return;
        }
        for (int seat = 0; seat < seats.size(); seat++) {
            SeatInfo info = seats.get(seat);
            if (info == null) {
                continue;
            }
            Vec3 bonePos = this.boneVehiclePos(seatBoneName(seat));
            if (bonePos == null) {
                continue;   // 骨骼缺失 -> 保持卓前原数据（兜底）
            }
            info.setPosition(bonePos);
            CameraPos camera = info.getCameraPos();
            if (camera != null) {
                camera.setPosition(bonePos.add(0.0D, CAMERA_EYE_HEIGHT, 0.0D));
                camera.setUseFixedCameraPos(true);
            }
        }
    }

    /**
     * 维度/客户端判断：第三人称时才使用越肩瞄准相机（1P 保持卓前原逻辑）。
     * 客户端专用类只在 CLIENT 分支被引用，服务端不会加载。
     */
    private static boolean thirdPersonClient() {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist
                != net.minecraftforge.api.distmarker.Dist.CLIENT) {
            return false;
        }
        try {
            return com.tlmformation.client.helicopter.HelicopterCameraSupport.isThirdPerson();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 第三人称右键瞄准（越肩）相机偏移：车辆局部坐标（侧向 +X、抬高 +Y、机尾方向 -Z）。 */
    public static final Vec3 THIRD_PERSON_ZOOM_OFFSET = new Vec3(2.2D, 3.9D, -6.2D);

    /**
     * 第三人称右键瞄准相机（只在 3P 生效）。
     *
     * <p>卓越前线的 {@code VehicleClientUtils.getCameraPosition} 在 zoomVehicle 时走
     * {@code getZoomPos}；默认回退到机舱座位相机，导致放大后几乎只看到机身尾部。
     * 这里只对本实体改成"越肩"偏移，不影响其它载具。</p>
     */
    @Override
    public Vec3 getZoomPos(Entity entity, float partialTick) {
        if (!thirdPersonClient()) {
            return super.getZoomPos(entity, partialTick);
        }
        try {
            Matrix4d transform = this.getTransformFromString("Vehicle", partialTick);
            if (transform != null) {
                Vector4d p = this.transformPosition(transform,
                        THIRD_PERSON_ZOOM_OFFSET.x,
                        THIRD_PERSON_ZOOM_OFFSET.y,
                        THIRD_PERSON_ZOOM_OFFSET.z);
                return new Vec3(p.x, p.y, p.z);
            }
        } catch (Throwable ignored) {
            // 数据未初始化时走卓前默认
        }
        return super.getZoomPos(entity, partialTick);
    }

    /**
     * 主驾第一人称相机：直接取 zuo1 骨骼位置 + 眼高，经 Vehicle 变换到世界。
     * 其它座位/其它情况回退到卓前原逻辑。
     */
    @Override
    public Vec3 getCameraPos(Entity entity, float partialTick) {
        if (this.getSeatIndex(entity) == 0) {
            Vec3 seat = this.boneVehiclePos("zuo1");
            if (seat != null) {
                // 座位骨骼 + 眼高 + 抬高补偿；横向按比例向舱中线收拢，向后偏移（机头为 +Z）
                Vec3 eye = new Vec3(
                        seat.x * (1.0D - DRIVER_CAMERA_CENTER),
                        seat.y + CAMERA_EYE_HEIGHT + DRIVER_CAMERA_UP,
                        seat.z - DRIVER_CAMERA_BACK);
                Vector4d p = this.transformPosition(
                        this.getTransformFromString("Vehicle", partialTick), eye.x, eye.y, eye.z);
                return new Vec3(p.x, p.y, p.z);
            }
        }
        return super.getCameraPos(entity, partialTick);
    }

    // ===================== 悬停锁定 =====================
    /** 进入索降：锁死当前位置 + 姿态，并记录到服务端字段。 */
    public void beginRappelHold() {
        // 新一轮索降 / 索升：清掉上一轮的“被打断”标记
        this.rappelAborted = false;
        // 悬停锁定期间位置完全由服务端接管，连重力都关掉：
        // 否则没人驾驶的直升机会每 tick 下沉一点点，把绳索锚点带偏，
        // 并在掉到锁点下方 0.5 格时触发 checkAbort 的“引擎丢失”误判。
        this.setNoGravity(true);
        this.rappelLockPos = this.position();
        this.rappelLockYaw = this.getYRot();
        this.rappelLockPitch = this.getXRot();
        this.applyRappelHold();
    }

    /** 每 tick 强制悬停：屏蔽驾驶员输入、锁 XYZ 与姿态。仅服务端调用。 */
    public void applyRappelHold() {
        if (this.rappelLockPos == null) {
            return;
        }
        // 1) 屏蔽卓越前线驾驶输入
        this.setForwardInputDown(false);
        this.setBackInputDown(false);
        this.setLeftInputDown(false);
        this.setRightInputDown(false);
        this.setUpInputDown(false);
        this.setDownInputDown(false);
        this.setFireInputDown(false);
        this.setSprintInputDown(false);
        this.setDecoyInputDown(false);

        // 2) 关掉卓越前线自带的悬停 / 盘旋自动控制。
        //    索降期间位置与姿态完全由本模组接管（下面第 4 步每 tick 拉回锁点），
        //    再让卓前的悬停控制同时工作就是两套控制系统抢高度，
        //    表现正是「放下绳索时直升机从高到低、从低到高来回抽触」。
        //    HOVER_MODE / LOITER 都是同步数据，客户端也会跟着关掉，不会自己漂。
        this.setHoverMode(false);
        this.setLoiterActive(false);
        this.setLoiterRadius(0.0D);

        // 3) 锁姿态
        this.setYRot(this.rappelLockYaw);
        this.yRotO = this.rappelLockYaw;
        this.setXRot(this.rappelLockPitch);
        this.xRotO = this.rappelLockPitch;
        this.setRoll(0.0F);
        this.setPitchAngle(0.0F);
        this.setRollAngle(0.0F);
        this.setPitchVelocity(0.0F);
        this.setRollVelocity(0.0F);

        // 4) 位置由服务端完全接管：关掉重力 + 任何超过 0.05 格的偏移立刻拉回。
        //    索降时绳索锚点必须绝对稳定，否则绳上的人会跟着机舱“抖动”。
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        if (this.position().distanceToSqr(this.rappelLockPos) > 0.0025D) {
            this.setPos(this.rappelLockPos.x, this.rappelLockPos.y, this.rappelLockPos.z);
        }
    }

    /** 收尾：退出悬停锁定，交还驾驶权。 */
    public void releaseRappelHold() {
        this.setHoverMode(false);
        this.setLoiterActive(false);
        this.setLoiterRadius(0.0D);
        this.setNoGravity(false);
        this.setDeltaMovement(Vec3.ZERO);
        this.rappelLockPos = null;
    }

    @Nullable
    public Vec3 getRappelLockPos() {
        return this.rappelLockPos;
    }

    // ===================== 绳索锚点 =====================
    /** 骨骼 117/118 的载具局部坐标（格）。 */
    public Vec3 rappelAnchorLocal(int index) {
        Vec3 bone = this.boneVehiclePos(index <= 0 ? "117" : "118");
        if (bone != null) {
            return bone;
        }
        return this.modelRootPivotLocal();
    }

    /**
     * 骨骼 117/118 的世界坐标。
     *
     * <p>用卓越前线自己的载具矩阵 {@code getTransformFromString("Vehicle")} 换算，
     * 与乘客座位、OBB 用的是同一套变换（包含绕抬高点的横滚/俯仰），
     * 所以绳索锚点、乘客和机身模型在翻滚时不会互相错位。</p>
     */
    public Vec3 rappelAnchorWorld(int index, float partialTick) {
        Vec3 local = this.rappelAnchorLocal(index);
        Vector3d v = new Vector3d(local.x, local.y, local.z);
        try {
            Matrix4d transform = this.getTransformFromString("Vehicle", partialTick);
            if (transform != null) {
                transform.transformPosition(v);
                return new Vec3(v.x, v.y, v.z);
            }
        } catch (Throwable ignored) {
            // 数据未初始化时走下面的兜底
        }
        v.rotate(this.modelToWorldQuat(partialTick));
        return new Vec3(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
    }

    /**
     * 索降绳锚点（带兜底），服务端放人 / 客户端画绳都用它，保证两边一致。
     *
     * <p>骨骼 117 / 118 的枢轴来自 geo 资源，数据缺失或版本不匹配时 {@link #rappelAnchorWorld}
     * 可能返回离谱的坐标（最坏情况：锚点落到地面以下）。那样“绳子一放就判定触地”，
     * 女仆会瞬间出现在地面 —— 实机 bug 的现象。这里做两层钳制：</p>
     * <ul>
     *   <li>水平方向：离机身超过 4 格拉回 4 格；</li>
     *   <li>竖直方向：钳在【机腹下方 2 格，机身中点上方 0.5 格】之间。</li>
     * </ul>
     */
    public Vec3 rappelAnchorWorldSafe(int index, float partialTick) {
        Vec3 anchor = this.rappelAnchorWorld(index, partialTick);
        double dx = anchor.x - this.getX();
        double dz = anchor.z - this.getZ();
        double horizSqr = dx * dx + dz * dz;
        if (horizSqr > 16.0D) {
            double scale = 4.0D / Math.sqrt(horizSqr);
            dx *= scale;
            dz *= scale;
        }
        double y = Math.max(this.getY() - 2.0D,
                Math.min(anchor.y, this.getY() + 0.5D));
        return new Vec3(this.getX() + dx, y, this.getZ() + dz);
    }

    /** 渲染插值后的实体位置（与卓越前线载具矩阵的平移一致）。 */
    public Vec3 lerpedRenderPos(float partialTick) {
        return new Vec3(Mth.lerp(partialTick, this.xo, this.getX()),
                Mth.lerp(partialTick, this.yo, this.getY()),
                Mth.lerp(partialTick, this.zo, this.getZ()));
    }

    /** 模型空间 -> 世界的载具四元数（朝向修正统一在骨骼坐标换算里做）。 */
    public Quaterniond modelToWorldQuat(float partialTick) {
        try {
            Quaterniond q = this.getRotationFromString("Vehicle", partialTick);
            if (q != null) {
                return q;
            }
        } catch (Throwable ignored) {
            // 未初始化时退化为单位四元数
        }
        return new Quaterniond();
    }

    /** 从锚点垂直向下射线找地面（含方块碰撞）；找不到返回地表高度。 */
    public Vec3 rappelGroundPoint(Vec3 anchor) {
        Vec3 from = new Vec3(anchor.x, this.getY() + 1.5D, anchor.z);
        Vec3 to = new Vec3(anchor.x, this.getY() - 192.0D, anchor.z);
        BlockHitResult hit = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            return hit.getLocation();
        }
        BlockPos pos = BlockPos.containing(anchor.x, this.getY(), anchor.z);
        int y = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                pos.getX(), pos.getZ());
        return new Vec3(anchor.x, y, anchor.z);
    }

    /** 当前悬停高度（离地格数）。 */
    public double altitudeToGround() {
        Vec3 anchor = this.rappelAnchorWorld(0, 1.0F);
        return this.getY() - this.rappelGroundPoint(anchor).y;
    }

    // ===================== 下滑乘客（服务端） =====================
    public List<HelicopterRappelManager.Descender> getRappelDescenders() {
        return this.rappelDescenders;
    }

    public List<HelicopterRappelManager.Ascender> getRappelAscenders() {
        return this.rappelAscenders;
    }

    public List<Entity> getRappelQueue() {
        return this.rappelQueue;
    }

    public int getRappelPipelineTick() {
        return this.rappelPipelineTick;
    }

    public void setRappelPipelineTick(int tick) {
        this.rappelPipelineTick = tick;
    }

    public int getRappelSpawned() {
        return this.rappelSpawned;
    }

    public void setRappelSpawned(int spawned) {
        this.rappelSpawned = spawned;
    }

    // ===================== 异常清理 =====================
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && this.isRappelling()) {
            // 区块卸载/切维度：只解冻乘客，不做传送（避免触发区块加载）
            boolean keepPassengersInPlace = reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                    || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                    || reason == Entity.RemovalReason.CHANGED_DIMENSION;
            HelicopterRappelManager.forceAbort(this, !keepPassengersInPlace);
        }
        super.remove(reason);
    }

    /**
     * 乘客（女仆/玩家）下机落点。
     *
     * <p>原版 {@code LivingEntity.stopRiding()} 会调用这个方法来决定落点，所以这里是
     * 唯一真正生效的下机位置入口。</p>
     *
     * <p>修复点：</p>
     * <ul>
     *   <li>用射线取真实可站立面（方块顶面/台阶/雪层/水面），不再只用 heightmap：</li>
     *   <li>heightmap 忽略树叶，且区块未加载时 {@code Level.getHeight} 会返回 getMinBuildHeight()（-64），
     *       直接把女仆送进地基/虚空；</li>
     *   <li>用原版 {@link DismountHelper#findSafeDismountLocation} 校验乘客碰撞箱放得下，
     *       放到方块里就抬一格重试，避免"下机遁地"。</li>
     * </ul>
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 base = super.getDismountLocationForPassenger(passenger);
        BlockPos basePos = BlockPos.containing(base);
        if (!this.level().hasChunkAt(basePos)) {
            return base;   // 区块没加载：别计算，交给原版处理
        }
        // 1) 垂直向下打射线，取真实可站立面
        Vec3 from = new Vec3(base.x, Math.max(base.y, this.getY()) + 2.0D, base.z);
        Vec3 to = new Vec3(base.x, this.level().getMinBuildHeight() - 1.0D, base.z);
        BlockHitResult hit = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        double groundY = hit.getType() == HitResult.Type.MISS
                ? this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        Mth.floor(base.x), Mth.floor(base.z)) + 0.1D
                : hit.getLocation().y + 0.05D;
        // 2) 碰撞箱校验：放不下就往上抬，最多试 3 格
        BlockPos start = BlockPos.containing(base.x, groundY, base.z);
        for (int dy = 0; dy <= 3; dy++) {
            Vec3 safe = DismountHelper.findSafeDismountLocation(
                    passenger.getType(), this.level(), start.above(dy), false);
            if (safe != null) {
                return safe;
            }
        }
        return new Vec3(base.x, groundY, base.z);
    }
}
