package com.tlmformation.combat;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidHurtTarget;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CombatEvents {
    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide) {
            CombatManager.mark(event.getEntity());
        }
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity living && living != player) {
            CombatManager.mark(player);
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Any damage to the owner (mob hit, arrow, explosion, etc.) opens the window.
            CombatManager.mark(player);
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            CombatManager.mark(player);
        }
    }

    @SubscribeEvent
    public void onMaidHurtTarget(MaidHurtTarget.Post event) {
        if (event.isHurt() && event.getMaid().getOwner() instanceof ServerPlayer player) {
            CombatManager.mark(player);
        }
    }

    @SubscribeEvent
    public void onTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CombatManager.markTeleport(player);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CombatManager.clear(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CombatManager.clear(player);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.getServer().getTickCount() % 10 == 0) {
            CombatManager.reapTargets(event.getServer());
        }
    }
}
