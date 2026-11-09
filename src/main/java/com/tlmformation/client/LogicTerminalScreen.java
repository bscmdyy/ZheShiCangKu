package com.tlmformation.client;

import com.tlmformation.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LogicTerminalScreen extends Screen {
    private static final int W = 400;
    private static final int H = 270;
    private int px, py;

    public LogicTerminalScreen() {
        super(Component.translatable("gui.tlmformation.logic_terminal"));
    }

    @Override
    protected void init() {
        super.init();
        px = (width - W) / 2;
        py = (height - H) / 2;
        NetworkHandler.sendAction(9); // request current values
    }

    public void refresh() {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        px = (width - W) / 2;
        py = (height - H) / 2;

        g.fill(px, py, px + W, py + H, 0xC0202020);
        g.fill(px, py, px + W, py + 24, 0xC0181818);
        g.drawCenteredString(font, title, px + W / 2, py + 8, 0xFFFFFFFF);

        int x = px + 18;
        int w = W - 36;

        drawSetting(g, x, py + 40, w, "战斗扩圈", "ExpandRange",
                LogicClientData.expandRange() + " 格", mouseX, mouseY,
                "以玩家自身为中心展开战斗的范围大小。在此范围内，女仆将自动索敌并展开追击。");
        drawSetting(g, x, py + 105, w, "跟随半径", "FollowRange",
                LogicClientData.followRange() + " 格", mouseX, mouseY,
                "设定触发女仆跟随的激活距离。该半径仅在非战斗状态下生效，不会与战斗扩圈机制产生冲突。");

        int ty = py + 170;
        g.fill(x, ty, x + w, ty + 78, 0x70101010);
        outline(g, x, ty, w, 78, 0xFF555555);
        g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.formation_toggle").getString(), x + 8, ty + 8, 0xFFFFFFFF, false);
        g.drawString(font, "Formation", x + 92, ty + 8, 0xFF777777, false);

        boolean on = LogicClientData.formationEnabled();
        int toggleX = x + w - 58;
        fillButton(g, toggleX, ty + 5, 48, 16, 0xFF2E4A42, mouseX, mouseY);
        g.drawCenteredString(font, on ? net.minecraft.network.chat.Component.translatable("gui.tlmformation.on").getString() : net.minecraft.network.chat.Component.translatable("gui.tlmformation.off").getString(), toggleX + 24, ty + 9,
                on ? 0xFF55FFCC : 0xFFFF7777);

        drawWrapped(g, "开启后，小队将启用高级战术跟随算法。女仆会自动分配战术槽位"
                + "进行前方开路或侧翼护卫，形成包围阵型，避免队伍在移动时挤作一团，"
                + "实现更富逻辑性的寻路与移动优化。",
                x + 8, ty + 28, w - 16, 0xFFAAAAAA);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawSetting(GuiGraphics g, int x, int y, int w, String name,
                             String en, String value, int mouseX, int mouseY,
                             String desc) {
        g.fill(x, y, x + w, y + 58, 0x70101010);
        outline(g, x, y, w, 58, 0xFF555555);
        g.drawString(font, name, x + 8, y + 8, 0xFFFFFFFF, false);
        g.drawString(font, en, x + 72, y + 8, 0xFF777777, false);

        int minusX = x + w - 74;
        int plusX = x + w - 30;
        fillButton(g, minusX, y + 5, 18, 16, 0xFF303A44, mouseX, mouseY);
        g.drawCenteredString(font, "-", minusX + 9, y + 9, 0xFFFFFFFF);
        g.drawCenteredString(font, value, x + w - 42, y + 9, 0xFFFFD45C);
        fillButton(g, plusX, y + 5, 18, 16, 0xFF303A44, mouseX, mouseY);
        g.drawCenteredString(font, "+", plusX + 9, y + 9, 0xFFFFFFFF);

        drawWrapped(g, desc, x + 8, y + 28, w - 80, 0xFFAAAAAA);
    }

    private void drawWrapped(GuiGraphics g, String text, int x, int y,
                             int maxWidth, int color) {
        StringBuilder line = new StringBuilder();
        int yy = y;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (font.width(line.toString() + c) > maxWidth && line.length() > 0) {
                g.drawString(font, line.toString(), x, yy, color, false);
                line.setLength(0);
                yy += 10;
            }
            line.append(c);
        }
        if (line.length() > 0) g.drawString(font, line.toString(), x, yy, color, false);
    }

    private void fillButton(GuiGraphics g, int x, int y, int w, int h, int normal,
                            int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        g.fill(x, y, x + w, y + h, hover ? 0xFF4A5A6A : normal);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = px + 18;
        int w = W - 36;
        int minusX = x + w - 74;
        int plusX = x + w - 30;

        if (in(mx, my, minusX, py + 45, 18, 16)) {
            NetworkHandler.sendAction(1);
            return true;
        }
        if (in(mx, my, plusX, py + 45, 18, 16)) {
            NetworkHandler.sendAction(2);
            return true;
        }
        if (in(mx, my, minusX, py + 110, 18, 16)) {
            NetworkHandler.sendAction(6);
            return true;
        }
        if (in(mx, my, plusX, py + 110, 18, 16)) {
            NetworkHandler.sendAction(7);
            return true;
        }
        int ty = py + 170;
        if (in(mx, my, x + w - 58, ty + 5, 48, 16)) {
            NetworkHandler.sendAction(0);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
