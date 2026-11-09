package com.tlmformation;

import com.tlmformation.client.ArmorKeys;
import com.tlmformation.client.RappelKeys;
import com.tlmformation.client.TacticalKeys;
import com.tlmformation.client.TeamKeys;
import com.tlmformation.menu.FormationScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TacticalKeys.WHEEL);
        event.register(TeamKeys.TEAM);
        event.register(ArmorKeys.REINFORCEMENT);
        event.register(RappelKeys.RAPPEL);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(
                ModMenus.FORMATION.get(), FormationScreen::new));
    }
}
