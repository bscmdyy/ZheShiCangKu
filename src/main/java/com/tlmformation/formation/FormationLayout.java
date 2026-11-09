package com.tlmformation.formation;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Formation offsets are in "local" coordinates:
 * right > 0 is the player's right side, back > 0 is behind the player,
 * back < 0 is in front of the player.
 */
public final class FormationLayout {
    public record SlotOffset(double right, double back) {
        public SlotOffset scaled(double scale) {
            return new SlotOffset(right * scale, back * scale);
        }
    }

    private static final SlotOffset[] TWO = {
            new SlotOffset(-1.8, 2.2),
            new SlotOffset(1.8, 2.2)
    };

    private static final SlotOffset[] THREE = {
            new SlotOffset(-1.8, 2.2),
            new SlotOffset(1.8, 2.2),
            new SlotOffset(0.0, 4.2)
    };

    private FormationLayout() {
    }

    /**
     * Slot table:
     *
     * n=2: RL, RR
     * n=3: RL, RR, C
     * n=4: RL, RR, FL, FR
     * n=5: RL, RR, FL, FR, C
     * n>=6: RL0, RR0, FL, FR, RL1, RR1, ... and a center tail for odd counts.
     *
     * Front slots use a negative "back" value, so they stand ahead of the
     * predicted anchor instead of behind it.
     */
    public static SlotOffset slot(int index, int count) {
        return slot(index, count, 2);
    }

    /**
     * Same formation table, with a configurable front guard quota.
     * Grand assembly passes 4; ordinary squads keep the original 2.
     */
    public static SlotOffset slot(int index, int count, int frontQuota) {
        if (frontQuota <= 2 || count <= 3) {
            return switch (count) {
                case 1 -> new SlotOffset(0.0, 2.2);
                case 2 -> TWO[clamp(index, 2)];
                case 3 -> THREE[clamp(index, 3)];
                default -> fourOrMore(index, count);
            };
        }
        int i = clamp(index, count);
        int front = Math.min(frontQuota, count);
        // Rear wings remain first, exactly as the original squad layout.
        if (i == 0) return new SlotOffset(-1.8, 2.4);
        if (i == 1) return new SlotOffset(1.8, 2.4);

        int fi = i - 2;
        if (fi >= 0 && fi < front) {
            double[] lateral = {-4.5, -1.5, 1.5, 4.5};
            return new SlotOffset(lateral[fi % 4],
                    -1.0 - (fi / 4) * 1.5);
        }

        int rearIndex = i - 2 - front;
        double back = 4.4 + 2.0 * (rearIndex / 2);
        double right = rearIndex % 2 == 0 ? -1.8 : 1.8;
        return new SlotOffset(right, back);
    }

    private static SlotOffset fourOrMore(int index, int count) {
        int i = clamp(index, count);

        // Rear wings, row 0.
        if (i == 0) {
            return new SlotOffset(-1.8, 2.4);
        }
        if (i == 1) {
            return new SlotOffset(1.8, 2.4);
        }

        // Front guards, one on each side.
        double frontLateral = count >= 6 ? 2.5 : (count == 5 ? 2.4 : 2.2);
        double frontBack = count >= 6 ? -1.2 : (count == 5 ? -1.1 : -1.0);
        if (i == 2) {
            return new SlotOffset(-frontLateral, frontBack);
        }
        if (i == 3) {
            return new SlotOffset(frontLateral, frontBack);
        }

        int rearPairs = (count - 2 - (count % 2 == 1 ? 1 : 0)) / 2;
        int centerIndex = 2 + 2 * rearPairs;

        // Center tail for odd counts (5, 7, 9, ...).
        if (count % 2 == 1 && i == centerIndex) {
            double back = 4.4 + 2.0 * (rearPairs - 1);
            return new SlotOffset(0.0, back);
        }

        // Rear pairs after the first one: slots 4/5, 6/7, ...
        int pair = (i - 4) / 2;
        double back = 4.4 + 2.0 * pair;
        double right = i % 2 == 0 ? -1.8 : 1.8;
        return new SlotOffset(right, back);
    }

    private static int clamp(int index, int length) {
        if (index < 0) {
            return 0;
        }
        return Math.min(index, length - 1);
    }

    /**
     * Front guards exist only for 4+ maids: slot 2 is front-left, slot 3 is front-right.
     */
    public static boolean isFrontSlot(int index, int count) {
        return count >= 4 && (index == 2 || index == 3);
    }

    /**
     * Player yaw 0 = +Z (south), 90 = -X (west), 180 = -Z (north), 270 = +X (east).
     * rightX/rightZ is the player's right vector.
     */
    public static Vec3 target(Player owner, Vec3 anchor, SlotOffset offset) {
        double yaw = Math.toRadians(owner.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);

        double x = anchor.x + rightX * offset.right() - forwardX * offset.back();
        double z = anchor.z + rightZ * offset.right() - forwardZ * offset.back();
        return new Vec3(x, anchor.y, z);
    }

    public static Vec3 target(Player owner, SlotOffset offset) {
        return target(owner, owner.position(), offset);
    }
}
