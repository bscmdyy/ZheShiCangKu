package com.tlmformation.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class FormationMenuProvider implements MenuProvider {
    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.tlmformation.formation");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new FormationMenu(id, inventory, (ServerPlayer) player);
    }
}
