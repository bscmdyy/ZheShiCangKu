package com.tlmformation.mixin;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMaidContainer.class)
public abstract class MaidContainerStillValidMixin {
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void tlmformation$alwaysValid(Player player,
                                          CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
