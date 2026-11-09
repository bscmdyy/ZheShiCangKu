package com.tlmformation.vehicle;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.formation.FormationManager;
import com.tlmformation.formation.TargetSafety;
import com.tlmformation.team.MaidDownedManager;
import com.tlmformation.team.MaidReviveManager;
import com.tlmformation.team.MaidTeamManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 全自动 AI 直升机小队空投 / 接走（服务端）。
 *
 * <p>两种作业模式共用一台状态机：</p>
 * <ul>
 *   <li>{@link Mode#DEPLOY}：F9「部署」—— 直升机从玩家 <b>-Z</b> 方向 90 格处进场，
 *       沿 <b>+Z</b> 飞越玩家上空，悬停索降，然后沿 <b>+Z</b> 飞离；</li>
 *   <li>{@link Mode#PICKUP}：F9「小队撤离」/ 呼救器回收 —— 同一航线进场，悬停抛绳，
 *       女仆沿绳爬升登机，然后沿 <b>+Z</b> 飞离，飞离 120 格后整队送回据点。</li>
 * </ul>
 *
 * <p>状态机（每名玩家一份，由服务端 tick 驱动）：</p>
 * <pre>
 * CALL      找女仆（最多 6 名：未骑乘 / 未倒地 / 属于该玩家）
 *   │
 * SPAWN     在玩家 z-90、同 x、玩家头顶 +40 格（并抬到地表 +25 之上）生成 EntityHelicopter
 *   │
 * FLYIN     固定 yaw 0°，只沿 +Z 推进；heli.z &gt;= player.z - 8 进入悬停
 *   │
 * HOVER     DEPLOY：startAutonomousRappel（开门 100 → 抛绳 8 → 逐个下滑 0.2/tick，间隔 10）
 *   │       PICKUP：startAutonomousAscend（开门 100 → 抛绳 8 → 女仆归队沿绳爬升登机）
 *   │  索降状态机回到 IDLE
 * EXTRACT   沿 +Z 飞离（不落地、不转向）；heli.z - player.z &gt; 120 判定完成
 *   │
 * DONE      丢弃直升机、清理状态、同步 F9 队伍面板（PICKUP 还会把整队送回据点）
 * </pre>
 *
 * <p>任何“落地前”阶段超过 {@link #PHASE_TIMEOUT} tick 都会中止：直升机与乘客都被安全回收。
 * 头部上方 {@link SkyVisibility#CHECK_HEIGHT} 格内有实心天花板（洞穴 / 室内）时，
 * 部署与接走都会被拒绝，并提示 {@code message.tlmformation.airdrop.signal_poor}。</p>
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AirdropManager {
    /** 一次空投最多投放的女仆数量。 */
    public static final int MAX_SQUAD = 6;
    /** 单名女仆登机的最多尝试次数（卓越前线座位表刚生成时可能还没就绪）。 */
    public static final int MOUNT_ATTEMPTS = 3;
    /** 生成点：玩家 -Z 方向的水平距离（格）。 */
    public static final double SPAWN_DISTANCE = 90.0D;
    /** 生成点：玩家头顶 +40 格。 */
    public static final double SPAWN_HEIGHT = 40.0D;
    /** 进场速度（格 / tick）：原 0.9 减半。 */
    public static final double FLYIN_SPEED = 0.45D;
    /** 撤离速度（格 / tick）：原 1.2 减半。 */
    public static final double EXTRACT_SPEED = 0.6D;
    /** 进场到达判定：heli.z &gt;= player.z - 该值。 */
    public static final double ARRIVE_DISTANCE = 8.0D;
    /** 直升机相对玩家沿 +Z 飞出该距离后判定撤离完成（格）。 */
    public static final double EXTRACT_DISTANCE = 120.0D;
    /** 搜索可空投女仆的半径（格）。 */
    public static final double SEARCH_RADIUS = 192.0D;
    /** 单个阶段的超时（tick）。索降执行期间不计时（见 {@link #tick}）。 */
    public static final int PHASE_TIMEOUT = 400;
    /** 整场空投的绝对上限（tick），防止任何分支卡死。 */
    public static final int TOTAL_TIMEOUT = 2400;
    /** AI 空投固定朝向（0° = 机头朝 +Z）。 */
    public static final float FLIGHT_YAW = 0.0F;

    private static final Map<UUID, Airdrop> ACTIVE = new HashMap<>();

    private AirdropManager() {
    }

    // ===================== 入口 =====================
    /**
     * 尝试为玩家启动一次空投（F9「部署」）。
     *
     * @param team F9 里点「部署」的那支队伍（越界时退回当前激活队伍）
     * @return true = 已接管本次「部署」请求（成功、已被拦截或已在空投中），调用方不要再走传送召唤；
     *         false = 没有可空投的女仆（或没装卓越前线），调用方回退到旧逻辑
     */
    public static boolean tryStart(ServerPlayer player, int team) {
        if (player == null || player.isSpectator()) {
            return false;
        }
        // 卓越前线（SBW）是强前置；没有直升机实体时仍交给旧的传送召唤兜底
        if (HavocEntities.HELICOPTER == null) {
            return false;
        }
        int teamIndex = normalizeTeam(player, team);

        // 0) 地下 / 洞穴 / 室内：头顶没有开阔天空，信号不通，直接中止（先判断，避免误发 swap 提示）
        if (!SkyVisibility.hasOpenSky(player)) {
            chatMessage(player, "message.tlmformation.airdrop.signal_poor");
            return true;
        }

        // 1) 交换部署（swap）：旧小队还在场时不再拦截。
        //    规则：新队伍照常投下去；落地后同一架直升机再把旧小队接走（旧队登机飞离）。
        List<UUID> previousSquad = new ArrayList<>();
        int previousTeam = -1;
        Airdrop existing = ACTIVE.get(player.getUUID());
        if (existing != null && existing.phase != Phase.DONE) {
            if (existing.phase == Phase.EXTRACT) {
                // 上一架已经在飞离途中（机上通常只剩驾驶员或已空）：直接收尾，腾出状态
                existing.phase = Phase.DONE;
                discardWithRiders(existing);
                ACTIVE.remove(player.getUUID());
                existing = null;
            } else {
                // 还在进场 / 悬停 / 索降：绝不同时生成第二架
                message(player, "message.tlmformation.airdrop.in_progress");
                return true;
            }
        }
        int currentActive = MaidTeamManager.activeTeam(player);
        if (currentActive >= 0 && MaidTeamManager.isAirdropDeployed(player)) {
            if (currentActive == teamIndex) {
                // 同一支队伍重复部署：让它先撤离再部署，避免“自己接自己”
                notifySquadDeployed(player);
                return true;
            }
            // 另一支队伍还部署在场上：记为“待接走的旧小队”，本次空投结束后由同一架直升机接走
            previousTeam = currentActive;
            previousSquad.addAll(MaidTeamManager.activeMembers(player));
            if (!previousSquad.isEmpty()) {
                chatMessage(player, "message.tlmformation.airdrop.swap_planned");
            }
        }

        Set<UUID> excluded = new HashSet<>(previousSquad);
        List<EntityMaid> squad = findSquad(player, teamIndex, excluded);
        if (squad.isEmpty()) {
            // 交给 MaidTeamManager.summon 的旧路径（可能从备份里重建女仆）
            return false;
        }
        ServerLevel level = player.serverLevel();
        EntityHelicopter heli = HavocEntities.HELICOPTER.get().create(level);
        if (heli == null) {
            return false;
        }
        // 生成点：玩家正 -Z 方向 90 格、同 x、头顶 +40（地表 +25 之上）
        Vec3 spawn = spawnPoint(player, level);
        heli.moveTo(spawn.x, spawn.y, spawn.z, FLIGHT_YAW, 0.0F);
        heli.setOwner(player);
        heli.setEngineStart(true);
        // 只沿 +Z 飞：x / y 锁死，只有 z 跟着玩家走
        heli.setAutopilotTarget(new Vec3(spawn.x, spawn.y, player.getZ()), FLYIN_SPEED, FLIGHT_YAW);
        if (!level.addFreshEntity(heli)) {
            heli.discard();
            return false;
        }
        // 召唤成功：旋翼/引擎声由客户端 HelicopterSoundManager 按实体状态自动播放
        message(player, "message.tlmformation.airdrop.called");

        List<UUID> mounted = new ArrayList<>();
        for (EntityMaid maid : squad) {
            if (mounted.size() >= MAX_SQUAD) {
                break;
            }
            if (mount(heli, maid)) {
                mounted.add(maid.getUUID());
            }
        }
        if (mounted.isEmpty()) {
            // 一个人都登不上机：不要留一架空直升机在天上，交回旧的传送召唤
            TLMFormationMod.LOGGER.warn("[MOUNT-DEBUG] no maid boarded, fall back to summon. squad={}", squad.size());
            heli.discard();
            return false;
        }

        Airdrop airdrop = new Airdrop(heli, Mode.DEPLOY, teamIndex, spawn.x, spawn.y);
        airdrop.squad.addAll(mounted);
        airdrop.previousSquad.addAll(previousSquad);
        airdrop.previousTeam = previousTeam;
        ACTIVE.put(player.getUUID(), airdrop);

        try {
            // 登记队伍 + 撤回旧队伍 + 同步 F9 面板（不出网新包，复用现有同步）。
            // swap 时不能把旧小队“撤回据点”——她们要登上这架直升机，由 keepOldSquad=true 跳过旧队撤退。
            MaidTeamManager.registerAirdropSquad(player, teamIndex, mounted, !previousSquad.isEmpty());
        } catch (Throwable throwable) {
            TLMFormationMod.LOGGER.error("[Airdrop] register squad failed", throwable);
            abort(player, airdrop, "message.tlmformation.airdrop.aborted");
            return true;
        }

        message(player, "message.tlmformation.airdrop.inbound", mounted.size());
        return true;
    }

    /**
     * 尝试启动一次「直升机接走」作业（F9「小队撤离」/ 呼救器回收）。
     *
     * <p>与 {@link #tryStart} 相同的进场航线，但悬停后走索升（女仆爬绳登机），
     * 飞离 120 格后再由 {@link MaidTeamManager#finishAirdropPickup} 把整队送回据点。</p>
     *
     * @param team 要接走的队伍下标（越界时退回当前激活队伍）
     * @return true = 直升机已经在路上 / 已在作业；false = 无法起飞（地下、没有在线的女仆、没装卓前），
     *         调用方需要回退到旧的瞬间传送撤离
     */
    public static boolean tryStartExtraction(ServerPlayer player, int team) {
        if (player == null || player.isSpectator()) {
            return false;
        }
        if (HavocEntities.HELICOPTER == null) {
            return false;
        }
        if (ACTIVE.containsKey(player.getUUID())) {
            message(player, "message.tlmformation.airdrop.in_progress");
            return true;
        }
        // 地下 / 洞穴：信号不通，直升机来不了，交给调用方用旧的瞬间撤离兜底
        if (!SkyVisibility.hasOpenSky(player)) {
            chatMessage(player, "message.tlmformation.airdrop.signal_poor");
            return false;
        }

        int teamIndex = normalizeTeam(player, team);
        List<EntityMaid> squad = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (EntityMaid maid : MaidTeamManager.loadedTeamMaids(player, teamIndex)) {
            // 女仆同样受“头顶能看见天”约束：玩家带进洞穴的女仆不能被机降接走，
            // 她们会由 finishAirdropPickup / 旧的瞬间撤离送回家。
            if (!SkyVisibility.hasOpenSky(player.serverLevel(), maid.blockPosition())) {
                continue;
            }
            squad.add(maid);
            ids.add(maid.getUUID());
        }
        if (squad.isEmpty()) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        EntityHelicopter heli = HavocEntities.HELICOPTER.get().create(level);
        if (heli == null) {
            return false;
        }
        Vec3 spawn = spawnPoint(player, level);
        heli.moveTo(spawn.x, spawn.y, spawn.z, FLIGHT_YAW, 0.0F);
        heli.setOwner(player);
        heli.setEngineStart(true);
        // 只接这支队伍的女仆，避免把路过的其它女仆也带走
        heli.setAscendFilter(new HashSet<>(ids));
        heli.setAutopilotTarget(new Vec3(spawn.x, spawn.y, player.getZ()), FLYIN_SPEED, FLIGHT_YAW);
        if (!level.addFreshEntity(heli)) {
            heli.discard();
            return false;
        }
        // 回收直升机进场：旋翼/引擎声由客户端 HelicopterSoundManager 自动播放
        Airdrop airdrop = new Airdrop(heli, Mode.PICKUP, teamIndex, spawn.x, spawn.y);
        airdrop.squad.addAll(ids);
        ACTIVE.put(player.getUUID(), airdrop);
        message(player, "message.tlmformation.airdrop.pickup_inbound");
        return true;
    }

    /** 该玩家是否正在空投作业（给 UI / 调试用）。 */
    public static boolean isActive(ServerPlayer player) {
        return player != null && ACTIVE.containsKey(player.getUUID());
    }

    /**
     * 交换部署期间“正在等登机”的旧小队成员（供区块加载 / 调试用）；没有则返回空列表。
     */
    public static List<UUID> swapWaitingMembers(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        Airdrop airdrop = ACTIVE.get(player.getUUID());
        if (airdrop == null || airdrop.phase == Phase.DONE
                || airdrop.mode != Mode.DEPLOY) {
            return List.of();
        }
        return airdrop.previousSquad;
    }

    /**
     * 硬救援前调用：立刻中止该玩家正在进行的空投作业。
     *
     * <p>必须先停动画：否则救援把女仆传回玩家身边后，空投状态机还会按座位/绳索继续摆弄她，
     * 下一 tick 又会被拽回直升机。返回 true 表示确实中止了一场作业。</p>
     */
    public static boolean forceAbortForRescue(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        Airdrop airdrop = ACTIVE.get(player.getUUID());
        if (airdrop == null || airdrop.phase == Phase.DONE) {
            return false;
        }
        abort(player, airdrop, "message.tlmformation.airdrop.aborted");
        return true;
    }

    /**
     * 该女仆是否正在空投作业流程中（登机 / 飞行 / 索降 / 索升 / 回收）。
     *
     * <p>作业期间必须压制一切「跟随主人」逻辑，尤其是 TLM 的
     * {@code MaidFollowOwnerTask}：索降途中女仆会脱离座位挂在绳索上，
     * 此时 {@code canBrainMoving()} 为 true、离主人约 40 格，
     * TLM 会直接 {@code teleportToOwner} 把她拽到主人脚下 ——
     * 表现就是「部署时女仆有概率瞬间出现在地面」。</p>
     *
     * <p>落地后由 {@code applyGuardDuty} 切换为广域警戒；作业结束（ACTIVE 移除）后
     * 完全恢复正常跟随 / 战术逻辑。这里只看内存状态，不写任何持久化标记，
     * 所以不会出现「女仆永久不跟随」的脏数据。</p>
     */
    public static boolean isAirdropManaged(@Nullable UUID maidId) {
        if (maidId == null) {
            return false;
        }
        for (Airdrop airdrop : ACTIVE.values()) {
            if (airdrop.phase != Phase.DONE && airdrop.squad.contains(maidId)) {
                return true;
            }
        }
        return false;
    }

    /** 同上，直接传女仆实体。 */
    public static boolean isAirdropManaged(@Nullable EntityMaid maid) {
        return maid != null && isAirdropManaged(maid.getUUID());
    }

    /**
     * 该玩家是否还有“已部署但还没撤离”的空投小队。
     *
     * <p>两个来源：正在进行的空投作业（空中 / 索降 / 飞离），以及已经落地、
     * 队伍仍处于部署状态（{@link MaidTeamManager#isAirdropDeployed}）。</p>
     */
    public static boolean hasDeployedSquad(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        Airdrop airdrop = ACTIVE.get(player.getUUID());
        if (airdrop != null && airdrop.phase != Phase.DONE) {
            return true;
        }
        return MaidTeamManager.isAirdropDeployed(player);
    }

    /** 统一的“先前小队尚未撤离”提示（F9 / 呼救器 / 空投入口都用它）。 */
    public static void notifySquadDeployed(ServerPlayer player) {
        if (player != null) {
            message(player, "message.tlmformation.airdrop.squad_deployed");
        }
    }

    /** 头顶是否有足够空域（洞穴 / 室内返回 false）。 */
    public static boolean hasSignal(ServerPlayer player) {
        return SkyVisibility.hasOpenSky(player);
    }

    // ===================== tick =====================
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
            Airdrop airdrop = ACTIVE.get(id);
            if (airdrop == null || airdrop.phase == Phase.DONE) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                // 玩家下线：清掉直升机，别把女仆留在无人认领的载具里
                discardWithRiders(airdrop);
                airdrop.phase = Phase.DONE;
                ACTIVE.remove(id);
                continue;
            }
            try {
                tick(player, airdrop);
            } catch (Throwable throwable) {
                TLMFormationMod.LOGGER.error("[Airdrop] tick failed for " + id, throwable);
                abort(player, airdrop, "message.tlmformation.airdrop.error");
            }
        }
    }

    // ===================== 台词池（全部走 lang key） =====================
    /** 直升机抵达悬停点时的通讯台词（需求：5 条）。 */
    private static final String[] ARRIVAL_LINES = {
            "line.tlmformation.airdrop.arrival.1",
            "line.tlmformation.airdrop.arrival.2",
            "line.tlmformation.airdrop.arrival.3",
            "line.tlmformation.airdrop.arrival.4",
            "line.tlmformation.airdrop.arrival.5"
    };

    /**
     * 回收（索升）任务抵达悬停点时的台词（3 条）。
     *
     * <p>部署与回收原来是同一池的索降口吻文案（“准备索降”），回收时听起来完全不对；
     * 这里单独给回收一套“放下绳索、请归队登机”的文案。</p>
     */
    private static final String[] ARRIVAL_PICKUP_LINES = {
            "line.tlmformation.airdrop.arrival_pickup.1",
            "line.tlmformation.airdrop.arrival_pickup.2",
            "line.tlmformation.airdrop.arrival_pickup.3"
    };

    /** 索降落地后的通讯台词（10 条）。 */
    private static final String[] LANDING_LINES = {
            "line.tlmformation.airdrop.landing.1",
            "line.tlmformation.airdrop.landing.2",
            "line.tlmformation.airdrop.landing.3",
            "line.tlmformation.airdrop.landing.4",
            "line.tlmformation.airdrop.landing.5",
            "line.tlmformation.airdrop.landing.6",
            "line.tlmformation.airdrop.landing.7",
            "line.tlmformation.airdrop.landing.8",
            "line.tlmformation.airdrop.landing.9",
            "line.tlmformation.airdrop.landing.10"
    };

    /** 直升机撤离时的通讯台词（10 条）。 */
    private static final String[] DEPARTURE_LINES = {
            "line.tlmformation.airdrop.departure.1",
            "line.tlmformation.airdrop.departure.2",
            "line.tlmformation.airdrop.departure.3",
            "line.tlmformation.airdrop.departure.4",
            "line.tlmformation.airdrop.departure.5",
            "line.tlmformation.airdrop.departure.6",
            "line.tlmformation.airdrop.departure.7",
            "line.tlmformation.airdrop.departure.8",
            "line.tlmformation.airdrop.departure.9",
            "line.tlmformation.airdrop.departure.10"
    };

    /** 以「[小队通讯] 女仆：xxx」格式广播一条随机台词（玩家 + 64 格内其他玩家）。 */
    private static void broadcastComm(ServerPlayer player, String[] lineKeys) {
        if (lineKeys.length == 0) {
            return;
        }
        String key = lineKeys[player.getRandom().nextInt(lineKeys.length)];
        Component msg = Component.translatable("chat.tlmformation.squad.comm",
                Component.translatable(key));
        player.sendSystemMessage(msg);
        double r = 64.0D;
        for (ServerPlayer other : player.serverLevel().getPlayers(p -> p.distanceToSqr(player) < r * r)) {
            if (other != player) {
                other.sendSystemMessage(msg);
            }
        }
    }

    private static void tick(ServerPlayer player, Airdrop airdrop) {
        EntityHelicopter heli = airdrop.heli;
        if (heli == null || heli.isRemoved() || !heli.isAlive()) {
            message(player, "message.tlmformation.airdrop.heli_lost");
            airdrop.phase = Phase.DONE;
            ACTIVE.remove(player.getUUID());
            MaidTeamManager.sync(player);
            return;
        }
        if (heli.level() != player.serverLevel()) {
            // 玩家切了维度：在原维度把人放到地面再丢弃，不做跨维度传送
            message(player, "message.tlmformation.airdrop.dimension_left");
            discardWithRiders(airdrop);
            airdrop.phase = Phase.DONE;
            ACTIVE.remove(player.getUUID());
            MaidTeamManager.sync(player);
            return;
        }

        airdrop.phaseTicks++;
        airdrop.totalTicks++;
        // 索降机械本身（开门 100 + 抛绳 8 + 流水线 50 + 下滑 ~175 + 关门 30）可能接近 400 tick，
        // 执行期间不累计超时；一旦卡住（长时间不处于索降状态）才会触发 400 tick 上限。
        if (airdrop.phase == Phase.HOVER && heli.isRappelling()) {
            airdrop.phaseTicks = 0;
        }
        if (airdrop.phaseTicks > PHASE_TIMEOUT || airdrop.totalTicks > TOTAL_TIMEOUT) {
            abort(player, airdrop, "message.tlmformation.airdrop.timeout");
            return;
        }

        switch (airdrop.phase) {
            case FLYIN -> tickFlyIn(player, airdrop, heli);
            case HOVER -> tickHover(player, airdrop, heli);
            case EXTRACT -> tickExtract(player, airdrop, heli);
            default -> {
            }
        }
    }

    // ===================== 阶段：进场（纯 +Z） =====================
    private static void tickFlyIn(ServerPlayer player, Airdrop airdrop, EntityHelicopter heli) {
        // x / y 锁死，只有 z 朝玩家推进 —— 全程沿 +Z 轴，没有任何“绕后 / 斜插”逻辑
        heli.setAutopilotTarget(new Vec3(airdrop.flightX, airdrop.flightY, player.getZ()),
                FLYIN_SPEED, FLIGHT_YAW);
        if (heli.getZ() >= player.getZ() - ARRIVE_DISTANCE) {
            airdrop.phase = Phase.HOVER;
            airdrop.phaseTicks = 0;
            enterHover(player, airdrop, heli);
        }
    }

    private static void enterHover(ServerPlayer player, Airdrop airdrop, EntityHelicopter heli) {
        // 交给索降锁定，先松开自动驾驶
        heli.clearAutopilot();
        heli.setDeltaMovement(Vec3.ZERO);
        heli.setYRot(FLIGHT_YAW);
        heli.yRotO = FLIGHT_YAW;

        // 抵达悬停点：先来一句通讯台词（需求 6）。部署 / 回收用不同的文案池
        broadcastComm(player, airdrop.mode == Mode.PICKUP ? ARRIVAL_PICKUP_LINES : ARRIVAL_LINES);
        // 记录悬停时的乘客数：索降结束后用它判断“到底有没有人真的落地”
        airdrop.startPax = heli.getPassengers().size();

        boolean started;
        if (airdrop.mode == Mode.PICKUP) {
            // FORCE-GATHER：先把小队成员拉到直升机正下方（绳底附近），保证索升候选不为空
            try {
                net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) heli.level();
                int i = 0;
                for (java.util.UUID id : airdrop.squad) {
                    if (!(level.getEntity(id) instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)
                            || !maid.isAlive() || maid.isPassenger()) {
                        continue;
                    }
                    double ox = (i % 2 == 0 ? 0.9D : -0.9D);
                    double oz = (i % 3 - 1) * 0.9D;
                    net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(
                            heli.getX() + ox, heli.getY(), heli.getZ() + oz);
                    int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            feet.getX(), feet.getZ());
                    maid.teleportTo(heli.getX() + ox, surfaceY, heli.getZ() + oz);
                    i++;
                }
            } catch (Throwable throwable) {
                TLMFormationMod.LOGGER.error("[AIRDROP-DEBUG] force-gather failed", throwable);
            }
            started = HelicopterRappelManager.startAutonomousAscend(heli);
            TLMFormationMod.LOGGER.info("[AIRDROP-DEBUG] ascend started={} pax={} altitude={}",
                    started, heli.getPassengers().size(), heli.altitudeToGround());
            if (started) {
                message(player, "message.tlmformation.airdrop.pickup_hover");
            }
        } else {
            // 驾驶位（座位 0）默认留机。只有“机舱里一个人都没有”时才连驾驶位一起放下：
            // 单名女仆空投、或多名女仆只登上驾驶位（其余登机失败）时，否则永远没人能下机。
            boolean hasCabinPassenger = false;
            for (Entity rider : heli.getPassengers()) {
                if (heli.getSeatIndex(rider) > 0) {
                    hasCabinPassenger = true;
                    break;
                }
            }
            boolean includePilotSeat = !hasCabinPassenger;
            started = HelicopterRappelManager.startAutonomousRappel(heli, includePilotSeat);
            TLMFormationMod.LOGGER.info("[AIRDROP-DEBUG] rappel started={} pax={} includePilot={} altitude={}",
                    started, heli.getPassengers().size(), includePilotSeat, heli.altitudeToGround());
            if (started) {
                message(player, "message.tlmformation.airdrop.hover");
            }
        }
        if (!started) {
            abort(player, airdrop, "message.tlmformation.airdrop.rappelling_failed");
        }
    }

    // ===================== 阶段：悬停索降 / 索升 =====================
    private static void tickHover(ServerPlayer player, Airdrop airdrop, EntityHelicopter heli) {
        if (heli.isRappelling()) {
            return;   // 开门 / 抛绳 / 下滑 / 关门（或索升），全部交给 HelicopterRappelManager
        }
        // 索降被安全系统中途打断（被击毁 / 坠机 / 引擎丢失…）：
        // 此时人还挂在绳上或座位里，绝不能当成“已经落地”直接走 enterExtract，
        // 否则 ejectRiders 会把整队人从几十格高空瞬间卸到地面（旧 bug 的可见现象）。
        if (heli.isRappelAborted()) {
            abort(player, airdrop, "message.tlmformation.airdrop.rappelling_failed");
            return;
        }
        if (airdrop.mode == Mode.DEPLOY) {
            // 只有真的有人离开机舱（索降落地）时才播“落地台词”，
            // 否则会出现“脚还没沾地就喊全员安全落地”。
            boolean landed = airdrop.startPax > 0
                    && heli.getPassengers().size() < airdrop.startPax;
            if (landed) {
                broadcastComm(player, LANDING_LINES);
            }
            // 交换部署：新队伍落完之后，同一架直升机放绳把旧小队接走
            if (!airdrop.previousSquad.isEmpty() && !airdrop.oldBoardingStarted) {
                airdrop.oldBoardingStarted = true;
                if (startSwapBoarding(player, airdrop, heli)) {
                    return;   // 旧小队正在归队爬绳，等这一轮索升结束再进入撤离
                }
            }
        }
        enterExtract(player, airdrop, heli);
    }

    /**
     * 交换部署的第二段：让旧小队沿着绳索爬上这架直升机。
     *
     * @return true = 已进入索升动画（后续由 {@code HelicopterRappelManager} 驱动）；
     *         false = 起不来（旧小队不在附近 / 没信号 / 已在作业），直接走撤离，收尾时把旧队送回据点
     */
    private static boolean startSwapBoarding(ServerPlayer player, Airdrop airdrop,
                                             EntityHelicopter heli) {
        try {
            heli.setAscendFilter(new HashSet<>(airdrop.previousSquad));
            boolean started = HelicopterRappelManager.startAutonomousAscend(heli);
            TLMFormationMod.LOGGER.info(
                    "[AIRDROP-DEBUG] swap boarding started={} old={} altitude={}",
                    started, airdrop.previousSquad.size(), heli.altitudeToGround());
            if (started) {
                chatMessage(player, "message.tlmformation.airdrop.swap_boarding");
            }
            return started;
        } catch (Throwable throwable) {
            TLMFormationMod.LOGGER.error("[Airdrop] swap boarding failed", throwable);
            return false;
        }
    }

    // ===================== 阶段：撤离（纯 +Z） =====================
    private static void enterExtract(ServerPlayer player, Airdrop airdrop, EntityHelicopter heli) {
        airdrop.phase = Phase.EXTRACT;
        airdrop.phaseTicks = 0;
        if (airdrop.mode == Mode.DEPLOY) {
            message(player, "message.tlmformation.airdrop.deployed");
            // 索降只会放下座位 1..n；驾驶位（以及异常情况下没下机的乘客）在这里安全落地。
            // 交换部署时机舱里坐的是刚接上来的旧小队 —— 绝不能把他们卸下去。
            TLMFormationMod.LOGGER.info("[AIRDROP-DEBUG] enterExtract eject, pax={} rappelling={} swap={}",
                    heli.getPassengers().size(), heli.isRappelling(),
                    airdrop.previousSquad.size());
            if (airdrop.previousSquad.isEmpty()) {
                ejectRiders(heli);
            }
        } else if (heli.getPassengers().isEmpty()) {
            // 没人登机：不要播撤离台词，避免“飞机还在悬停就说小队撤离了”
            message(player, "message.tlmformation.airdrop.pickup_empty");
        } else {
            message(player, "message.tlmformation.airdrop.pickup_aboard");
            // 回收完成提示在这里（直升机刚起飞）就发系统聊天，
            // 不要拖到飞离 120 格的收尾阶段再发 —— 那时玩家往往已经在按下一次「部署」，
            // 会误以为“召唤触发了撤离聊天”。
            chatMessage(player, "message.tlmformation.team.pickup_done", airdrop.team + 1);
            // 确认有人登机 → 这时才是真正的“撤离”，台词此刻播才对
            broadcastComm(player, DEPARTURE_LINES);
            airdrop.departureAnnounced = true;
        }
        // 部署模式的撤离台词不在这里播：等直升机真正离开悬停点后再播（见 tickExtract）

        // 沿 +Z 飞离：x / y 锁死，只有 z 一直加
        heli.setAutopilotTarget(extractTarget(player, airdrop), EXTRACT_SPEED, FLIGHT_YAW);
    }

    private static void tickExtract(ServerPlayer player, Airdrop airdrop, EntityHelicopter heli) {
        heli.setAutopilotTarget(extractTarget(player, airdrop), EXTRACT_SPEED, FLIGHT_YAW);
        // 部署模式：起飞后真正离开悬停点 16 格再播“小队正在撤离”，
        // 不再和落地台词挤在同一 tick（否则听起来像刚落地就喊撤离）。
        if (!airdrop.departureAnnounced && heli.getZ() - player.getZ() > 16.0D) {
            airdrop.departureAnnounced = true;
            broadcastComm(player, DEPARTURE_LINES);
        }
        if (heli.getZ() - player.getZ() > EXTRACT_DISTANCE) {
            finish(player, airdrop);
        }
    }

    private static Vec3 extractTarget(ServerPlayer player, Airdrop airdrop) {
        return new Vec3(airdrop.flightX, airdrop.flightY,
                player.getZ() + EXTRACT_DISTANCE + 60.0D);
    }

    // ===================== 收尾 =====================
    private static void finish(ServerPlayer player, Airdrop airdrop) {
        if (airdrop.mode == Mode.PICKUP) {
            message(player, "message.tlmformation.airdrop.pickup_done");
            // 先让整队回家（含还坐在机舱里的女仆），再丢弃直升机
            MaidTeamManager.finishAirdropPickup(player, airdrop.team);
            discardWithRiders(airdrop);
        } else {
            message(player, "message.tlmformation.airdrop.done");
            // 交换部署：把旧小队（已登机的 + 没赶上的）一起送回据点/出生点
            if (!airdrop.previousSquad.isEmpty()) {
                try {
                    int sent = MaidTeamManager.returnSquadHome(player, airdrop.previousSquad,
                            airdrop.previousTeam);
                    chatMessage(player, "message.tlmformation.airdrop.swap_done", sent);
                } catch (Throwable throwable) {
                    TLMFormationMod.LOGGER.error("[Airdrop] swap squad return failed", throwable);
                }
            }
            discardWithRiders(airdrop);
        }
        airdrop.phase = Phase.DONE;
        ACTIVE.remove(player.getUUID());
        MaidTeamManager.sync(player);
    }

    private static void abort(ServerPlayer player, Airdrop airdrop, String messageKey) {
        message(player, messageKey);
        EntityHelicopter heli = airdrop.heli;
        if (heli != null && !heli.isRemoved()) {
            // 索降 / 索升中的乘客先交还给索降管理器（它会把人放回地面）
            if (heli.isRappelling()) {
                HelicopterRappelManager.forceAbort(heli);
            }
            // 还坐在座位上的女仆：送回玩家身边（等价于旧的传送召唤），而不是从高空丢下去
            rescueRiders(player, heli);
            heli.clearAutopilot();
            heli.discard();
        }
        airdrop.heli = null;
        airdrop.phase = Phase.DONE;
        ACTIVE.remove(player.getUUID());
        MaidTeamManager.sync(player);
    }

    /** 丢弃直升机；还挂在上面的乘客先落到地面，避免“乘客跟着被移除的载具消失”。 */
    private static void discardWithRiders(Airdrop airdrop) {
        EntityHelicopter heli = airdrop.heli;
        airdrop.heli = null;
        if (heli == null || heli.isRemoved()) {
            return;
        }
        if (heli.isRappelling()) {
            HelicopterRappelManager.forceAbort(heli);
        }
        ejectRiders(heli);
        heli.clearAutopilot();
        heli.discard();
    }

    // ===================== 玩家上下线 =====================
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Airdrop airdrop = ACTIVE.remove(player.getUUID());
        if (airdrop != null) {
            discardWithRiders(airdrop);
            airdrop.phase = Phase.DONE;
        }
    }

    /**
     * 服务器关闭：直接清空状态。
     *
     * <p>此时世界正在存档，直升机与乘客会被一起写进存档；队伍的 active 标记仍然存在，
     * 玩家下次进服可以用 F9「小队撤离」把还坐在机舱里的女仆收回，不会永久丢失。</p>
     */
    @SubscribeEvent
    public static void onServerStopping(
            net.minecraftforge.event.server.ServerStoppingEvent event) {
        ACTIVE.clear();
    }

    // ===================== 女仆选取 / 登机 =====================
    /**
     * 可空投的女仆：先按队伍顺序，再用玩家附近的其它女仆补足（不新建实体）。
     *
     * @param exclude 不允许登机的 UUID（swap 部署时的旧小队：她们要留在地面等登机，不能又被装进机舱）
     */
    private static List<EntityMaid> findSquad(ServerPlayer player, int team, Set<UUID> exclude) {
        ServerLevel level = player.serverLevel();
        List<EntityMaid> result = new ArrayList<>();
        Set<UUID> used = new HashSet<>();

        for (UUID id : MaidTeamManager.teamMemberIds(player, team)) {
            if (result.size() >= MAX_SQUAD) {
                break;
            }
            if (exclude.contains(id)) {
                continue;
            }
            Entity entity = level.getEntity(id);
            if (entity instanceof EntityMaid maid && isAvailable(player, maid)) {
                result.add(maid);
                used.add(id);
            }
        }
        if (result.size() < MAX_SQUAD) {
            List<EntityMaid> extra = level.getEntitiesOfClass(EntityMaid.class,
                    player.getBoundingBox().inflate(SEARCH_RADIUS),
                    maid -> isAvailable(player, maid) && !used.contains(maid.getUUID())
                            && !exclude.contains(maid.getUUID()));
            extra.sort(Comparator.comparingDouble(maid -> maid.distanceToSqr(player)));
            for (EntityMaid maid : extra) {
                if (result.size() >= MAX_SQUAD) {
                    break;
                }
                if (used.add(maid.getUUID())) {
                    result.add(maid);
                }
            }
        }
        return result;
    }

    private static boolean isAvailable(ServerPlayer player, EntityMaid maid) {
        return maid != null
                && maid.isAlive()
                && !maid.isRemoved()
                && maid.isOwnedBy(player)
                && !maid.isPassenger()
                && !MaidDownedManager.isDowned(maid.getUUID())
                && !MaidReviveManager.isWaiting(player, maid.getUUID());
    }

    /**
     * 把女仆放进直升机座位（按调用顺序依次入座：第 1 名 = 座位 0 = 驾驶）。
     * 女仆可以在任意距离被“拉上机”，不需要先走到直升机旁边。
     *
     * <p>卓越前线的 {@code addPassenger} 在座位表未初始化 / 座位号越界时会<b>静默返回</b>，
     * 结果是 {@code startRiding} 返回 true 但座位表里没有这个人（幽灵乘客），
     * 索降队列自然也就空了。所以这里最多重试 {@link #MOUNT_ATTEMPTS} 次：
     * 第 2 次起先把女仆摆到目标座位骨骼的世界坐标再上机，并逐次记录 [MOUNT-DEBUG]。</p>
     */
    private static boolean mount(EntityHelicopter heli, EntityMaid maid) {
        Vec3 originalPos = maid.position();
        try {
            VehicleMaidRelease.release(maid);
            if (maid.isPassenger()) {
                maid.stopRiding();
            }
            maid.setTarget(null);
            maid.getNavigation().stop();
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            FormationManager.unpark(maid);
            for (int attempt = 1; attempt <= MOUNT_ATTEMPTS; attempt++) {
                if (attempt > 1) {
                    // 重试前把女仆摆到座位骨骼上：卓前的座位登记依赖“人在座位附近”的那套数据，
                    // 刚生成的载具数据还没算好时，隔着 90 格硬上机很容易登记失败。
                    placeAtSeat(heli, maid);
                }
                boolean riding = maid.startRiding(heli, true);
                int seat = heli.getSeatIndex(maid);
                boolean registered = riding && heli.getPassengers().contains(maid) && seat >= 0;
                TLMFormationMod.LOGGER.info(
                        "[MOUNT-DEBUG] attempt={} riding={} registered={} seat={} pax={}",
                        attempt, riding, registered, seat, heli.getPassengers().size());
                if (riding && registered) {
                    maid.setNoAi(false);
                    maid.setNoGravity(false);
                    maid.setDeltaMovement(Vec3.ZERO);
                    maid.fallDistance = 0.0F;
                    return true;
                }
                if (maid.isPassenger()) {
                    maid.stopRiding();   // 幽灵乘客：骑着但没登记座位，先下来再重试
                }
            }
            TLMFormationMod.LOGGER.warn("[MOUNT-DEBUG] give up maid={} pax={}",
                    maid.getUUID(), heli.getPassengers().size());
            // 重试期间可能已经把女仆挪到过 40 格高空的座位上：全部失败就放回原位，
            // 绝不能把没登机的女仆留在半空（会摔伤 / 掉进未加载区块）。
            if (maid.position().distanceToSqr(originalPos) > 1.0E-4D) {
                maid.teleportTo(originalPos.x, originalPos.y, originalPos.z);
                maid.setDeltaMovement(Vec3.ZERO);
                maid.fallDistance = 0.0F;
            }
            return false;
        } catch (Throwable throwable) {
            TLMFormationMod.LOGGER.error("[Airdrop] mount failed for " + maid.getUUID(), throwable);
            // 异常路径同理：别把女仆留在高空
            try {
                if (maid.position().distanceToSqr(originalPos) > 1.0E-4D) {
                    maid.teleportTo(originalPos.x, originalPos.y, originalPos.z);
                    maid.setDeltaMovement(Vec3.ZERO);
                    maid.fallDistance = 0.0F;
                }
            } catch (Throwable ignored) {
                // 兜底失败就算了，至少不要让异常继续外抛
            }
            return false;
        }
    }

    /**
     * 登机重试前的落点：下一个空座位骨骼的世界坐标（走卓越前线的载具矩阵）。
     * 数据未就绪时退回到直升机原点。
     */
    private static void placeAtSeat(EntityHelicopter heli, EntityMaid maid) {
        Vec3 target = null;
        try {
            int seat = Math.max(0, Math.min(heli.getPassengers().size(),
                    EntityHelicopter.SEAT_BONES.length - 1));
            Vec3 local = heli.boneVehiclePos(EntityHelicopter.seatBoneName(seat));
            org.joml.Matrix4d transform = heli.getTransformFromString("Vehicle", 1.0F);
            if (local != null && transform != null) {
                org.joml.Vector4d world = heli.transformPosition(transform, local.x, local.y, local.z);
                target = new Vec3(world.x, world.y, world.z);
            }
        } catch (Throwable ignored) {
            // 座位数据还没初始化：用下面的兜底
        }
        if (target == null) {
            target = heli.position();
        }
        maid.setPos(target.x, target.y, target.z);
        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0.0F;
    }

    // ===================== 乘客落地 =====================
    /** 撤离时把还坐在机舱里的乘客（驾驶）放到正下方地面，并恢复警戒状态。 */
    private static void ejectRiders(EntityHelicopter heli) {
        for (Entity rider : new ArrayList<>(heli.getPassengers())) {
            Vec3 landing = null;
            if (rider instanceof LivingEntity living) {
                landing = heli.getDismountLocationForPassenger(living);
            }
            rider.stopRiding();
            if (landing != null) {
                if (rider instanceof ServerPlayer serverPlayer) {
                    serverPlayer.teleportTo(landing.x, landing.y, landing.z);
                } else {
                    rider.teleportTo(landing.x, landing.y, landing.z);
                }
            }
            rider.setNoGravity(false);
            rider.setDeltaMovement(Vec3.ZERO);
            rider.fallDistance = 0.0F;
            if (rider instanceof Mob mob) {
                mob.setNoAi(false);
            }
            if (rider instanceof EntityMaid maid) {
                HelicopterRappelManager.applyGuardDuty(maid);
            }
        }
    }

    /** 中止时把座位上的女仆送回玩家身边（与旧的传送召唤一致），避免高空掉落或跟着载具消失。 */
    private static void rescueRiders(ServerPlayer player, EntityHelicopter heli) {
        ServerLevel level = player.serverLevel();
        for (Entity rider : new ArrayList<>(heli.getPassengers())) {
            rider.stopRiding();
            if (rider instanceof ServerPlayer serverPlayer) {
                serverPlayer.teleportTo(player.getX(), player.getY(), player.getZ());
                continue;
            }
            if (rider instanceof EntityMaid maid) {
                Vec3 safe = TargetSafety.findSafe(level, maid, player.position());
                if (safe != null) {
                    maid.teleportTo(safe.x, safe.y, safe.z);
                } else {
                    maid.teleportTo(player.getX(), player.getY(), player.getZ());
                }
                maid.setNoGravity(false);
                maid.setNoAi(false);
                maid.setDeltaMovement(Vec3.ZERO);
                maid.fallDistance = 0.0F;
                HelicopterRappelManager.applyGuardDuty(maid);
            } else {
                rider.setNoGravity(false);
                rider.setDeltaMovement(Vec3.ZERO);
                rider.fallDistance = 0.0F;
                if (rider instanceof Mob mob) {
                    mob.setNoAi(false);
                }
            }
        }
    }

    // ===================== 坐标 / 工具 =====================
    private static int normalizeTeam(ServerPlayer player, int team) {
        int count = MaidTeamManager.teamCount(player);
        if (team >= 0 && team < count) {
            return team;
        }
        int active = MaidTeamManager.activeTeam(player);
        if (active >= 0 && active < count) {
            return active;
        }
        return 0;
    }

    /**
     * 生成点：玩家 <b>-Z</b> 方向 {@link #SPAWN_DISTANCE} 格、<b>同一个 x</b>，
     * 高度 = 玩家 + {@link #SPAWN_HEIGHT}（限制在地表 +25 ~ 世界顶 -15）。
     *
     * <p>因为进场是纯 +Z 直线，所以生成点的 x 就是整条航线的 x。</p>
     */
    private static Vec3 spawnPoint(ServerPlayer player, ServerLevel level) {
        double x = player.getX();
        double z = player.getZ() - SPAWN_DISTANCE;
        // 目标点可能不在玩家已加载的区块里：先加载，避免实体掉进未加载区块
        BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(player.getY()), Mth.floor(z));
        level.getChunkAt(pos);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        double minY = surface + 25.0D;
        double maxY = Math.max(minY, level.getMaxBuildHeight() - 15.0D);
        double y = Mth.clamp(player.getY() + SPAWN_HEIGHT, minY, maxY);
        return new Vec3(x, y, z);
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    /** 聊天栏消息（信号差这种需要留痕的提示用，不进 action bar）。 */
    private static void chatMessage(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }

    // ===================== 数据 =====================
    private enum Phase {
        CALL,
        FLYIN,
        HOVER,
        EXTRACT,
        DONE
    }

    /** 作业模式：投人 or 接人。 */
    private enum Mode {
        DEPLOY,
        PICKUP
    }

    private static final class Airdrop {
        private final List<UUID> squad = new ArrayList<>();
        @Nullable
        private EntityHelicopter heli;
        private final Mode mode;
        private final int team;
        /** 整条航线的固定 x（= 生成点 x）。 */
        private final double flightX;
        /** 整条航线的固定 y（= 生成点高度）。 */
        private final double flightY;
        private Phase phase = Phase.CALL;
        private int phaseTicks;
        private int totalTicks;
        /** 悬停开始时的乘客数：用来判断索降结束后是否真的有人落地。 */
        private int startPax;
        /** 撤离台词是否已经播过（每场只播一次，且不在落地同一 tick）。 */
        private boolean departureAnnounced;
        /** 交换部署：上一支已部署小队（本次落地后由同一架直升机接走）。 */
        private final List<UUID> previousSquad = new ArrayList<>();
        /** 交换部署：旧小队所属队伍下标（-1 = 无）。 */
        private int previousTeam = -1;
        /** 交换部署：是否已经尝试过让旧小队登机（每场只试一次）。 */
        private boolean oldBoardingStarted;

        private Airdrop(EntityHelicopter heli, Mode mode, int team, double flightX, double flightY) {
            this.heli = heli;
            this.mode = mode;
            this.team = team;
            this.flightX = flightX;
            this.flightY = flightY;
            this.phase = Phase.FLYIN;
        }
    }
}
