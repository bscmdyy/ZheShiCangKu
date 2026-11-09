package com.tlmformation.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class TeamKeys {
    public static final KeyMapping TEAM = new KeyMapping(
            "key.tlmformation.team_config",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "key.categories.tlmformation"
    );

    private TeamKeys() {
    }
}
