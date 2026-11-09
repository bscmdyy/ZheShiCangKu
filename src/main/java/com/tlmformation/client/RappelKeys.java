package com.tlmformation.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** 索降按键：小键盘 1。 */
public final class RappelKeys {
    public static final String CATEGORY = "key.categories.tlmformation";
    public static final KeyMapping RAPPEL = new KeyMapping(
            "key.tlmformation.rappel",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_1,
            CATEGORY
    );

    private RappelKeys() {
    }
}
