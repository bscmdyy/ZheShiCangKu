package com.tlmformation;

import com.tlmformation.chunk.ChunkEvents;
import com.tlmformation.chunk.ChunkManager;
import com.tlmformation.combat.CombatEvents;
import com.tlmformation.command.FormationCommand;
import com.tlmformation.config.FormationConfig;
import com.tlmformation.config.HudConfig;
import com.tlmformation.config.TankConfig;

import com.tlmformation.formation.EmergencyTeleportHandler;
import com.tlmformation.formation.FormationEvents;
import com.tlmformation.friendly.FriendlyFireHandler;
import com.tlmformation.network.NetworkHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TLMFormationMod.MOD_ID)
public final class TLMFormationMod {
    public static final String MOD_ID = "tlmformation";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public TLMFormationMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModMenus.MENUS.register(modBus);
        com.tlmformation.vehicle.HavocEntities.register(modBus);
        com.tlmformation.sound.ModSounds.register(modBus);
        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, FormationConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TankConfig.SPEC,
                "tlmformation-tank-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HudConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(new FormationEvents());
        MinecraftForge.EVENT_BUS.register(new CombatEvents());
        MinecraftForge.EVENT_BUS.register(new ChunkEvents());
        MinecraftForge.EVENT_BUS.register(new FormationCommand());
        MinecraftForge.EVENT_BUS.register(new EmergencyTeleportHandler());
        MinecraftForge.EVENT_BUS.register(new FriendlyFireHandler());
        NetworkHandler.init();

        if (FMLEnvironment.dist.isDedicatedServer()) {
            LOGGER.info("TLM Formation loaded on dedicated server");
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ForgeChunkManager.setForcedChunkLoadingCallback(
                MOD_ID, ChunkManager::validateTickets));
    }
}
