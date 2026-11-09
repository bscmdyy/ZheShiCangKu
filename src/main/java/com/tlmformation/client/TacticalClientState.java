package com.tlmformation.client;

import com.tlmformation.tactical.TacticalState;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class TacticalClientState {
    private static final Map<Integer, TacticalState> STATES = new HashMap<>();
    private static BlockPos marker;
    private static int lockedTargetEntityId = -1;
    private static int commandOrdinal = 0;

    private TacticalClientState() {
    }

    public static void update(Map<Integer, Integer> states, @Nullable BlockPos markerPos,
                              int lockedTargetEntityId, int commandOrdinal) {
        STATES.clear();
        states.forEach((entityId, ordinal) -> {
            TacticalState[] values = TacticalState.values();
            if (ordinal >= 0 && ordinal < values.length) {
                STATES.put(entityId, values[ordinal]);
            }
        });
        marker = markerPos;
        TacticalClientState.lockedTargetEntityId = lockedTargetEntityId;
        TacticalClientState.commandOrdinal = commandOrdinal;
    }

    public static String protocolName() {
        return switch (com.tlmformation.tactical.TacticalCommand.byOrdinal(commandOrdinal)) {
            case MARK -> "战术机动";
            case LOCK -> "集火歼灭";
            case GUARD -> "广域索敌";
            case RETREAT -> "全体撤防";
            case HOLD -> "原地驻防";
            default -> "队列跟随";
        };
    }

    @Nullable
    public static TacticalState get(int entityId) {
        return STATES.get(entityId);
    }

    public static java.util.Set<Integer> entityIds() {
        return STATES.keySet();
    }

    @Nullable
    public static BlockPos marker() {
        return marker;
    }

    public static int lockedTargetEntityId() {
        return lockedTargetEntityId;
    }

    public static void clear() {
        STATES.clear();
        marker = null;
        lockedTargetEntityId = -1;
    }
}
