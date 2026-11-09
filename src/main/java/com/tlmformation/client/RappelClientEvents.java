package com.tlmformation.client;

import com.tlmformation.TLMFormationMod;
import com.tlmformation.network.C2SRappelAction;
import com.tlmformation.network.NetworkHandler;
import com.tlmformation.vehicle.EntityHelicopter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 索降按键轮询：小键盘 1 只在「玩家坐 zuo1 + 载具是黑鹰」时才发包，
 * 其余情况静默吞掉按键，避免误触其它 HUD。
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RappelClientEvents {
    private RappelClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            drainClicks();
            return;
        }
        while (RappelKeys.RAPPEL.consumeClick()) {
            if (minecraft.screen != null) {
                continue;
            }
            if (!ModList.get().isLoaded("superbwarfare")) {
                continue;
            }
            Entity vehicle = minecraft.player.getVehicle();
            if (vehicle instanceof EntityHelicopter heli
                    && heli.getSeatIndex(minecraft.player) == 0) {
                NetworkHandler.sendRappelAction(C2SRappelAction.START);
            }
        }
    }

    private static void drainClicks() {
        while (RappelKeys.RAPPEL.consumeClick()) {
            // 丢弃离线期间积压的点击
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        com.tlmformation.client.helicopter.RappelClientState.clear();
        // 退出世界时把还在循环的旋翼声实例全部停掉
        com.tlmformation.client.heli.HelicopterSoundManager.clear();
    }
}
