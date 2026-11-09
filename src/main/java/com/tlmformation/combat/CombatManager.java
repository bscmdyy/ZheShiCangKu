package com.tlmformation.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.config.FormationConfig;
import com.tlmformation.formation.FormationSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The "third circle": while the owner is in combat, maids can see and keep
 * targets up to ExpandRange blocks from the owner.
 */
public final class CombatManager {
    private static final Map<UUID, Long> EXPAND_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> TELEPORT_GRACE = new HashMap<>();

    private CombatManager() {
    }

    public static void mark(LivingEntity owner) {
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        if (!FormationConfig.EXPAND_ENABLED.get()) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        EXPAND_UNTIL.put(player.getUUID(), now + FormationConfig.EXPAND_DURATION.get());
    }

    public static void clear(ServerPlayer player) {
        EXPAND_UNTIL.remove(player.getUUID());
        TELEPORT_GRACE.remove(player.getUUID());
    }

    public static boolean isActive(ServerPlayer player) {
        Long until = EXPAND_UNTIL.get(player.getUUID());
        return until != null && player.serverLevel().getGameTime() < until;
    }

    public static boolean isActive(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return false;
        }
        if (!owner.isAlive() || owner.level() != maid.level()) {
            return false;
        }
        return isActive(player);
    }

    public static int range(ServerPlayer player) {
        return FormationSettings.get(player.serverLevel()).getExpandRange(player);
    }

    public static int range(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        return owner instanceof ServerPlayer player ? range(player) : FormationConfig.EXPAND_RANGE.get();
    }

    public static boolean withinOwnerRange(EntityMaid maid, Entity target, double hysteresis) {
        LivingEntity owner = maid.getOwner();
        if (owner == null) {
            return false;
        }
        double radius = range(maid) + hysteresis;
        return owner.distanceToSqr(target) <= radius * radius;
    }

    public static boolean shouldKeepTarget(EntityMaid maid, LivingEntity target) {
        return (isActive(maid) || isTeleportGrace(maid))
                && (withinOwnerRange(maid, target, 0.0D) || isTeleportGrace(maid));
    }

    public static void markTeleport(LivingEntity owner) {
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        TELEPORT_GRACE.put(player.getUUID(), now + FormationConfig.TELEPORT_GRACE.get());
    }

    public static boolean isTeleportGrace(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return false;
        }
        Long until = TELEPORT_GRACE.get(player.getUUID());
        return until != null && player.serverLevel().getGameTime() < until;
    }

    /**
     * Drop targets that died, were removed, or escaped the expanded circle.
     * Run every 10 ticks from the server tick handler.
     */
    public static void reapTargets(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isActive(player)) {
                continue;
            }
            int range = range(player);
            for (EntityMaid maid : player.serverLevel().getEntitiesOfClass(
                    EntityMaid.class,
                    player.getBoundingBox().inflate(range + 16.0D),
                    maid -> player.getUUID().equals(maid.getOwnerUUID()))) {
                maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(target -> {
                    boolean dead = !target.isAlive() || target.isRemoved();
                    boolean escaped = !withinOwnerRange(maid, target, 4.0D)
                            && !isTeleportGrace(maid);
                    if (dead || escaped) {
                        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                        maid.getNavigation().stop();
                    }
                });
            }
        }
    }
}
