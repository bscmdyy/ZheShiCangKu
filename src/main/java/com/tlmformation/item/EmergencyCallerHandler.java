package com.tlmformation.item;

import com.tlmformation.TLMFormationMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmergencyCallerHandler {
    private EmergencyCallerHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLethalHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getAmount() < player.getHealth()) return;
        if (EmergencyCallerItem.tryTrigger(player, true)) {
            event.setCanceled(true);
        }
    }
}
