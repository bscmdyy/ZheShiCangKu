package com.tlmformation.chunk;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.formation.FormationManager;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ChunkEvents {
    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (maid.tickCount % 20 == 0 && ChunkManager.isRegistered(maid)) {
            ChunkManager.updateIfMoved(maid);
        }
    }

    @SubscribeEvent
    public void onMaidDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            ChunkManager.onMaidDeath(maid);
            FormationManager.onMaidDeath(maid);
        }
    }

    @SubscribeEvent
    public void onMaidChangeDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            ChunkManager.onMaidDeath(maid);
            FormationManager.onMaidChangedDimension(maid);
        }
    }
}
