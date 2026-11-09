package com.tlmformation.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskBowAttack;
import com.tlmformation.combat.CombatManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TaskBowAttack.class)
public abstract class TaskBowAttackMixin {
    @Inject(remap = false, method = "searchRadius", at = @At("RETURN"), cancellable = true)
    private void tlmf$expandRadius(EntityMaid maid, CallbackInfoReturnable<Float> cir) {
        if (CombatManager.isActive(maid) || CombatManager.isTeleportGrace(maid)) {
            cir.setReturnValue(Math.max(cir.getReturnValueF(), CombatManager.range(maid)));
        }
    }
}

