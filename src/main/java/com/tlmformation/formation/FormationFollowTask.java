package com.tlmformation.formation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import com.tlmformation.config.FormationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

/**
 * Core behavior added through IExtraMaidBrain. Raw targets are recomputed
 * periodically, while a per-tick smoothed target prevents path resets from
 * small target changes.
 */
public final class FormationFollowTask extends Behavior<EntityMaid> {
    private static final int RAW_TARGET_TICKS = 5;
    private static final double MOVING_LOOK_SPEED_SQR = 0.0025D;
    private static final long BOOST_HOLD_TICKS = 20L;

    private int rawTargetCooldown;
    private int pathCommitCooldown;
    private long boostUntil;
    private Vec3 rawTarget;
    private Vec3 smoothedTarget;
    private Vec3 lastCommittedTarget;
    private double lastX;
    private double lastZ;
    private boolean hasLastPosition;

    public FormationFollowTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED
        ));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return FormationManager.canFollow(maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        resetState();
        updateTarget(maid, gameTime);
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        updateTarget(maid, gameTime);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return FormationManager.canFollow(maid);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        resetState();
        FormationManager.setFrontBoost(maid, false);
        FormationManager.setRearBoost(maid, false);
        if (maid.getTarget() == null) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }

    private void resetState() {
        rawTargetCooldown = 0;
        pathCommitCooldown = 0;
        boostUntil = 0L;
        rawTarget = null;
        smoothedTarget = null;
        lastCommittedTarget = null;
        hasLastPosition = false;
    }

    private void updateTarget(EntityMaid maid, long gameTime) {
        LivingEntity owner = maid.getOwner();
        if (owner == null || maid.getTarget() != null) {
            FormationManager.setFrontBoost(maid, false);
            FormationManager.setRearBoost(maid, false);
            return;
        }

        FormationSlot slot = FormationManager.getSlot(maid);
        if (slot == null) {
            FormationManager.setFrontBoost(maid, false);
            FormationManager.setRearBoost(maid, false);
            return;
        }

        if (--rawTargetCooldown <= 0 || rawTarget == null) {
            rawTargetCooldown = RAW_TARGET_TICKS;
            rawTarget = FormationManager.getTarget(maid, owner, slot);
            if (rawTarget == null) {
                FormationManager.setFrontBoost(maid, false);
                FormationManager.setRearBoost(maid, false);
                return;
            }
        }

        double mx = movementX(maid);
        double mz = movementZ(maid);
        lastX = maid.getX();
        lastZ = maid.getZ();
        hasLastPosition = true;

        smoothTarget(maid, mx, mz);
        updateLookTarget(maid, owner, mx, mz);

        boolean front = FormationLayout.isFrontSlot(slot.index(), slot.count());
        boolean highSpeed = FormationManager.isOwnerHighSpeed(maid);
        double distance = horizontalDistance(maid, smoothedTarget);

        // Front boost is only for an actual chase, never for a maid already
        // standing on/near the slot.
        if (front && highSpeed
                && distance > FormationConfig.FRONT_BOOST_DISTANCE.get()) {
            boostUntil = Math.max(boostUntil, gameTime + BOOST_HOLD_TICKS);
        }
        if (!front || distance <= FormationConfig.FRONT_BOOST_DISTANCE.get()) {
            boostUntil = 0L;
        }
        boolean boosting = gameTime < boostUntil;
        FormationManager.setFrontBoost(maid, boosting);

        // Rear maids are never slowed by approach logic. If they are behind
        // their rear target while the owner is moving, give them a temporary
        // chase boost and full walk speed.
        boolean rearChasing = !front
                && FormationManager.isOwnerMoving(maid)
                && distance > FormationConfig.REAR_CHASE_DISTANCE.get();
        FormationManager.setRearBoost(maid, rearChasing);

        if (distance < 1.5D) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            lastCommittedTarget = null;
            return;
        }

        if (pathCommitCooldown > 0) {
            pathCommitCooldown--;
        }

        boolean smoothedSafe = FormationManager.isTargetSafe(maid, smoothedTarget);
        boolean committedSafe = lastCommittedTarget != null
                && FormationManager.isTargetSafe(maid, lastCommittedTarget);
        if (!smoothedSafe) {
            if (lastCommittedTarget != null && committedSafe) {
                // Keep the valid old path; do not commit an unsafe target.
            } else if (FormationManager.isTargetSafe(maid, rawTarget)) {
                smoothedTarget = rawTarget;
                lastCommittedTarget = null;
                pathCommitCooldown = 0;
            } else {
                return;
            }
        } else if (lastCommittedTarget != null && !committedSafe) {
            lastCommittedTarget = null;
            pathCommitCooldown = 0;
        }

        if (!maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            lastCommittedTarget = null;
        }
        double threshold = FormationConfig.TARGET_CHANGE_THRESHOLD.get();
        boolean targetMoved = lastCommittedTarget == null
                || lastCommittedTarget.distanceToSqr(smoothedTarget) > threshold * threshold;

        if (targetMoved
                && (lastCommittedTarget == null || pathCommitCooldown <= 0)
                && FormationManager.isTargetSafe(maid, smoothedTarget)) {
            float speed = FormationConfig.FOLLOW_SPEED.get().floatValue();
            if (front && (highSpeed || boosting)) {
                speed *= 1.35F;
            } else if (rearChasing) {
                speed *= 1.15F;
            }
            int closeEnough = front ? 1 : 2;
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(smoothedTarget, speed, closeEnough));
            lastCommittedTarget = smoothedTarget;
            pathCommitCooldown = FormationConfig.PATH_COMMIT_TICKS.get();
        }
    }

    private void smoothTarget(EntityMaid maid, double mx, double mz) {
        if (smoothedTarget == null) {
            Vec3 delta = rawTarget.subtract(maid.position());
            double distance = delta.length();
            if (distance <= 1.5D) {
                smoothedTarget = rawTarget;
            } else {
                smoothedTarget = maid.position().add(delta.normalize().scale(1.5D));
            }
            lastCommittedTarget = null;
            return;
        }

        Vec3 delta = rawTarget.subtract(smoothedTarget);
        double distance = delta.length();
        if (distance <= 1.0E-4D) {
            smoothedTarget = rawTarget;
            return;
        }
        if (distance > 24.0D) {
            smoothedTarget = rawTarget;
            lastCommittedTarget = null;
            return;
        }

        double maidSpeed = Math.sqrt(mx * mx + mz * mz);
        double step = Math.max(
                FormationConfig.TARGET_SMOOTHING_MIN.get(),
                maidSpeed * FormationConfig.TARGET_SMOOTHING_FACTOR.get()
        );
        step = Math.min(step, FormationConfig.TARGET_SMOOTHING_SPEED.get());

        if (distance <= step) {
            smoothedTarget = rawTarget;
        } else {
            smoothedTarget = smoothedTarget.add(delta.scale(step / distance));
        }
    }

    private void updateLookTarget(EntityMaid maid, LivingEntity owner,
                                  double mx, double mz) {
        if (mx * mx + mz * mz > MOVING_LOOK_SPEED_SQR) {
            double lookX = maid.getX() + mx * 8.0D;
            double lookZ = maid.getZ() + mz * 8.0D;
            setLookTarget(maid, new Vec3(lookX, maid.getEyeY(), lookZ));
            return;
        }

        boolean pathing = maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                || !maid.getNavigation().isDone();
        if (pathing) {
            Vec3 lookTarget = lastCommittedTarget != null ? lastCommittedTarget : smoothedTarget;
            if (lookTarget != null) {
                setLookTarget(maid, lookTarget);
                return;
            }
        }

        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
                new EntityTracker(owner, false));
    }

    private void setLookTarget(EntityMaid maid, Vec3 target) {
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
                new BlockPosTracker(BlockPos.containing(target)));
    }

    private double horizontalDistance(EntityMaid maid, Vec3 target) {
        double dx = maid.getX() - target.x;
        double dz = maid.getZ() - target.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double movementX(EntityMaid maid) {
        if (!hasLastPosition) {
            return maid.getDeltaMovement().x;
        }
        return maid.getX() - lastX;
    }

    private double movementZ(EntityMaid maid) {
        if (!hasLastPosition) {
            return maid.getDeltaMovement().z;
        }
        return maid.getZ() - lastZ;
    }
}
