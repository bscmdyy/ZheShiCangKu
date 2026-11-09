package com.tlmformation.client;

import com.tlmformation.TLMFormationMod;
import com.tlmformation.sound.ModSounds;
import com.tlmformation.vehicle.EntityHavoc;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 客户端循环音管理：
 * 引擎（drive/idle 切换 + 速度 pitch）、炮塔旋转循环 + 停转一次性音效。
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HavocSoundHandler {
    private static final Map<Integer, EngineState> ENGINES = new HashMap<>();
    private static final Map<Integer, TurretState> TURRETS = new HashMap<>();
    private static final Set<UUID> SEEN = new HashSet<>();

    private HavocSoundHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clearAll();
            return;
        }

        SEEN.clear();
        for (EntityHavoc vehicle : minecraft.level.getEntitiesOfClass(EntityHavoc.class,
                minecraft.player.getBoundingBox().inflate(160.0D), EntityHavoc::isAlive)) {
            SEEN.add(vehicle.getUUID());
            updateEngine(minecraft, vehicle);
            updateTurret(minecraft, vehicle);
        }

        // 清理已消失/死亡战车的循环音
        ENGINES.entrySet().removeIf(entry -> {
            if (!SEEN.contains(idToUuid(minecraft, entry.getKey()))) {
                entry.getValue().stopAll();
                return true;
            }
            return false;
        });
        TURRETS.entrySet().removeIf(entry -> {
            if (!SEEN.contains(idToUuid(minecraft, entry.getKey()))) {
                entry.getValue().stop();
                return true;
            }
            return false;
        });
    }

    private static UUID idToUuid(Minecraft minecraft, int id) {
        var entity = minecraft.level == null ? null : minecraft.level.getEntity(id);
        return entity == null ? new UUID(0L, 0L) : entity.getUUID();
    }

    private static void updateEngine(Minecraft minecraft, EntityHavoc vehicle) {
        float speed = (float) Math.abs(vehicle.getVehicleSpeed());
        boolean moving = speed > 0.05F;
        EngineState state = ENGINES.computeIfAbsent(vehicle.getId(), id -> new EngineState());

        if (moving) {
            if (state.drive == null) {
                state.stopIdle();
                state.drive = new HavocEngineSound(vehicle, true);
                minecraft.getSoundManager().play(state.drive);
            }
        } else {
            if (state.idle == null) {
                state.stopDrive();
                state.idle = new HavocEngineSound(vehicle, false);
                minecraft.getSoundManager().play(state.idle);
            }
        }
    }

    private static void updateTurret(Minecraft minecraft, EntityHavoc vehicle) {
        float yaw = (float) vehicle.getTurretYaw();
        TurretState state = TURRETS.computeIfAbsent(vehicle.getId(), id -> new TurretState(yaw));
        float delta = Math.abs(Mth.wrapDegrees(yaw - state.lastYaw));
        state.lastYaw = yaw;
        boolean rotating = delta > 0.8F;

        if (rotating) {
            if (state.sound == null) {
                state.sound = new HavocTurretSound(vehicle);
                minecraft.getSoundManager().play(state.sound);
            }
        } else if (state.sound != null) {
            state.sound.stopSound();
            state.sound = null;
            // 停转音效只播一次
            minecraft.level.playLocalSound(vehicle.getX(), vehicle.getY() + 1.0D, vehicle.getZ(),
                    ModSounds.TURRET_STOP.get(), SoundSource.NEUTRAL, 0.1F, 1.0F, false);
        }
    }

    private static void clearAll() {
        ENGINES.values().forEach(EngineState::stopAll);
        ENGINES.clear();
        TURRETS.values().forEach(TurretState::stop);
        TURRETS.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    private static final class EngineState {
        private HavocEngineSound idle;
        private HavocEngineSound drive;

        private void stopIdle() {
            if (idle != null) {
                idle.stopSound();
                idle = null;
            }
        }

        private void stopDrive() {
            if (drive != null) {
                drive.stopSound();
                drive = null;
            }
        }

        private void stopAll() {
            stopIdle();
            stopDrive();
        }
    }

    private static final class TurretState {
        private float lastYaw;
        private HavocTurretSound sound;

        private TurretState(float lastYaw) {
            this.lastYaw = lastYaw;
        }

        private void stop() {
            if (sound != null) {
                sound.stopSound();
                sound = null;
            }
        }
    }
}
