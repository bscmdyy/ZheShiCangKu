package com.tlmformation.client.heli;

import com.tlmformation.sound.ModSounds;
import com.tlmformation.vehicle.EntityHelicopter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 直升机旋翼声管理（仅客户端）。
 *
 * <p>每个 {@link EntityHelicopter} 最多一个循环声实例：</p>
 * <ul>
 *   <li>当“应该发声”（离地 / 有动力 / 在移动 / 正在索降）时启动循环，并播一次引擎启动音；</li>
 *   <li>落地熄火、实体移除或离玩家太远时停止并回收；</li>
 *   <li>换世界 / 退出登录时 {@link #clear()}。</li>
 * </ul>
 *
 * <p>由 {@code EntityHelicopter.tick()} 的客户端分支每 tick 调用一次（DistExecutor 保护，
 * 专用服务器不会加载本类）。</p>
 */
public final class HelicopterSoundManager {
    /** 超过这个距离就停声（区块卸载/传送后不必继续保留实例）。 */
    private static final double MAX_DISTANCE_SQR = 512.0D * 512.0D;

    private static final Map<Integer, HelicopterEngineLoop> LOOPS = new HashMap<>();
    /** 已经播过“启动音”的实体，避免反复起飞时重复播。 */
    private static final Map<Integer, Long> STARTED_AT = new HashMap<>();

    private HelicopterSoundManager() {
    }

    /** 由实体 tick 调用（客户端）。 */
    public static void tick(EntityHelicopter heli) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() == null || minecraft.level == null || heli == null) {
            return;
        }
        int id = heli.getId();
        boolean shouldPlay = shouldPlay(heli);
        HelicopterEngineLoop loop = LOOPS.get(id);
        if (!shouldPlay) {
            if (loop != null) {
                loop.stopNow();
                LOOPS.remove(id);
            }
            STARTED_AT.remove(id);
            return;
        }
        if (loop == null || loop.finished()) {
            loop = new HelicopterEngineLoop(heli);
            LOOPS.put(id, loop);
            minecraft.getSoundManager().play(loop);
            // 第一次启动时补一声“引擎启动”
            long now = minecraft.level.getGameTime();
            Long lastStart = STARTED_AT.get(id);
            if (lastStart == null || now - lastStart > 100L) {
                STARTED_AT.put(id, now);
                minecraft.getSoundManager().play(new SimpleSoundInstance(
                        ModSounds.HELI_ENGINE_START.get(), SoundSource.NEUTRAL,
                        1.5F, 1.0F, SoundInstance.createUnseededRandom(),
                        heli.getX(), heli.getY() + 1.0D, heli.getZ()));
            }
        }
        pruneStopped();
    }

    /**
     * 是否由本模组接管旋翼声。
     *
     * <p>规则（按用户要求）：</p>
     * <ul>
     *   <li><b>玩家正常驾驶</b>：不接管 —— 用卓越前线原版引擎音（数据驱动，随 power/引擎状态播放）；</li>
     *   <li><b>AI 脚本飞行</b>（F9 召唤进场 / 悬停索降 / 飞离撤离）：由本模组播放自有旋翼声，
     *       因为此时没有真实驾驶员、卓越前线会把 power 压到 0 导致引擎循环静音；</li>
     * </ul>
     *
     * <p>判定「有玩家在机上」用乘客列表，不依赖客户端控制权，服务端/客户端语义一致。</p>
     */
    public static boolean shouldPlay(EntityHelicopter heli) {
        if (heli == null || heli.isRemoved() || !heli.isAlive()) {
            return false;
        }
        // 玩家在机上 = 正常驾驶：交还原版引擎音，不叠加我们的循环声
        if (hasPlayerAboard(heli)) {
            return false;
        }
        // 只有 AI 脚本飞行才归本模组接管
        boolean scripted = heli.isRappelling()
                || heli.getRappelLockPos() != null
                || heli.getAutopilotTarget() != null;
        if (!scripted) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null
                || heli.distanceToSqr(minecraft.player) <= MAX_DISTANCE_SQR;
    }

    /** 机上是否有玩家（= 这架是玩家在开，声音交给卓越前线原版）。 */
    private static boolean hasPlayerAboard(EntityHelicopter heli) {
        for (net.minecraft.world.entity.Entity passenger : heli.getPassengers()) {
            if (passenger instanceof net.minecraft.world.entity.player.Player) {
                return true;
            }
        }
        return false;
    }

    /** 回收已经自行停止的实例（实体被卸载时不会再调用 tick）。 */
    private static void pruneStopped() {
        Iterator<Map.Entry<Integer, HelicopterEngineLoop>> it = LOOPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, HelicopterEngineLoop> entry = it.next();
            if (entry.getValue().finished()) {
                STARTED_AT.remove(entry.getKey());
                it.remove();
            }
        }
    }

    /** 退出世界 / 换维度时清空。 */
    public static void clear() {
        for (HelicopterEngineLoop loop : LOOPS.values()) {
            loop.stopNow();
        }
        LOOPS.clear();
        STARTED_AT.clear();
    }
}

