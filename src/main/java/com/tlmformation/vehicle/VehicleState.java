package com.tlmformation.vehicle;

/**
 * 战车 AI 状态机（战术轮盘只保留两个选项）。
 * FOLLOW 跟随玩家 / PATROL 自主巡逻。
 */
public enum VehicleState {
    FOLLOW("vehicle.tlmformation.state.follow"),
    PATROL("vehicle.tlmformation.state.patrol");

    private final String translationKey;

    VehicleState(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return this.translationKey;
    }

    public static VehicleState byId(int id) {
        VehicleState[] values = values();
        return id >= 0 && id < values.length ? values[id] : FOLLOW;
    }
}
