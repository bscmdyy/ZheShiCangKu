package com.tlmformation.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * F10 — 装甲增援轮盘.
 */
public final class ArmorKeys {
    public static final String CATEGORY = "key.categories.tlmformation";
    public static final KeyMapping REINFORCEMENT = new KeyMapping(
            "key.tlmformation.armor_reinforcement",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            CATEGORY
    );

    private ArmorKeys() {
    }
}
