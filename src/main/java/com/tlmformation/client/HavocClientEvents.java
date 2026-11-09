package com.tlmformation.client;

import com.tlmformation.TLMFormationMod;
import com.tlmformation.vehicle.HavocEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** 客户端渲染器注册。 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HavocClientEvents {
    private HavocClientEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HavocEntities.HAVOC.get(), HavocRenderer::new);
        if (ModList.get().isLoaded("superbwarfare") && HavocEntities.HELICOPTER != null) {
            event.registerEntityRenderer(HavocEntities.HELICOPTER.get(),
                    com.tlmformation.client.helicopter.HelicopterGeoRenderer::new);
        }
    }
}
