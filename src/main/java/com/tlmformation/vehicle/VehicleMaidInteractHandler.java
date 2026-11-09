package com.tlmformation.vehicle;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.TLMFormationMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 右键点击“坐在后座”的女仆：解除伪挂载，恢复自由 AI。
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VehicleMaidInteractHandler {
    private VehicleMaidInteractHandler() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!(target instanceof EntityMaid maid) || maid.level().isClientSide) {
            return;
        }
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        List<EntityHavoc> vehicles = level.getEntitiesOfClass(EntityHavoc.class,
                maid.getBoundingBox().inflate(8.0D),
                vehicle -> vehicle.isAlive() && maid.getUUID().toString()
                        .equals(vehicle.getAttachedMaidString()));
        if (!vehicles.isEmpty()) {
            vehicles.get(0).detachMaid();
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
