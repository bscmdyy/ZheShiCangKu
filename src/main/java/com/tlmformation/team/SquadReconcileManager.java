package com.tlmformation.team;

import com.tlmformation.TLMFormationMod;
import com.tlmformation.config.FormationConfig;
import com.tlmformation.vehicle.AirdropManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 已部署小队的「周期对账」兜底。
 *
 * <p>有些传送<b>没有任何事件可挂钩</b>（小地图模组 / 指令 / 传送物品），玩家一旦被传送走，
 * 小队就被留在原地且区块卸载，F9 撤离也点不动。这里每 {@code reconcileIntervalTicks}
 * （默认 5 秒）做一次轻量检查：</p>
 *
 * <ul>
 *   <li>没有激活队伍：若 {@code airdropDeployed} 还卡着 true，清掉（避免 F9 永久拦截）；</li>
 *   <li>有空投动画在跑：不插手（动画自己会处理）；</li>
 *   <li>激活小队里有人未加载 / 在别的维度，或最近成员距离 &gt; {@code maxDistance}：
 *       触发一次 {@link SquadRescueManager#hardRescue}（带冷却去抖）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SquadReconcileManager {
    /** 每个玩家上次自动救援的 gameTime，防止与重生/换维度钩子重复触发。 */
    private static final Map<UUID, Long> LAST_RESCUE = new HashMap<>();

    private SquadReconcileManager() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!FormationConfig.SQUAD_RESCUE_ENABLED.get()
                || !FormationConfig.SQUAD_RECONCILE_ENABLED.get()) {
            return;
        }
        int interval = Math.max(20, FormationConfig.SQUAD_RECONCILE_TICKS.get());
        if (event.getServer().getTickCount() % interval != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            try {
                reconcile(player);
            } catch (Throwable throwable) {
                TLMFormationMod.LOGGER.error("[SquadRescue] reconcile failed for {}", player.getUUID(), throwable);
            }
        }
    }

    private static void reconcile(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.hasDisconnected()) {
            return;
        }
        if (com.tlmformation.team.GrandAssembleManager.isActive(player)) {
            return;   // 集团体模式自己管移动
        }
        if (!MaidTeamManager.hasActiveSquad(player)) {
            // 没有激活队伍但“已部署”标记还在：上次撤离异常，清掉，别让 F9 一直说“尚未撤离”
            if (MaidTeamManager.isAirdropDeployed(player)) {
                MaidTeamManager.clearActiveState(player);
            }
            return;
        }
        if (AirdropManager.isActive(player)) {
            return;   // 空投动画进行中：由状态机负责
        }
        double maxDistance = FormationConfig.SQUAD_RESCUE_MAX_DISTANCE.get();
        int unreachable = SquadRescueManager.countUnreachable(player);
        double nearest = SquadRescueManager.nearestLoadedDistance(player);
        boolean noneLoaded = nearest < 0.0D;

        boolean trigger = noneLoaded
                || unreachable > 0
                || nearest > maxDistance;
        if (!trigger) {
            return;
        }
        long now = player.server.getTickCount();
        long last = LAST_RESCUE.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        if (now - last < FormationConfig.SQUAD_RESCUE_COOLDOWN.get()) {
            return;   // 刚救过，防抖
        }
        LAST_RESCUE.put(player.getUUID(), now);
        TLMFormationMod.LOGGER.info(
                "[SquadRescue] reconcile trigger: unreachable={} nearest={} max={}",
                unreachable, String.format(java.util.Locale.ROOT, "%.1f", nearest), maxDistance);
        SquadRescueManager.hardRescue(player, "reconcile");
    }

    /** 救援成功后调用，避免紧接着的对账又触发一次。 */
    public static void markRescued(ServerPlayer player) {
        if (player != null) {
            LAST_RESCUE.put(player.getUUID(), (long) player.server.getTickCount());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_RESCUE.remove(player.getUUID());
        }
    }
}
