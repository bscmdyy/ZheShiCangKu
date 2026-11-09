package com.tlmformation.client;

import com.tlmformation.network.S2CLogicData;
import net.minecraft.client.Minecraft;

public final class LogicClientData {
    private static int expandRange = 48;
    private static int followRange = 32;
    private static boolean formationEnabled = true;

    private LogicClientData() {
    }

    public static int expandRange() { return expandRange; }
    public static int followRange() { return followRange; }
    public static boolean formationEnabled() { return formationEnabled; }

    public static void update(S2CLogicData data) {
        expandRange = data.expandRange();
        followRange = data.followRange();
        formationEnabled = data.formationEnabled();
        if (Minecraft.getInstance().screen instanceof LogicTerminalScreen screen) {
            screen.refresh();
        }
    }
}
