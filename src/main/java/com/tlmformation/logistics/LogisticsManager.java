package com.tlmformation.logistics;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import com.tlmformation.TLMFormationMod;


import com.tlmformation.tactical.TacticalLogisticsTask;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LogisticsManager {
    private static final Map<UUID, Long> RESCUE_CD = new HashMap<>();
    private static final long RESCUE_COOLDOWN = 3000L;

    private LogisticsManager() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().overworld().getGameTime();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Long ready = RESCUE_CD.get(player.getUUID());
            if (ready != null && now >= ready) {
                RESCUE_CD.remove(player.getUUID());
                player.displayClientMessage(Component.literal(
                        "[后勤] 救援协议已重新就绪。"), true);
            }
            if (event.getServer().getTickCount() % 20 != 0) continue;
            for (EntityMaid maid : player.serverLevel().getEntitiesOfClass(
                    EntityMaid.class, player.getBoundingBox().inflate(32),
                    m -> m.isAlive() && player.getUUID().equals(m.getOwnerUUID())
                            && isLogistics(m))) {
                logisticsTick(player, maid, now);
            }
        }
    }

    public static boolean hasLogistics(net.minecraft.world.entity.player.Player player) {
        if (player == null) return false;
        return !player.level().getEntitiesOfClass(EntityMaid.class,
                player.getBoundingBox().inflate(96),
                m -> m.isAlive() && player.getUUID().equals(m.getOwnerUUID())
                        && isLogistics(m)).isEmpty();
    }

    public static int rescueCooldownSeconds(net.minecraft.world.entity.player.Player player) {
        if (player == null) return 0;
        Long ready = RESCUE_CD.get(player.getUUID());
        if (ready == null) return 0;
        long left = ready - player.level().getGameTime();
        return left <= 0 ? 0 : (int) ((left + 19) / 20);
    }

    private static boolean isLogistics(EntityMaid maid) {
        return maid.getTask() instanceof TacticalLogisticsTask
                || TacticalLogisticsTask.UID.equals(maid.getTask().getUid());
    }

    private static void logisticsTick(ServerPlayer player, EntityMaid maid, long now) {
        TacticalLogisticsTask.tick(maid, player);

        if (now % 40L == 0) {
            for (ItemEntity item : maid.level().getEntitiesOfClass(ItemEntity.class,
                    maid.getBoundingBox().inflate(8))) {
                ItemStack stack = item.getItem();
                ItemStack rest = maid.getMaidInv().insertItem(0, stack, false);
                if (rest.getCount() != stack.getCount()) {
                    item.setItem(rest);
                    player.displayClientMessage(Component.literal(
                            "[后勤支援] 已回收周边物资。"), true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLethalHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        if (event.getAmount() < player.getHealth()) return;
        if (tryRescue(player)) {
            event.setCanceled(true);
        }
    }

    /** Returns true when a logistics maid consumes the team rescue pact. */
    public static boolean tryRescue(LivingEntity target) {
        ServerPlayer owner = null;
        if (target instanceof ServerPlayer player) owner = player;
        else if (target instanceof EntityMaid maid
                && maid.getOwner() instanceof ServerPlayer player) owner = player;
        if (owner == null) return false;
        final ServerPlayer rescueOwner = owner;

        long now = owner.server.overworld().getGameTime();
        Long ready = RESCUE_CD.get(owner.getUUID());
        if (ready != null && now < ready) return false;

        for (EntityMaid maid : owner.serverLevel().getEntitiesOfClass(
                EntityMaid.class, rescueOwner.getBoundingBox().inflate(64),
                m -> m.isAlive() && rescueOwner.getUUID().equals(m.getOwnerUUID())
                        && isLogistics(m))) {
            RESCUE_CD.put(owner.getUUID(), now + RESCUE_COOLDOWN);
            target.setHealth(10.0F);
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 2));
            if (target.level() instanceof ServerLevel level) {
                level.broadcastEntityEvent(target, (byte) 35);
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        target.getX(), target.getY() + 1.0D, target.getZ(),
                        60, 0.5D, 0.8D, 0.5D, 0.2D);
            }
            target.level().playSound(null, target.blockPosition(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            owner.displayClientMessage(Component.literal(
                    "[后勤] 救援协议激活，目标已脱离濒死！"), true);
            return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && tryRescue(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
