package com.tlmformation;

import com.tlmformation.item.TankAmmoItem;
import com.tlmformation.item.TacticalCarItem;
import com.tlmformation.config.TankConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 本模组独立创造模式标签页。 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
                    TLMFormationMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> TACTICAL_TAB = TABS.register("tactical_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tlmformation.tactical"))
                    .icon(() -> new ItemStack(ModItems.WALKIE_TALKIE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.TACTICAL_CAR.get());
                        output.accept(ModItems.WALKIE_TALKIE.get());
                        output.accept(ModItems.TANK_AMMO.get());
                        addHelicopterContainer(output);
                    })
                    .build());

    private ModCreativeTabs() {
    }

    /**
     * 直升机载具箱：必须用卓越前线的 {@code ContainerBlockItem.createInstance(EntityType)} 生成，
     * 这样才会带上 {@code EntityType}（以及卓前后续需要的默认数据）NBT。
     * 之前直接 {@code output.accept(container)} 加的是白板物品，所以打开是空的、无法释放。
     */
    private static void addHelicopterContainer(CreativeModeTab.Output output) {
        if (!ModList.get().isLoaded("superbwarfare")) {
            return;
        }
        EntityType<?> helicopterType = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation(TLMFormationMod.MOD_ID, "helicopter"));
        if (helicopterType == null) {
            return;
        }
        output.accept(com.atsuishio.superbwarfare.item.container.ContainerBlockItem.Companion
                .createInstance(helicopterType));
    }
}
