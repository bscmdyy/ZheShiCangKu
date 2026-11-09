package com.tlmformation.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class TacticalKeys {
    public static final String CATEGORY = "key.categories.tlmformation";
    public static final KeyMapping WHEEL = new KeyMapping(
            "key.tlmformation.tactical_wheel",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY
    );

    private TacticalKeys() {
    }
}
