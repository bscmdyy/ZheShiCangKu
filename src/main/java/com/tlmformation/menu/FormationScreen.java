package com.tlmformation.menu;

import com.tlmformation.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class FormationScreen extends AbstractContainerScreen<FormationMenu> {
    public FormationScreen(FormationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageHeight = 190;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        addRenderableWidget(Button.builder(Component.translatable("gui.tlmformation.formation_toggle"),
                        button -> NetworkHandler.sendAction(0))
                .bounds(x + 8, y + 20, 75, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-10"),
                        button -> NetworkHandler.sendAction(1))
                .bounds(x + 88, y + 20, 35, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+10"),
                        button -> NetworkHandler.sendAction(2))
                .bounds(x + 128, y + 20, 35, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.tlmformation.register_nearest"),
                        button -> NetworkHandler.sendAction(3))
                .bounds(x + 8, y + 44, 75, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.tlmformation.unregister"),
                        button -> NetworkHandler.sendAction(4))
                .bounds(x + 88, y + 44, 75, 20).build());

        addRenderableWidget(Button.builder(Component.literal("-8"),
                        button -> NetworkHandler.sendAction(6))
                .bounds(x + 8, y + 68, 35, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+8"),
                        button -> NetworkHandler.sendAction(7))
                .bounds(x + 48, y + 68, 35, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.tlmformation.recall"),
                        button -> NetworkHandler.sendAction(5))
                .bounds(x + 88, y + 68, 75, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(this.leftPos, this.topPos,
                this.leftPos + this.imageWidth, this.topPos + this.imageHeight,
                0xE0101010);
        graphics.fill(this.leftPos + 2, this.topPos + 2,
                this.leftPos + this.imageWidth - 2, this.topPos + this.imageHeight - 2,
                0xE0202020);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFF);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int x = this.leftPos + 8;
        int y = this.topPos + 96;
        int enabled = this.menu.value(0);
        int expand = this.menu.value(1);
        int roster = this.menu.value(2);
        int registered = this.menu.value(3);
        int max = this.menu.value(4);
        int emergency = this.menu.value(5);
        int followRange = this.menu.value(7);

        graphics.drawString(this.font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.formation_state", net.minecraft.network.chat.Component.translatable(enabled == 1 ? "gui.tlmformation.on" : "gui.tlmformation.off")).getString(), x, y, 0xFFFFFF);
        graphics.drawString(this.font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.expand", expand).getString(), x, y + 12, 0xFFFFFF);
        graphics.drawString(this.font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.follow_range", followRange).getString(), x, y + 24, 0xFFD080);
        graphics.drawString(this.font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.roster", roster).getString(), x, y + 36, 0xFFFFFF);
        graphics.drawString(this.font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.chunk_loading", registered + "/" + max).getString(), x, y + 48, 0xFFFFFF);
        graphics.drawString(this.font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.emergency_teleport", emergency).getString(), x, y + 60, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
