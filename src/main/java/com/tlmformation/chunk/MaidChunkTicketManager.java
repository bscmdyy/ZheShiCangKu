package com.tlmformation.chunk;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.TLMFormationMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Per-maid Entity-Ticking ticket for maids that are NOT following the player
 * (station/hold/guard/mark or parked at a rally). Tickets are debounced by
 * NBT lastChunkX/lastChunkZ, so crossing a chunk boundary performs exactly one
 * release + one force.
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidChunkTicketManager {
    private static final String LAST_X = "lastChunkX";
    private static final String LAST_Z = "lastChunkZ";

    private MaidChunkTicketManager() {
    }

    public static void activate(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) return;

        FormationSavedData data = FormationSavedData.get(level);
        FormationSavedData.HoldRecord old = data.getHoldByMaid(maid.getUUID());
        ChunkPos pos = maid.chunkPosition();

        if (old == null) {
            FormationSavedData.HoldRecord record = new FormationSavedData.HoldRecord(
                    ticketOwner(maid.getUUID()), maid.getUUID(),
                    level.dimension().location(), pos.x, pos.z);
            data.putHold(record);
            force(level, record, true);
            setLast(maid, pos);
        } else {
            update(maid);
        }
    }

    /** Called when the maid is summoned/following/dead/removed. */
    public static void release(MinecraftServer server, UUID maidId) {
        FormationSavedData data = FormationSavedData.get(server);
        FormationSavedData.HoldRecord record = data.getHoldByMaid(maidId);
        if (record == null) return;

        ServerLevel level = server.getLevel(ResourceKey.create(
                Registries.DIMENSION, record.dimension()));
        if (level != null) {
            force(level, record, false);
        }
        data.removeHold(record.ticketOwner());
    }

    public static void release(ServerLevel level, UUID maidId) {
        release(level.getServer(), maidId);
    }

    @Nullable
    public static EntityMaid findEntity(MinecraftServer server, UUID maidId) {
        FormationSavedData.HoldRecord record = FormationSavedData.get(server)
                .getHoldByMaid(maidId);
        if (record != null) {
            ServerLevel level = server.getLevel(ResourceKey.create(
                    Registries.DIMENSION, record.dimension()));
            if (level != null) {
                level.getChunk(record.chunkX(), record.chunkZ());
                Entity entity = level.getEntity(maidId);
                if (entity instanceof EntityMaid maid) return maid;
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(maidId);
            if (entity instanceof EntityMaid maid) return maid;
        }
        return null;
    }

    public static void update(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) return;

        FormationSavedData data = FormationSavedData.get(level);
        FormationSavedData.HoldRecord record = data.getHoldByMaid(maid.getUUID());
        if (record == null) return;

        ChunkPos pos = maid.chunkPosition();
        if (record.chunkX() == pos.x && record.chunkZ() == pos.z
                && record.dimension().equals(level.dimension().location())) {
            setLast(maid, pos); // no ticket churn
            return;
        }

        // Debounced boundary crossing: release old, force new, update NBT.
        force(level, record, false);
        FormationSavedData.HoldRecord updated = new FormationSavedData.HoldRecord(
                record.ticketOwner(), record.maidId(),
                level.dimension().location(), pos.x, pos.z);
        data.putHold(updated);
        force(level, updated, true);
        setLast(maid, pos);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (FormationSavedData.HoldRecord record
                : FormationSavedData.get(server).allHolds().toArray(
                new FormationSavedData.HoldRecord[0])) {
            ServerLevel level = server.getLevel(ResourceKey.create(
                    Registries.DIMENSION, record.dimension()));
            if (level == null) {
                release(server, record.maidId());
                continue;
            }
            level.getChunk(record.chunkX(), record.chunkZ());
            Entity entity = level.getEntity(record.maidId());
            if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) {
                release(server, record.maidId());
            } else {
                update(maid);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        for (FormationSavedData.HoldRecord record
                : FormationSavedData.get(event.getServer()).allHolds()) {
            ServerLevel level = event.getServer().getLevel(ResourceKey.create(
                    Registries.DIMENSION, record.dimension()));
            if (level != null) {
                force(level, record, true);
            }
        }
    }

    private static void force(ServerLevel level, FormationSavedData.HoldRecord record,
                              boolean add) {
        ForgeChunkManager.forceChunk(level, TLMFormationMod.MOD_ID,
                record.ticketOwner(), record.chunkX(), record.chunkZ(), add, true);
    }

    private static void setLast(EntityMaid maid, ChunkPos pos) {
        maid.getPersistentData().putInt(LAST_X, pos.x);
        maid.getPersistentData().putInt(LAST_Z, pos.z);
    }

    private static UUID ticketOwner(UUID maidId) {
        return UUID.nameUUIDFromBytes(("tlmformation:maidchunk:" + maidId)
                .getBytes(StandardCharsets.UTF_8));
    }
}
