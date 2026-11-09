package com.tlmformation.formation;

import com.tlmformation.config.FormationConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class FormationEvents {
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.getServer().getTickCount() % FormationConfig.RECONCILE_INTERVAL.get() == 0) {
            FormationManager.reconcileAll(event.getServer());
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            FormationManager.tickPlayer(player);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FormationManager.onOwnerRespawn(player);
        if (com.tlmformation.team.GrandAssembleManager.isActive(player)) {
            return;
        }
        if (!FormationConfig.RECALL_ON_RESPAWN.get()) {
            return;
        }

        // Wait until the player is fully placed at the respawn point.
        player.server.execute(() -> {
            FormationManager.recall(player, FormationConfig.RESPAWN_RECALL_DISTANCE.get());
            FormationManager.reconcileAll(player.server);
        });
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FormationManager.onOwnerLogout(player);
        }
    }
}
