package com.tlmformation.client;

public final class HudEditState {
    private static boolean editing;

    private HudEditState() {
    }

    public static boolean isEditing() { return editing; }
    public static void setEditing(boolean value) { editing = value; }
}
