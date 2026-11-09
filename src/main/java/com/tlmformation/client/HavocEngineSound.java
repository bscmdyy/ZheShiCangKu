package com.tlmformation.client;

import com.tlmformation.sound.ModSounds;
import com.tlmformation.vehicle.EntityHavoc;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * 引擎循环音：绑定战车实体，移动时用 drive_loop，静止时用 idle_loop。
 * 速度越快 pitch 越高。
 */
public class HavocEngineSound extends AbstractTickableSoundInstance {
    private final EntityHavoc vehicle;
    private final boolean drive;

    public HavocEngineSound(EntityHavoc vehicle, boolean drive) {
        super(drive ? ModSounds.DRIVE_LOOP.get() : ModSounds.IDLE_LOOP.get(),
                SoundSource.NEUTRAL, RandomSource.create());
        this.vehicle = vehicle;
        this.drive = drive;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.1F;
        this.pitch = 1.0F;
        this.x = vehicle.getX();
        this.y = vehicle.getY() + 0.4D;
        this.z = vehicle.getZ();
    }

    public boolean isDrive() {
        return this.drive;
    }

    public void stopSound() {
        this.stop();
    }

    @Override
    public void tick() {
        if (!this.vehicle.isAlive() || this.vehicle.isRemoved()) {
            this.stop();
            return;
        }
        this.x = this.vehicle.getX();
        this.y = this.vehicle.getY() + 0.4D;
        this.z = this.vehicle.getZ();
        float speed = (float) Math.abs(this.vehicle.getVehicleSpeed());
        if (this.drive) {
            this.volume = 0.1F;
            this.pitch = 0.90F + Math.min(0.45F, speed * 0.055F);
        } else {
            this.volume = 0.1F;
            this.pitch = 0.90F;
        }
    }
}
