package com.tlmformation.menu;

import com.tlmformation.ModMenus;
import com.tlmformation.chunk.ChunkManager;
import com.tlmformation.config.FormationConfig;
import com.tlmformation.formation.FormationManager;
import com.tlmformation.formation.FormationSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class FormationMenu extends AbstractContainerMenu {
    public static final int DATA_SIZE = 8;

    private final ContainerData data;

    // Client constructor
    public FormationMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, new SimpleContainerData(DATA_SIZE));
    }

    // Server constructor
    public FormationMenu(int id, Inventory inventory, ServerPlayer player) {
        this(id, inventory, new SimpleContainerData(DATA_SIZE));
        refresh(player);
    }

    private FormationMenu(int id, Inventory inventory, ContainerData data) {
        super(ModMenus.FORMATION.get(), id);
        this.data = data;
        addDataSlots(data);
    }

    public void refresh(ServerPlayer player) {
        FormationSettings settings = FormationSettings.get(player.serverLevel());
        data.set(0, settings.isFormationEnabled(player) && FormationConfig.ENABLED.get() ? 1 : 0);
        data.set(1, settings.getExpandRange(player));
        data.set(2, FormationManager.getRosterSize(player));
        data.set(3, ChunkManager.registeredCount(player));
        data.set(4, FormationConfig.MAX_REGISTERED_MAIDS.get());
        data.set(5, FormationConfig.EMERGENCY_TELEPORT_DISTANCE.get());
        data.set(6, FormationConfig.ENABLED.get() ? 1 : 0);
        data.set(7, settings.getFollowRange(player));
    }

    public void refreshAndBroadcast(ServerPlayer player) {
        refresh(player);
        broadcastChanges();
    }

    public int value(int index) {
        return data.get(index);
    }

    @Override
    public boolean stillValid(Player player) {
        return player != null && player.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
