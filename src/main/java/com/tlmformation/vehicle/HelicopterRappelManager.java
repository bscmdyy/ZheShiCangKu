package com.tlmformation.vehicle;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.tactical.TacticalManager;
import com.tlmformation.tactical.TacticalState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector4d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * 战术索降 / 索升 服务端逻辑。
 *
 * <p>索降（rappel）：开门 5 秒 → 抛绳 → 非驾驶位乘客（女仆/玩家）沿绳下滑 → 落地 → 关门收尾。</p>
 * <p>索升（extraction）：再按一次小键盘 1 → 开门抛绳 → 附近「广域警戒」女仆寻路到绳底 →
 * 沿绳向上爬 → 自动坐进空座位 → 全部登机后关门收尾。</p>
 *
 * <p>女仆索降落地后会切换到「广域警戒」并把作息强制为全天（{@link MaidSchedule#ALL}）。</p>
 */
public final class HelicopterRappelManager {
    /** 开门悬停时长（ticks）。 */
    public static final int DOOR_OPEN_TICKS = 100;
    /** 抛绳后的缓冲（ticks），给客户端渲染留出绳子“甩出去”的时间。 */
    public static final int ROPE_DEPLOY_TICKS = 8;
    /** 关舱门收尾时长（ticks）。 */
    public static final int DOOR_CLOSE_TICKS = 30;
    /** 异常中断收绳时长（ticks）。 */
    public static final int ABORT_TICKS = 10;
    /** 每 tick 强制下降格数。 */
    public static final double DESCENT_PER_TICK = 0.2D;
    /** 每 tick 强制上升格数（索升）。 */
    public static final double CLIMB_PER_TICK = 0.2D;
    /** 索降最小离地高度（格）。 */
    public static final double MIN_ALTITUDE = 8.0D;
    /** 索升最小离地高度（格）：女仆只需爬 2~3 格，别卡太高。 */
    public static final double MIN_ALTITUDE_ASCEND = 1.5D;
    /** 索升搜索警戒女仆的半径（格）。 */
    public static final double ASCEND_RADIUS = 64.0D;
    /** 女仆距离绳底多近开始爬升（格）。 */
    public static final double ROPE_APPROACH_DISTANCE = 1.0D;
    /** 女仆距离座位骨骼多近可以登机（格）。 */
    public static final double BOARD_DISTANCE = 1.5D;
    /** 流水线间隔（ticks）：每 0.5 秒放行一个人。 */
    public static final int PIPELINE_INTERVAL = 10;
    /** 排队等待点离绳底的距离（格）。 */
    public static final double WAIT_POINT_OFFSET = 2.6D;
    /** 落地散开半径（格）。 */
    public static final double SCATTER_MIN = 7.0D;
    public static final double SCATTER_MAX = 14.0D;
    /** 悬停允许的最大水平漂移（格），超过判定为“飞走”。 */
    public static final double MAX_DRIFT = 4.0D;
    /** 悬停允许的最大下沉（格），超过判定为“下坠”。 */
    public static final double MAX_SINK = 2.5D;

    private HelicopterRappelManager() {
    }

    // ===================== 触发 / 中止 =====================
    /**
     * C2S 请求：小键盘 1。
     *
     * <p>机舱里有乘客 → 索降；只剩驾驶员、且机外有「广域警戒」女仆 → 索升。</p>
     */
    public static void requestStart(ServerPlayer player) {
        if (!(player.getVehicle() instanceof EntityHelicopter heli)) {
            return;
        }
        if (heli.getSeatIndex(player) != 0) {
            tell(player, "message.tlmformation.rappel.driver_only");
            return;
        }
        if (heli.getRappelPhase() != EntityHelicopter.RAPPEL_IDLE) {
            return;
        }

        boolean hasPassengers = false;
        for (Entity rider : heli.getPassengers()) {
            if (rider != player && heli.getSeatIndex(rider) > 0) {
                hasPassengers = true;
                break;
            }
        }
        List<EntityMaid> guards = findAscendCandidates(heli);

        // 索升：只剩驾驶员 + 机外有警戒女仆
        if (!hasPassengers && !guards.isEmpty()) {
            if (heli.onGround() || heli.altitudeToGround() < MIN_ALTITUDE_ASCEND) {
                tell(player, "message.tlmformation.rappel.too_low");
                return;
            }
            heli.beginRappelHold();
            heli.setRappelTimer(0);
            heli.setRappelRemain(0);
            heli.setAutoAscend(false);
            heli.setRappelPhase(EntityHelicopter.RAPPEL_ASCEND_ROPES);
            tell(player, "message.tlmformation.rappel.ascend_started");
            return;
        }

        // 索降
        if (hasPassengers) {
            if (heli.onGround() || heli.altitudeToGround() < MIN_ALTITUDE) {
                tell(player, "message.tlmformation.rappel.too_low");
                return;
            }
            heli.beginRappelHold();
            heli.setRappelTimer(0);
            heli.setRappelRemain(0);
            heli.setAutoAscend(false);
            heli.setRappelPhase(EntityHelicopter.RAPPEL_DOOR_OPENING);
            tell(player, "message.tlmformation.rappel.started");
            return;
        }

        tell(player, "message.tlmformation.rappel.no_maids");
    }

    /** C2S 请求：手动中止索降 / 索升。 */
    public static void requestAbort(ServerPlayer player) {
        if (!(player.getVehicle() instanceof EntityHelicopter heli)) {
            return;
        }
        if (heli.getRappelPhase() == EntityHelicopter.RAPPEL_IDLE) {
            return;
        }
        forceAbort(heli);
        tell(player, "message.tlmformation.rappel.aborted");
    }

    /**
     * 无人驾驶（AI 空投）索降入口：与 {@link #requestStart(ServerPlayer)} 的索降分支完全一致，
     * 只是不再要求“驾驶员必须是玩家”。
     *
     * @param includePilotSeat true = 连座位 0 的乘客一起放下（空投只有 1 名女仆时，她就是驾驶员，
     *                         否则永远没人能下机）
     * @return true = 已进入 DOOR_OPENING（舱门开启），false = 条件不满足（没有乘客 / 太矮 / 已在作业）
     */
    public static boolean startAutonomousRappel(EntityHelicopter heli, boolean includePilotSeat) {
        if (heli == null || heli.level().isClientSide) {
            return false;
        }
        if (heli.getRappelPhase() != EntityHelicopter.RAPPEL_IDLE) {
            return false;
        }
        boolean hasPassengers = false;
        for (Entity rider : heli.getPassengers()) {
            if (includePilotSeat ? heli.getSeatIndex(rider) >= 0 : heli.getSeatIndex(rider) > 0) {
                hasPassengers = true;
                break;
            }
        }
        if (!hasPassengers) {
            return false;
        }
        if (heli.onGround() || heli.altitudeToGround() < MIN_ALTITUDE) {
            return false;
        }
        heli.setRappelAllSeats(includePilotSeat);
        heli.setAutoAscend(false);
        heli.beginRappelHold();
        heli.setRappelTimer(0);
        heli.setRappelRemain(0);
        heli.setRappelPhase(EntityHelicopter.RAPPEL_DOOR_OPENING);
        return true;
    }

    /**
     * 无人驾驶（AI 空投接人）索升入口。
     *
     * <p>与玩家手动索升的区别只有两点：</p>
     * <ol>
     *   <li>没有驾驶员也能启动；</li>
     *   <li>仍从<b>开门 5 秒</b>开始（{@link EntityHelicopter#RAPPEL_DOOR_OPENING}），
     *       而不是直接跳到抛绳阶段 —— 保证和手动路径完全一样的动画节奏。</li>
     * </ol>
     *
     * <p>开门结束时由 {@link #tick(EntityHelicopter)} 根据
     * {@link EntityHelicopter#isAutoAscend()} 转入 {@link EntityHelicopter#RAPPEL_ASCEND_ROPES}。</p>
     *
     * @return true = 已进入 DOOR_OPENING
     */
    public static boolean startAutonomousAscend(EntityHelicopter heli) {
        if (heli == null || heli.level().isClientSide) {
            return false;
        }
        if (heli.getRappelPhase() != EntityHelicopter.RAPPEL_IDLE) {
            return false;
        }
        if (heli.onGround() || heli.altitudeToGround() < MIN_ALTITUDE_ASCEND) {
            return false;
        }
        heli.setRappelAllSeats(false);
        heli.setAutoAscend(true);
        heli.beginRappelHold();
        heli.setRappelTimer(0);
        heli.setRappelRemain(0);
        heli.setRappelPhase(EntityHelicopter.RAPPEL_DOOR_OPENING);
        return true;
    }

    // ===================== 主状态机 =====================
    /** 由 {@link EntityHelicopter#tick()} 每 tick 调用（仅服务端）。 */
    public static void tick(EntityHelicopter heli) {
        int phase = heli.getRappelPhase();
        if (phase == EntityHelicopter.RAPPEL_IDLE) {
            return;
        }
        // 先记录自然下落速度，再强制悬停（悬停会把速度清零，之后就无法判断“下坠”了）
        double verticalSpeed = heli.getDeltaMovement().y;
        // 任何索降/索升阶段都强制悬停锁定
        heli.applyRappelHold();

        // 临时诊断（每秒一行）：确认服务端悬停点是否真的稳定。
        // 若这里 dpos 一直是 0、但游戏里机身仍上下抽触，则问题一定在客户端渲染/插值。
        if (heli.tickCount % 20 == 0) {
            Vec3 lock = heli.getRappelLockPos();
            if (lock != null) {
                com.tlmformation.TLMFormationMod.LOGGER.info(
                        "[RAPPEL-DBG] phase={} pax={} y={} lockY={} dpos={}",
                        phase, heli.getPassengers().size(), heli.getY(), lock.y,
                        String.format(java.util.Locale.ROOT, "%.4f",
                                heli.position().distanceTo(lock)));
            }
        }

        switch (phase) {
            case EntityHelicopter.RAPPEL_DOOR_OPENING -> {
                heli.setRappelTimer(heli.getRappelTimer() + 1);
                if (heli.getRappelTimer() >= DOOR_OPEN_TICKS) {
                    heli.setRappelTimer(0);
                    // 无人驾驶接人：开门结束 -> 抛绳 -> 索升；其它（含玩家手动）-> 抛绳 -> 索降
                    heli.setRappelPhase(heli.isAutoAscend()
                            ? EntityHelicopter.RAPPEL_ASCEND_ROPES
                            : EntityHelicopter.RAPPEL_ROPES_OUT);
                }
            }
            case EntityHelicopter.RAPPEL_ROPES_OUT -> {
                heli.setRappelTimer(heli.getRappelTimer() + 1);
                if (heli.getRappelTimer() >= ROPE_DEPLOY_TICKS) {
                    heli.setRappelTimer(0);
                    beginDescending(heli);
                }
            }
            case EntityHelicopter.RAPPEL_DESCENDING -> tickDescenders(heli);
            case EntityHelicopter.RAPPEL_ASCEND_ROPES -> {
                heli.setRappelTimer(heli.getRappelTimer() + 1);
                if (heli.getRappelTimer() >= ROPE_DEPLOY_TICKS) {
                    heli.setRappelTimer(0);
                    beginAscending(heli);
                }
            }
            case EntityHelicopter.RAPPEL_ASCENDING -> tickAscenders(heli);
            case EntityHelicopter.RAPPEL_CLOSING -> {
                heli.setRappelTimer(heli.getRappelTimer() + 1);
                if (heli.getRappelTimer() >= DOOR_CLOSE_TICKS) {
                    finish(heli);
                }
            }
            case EntityHelicopter.RAPPEL_ABORTING -> {
                heli.setRappelTimer(heli.getRappelTimer() + 1);
                if (heli.getRappelTimer() >= ABORT_TICKS) {
                    finish(heli);
                }
            }
            default -> {
            }
        }
        checkAbort(heli, verticalSpeed);
    }

    // ===================== 索降：排队 + 乘客开始下滑 =====================
    /**
     * 把非驾驶位乘客按座位顺序排进队列（先不下机），
     * 由 {@link #tickDescenders} 每 {@link #PIPELINE_INTERVAL} tick 放行一个，左右绳交替。
     */
    private static void beginDescending(EntityHelicopter heli) {
        List<Entity> riders = new ArrayList<>(heli.getPassengers());
        riders.sort(Comparator.comparingInt(heli::getSeatIndex));
        heli.getRappelQueue().clear();
        heli.getRappelDescenders().clear();
        for (Entity rider : riders) {
            if (rider == null || rider.isRemoved() || !rider.isAlive()) {
                continue;
            }
            // 玩家手动索降：zuo1 驾驶员留机；AI 空投单人时（includePilotSeat）连驾驶员一起放
            if (!heli.isRappelAllSeats() && heli.getSeatIndex(rider) <= 0) {
                continue;
            }
            heli.getRappelQueue().add(rider);
        }
        heli.setRappelSpawned(0);
        heli.setRappelPipelineTick(heli.tickCount - PIPELINE_INTERVAL);   // 第一个立刻放行
        heli.setRappelRemain(heli.getRappelQueue().size());
        heli.setRappelTimer(0);
        heli.setRappelPhase(EntityHelicopter.RAPPEL_DESCENDING);
    }

    /** 流水线放行下一个乘客：解除坐骑 → 挂到 117/118 绳上。 */
    private static void spawnNextRider(EntityHelicopter heli) {
        List<Entity> queue = heli.getRappelQueue();
        while (!queue.isEmpty()) {
            Entity rider = queue.remove(0);
            if (rider == null || rider.isRemoved() || !rider.isAlive()) {
                continue;
            }
            int ropeIndex = heli.getRappelSpawned() % EntityHelicopter.ROPE_COUNT;
            heli.setRappelSpawned(heli.getRappelSpawned() + 1);
            rider.stopRiding();
            // 锚点带兜底：水平拉回机身 4 格内、竖直钳在【机腹下方 2 格，机身中点上方 0.5 格】，
            // 骨骼数据异常时也不会出现“绳子从地面放出去、一放就触地”。
            Vec3 anchor = heli.rappelAnchorWorldSafe(ropeIndex, 1.0F);
            double startY = anchor.y;
            // 落点在“放绳”时只算一次（旧版每 tick 重算，数据一抖就提前触地）。
            double groundY = heli.rappelGroundPoint(new Vec3(anchor.x, startY, anchor.z)).y;
            // 兜底 1：射线返回的地面几乎贴着机身 = 数据异常，改用悬停高度当绳长
            if (groundY > heli.getY() - 2.0D) {
                double altitude = Math.max(MIN_ALTITUDE, Math.min(heli.altitudeToGround(), 64.0D));
                groundY = heli.getY() - altitude;
            }
            // 兜底 2：落点必须比起点低至少 1 格，否则按 8 格绳长处理
            if (groundY > startY - 1.0D) {
                groundY = startY - Math.max(MIN_ALTITUDE, 8.0D);
            }
            // 临时诊断：起点 / 落点 / 绳长（定位后删）
            com.tlmformation.TLMFormationMod.LOGGER.info(
                    "[RAPPEL-DBG] spawn rope={} seat={} heliY={} anchorY={} startY={} groundY={} drop={}",
                    ropeIndex, heli.getSeatIndex(rider), heli.getY(), anchor.y, startY, groundY,
                    String.format(java.util.Locale.ROOT, "%.2f", startY - groundY));
            placeAt(rider, anchor.x, startY, anchor.z);
            rider.setDeltaMovement(Vec3.ZERO);
            rider.setNoGravity(true);
            rider.fallDistance = 0.0F;
            // 临时诊断：确认 setPos 真的生效（若这里就是 -60，说明放绳瞬间已被拉走）
            com.tlmformation.TLMFormationMod.LOGGER.info(
                    "[RAPPEL-DBG] spawn-placed riderY={} gy={} passenger={} vehicle={}",
                    String.format(java.util.Locale.ROOT, "%.3f", rider.getY()),
                    String.format(java.util.Locale.ROOT, "%.3f", rider.getEyeY()),
                    rider.isPassenger(), rider.getVehicle());
            if (rider instanceof Mob mob) {
                mob.setNoAi(true);
            }
            heli.getRappelDescenders().add(
                    new Descender(rider, ropeIndex, heli.getSeatIndex(rider),
                            startY, groundY, heli.tickCount));
            heli.setRappelPipelineTick(heli.tickCount);
            return;
        }
    }

    // ===================== 索降：下滑 tick =====================
    private static void tickDescenders(EntityHelicopter heli) {
        // 队列非空：每 0.5 秒放行一个（左右交替，保证绳上垂直间距）
        if (!heli.getRappelQueue().isEmpty()
                && heli.tickCount - heli.getRappelPipelineTick() >= PIPELINE_INTERVAL) {
            spawnNextRider(heli);
        }

        Iterator<Descender> it = heli.getRappelDescenders().iterator();
        while (it.hasNext()) {
            Descender d = it.next();
            Entity rider = d.entity();
            if (rider.isRemoved() || !rider.isAlive()) {
                // 临时诊断：如果女仆是“非触地”方式掉出索降列表的，这里会留下原因
                com.tlmformation.TLMFormationMod.LOGGER.info(
                        "[RAPPEL-DBG] drop rider removed={} alive={} y={}",
                        rider.isRemoved(), rider.isAlive(),
                        String.format(java.util.Locale.ROOT, "%.3f", rider.getY()));
                it.remove();
                if (rider instanceof Mob mob) {
                    mob.setNoAi(false);
                }
                continue;
            }
            Vec3 anchor = heli.rappelAnchorWorldSafe(d.rope(), 1.0F);
            // 落点用放绳时算好的值，滑降途中不再重算（避免中途数据抖动导致提前触地）
            double groundY = d.groundY();
            // 下滑高度由“已放绳 tick 数”推算，而不是取女仆当前 Y：
            // 这样任何外部逻辑（跟随/紧急传送/其它模组）把女仆挪走后，下一 tick 就会被拉回绳上，
            // 不会再出现“瞬移到地面后立刻判定触地”的连锁反应。
            int elapsed = heli.tickCount - d.startTick();
            double nextY = d.startY() - DESCENT_PER_TICK * elapsed;
            // 临时诊断：每 5 tick 打一次下滑轨迹，定位到底是谁在动女仆
            if (heli.tickCount % 5 == 0) {
                com.tlmformation.TLMFormationMod.LOGGER.info(
                        "[RAPPEL-DBG] tick elapsed={} riderY={} nextY={} groundY={} passenger={} noGravity={}",
                        elapsed,
                        String.format(java.util.Locale.ROOT, "%.3f", rider.getY()),
                        String.format(java.util.Locale.ROOT, "%.3f", nextY),
                        String.format(java.util.Locale.ROOT, "%.3f", groundY),
                        rider.isPassenger(), rider.isNoGravity());
            }
            if (nextY <= groundY + 0.05D) {
                // 触地：解除锁定，恢复 AI / 移动权（女仆同时进入广域警戒 + 全天作息 + 散开）
                land(rider, anchor.x, groundY, anchor.z);
                it.remove();
                continue;
            }
            // 强制锁在绳索正下方，每 tick 下降 0.2 格
            placeAt(rider, anchor.x, nextY, anchor.z);
            rider.setDeltaMovement(0.0D, -DESCENT_PER_TICK, 0.0D);
            rider.fallDistance = 0.0F;
            rider.setNoGravity(true);
            // 每 tick 重新冻结 AI：任何外部逻辑（紧急传送的 release、TLM 跟随等）
            // 一旦把 noAi 解掉，女仆就可能被瞬移到主人脚下。
            if (rider instanceof Mob mob) {
                mob.setNoAi(true);
            }
        }
        heli.setRappelRemain(heli.getRappelQueue().size() + heli.getRappelDescenders().size());
        if (heli.getRappelQueue().isEmpty() && heli.getRappelDescenders().isEmpty()) {
            // 全部落地 -> 关舱门
            heli.setRappelTimer(0);
            heli.setRappelPhase(EntityHelicopter.RAPPEL_CLOSING);
        }
    }

    // ===================== 索升：女仆寻路 + 爬升 =====================
    /**
     * 收集可索升的女仆：
     * 优先「广域警戒」，其次任何属于驾驶员的、没在骑乘的女仆 —— 只要驾驶员还坐在 zuo1，
     * 就不能因为状态短暂丢失而导致按键没反应。
     *
     * <p>无人驾驶（AI 空投接人）时没有控制乘客，改用
     * {@link EntityHelicopter#getOwnerUUID()} 作为归属判断，并且只接
     * {@link EntityHelicopter#getAscendFilter()} 白名单里的女仆，避免把路过别人家的女仆也带走。</p>
     */
    private static List<EntityMaid> findAscendCandidates(EntityHelicopter heli) {
        Entity pilot = heli.getControllingPassenger();
        java.util.UUID ownerId = pilot instanceof ServerPlayer player ? player.getUUID() : null;
        if (ownerId == null) {
            ownerId = heli.getOwnerUUID();   // 无人驾驶空投：用召唤者归属
        }
        final java.util.UUID ownerFilterId = ownerId;
        final java.util.Set<java.util.UUID> filter = heli.getAscendFilter();
        List<EntityMaid> maids = heli.level().getEntitiesOfClass(EntityMaid.class,
                heli.getBoundingBox().inflate(ASCEND_RADIUS),
                maid -> maid.isAlive()
                        && !maid.isRemoved()
                        && !maid.isPassenger()
                        && (ownerFilterId == null || ownerFilterId.equals(maid.getOwnerUUID()))
                        && (filter == null || filter.contains(maid.getUUID())));
        maids.sort(Comparator.comparingInt(maid ->
                TacticalManager.getState(maid) == TacticalState.GUARD ? 0 : 1));
        return maids;
    }

    private static void beginAscending(EntityHelicopter heli) {
        List<EntityMaid> maids = findAscendCandidates(heli);
        heli.getRappelAscenders().clear();
        int index = 0;
        for (EntityMaid maid : maids) {
            int rope = index % EntityHelicopter.ROPE_COUNT;
            Vec3 anchor = heli.rappelAnchorWorld(rope, 1.0F);
            Vec3 bottom = heli.rappelGroundPoint(anchor);
            // 等待点：绕绳底错开，避免所有人都挤在同一个点
            double angle = index * 1.1D;
            Vec3 wait = new Vec3(
                    bottom.x + Math.cos(angle) * WAIT_POINT_OFFSET,
                    bottom.y,
                    bottom.z + Math.sin(angle) * WAIT_POINT_OFFSET);
            heli.getRappelAscenders().add(new Ascender(maid, rope, wait));
            // 集结指令：切到 STATION（目标 = 等待点），停止巡逻，自动寻路
            if (maid.getOwner() instanceof ServerPlayer owner) {
                TacticalManager.forceStation(maid, owner, BlockPos.containing(wait));
            }
            index++;
        }
        heli.setRappelSpawned(0);
        heli.setRappelPipelineTick(heli.tickCount - PIPELINE_INTERVAL);   // 第一个立刻可以上绳
        heli.setRappelRemain(heli.getRappelAscenders().size());
        heli.setRappelTimer(0);
        if (heli.getRappelAscenders().isEmpty()) {
            heli.setRappelPhase(EntityHelicopter.RAPPEL_CLOSING);
            return;
        }
        heli.setRappelPhase(EntityHelicopter.RAPPEL_ASCENDING);
    }

    private static void tickAscenders(EntityHelicopter heli) {
        double seatTopY = seatTopY(heli);
        Iterator<Ascender> it = heli.getRappelAscenders().iterator();
        while (it.hasNext()) {
            Ascender ascender = it.next();
            EntityMaid maid = ascender.maid();
            if (maid.isRemoved() || !maid.isAlive() || maid.getVehicle() == heli) {
                if (maid.isRemoved() || !maid.isAlive()) {
                    restoreMaid(maid);
                }
                it.remove();
                continue;
            }
            Vec3 anchor = heli.rappelAnchorWorld(ascender.rope(), 1.0F);
            Vec3 bottom = heli.rappelGroundPoint(anchor);
            Vec3 wait = ascender.waitPoint();

            if (!ascender.climbing()) {
                // ① 集结：走到自己的等待点
                if (maid.position().distanceTo(wait) > ASCEND_RADIUS * 1.5D) {
                    restoreMaid(maid);
                    it.remove();
                    continue;
                }
                if (heli.tickCount % 20 == 0) {
                    maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                            new WalkTarget(BlockPos.containing(wait), 0.8F, 1));
                    maid.getNavigation().moveTo(wait.x, wait.y, wait.z, 0.8D);
                }
                boolean atRope = maid.position().distanceTo(
                        new Vec3(anchor.x, bottom.y, anchor.z)) <= ROPE_APPROACH_DISTANCE + 0.5D;
                boolean atWait = maid.position().distanceTo(wait) <= 1.2D;
                // ② 流水线：每 0.5 秒放行一个人
                if ((atRope || atWait)
                        && heli.tickCount - heli.getRappelPipelineTick() >= PIPELINE_INTERVAL) {
                    maid.getNavigation().stop();
                    maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    maid.setNoGravity(true);
                    maid.setNoAi(true);
                    placeAt(maid, anchor.x, bottom.y + 0.1D, anchor.z);
                    maid.setDeltaMovement(Vec3.ZERO);
                    maid.fallDistance = 0.0F;
                    ascender.setClimbing(true);
                    heli.setRappelPipelineTick(heli.tickCount);
                }
                continue;
            }

            // ③ 沿绳向上爬：最高只到机舱底板，不允许穿到机顶
            double nextY = Math.min(maid.getY() + CLIMB_PER_TICK, seatTopY + 0.35D);
            placeAt(maid, anchor.x, nextY, anchor.z);
            maid.setDeltaMovement(0.0D, CLIMB_PER_TICK, 0.0D);
            maid.fallDistance = 0.0F;
            maid.setNoGravity(true);
            // 爬升期间同样每 tick 冻结 AI，避免被跟随 / 紧急传送逻辑拽走
            maid.setNoAi(true);

            // ④ 到座位骨骼 1.5 格以内（或已到机舱底板）→ 解除锁定并登机
            if (nearestSeatDistanceSq(heli, maid.position()) <= BOARD_DISTANCE * BOARD_DISTANCE
                    || nextY >= seatTopY + 0.34D) {
                if (tryBoard(heli, maid)) {
                    it.remove();
                    continue;
                }
            }
        }
        heli.setRappelRemain(heli.getRappelAscenders().size());
        if (heli.getRappelAscenders().isEmpty()) {
            heli.setRappelTimer(0);
            heli.setRappelPhase(EntityHelicopter.RAPPEL_CLOSING);
        }
    }

    /** 所有可登机座位骨骼的世界坐标里最高的 Y（机舱底板高度）。 */
    private static double seatTopY(EntityHelicopter heli) {
        double top = heli.getY();
        try {
            Matrix4d transform = heli.getTransformFromString("Vehicle", 1.0F);
            if (transform == null) {
                return top;
            }
            for (int seat = 1; seat < EntityHelicopter.SEAT_BONES.length; seat++) {
                Vec3 local = heli.boneVehiclePos(EntityHelicopter.seatBoneName(seat));
                if (local == null) {
                    continue;
                }
                Vector4d world = heli.transformPosition(transform, local.x, local.y, local.z);
                top = Math.max(top, world.y);
            }
        } catch (Throwable ignored) {
            // 数据未初始化时退回实体高度
        }
        return top;
    }

    /** 到最近可登机座位骨骼的距离平方。 */
    private static double nearestSeatDistanceSq(EntityHelicopter heli, Vec3 pos) {
        double best = Double.MAX_VALUE;
        try {
            Matrix4d transform = heli.getTransformFromString("Vehicle", 1.0F);
            if (transform == null) {
                return best;
            }
            for (int seat = 1; seat < EntityHelicopter.SEAT_BONES.length; seat++) {
                Vec3 local = heli.boneVehiclePos(EntityHelicopter.seatBoneName(seat));
                if (local == null) {
                    continue;
                }
                Vector4d world = heli.transformPosition(transform, local.x, local.y, local.z);
                best = Math.min(best, pos.distanceToSqr(world.x, world.y, world.z));
            }
        } catch (Throwable ignored) {
            // 数据未初始化
        }
        return best;
    }

    /** 到机舱高度：自动坐进最近的空座位。 */
    private static boolean tryBoard(EntityHelicopter heli, EntityMaid maid) {
        if (heli.getPassengers().size() >= heli.getMaxPassengers()) {
            return false;
        }
        maid.setNoAi(false);
        maid.setNoGravity(false);
        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0.0F;
        boolean riding = maid.startRiding(heli, true);
        // 卓前的 addPassenger 在座位表未就绪 / 座位号越界时会静默返回，
        // 结果就是“骑着但没有座位”的幽灵乘客：必须核对座位号才认定登机成功。
        if (riding && heli.getSeatIndex(maid) >= 0) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            return true;
        }
        if (maid.isPassenger()) {
            maid.stopRiding();
        }
        com.tlmformation.TLMFormationMod.LOGGER.warn(
                "[RAPPEL] board failed maid={} riding={} seat={} pax={}",
                maid.getUUID(), riding, heli.getSeatIndex(maid), heli.getPassengers().size());
        // 没坐上（座位表未登记 / 座位冲突）：恢复挂绳状态，下 tick 继续试
        maid.setNoGravity(true);
        maid.setNoAi(true);
        return false;
    }

    // ===================== 异常中断 =====================
    /** 立即打断索降/索升：所有人回到地面并恢复 AI，绳索收回。 */
    public static void forceAbort(EntityHelicopter heli) {
        forceAbort(heli, true);
    }

    /**
     * @param teleportToGround true = 把乘客/女仆放回地面（被毁/飞走时防卡死）；
     *                         false = 只解冻（区块卸载/切维度时不要触发区块加载）
     */
    public static void forceAbort(EntityHelicopter heli, boolean teleportToGround) {
        forceAbort(heli, teleportToGround, "external");
    }

    /**
     * @param reason 触发原因（destroyed / drifted / sinking / falling / manual / unload），只用于日志
     */
    public static void forceAbort(EntityHelicopter heli, boolean teleportToGround, String reason) {
        if (heli.getRappelPhase() == EntityHelicopter.RAPPEL_IDLE
                && heli.getRappelDescenders().isEmpty()
                && heli.getRappelAscenders().isEmpty()) {
            return;   // 本来就没在作业，什么都不用做
        }
        com.tlmformation.TLMFormationMod.LOGGER.info(
                "[RAPPEL] forceAbort reason={} phase={} pax={} pos={} lock={}",
                reason, heli.getRappelPhase(), heli.getPassengers().size(),
                heli.position(), heli.getRappelLockPos());
        // 标记“这一轮没走完”：AirdropManager 由此判断不能把乘客当成已落地卸下去
        heli.setRappelAborted(true);
        for (Descender d : heli.getRappelDescenders()) {
            rescue(d.entity(), teleportToGround);
        }
        heli.getRappelDescenders().clear();
        heli.getRappelQueue().clear();
        for (Ascender a : heli.getRappelAscenders()) {
            rescue(a.maid(), teleportToGround);
        }
        heli.getRappelAscenders().clear();
        heli.setRappelRemain(0);
        heli.setAutoAscend(false);
        heli.setAscendFilter(null);
        if (heli.isRemoved()) {
            // 实体已经被卸载/摧毁：直接复位，避免残留状态
            heli.setRappelPhase(EntityHelicopter.RAPPEL_IDLE);
            heli.setRappelTimer(0);
            heli.releaseRappelHold();
        } else {
            heli.setRappelTimer(0);
            heli.setRappelPhase(EntityHelicopter.RAPPEL_ABORTING);
        }
    }

    private static void checkAbort(EntityHelicopter heli, double verticalSpeed) {
        int phase = heli.getRappelPhase();
        if (phase == EntityHelicopter.RAPPEL_IDLE || phase == EntityHelicopter.RAPPEL_ABORTING) {
            return;
        }
        boolean destroyed = heli.isRemoved() || heli.getHealth() <= 0.0F;
        Vec3 lock = heli.getRappelLockPos();
        boolean drifted = lock != null && heli.position().distanceTo(lock) > MAX_DRIFT;
        boolean sinking = lock != null && heli.getY() < lock.y - MAX_SINK;
        boolean falling = verticalSpeed < -0.45D;
        // 注意：这里绝对不能用 engineRunning() 判断“引擎熄火”。
        // 卓越前线对 HELICOPTER 的实现是 abs(power) > 0，而悬停锁定会把 power 压到 0，
        // 所以索降期间它恒为 false —— 旧版的 engineLost 条件因此 100% 误判：
        // 直升机在悬停点上每 tick 下沉一点点，掉到锁点下方 0.5 格就立刻中止索降
        // （实机日志里表现为“开门 8 tick 后 forceAbort”，人直接被卸到地面）。
        // 现在悬停锁定会关重力并每 tick 把位置拉回锁点，真正的坠机 / 被击毁由
        // destroyed、falling、sinking 兜底。
        if (destroyed || drifted || sinking || falling) {
            forceAbort(heli, true, "destroyed=" + destroyed + " drifted=" + drifted
                    + " sinking=" + sinking + " falling=" + falling
                    + " verticalSpeed=" + String.format(java.util.Locale.ROOT, "%.3f", verticalSpeed));
        }
    }

    // ===================== 收尾 =====================
    private static void finish(EntityHelicopter heli) {
        for (Descender d : heli.getRappelDescenders()) {
            rescue(d.entity(), true);
        }
        heli.getRappelDescenders().clear();
        heli.getRappelQueue().clear();
        for (Ascender a : heli.getRappelAscenders()) {
            rescue(a.maid(), true);
        }
        heli.getRappelAscenders().clear();
        heli.setRappelRemain(0);
        heli.setRappelTimer(0);
        heli.setRappelAllSeats(false);
        heli.setAutoAscend(false);
        heli.setAscendFilter(null);
        heli.setRappelPhase(EntityHelicopter.RAPPEL_IDLE);
        heli.releaseRappelHold();
    }

    // ===================== 工具 =====================
    private static void placeAt(Entity entity, double x, double y, double z) {
        if (entity instanceof ServerPlayer sp) {
            // 玩家的位置必须服务端 teleport，客户端才能平滑跟随
            sp.teleportTo(x, y, z);
        } else {
            entity.setPos(x, y, z);
        }
    }

    /** 索降触地：恢复自由移动，并让女仆进入「广域警戒 + 全天作息」，同时向随机点散开。 */
    private static void land(Entity rider, double x, double y, double z) {
        // 临时诊断：确认触地高度是否符合预期（起点 ~机腹，终点 ~地表）
        com.tlmformation.TLMFormationMod.LOGGER.info(
                "[RAPPEL-DBG] land riderY={} groundY={} x={} z={}",
                String.format(java.util.Locale.ROOT, "%.3f", rider.getY()),
                String.format(java.util.Locale.ROOT, "%.3f", y),
                String.format(java.util.Locale.ROOT, "%.2f", x),
                String.format(java.util.Locale.ROOT, "%.2f", z));
        placeAt(rider, x, y + 0.05D, z);
        rider.setNoGravity(false);
        rider.setDeltaMovement(Vec3.ZERO);
        rider.fallDistance = 0.0F;
        if (rider instanceof Mob mob) {
            mob.setNoAi(false);
        }
        if (rider instanceof EntityMaid maid) {
            applyGuardDuty(maid, scatterPoint(maid, x, z));
        }
    }

    /** 落地散开点：以绳底为圆心，5~10 格随机方位，取该处地面高度。 */
    private static Vec3 scatterPoint(Entity entity, double x, double z) {
        var random = entity.level().getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = SCATTER_MIN + random.nextDouble() * (SCATTER_MAX - SCATTER_MIN);
        double targetX = x + Math.cos(angle) * radius;
        double targetZ = z + Math.sin(angle) * radius;
        return new Vec3(targetX, groundHeightAt(entity, targetX, targetZ), targetZ);
    }

    /**
     * 索降落地后的自动警戒：
     * <ol>
     *   <li>切到「广域警戒」（TacticalState.GUARD）；</li>
     *   <li>作息强制改为「全天」（MaidSchedule.ALL），不会到点睡觉/罢工；</li>
     *   <li>给每个人一个不同的随机散开点，落地立刻四散，不挤成一坨。</li>
     * </ol>
     */
    public static void applyGuardDuty(EntityMaid maid) {
        applyGuardDuty(maid, null);
    }

    public static void applyGuardDuty(EntityMaid maid, Vec3 scatterTarget) {
        try {
            maid.setSchedule(MaidSchedule.ALL);
        } catch (Throwable ignored) {
            // TLM 版本差异时不影响其它逻辑
        }
        try {
            if (maid.getOwner() instanceof ServerPlayer owner) {
                TacticalManager.forceGuard(maid, owner);
            }
        } catch (Throwable ignored) {
            // 没有在线主人时保持原状态
        }
        if (scatterTarget != null) {
            try {
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(BlockPos.containing(scatterTarget), 0.9F, 1));
                maid.getNavigation().moveTo(
                        scatterTarget.x, scatterTarget.y, scatterTarget.z, 0.9D);
            } catch (Throwable ignored) {
                // 导航不可用时保持原地警戒
            }
        }
    }

    private static void restoreMaid(EntityMaid maid) {
        if (maid == null) {
            return;
        }
        maid.setNoGravity(false);
        maid.setNoAi(false);
        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0.0F;
    }

    /** 异常中断时把乘客/女仆放回地面（防卡死）。 */
    private static void rescue(Entity rider, boolean teleportToGround) {
        if (rider.isRemoved() || !rider.isAlive()) {
            return;
        }
        if (teleportToGround) {
            Vec3 pos = rider.position();
            double groundY = groundHeightAt(rider, pos.x, pos.z);
            placeAt(rider, pos.x, groundY + 0.1D, pos.z);
        }
        rider.setNoGravity(false);
        rider.setDeltaMovement(Vec3.ZERO);
        rider.fallDistance = 0.0F;
        if (rider instanceof Mob mob) {
            mob.setNoAi(false);
        }
    }

    private static double groundHeightAt(Entity entity, double x, double z) {
        Vec3 from = new Vec3(x, entity.getY() + 2.0D, z);
        Vec3 to = new Vec3(x, entity.level().getMinBuildHeight(), z);
        BlockHitResult hit = entity.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (hit.getType() != HitResult.Type.MISS) {
            return hit.getLocation().y;
        }
        BlockPos pos = BlockPos.containing(x, entity.getY(), z);
        return entity.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                pos.getX(), pos.getZ());
    }

    private static void tell(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    /** 一名正在沿绳索下滑的乘客（startY / groundY / startTick 在放绳时记下，滑降途中不再重算）。 */
    public record Descender(Entity entity, int rope, int seat,
                            double startY, double groundY, int startTick) {
    }

    /** 一名正在执行索升的女仆。 */
    public static final class Ascender {
        private final EntityMaid maid;
        private final int rope;
        private final Vec3 waitPoint;
        private boolean climbing;

        public Ascender(EntityMaid maid, int rope, Vec3 waitPoint) {
            this.maid = maid;
            this.rope = rope;
            this.waitPoint = waitPoint;
        }

        public EntityMaid maid() {
            return this.maid;
        }

        public int rope() {
            return this.rope;
        }

        public Vec3 waitPoint() {
            return this.waitPoint;
        }

        public boolean climbing() {
            return this.climbing;
        }

        public void setClimbing(boolean climbing) {
            this.climbing = climbing;
        }
    }
}
