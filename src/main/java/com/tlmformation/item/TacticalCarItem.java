package com.tlmformation.item;

import com.tlmformation.config.TankConfig;
import com.tlmformation.vehicle.TankData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 战术小车（解锁 F10 召唤）。 */
public final class TacticalCarItem extends Item {
    public TacticalCarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            if (TankData.isUnlocked(server)) {
                if (!allowReunlock()) {
                    server.displayClientMessage(Component.literal(
                            "已加入 F10 召唤增援项目").withStyle(ChatFormatting.GRAY), true);
                    return InteractionResultHolder.success(stack);
                }
            } else {
                TankData.setUnlocked(server);
            }
            server.displayClientMessage(Component.literal(
                    "已加入 F10 召唤增援项目").withStyle(ChatFormatting.GREEN), false);
            if (consumeItem()) {
                if (!server.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            com.tlmformation.vehicle.ArmorReinforcementManager.sync(server);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static boolean consumeItem() {
        try {
            return TankConfig.UNLOCK_CONSUMES_ITEM.get();
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean allowReunlock() {
        try {
            return TankConfig.ALLOW_REUNLOCK.get();
        } catch (Exception e) {
            return false;
        }
    }
}
