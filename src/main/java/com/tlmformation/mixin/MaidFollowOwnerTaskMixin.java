package com.tlmformation.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.formation.FormationManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MaidFollowOwnerTask.class)
public abstract class MaidFollowOwnerTaskMixin {
    @Inject(remap = false, method = "checkExtraStartConditions", at = @At("HEAD"), cancellable = true)
    private void tlmf$suppressVanillaFollow(ServerLevel level, EntityMaid maid,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (FormationManager.shouldSuppressVanillaFollow(maid)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(remap = false, method = "start", at = @At("HEAD"), cancellable = true)
    private void tlmf$suppressVanillaFollowStart(ServerLevel level, EntityMaid maid,
                                                  long gameTime, CallbackInfo ci) {
        if (FormationManager.shouldSuppressVanillaFollow(maid)) {
            ci.cancel();
        }
    }
}

