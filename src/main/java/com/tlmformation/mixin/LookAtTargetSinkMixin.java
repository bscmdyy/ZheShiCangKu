package com.tlmformation.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.formation.FormationManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Native TLM idle/work look tasks can set LOOK_TARGET to a nearby player
 * between FormationFollowTask updates. LookAtTargetSink runs at CORE priority
 * 0, before those tasks, so replace the target at the sink itself when the maid
 * is actively following. This prevents one-tick "glance back at owner" frames.
 */
@Mixin(LookAtTargetSink.class)
public abstract class LookAtTargetSinkMixin {
    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)V",
            at = @At("HEAD"))
    private void tlmf$forceMovementLook(ServerLevel level, Mob mob, long gameTime,
                                        CallbackInfo ci) {
        if (!(mob instanceof EntityMaid maid)
                || !FormationManager.shouldSuppressNativeLook(maid)) {
            return;
        }
        PositionTracker tracker = FormationManager.getMovementLookTracker(maid);
        if (tracker != null) {
            maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, tracker);
        }
    }
}
