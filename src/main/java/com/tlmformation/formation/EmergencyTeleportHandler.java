package com.tlmformation.formation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.config.FormationConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Emergency teleport fallback, deliberately independent from FollowRange.
 * FollowRange can never cause a teleport; only a much larger distance can.
 */
public final class EmergencyTeleportHandler {
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.getServer().getTickCount() % 20 != 0) {
            return;
        }

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (com.tlmformation.team.GrandAssembleManager.isActive(player)) {
                continue; // grand mode: never auto-teleport
            }
            double configured = FormationConfig.EMERGENCY_TELEPORT_DISTANCE.get();
            double followRange = FormationManager.getFollowRange(player);
            double emergency = Math.max(configured, followRange + 16.0D);
            double emergencySqr = emergency * emergency;

            for (EntityMaid maid : player.serverLevel().getEntitiesOfClass(EntityMaid.class,
                    player.getBoundingBox().inflate(256.0D),
                    maid -> player.getUUID().equals(maid.getOwnerUUID())
                            && maid.isAlive()
                            && !maid.isHomeModeEnable()
                            && !com.tlmformation.tactical.TacticalManager.hasState(maid)
                            // 空投作业中的女仆（含索降途中脱离座位的）绝不能被跟随逻辑拽走
                            && !com.tlmformation.vehicle.AirdropManager.isAirdropManaged(maid)
                            && maid.canBrainMoving())) {
                if (maid.distanceToSqr(player) <= emergencySqr) {
                    continue;
                }
                // 只在真的要传送时才解绑载具：原来无条件 release() 会把索降中的女仆
                // 的 setNoAi(true) 解掉，随后 TLM 跟随任务就能把她瞬移下去。
                com.tlmformation.vehicle.VehicleMaidRelease.release(maid);
                if (maid.teleportToOwner(player)) {
                    maid.getNavigation().stop();
                    maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                    if (FormationConfig.DEBUG.get()) {
                        TLMFormationMod.LOGGER.info("[TLMF] emergency teleported {} to {}",
                                maid.getUUID(), player.getGameProfile().getName());
                    }
                }
            }
        }
    }
}
