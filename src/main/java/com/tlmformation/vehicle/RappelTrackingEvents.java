package com.tlmformation.vehicle;

import com.tlmformation.TLMFormationMod;
import com.tlmformation.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 索降状态补发：玩家中途进入追踪范围时，把当前索降阶段补发给该玩家，
 * 保证后加入的客户端也能看到绳索。
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RappelTrackingEvents {
    private RappelTrackingEvents() {
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!ModList.get().isLoaded("superbwarfare")) {
            return;
        }
        if (!(event.getTarget() instanceof EntityHelicopter heli)) {
            return;
        }
        if (heli.getRappelPhase() == EntityHelicopter.RAPPEL_IDLE) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            NetworkHandler.sendRappelState(player, heli);
        }
    }
}
