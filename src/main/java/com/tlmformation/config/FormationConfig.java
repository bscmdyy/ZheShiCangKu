package com.tlmformation.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class FormationConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_MAIDS;
    public static final ForgeConfigSpec.DoubleValue SLOT_SCALE;
    public static final ForgeConfigSpec.DoubleValue FOLLOW_SPEED;
    public static final ForgeConfigSpec.IntValue FOLLOW_RANGE;
    public static final ForgeConfigSpec.IntValue FOLLOW_RELEASE_TICKS;
    public static final ForgeConfigSpec.DoubleValue STILL_SPEED;
    public static final ForgeConfigSpec.IntValue STILL_TICKS;
    public static final ForgeConfigSpec.IntValue PREDICTION_TICKS;
    public static final ForgeConfigSpec.DoubleValue PREDICTION_STRENGTH;
    public static final ForgeConfigSpec.IntValue MAX_PREDICTION;
    public static final ForgeConfigSpec.DoubleValue HIGH_SPEED_PREDICTION_CAP;
    public static final ForgeConfigSpec.DoubleValue PREDICTION_SPEED_CAP;
    public static final ForgeConfigSpec.DoubleValue LOW_SPEED_PREDICTION_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue LOW_SPEED_PREDICTION_FACTOR;
    public static final ForgeConfigSpec.DoubleValue TARGET_SMOOTHING_SPEED;
    public static final ForgeConfigSpec.DoubleValue TARGET_SMOOTHING_MIN;
    public static final ForgeConfigSpec.DoubleValue TARGET_SMOOTHING_FACTOR;
    public static final ForgeConfigSpec.DoubleValue TARGET_CHANGE_THRESHOLD;
    public static final ForgeConfigSpec.IntValue PATH_COMMIT_TICKS;
    public static final ForgeConfigSpec.IntValue FRONT_FALLBACK_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue FRONT_TARGET_LEAD;
    public static final ForgeConfigSpec.DoubleValue LOW_SPEED_FRONT_LEAD;
    public static final ForgeConfigSpec.DoubleValue REAR_SPEED_BOOST;
    public static final ForgeConfigSpec.DoubleValue REAR_CHASE_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FRONT_BOOST_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue FRONT_PRIORITY_SPEED;
    public static final ForgeConfigSpec.DoubleValue FRONT_SPEED_BOOST;
    public static final ForgeConfigSpec.IntValue EMERGENCY_TELEPORT_DISTANCE;
    public static final ForgeConfigSpec.IntValue RECONCILE_INTERVAL;
    public static final ForgeConfigSpec.IntValue OWNER_ACTIVE_WINDOW;
    public static final ForgeConfigSpec.IntValue TACTICAL_RADIUS;
    public static final ForgeConfigSpec.IntValue TACTICAL_GUARD_PATROL_INTERVAL;
    public static final ForgeConfigSpec.IntValue TACTICAL_MARKER_RECALL_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue TACTICAL_SPEED_CAP;
    public static final ForgeConfigSpec.DoubleValue TACTICAL_TARGET_CHANGE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue TACTICAL_GUARD_SPEED;
    public static final ForgeConfigSpec.DoubleValue TACTICAL_MOVE_SPEED;
    public static final ForgeConfigSpec.IntValue TACTICAL_LOCK_RANGE;
    public static final ForgeConfigSpec.DoubleValue TACTICAL_LOCK_CONE;
    public static final ForgeConfigSpec.BooleanValue TACTICAL_CHUNK_LOADING;

    public static final ForgeConfigSpec.BooleanValue EXPAND_ENABLED;
    public static final ForgeConfigSpec.IntValue EXPAND_RANGE;
    public static final ForgeConfigSpec.IntValue EXPAND_DURATION;
    public static final ForgeConfigSpec.IntValue TELEPORT_GRACE;
    public static final ForgeConfigSpec.BooleanValue REFRESH_ON_MAID_HIT;

    public static final ForgeConfigSpec.BooleanValue CHUNK_LOADING_ENABLED;
    public static final ForgeConfigSpec.IntValue CHUNK_RADIUS;
    public static final ForgeConfigSpec.BooleanValue CHUNK_TICKING;
    public static final ForgeConfigSpec.IntValue MAX_REGISTERED_MAIDS;

    // ===== 部署期小队区块常加载 =====
    public static final ForgeConfigSpec.BooleanValue SQUAD_CHUNK_ENABLED;
    public static final ForgeConfigSpec.IntValue SQUAD_CHUNK_RADIUS;
    public static final ForgeConfigSpec.BooleanValue SQUAD_CHUNK_TICKING;
    public static final ForgeConfigSpec.IntValue SQUAD_CHUNK_MAX;

    // ===== 小队硬救援（死亡/跨维度/小地图传送/倒地） =====
    public static final ForgeConfigSpec.BooleanValue SQUAD_RESCUE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SQUAD_RESCUE_ON_RESPAWN;
    public static final ForgeConfigSpec.BooleanValue SQUAD_RESCUE_ON_DIMENSION;
    public static final ForgeConfigSpec.BooleanValue SQUAD_RECONCILE_ENABLED;
    public static final ForgeConfigSpec.IntValue SQUAD_RECONCILE_TICKS;
    public static final ForgeConfigSpec.IntValue SQUAD_RESCUE_MAX_DISTANCE;
    public static final ForgeConfigSpec.IntValue SQUAD_RESCUE_COOLDOWN;
    public static final ForgeConfigSpec.BooleanValue SQUAD_RESCUE_REVIVE_DOWNED;

    public static final ForgeConfigSpec.BooleanValue RECALL_ON_RESPAWN;
    public static final ForgeConfigSpec.IntValue RESPAWN_RECALL_DISTANCE;

    public static final ForgeConfigSpec.BooleanValue PROTECT_OWNER;
    public static final ForgeConfigSpec.BooleanValue PROTECT_OTHER_MAIDS;
    public static final ForgeConfigSpec.EnumValue<OwnerDamageMode> OWNER_DAMAGE_MODE;

    public static final ForgeConfigSpec.BooleanValue DEBUG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Formation following").push("formation");
        ENABLED = b.comment("Enable the formation follow system.").define("enabled", true);
        MAX_MAIDS = b.comment("Maximum number of maids in one formation.")
                .defineInRange("maxMaids", 12, 1, 32);
        SLOT_SCALE = b.comment("Scale of formation offsets.")
                .defineInRange("slotScale", 1.0D, 0.5D, 2.0D);
        FOLLOW_SPEED = b.comment("Movement speed modifier used by the formation task.")
                .defineInRange("followSpeed", 0.55D, 0.1D, 1.5D);
        FOLLOW_RANGE = b.comment("Activation radius for forced following. This is NOT a teleport distance.")
                .defineInRange("followRange", 32, 8, 128);
        FOLLOW_RELEASE_TICKS = b.comment("Ticks the owner must be still before an engaged maid can return to FREE.")
                .defineInRange("followReleaseTicks", 100, 20, 400);
        STILL_SPEED = b.comment("Horizontal speed below which the owner is considered stationary.")
                .defineInRange("stillSpeed", 0.05D, 0.001D, 0.5D);
        STILL_TICKS = b.comment("Ticks the owner must be stationary before maids disperse.")
                .defineInRange("stillTicks", 40, 10, 200);
        PREDICTION_TICKS = b.comment("How many ticks of owner velocity are used for path prediction.")
                .defineInRange("predictionTicks", 20, 0, 40);
        PREDICTION_STRENGTH = b.comment("Multiplier applied to the predicted offset.")
                .defineInRange("predictionStrength", 1.0D, 0.0D, 2.0D);
        MAX_PREDICTION = b.comment("Maximum forward prediction distance in blocks.")
                .defineInRange("maxPrediction", 5, 0, 8);
        HIGH_SPEED_PREDICTION_CAP = b.comment("Prediction distance cap at high owner speed; high speed should tighten, not extend, the target.")
                .defineInRange("highSpeedPredictionCap", 3.0D, 1.0D, 8.0D);
        PREDICTION_SPEED_CAP = b.comment("Owner speed at which the high-speed prediction cap is reached.")
                .defineInRange("predictionSpeedCap", 0.25D, 0.05D, 1.0D);
        LOW_SPEED_PREDICTION_THRESHOLD = b.comment("Owner speed below which prediction is heavily reduced (walking pace).")
                .defineInRange("lowSpeedPredictionThreshold", 0.11D, 0.02D, 0.3D);
        LOW_SPEED_PREDICTION_FACTOR = b.comment("Prediction multiplier below the low-speed threshold.")
                .defineInRange("lowSpeedPredictionFactor", 0.25D, 0.0D, 1.0D);
        TARGET_SMOOTHING_SPEED = b.comment("Maximum blocks per tick the smoothed target may move; also the interpolation cap.")
                .defineInRange("targetSmoothingSpeed", 1.0D, 0.2D, 2.0D);
        TARGET_SMOOTHING_MIN = b.comment("Minimum smoothing speed when the maid is almost stationary.")
                .defineInRange("targetSmoothingMin", 0.15D, 0.05D, 0.5D);
        TARGET_SMOOTHING_FACTOR = b.comment("Smoothing speed multiplier over the maid's current movement speed.")
                .defineInRange("targetSmoothingFactor", 1.25D, 1.0D, 2.0D);
        TARGET_CHANGE_THRESHOLD = b.comment("Only re-commit WalkTarget when the smoothed target moved this far.")
                .defineInRange("targetChangeThreshold", 1.5D, 0.5D, 4.0D);
        PATH_COMMIT_TICKS = b.comment("Minimum ticks a new path is kept before another WalkTarget commit.")
                .defineInRange("pathCommitTicks", 12, 10, 40);
        FRONT_FALLBACK_COOLDOWN = b.comment("Ticks to keep using the last safe front target after an invalid predicted point.")
                .defineInRange("frontFallbackCooldown", 10, 0, 40);
        FRONT_TARGET_LEAD = b.comment("Extra forward lead for front-slot targets, beyond the normal front slot offset.")
                .defineInRange("frontTargetLead", 1.25D, 0.0D, 3.0D);
        LOW_SPEED_FRONT_LEAD = b.comment("Additional front lead while the owner walks slowly, so the maid does not arrive and wait.")
                .defineInRange("lowSpeedFrontLead", 2.5D, 0.0D, 4.0D);
        REAR_SPEED_BOOST = b.comment("Temporary movement speed bonus for rear maids that are chasing.")
                .defineInRange("rearSpeedBoost", 0.2D, 0.0D, 0.6D);
        REAR_CHASE_DISTANCE = b.comment("Distance from the rear target at which the rear chase boost activates.")
                .defineInRange("rearChaseDistance", 3.0D, 1.0D, 8.0D);
        FRONT_BOOST_DISTANCE = b.comment("Front speed boost only applies when the maid is at least this far from the target.")
                .defineInRange("frontBoostDistance", 6.0D, 2.0D, 12.0D);
        FRONT_PRIORITY_SPEED = b.comment("Owner horizontal speed above which front slots get priority chase and a temporary speed boost.")
                .defineInRange("frontPrioritySpeed", 0.12D, 0.05D, 1.0D);
        FRONT_SPEED_BOOST = b.comment("Temporary movement speed bonus for front-slot maids during high-speed chase.")
                .defineInRange("frontSpeedBoost", 0.25D, 0.0D, 0.6D);
        EMERGENCY_TELEPORT_DISTANCE = b.comment("Emergency teleport fallback distance. Must stay independent from followRange.")
                .defineInRange("emergencyTeleportDistance", 64, 16, 256);
        RECONCILE_INTERVAL = b.comment("Ticks between roster scans.")
                .defineInRange("reconcileInterval", 20, 5, 200);
        OWNER_ACTIVE_WINDOW = b.comment("Legacy compatibility value; no longer used for follow activation.")
                .defineInRange("ownerActiveWindow", 20, 5, 100);
        TACTICAL_RADIUS = b.comment("Radius around the player affected by tactical wheel commands.")
                .defineInRange("tacticalRadius", 32, 8, 96);
        TACTICAL_GUARD_PATROL_INTERVAL = b.comment("Ticks between random patrol points for guarding maids.")
                .defineInRange("tacticalGuardPatrolInterval", 40, 10, 200);
        TACTICAL_MARKER_RECALL_DISTANCE = b.comment("Distance from a station marker at which the owner recalls the maid.")
                .defineInRange("tacticalMarkerRecallDistance", 64, 16, 256);
        TACTICAL_SPEED_CAP = b.comment("Hard cap for temporary tactical WalkTarget speed; does not affect normal formation following.")
                .defineInRange("tacticalSpeedCap", 1.2D, 0.5D, 1.2D);
        TACTICAL_TARGET_CHANGE_THRESHOLD = b.comment("Distance threshold before a temporary tactical WalkTarget is recomputed.")
                .defineInRange("tacticalTargetChangeThreshold", 1.5D, 0.5D, 3.0D);
        TACTICAL_GUARD_SPEED = b.comment("Patrol speed for guarding maids; deliberately villager-slow.")
                .defineInRange("tacticalGuardSpeed", 0.4D, 0.2D, 0.6D);
        TACTICAL_MOVE_SPEED = b.comment("WalkTarget speed for station/retreat tactical movement.")
                .defineInRange("tacticalMoveSpeed", 0.65D, 0.3D, 0.8D);
        TACTICAL_LOCK_RANGE = b.comment("Maximum ray/cone range for the lock command.")
                .defineInRange("tacticalLockRange", 96, 16, 128);
        TACTICAL_LOCK_CONE = b.comment("Cone radius around the crosshair for lock target snapping.")
                .defineInRange("tacticalLockCone", 9.0D, 1.0D, 12.0D);
        TACTICAL_CHUNK_LOADING = b.comment("Force-load Entity-Ticking chunks for parked/stationed tactical maids.")
                .define("tacticalChunkLoading", true);
        b.pop();

        b.comment("Combat expansion").push("combat");
        EXPAND_ENABLED = b.comment("Expand targeting range while the owner is in combat.").define("enabled", true);
        EXPAND_RANGE = b.comment("Expanded targeting radius in blocks.")
                .defineInRange("expandRange", 100, 16, 192);
        EXPAND_DURATION = b.comment("How long the expanded window lasts after combat, in ticks.")
                .defineInRange("expandDurationTicks", 200, 20, 1200);
        TELEPORT_GRACE = b.comment("Keep current target for this many ticks after the owner teleports.")
                .defineInRange("teleportGraceTicks", 100, 0, 400);
        REFRESH_ON_MAID_HIT = b.comment("Refresh the owner's combat window when a maid hits a target.")
                .define("refreshOnMaidHit", true);
        b.pop();

        b.comment("Chunk loading").push("chunkLoading");
        CHUNK_LOADING_ENABLED = b.comment("Enable ForgeChunkManager based chunk loading for registered maids.")
                .define("enabled", true);
        CHUNK_RADIUS = b.comment("Extra chunk radius around the maid's chunk. 0 = only the maid's chunk.")
                .defineInRange("radius", 0, 0, 2);
        CHUNK_TICKING = b.comment("Force the center chunk to tick without a nearby player.")
                .define("ticking", true);
        MAX_REGISTERED_MAIDS = b.comment("Maximum registered maids per player.")
                .defineInRange("maxRegisteredMaids", 8, 1, 32);
        b.pop();

        b.comment("Owner death and respawn").push("respawn");
        RECALL_ON_RESPAWN = b.comment("Try to recall same-dimension maids after the owner respawns.")
                .define("recallOnRespawn", true);
        RESPAWN_RECALL_DISTANCE = b.comment("If a maid is farther than this after respawn, teleport it to the owner.")
                .defineInRange("respawnRecallDistance", 32, 8, 128);
        b.pop();

        b.comment("Friendly fire protection").push("friendlyFire");
        PROTECT_OWNER = b.comment("Prevent maid attacks from damaging their owner.")
                .define("protectOwner", true);
        PROTECT_OTHER_MAIDS = b.comment("Prevent maids from damaging other maids with the same owner.")
                .define("protectSameOwnerMaids", true);
        OWNER_DAMAGE_MODE = b.comment("How owner damage to their own maids is handled.")
                .defineEnum("ownerDamageMode", OwnerDamageMode.VANILLA);
        b.pop();

        b.comment("Force-load the chunks around a deployed airdrop squad so the maids",
                        "keep ticking even when the owner walks away.")
                .push("squadChunks");
        SQUAD_CHUNK_ENABLED = b.comment("Enable squad chunk force-loading while a squad is deployed.")
                .define("enabled", true);
        SQUAD_CHUNK_RADIUS = b.comment("Chunks around each squad member (1 = 3x3).")
                .defineInRange("radius", 1, 0, 2);
        SQUAD_CHUNK_TICKING = b.comment("Ticking chunks (entity ticking). Turn off to only keep them loaded.")
                .define("ticking", true);
        SQUAD_CHUNK_MAX = b.comment("Hard cap of forced chunks per squad.")
                .defineInRange("maxChunks", 16, 1, 64);
        b.pop();

        b.comment("Hard squad rescue: bring deployed maids back after death, dimension change,",
                        "long-distance (minimap) teleports, or when they are downed out of reach.")
                .push("squadRescue");
        SQUAD_RESCUE_ENABLED = b.comment("Master switch for hard squad rescue.")
                .define("enabled", true);
        SQUAD_RESCUE_ON_RESPAWN = b.comment("Force-rescue the active squad right after the owner respawns.")
                .define("onRespawn", true);
        SQUAD_RESCUE_ON_DIMENSION = b.comment("Force-rescue the active squad when the owner changes dimension.")
                .define("onDimensionChange", true);
        SQUAD_RECONCILE_ENABLED = b.comment("Periodic safety net: catches teleports no event can see (minimap mods).")
                .define("reconcileEnabled", true);
        SQUAD_RECONCILE_TICKS = b.comment("Reconcile scan interval in ticks (100 = 5 s).")
                .defineInRange("reconcileIntervalTicks", 100, 20, 1200);
        SQUAD_RESCUE_MAX_DISTANCE = b.comment("If no squad member is loaded within this distance of the owner, rescue.")
                .defineInRange("maxDistance", 512, 32, 4096);
        SQUAD_RESCUE_COOLDOWN = b.comment("Minimum ticks between two automatic rescues for the same player.")
                .defineInRange("cooldownTicks", 200, 20, 2400);
        SQUAD_RESCUE_REVIVE_DOWNED = b.comment("Revive downed maids while rescuing them.")
                .define("reviveDowned", true);
        b.pop();

        b.comment("Debug").push("debug");        DEBUG = b.comment("Log formation and chunk-loading decisions.")
                .define("enabled", false);
        b.pop();

        SPEC = b.build();
    }

    private FormationConfig() {
    }

    public enum OwnerDamageMode {
        VANILLA,
        REDUCE,
        CANCEL
    }
}
