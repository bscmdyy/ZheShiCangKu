package com.tlmformation.client;

/**
 * Client-side mirror of the server "armored reinforcement" state.
 * Slot 0 is the HAVOC / 沉米级无人战车; slots 1..3 are reserved.
 */
public final class ArmorClientData {
    public static final int SLOTS = 4;
    private static final boolean[] DEPLOYED = new boolean[SLOTS];
    private static int deployedMask;
    private static int followDistance = 20;
    private static boolean unlocked;
    private static int ammo;

    private ArmorClientData() {
    }

    public static boolean isDeployed(int slot) {
        return slot >= 0 && slot < SLOTS && DEPLOYED[slot];
    }

    public static int deployedMask() {
        return deployedMask;
    }

    public static int followDistance() {
        return followDistance;
    }

    public static boolean unlocked() {
        return unlocked;
    }

    public static int ammo() {
        return ammo;
    }

    public static void update(int mask, int distance) {
        update(mask, distance, false, 0);
    }

    public static void update(int mask, int distance, boolean isUnlocked, int ammoCount) {
        deployedMask = mask;
        unlocked = isUnlocked;
        ammo = Math.max(0, ammoCount);
        followDistance = Math.max(10, Math.min(50, distance));
        for (int i = 0; i < SLOTS; i++) {
            DEPLOYED[i] = (mask & (1 << i)) != 0;
        }
    }

    public static void clear() {
        update(0, 20);
    }
}
