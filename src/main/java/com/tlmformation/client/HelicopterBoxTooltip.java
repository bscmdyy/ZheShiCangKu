package com.tlmformation.client;

import com.tlmformation.TLMFormationMod;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HelicopterBoxTooltip {
    private HelicopterBoxTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        var container = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "container"));
        if (container == null || stack.getItem() != container) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("EntityType")) {
            return;
        }
        if (!tag.getString("EntityType").contains("tlmformation:helicopter")) {
            return;
        }
        event.getToolTip().add(Component.literal(
                "车万女仆：功能小队，特配UH-60直升机，在战场上穿梭运兵，总是能快人一步，你需要的我们都能送到")
                .withStyle(ChatFormatting.GRAY));
    }
}