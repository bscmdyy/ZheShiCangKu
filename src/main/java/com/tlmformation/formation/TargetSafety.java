package com.tlmformation.formation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Shared target-point validation for formation, dispersion and prediction.
 * It never loads chunks; an unloaded target is simply rejected.
 */
public final class TargetSafety {
    private TargetSafety() {
    }

    @Nullable
    public static Vec3 findSafe(ServerLevel level, EntityMaid maid, Vec3 candidate) {
        BlockPos feet = BlockPos.containing(candidate);
        if (!level.isLoaded(feet)) {
            return null;
        }

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        BlockState belowState = level.getBlockState(feet.below());

        boolean water = !feetState.getFluidState().isEmpty()
                && !headState.getFluidState().isEmpty();
        if (water) {
            return candidate;
        }

        if (!feetState.getCollisionShape(level, feet).isEmpty()) {
            return null;
        }
        if (!headState.getCollisionShape(level, feet.above()).isEmpty()) {
            return null;
        }

        // Not floating: require a supporting block or fluid below.
        if (belowState.getCollisionShape(level, feet.below()).isEmpty()
                && belowState.getFluidState().isEmpty()) {
            return null;
        }

        AABB movedBox = maid.getBoundingBox().move(candidate.subtract(maid.position()));
        if (!level.noCollision(maid, movedBox)) {
            return null;
        }

        return new Vec3(candidate.x, feet.getY(), candidate.z);
    }

    /**
     * Exact validation for an already committed target. findSafe() may return a
     * vertically adjusted safe point, which must not count as the original
     * target still being valid.
     */
    public static boolean isSafe(ServerLevel level, EntityMaid maid, Vec3 target) {
        Vec3 safe = findSafe(level, maid, target);
        return safe != null && Math.abs(safe.y - target.y) < 1.0D;
    }
}
