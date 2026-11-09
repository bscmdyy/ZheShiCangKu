package com.tlmformation.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.tlmformation.combat.CombatManager;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TaskAttack.class)
public abstract class TaskAttackMixin {
    @Inject(remap = false, method = "farAway", at = @At("RETURN"), cancellable = true)
    private void tlmf$expandFarAway(LivingEntity target, EntityMaid maid,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        if (!target.isAlive() || target.isRemoved()) {
            return;
        }
        if (CombatManager.shouldKeepTarget(maid, target)) {
            cir.setReturnValue(false);
        }
    }
}

