package com.tlmformation.client;

import com.tlmformation.TLMFormationMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TacticalClientEvents {
    private static final int HOLD_DELAY_TICKS = 3;
    private static int heldTicks;
    private static boolean suppressReopen;

    private TacticalClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TacticalWheelScreen) {
            heldTicks = 0;
            return;
        }
        if (minecraft.screen != null || minecraft.player == null || minecraft.level == null) {
            heldTicks = 0;
            return;
        }

        if (!TacticalKeys.WHEEL.isDown()) {
            heldTicks = 0;
            suppressReopen = false;
            return;
        }
        if (suppressReopen) {
            return;
        }

        if (++heldTicks >= HOLD_DELAY_TICKS) {
            heldTicks = 0;
            minecraft.setScreen(new TacticalWheelScreen());
        }
    }

    public static void suppressReopenUntilRelease() {
        suppressReopen = true;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TacticalClientState.clear();
        heldTicks = 0;
        suppressReopen = false;
    }
}
