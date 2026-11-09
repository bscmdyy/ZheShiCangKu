package com.tlmformation.client.helicopter;

import com.tlmformation.vehicle.EntityHelicopter;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端索降状态缓存（由 {@code S2CRappelState} 写入）。
 *
 * <p>因为卓越前线是软依赖、且 {@code EntityHelicopter} 直接继承它的基类，
 * 这里不复用原版 {@code SynchedEntityData}，改为独立网络包同步，避免映射差异。</p>
 *
 * <p>除了阶段 / 剩余人数，还缓存服务端的悬停锁点：索降期间客户端要把机身
 * 钉在同一个点上，否则卓越前线的插值 + 客户端悬停补偿会让机身上下漂。</p>
 */
public final class RappelClientState {
    private static final Map<Integer, State> STATES = new ConcurrentHashMap<>();

    private RappelClientState() {
    }

    public static void update(int entityId, int phase, int remain,
                              double lockX, double lockY, double lockZ) {
        if (phase == 0) {
            STATES.remove(entityId);
        } else {
            STATES.put(entityId, new State(phase, remain, lockX, lockY, lockZ));
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.level.getEntity(entityId) instanceof EntityHelicopter heli) {
            heli.applySyncedRappelState(phase, remain, lockX, lockY, lockZ);
        }
    }

    public static int phase(int entityId) {
        State state = STATES.get(entityId);
        return state == null ? 0 : state.phase();
    }

    public static int remain(int entityId) {
        State state = STATES.get(entityId);
        return state == null ? 0 : state.remain();
    }

    public static void clear() {
        STATES.clear();
    }

    /** 渲染前把缓存状态写回实体，保证开舱门动画在“网络包先于实体加载”时也不丢。 */
    public static void syncToEntity(EntityHelicopter heli) {
        State state = STATES.get(heli.getId());
        if (state == null) {
            heli.applySyncedRappelState(0, 0, 0.0D, 0.0D, 0.0D);
            return;
        }
        heli.applySyncedRappelState(state.phase(), state.remain(),
                state.lockX(), state.lockY(), state.lockZ());
    }

    public record State(int phase, int remain, double lockX, double lockY, double lockZ) {
    }
}
