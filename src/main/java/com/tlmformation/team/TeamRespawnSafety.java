package com.tlmformation.team;

import com.tlmformation.TLMFormationMod;
import com.tlmformation.config.FormationConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 玩家重生 / 跨维度时的「小队硬救援」入口。
 *
 * <p>旧实现只遍历当前维度、只取已加载实体，玩家死在远处或另一个维度时静默跳过，
 * 却仍然播报“已强制跃迁至重生点”。现在改为：</p>
 * <ul>
 *   <li>重生：等玩家真正落位后调用 {@link SquadRescueManager#hardRescue}（可跨维度、可加载区块定位）；</li>
 *   <li>跨维度：同样触发一次硬救援，避免小队被留在原维度；</li>
 *   <li>是否启用由 {@code squadRescue.enabled / onRespawn / onDimensionChange} 配置控制。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeamRespawnSafety {
    private TeamRespawnSafety() {
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!FormationConfig.SQUAD_RESCUE_ENABLED.get()
                || !FormationConfig.SQUAD_RESCUE_ON_RESPAWN.get()) {
            return;
        }
        // 延后一个任务：确保玩家已经被放到重生点，位置可用
        player.server.execute(() -> {
            SquadRescueManager.Report report = SquadRescueManager.hardRescue(player, "respawn");
            if (report.total() > 0) {
                SquadReconcileManager.markRescued(player);
            }
        });
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!FormationConfig.SQUAD_RESCUE_ENABLED.get()
                || !FormationConfig.SQUAD_RESCUE_ON_DIMENSION.get()) {
            return;
        }
        player.server.execute(() -> {
            SquadRescueManager.Report report = SquadRescueManager.hardRescue(player, "dimension");
            if (report.total() > 0) {
                SquadReconcileManager.markRescued(player);
            }
        });
    }
}
