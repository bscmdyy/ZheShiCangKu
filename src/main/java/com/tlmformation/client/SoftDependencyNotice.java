package com.tlmformation.client;

import com.tlmformation.TLMFormationMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** 软依赖提示：没有卓越前线时给出聊天栏提示（不崩溃，本体功能照常）。 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoftDependencyNotice {
    private static boolean announced;

    private SoftDependencyNotice() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || announced) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        announced = true;
        if (!ModList.get().isLoaded("superbwarfare")) {
            minecraft.player.displayClientMessage(Component.literal(
                    "\u00a7e【车万女仆：功能小队】缺少前置模组：卓越前线（Superb Warfare）\u00a77，载具相关功能不可用"),
                    false);
        }
    }
}
