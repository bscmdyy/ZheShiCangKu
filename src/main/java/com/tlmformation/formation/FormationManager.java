package com.tlmformation.formation;

import com.tlmformation.vehicle.VehicleMaidRelease;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.config.FormationConfig;
import com.tlmformation.formation.FormationLayout.SlotOffset;
import com.tlmformation.tactical.TacticalManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side roster, follow activation and target manager.
 *
 * FollowRange only decides whether a maid enters or leaves forced following.
 * Teleporting is handled separately by EmergencyTeleportHandler.
 */
public final class FormationManager {
    private static final String SLOT_TAG = "TLMFSlot";
    private static final int SCAN_RADIUS = 96;
    private static final UUID FRONT_BOOST_ID =
            UUID.fromString("8f6b2f46-3a4d-4f8e-9a0d-2f4c6b8d1e73");
    private static final UUID REAR_BOOST_ID =
            UUID.fromString("c7d2e9a1-5b4f-4c3e-8a6d-1f2b3c4d5e6f");

    private static final Map<UUID, OwnerFormation> FORMATIONS = new HashMap<>();
    private static final Map<UUID, Long> FRONT_FALLBACK_UNTIL = new HashMap<>();
    private static final Map<UUID, Vec3> FRONT_LAST_SAFE = new HashMap<>();

    private FormationManager() {
    }

    public enum FollowMode {
        FORMATION,
        DISPERSE
    }

    public static void reconcileAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            reconcile(player);
        }
    }

    public static void tickPlayer(ServerPlayer player) {
        if (com.tlmformation.team.GrandAssembleManager.isActive(player)) {
            return; // grand commands exclusively own movement
        }
        OwnerFormation formation = FORMATIONS.get(player.getUUID());
        if (formation == null) {
            return;
        }
        if (formation.dimension != player.level().dimension()) {
            formation.dimension = player.level().dimension();
            formation.slotByMaid.clear();
            formation.engaged.clear();
            formation.stillTicks = 0;
            formation.lastX = player.getX();
            formation.lastZ = player.getZ();
            formation.settleUntil = player.level().getGameTime() + 40;
            return;
        }

        double dx = player.getX() - formation.lastX;
        double dz = player.getZ() - formation.lastZ;
        formation.lastX = player.getX();
        formation.lastZ = player.getZ();
        formation.motionX = dx;
        formation.motionZ = dz;

        double stillSpeed = FormationConfig.STILL_SPEED.get();
        double horizontalSpeedSq = dx * dx + dz * dz;
        if (horizontalSpeedSq < stillSpeed * stillSpeed) {
            formation.stillTicks++;
        } else {
            formation.stillTicks = 0;
        }
    }

    private static void reconcile(ServerPlayer player) {
        if (com.tlmformation.team.GrandAssembleManager.isActive(player)) {
            return;
        }
        if (!FormationConfig.ENABLED.get()
                || !FormationSettings.get(player.server).isFormationEnabled(player)) {
            FORMATIONS.remove(player.getUUID());
            return;
        }

        ServerLevel level = player.serverLevel();
        List<EntityMaid> roster = level.getEntitiesOfClass(EntityMaid.class,
                player.getBoundingBox().inflate(SCAN_RADIUS),
                maid -> isFollower(maid, player));
        if (roster.isEmpty()) {
            FORMATIONS.remove(player.getUUID());
            return;
        }

        int limit = Math.min(roster.size(), FormationConfig.MAX_MAIDS.get());
        long now = level.getGameTime();
        OwnerFormation formation = FORMATIONS.computeIfAbsent(player.getUUID(), id -> {
            OwnerFormation created = new OwnerFormation(id);
            created.dimension = level.dimension();
            created.lastX = player.getX();
            created.lastZ = player.getZ();
            created.settleUntil = now + 40;
            return created;
        });

        if (formation.dimension != level.dimension()) {
            formation.dimension = level.dimension();
            formation.slotByMaid.clear();
            formation.engaged.clear();
            formation.stillTicks = 0;
            formation.lastX = player.getX();
            formation.lastZ = player.getZ();
            formation.settleUntil = now + 40;
        }

        roster.sort(Comparator.comparing(maid -> maid.getUUID().toString()));
        Map<UUID, Integer> newSlots = new HashMap<>();
        Set<Integer> used = new HashSet<>();
        List<EntityMaid> pending = new ArrayList<>();

        for (EntityMaid maid : roster) {
            int saved = getSavedSlot(maid);
            if (saved < 0) {
                Integer old = formation.slotByMaid.get(maid.getUUID());
                if (old != null) {
                    saved = old;
                }
            }
            if (saved >= 0 && saved < limit && used.add(saved)) {
                newSlots.put(maid.getUUID(), saved);
            } else {
                pending.add(maid);
            }
        }

        for (EntityMaid maid : pending) {
            for (int slot = 0; slot < limit; slot++) {
                if (used.add(slot)) {
                    newSlots.put(maid.getUUID(), slot);
                    setSavedSlot(maid, slot);
                    break;
                }
            }
        }

        boolean changed = formation.rosterSize != limit
                || !formation.slotByMaid.equals(newSlots);
        formation.slotByMaid.clear();
        formation.slotByMaid.putAll(newSlots);
        formation.rosterSize = limit;
        formation.engaged.keySet().retainAll(newSlots.keySet());
        if (changed) {
            formation.settleUntil = now + 40;
        }

        if (FormationConfig.DEBUG.get()) {
            TLMFormationMod.LOGGER.info("[TLMF] roster owner={} size={} slots={} engaged={}",
                    player.getGameProfile().getName(), limit, newSlots, formation.engaged);
        }
    }

    private static boolean isFollower(EntityMaid maid, ServerPlayer player) {
        if (!maid.isAlive() || maid.level() != player.level()) {
            return false;
        }
        if (!player.getUUID().equals(maid.getOwnerUUID())) {
            return false;
        }
        if (maid.isPassenger() || maid.isHomeModeEnable() || !maid.canBrainMoving()) {
            return false;
        }
        return true;
    }

    @Nullable
    public static FormationSlot getSlot(EntityMaid maid) {
        OwnerFormation formation = FORMATIONS.get(maid.getOwnerUUID());
        if (formation == null || formation.rosterSize < 2) {
            return null;
        }
        Integer index = formation.slotByMaid.get(maid.getUUID());
        if (index == null || index < 0 || index >= formation.rosterSize) {
            return null;
        }
        return new FormationSlot(index, formation.rosterSize);
    }

    public static int getFollowRange(ServerPlayer player) {
        return FormationSettings.get(player.serverLevel()).getFollowRange(player);
    }

    public static boolean isOwnerHighSpeed(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return false;
        }
        if (player.isSprinting()) {
            return true;
        }
        OwnerFormation formation = FORMATIONS.get(player.getUUID());
        if (formation == null) {
            return false;
        }
        double speed = Math.sqrt(
                formation.motionX * formation.motionX + formation.motionZ * formation.motionZ);
        return speed >= FormationConfig.FRONT_PRIORITY_SPEED.get();
    }

    public static boolean isOwnerMoving(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return false;
        }
        OwnerFormation formation = FORMATIONS.get(player.getUUID());
        if (formation == null) {
            return false;
        }
        double speed = Math.sqrt(
                formation.motionX * formation.motionX + formation.motionZ * formation.motionZ);
        return speed > FormationConfig.STILL_SPEED.get();
    }

    /**
     * Native TLM look-at-player behaviors use LOOK_TARGET through
     * SetEntityLookTarget / BehaviorUtils. They must not overwrite the
     * movement-facing look while the maid is actively following.
     */
    public static boolean shouldSuppressNativeLook(EntityMaid maid) {
        if (maid.getTarget() != null || !canFollow(maid)) {
            return false;
        }
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return true;
        }
        Vec3 movement = maid.getDeltaMovement();
        return movement.x * movement.x + movement.z * movement.z > 0.0025D;
    }

    /**
     * Look direction used to override native idle/work look targets. Movement
     * takes priority; if the maid is momentarily blocked but still has a path,
     * keep facing the path target instead of snapping to the owner.
     */
    @Nullable
    public static PositionTracker getMovementLookTracker(EntityMaid maid) {
        Vec3 movement = maid.getDeltaMovement();
        if (movement.x * movement.x + movement.z * movement.z > 0.0025D) {
            BlockPos lookPos = BlockPos.containing(
                    maid.getX() + movement.x * 8.0D,
                    maid.getEyeY(),
                    maid.getZ() + movement.z * 8.0D
            );
            return new BlockPosTracker(lookPos);
        }
        WalkTarget walkTarget = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        return walkTarget == null ? null : walkTarget.getTarget();
    }

    /**
     * Temporary movement-speed bonus for front guards during a high-speed chase.
     * The modifier has a fixed UUID and is removed as soon as the front chase
     * ends or the maid enters combat.
     */
    public static void setFrontBoost(EntityMaid maid, boolean active) {
        AttributeInstance speed = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier existing = speed.getModifier(FRONT_BOOST_ID);
        if (active && existing == null) {
            speed.addTransientModifier(new AttributeModifier(
                    FRONT_BOOST_ID,
                    "tlmformation:front_guard_boost",
                    FormationConfig.FRONT_SPEED_BOOST.get(),
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        } else if (!active && existing != null) {
            speed.removeModifier(FRONT_BOOST_ID);
        }
    }

    /**
     * Rear maids only get this while actively chasing. It is removed as soon
     * as they catch the rear slot, so their base movement_speed is untouched.
     */
    public static void setRearBoost(EntityMaid maid, boolean active) {
        AttributeInstance speed = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier existing = speed.getModifier(REAR_BOOST_ID);
        if (active && existing == null) {
            speed.addTransientModifier(new AttributeModifier(
                    REAR_BOOST_ID,
                    "tlmformation:rear_chase_boost",
                    FormationConfig.REAR_SPEED_BOOST.get(),
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        } else if (!active && existing != null) {
            speed.removeModifier(REAR_BOOST_ID);
        }
    }

    /**
     * Whether the maid is currently in forced-follow mode.
     *
     * Enter at FollowRange; release only after the owner has been stationary
     * for a while and the maid is back inside 0.75 * FollowRange.
     */
    public static boolean shouldUseFormation(EntityMaid maid) {
        if (isParked(maid) || TacticalManager.hasState(maid)
                || com.tlmformation.vehicle.AirdropManager.isAirdropManaged(maid)) {
            // 空投作业期间（含索降/索升）不参与编队跟随：位置由 AirdropManager / 索降状态机全权接管
            return false;
        }
        if (!shouldSuppressVanillaFollow(maid)) {
            return false;
        }
        if (maid.getTarget() != null
                || maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            return false;
        }

        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return false;
        }
        OwnerFormation formation = FORMATIONS.get(player.getUUID());
        if (formation == null) {
            return false;
        }

        double distance = maid.distanceTo(owner);
        double followRange = getFollowRange(player);
        boolean engaged = formation.engaged.getOrDefault(maid.getUUID(), false);

        if (!engaged) {
            if (distance <= followRange) {
                return false;
            }
            engaged = true;
            formation.engaged.put(maid.getUUID(), true);
        }

        if (distance <= followRange * 0.75D
                && formation.stillTicks >= FormationConfig.FOLLOW_RELEASE_TICKS.get()
                && maid.getTask() != TaskManager.getIdleTask()) {
            // Working maids may leave formation and continue their job once
            // they are well inside FollowRange and the owner is stationary.
            formation.engaged.put(maid.getUUID(), false);
            return false;
        }

        return true;
    }

    public static FollowMode getMode(EntityMaid maid) {
        OwnerFormation formation = FORMATIONS.get(maid.getOwnerUUID());
        if (formation != null
                && formation.stillTicks >= FormationConfig.STILL_TICKS.get()) {
            return FollowMode.DISPERSE;
        }
        return FollowMode.FORMATION;
    }

    public static boolean shouldSuppressVanillaFollow(EntityMaid maid) {
        if (isParked(maid)) {
            return true;
        }
        // 空投作业中的女仆一律压制原生跟随。
        // 索降途中她会脱离座位挂在绳上（canBrainMoving() 为 true、离主人约 40 格），
        // TLM 的 MaidFollowOwnerTask 会直接 teleportToOwner —— 表现为「部署时有概率瞬移落地」。
        if (com.tlmformation.vehicle.AirdropManager.isAirdropManaged(maid)) {
            return true;
        }
        if (!FormationConfig.ENABLED.get()) {
            return false;
        }
        LivingEntity owner = maid.getOwner();
        if (owner == null || owner.isSpectator() || owner.isDeadOrDying()) {
            return false;
        }
        if (owner.isPassenger()) {
            // 主人坐在黑鹰上做索降/索升时，也必须压制原生跟随：
            // 否则女仆会追着空中的主人跑，永远走不到绳底（表现为“驾驶员不下机，索升就不动”）
            if (owner.getVehicle() instanceof com.tlmformation.vehicle.EntityHelicopter helicopter
                    && helicopter.isRappelling()) {
                return true;
            }
            return false;
        }
        if (maid.level() != owner.level() || maid.isHomeModeEnable() || !maid.canBrainMoving()) {
            return false;
        }
        if (TacticalManager.hasState(maid)) {
            return true;
        }
        return getSlot(maid) != null;
    }

    public static boolean canFollow(EntityMaid maid) {
        if (maid.getOwner() instanceof ServerPlayer player
                && com.tlmformation.team.GrandAssembleManager.isActive(player)) {
            return false;
        }
        return shouldUseFormation(maid);
    }

    /**
     * Team-only parking state. A parked maid is excluded from formation and
     * vanilla follow while her team is inactive, but ordinary non-team follow
     * logic is untouched.
     */
    public static void park(EntityMaid maid) {
        maid.getPersistentData().putBoolean("TLMParked", true);
        maid.setNoAi(true);
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.setTarget(null);
        setFrontBoost(maid, false);
        setRearBoost(maid, false);
    }

    public static void unpark(EntityMaid maid) {
        maid.getPersistentData().remove("TLMParked");
        maid.setNoAi(false);
    }

    public static boolean isParked(EntityMaid maid) {
        return maid.getPersistentData().getBoolean("TLMParked");
    }

    /**
     * The target only needs to be refreshed while the owner is moving, during
     * the initial settle window, or when the maid is far behind. While the
     * owner is stationary and dispersed, the existing WalkTarget is enough.
     */
    public static boolean shouldRefreshTarget(EntityMaid maid, LivingEntity owner) {
        // Recompute every few ticks. This is required so the transition from
        // FORMATION to DISPERSE is picked up immediately when stillTicks hits
        // the threshold; FormationFollowTask already avoids writing a target
        // when the maid is standing on it.
        return true;
    }

    @Nullable
    public static Vec3 getTarget(EntityMaid maid, LivingEntity owner, FormationSlot slot) {
        if (!(owner instanceof ServerPlayer player)) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        OwnerFormation formation = FORMATIONS.get(player.getUUID());
        if (formation == null) {
            return null;
        }
        int frontQuota = com.tlmformation.team.GrandAssembleManager
                .isActive(player) ? 4 : 2;
        SlotOffset baseOffset = FormationLayout.slot(
                slot.index(), slot.count(), frontQuota)
                .scaled(FormationConfig.SLOT_SCALE.get());
        boolean formationMode = getMode(maid) == FollowMode.FORMATION;
        boolean frontSlot = FormationLayout.isFrontSlot(slot.index(), slot.count());
        double ownerSpeed = Math.sqrt(
                formation.motionX * formation.motionX + formation.motionZ * formation.motionZ);
        boolean lowSpeedWalking = ownerSpeed < FormationConfig.LOW_SPEED_PREDICTION_THRESHOLD.get();
        double frontLead = frontSlot
                ? FormationConfig.FRONT_TARGET_LEAD.get()
                + (lowSpeedWalking ? FormationConfig.LOW_SPEED_FRONT_LEAD.get() : 0.0D)
                : 0.0D;
        SlotOffset offset = frontSlot
                ? new SlotOffset(baseOffset.right(), baseOffset.back() - frontLead)
                : baseOffset;
        boolean frontChase = formationMode && frontSlot && isOwnerHighSpeed(maid);
        boolean coolingDown = frontChase && isFrontFallbackCooling(maid);

        Vec3 safe = null;
        boolean predictedTargetSafe = false;

        if (!coolingDown) {
            Vec3 raw;
            if (formationMode) {
                raw = formationTarget(player, offset, formation.motionX, formation.motionZ);
            } else {
                raw = dispersionTarget(maid, player, slot);
            }

            safe = TargetSafety.findSafe(level, maid, raw);
            predictedTargetSafe = safe != null;

            // Front guards must stay on the predicted point, even if a straight
            // approach is partially blocked. Try nearby predicted points before
            // falling back to an unpredicted / rear position.
            if (safe == null && frontChase) {
                Vec3 predictedPoint = predictionAnchor(player, formation.motionX, formation.motionZ);
                double[] lateralOffsets = {
                        offset.right() - 0.8D,
                        offset.right() + 0.8D,
                        offset.right() - 1.6D,
                        offset.right() + 1.6D
                };
                for (double lateral : lateralOffsets) {
                    SlotOffset variant = new SlotOffset(lateral, offset.back());
                    safe = TargetSafety.findSafe(level, maid,
                            FormationLayout.target(player, predictedPoint, variant));
                    if (safe != null) {
                        predictedTargetSafe = true;
                        break;
                    }
                }

                // If the full lead is blocked, progressively reduce only the
                // extra front lead and keep the closest legal front position.
                if (safe == null) {
                    double[] reducedLeads = {frontLead * 0.6D, frontLead * 0.3D, 0.0D};
                    for (double reducedLead : reducedLeads) {
                        SlotOffset variant = new SlotOffset(
                                baseOffset.right(), baseOffset.back() - reducedLead);
                        safe = TargetSafety.findSafe(level, maid,
                                FormationLayout.target(player, predictedPoint, variant));
                        if (safe != null) {
                            predictedTargetSafe = true;
                            break;
                        }
                    }
                }
                if (safe == null) {
                    markFrontFallback(maid);
                }
            }
        } else {
            // Do not immediately retry the same invalid predicted point.
            // Stay on the last safe front target for the cooldown window.
            Vec3 lastSafe = FRONT_LAST_SAFE.get(maid.getUUID());
            if (lastSafe != null) {
                safe = TargetSafety.findSafe(level, maid, lastSafe);
            }
        }

        if (safe == null) {
            // Retry without prediction.
            safe = TargetSafety.findSafe(level, maid,
                    FormationLayout.target(player, player.position(), offset));
        }
        if (safe == null) {
            // A blocked front slot falls back a bit behind the owner.
            SlotOffset fallback = new SlotOffset(offset.right(), offset.back() + 2.0D);
            safe = TargetSafety.findSafe(level, maid,
                    FormationLayout.target(player, player.position(), fallback));
        }
        if (safe == null) {
            // Last resort: the player's feet are always a reachable anchor.
            safe = player.position();
        }

        if (frontSlot && predictedTargetSafe && safe != null) {
            FRONT_LAST_SAFE.put(maid.getUUID(), safe);
        }
        return safe;
    }

    private static boolean isFrontFallbackCooling(EntityMaid maid) {
        Long until = FRONT_FALLBACK_UNTIL.get(maid.getUUID());
        return until != null && maid.level().getGameTime() < until;
    }

    private static void markFrontFallback(EntityMaid maid) {
        long until = maid.level().getGameTime()
                + FormationConfig.FRONT_FALLBACK_COOLDOWN.get();
        FRONT_FALLBACK_UNTIL.put(maid.getUUID(), until);
    }

    public static boolean isTargetSafe(EntityMaid maid, Vec3 target) {
        if (!(maid.level() instanceof ServerLevel level) || target == null) {
            return false;
        }
        return TargetSafety.isSafe(level, maid, target);
    }

    private static Vec3 predictionAnchor(Player player, double motionX, double motionZ) {
        double speed = Math.sqrt(motionX * motionX + motionZ * motionZ);
        Vec3 anchor = player.position();
        double stillSpeed = FormationConfig.STILL_SPEED.get();
        if (speed > stillSpeed) {
            Vec3 direction = new Vec3(motionX, 0, motionZ).normalize();

            // High speed tightens the prediction, keeping the target inside the
            // maid's practical pathfinding reach instead of extending it.
            double t = Math.min(1.0D, speed / FormationConfig.PREDICTION_SPEED_CAP.get());
            double cap = FormationConfig.MAX_PREDICTION.get()
                    + (FormationConfig.HIGH_SPEED_PREDICTION_CAP.get()
                    - FormationConfig.MAX_PREDICTION.get()) * t;

            // Walking pace should not constantly chase a point far in front.
            double lowSpeedFactor = speed < FormationConfig.LOW_SPEED_PREDICTION_THRESHOLD.get()
                    ? FormationConfig.LOW_SPEED_PREDICTION_FACTOR.get()
                    : 1.0D;

            double prediction = Math.min(
                    speed * FormationConfig.PREDICTION_TICKS.get()
                            * FormationConfig.PREDICTION_STRENGTH.get() * lowSpeedFactor,
                    cap
            );
            anchor = player.position().add(direction.scale(prediction));
        }
        return anchor;
    }

    private static Vec3 formationTarget(Player player, SlotOffset offset,
                                        double motionX, double motionZ) {
        return FormationLayout.target(player, predictionAnchor(player, motionX, motionZ), offset);
    }

    private static Vec3 dispersionTarget(EntityMaid maid, ServerPlayer player, FormationSlot slot) {
        double followRange = getFollowRange(player);
        double radius = Math.min(
                followRange * 0.8D,
                Math.max(4.0D, 1.1D * slot.count())
        );

        int hash = maid.getUUID().hashCode();
        double angleJitter = (((hash & 0xFF) / 255.0D) - 0.5D) * 0.35D;
        double radiusJitter = 1.0D + ((((hash >> 8) & 0xFF) / 255.0D) - 0.5D) * 0.15D;

        double angle = (Math.PI * 2.0D * slot.index()) / slot.count() + angleJitter;
        double r = radius * radiusJitter;

        return new Vec3(
                player.getX() + Math.cos(angle) * r,
                player.getY(),
                player.getZ() + Math.sin(angle) * r
        );
    }

    public static int getRosterSize(ServerPlayer player) {
        OwnerFormation formation = FORMATIONS.get(player.getUUID());
        return formation == null ? 0 : formation.rosterSize;
    }

    public static int getSlotIndex(EntityMaid maid) {
        FormationSlot slot = getSlot(maid);
        return slot == null ? -1 : slot.index();
    }

    public static List<EntityMaid> findNearbyMaids(ServerPlayer player, double radius) {
        return player.serverLevel().getEntitiesOfClass(EntityMaid.class,
                player.getBoundingBox().inflate(radius),
                maid -> isFollower(maid, player));
    }

    private static int getSavedSlot(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        return data.contains(SLOT_TAG) ? data.getInt(SLOT_TAG) : -1;
    }

    private static void setSavedSlot(EntityMaid maid, int slot) {
        maid.getPersistentData().putInt(SLOT_TAG, slot);
    }

    /**
     * Force an immediate return to formation following, even when the maid is
     * already inside FollowRange. Used after tactical states end.
     */
    public static void engage(EntityMaid maid) {
        OwnerFormation formation = FORMATIONS.get(maid.getOwnerUUID());
        if (formation != null) {
            formation.engaged.put(maid.getUUID(), true);
        }
    }

    public static void onMaidDeath(EntityMaid maid) {
        maid.getPersistentData().remove(SLOT_TAG);
        FRONT_FALLBACK_UNTIL.remove(maid.getUUID());
        FRONT_LAST_SAFE.remove(maid.getUUID());
        OwnerFormation formation = FORMATIONS.get(maid.getOwnerUUID());
        if (formation != null) {
            formation.slotByMaid.remove(maid.getUUID());
            formation.engaged.remove(maid.getUUID());
        }
    }

    public static void onMaidChangedDimension(EntityMaid maid) {
        maid.getPersistentData().remove(SLOT_TAG);
        FRONT_FALLBACK_UNTIL.remove(maid.getUUID());
        FRONT_LAST_SAFE.remove(maid.getUUID());
        OwnerFormation formation = FORMATIONS.get(maid.getOwnerUUID());
        if (formation != null) {
            formation.slotByMaid.remove(maid.getUUID());
            formation.engaged.remove(maid.getUUID());
        }
    }

    public static void onOwnerRespawn(ServerPlayer player) {
        FORMATIONS.remove(player.getUUID());
    }

    public static void onOwnerLogout(ServerPlayer player) {
        FORMATIONS.remove(player.getUUID());
    }

    public static int recall(ServerPlayer player) {
        return recall(player, 16.0D);
    }

    public static int recall(ServerPlayer player, double minDistance) {
        if (com.tlmformation.team.GrandAssembleManager.isActive(player)) {
            return 0;
        }
        int count = 0;
        double minDistanceSqr = minDistance * minDistance;
        for (EntityMaid maid : player.serverLevel().getEntitiesOfClass(EntityMaid.class,
                player.getBoundingBox().inflate(256),
                maid -> player.getUUID().equals(maid.getOwnerUUID())
                        && maid.isAlive()
                        && !maid.isHomeModeEnable()
                        // 空投作业中的女仆不能被“紧急召回”从绳上/机舱里拽走
                        && !com.tlmformation.vehicle.AirdropManager.isAirdropManaged(maid)
                        && maid.canBrainMoving())) {
            VehicleMaidRelease.release(maid);
            if (maid.distanceToSqr(player) > minDistanceSqr && maid.teleportToOwner(player)) {
                maid.getNavigation().stop();
                count++;
            }
        }
        return count;
    }

    private static final class OwnerFormation {
        private final UUID owner;
        private ResourceKey<Level> dimension;
        private int rosterSize;
        private long stillTicks;
        private long settleUntil;
        private double lastX;
        private double lastZ;
        private double motionX;
        private double motionZ;
        private final Map<UUID, Integer> slotByMaid = new HashMap<>();
        private final Map<UUID, Boolean> engaged = new HashMap<>();

        private OwnerFormation(UUID owner) {
            this.owner = owner;
        }
    }
}
