package com.tlmformation.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Flat, semi-transparent 2D tactical grid. Draws 1-pixel lines only; it never
 * creates 3D geometry, so it cannot produce a missing-texture cube.
 */
public final class TacticalGridRenderer {
    private TacticalGridRenderer() {
    }

    public static void draw(GuiGraphics graphics, int x, int y,
                            int width, int height, int step, int color) {
        if (step <= 0 || width <= 0 || height <= 0) {
            return;
        }
        for (int i = x + step; i < x + width; i += step) {
            graphics.fill(i, y, i + 1, y + height, color);
        }
        for (int j = y + step; j < y + height; j += step) {
            graphics.fill(x, j, x + width, j + 1, color);
        }
    }
}
