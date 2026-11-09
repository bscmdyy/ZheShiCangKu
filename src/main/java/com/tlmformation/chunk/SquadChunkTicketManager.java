package com.tlmformation.chunk;

import com.tlmformation.TLMFormationMod;
import com.tlmformation.config.FormationConfig;
import com.tlmformation.team.MaidTeamManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 部署期「小队区块常加载」。
 *
 * <p>空投小队在场（{@code airdropDeployed = true}）时，围绕每名女仆所在区块强制加载
 * 3×3（可配）区块，保证她们始终处于 ticking 状态：不会被卸载、能正常战斗/寻路，
 * 也让 F9 面板始终拿得到实体。</p>
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>票据 owner = 由玩家 UUID 派生（{@code tlmformation:squadchunk:&lt;uuid&gt;}），与女仆个人票据分开；</li>
 *   <li>每 20 tick 刷新一次：女仆跨区块时释放旧块、签新块（与 {@link MaidChunkTicketManager} 同样的防抖思路）；</li>
 *   <li>受 {@code squadChunks.enabled / radius / ticking / maxChunks} 配置控制；</li>
 *   <li>撤离完成、队伍清空、玩家登出、服务器关闭时全部释放，避免票据泄漏（airdropDeployed 卡 true 的
 *       异常情况由 {@code SquadReconcileManager} 每 5 秒纠正）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SquadChunkTicketManager {
    /** playerId -> 已强制加载的区块 key 集合。 */
    private static final Map<UUID, Set<Long>> ACTIVE = new HashMap<>();
    /** playerId -> 维度 key（用于释放时找回正确的 ServerLevel）。 */
    private static final Map<UUID, String> DIMENSIONS = new HashMap<>();

    private SquadChunkTicketManager() {
    }

    /** 小队登记为“已部署”后调用。 */
    public static void activate(ServerPlayer player) {
        if (player == null || !FormationConfig.SQUAD_CHUNK_ENABLED.get()) {
            return;
        }
        refresh(player);
    }

    /** 小队不再部署（撤离/清空/登出）时调用：释放全部票据。 */
    public static void release(ServerPlayer player) {
        if (player == null) {
            return;
        }
        release(player.server, player.getUUID());
    }

    private static void release(MinecraftServer server, UUID playerId) {
        Set<Long> keys = ACTIVE.remove(playerId);
        String dim = DIMENSIONS.remove(playerId);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        ServerLevel level = levelOf(server, dim);
        if (level == null) {
            return;
        }
        UUID owner = ticketOwner(playerId);
        for (long key : keys) {
            try {
                ForgeChunkManager.forceChunk(level, TLMFormationMod.MOD_ID, owner,
                        ChunkPos.getX(key), ChunkPos.getZ(key), false, true);
            } catch (Throwable ignored) {
                // 维度已卸载等情况：忽略
            }
        }
    }

    /** 每 20 tick 刷新一次：按女仆当前位置重新签票据（跨区块时释放旧块）。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!FormationConfig.SQUAD_CHUNK_ENABLED.get()) {
            return;
        }
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            try {
                if (MaidTeamManager.isAirdropDeployed(player)) {
                    refresh(player);
                } else {
                    release(player);
                }
            } catch (Throwable throwable) {
                TLMFormationMod.LOGGER.error(
                        "[SquadChunks] refresh failed for {}", player.getUUID(), throwable);
            }
        }
    }

    private static void refresh(ServerPlayer player) {
        PlayerSquad squad = resolveSquad(player);
        if (squad.level == null || squad.chunks.isEmpty()) {
            release(player);
            return;
        }
        UUID playerId = player.getUUID();
        Set<Long> previous = ACTIVE.getOrDefault(playerId, Set.of());
        String previousDim = DIMENSIONS.get(playerId);
        UUID owner = ticketOwner(playerId);
        // 维度变了：先释放旧维度的块
        if (previousDim != null && !previousDim.equals(squad.dimension)) {
            release(player.server, playerId);
            previous = Set.of();
        }
        // 释放不再需要的
        for (long key : previous) {
            if (!squad.chunks.contains(key)) {
                ForgeChunkManager.forceChunk(squad.level, TLMFormationMod.MOD_ID, owner,
                        ChunkPos.getX(key), ChunkPos.getZ(key), false, true);
            }
        }
        // 新签
        boolean ticking = FormationConfig.SQUAD_CHUNK_TICKING.get();
        for (long key : squad.chunks) {
            if (!previous.contains(key)) {
                ForgeChunkManager.forceChunk(squad.level, TLMFormationMod.MOD_ID, owner,
                        ChunkPos.getX(key), ChunkPos.getZ(key), true, ticking);
            } else if (ticking) {
                // 已存在的票据补一次 ticking 标记（幂等）
                ForgeChunkManager.forceChunk(squad.level, TLMFormationMod.MOD_ID, owner,
                        ChunkPos.getX(key), ChunkPos.getZ(key), true, true);
            }
        }
        ACTIVE.put(playerId, squad.chunks);
        DIMENSIONS.put(playerId, squad.dimension);
    }

    private record PlayerSquad(ServerLevel level, String dimension, Set<Long> chunks) {
    }

    private static PlayerSquad resolveSquad(ServerPlayer player) {
        List<UUID> members = new java.util.ArrayList<>(MaidTeamManager.activeMembers(player));
        // 交换部署期间：旧小队正在等登机，也必须保持加载才能走到绳底爬上去
        for (UUID waiting : com.tlmformation.vehicle.AirdropManager.swapWaitingMembers(player)) {
            if (!members.contains(waiting)) {
                members.add(waiting);
            }
        }
        if (members.isEmpty()) {
            return new PlayerSquad(null, "", Set.of());
        }
        int radius = Math.max(0, FormationConfig.SQUAD_CHUNK_RADIUS.get());
        int max = Math.max(1, FormationConfig.SQUAD_CHUNK_MAX.get());
        Set<Long> chunks = new HashSet<>();
        ServerLevel level = null;
        String dimension = "";
        for (UUID id : members) {
            Entity entity = null;
            for (ServerLevel candidate : player.server.getAllLevels()) {
                Entity found = candidate.getEntity(id);
                if (found != null) {
                    entity = found;
                    level = candidate;
                    dimension = candidate.dimension().location().toString();
                    break;
                }
            }
            if (entity == null) {
                continue;
            }
            ChunkPos center = entity.chunkPosition();
            for (int dx = -radius; dx <= radius && chunks.size() < max; dx++) {
                for (int dz = -radius; dz <= radius && chunks.size() < max; dz++) {
                    chunks.add(ChunkPos.asLong(center.x + dx, center.z + dz));
                }
            }
            if (chunks.size() >= max) {
                break;
            }
        }
        return new PlayerSquad(level, dimension, chunks);
    }

    private static ServerLevel levelOf(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isBlank()) {
            return null;
        }
        try {
            return server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    new net.minecraft.resources.ResourceLocation(dimension)));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static UUID ticketOwner(UUID playerId) {
        return UUID.nameUUIDFromBytes(("tlmformation:squadchunk:" + playerId)
                .getBytes(StandardCharsets.UTF_8));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // 服务器关闭：票据由 Forge 持久化，但我们的内存表要清掉，避免下次启动误判
        ACTIVE.clear();
        DIMENSIONS.clear();
    }
}
