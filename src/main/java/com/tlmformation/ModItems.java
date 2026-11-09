package com.tlmformation;

import com.tlmformation.item.EmergencyCallerItem;
import com.tlmformation.item.TacticalCarItem;
import com.tlmformation.item.TankAmmoItem;


import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TLMFormationMod.MOD_ID);

    public static final RegistryObject<Item> WALKIE_TALKIE =
            ITEMS.register("walkie_talkie",
                    () -> new EmergencyCallerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TACTICAL_CAR =
            ITEMS.register("tactical_car",
                    () -> new TacticalCarItem(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> TANK_AMMO =
            ITEMS.register("tank_ammo",
                    () -> new TankAmmoItem(new Item.Properties().stacksTo(64)));



    private ModItems() {
    }

    @SubscribeEvent
    public static void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WALKIE_TALKIE);
            event.accept(TACTICAL_CAR);
            event.accept(TANK_AMMO);
        }
    }
}
