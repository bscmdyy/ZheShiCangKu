package com.tlmformation.client;

import com.tlmformation.config.HudConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * F9「战术面板校准」：可分别校准 小队 HUD 与 战术装甲单位 HUD 的
 * X/Y 偏移与缩放，实时生效，关闭时写入配置。
 */
public final class HudEditScreen extends Screen {
    private boolean draggingPosition;
    private boolean draggingScale;
    private boolean editingVehicleHud;
    private double startMouseX;
    private double startMouseY;
    private int startOffsetX;
    private int startOffsetY;
    private float startScale;

    public HudEditScreen() {
        super(Component.translatable("gui.tlmformation.hud_edit"));
        HudEditState.setEditing(true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // Transparent: keep world/HUD visible while editing.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;

        // 顶部选择：小队 HUD / 战术装甲单位 HUD
        int tabW = 120;
        int tabY = cy - 70;
        drawTab(g, cx - tabW - 4, tabY, tabW, "小队 HUD", !editingVehicleHud);
        drawTab(g, cx + 4, tabY, tabW, "战术装甲单位 HUD", editingVehicleHud);

        // 中央：重置 + 唯一“隐藏/显示”按钮
        int bh = 20;
        int resetW = 190;
        int hideW = 90;
        int gap = 8;
        int totalW = resetW + gap + hideW;
        int rx = cx - totalW / 2;
        int hideX = rx + resetW + gap;
        int top = cy - bh / 2;
        drawButton(g, rx, top, resetW, bh, "重置 HUD 位置与大小", 0xFFFFD45C);
        drawButton(g, hideX, top, hideW, bh,
                HudConfig.uiHidden() ? "显示" : "隐藏", 0xFF55FFFF);

        String hint = editingVehicleHud
                ? "拖左上角方块调 XY · 拖右下角方块调缩放（战车 HUD）"
                : "拖左上角方块调 XY · 拖右下角方块调缩放（小队 HUD）";
        g.drawCenteredString(font, hint, cx, cy + 34, 0xFFAAAAAA);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label, int color) {
        g.fill(x, y, x + w, y + h, 0xE0181818);
        g.fill(x, y, x + w, y + 1, 0xFFAAAAAA);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFAAAAAA);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, color);
    }

    private void drawTab(GuiGraphics g, int x, int y, int w, String label, boolean selected) {
        int color = selected ? 0xE02A3C46 : 0xC0181818;
        g.fill(x, y, x + w, y + 18, color);
        g.fill(x, y, x + w, y + 1, selected ? 0xFF55FFFF : 0xFF555555);
        g.drawCenteredString(font, label, x + w / 2, y + 5,
                selected ? 0xFFFFFFFF : 0xFFAAAAAA);
    }

    private int activeHudX() {
        return editingVehicleHud ? VehicleHudRenderer.lastX : TeamHudRenderer.lastX;
    }

    private int activeHudY() {
        return editingVehicleHud ? VehicleHudRenderer.lastY : TeamHudRenderer.lastY;
    }

    private int activeHudW() {
        return editingVehicleHud ? VehicleHudRenderer.lastW : TeamHudRenderer.lastW;
    }

    private int activeHudH() {
        return editingVehicleHud ? VehicleHudRenderer.lastH : TeamHudRenderer.lastH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cx = width / 2;
        int cy = height / 2;
        if (button == 0) {
            int tabW = 120;
            int tabY = cy - 70;
            if (mx >= cx - tabW - 4 && mx <= cx - 4 && my >= tabY && my <= tabY + 18) {
                editingVehicleHud = false;
                return true;
            }
            if (mx >= cx + 4 && mx <= cx + 4 + tabW && my >= tabY && my <= tabY + 18) {
                editingVehicleHud = true;
                return true;
            }
        }
        int bh = 20;
        int resetW = 190;
        int hideW = 90;
        int gap = 8;
        int totalW = resetW + gap + hideW;
        int rx = cx - totalW / 2;
        int hideX = rx + resetW + gap;
        int top = cy - bh / 2;
        if (button == 0 && mx >= rx && mx <= rx + resetW && my >= top && my <= top + bh) {
            HudConfig.reset();
            return true;
        }
        if (button == 0 && mx >= hideX && mx <= hideX + hideW && my >= top && my <= top + bh) {
            HudConfig.toggleUiHidden();
            HudConfig.commit();
            return true;
        }

        int hx = activeHudX();
        int hy = activeHudY();
        int hw = activeHudW();
        int hh = activeHudH();
        int handle = editingVehicleHud ? 10 : 22;   // ★ 战车 HUD 只响应 10x10 角落手柄

        if (button == 0 && mx >= hx && mx <= hx + handle
                && my >= hy && my <= hy + handle) {
            draggingPosition = true;
            startMouseX = mx;
            startMouseY = my;
            startOffsetX = editingVehicleHud ? HudConfig.vehicleXOffset() : HudConfig.xOffset();
            startOffsetY = editingVehicleHud ? HudConfig.vehicleYOffset() : HudConfig.yOffset();
            return true;
        }
        if (button == 0 && mx >= hx + hw - handle && mx <= hx + hw
                && my >= hy + hh - handle && my <= hy + hh) {
            draggingScale = true;
            startMouseX = mx;
            startScale = editingVehicleHud ? HudConfig.vehicleScale() : HudConfig.scale();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button,
                                double dragX, double dragY) {
        if (draggingPosition) {
            int ox = startOffsetX + (int) Math.round(mx - startMouseX);
            int oy = startOffsetY + (int) Math.round(my - startMouseY);
            if (editingVehicleHud) {
                HudConfig.setVehicleOffsets(ox, oy);
            } else {
                HudConfig.setOffsets(ox, oy);
            }
            clamp();
            return true;
        }
        if (draggingScale) {
            float scale = startScale + (float) ((mx - startMouseX) / 220.0D);
            if (editingVehicleHud) {
                HudConfig.setVehicleScale(scale);
            } else {
                HudConfig.setScale(scale);
            }
            clamp();
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingPosition = false;
        draggingScale = false;
        return super.mouseReleased(mx, my, button);
    }

    private void clamp() {
        if (editingVehicleHud) {
            float scale = HudConfig.vehicleScale();
            int panelW = Math.round(152 * scale);
            int panelH = Math.round(42 * scale);
            int baseX = width - panelW - 8;
            int baseY = height - 24 - 8 - 4 - panelH;
            int minX = -panelW * 3 / 4;
            int maxX = width - panelW / 4;
            int minY = -panelH * 3 / 4;
            int maxY = height - panelH / 4;
            int x = Math.max(minX, Math.min(maxX, baseX + HudConfig.vehicleXOffset()));
            int y = Math.max(minY, Math.min(maxY, baseY + HudConfig.vehicleYOffset()));
            HudConfig.setVehicleOffsets(x - baseX, y - baseY);
            return;
        }
        float scale = HudConfig.scale();
        int panelW = Math.round(220 * scale);
        int panelH = Math.round(146 * scale);
        int baseX = width - panelW - 8;
        int baseY = height - panelH - 8;
        int x = baseX + HudConfig.xOffset();
        int y = baseY + HudConfig.yOffset();
        int minX = -panelW * 3 / 4;
        int maxX = width - panelW / 4;
        int minY = -panelH * 3 / 4;
        int maxY = height - panelH / 4;
        x = Math.max(minX, Math.min(maxX, x));
        y = Math.max(minY, Math.min(maxY, y));
        HudConfig.setOffsets(x - baseX, y - baseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        HudConfig.commit();
        HudEditState.setEditing(false);
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }
}
