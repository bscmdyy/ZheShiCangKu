package com.tlmformation.client;

public final class GrandModeClient {
    private static boolean active;

    private GrandModeClient() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }
}
