package com.tlmformation.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerVehicleTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.vehicle.EntityHelicopter;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 索降 / 索升作业期间，压制 TLM 的「追着主人的载具上机」任务。
 *
 * <p>否则主人坐在黑鹰驾驶位上时，女仆会一直尝试奔向空中的载具（永远到不了），
 * 覆盖掉我们下发的集结/爬升路径 —— 表现为“驾驶员一离位女仆才开始动”。</p>
 */
@Mixin(MaidFollowOwnerVehicleTask.class)
public abstract class MaidFollowOwnerVehicleTaskMixin {
    @Inject(remap = false, method = "checkExtraStartConditions", at = @At("HEAD"), cancellable = true)
    private void tlmf$suppressDuringAirborneRappel(ServerLevel level, EntityMaid maid,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (tlmf$ownerInRappel(maid)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(remap = false, method = "start", at = @At("HEAD"), cancellable = true)
    private void tlmf$suppressDuringAirborneRappelStart(ServerLevel level, EntityMaid maid,
                                                        long gameTime, CallbackInfo ci) {
        if (tlmf$ownerInRappel(maid)) {
            ci.cancel();
        }
    }

    private static boolean tlmf$ownerInRappel(EntityMaid maid) {
        return maid.getOwner() != null
                && maid.getOwner().getVehicle() instanceof EntityHelicopter helicopter
                && helicopter.isRappelling();
    }
}
