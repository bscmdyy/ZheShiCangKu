package com.tlmformation;

import com.tlmformation.menu.FormationMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, TLMFormationMod.MOD_ID);

    public static final RegistryObject<MenuType<FormationMenu>> FORMATION =
            MENUS.register("formation", () -> IForgeMenuType.create(FormationMenu::new));

    private ModMenus() {
    }
}
