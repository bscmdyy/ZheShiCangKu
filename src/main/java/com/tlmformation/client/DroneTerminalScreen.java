package com.tlmformation.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tlmformation.config.HudConfig;
import com.tlmformation.network.NetworkHandler;
import com.tlmformation.vehicle.EntityHavoc;
import com.tlmformation.vehicle.HavocEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/** F10 无人载具终端：3 列网格 + 滑条，点击图标部署/撤离战术小车。 */
public final class DroneTerminalScreen extends Screen {
    private static final int COLS = 3;
    private static final int ROWS = 4;
    private static final int VIEW_ROWS = 3;
    private static final int SLOT_W = 64;
    private static final int SLOT_H = 64;
    private static final int GAP = 8;
    private static final int PAD = 8;
    private static final int TITLE_H = 26;
    private static final int PREVIEW_W = 112;

    private static final int DARK = 0xA81A1A1E;
    private static final int DARK_HOVER = 0xC82A2A30;
    private static final int DARK_SELECTED = 0xD838383E;
    private static final int CYAN = 0xFF2FD8E8;
    private static final int CYAN_DIM = 0x552FD8E8;
    private static final int AMBER = 0xFFFFC24A;
    private static final int WHITE = 0xFFE9F6F8;
    private static final int GREY = 0xFF93A3AC;
    private static final int GREY_DIM = 0xFF4E5A62;
    private static final int TOTAL = COLS * ROWS;
    private static final int VIEW_H = VIEW_ROWS * SLOT_H + (VIEW_ROWS - 1) * GAP;
    private static final int CONTENT_H = ROWS * SLOT_H + (ROWS - 1) * GAP;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int gridX;
    private int gridY;
    private int scrollOffset;
    private int selected;
    private EntityHavoc preview;
    private boolean previewBroken;
    private float spin;

    public DroneTerminalScreen() {
        super(Component.translatable("gui.tlmformation.drone_terminal"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
        try {
            NetworkHandler.sendArmorAction(-1);
        } catch (Throwable ignored) {
        }
    }

    private void updateLayout() {
        int gridW = COLS * SLOT_W + (COLS - 1) * GAP;
        this.panelW = PAD * 2 + PREVIEW_W + 10 + gridW + 12;
        this.panelH = TITLE_H + Math.max(VIEW_H, 170) + 10;
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;
        this.gridX = this.panelX + PAD + PREVIEW_W + 10;
        this.gridY = this.panelY + TITLE_H;
    }

    private int maxScroll() {
        return Math.max(0, CONTENT_H - VIEW_H);
    }

    private int cellX(int i) {
        return gridX + (i % COLS) * (SLOT_W + GAP);
    }

    private int cellY(int i) {
        return gridY + (i / COLS) * (SLOT_H + GAP) - scrollOffset;
    }

    private int cellAt(double mx, double my) {
        if (mx < gridX || my < gridY || my >= gridY + VIEW_H) {
            return -1;
        }
        for (int i = 0; i < TOTAL; i++) {
            int x = cellX(i);
            int y = cellY(i);
            if (mx >= x && mx < x + SLOT_W && my >= y && my < y + SLOT_H) {
                return i;
            }
        }
        return -1;
    }

    private boolean occupied(int i) {
        return i == 0;
    }


    private int actionButtonX() {
        return panelX + PAD + 8;
    }

    private int actionButtonY() {
        return gridY + VIEW_H - 26;
    }

    private int actionButtonW() {
        return PREVIEW_W - 16;
    }

    private boolean inActionButton(double mx, double my) {
        return mx >= actionButtonX() && mx < actionButtonX() + actionButtonW()
                && my >= actionButtonY() && my < actionButtonY() + 18;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // 左侧执行按钮（唯一可触发部署/撤回的点击区域）
            if (inActionButton(mx, my) && selected == 0) {
                boolean deployed = ArmorClientData.isDeployed(0);
                NetworkHandler.sendUnitAction(0, deployed ? 5 : 4);
                playClick();
                return true;
            }
            // 右侧图标：只切换选中，绝不召唤
            int cell = cellAt(mx, my);
            if (cell >= 0) {
                this.selected = cell;
                playClick();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= gridX - 6 && mx <= panelX + panelW && my >= gridY && my <= gridY + VIEW_H) {
            this.scrollOffset = Mth.clamp(this.scrollOffset - (int) (delta * 24), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private void playClick() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9F));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == ArmorKeys.REINFORCEMENT.getKey().getValue()) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F9) {
            HudConfig.toggleUiHidden();
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        this.spin = (this.spin + 0.35F) % 360.0F;
        g.fill(0, 0, this.width, this.height, 0xB0010204);
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0x40000000);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, DARK);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, CYAN_DIM);
        g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.drone_terminal.header").getString(), panelX + PAD, panelY + 8, CYAN, false);
        g.drawString(font, "F10", panelX + panelW - PAD - font.width("F10"), panelY + 8, AMBER, false);
        drawLeft(g, partialTick);
        g.enableScissor(gridX - 2, gridY - 2, gridX + COLS * SLOT_W + (COLS - 1) * GAP + 2, gridY + VIEW_H + 2);
        for (int i = 0; i < TOTAL; i++) {
            drawCell(g, i, cellAt(mouseX, mouseY) == i, partialTick);
        }
        g.disableScissor();
        g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.drone_terminal.hint").getString(), panelX + PAD, panelY + panelH + 6, GREY_DIM, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawLeft(GuiGraphics g, float partialTick) {
        int x = panelX + PAD;
        int y = gridY;
        int h = VIEW_H;
        g.fill(x, y, x + PREVIEW_W, y + h, 0x40101820);
        g.fill(x, y, x + PREVIEW_W, y + 1, CYAN_DIM);
        boolean deployed = ArmorClientData.isDeployed(0);
        if (selected == 0) {
            createPreview();
            drawPreview(g, x + PREVIEW_W / 2.0F, y + h - 34.0F, partialTick);
            g.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.tactical_car").getString(), x + PREVIEW_W / 2, y + h - 24, WHITE);
            g.drawCenteredString(font, deployed ? net.minecraft.network.chat.Component.translatable("gui.tlmformation.deployed").getString() : net.minecraft.network.chat.Component.translatable("gui.tlmformation.not_deployed").getString(),
                    x + PREVIEW_W / 2, y + h - 12, deployed ? AMBER : GREY);
        } else {
            g.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.no_unit_selected").getString(), x + PREVIEW_W / 2, y + h / 2 - 4, GREY);
        }
    }

    private void createPreview() {
        if (preview != null || previewBroken) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                previewBroken = true;
                return;
            }
            preview = new EntityHavoc(HavocEntities.HAVOC.get(), mc.level);
            preview.setPos(0.0D, 0.0D, 0.0D);
        } catch (Throwable throwable) {
            previewBroken = true;
            preview = null;
        }
    }

    private void drawPreview(GuiGraphics g, float cx, float bottomY, float partialTick) {
        if (preview == null) {
            g.renderItem(new ItemStack(com.tlmformation.ModItems.TACTICAL_CAR.get()),
                    (int) cx - 8, (int) bottomY - 40);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var dispatcher = mc.getEntityRenderDispatcher();
        var buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = g.pose();
        pose.pushPose();
        try {
            Lighting.setupForEntityInInventory();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();
            RenderSystem.disableCull();
            float scale = PREVIEW_W * 0.20F;
            pose.translate(cx, bottomY, 100.0F);
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            dispatcher.render(preview, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, pose, buffers, 0xF000F0);
            buffers.endBatch();
        } catch (Throwable throwable) {
            preview = null;
            previewBroken = true;
        } finally {
            RenderSystem.enableCull();
            RenderSystem.disableDepthTest();
            Lighting.setupFor3DItems();
            pose.popPose();
        }
    }

    private void drawCell(GuiGraphics g, int i, boolean hovered, float partialTick) {
        int x = cellX(i);
        int y = cellY(i);
        boolean occ = occupied(i);
        boolean sel = selected == i;
        int bg = !occ ? 0xB804070A : sel ? DARK_SELECTED : hovered ? DARK_HOVER : 0xC0060A0F;
        g.fill(x, y, x + SLOT_W, y + SLOT_H, bg);
        int border = sel ? CYAN : (hovered && occ ? CYAN_DIM : GREY_DIM);
        g.fill(x, y, x + SLOT_W, y + 1, border);
        g.fill(x, y + SLOT_H - 1, x + SLOT_W, y + SLOT_H, border);
        g.fill(x, y, x + 1, y + SLOT_H, border);
        g.fill(x + SLOT_W - 1, y, x + SLOT_W, y + SLOT_H, border);
        if (!occ) {
            g.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.reserved").getString(), x + SLOT_W / 2, y + SLOT_H / 2 - 4, GREY_DIM);
            return;
        }
        createPreview();
        if (preview != null) {
            drawCellPreview(g, x + SLOT_W / 2.0F, y + SLOT_H - 20.0F, partialTick);
        } else {
            g.renderItem(new ItemStack(com.tlmformation.ModItems.TACTICAL_CAR.get()),
                    x + (SLOT_W - 16) / 2, y + 12);
        }
        g.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.tactical_car").getString(), x + SLOT_W / 2, y + SLOT_H - 11, WHITE);
    }

    private void drawCellPreview(GuiGraphics g, float cx, float bottomY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        var dispatcher = mc.getEntityRenderDispatcher();
        var buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = g.pose();
        pose.pushPose();
        try {
            Lighting.setupForEntityInInventory();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();
            RenderSystem.disableCull();
            float scale = SLOT_W * 0.22F;
            pose.translate(cx, bottomY, 100.0F);
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            dispatcher.render(preview, 0.0D, 0.0D, 0.0D, 0.0F, partialTick, pose, buffers, 0xF000F0);
            buffers.endBatch();
        } catch (Throwable throwable) {
            preview = null;
            previewBroken = true;
        } finally {
            RenderSystem.enableCull();
            RenderSystem.disableDepthTest();
            Lighting.setupFor3DItems();
            pose.popPose();
        }
    }
}
