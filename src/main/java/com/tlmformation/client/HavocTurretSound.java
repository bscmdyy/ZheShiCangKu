package com.tlmformation.client;

import com.tlmformation.sound.ModSounds;
import com.tlmformation.vehicle.EntityHavoc;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** 炮塔旋转循环音：绑定战车，炮塔转动时播放。 */
public class HavocTurretSound extends AbstractTickableSoundInstance {
    private final EntityHavoc vehicle;

    public HavocTurretSound(EntityHavoc vehicle) {
        super(ModSounds.TURRET_ROTATE.get(), SoundSource.NEUTRAL, RandomSource.create());
        this.vehicle = vehicle;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.1F;
        this.pitch = 1.0F;
        this.x = vehicle.getX();
        this.y = vehicle.getY() + 1.0D;
        this.z = vehicle.getZ();
    }

    @Override
    public void tick() {
        if (!this.vehicle.isAlive() || this.vehicle.isRemoved()) {
            this.stop();
            return;
        }
        this.x = this.vehicle.getX();
        this.y = this.vehicle.getY() + 1.0D;
        this.z = this.vehicle.getZ();
    }

    public void stopSound() {
        this.stop();
    }
}
