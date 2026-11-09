package com.tlmformation.tactical;

public enum TacticalState {
    FOLLOW,
    STATION,
    GUARD,
    RETREAT,
    LOCK,
    HOLD;

    public boolean isTemporary() {
        return this != FOLLOW;
    }

    public TacticalCommand iconCommand() {
        return switch (this) {
            case STATION -> TacticalCommand.MARK;
            case GUARD -> TacticalCommand.GUARD;
            case RETREAT -> TacticalCommand.RETREAT;
            case LOCK -> TacticalCommand.LOCK;
            case HOLD -> TacticalCommand.HOLD;
            default -> TacticalCommand.FOLLOW;
        };
    }

    public static TacticalState byName(String name) {
        for (TacticalState value : values()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return FOLLOW;
    }
}
