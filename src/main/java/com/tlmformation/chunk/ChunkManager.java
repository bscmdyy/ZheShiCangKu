package com.tlmformation.chunk;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.config.FormationConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.List;
import java.util.UUID;

/**
 * Forge 1.20.1 chunk loading. forceChunk persists a UUID ticket in
 * ForcedChunksSavedData, so no old ChunkLoader/Ticket object is needed.
 */
public final class ChunkManager {
    private ChunkManager() {
    }

    public static boolean register(ServerPlayer player, EntityMaid maid) {
        if (!FormationConfig.CHUNK_LOADING_ENABLED.get()) {
            return false;
        }
        if (!player.getUUID().equals(maid.getOwnerUUID()) || !(maid.level() instanceof ServerLevel level)) {
            return false;
        }

        FormationSavedData data = FormationSavedData.get(level);
        if (data.isRegistered(maid.getUUID())) {
            return false;
        }
        if (data.recordsFor(player.getUUID()).size() >= FormationConfig.MAX_REGISTERED_MAIDS.get()) {
            return false;
        }

        ChunkPos pos = maid.chunkPosition();
        FormationSavedData.MaidRecord record = new FormationSavedData.MaidRecord(
                maid.getUUID(), player.getUUID(), level.dimension().location(), pos.x, pos.z);
        data.register(record);
        force(level, record, true);
        if (FormationConfig.DEBUG.get()) {
            TLMFormationMod.LOGGER.info("[TLMF] registered chunk loading for {} at {} {}",
                    maid.getUUID(), level.dimension().location(), pos);
        }
        return true;
    }

    public static boolean unregister(ServerPlayer player, EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return false;
        }
        return unregister(level, maid.getUUID());
    }

    public static boolean unregister(ServerLevel level, UUID maidId) {
        FormationSavedData data = FormationSavedData.get(level);
        FormationSavedData.MaidRecord record = data.get(maidId);
        if (record == null) {
            return false;
        }
        ServerLevel recordLevel = level.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, record.dimension()));
        if (recordLevel != null) {
            force(recordLevel, record, false);
        }
        data.unregister(maidId);
        return true;
    }

    public static boolean isRegistered(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return false;
        }
        return FormationSavedData.get(level).isRegistered(maid.getUUID());
    }

    public static int registeredCount(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        return FormationSavedData.get(level).recordsFor(player.getUUID()).size();
    }

    /**
     * Keep the ticket on the maid's current chunk. Called from LivingTickEvent.
     */
    public static void updateIfMoved(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        FormationSavedData data = FormationSavedData.get(level);
        FormationSavedData.MaidRecord record = data.get(maid.getUUID());
        if (record == null) {
            return;
        }
        ChunkPos pos = maid.chunkPosition();
        if (record.chunkX() == pos.x && record.chunkZ() == pos.z
                && record.dimension().equals(level.dimension().location())) {
            return;
        }

        ServerLevel oldLevel = level.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, record.dimension()));
        if (oldLevel != null) {
            force(oldLevel, record, false);
        }
        FormationSavedData.MaidRecord updated = new FormationSavedData.MaidRecord(
                record.maidId(), record.ownerId(), level.dimension().location(), pos.x, pos.z);
        data.register(updated);
        force(level, updated, true);
    }

    public static void onMaidDeath(EntityMaid maid) {
        if (maid.level() instanceof ServerLevel level) {
            unregister(level, maid.getUUID());
        }
    }

    /**
     * Remove stale Forge tickets on world load. The callback is registered in
     * FMLCommonSetupEvent.
     */
    public static void validateTickets(ServerLevel level, ForgeChunkManager.TicketHelper helper) {
        FormationSavedData data = FormationSavedData.get(level);
        boolean enabled = FormationConfig.CHUNK_LOADING_ENABLED.get();
        for (UUID uuid : List.copyOf(helper.getEntityTickets().keySet())) {
            if (!enabled || (!data.isRegistered(uuid) && !data.isRallyOwner(uuid) && !data.isHoldOwner(uuid))) {
                helper.removeAllTickets(uuid);
            }
        }
    }

    private static void force(ServerLevel level, FormationSavedData.MaidRecord record, boolean add) {
        boolean ticking = FormationConfig.CHUNK_TICKING.get();
        ForgeChunkManager.forceChunk(level, TLMFormationMod.MOD_ID, record.maidId(),
                record.chunkX(), record.chunkZ(), add, ticking);

        int radius = FormationConfig.CHUNK_RADIUS.get();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                ForgeChunkManager.forceChunk(level, TLMFormationMod.MOD_ID, record.maidId(),
                        record.chunkX() + dx, record.chunkZ() + dz, add, false);
            }
        }
    }
}
