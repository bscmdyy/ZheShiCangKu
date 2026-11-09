package com.tlmformation.chunk;

import com.tlmformation.TLMFormationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Permanent 5x5 chunk loading around a team rally. No maid tracking: as long
 * as the rally exists, its area stays Entity-Ticking loaded.
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RallyChunkManager {
    private static final int RADIUS = 2; // 5x5 chunks
    private static final Map<UUID, TempChunk> TEMP = new HashMap<>();

    private RallyChunkManager() {
    }

    public static void setRally(ServerPlayer player, int team, Vec3 position,
                                ResourceKey<net.minecraft.world.level.Level> dimension) {
        MinecraftServer server = player.server;
        FormationSavedData data = FormationSavedData.get(server);
        releaseRally(server, player.getUUID(), team);

        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return;
        }
        BlockPos center = BlockPos.containing(position);
        ChunkPos chunk = new ChunkPos(center);
        UUID ticket = ticketOwner(player.getUUID(), team);
        FormationSavedData.RallyRecord record = new FormationSavedData.RallyRecord(
                ticket, player.getUUID(), team, dimension.location(),
                chunk.x, chunk.z);
        data.putRally(record);
        forceArea(level, record, true);
    }

    public static void releaseRally(MinecraftServer server, UUID playerId, int team) {
        FormationSavedData data = FormationSavedData.get(server);
        FormationSavedData.RallyRecord record = data.getRally(playerId, team);
        if (record == null) {
            return;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(
                Registries.DIMENSION, record.dimension()));
        if (level != null) {
            forceArea(level, record, false);
        }
        data.removeRally(record.ticketOwner());
    }

    public static void releaseAllRallies(MinecraftServer server, UUID playerId) {
        for (FormationSavedData.RallyRecord record
                : FormationSavedData.get(server).allRallies().toArray(
                new FormationSavedData.RallyRecord[0])) {
            if (record.playerId().equals(playerId)) {
                ServerLevel level = server.getLevel(ResourceKey.create(
                        Registries.DIMENSION, record.dimension()));
                if (level != null) {
                    forceArea(level, record, false);
                }
                FormationSavedData.get(server).removeRally(record.ticketOwner());
            }
        }
    }

    /**
     * Temporary load for a death coordinate teleport. Released automatically
     * after 2400 ticks; the rally system remains permanent and untouched.
     */
    public static void tempLoadDeathChunk(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        UUID owner = UUID.nameUUIDFromBytes(("tlmdeath:" + level.dimension().location()
                + ":" + chunk.x + ":" + chunk.z).getBytes(StandardCharsets.UTF_8));
        ForgeChunkManager.forceChunk(level, TLMFormationMod.MOD_ID, owner,
                chunk.x, chunk.z, true, true);
        TEMP.put(owner, new TempChunk(level, chunk, level.getGameTime() + 2400));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.getServer().getTickCount() % 20 != 0 || TEMP.isEmpty()) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        for (Map.Entry<UUID, TempChunk> entry : TEMP.entrySet()) {
            if (now < entry.getValue().expires()) {
                continue;
            }
            TempChunk ticket = entry.getValue();
            ForgeChunkManager.forceChunk(ticket.level(), TLMFormationMod.MOD_ID,
                    entry.getKey(), ticket.chunk().x, ticket.chunk().z, false, true);
            TEMP.remove(entry.getKey());
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (FormationSavedData.RallyRecord record
                : FormationSavedData.get(event.getServer()).allRallies()) {
            ServerLevel level = event.getServer().getLevel(ResourceKey.create(
                    Registries.DIMENSION, record.dimension()));
            if (level != null) {
                forceArea(level, record, true);
            }
        }
    }

    private static void forceArea(ServerLevel level,
                                  FormationSavedData.RallyRecord record, boolean add) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                ForgeChunkManager.forceChunk(level, TLMFormationMod.MOD_ID,
                        record.ticketOwner(), record.chunkX() + dx,
                        record.chunkZ() + dz, add, true);
            }
        }
    }

    private static UUID ticketOwner(UUID playerId, int team) {
        return UUID.nameUUIDFromBytes(("tlmformation:rally:" + playerId + ":" + team)
                .getBytes(StandardCharsets.UTF_8));
    }

    private record TempChunk(ServerLevel level, ChunkPos chunk, long expires) {
    }
}
