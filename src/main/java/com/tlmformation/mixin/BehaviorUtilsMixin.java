package com.tlmformation.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.formation.FormationManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Some TLM tasks call BehaviorUtils.lookAtEntity directly. Suppress those only
 * while the maid is actively formation-following; combat look targets still
 * use the normal path because EntityMaid#getTarget() disables suppression.
 */
@Mixin(BehaviorUtils.class)
public abstract class BehaviorUtilsMixin {
    @Inject(method = "lookAtEntity(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void tlmf$suppressFollowLook(LivingEntity entity, LivingEntity target,
                                                CallbackInfo ci) {
        if (entity instanceof EntityMaid maid
                && FormationManager.shouldSuppressNativeLook(maid)) {
            ci.cancel();
        }
    }
}
