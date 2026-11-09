package com.tlmformation.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tlmformation.network.NetworkHandler;
import com.tlmformation.tactical.TacticalCommand;
import com.tlmformation.client.GrandModeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * Pure hardware-drag radial wheel. The game cursor stays hidden while this
 * screen is open; MouseHandlerMixin redirects raw mouse deltas here and cancels
 * the vanilla camera turn.
 */
public final class TacticalWheelScreen extends Screen {
    private static final double MAX_DRAG = 92.0D;
    private static final double DEAD_ZONE = 7.0D;
    private static final float SECTOR_GAP = 3.0F;
    private static final int DIM_COLOR = 0x44000000;
    private static final int RING_COLOR = 0xAA101014;
    private static final int RING_SELECTED = 0xE6D9A521;
    private static final TacticalCommand[] NORMAL = {
            TacticalCommand.MARK, TacticalCommand.LOCK, TacticalCommand.GUARD,
            TacticalCommand.FOLLOW, TacticalCommand.HOLD, TacticalCommand.RETREAT
    };
    private TacticalCommand[] commands;
    private double dragX;
    private double dragY;
    private int selected = TacticalCommand.FOLLOW.ordinal();
    private boolean confirmed;

    public TacticalWheelScreen() {
        super(Component.translatable("screen.tlmformation.tactical_wheel"));
    }

    public static boolean isActive() {
        return Minecraft.getInstance().screen instanceof TacticalWheelScreen;
    }

    public static void addRawDelta(double dx, double dy) {
        if (Minecraft.getInstance().screen instanceof TacticalWheelScreen wheel) {
            wheel.accumulate(dx, dy);
        }
    }

    private void accumulate(double dx, double dy) {
        dragX += dx;
        dragY += dy;
        double length = Math.sqrt(dragX * dragX + dragY * dragY);
        if (length > MAX_DRAG) {
            dragX = dragX / length * MAX_DRAG;
            dragY = dragY / length * MAX_DRAG;
            length = MAX_DRAG;
        }
        if (length < DEAD_ZONE) {
            return;
        }
        double angle = Math.toDegrees(Math.atan2(dragY, dragX));
        int best = selected;
        double bestDiff = 180.0D;
        for (int i = 0; i < commands.length; i++) {
            double diff = Math.abs(wrapDegrees(angle - angleFor(i)));
            if (diff < sectorHalf() - SECTOR_GAP && diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        selected = best;
    }

    private static double wrapDegrees(double degrees) {
        double value = degrees % 360.0D;
        if (value > 180.0D) {
            value -= 360.0D;
        }
        if (value < -180.0D) {
            value += 360.0D;
        }
        return value;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        // Keep the system cursor invisible. Mouse movement is read from
        // MouseHandler's accumulated deltas and never reaches the camera.
        setCursorHidden(true);
        commands = NORMAL;
        selected = indexOf(TacticalCommand.FOLLOW);
        dragX = 0;
        dragY = 0;
    }

    @Override
    public void tick() {
        // Closing is driven by keyReleased; KeyMapping.isDown can be reset
        // by the cursor-mode change that happens when the wheel opens.
    }

    private void setCursorHidden(boolean hidden) {
        if (minecraft == null) {
            return;
        }
        long window = minecraft.getWindow().getWindow();
        double centerX = minecraft.getWindow().getScreenWidth() / 2.0D;
        double centerY = minecraft.getWindow().getScreenHeight() / 2.0D;
        InputConstants.grabOrReleaseMouse(window,
                hidden ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL,
                centerX, centerY);
    }

    @Override
    public void removed() {
        if (minecraft != null) {
            setCursorHidden(false);
        }
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            setCursorHidden(false);
        }
        super.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            confirmSelection();
            return true;
        }
        // Consume all clicks so attacking/using the held item is impossible
        // while the wheel is open.
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == TacticalKeys.WHEEL.getKey().getValue()) {
            closeWheel();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeWheel();
            return true;
        }
        if (keyCode == TacticalKeys.WHEEL.getKey().getValue()) {
            // Key auto-repeat must not close the wheel; release is handled by tick().
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void closeWheel() {
        confirmed = true;
        TacticalClientEvents.suppressReopenUntilRelease();
        if (minecraft != null) {
            setCursorHidden(false);
            minecraft.setScreen(null);
        }
    }

    private void confirmSelection() {
        if (confirmed) {
            return;
        }
        confirmed = true;
        TacticalClientEvents.suppressReopenUntilRelease();
        int index = selected;
        if (index < 0 || index >= commands.length) {
            index = TacticalCommand.FOLLOW.ordinal();
        }
        NetworkHandler.sendTacticalCommand(commands[index], targetFor(commands[index]));
        if (minecraft != null) {
            setCursorHidden(false);
            minecraft.setScreen(null);
        }
    }

    @Nullable
    private BlockPos targetFor(TacticalCommand command) {
        if (command != TacticalCommand.MARK || minecraft == null
                || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        // Vanilla crosshair result is authoritative and never jitters.
        if (minecraft.hitResult instanceof BlockHitResult crosshair
                && crosshair.getType() == HitResult.Type.BLOCK) {
            return crosshair.getBlockPos();
        }

        // MISS (sky/air): look 10 blocks ahead and ray down to the floor.
        Vec3 ahead = minecraft.player.getEyePosition()
                .add(minecraft.player.getLookAngle().scale(10.0D));
        return groundBelow(minecraft.level,
                BlockPos.containing(ahead), 64);
    }

    @Nullable
    private static BlockPos groundBelow(Level level, BlockPos start, int maxDown) {
        for (int down = 0; down <= maxDown; down++) {
            BlockPos floor = start.below(down);
            if (!level.isLoaded(floor)) {
                continue;
            }
            if (!level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                    && level.getBlockState(floor.above()).getCollisionShape(
                    level, floor.above()).isEmpty()) {
                return floor.above();
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, DIM_COLOR);

        float cx = width / 2.0F;
        float cy = height / 2.0F;
        float outer = Math.max(86.0F, Math.min(122.0F, Math.min(width, height) * 0.22F));
        float inner = outer * 0.42F;

        drawRing(graphics, cx, cy, inner, outer);
        drawBorders(graphics, cx, cy, inner, outer);
        drawCenter(graphics, cx, cy, inner);
        drawSectors(graphics, cx, cy, inner, outer);
        drawCenterText(graphics, cx, cy);
    }

    private void drawRing(GuiGraphics graphics, float cx, float cy,
                          float inner, float outer) {
        for (int i = 0; i < commands.length; i++) {
            float half = sectorHalf();
            float start = angleFor(i) - half + SECTOR_GAP / 2.0F;
            float end = angleFor(i) + half - SECTOR_GAP / 2.0F;
            int color = i == selected ? RING_SELECTED : RING_COLOR;
            drawRingSector(graphics, cx, cy, inner, outer, start, end, color);
        }
    }

    private void drawRingSector(GuiGraphics graphics, float cx, float cy,
                                float inner, float outer, float startDegrees,
                                float endDegrees, int color) {
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);

        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = 14;
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(startDegrees
                    + (endDegrees - startDegrees) * i / steps);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            buffer.vertex(matrix, cx + cos * outer, cy + sin * outer, 0.0F)
                    .color(red, green, blue, alpha).endVertex();
            buffer.vertex(matrix, cx + cos * inner, cy + sin * inner, 0.0F)
                    .color(red, green, blue, alpha).endVertex();
        }
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void drawBorders(GuiGraphics graphics, float cx, float cy,
                             float inner, float outer) {
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(1.5F);
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < commands.length; i++) {
            double angle = Math.toRadians(angleFor(i)
                    - sectorHalf() + SECTOR_GAP / 2.0D);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            line(buffer, matrix, cx + cos * inner, cy + sin * inner,
                    cx + cos * outer, cy + sin * outer);
        }
        for (int i = 0; i < 64; i++) {
            double a1 = Math.PI * 2.0D * i / 64.0D;
            double a2 = Math.PI * 2.0D * (i + 1) / 64.0D;
            line(buffer, matrix,
                    cx + (float) Math.cos(a1) * outer, cy + (float) Math.sin(a1) * outer,
                    cx + (float) Math.cos(a2) * outer, cy + (float) Math.sin(a2) * outer);
        }
        Tesselator.getInstance().end();
        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
    }

    private void line(BufferBuilder buffer, Matrix4f matrix,
                      float x1, float y1, float x2, float y2) {
        float a = 0.78F;
        buffer.vertex(matrix, x1, y1, 0).color(1.0F, 1.0F, 1.0F, a).endVertex();
        buffer.vertex(matrix, x2, y2, 0).color(1.0F, 1.0F, 1.0F, a).endVertex();
    }

    private void drawCenter(GuiGraphics graphics, float cx, float cy, float inner) {
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, cx, cy, 0).color(0.03F, 0.03F, 0.04F, 0.88F).endVertex();
        for (int i = 0; i <= 36; i++) {
            double angle = Math.PI * 2.0D * i / 36.0D;
            buffer.vertex(matrix, cx + (float) Math.cos(angle) * (inner - 4.0F),
                    cy + (float) Math.sin(angle) * (inner - 4.0F), 0)
                    .color(0.03F, 0.03F, 0.04F, 0.88F).endVertex();
        }
        Tesselator.getInstance().end();
        RenderSystem.disableBlend();
    }

    private void drawSectors(GuiGraphics graphics, float cx, float cy,
                             float inner, float outer) {
        float iconRadius = inner + (outer - inner) * 0.58F;
        float labelRadius = outer + 13.0F;
        for (int i = 0; i < commands.length; i++) {
            TacticalCommand command = commands[i];
            double angle = Math.toRadians(angleFor(i));
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float iconX = cx + cos * iconRadius;
            float iconY = cy + sin * iconRadius;

            if (command.texture() != null) {
                // Center the 64x64 texture on the sector point. The pose
                // push/pop and scale guarantee the icon is not anchored at its
                // top-left corner.
                graphics.pose().pushPose();
                graphics.pose().translate(iconX, iconY, 0.0F);
                graphics.pose().scale(0.375F, 0.375F, 1.0F);
                graphics.blit(command.texture(), -32, -32, 0, 0, 64, 64, 64, 64);
                graphics.pose().popPose();
            }

            float labelX = cx + cos * labelRadius;
            float labelY = cy + sin * labelRadius;
            graphics.drawCenteredString(font, command.displayName(),
                    (int) labelX, (int) labelY, 0xFFF0F0F0);
        }
    }

    private void drawCenterText(GuiGraphics graphics, float cx, float cy) {
        int index = Math.max(0, Math.min(selected, commands.length - 1));
        graphics.drawCenteredString(font, commands[index].displayName(),
                (int) cx, (int) cy - 5, 0xFFFFD45C);
        graphics.drawCenteredString(font, Component.translatable(
                        "screen.tlmformation.tactical_wheel.confirm"),
                (int) cx, (int) cy + 8, 0xFFD8D8D8);
    }


    private float angleFor(int index) {
        return commands[index].angleDegrees();
    }

    private float sectorHalf() {
        return 30.0F;
    }

    private int indexOf(TacticalCommand command) {
        for (int i = 0; i < commands.length; i++) {
            if (commands[i] == command) return i;
        }
        return 0;
    }
}
