package com.tlmformation.client.heli;

import com.tlmformation.sound.ModSounds;
import com.tlmformation.vehicle.EntityHelicopter;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * 直升机旋翼/引擎循环声（客户端）。
 *
 * <p>用一个循环的 {@link AbstractTickableSoundInstance} 跟随实体位置，
 * 取代原先“服务器在几个关键节点各播一次”的做法：</p>
 * <ul>
 *   <li><b>AI 空投</b>：直升机一直在天上（索降悬停时也在），循环声一直有；</li>
 *   <li><b>玩家正常驾驶</b>：只要起飞/有动力/在移动，同样有旋翼声；落地熄火后自动停；</li>
 *   <li>音量/距离衰减由 sounds.json 的 {@code attenuation_distance}（384 格）处理。</li>
 * </ul>
 *
 * <p>声音实例的生命周期由 {@link HelicopterSoundManager} 管理（每个实体一个）。</p>
 */
public class HelicopterEngineLoop extends AbstractTickableSoundInstance {
    private final EntityHelicopter helicopter;

    public HelicopterEngineLoop(EntityHelicopter helicopter) {
        super(ModSounds.HELI_ENGINE.get(), SoundSource.NEUTRAL,
                SoundInstance.createUnseededRandom());
        this.helicopter = helicopter;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.15F;
        this.pitch = 1.0F;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.syncPosition();
    }

    @Override
    public void tick() {
        if (this.helicopter.isRemoved() || !this.helicopter.isAlive()
                || !HelicopterSoundManager.shouldPlay(this.helicopter)) {
            this.stop();   // AbstractTickableSoundInstance.stop() → isStopped() 变 true，声音引擎会回收
            return;
        }
        this.syncPosition();
        // 悬停/索降时稍微收一点音量，避免长时间高音量疲劳
        this.volume = this.helicopter.isRappelling() ? 0.95F : 1.15F;
    }

    private void syncPosition() {
        this.x = this.helicopter.getX();
        this.y = this.helicopter.getY() + 1.2D;
        this.z = this.helicopter.getZ();
    }

    /** 供管理器判断：这个实例是否已经停止（不再需要保留）。 */
    public boolean finished() {
        return this.isStopped();
    }

    /** 外部（管理器）主动停止：`stop()` 在基类里是 protected，这里开一个公共入口。 */
    public void stopNow() {
        this.stop();
    }
}
