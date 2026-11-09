package com.tlmformation.item;

import com.tlmformation.ModItems;
import com.tlmformation.team.MaidTeamManager;
import com.tlmformation.voice.MaidVoiceManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EmergencyCallerItem extends Item {
    private static final long COOLDOWN_MS = 300_000L;
    private static final Map<UUID, Long> FALL_IMMUNE = new HashMap<>();

    public EmergencyCallerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            // 小队还在场：这次右键 = 叫直升机来把她们接走（索升撤离）
            if (MaidTeamManager.activeTeam(server) >= 0) {
                recallSquad(server);
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
            long readyAt = stack.hasTag()
                    ? stack.getTag().getLong("CallerReadyAt") : 0L;
            long left = readyAt - System.currentTimeMillis();
            if (left > 0L || server.getCooldowns().isOnCooldown(
                    ModItems.WALKIE_TALKIE.get())) {
                long seconds = Math.max(1L, (left + 999L) / 1000L);
                server.displayClientMessage(Component.literal(
                        "[呼救器] 充能中，还需等待 (" + seconds + "s)。")
                        .withStyle(ChatFormatting.GRAY), true);
            } else {
                server.displayClientMessage(Component.literal(
                        "[呼救器] 已就位，致命受击时自动触发。")
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * 回收小队：走直升机的 EXTRACT 流程 —— 沿 +Z 进场、悬停抛绳、女仆爬升登机、
     * 沿 +Z 飞离 120 格后送回据点。
     *
     * <p>地下 / 洞穴里信号不通：只提示、不召唤；如果直升机因其它原因起飞不了，
     * 回退到原来的瞬间传送撤离。</p>
     */
    private static void recallSquad(ServerPlayer server) {
        if (!com.tlmformation.vehicle.AirdropManager.hasSignal(server)) {
            // 洞穴里叫不动直升机：提示信号差，然后走旧的瞬间撤离兜底
            server.sendSystemMessage(Component.translatable(
                    "message.tlmformation.airdrop.signal_poor"));
            MaidTeamManager.returnTeamInstantly(server);
            return;
        }
        int team = MaidTeamManager.activeTeam(server);
        boolean started = false;
        try {
            started = com.tlmformation.vehicle.AirdropManager.tryStartExtraction(server, team);
        } catch (Throwable throwable) {
            com.tlmformation.TLMFormationMod.LOGGER.error(
                    "[Caller] tryStartExtraction failed", throwable);
            started = com.tlmformation.vehicle.AirdropManager.isActive(server);
        }
        if (started) {
            server.displayClientMessage(Component.translatable(
                    "message.tlmformation.caller.airlift")
                    .withStyle(ChatFormatting.AQUA), true);
        } else {
            MaidTeamManager.returnTeamInstantly(server);
        }
    }

    public static boolean tryTrigger(ServerPlayer player, boolean passive) {
        // 地下 / 洞穴：信号不通，呼救器也叫不来人（直接中止，不做任何传送）
        if (!com.tlmformation.vehicle.AirdropManager.hasSignal(player)) {
            player.sendSystemMessage(Component.translatable(
                    "message.tlmformation.airdrop.signal_poor"));
            return false;
        }
        if (!MaidTeamManager.activeMembers(player).isEmpty()) return false;
        if (player.getCooldowns().isOnCooldown(ModItems.WALKIE_TALKIE.get())) {
            return false;
        }

        ItemStack caller = findCaller(player);
        if (passive && caller.isEmpty()) return false;
        if (!caller.isEmpty()) {
            caller.getOrCreateTag().putLong("CallerReadyAt",
                    System.currentTimeMillis() + COOLDOWN_MS);
        }

        // 呼救器必须“瞬间到场”，跳过直升机空投（F9 部署才走 AirdropManager）
        MaidTeamManager.summonInstantly(player, 0);
        teleportTeamOne(player);
        knockbackEnemies(player);

        player.setHealth(Math.min(player.getMaxHealth(),
                player.getHealth() + player.getMaxHealth() * 0.5F));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 4));

        player.displayClientMessage(Component.literal(
                "[快反小队抵达战场]").withStyle(ChatFormatting.GREEN), true);
        MaidVoiceManager.emergencyCall(player);
        playCallSounds(player);

        player.getCooldowns().addCooldown(ModItems.WALKIE_TALKIE.get(), 6000);
        return true;
    }

    private static void teleportTeamOne(ServerPlayer player) {
        long immuneUntil = player.level().getGameTime() + 300L;
        boolean openSky = hasClearSky(player, 15);
        Vec3 front = player.position().add(
                player.getLookAngle().multiply(5.0D, 0.0D, 5.0D));
        int index = 0;

        for (UUID id : MaidTeamManager.activeMembers(player)) {
            double x;
            double y;
            double z;

            if (openSky) {
                x = front.x;
                y = player.getY() + 15.0D;
                z = front.z;
            } else {
                double angle = Math.PI * 2.0D * index / 6.0D;
                x = player.getX() + Math.cos(angle) * 2.5D;
                y = player.getY();
                z = player.getZ() + Math.sin(angle) * 2.5D;
            }
            index++;

            final double tx = x;
            final double ty = y;
            final double tz = z;
            final Vec3 impact = new Vec3(tx, ty, tz);
            final boolean delayed = openSky;

            player.serverLevel().getEntitiesOfClass(
                            com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class,
                            player.getBoundingBox().inflate(128))
                    .stream().filter(m -> m.getUUID().equals(id)).findFirst()
                    .ifPresent(maid -> {
                        maid.setPos(tx, ty, tz);
                        maid.setDeltaMovement(0, 0, 0);
                        maid.fallDistance = 0;
                        FALL_IMMUNE.put(maid.getUUID(), immuneUntil);
                        player.server.tell(new TickTask(delayed ? 35 : 2, () ->
                                impactBurst(player, impact)));
                    });
        }
    }

    private static void impactBurst(ServerPlayer player, Vec3 center) {
        Level level = player.serverLevel();
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.8F);
        for (int i = 0; i < 90; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            double r = level.random.nextDouble() * 7.0D;
            level.addParticle(ParticleTypes.EXPLOSION_EMITTER,
                    center.x + Math.cos(a) * r, center.y + 1.0D,
                    center.z + Math.sin(a) * r, 0, 0, 0);
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    center.x + Math.cos(a) * r, center.y + 0.5D,
                    center.z + Math.sin(a) * r, 0, 0.08D, 0);
            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    center.x + Math.cos(a) * r, center.y + 0.5D,
                    center.z + Math.sin(a) * r, 0, 0.04D, 0);
        }
        for (LivingEntity enemy : level.getEntitiesOfClass(
                LivingEntity.class, new net.minecraft.world.phys.AABB(
                        center.x - 20, center.y - 8, center.z - 20,
                        center.x + 20, center.y + 8, center.z + 20),
                e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive())) {
            Vec3 dir = enemy.position().subtract(center);
            if (dir.lengthSqr() < 0.01D) dir = new Vec3(1, 0, 0);
            dir = dir.normalize().scale(5.0D);
            enemy.setDeltaMovement(dir.x, 0.7D, dir.z);
            enemy.hurtMarked = true;
        }
    }

    private static boolean hasClearSky(ServerPlayer player, int height) {
        var level = player.level();
        Vec3 look = player.getLookAngle();
        double horiz = Math.sqrt(look.x * look.x + look.z * look.z);
        double dx = horiz < 0.01D ? 0.0D : look.x / horiz;
        double dz = horiz < 0.01D ? 1.0D : look.z / horiz;
        var front = player.blockPosition().offset(
                (int) Math.round(dx * 5.0D), 0, (int) Math.round(dz * 5.0D));
        for (int dy = 1; dy <= height; dy++) {
            if (!level.getBlockState(front.above(dy)).isAir()) return false;
        }
        return true;
    }

    public static boolean cancelsFall(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        Long until = FALL_IMMUNE.get(maid.getUUID());
        if (until == null) return false;
        if (maid.level().getGameTime() >= until) {
            FALL_IMMUNE.remove(maid.getUUID());
            return false;
        }
        maid.fallDistance = 0;
        return true;
    }

    private static void knockbackEnemies(ServerPlayer player) {
        for (LivingEntity enemy : player.level().getEntitiesOfClass(
                LivingEntity.class, player.getBoundingBox().inflate(20.0D),
                e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive())) {
            Vec3 dir = enemy.position().subtract(player.position());
            if (dir.lengthSqr() < 0.01D) dir = new Vec3(1, 0, 0);
            dir = dir.normalize().scale(4.0D);
            enemy.setDeltaMovement(dir.x, 0.65D, dir.z);
            enemy.hurtMarked = true;
        }
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.7F);
        for (int i = 0; i < 60; i++) {
            double angle = Math.random() * Math.PI * 2.0D;
            player.level().addParticle(ParticleTypes.EXPLOSION_EMITTER,
                    player.getX() + Math.cos(angle) * 2.0D,
                    player.getY() + 0.5D,
                    player.getZ() + Math.sin(angle) * 2.0D,
                    Math.cos(angle) * 0.8D, 0.5D, Math.sin(angle) * 0.8D);
        }
    }

    private static ItemStack findCaller(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.WALKIE_TALKIE.get())) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void playCallSounds(ServerPlayer player) {
        Level level = player.level();
        level.playSound(null, player.blockPosition(),
                SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
        level.playSound(null, player.blockPosition(),
                SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.server.tell(new TickTask(8, () ->
                level.playSound(null, player.blockPosition(),
                        SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.PLAYERS, 1.0F, 1.0F)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        long readyAt = stack.hasTag() ? stack.getTag().getLong("CallerReadyAt") : 0L;
        long left = readyAt - System.currentTimeMillis();
        if (left <= 0L) {
            tooltip.add(Component.translatable("item.tlmformation.caller.ready")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("item.tlmformation.caller.charging", left / 1000L + 1L)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
