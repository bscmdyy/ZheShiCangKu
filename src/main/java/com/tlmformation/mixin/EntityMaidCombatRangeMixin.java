package com.tlmformation.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.combat.CombatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityMaid.class)
public abstract class EntityMaidCombatRangeMixin {
    @Inject(remap = false, method = "searchDimension", at = @At("RETURN"), cancellable = true)
    private void tlmf$expandSearch(CallbackInfoReturnable<AABB> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (!CombatManager.isActive(maid)) {
            return;
        }
        LivingEntity owner = maid.getOwner();
        if (owner == null) {
            return;
        }
        double range = CombatManager.range(maid);
        cir.setReturnValue(owner.getBoundingBox().inflate(range, Math.min(range, 16.0D), range));
    }

    @Inject(method = "isWithinRestriction(Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void tlmf$expandRestriction(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (!CombatManager.isActive(maid)) {
            return;
        }
        LivingEntity owner = maid.getOwner();
        if (owner == null) {
            return;
        }
        double range = CombatManager.range(maid);
        if (owner.distanceToSqr(Vec3.atCenterOf(pos)) <= range * range) {
            cir.setReturnValue(true);
        }
    }

    @Inject(remap = false, method = "canSee(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void tlmf$expandCanSee(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (target == null || target == maid || !CombatManager.isActive(maid)) {
            return;
        }
        double range = CombatManager.range(maid);
        if (maid.distanceToSqr(target) <= range * range && maid.hasLineOfSight(target)) {
            cir.setReturnValue(true);
        }
    }
}

