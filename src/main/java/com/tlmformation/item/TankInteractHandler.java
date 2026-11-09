package com.tlmformation.item;

import com.tlmformation.ModItems;
import com.tlmformation.vehicle.EntityHavoc;
import com.tlmformation.vehicle.TankData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 右键已召唤的战术小车实体：装填无人战车子弹。 */
@Mod.EventBusSubscriber(modid = com.tlmformation.TLMFormationMod.MOD_ID)
public final class TankInteractHandler {
    private TankInteractHandler() {
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof EntityHavoc vehicle)
                || !(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!vehicle.isOwnedBy(player)) {
            return;
        }
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.TANK_AMMO.get())) {
                count += stack.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        if (count <= 0) {
            player.displayClientMessage(Component.literal(
                    "§e没有可装填的“无人战车子弹”").withStyle(ChatFormatting.GRAY), true);
            event.setCanceled(true);
            return;
        }
        TankData.setStock(player, TankData.stock(player) + count);
        TankData.addVehicleAmmo(vehicle, count);
        com.tlmformation.vehicle.ArmorReinforcementManager.sync(player);
        player.displayClientMessage(Component.literal(
                "已装填 " + count + " 发无人战车子弹").withStyle(ChatFormatting.GREEN), true);
        event.setCanceled(true);
    }
}
