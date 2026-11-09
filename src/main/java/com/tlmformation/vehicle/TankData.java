package com.tlmformation.vehicle;

import com.tlmformation.TLMFormationMod;
import com.tlmformation.config.TankConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** 玩家解锁状态 / 持久化弹药库存 / 小车弹药。 */
public final class TankData {
    public static final String TAG_UNLOCKED = "TLMTankUnlocked";
    public static final String TAG_AMMO_STOCK = "TLMTankAmmoStock";
    public static final String TAG_VEHICLE_AMMO = "TLMTankVehicleAmmo";

    private TankData() {
    }

    public static boolean isUnlocked(Player player) {
        return player.getPersistentData().getBoolean(TAG_UNLOCKED);
    }

    public static void setUnlocked(Player player) {
        player.getPersistentData().putBoolean(TAG_UNLOCKED, true);
    }

    public static int stock(Player player) {
        return Math.max(0, player.getPersistentData().getInt(TAG_AMMO_STOCK));
    }

    public static void setStock(Player player, int amount) {
        player.getPersistentData().putInt(TAG_AMMO_STOCK, Math.max(0, amount));
    }

    public static int addStock(Player player, int amount) {
        setStock(player, stock(player) + Math.max(0, amount));
        return stock(player);
    }

    public static int consumeStock(Player player, int amount) {
        int value = stock(player);
        int used = Math.min(value, Math.max(0, amount));
        setStock(player, value - used);
        return used;
    }

    public static int vehicleAmmo(EntityHavoc vehicle) {
        return Math.max(0, vehicle.getPersistentData().getInt(TAG_VEHICLE_AMMO));
    }

    public static void setVehicleAmmo(EntityHavoc vehicle, int amount) {
        vehicle.getPersistentData().putInt(TAG_VEHICLE_AMMO, Math.max(0, amount));
    }

    public static void addVehicleAmmo(EntityHavoc vehicle, int amount) {
        setVehicleAmmo(vehicle, vehicleAmmo(vehicle) + Math.max(0, amount));
    }

    public static boolean consumeVehicleAmmo(EntityHavoc vehicle, int amount) {
        int value = vehicleAmmo(vehicle);
        if (value < amount) {
            return false;
        }
        setVehicleAmmo(vehicle, value - amount);
        return true;
    }

    public static int ammoPerShot() {
        try {
            return TankConfig.AMMO_PER_SHOT.get();
        } catch (Exception e) {
            return 1;
        }
    }

    public static boolean requireAmmo() {
        try {
            return TankConfig.REQUIRE_AMMO.get();
        } catch (Exception e) {
            return true;
        }
    }

    public static void debug(String message) {
        TLMFormationMod.LOGGER.debug("[Tank] " + message);
    }

    /** MQ-9 无人机芯片是否已使用（F10 槽位 2 解锁）。 */
    public static boolean isDroneUnlocked(net.minecraft.server.level.ServerPlayer player) {
        return player.getPersistentData().getBoolean("tlmf_drone_unlocked");
    }

    public static void setDroneUnlocked(net.minecraft.server.level.ServerPlayer player) {
        player.getPersistentData().putBoolean("tlmf_drone_unlocked", true);
    }

}
