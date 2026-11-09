package com.tlmformation.logistics;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe queue shared by server-side rescue and client renderer. */
public final class KillFeedQueue {
    public record Entry(String left, String right, ItemStack icon,
                        int leftColor, int rightColor, long created) {
    }

    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private KillFeedQueue() {
    }

    public static void addKill(String killer, int killerColor, String victim,
                               ItemStack icon) {
        ENTRIES.add(0, new Entry(killer, victim, icon,
                killerColor, 0xFFFF5555, System.currentTimeMillis()));
    }

    public static void addRescue(String maidName) {
        ENTRIES.add(0, new Entry("[后勤] 成功保住了", maidName,
                new ItemStack(Items.GOLDEN_APPLE),
                0xFFFFD45C, 0xFF55FFFF, System.currentTimeMillis()));
    }

    public static void removeExpired(long now) {
        ENTRIES.removeIf(e -> now - e.created() > 5000L);
    }

    public static List<Entry> entries() {
        return new ArrayList<>(ENTRIES);
    }
}
