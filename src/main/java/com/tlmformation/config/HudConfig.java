package com.tlmformation.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class HudConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue HUD_X_OFFSET;
    public static final ForgeConfigSpec.IntValue HUD_Y_OFFSET;
    public static final ForgeConfigSpec.DoubleValue HUD_SCALE;

    // ===== 战术装甲单位（战车）HUD 独立校准 =====
    public static final ForgeConfigSpec.IntValue VEHICLE_HUD_X_OFFSET;
    public static final ForgeConfigSpec.IntValue VEHICLE_HUD_Y_OFFSET;
    public static final ForgeConfigSpec.DoubleValue VEHICLE_HUD_SCALE;
    /** 一键隐藏小队 HUD + 战术单位 HUD。 */
    public static final ForgeConfigSpec.BooleanValue UI_HIDDEN;
    /** 直升机上自定义模型（gecko / YSM）女仆的整体下沉量（格）。 */
    public static final ForgeConfigSpec.DoubleValue HELI_MAID_SEAT_DROP;
    /** 女仆是否跟随直升机横滚/俯仰（防止转弯时露出机身）。 */
    public static final ForgeConfigSpec.BooleanValue HELI_MAID_FOLLOW_TILT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Tactical team HUD (bottom-right by default)").push("hud");
        HUD_X_OFFSET = b.comment("Horizontal offset in screen pixels; positive moves right.")
                .defineInRange("HudXOffset", 0, -400, 400);
        HUD_Y_OFFSET = b.comment("Vertical offset in screen pixels; positive moves down.")
                .defineInRange("HudYOffset", 0, -400, 400);
        HUD_SCALE = b.comment("HUD scale, 0.5 to 1.5")
                .defineInRange("HudScale", 0.8D, 0.5D, 1.5D);
        b.pop();

        b.comment("Tactical armored unit (vehicle) HUD calibration").push("vehicle_hud");
        VEHICLE_HUD_X_OFFSET = b.comment("Vehicle HUD horizontal offset; positive moves right.")
                .defineInRange("VehicleHudXOffset", 0, -400, 400);
        VEHICLE_HUD_Y_OFFSET = b.comment("Vehicle HUD vertical offset; positive moves down.")
                .defineInRange("VehicleHudYOffset", 0, -400, 400);
        VEHICLE_HUD_SCALE = b.comment("Vehicle HUD scale, 0.5 to 2.0")
                .defineInRange("VehicleHudScale", 1.0D, 0.5D, 2.0D);
        b.pop();

        b.comment("Tactical HUD visibility").push("hud_visibility");
        UI_HIDDEN = b.comment("When true, both the team HUD and vehicle HUD are hidden.")
                .define("uiHidden", false);
        b.pop();

        b.comment("Maid rendering on the tlmformation helicopter").push("maid_ride");
        HELI_MAID_SEAT_DROP = b.comment(
                        "How many blocks to lower a maid using a custom model (GeckoLib / YSM) when she rides the",
                        "tlmformation helicopter. TLM decides the riding pose from the model pack's vehicle$ animations;",
                        "models without a helicopter animation stay in the standing pose, which looks too high.",
                        "0 = disable. Negative values raise the maid.")
                .defineInRange("heliMaidSeatDrop", 0.40D, -1.0D, 1.0D);
        HELI_MAID_FOLLOW_TILT = b.comment(
                        "Make maids lean with the helicopter's roll/pitch. TLM renders maids without the",
                        "Superb Warfare vehicle rotation, so fast banking makes them poke through the hull.",
                        "Only the tilt is applied; the facing direction is still handled by TLM.")
                .define("heliMaidFollowTilt", true);
        b.pop();
        SPEC = b.build();
    }

    private HudConfig() {
    }

    private static Integer xOverride;
    private static Integer yOverride;
    private static Float scaleOverride;

    private static Integer vehicleXOverride;
    private static Integer vehicleYOverride;
    private static Float vehicleScaleOverride;
    private static Boolean uiHiddenOverride;

    public static int xOffset() {
        return xOverride != null ? xOverride : HUD_X_OFFSET.get();
    }

    public static int yOffset() {
        return yOverride != null ? yOverride : HUD_Y_OFFSET.get();
    }

    public static float scale() {
        return scaleOverride != null ? scaleOverride : HUD_SCALE.get().floatValue();
    }

    // ===== 战车 HUD =====
    public static int vehicleXOffset() {
        return vehicleXOverride != null ? vehicleXOverride : VEHICLE_HUD_X_OFFSET.get();
    }

    public static int vehicleYOffset() {
        return vehicleYOverride != null ? vehicleYOverride : VEHICLE_HUD_Y_OFFSET.get();
    }

    public static float vehicleScale() {
        return vehicleScaleOverride != null ? vehicleScaleOverride
                : VEHICLE_HUD_SCALE.get().floatValue();
    }

    public static boolean uiHidden() {
        return uiHiddenOverride != null ? uiHiddenOverride : UI_HIDDEN.get();
    }

    /** 直升机上自定义模型女仆的下沉量（格）。 */
    public static double heliMaidSeatDrop() {
        return HELI_MAID_SEAT_DROP.get();
    }

    /** 女仆是否跟随直升机横滚/俯仰。 */
    public static boolean heliMaidFollowTilt() {
        return HELI_MAID_FOLLOW_TILT.get();
    }

    public static void toggleUiHidden() {
        uiHiddenOverride = !uiHidden();
    }

    public static void setOffsets(int x, int y) {
        xOverride = x;
        yOverride = y;
    }

    public static void setScale(float scale) {
        scaleOverride = Math.max(0.5F, Math.min(2.0F, scale));
    }

    public static void setVehicleOffsets(int x, int y) {
        vehicleXOverride = x;
        vehicleYOverride = y;
    }

    public static void setVehicleScale(float scale) {
        vehicleScaleOverride = Math.max(0.5F, Math.min(2.0F, scale));
    }

    public static void commit() {
        if (xOverride != null) HUD_X_OFFSET.set(xOverride);
        if (yOverride != null) HUD_Y_OFFSET.set(yOverride);
        if (scaleOverride != null) HUD_SCALE.set((double) scaleOverride);
        if (vehicleXOverride != null) VEHICLE_HUD_X_OFFSET.set(vehicleXOverride);
        if (vehicleYOverride != null) VEHICLE_HUD_Y_OFFSET.set(vehicleYOverride);
        if (vehicleScaleOverride != null) VEHICLE_HUD_SCALE.set((double) vehicleScaleOverride);
        if (uiHiddenOverride != null) UI_HIDDEN.set(uiHiddenOverride);
    }

    public static void reset() {
        xOverride = 0;
        yOverride = 0;
        scaleOverride = 0.8F;
        vehicleXOverride = 0;
        vehicleYOverride = 0;
        vehicleScaleOverride = 1.0F;
    }
}
