package com.tlmformation.logistics;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.ModItems;
import com.tlmformation.TLMFormationMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HealingBarrierManager {
    private static final Map<UUID, Long> BARRIER_CD = new HashMap<>();
    private static final java.util.Set<UUID> BARRIER_READY_NOTIFIED = new java.util.HashSet<>();
    private static final List<Barrier> BARRIERS = new ArrayList<>();
    private static final long COOLDOWN = 1800L;
    private static final int DURATION = 400;

    private static final class Barrier {
        final ServerLevel level;
        final Vec3 center;
        final long until;
        final ItemEntity icon;
        final UUID owner;

        Barrier(ServerLevel level, Vec3 center, long until, ItemEntity icon, UUID owner) {
            this.level = level;
            this.center = center;
            this.until = until;
            this.icon = icon;
            this.owner = owner;
        }
    }

    private HealingBarrierManager() {
    }

    public static void tryCreate(ServerPlayer owner, EntityMaid maid, long now) {
        Long ready = BARRIER_CD.get(owner.getUUID());
        if (ready != null && now < ready) return;

        LivingEntity target = null;
        for (LivingEntity e : maid.level().getEntitiesOfClass(
                LivingEntity.class, maid.getBoundingBox().inflate(8.0D))) {
            boolean friendly = e == owner
                    || (e instanceof EntityMaid m
                    && owner.getUUID().equals(m.getOwnerUUID()));
            if (friendly && e.isAlive()
                    && e.getHealth() <= e.getMaxHealth() * 0.5F) {
                target = e;
                break;
            }
        }
        if (target == null) return;

        BARRIER_CD.put(owner.getUUID(), now + COOLDOWN);
        BARRIER_READY_NOTIFIED.remove(owner.getUUID());
        Vec3 center = target.position();
        ItemEntity icon = new ItemEntity(target.level(),
                center.x, center.y + 1.5D, center.z,
                new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE));
        icon.setNoGravity(true);
        icon.setPickUpDelay(32767);
        icon.setDeltaMovement(0.0D, 0.02D, 0.0D);
        target.level().addFreshEntity(icon);
        BARRIERS.add(new Barrier((ServerLevel) target.level(), center,
                now + DURATION, icon, owner.getUUID()));

        target.level().playSound(null, target.blockPosition(),
                SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS,
                1.0F, 1.0F);
        owner.displayClientMessage(Component.literal(
                "[后勤] 救援结界已释放！")
                .withStyle(ChatFormatting.GREEN), true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().overworld().getGameTime();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Long ready = BARRIER_CD.get(player.getUUID());
            if (ready != null && now >= ready
                    && BARRIER_READY_NOTIFIED.add(player.getUUID())) {
                player.displayClientMessage(Component.literal(
                        "[后勤] 救援结界充能完毕！")
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }
        Iterator<Barrier> it = BARRIERS.iterator();
        while (it.hasNext()) {
            Barrier b = it.next();
            if (now >= b.until || b.icon.isRemoved()) {
                b.icon.discard();
                it.remove();
                continue;
            }
            b.icon.setNoGravity(true);
            b.icon.setPos(b.center.x,
                    b.center.y + 1.5D + Math.sin(now * 0.12D) * 0.10D,
                    b.center.z);
            b.icon.setYRot((now * 3.0F) % 360.0F);
            b.icon.setXRot(0.0F);
            b.icon.setDeltaMovement(0.0D, 0.0D, 0.0D);

            if (event.getServer().getTickCount() % 20 != 0) continue;

            for (LivingEntity e : b.level.getEntitiesOfClass(
                    LivingEntity.class, new net.minecraft.world.phys.AABB(
                            b.center.x - 8, b.center.y - 4, b.center.z - 8,
                            b.center.x + 8, b.center.y + 4, b.center.z + 8))) {
                boolean friendly = e instanceof EntityMaid m
                        && b.owner.equals(m.getOwnerUUID())
                        || e instanceof ServerPlayer player
                        && b.owner.equals(player.getUUID());
                if (friendly) e.heal(2.0F);
            }
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2.0D * i / 24.0D;
                b.level.sendParticles(ParticleTypes.HEART,
                        b.center.x + Math.cos(angle) * 8.0D,
                        b.center.y + 0.2D,
                        b.center.z + Math.sin(angle) * 8.0D,
                        1, 0, 0.05D, 0, 0.0D);
            }
        }
    }

    public static int barrierCooldownSeconds(net.minecraft.world.entity.player.Player player) {
        if (player == null) return 0;
        Long ready = BARRIER_CD.get(player.getUUID());
        if (ready == null) return 0;
        long left = ready - player.level().getGameTime();
        return left <= 0 ? 0 : (int) ((left + 19) / 20);
    }
}
