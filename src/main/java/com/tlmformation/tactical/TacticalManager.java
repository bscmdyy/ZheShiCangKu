package com.tlmformation.tactical;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.config.FormationConfig;
import com.tlmformation.formation.FormationManager;
import com.tlmformation.chunk.MaidChunkTicketManager;
import com.tlmformation.formation.TargetSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative temporary tactical states.
 *
 * State is stored in maid persistent NBT (survives save/reload) and mirrored in
 * a server-side map for cheap ticking. It is deliberately NOT a custom TLM
 * Task: these commands must suppress normal formation and sometimes attack
 * behavior, while still leaving ordinary TLM tasks in place when the state
 * clears. A task would fight the existing brain priorities and be harder to
 * restore safely.
 */
public final class TacticalManager {
    private static final String TAG_STATE = "TLMTacticalState";
    private static final String TAG_TARGET = "TLMTacticalTarget";
    private static final String TAG_LOCK_TARGET = "TLMTacticalLockTarget";
    private static final String TAG_OWNER = "TLMTacticalOwner";
    private static final String TAG_DIMENSION = "TLMTacticalDimension";

    private static final Map<UUID, Data> ACTIVE = new HashMap<>();

    private TacticalManager() {
    }

    public static boolean hasState(EntityMaid maid) {
        Data data = ACTIVE.get(maid.getUUID());
        if (data != null) {
            return data.state != TacticalState.FOLLOW;
        }
        CompoundTag tag = maid.getPersistentData();
        return tag.contains(TAG_STATE)
                && TacticalState.byName(tag.getString(TAG_STATE)) != TacticalState.FOLLOW;
    }

    public static TacticalState getState(EntityMaid maid) {
        Data data = ACTIVE.get(maid.getUUID());
        if (data != null) {
            return data.state;
        }
        CompoundTag tag = maid.getPersistentData();
        return tag.contains(TAG_STATE)
                ? TacticalState.byName(tag.getString(TAG_STATE))
                : TacticalState.FOLLOW;
    }

    /**
     * 外部系统（索升集结）强制把女仆切到「定点」状态并走向目标点。
     */
    public static boolean forceStation(EntityMaid maid, ServerPlayer owner, BlockPos target) {
        if (maid == null || !maid.isAlive() || owner == null || target == null) {
            return false;
        }
        try {
            disableNativeFollow(maid);
            setState(maid, owner, TacticalState.STATION, target);
            forceClearNavigation(maid);
            maid.getNavigation().moveTo(target.getX() + 0.5D, target.getY(),
                    target.getZ() + 0.5D, 0.8D);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 外部系统（索降落地）强制把女仆切到「广域警戒」。
     *
     * <p>与 F8 轮盘的 GUARD 指令保持一致：禁用原生跟随、写入战术状态、清掉旧路径。</p>
     */
    public static boolean forceGuard(EntityMaid maid, ServerPlayer owner) {
        if (maid == null || !maid.isAlive() || owner == null) {
            return false;
        }
        try {
            disableNativeFollow(maid);
            setState(maid, owner, TacticalState.GUARD, null);
            forceClearNavigation(maid);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void restore(EntityMaid maid) {
        if (ACTIVE.containsKey(maid.getUUID())) {
            return;
        }
        CompoundTag tag = maid.getPersistentData();
        if (!tag.contains(TAG_STATE)) {
            return;
        }
        TacticalState state = TacticalState.byName(tag.getString(TAG_STATE));
        if (state == TacticalState.FOLLOW) {
            return;
        }
        UUID owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : maid.getOwnerUUID();
        if (owner == null) {
            clear(maid);
            return;
        }
        BlockPos target = tag.contains(TAG_TARGET) ? BlockPos.of(tag.getLong(TAG_TARGET)) : null;
        UUID lockTarget = tag.hasUUID(TAG_LOCK_TARGET) ? tag.getUUID(TAG_LOCK_TARGET) : null;
        ACTIVE.put(maid.getUUID(), new Data(owner, maid.level().dimension(), state, target, lockTarget));
    }

    public static void handle(ServerPlayer player, TacticalCommand command,
                              @Nullable BlockPos requestedTarget) {
        FormationManager.reconcileAll(player.server);
        List<EntityMaid> maids = com.tlmformation.team.GrandAssembleManager
                .isActive(player) ? allTeamMaids(player) : nearbyOwnedMaids(player);
        if (maids.isEmpty()) {
            return;
        }

        BlockPos mark = command == TacticalCommand.MARK
                ? resolveTarget(player, requestedTarget)
                : null;
        LivingEntity lockTarget = command == TacticalCommand.LOCK
                ? findLockTarget(player)
                : null;
        if (command == TacticalCommand.LOCK && lockTarget == null) {
            return;
        }

        boolean wasGrand = com.tlmformation.team.GrandAssembleManager.isActive(player);
        if (command == TacticalCommand.RETREAT) {
            com.tlmformation.team.GrandAssembleManager.set(player, false);
        }

        for (int mi = 0; mi < maids.size(); mi++) {
            EntityMaid maid = maids.get(mi);
            switch (command) {
                case FOLLOW -> {
                    enableNativeFollow(maid);
                    if (com.tlmformation.team.GrandAssembleManager.isActive(player)) {
                        Data data = setState(maid, player, TacticalState.GUARD, null);
                        forceClearNavigation(maid);
                        data.slotIndex = mi;
                        data.slotCount = maids.size();
                        data.marchSpeed = 1.0D;
                        Vec3 target = sectorTarget(maid, player, mi, maids.size());
                        data.walkTarget = target != null ? target
                                : marchPrediction(maid, player);
                        maid.getNavigation().moveTo(data.walkTarget.x,
                                data.walkTarget.y, data.walkTarget.z, 1.0D);
                    } else {
                        clear(maid);
                        FormationManager.engage(maid);
                    }
                }
                case RETREAT -> {
                    Vec3 retreatGoal = wasGrand
                            ? randomScatter(maid, player.position(), player.getY(), 32.0D)
                            : player.position();
                    Data data = setState(maid, player, TacticalState.RETREAT,
                            BlockPos.containing(retreatGoal));
                    clearAttackTarget(maid);
                    // Highest-priority interrupt: wipe the old marker path,
                    // Task and look target before issuing the retreat path.
                    forceClearNavigation(maid);
                    maid.setTarget(null);
                    data.walkTarget = retreatGoal;
                    maid.getNavigation().moveTo(retreatGoal.x, retreatGoal.y,
                            retreatGoal.z, 0.5D);
                }
                case MARK -> {
                    if (mark == null) {
                        return;
                    }
                    boolean grand = com.tlmformation.team.GrandAssembleManager.isActive(player);
                    Vec3 goal = grand
                            ? randomScatter(maid, Vec3.atCenterOf(mark), player.getY(), 32.0D)
                            : Vec3.atCenterOf(mark);
                    Data data = setState(maid, player, TacticalState.STATION,
                            BlockPos.containing(goal));
                    clearAttackTarget(maid);
                    forceClearNavigation(maid);
                    moveToIfNeeded(maid, data, goal,
                            grand ? 0.55D : FormationConfig.TACTICAL_MOVE_SPEED.get(), 2.0D);
                }
                case LOCK -> {
                    Data data = setState(maid, player, TacticalState.LOCK, null,
                            lockTarget.getUUID());
                    // Kill any current non-combat path/task, then force the
                    // target down both the entity and brain memories.
                    forceClearNavigation(maid);
                    maid.setTarget(lockTarget);
                    maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, lockTarget);
                }
                case MARCH -> {
                    Data data = setState(maid, player, TacticalState.GUARD, null);
                    forceClearNavigation(maid);
                    clearAttackTarget(maid);
                    Vec3 scatter = sectorTarget(maid, player, mi, maids.size());
                    if (scatter == null) {
                        scatter = marchPrediction(maid, player);
                    }
                    data.walkTarget = scatter;
                    data.marchSpeed = 1.0D;
                    maid.getNavigation().moveTo(scatter.x, scatter.y,
                            scatter.z, 1.0D);
                }
                case GUARD -> {
                    disableNativeFollow(maid);
                    setState(maid, player, TacticalState.GUARD, null);
                    forceClearNavigation(maid);
                    LivingEntity current = maid.getTarget();
                    if (current != null
                            && current.distanceToSqr(player)
                            > (double) FormationConfig.TACTICAL_RADIUS.get()
                            * FormationConfig.TACTICAL_RADIUS.get()) {
                        clearAttackTarget(maid);
                    }
                }
                case HOLD -> {
                    Data data = setState(maid, player, TacticalState.HOLD, maid.blockPosition());
                    clearAttackTarget(maid);
                    forceClearNavigation(maid);
                    stopNavigation(maid, data);
                }
            }
        }
        com.tlmformation.voice.MaidVoiceManager.onCommand(player, command);
        sync(player);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0 || ACTIVE.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityMaid maid) {
                    Data data = ACTIVE.get(maid.getUUID());
                    if (data != null) {
                        tickMaid(server, maid, data);
                    }
                }
            }
        }
    }

    private static void tickMaid(MinecraftServer server, EntityMaid maid, Data data) {
        if (!maid.isAlive()) {
            clear(maid);
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(data.owner);
        if (player == null || player.level().dimension() != data.dimension) {
            clear(maid);
            return;
        }

        double ownerDistance = maid.distanceTo(player);
        switch (data.state) {
            case RETREAT -> {
                clearAttackTarget(maid);
                Vec3 retreatGoal = data.target != null
                        ? Vec3.atCenterOf(data.target) : player.position();
                if (maid.position().distanceToSqr(retreatGoal) <= 9.0D) {
                    stopNavigation(maid, data);
                    lookAt(maid, player.position());
                    if (maid.position().distanceToSqr(player.position()) <= 256.0D) {
                        // Stay at the random retreat point and face the owner.
                        return;
                    }
                }
                if (ownerDistance > 64.0D) {
                    clear(maid);
                    FormationManager.engage(maid);
                    sync(player);
                    return;
                }
                moveToIfNeeded(maid, data, retreatGoal, 0.5D, 2.0D);
            }
            case STATION -> {
                clearAttackTarget(maid);
                if (data.target == null) {
                    clear(maid);
                    FormationManager.engage(maid);
                    sync(player);
                    return;
                }
                if (ownerDistance > FormationConfig.TACTICAL_MARKER_RECALL_DISTANCE.get()) {
                    clear(maid);
                    FormationManager.engage(maid);
                    sync(player);
                    return;
                }
                Vec3 station = Vec3.atCenterOf(data.target);
                if (maid.position().distanceToSqr(station) > 4.0D) {
                    moveToIfNeeded(maid, data, station, FormationConfig.TACTICAL_MOVE_SPEED.get(), 2.0D);
                } else if (server.getTickCount() >= data.nextPatrol) {
                    data.nextPatrol = server.getTickCount()
                            + FormationConfig.TACTICAL_GUARD_PATROL_INTERVAL.get();
                    double angle = maid.getRandom().nextDouble() * Math.PI * 2.0D;
                    double radius = 3.0D + maid.getRandom().nextDouble() * 2.0D;
                    Vec3 candidate = station.add(Math.cos(angle) * radius,
                            0.0D, Math.sin(angle) * radius);
                    Vec3 safe = safeGround(maid, candidate, station.y);
                    if (safe != null) {
                        data.walkTarget = safe;
                        maid.getNavigation().moveTo(safe.x, safe.y, safe.z, 0.5D);
                    } else {
                        stopNavigation(maid, data);
                        lookAt(maid, station);
                    }
                } else if (data.walkTarget != null
                        && maid.position().distanceToSqr(data.walkTarget) > 2.25D) {
                    moveToIfNeeded(maid, data, data.walkTarget, 0.5D, 1.5D);
                } else {
                    stopNavigation(maid, data);
                    lookAt(maid, station);
                }
            }
            case LOCK -> {
                if (data.lockTarget == null) {
                    clear(maid);
                    FormationManager.engage(maid);
                    sync(player);
                    return;
                }
                Entity locked = getEntityByUuid(maid, data.lockTarget);
                if (!(locked instanceof LivingEntity living)
                        || !living.isAlive() || living.level() != maid.level()
                        || living.distanceToSqr(maid) > 128.0D * 128.0D) {
                    clear(maid);
                    FormationManager.engage(maid);
                    sync(player);
                    return;
                }
                maid.setTarget(living);
                maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, living);
            }
            case HOLD -> {
                clearAttackTarget(maid);
                stopNavigation(maid, data);
                if (ownerDistance <= 4.0D) {
                    clear(maid);
                    FormationManager.engage(maid);
                    sync(player);
                }
            }
            case GUARD -> tickGuard(server, maid, player, data);
            default -> {
            }
        }
    }

    private static void tickMarch(EntityMaid maid, ServerPlayer player, Data data) {
        boolean refresh = data.lastOwner == null
                || data.lastOwner.distanceToSqr(player.position()) > 9.0D
                || Math.abs(wrapYaw(player.getYRot() - data.lastYaw)) > 45.0F;
        if (refresh) {
            Vec3 target = sectorTarget(maid, player,
                    data.slotIndex, data.slotCount);
            data.walkTarget = target != null ? target
                    : marchPrediction(maid, player);
            data.lastOwner = player.position();
            data.lastYaw = player.getYRot();
        }

        double playerSpeed = Math.sqrt(
                player.getDeltaMovement().x * player.getDeltaMovement().x
                + player.getDeltaMovement().z * player.getDeltaMovement().z);
        if (playerSpeed < 0.02D) data.stillTicks++;
        else data.stillTicks = 0;

        if (data.stillTicks > 40 && data.slotCount > 0) {
            double angle = Math.PI * 2.0D * data.slotIndex / data.slotCount;
            double radius = 3.0D + Math.random() * 2.0D;
            Vec3 ring = player.position().add(
                    Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            Vec3 safe = safeGround(maid, ring, player.getY());
            if (safe != null) data.walkTarget = safe;
        }

        Vec3 target = data.walkTarget;
        double distance = maid.position().distanceTo(target);
        maid.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);

        double baseSpeed = player.isSprinting() ? 0.8D : 0.6D;
        double wanted = baseSpeed + Math.min(0.5D, distance * 0.02D);
        data.marchSpeed = Math.min(1.3D,
                Math.min(wanted, data.marchSpeed + 0.05D));
        data.marchSpeed = Math.max(baseSpeed,
                data.marchSpeed - 0.05D);

        if (distance < 2.0D) {
            maid.getNavigation().stop();
            data.stuckTicks = 0;
            return;
        }

        if (data.lastPos != null && data.lastPos.distanceToSqr(maid.position()) < 0.01D) {
            data.stuckTicks++;
        } else {
            data.stuckTicks = 0;
            data.lastPos = maid.position();
        }
        if (data.stuckTicks > 20
                // 空投作业中的女仆不做“卡住自救传送”：她们的位置由索降状态机全权接管
                && !com.tlmformation.vehicle.AirdropManager.isAirdropManaged(maid)) {
            Vec3 safe = safeGround(maid, target, player.getY());
            if (safe != null) {
                com.tlmformation.vehicle.VehicleMaidRelease.release(maid);
                maid.teleportTo((ServerLevel) maid.level(), safe.x, safe.y,
                        safe.z, java.util.Set.of(), player.getYRot(), 0.0F);
                data.stuckTicks = 0;
            }
        } else {
            maid.getNavigation().moveTo(target.x, target.y, target.z,
                    data.marchSpeed);
        }
    }

    private static float wrapYaw(float value) {
        value = value % 360.0F;
        if (value > 180.0F) value -= 360.0F;
        if (value < -180.0F) value += 360.0F;
        return value;
    }

    private static void tickGuard(MinecraftServer server, EntityMaid maid,
                                  ServerPlayer player, Data data) {
        if (com.tlmformation.team.GrandAssembleManager.isActive(player)
                && data.walkTarget != null) {
            tickMarch(maid, player, data);
            return;
        }
        LivingEntity target = maid.getTarget();
        boolean grand = com.tlmformation.team.GrandAssembleManager.isActive(player);
        double radius = FormationConfig.TACTICAL_RADIUS.get() * (grand ? 3.0D : 1.0D);
        if (target == null) {
            double search = FormationConfig.TACTICAL_LOCK_RANGE.get() * 3.0D;
            target = maid.level().getEntitiesOfClass(LivingEntity.class,
                            maid.getBoundingBox().inflate(search),
                            e -> e != player && e.isAlive()
                                    && (e instanceof Enemy || e instanceof NeutralMob))
                    .stream().min(java.util.Comparator.comparingDouble(maid::distanceToSqr))
                    .orElse(null);
            if (target != null) {
                maid.setTarget(target);
                maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
            }
        }
        if (target != null) {
            if (target.distanceToSqr(player) > radius * radius) {
                clearAttackTarget(maid);
            } else {
                // Keep the vanilla combat AI and its current path.
                return;
            }
        }

        long now = server.getTickCount();
        if (now < data.nextPatrol) {
            return;
        }
        data.nextPatrol = now + FormationConfig.TACTICAL_GUARD_PATROL_INTERVAL.get();

        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = maid.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = 5.0D + maid.getRandom().nextDouble() * 5.0D;
            Vec3 candidate = maid.position().add(
                    Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
            Vec3 safe = safeGround(maid, candidate, player.getY());
            if (safe != null) {
                double speed = FormationConfig.TACTICAL_GUARD_SPEED.get()
                        * (grand ? 1.0D : 1.0D);
                moveToIfNeeded(maid, data, safe, speed, 1.5D);
                return;
            }
        }
    }

    private static List<EntityMaid> allTeamMaids(ServerPlayer player) {
        List<EntityMaid> result = new ArrayList<>();
        // Double loop: all five teams, then every member UUID.
        for (UUID id : com.tlmformation.team.MaidTeamManager.allMembers(player)) {
            for (ServerLevel level : player.server.getAllLevels()) {
                if (level.getEntity(id) instanceof EntityMaid maid
                        && maid.isAlive()) {
                    result.add(maid);
                    break;
                }
            }
        }
        return result;
    }

    private static List<EntityMaid> nearbyOwnedMaids(ServerPlayer player) {
        double radius = FormationConfig.TACTICAL_RADIUS.get();
        List<EntityMaid> result = new ArrayList<>();
        for (EntityMaid maid : player.serverLevel().getEntitiesOfClass(
                EntityMaid.class,
                player.getBoundingBox().inflate(radius),
                maid -> maid.isOwnedBy(player) && maid.isAlive()
                        && com.tlmformation.team.MaidTeamManager
                        .isActiveTeamMember(player, maid))) {
            result.add(maid);
        }
        return result;
    }

    @Nullable
    private static Entity getEntityByUuid(EntityMaid maid, UUID uuid) {
        return maid.level() instanceof ServerLevel level ? level.getEntity(uuid) : null;
    }

    @Nullable
    private static LivingEntity findLockTarget(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = FormationConfig.TACTICAL_LOCK_RANGE.get();
        Vec3 end = eye.add(look.scale(range));
        AABB search = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);

        EntityHitResult direct = ProjectileUtil.getEntityHitResult(
                level, player, eye, end, search,
                entity -> isValidLockTarget(entity, player),
                (float) (range * range));
        if (direct != null && direct.getEntity() instanceof LivingEntity living) {
            return living;
        }

        // Cone snap: nearest living entity within 5 blocks of the crosshair
        // ray, up to 96 blocks forward.
        double cone = Math.max(9.0D, FormationConfig.TACTICAL_LOCK_CONE.get());
        double coneSqr = cone * cone;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        AABB area = player.getBoundingBox().inflate(range);
        for (LivingEntity candidate : level.getEntitiesOfClass(
                LivingEntity.class, area, entity -> isValidLockTarget(entity, player))) {
            Vec3 center = candidate.getBoundingBox().getCenter();
            double along = center.subtract(eye).dot(look);
            if (along < 0.0D || along > range) {
                continue;
            }
            Vec3 closest = eye.add(look.scale(along));
            double perp = center.distanceToSqr(closest);
            if (perp > coneSqr) {
                continue;
            }
            double score = perp + along * 0.001D;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isValidLockTarget(Entity entity, ServerPlayer player) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living == player) {
            return false;
        }
        if (living instanceof Player || living instanceof EntityMaid) {
            return false;
        }
        return true;
    }

    private static BlockPos resolveTarget(ServerPlayer player, @Nullable BlockPos requested) {
        BlockPos ray = rayTraceGround(player);
        if (ray != null) {
            return ray;
        }
        if (requested != null && isGroundTarget(player.serverLevel(), requested)
                && requested.distSqr(player.blockPosition()) <= 64.0D * 64.0D) {
            return requested;
        }
        return null;
    }

    /**
     * Cast from the player's eyes along the real view vector. If the ray hits
     * nothing (sky), drop vertical columns in front of the player until a
     * solid floor with air above is found.
     */
    @Nullable
    private static BlockPos rayTraceGround(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(96.0D));
        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos ground = groundBelow(level, hit.getBlockPos(), 16);
            if (ground != null) {
                return ground;
            }
        }

        BlockPos endGround = groundBelow(level, BlockPos.containing(end), 128);
        if (endGround != null) {
            return endGround;
        }
        for (double distance = 4.0D; distance <= 96.0D; distance += 4.0D) {
            BlockPos column = BlockPos.containing(eye.add(look.scale(distance)));
            BlockPos ground = groundBelow(level, column, 128);
            if (ground != null) {
                return ground;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos groundBelow(ServerLevel level, BlockPos start, int maxDown) {
        for (int down = 0; down <= maxDown; down++) {
            BlockPos floor = start.below(down);
            if (!level.isLoaded(floor)) {
                continue;
            }
            if (!level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                    && level.getBlockState(floor.above()).getCollisionShape(
                    level, floor.above()).isEmpty()) {
                return floor.above();
            }
        }
        return null;
    }

    private static boolean isGroundTarget(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.below())) {
            return false;
        }
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(
                level, pos.below()).isEmpty();
    }

    private static Data setState(EntityMaid maid, ServerPlayer player,
                                 TacticalState state, @Nullable BlockPos target) {
        return setState(maid, player, state, target, null);
    }

    private static Data setState(EntityMaid maid, ServerPlayer player,
                                 TacticalState state, @Nullable BlockPos target,
                                 @Nullable UUID lockTarget) {
        FormationManager.setFrontBoost(maid, false);
        FormationManager.setRearBoost(maid, false);
        Data data = new Data(player.getUUID(), maid.level().dimension(), state, target, lockTarget);
        ACTIVE.put(maid.getUUID(), data);

        CompoundTag tag = maid.getPersistentData();
        tag.putString(TAG_STATE, state.name());
        tag.putUUID(TAG_OWNER, player.getUUID());
        tag.putString(TAG_DIMENSION, maid.level().dimension().location().toString());
        if (target != null) {
            tag.putLong(TAG_TARGET, target.asLong());
        } else {
            tag.remove(TAG_TARGET);
        }
        if (state == TacticalState.HOLD) {
            disableNativeFollow(maid);
        } else {
            enableNativeFollow(maid);
        }

        if (lockTarget != null) {
            tag.putUUID(TAG_LOCK_TARGET, lockTarget);
        } else {
            tag.remove(TAG_LOCK_TARGET);
        }
        if (state == TacticalState.STATION || state == TacticalState.HOLD
                || state == TacticalState.GUARD) {
            MaidChunkTicketManager.activate(maid);
        } else if (maid.level() instanceof ServerLevel level) {
            MaidChunkTicketManager.release(level, maid.getUUID());
        }
        return data;
    }

    public static void clear(EntityMaid maid) {
        if (maid.level() instanceof ServerLevel level) {
            MaidChunkTicketManager.release(level, maid.getUUID());
        }
        ACTIVE.remove(maid.getUUID());
        CompoundTag tag = maid.getPersistentData();
        tag.remove(TAG_STATE);
        tag.remove(TAG_TARGET);
        tag.remove(TAG_OWNER);
        tag.remove(TAG_DIMENSION);
        FormationManager.setFrontBoost(maid, false);
        FormationManager.setRearBoost(maid, false);
    }

    private static void clearAttackTarget(EntityMaid maid) {
        maid.setTarget(null);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }

    /**
     * Temporary tactical movement only. It never touches formation paths and
     * only commits a new navigation target after the destination moved beyond the
     * tactical threshold, preventing per-tick path resets.
     */
    private static void moveToIfNeeded(EntityMaid maid, Data data, Vec3 target,
                                       double speed, double stopDistance) {
        if (maid.position().distanceToSqr(target) <= stopDistance * stopDistance) {
            stopNavigation(maid, data);
            return;
        }

        double threshold = FormationConfig.TACTICAL_TARGET_CHANGE_THRESHOLD.get();
        boolean navigating = !maid.getNavigation().isDone();
        if (navigating && data.walkTarget != null
                && data.walkTarget.distanceToSqr(target) <= threshold * threshold) {
            return;
        }

        double capped = Math.min(Math.max(speed, 0.2D),
                FormationConfig.TACTICAL_SPEED_CAP.get());
        maid.getNavigation().moveTo(target.x, target.y, target.z, capped);
        data.walkTarget = target;
    }

    private static void lookAt(EntityMaid maid, Vec3 target) {
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
                new net.minecraft.world.entity.ai.behavior.BlockPosTracker(BlockPos.containing(target)));
    }

    /**
     * Hard interrupt used only by temporary tactical commands. It stops any
     * marker/guard path and resets the TLM task so the new command owns the
     * maid immediately. Normal formation following never calls this.
     */
    private static Vec3 sectorTarget(EntityMaid maid, ServerPlayer player,
                                     int index, int count) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z).normalize();
        Vec3 right = new Vec3(-flat.z, 0.0D, flat.x);

        int frontCount = Math.min(4, count);
        int remaining = Math.max(0, count - frontCount);
        int leftCount = Math.min(5, remaining / 2);
        int rightCount = Math.min(5, remaining - leftCount);
        double angleOffset;
        double distance;
        if (index < frontCount) {
            angleOffset = (Math.random() - 0.5D) * Math.toRadians(35.0D);
            distance = 10.0D + Math.random() * 5.0D;
        } else if (index < frontCount + leftCount) {
            angleOffset = Math.PI / 2.0D
                    + (Math.random() - 0.5D) * 0.5D;
            distance = 5.0D + Math.random() * 10.0D;
        } else if (index < frontCount + leftCount + rightCount) {
            angleOffset = -Math.PI / 2.0D
                    + (Math.random() - 0.5D) * 0.5D;
            distance = 5.0D + Math.random() * 10.0D;
        } else {
            angleOffset = Math.PI + (Math.random() - 0.5D) * 1.4D;
            distance = 5.0D + Math.random() * 15.0D;
        }

        Vec3 dir = flat.scale(Math.cos(angleOffset))
                .add(right.scale(Math.sin(angleOffset)));
        Vec3 candidate = player.position().add(dir.scale(distance));
        return safeGround(maid, candidate, player.getY());
    }

    private static Vec3 randomScatter(EntityMaid maid, Vec3 center,
                                      double referenceY, double maxRadius) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = Math.random() * Math.PI * 2.0D;
            double radius = 5.0D + Math.random() * Math.max(1.0D, maxRadius - 5.0D);
            Vec3 candidate = center.add(Math.cos(angle) * radius, 0.0D,
                    Math.sin(angle) * radius);
            Vec3 safe = safeGround(maid, candidate, referenceY);
            if (safe != null) return safe;
        }
        // Cliff/canyon fallback: contract toward the anchor until stable.
        for (double radius = 4.0D; radius >= 1.0D; radius -= 1.0D) {
            double angle = Math.random() * Math.PI * 2.0D;
            Vec3 candidate = center.add(Math.cos(angle) * radius, 0.0D,
                    Math.sin(angle) * radius);
            Vec3 safe = safeGround(maid, candidate, referenceY);
            if (safe != null) return safe;
        }
        return null;
    }

    /**
     * Ray-casts down to the actual floor. Rejects drops >3 blocks and floating
     * targets, so a maid is never sent off a cliff.
     */
    @Nullable
    private static Vec3 safeGround(EntityMaid maid, Vec3 candidate, double referenceY) {
        ServerLevel level = (ServerLevel) maid.level();
        Vec3 start = candidate.add(0.0D, 16.0D, 0.0D);
        Vec3 end = candidate.subtract(0.0D, 40.0D, 0.0D);
        BlockHitResult hit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maid));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        Vec3 point = hit.getLocation();
        if (point.y < referenceY - 3.0D) return null;

        BlockPos feet = BlockPos.containing(point.x, point.y + 0.01D, point.z);
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(feet.above())
                .getCollisionShape(level, feet.above()).isEmpty()) {
            return null;
        }
        Vec3 safe = TargetSafety.findSafe(level, maid, point);
        return safe != null ? safe : new Vec3(point.x, point.y, point.z);
    }

    private static Vec3 marchPrediction(EntityMaid maid, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) flat = new Vec3(0.0D, 0.0D, 1.0D);
        flat = flat.normalize();

        double distance = 15.0D + Math.random() * 5.0D;
        Vec3 raw = player.position().add(flat.scale(distance));
        Vec3 grounded = safeGround(maid, raw, player.getY());
        if (grounded != null) return grounded;

        // Cliff protection: never aim below the player.
        return new Vec3(raw.x, player.getY(), raw.z);
    }

    private static double synchronizedMarchSpeed(ServerPlayer player) {
        double speed = Math.sqrt(player.getDeltaMovement().x
                * player.getDeltaMovement().x
                + player.getDeltaMovement().z
                * player.getDeltaMovement().z);
        // Walk 0.6, sprint ~1.0, smooth between without overshoot.
        return Math.min(1.0D, 0.6D + speed * 2.0D);
    }

    private static void enableNativeFollow(EntityMaid maid) {
        maid.restrictTo(BlockPos.ZERO, MaidConfig.MAID_NON_HOME_RANGE.get());
        maid.setHomeModeEnable(false);
    }

    private static void disableNativeFollow(EntityMaid maid) {
        maid.getSchedulePos().setHomeModeEnable(maid, maid.blockPosition());
        maid.setHomeModeEnable(true);
    }

    private static void forceClearNavigation(EntityMaid maid) {
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private static void stopNavigation(EntityMaid maid, Data data) {
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        data.walkTarget = null;
    }

    public static void sync(ServerPlayer player) {
        Map<Integer, Integer> states = new HashMap<>();
        BlockPos marker = null;
        int lockedTargetEntityId = -1;
        double radius = 128.0D;
        for (EntityMaid maid : player.serverLevel().getEntitiesOfClass(
                EntityMaid.class, player.getBoundingBox().inflate(radius),
                maid -> maid.isOwnedBy(player))) {
            Data data = ACTIVE.get(maid.getUUID());
            if (data != null && data.state != TacticalState.FOLLOW) {
                states.put(maid.getId(), data.state.ordinal());
                if (data.state == TacticalState.STATION && data.target != null) {
                    marker = data.target;
                }
                if (data.state == TacticalState.LOCK && data.lockTarget != null) {
                    Entity locked = getEntityByUuid(maid, data.lockTarget);
                    if (locked != null && locked.isAlive()) {
                        lockedTargetEntityId = locked.getId();
                    }
                }
            }
        }
        int commandOrdinal = TacticalCommand.FOLLOW.ordinal();
        if (states.containsValue(TacticalState.LOCK.ordinal())) {
            commandOrdinal = TacticalCommand.LOCK.ordinal();
        } else if (states.containsValue(TacticalState.STATION.ordinal())) {
            commandOrdinal = TacticalCommand.MARK.ordinal();
        } else if (states.containsValue(TacticalState.GUARD.ordinal())) {
            commandOrdinal = TacticalCommand.GUARD.ordinal();
        } else if (states.containsValue(TacticalState.RETREAT.ordinal())) {
            commandOrdinal = TacticalCommand.RETREAT.ordinal();
        } else if (states.containsValue(TacticalState.HOLD.ordinal())) {
            commandOrdinal = TacticalCommand.HOLD.ordinal();
        }
        com.tlmformation.network.NetworkHandler.sendTacticalState(
                player, states, marker, lockedTargetEntityId, commandOrdinal);
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    private static final class Data {
        private final UUID owner;
        private final ResourceKey<Level> dimension;
        private final TacticalState state;
        @Nullable
        private final BlockPos target;
        @Nullable
        private final UUID lockTarget;
        @Nullable
        private Vec3 walkTarget;
        private long nextPatrol;
        private Vec3 lastOwner;
        private Vec3 lastPos;
        private float lastYaw;
        private int stuckTicks;
        private double marchSpeed = 1.0D;
        private int slotIndex;
        private int slotCount;
        private int stillTicks;

        private Data(UUID owner, ResourceKey<Level> dimension,
                     TacticalState state, @Nullable BlockPos target,
                     @Nullable UUID lockTarget) {
            this.owner = owner;
            this.dimension = dimension;
            this.state = state;
            this.target = target;
            this.lockTarget = lockTarget;
        }
    }
}
