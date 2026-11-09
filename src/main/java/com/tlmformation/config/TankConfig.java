package com.tlmformation.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TankConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue SAFE_FOLLOW_DISTANCE;
    public static final ForgeConfigSpec.IntValue SLOW_FOLLOW_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_FOLLOW_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue MAX_HEALTH;
    public static final ForgeConfigSpec.IntValue REGEN_WAIT_TICKS;
    public static final ForgeConfigSpec.IntValue REGEN_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue REGEN_AMOUNT;
    public static final ForgeConfigSpec.IntValue AMMO_PER_SHOT;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_AMMO;
    public static final ForgeConfigSpec.BooleanValue SUMMON_CHECK_AMMO;
    public static final ForgeConfigSpec.BooleanValue UNLOCK_CONSUMES_ITEM;
    public static final ForgeConfigSpec.BooleanValue ALLOW_REUNLOCK;
    public static final ForgeConfigSpec.IntValue AMMO_CRAFT_COUNT;
    public static final ForgeConfigSpec.IntValue AMMO_STACK_SIZE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Tactical vehicle (战术小车) settings").push("tactical_vehicle");
        SAFE_FOLLOW_DISTANCE = b.comment("Do not approach owner closer than this (blocks).")
                .defineInRange("safeFollowDistance", 10, 3, 48);
        SLOW_FOLLOW_DISTANCE = b.comment("Start slowing down at this distance.")
                .defineInRange("slowFollowDistance", 16, 4, 64);
        MAX_FOLLOW_DISTANCE = b.comment("Maximum follow distance.")
                .defineInRange("maxFollowDistance", 50, 10, 96);
        MAX_HEALTH = b.comment("Maximum health.").defineInRange("maxHealth", 300.0D, 20.0D, 2000.0D);
        REGEN_WAIT_TICKS = b.comment("Ticks after last damage before regen starts (600 = 30s).")
                .defineInRange("regenWaitTicks", 600, 20, 72000);
        REGEN_INTERVAL = b.comment("Ticks between regeneration ticks.")
                .defineInRange("regenInterval", 40, 1, 1200);
        REGEN_AMOUNT = b.comment("Health restored per regeneration tick.")
                .defineInRange("regenAmount", 1.0D, 0.1D, 100.0D);
        AMMO_PER_SHOT = b.comment("Ammo consumed per shot.")
                .defineInRange("ammoPerShot", 1, 1, 16);
        REQUIRE_AMMO = b.comment("Require ammo in the vehicle to fire.")
                .define("requireAmmo", true);
        SUMMON_CHECK_AMMO = b.comment("Check ammo when summoned from F10.")
                .define("summonCheckAmmo", false);
        UNLOCK_CONSUMES_ITEM = b.comment("Right-clicking the tactical car item consumes it.")
                .define("unlockConsumesItem", true);
        ALLOW_REUNLOCK = b.comment("Allow using the item again after unlocking.")
                .define("allowReunlock", false);
        AMMO_CRAFT_COUNT = b.comment("Ammo output per craft.").defineInRange("ammoCraftCount", 24, 1, 64);
        AMMO_STACK_SIZE = b.comment("Ammo stack size.").defineInRange("ammoStackSize", 64, 1, 64);
        b.pop();
        SPEC = b.build();
    }

    private TankConfig() {
    }
}
