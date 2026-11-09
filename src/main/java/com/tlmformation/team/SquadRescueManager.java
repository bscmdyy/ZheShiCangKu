package com.tlmformation.team;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.config.FormationConfig;
import com.tlmformation.formation.FormationManager;
import com.tlmformation.formation.TargetSafety;
import com.tlmformation.tactical.TacticalManager;
import com.tlmformation.vehicle.AirdropManager;
import com.tlmformation.vehicle.VehicleMaidRelease;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 小队「硬救援」：把已部署小队（含倒地、跨维度、超远距离、未加载）强制召回玩家身边。
 *
 * <p>为什么需要它：原来的 F9「小队撤离」依赖 {@code tryStartExtraction} 与
 * {@code returnTeamInstantly}，两者都只认“当前维度 + 已加载”的女仆；
 * 玩家死后重生到远处、切维度、或用小地图模组传送后，这两条路都会 0 次传送却报成功，
 * 结果状态被清空、女仆永久搁浅。</p>
 *
 * <p>本类走“定位 → 解冻 → 传送 → 收尾”的硬路径：</p>
 * <ol>
 *   <li>{@link MaidLocator} 四级定位（全维度已加载 / 区块票据 / 备份坐标 / 死亡坐标，均可加载区块）；</li>
 *   <li>先中止正在进行的空投动画，避免状态机把刚传回来的人再拽走；</li>
 *   <li>倒地先“睡一觉复活”，解除载具/坐骑/编队停放/战术状态；</li>
 *   <li>统一 {@code teleportTo(level, ...)}（支持跨维度）+ 清速度/重力/AI 冻结；</li>
 *   <li>清空队伍 active / airdropDeployed，并如实汇报“救到几人、缺几人”。</li>
 * </ol>
 */
public final class SquadRescueManager {
    private SquadRescueManager() {
    }

    /** 救援结果：rescued = 已传回，missing = 定位失败，total = 目标人数。 */
    public record Report(int rescued, int missing, int total) {
        public boolean any() {
            return rescued > 0;
        }
    }

    /**
     * 强制把该玩家“当前激活小队”全部召回玩家身边。
     *
     * @param reason 触发原因（respawn / dimension / f9 / reconcile），只用于日志
     */
    public static Report hardRescue(ServerPlayer player, String reason) {
        if (player == null || !player.isAlive() || player.hasDisconnected()) {
            return new Report(0, 0, 0);
        }
        List<UUID> targets = MaidTeamManager.activeMembers(player);
        if (targets.isEmpty()) {
            // 没有激活队伍：顺手把可能卡住的部署标记清掉，避免 F9 一直"尚未撤离"
            if (MaidTeamManager.isAirdropDeployed(player)) {
                MaidTeamManager.clearActiveState(player);
            }
            return new Report(0, 0, 0);
        }

        // 1) 先停空投动画（否则索降/索升状态机会继续接管这些人）
        AirdropManager.forceAbortForRescue(player);

        ServerLevel targetLevel = player.serverLevel();
        int rescued = 0;
        int missing = 0;
        boolean reviveDowned = FormationConfig.SQUAD_RESCUE_REVIVE_DOWNED.get();
        for (UUID id : new LinkedHashSet<>(targets)) {
            EntityMaid maid = MaidLocator.findOrLoad(player.server, player, id);
            if (maid == null) {
                missing++;
                continue;
            }
            try {
                if (reviveDowned && MaidDownedManager.isDowned(id)) {
                    MaidDownedManager.forceSleepRevive(player, maid);
                }
                // 解除一切“会把女仆拽住”的状态
                VehicleMaidRelease.release(maid);
                if (maid.isPassenger()) {
                    maid.stopRiding();
                }
                FormationManager.unpark(maid);
                TacticalManager.clear(maid);
                MaidChunkTicketManagerRelease.release(maid);
                // 落点：优先安全点，退化到玩家脚下
                Vec3 dest = TargetSafety.findSafe(targetLevel, maid, player.position());
                if (dest == null) {
                    dest = player.position();
                }
                maid.teleportTo(targetLevel, dest.x, dest.y, dest.z,
                        Set.<RelativeMovement>of(), player.getYRot(), 0.0F);
                maid.setDeltaMovement(Vec3.ZERO);
                maid.setNoGravity(false);
                maid.setNoAi(false);
                maid.fallDistance = 0.0F;
                maid.setTarget(null);
                maid.getNavigation().stop();
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                maid.getBrain().eraseMemory(MemoryModuleType.PATH);
                try {
                    maid.setSchedule(MaidSchedule.ALL);
                } catch (Throwable ignored) {
                    // TLM 版本差异不影响救援
                }
                FormationManager.engage(maid);
                rescued++;
            } catch (Throwable throwable) {
                missing++;
                TLMFormationMod.LOGGER.error("[SquadRescue] teleport failed for {}", id, throwable);
            }
        }

        // 2) 收尾：小队已回收，清 active / airdropDeployed（否则 F9 会一直拦“尚未撤离”）
        if (rescued > 0) {
            MaidTeamManager.clearActiveState(player);
        }

        TLMFormationMod.LOGGER.info(
                "[SquadRescue] reason={} rescued={} missing={} total={}",
                reason, rescued, missing, targets.size());
        player.displayClientMessage(Component.literal(
                missing == 0
                        ? "[小队救援] 已强制定位并召回 " + rescued + " 名女仆。"
                        : "[小队救援] 已召回 " + rescued + " 名，另有 " + missing + " 名未定位（可能已死亡或数据缺失）。")
                .withStyle(missing == 0
                        ? net.minecraft.ChatFormatting.GREEN
                        : net.minecraft.ChatFormatting.YELLOW), false);
        return new Report(rescued, missing, targets.size());
    }

    /** 便捷：只关心“是否救回了人”。 */
    public static boolean rescueIfNeeded(ServerPlayer player, String reason) {
        return hardRescue(player, reason).any();
    }

    // ===================== 小工具 =====================

    /** 释放该女仆的区块常加载票据（救援后她回到玩家身边，不需要独立 tick 区块）。 */
    private static final class MaidChunkTicketManagerRelease {
        private static void release(EntityMaid maid) {
            try {
                com.tlmformation.chunk.MaidChunkTicketManager.release(
                        (ServerLevel) maid.level(), maid.getUUID());
            } catch (Throwable ignored) {
                // 没有票据时是无操作
            }
        }
    }

    /** 该玩家当前小队里“仍未加载/不在同一维度”的人数（对账用）。 */
    public static int countUnreachable(ServerPlayer player) {
        int count = 0;
        for (UUID id : MaidTeamManager.activeMembers(player)) {
            EntityMaid loaded = MaidLocator.findLoaded(player.server, id);
            if (loaded == null || loaded.level() != player.level()) {
                count++;
            }
        }
        return count;
    }

    /** 距离玩家最近的小队成员距离；没有已加载成员时返回 -1。 */
    public static double nearestLoadedDistance(ServerPlayer player) {
        double best = -1.0D;
        for (UUID id : MaidTeamManager.activeMembers(player)) {
            EntityMaid loaded = MaidLocator.findLoaded(player.server, id);
            if (loaded == null || loaded.level() != player.level()) {
                continue;
            }
            double distance = Math.sqrt(loaded.distanceToSqr(player));
            if (best < 0.0D || distance < best) {
                best = distance;
            }
        }
        return best;
    }

    /** 安全落点搜索（供其它救援路径复用）。 */
    @Nullable
    public static Vec3 safeSpot(ServerPlayer player, EntityMaid maid) {
        return TargetSafety.findSafe(player.serverLevel(), maid, player.position());
    }
}
