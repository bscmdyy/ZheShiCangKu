package com.tlmformation.vehicle;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 强制传送/撤退前的清理：如果女仆被伪挂载在战车上，
 * 先解绑 UUID、解除坐姿、恢复 AI，再执行传送，杜绝“虚空坐姿”卡死。
 */
public final class VehicleMaidRelease {
    private VehicleMaidRelease() {
    }

    public static void release(EntityMaid maid) {
        if (maid.level() instanceof ServerLevel level) {
            List<EntityHavoc> vehicles = level.getEntitiesOfClass(EntityHavoc.class,
                    maid.getBoundingBox().inflate(16.0D),
                    vehicle -> vehicle.isAlive()
                            && maid.getUUID().toString().equals(vehicle.getAttachedMaidString()));
            for (EntityHavoc vehicle : vehicles) {
                vehicle.detachMaid();
            }
        }
        maid.setNoAi(false);
        maid.setInSittingPose(false);
        maid.setDeltaMovement(Vec3.ZERO);
    }
}
