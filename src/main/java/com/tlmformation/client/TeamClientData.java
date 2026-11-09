package com.tlmformation.client;

import com.tlmformation.network.S2CTeamData;

public final class TeamClientData {
    private static S2CTeamData current;

    private TeamClientData() {
    }

    public static void update(S2CTeamData data) {
        current = data;
    }

    public static S2CTeamData current() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
