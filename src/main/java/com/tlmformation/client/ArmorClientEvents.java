package com.tlmformation.client;

import com.tlmformation.TLMFormationMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** F10 —— 无人载具终端（只保留按下即打开）。 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArmorClientEvents {
    private ArmorClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (ArmorKeys.REINFORCEMENT.consumeClick()) {
            if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
                continue;
            }
            try {
                minecraft.setScreen(new DroneTerminalScreen());
            } catch (Throwable throwable) {
                TLMFormationMod.LOGGER.error("[F10] failed to open terminal", throwable);
            }
        }
    }

    @SubscribeEvent
    public static void onRawKey(net.minecraftforge.client.event.InputEvent.Key event) {
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS
                || event.getKey() != org.lwjgl.glfw.GLFW.GLFW_KEY_F10) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        minecraft.setScreen(new DroneTerminalScreen());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ArmorClientData.clear();
    }
}
