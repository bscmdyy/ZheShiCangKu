package com.tlmformation.mixin;

import com.tlmformation.client.TacticalWheelScreen;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures raw mouse movement while the tactical wheel is open. The deltas are
 * routed to the wheel, and the vanilla camera turn is cancelled.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    private double xpos;
    @Shadow
    private double ypos;

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void tlmf$captureWheelMove(long window, double newX, double newY,
                                       CallbackInfo ci) {
        if (!TacticalWheelScreen.isActive()) {
            return;
        }
        double dx = newX - this.xpos;
        double dy = newY - this.ypos;
        this.xpos = newX;
        this.ypos = newY;
        TacticalWheelScreen.addRawDelta(dx, dy);
        ci.cancel();
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void tlmf$blockWheelCameraTurn(CallbackInfo ci) {
        if (TacticalWheelScreen.isActive()) {
            ci.cancel();
        }
    }
}
