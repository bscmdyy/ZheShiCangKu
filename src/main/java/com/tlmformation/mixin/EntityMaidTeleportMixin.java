package com.tlmformation.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityMaid.class)
public abstract class EntityMaidTeleportMixin {
    @Unique
    private LivingEntity tlmf$targetBeforeTeleport;

    @Inject(remap = false, method = "teleportToOwner", at = @At("HEAD"), cancellable = true)
    private void tlmf$captureTarget(LivingEntity owner, CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        // 空投作业中的女仆（登机 / 飞行 / 索降 / 索升）一律禁止 teleportToOwner：
        // 索降途中她已脱离座位挂在绳上、离主人 ~40 格，TLM 的跟随任务会在这里
        // 直接把她瞬移到地面 —— 玩家看到的就是「部署时有概率女仆瞬间出现在脚下」。
        if (com.tlmformation.vehicle.AirdropManager.isAirdropManaged(maid)) {
            cir.setReturnValue(false);
            return;
        }
        com.tlmformation.vehicle.VehicleMaidRelease.release(maid);
        this.tlmf$targetBeforeTeleport = maid.getTarget();
    }

    @Inject(remap = false, method = "teleportToOwner", at = @At("RETURN"))
    private void tlmf$restoreTarget(LivingEntity owner, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || this.tlmf$targetBeforeTeleport == null) {
            return;
        }
        EntityMaid maid = (EntityMaid) (Object) this;
        LivingEntity target = this.tlmf$targetBeforeTeleport;
        this.tlmf$targetBeforeTeleport = null;

        if (target.isAlive() && !target.isRemoved() && target.level() == maid.level()) {
            maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        }
    }
}

