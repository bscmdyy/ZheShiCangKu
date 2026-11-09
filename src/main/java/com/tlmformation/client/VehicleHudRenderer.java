package com.tlmformation.client;

import com.tlmformation.TLMFormationMod;
import com.tlmformation.config.HudConfig;
import com.tlmformation.vehicle.EntityHavoc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 右下角【战术装甲单位】状态栏（小队状态栏下方）。
 * 位置/缩放在 F9「战术面板校准」里独立调节（HudConfig.vehicle*）。
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VehicleHudRenderer {
    public static int lastX;
    public static int lastY;
    public static int lastW;
    public static int lastH;

    private static final int BASE_W = 152;
    private static final int BASE_H = 42;

    private VehicleHudRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        // 按需求删除主界面“战术装甲单位”HUD：直接不渲染
        if (true) return;
        if (HudConfig.uiHidden()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        EntityHavoc vehicle = findVehicle(minecraft);
        if (vehicle == null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        float scale = HudConfig.vehicleScale();
        int panelW = Math.round(BASE_W * scale);
        int panelH = Math.round(BASE_H * scale);

        int baseX = graphics.guiWidth() - panelW - 8;
        int baseY = graphics.guiHeight() - 24 - 8 - 4 - panelH;
        int x = baseX + HudConfig.vehicleXOffset();
        int y = baseY + HudConfig.vehicleYOffset();
        lastX = x;
        lastY = y;
        lastW = panelW;
        lastH = panelH;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);

        graphics.fill(0, 0, BASE_W, BASE_H, 0xB01E1E1E);
        graphics.fill(0, 0, BASE_W, 1, 0xFF3E6A78);
        graphics.fill(0, BASE_H - 1, BASE_W, BASE_H, 0xFF2A2A2A);

        var font = minecraft.font;
        graphics.drawString(font,
                Component.translatable("hud.tlmformation.vehicle.title"), 6, 4,
                0xFF55FFFF, false);
        graphics.drawString(font,
                Component.translatable("entity.tlmformation.havoc_vehicle"), 6, 14,
                0xFFFFFFFF, false);
        graphics.drawString(font,
                "[" + Component.translatable(vehicle.getVehicleState().translationKey())
                        .getString() + "]", 6, 24, 0xFFFFD45C, false);

        float health = Math.max(0.0F, vehicle.getHealth());
        float maxHealth = Math.max(1.0F, vehicle.getMaxHealth());
        int percent = Math.round(health / maxHealth * 100.0F);

        int barX = 62;
        int barY = 27;
        int barWidth = BASE_W - 62 - 8 - 26;
        graphics.fill(barX, barY, barX + barWidth, barY + 5, 0xFF441111);
        int filled = (int) (barWidth * (health / maxHealth));
        graphics.fill(barX, barY, barX + Math.max(0, filled), barY + 5, 0xFFDD3333);

        String percentText = percent + "%";
        graphics.drawString(font, percentText,
                BASE_W - 8 - font.width(percentText), 24, 0xFFFFFFFF, false);
        String hpText = (int) health + "/" + (int) maxHealth;
        graphics.drawString(font, hpText,
                BASE_W - 8 - font.width(hpText), 33, 0xFFAAAAAA, false);

        graphics.pose().popPose();

        // ★ F9 校准模式：半透明虚线边框 + 左上移动手柄（白）/右下缩放手柄（红）
        if (HudEditState.isEditing()) {
            drawDashedRect(graphics, x, y, panelW, panelH, 0xAAFFFFFF);
            graphics.fill(x, y, x + 8, y + 8, 0xFFFFFFFF);                       // 左上：移动
            graphics.fill(x, y, x + 8, y + 1, 0xFF55FFFF);
            graphics.fill(x, y, x + 1, y + 8, 0xFF55FFFF);
            graphics.fill(x + panelW - 9, y + panelH - 9,
                    x + panelW - 1, y + panelH - 1, 0xFFFF3333);                 // 右下：缩放
        }
    }

    /** 半透明虚线框（每 4px 一段）。 */
    private static void drawDashedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int i = 0; i < w; i += 4) {
            g.fill(x + i, y, Math.min(x + i + 2, x + w), y + 1, color);
            g.fill(x + i, y + h - 1, Math.min(x + i + 2, x + w), y + h, color);
        }
        for (int j = 0; j < h; j += 4) {
            g.fill(x, y + j, x + 1, Math.min(y + j + 2, y + h), color);
            g.fill(x + w - 1, y + j, x + w, Math.min(y + j + 2, y + h), color);
        }
    }

    @Nullable
    public static EntityHavoc findVehicle(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        List<EntityHavoc> vehicles = minecraft.level.getEntitiesOfClass(EntityHavoc.class,
                minecraft.player.getBoundingBox().inflate(96.0D),
                EntityHavoc::isAlive);
        if (vehicles.isEmpty()) {
            return null;
        }
        Player player = minecraft.player;
        return vehicles.stream()
                .filter(vehicle -> vehicle.isOwnedBy(player))
                .findFirst()
                .orElse(vehicles.get(0));
    }
}
