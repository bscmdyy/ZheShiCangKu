package com.tlmformation.team;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.chunk.MaidChunkTicketManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 按 UUID 找回女仆（必要时加载区块），供「硬救援」使用。
 *
 * <p>查找顺序（从便宜到贵，任何一步命中就返回）：</p>
 * <ol>
 *   <li>所有维度里已加载的实体；</li>
 *   <li>战术/据点区块票据记录（{@link MaidChunkTicketManager#findEntity} 会按记录加载区块）；</li>
 *   <li>玩家 NBT 备份里的坐标 + 维度（可跨维度，加载对应区块后取实体）；</li>
 *   <li>死亡坐标记录里的维度（同样加载区块）。</li>
 * </ol>
 *
 * <p>注意：这里只负责“找到”，不做传送；也不会从备份 NBT 重新生成实体（避免复制女仆）。</p>
 */
public final class MaidLocator {
    private MaidLocator() {
    }

    @Nullable
    public static EntityMaid findOrLoad(MinecraftServer server, ServerPlayer player, UUID maidId) {
        if (server == null || maidId == null) {
            return null;
        }
        // 1) 已加载
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(maidId) instanceof EntityMaid maid && maid.isAlive()) {
                return maid;
            }
        }
        // 2) 区块票据记录
        try {
            EntityMaid byTicket = MaidChunkTicketManager.findEntity(server, maidId);
            if (byTicket != null && byTicket.isAlive()) {
                return byTicket;
            }
        } catch (Throwable ignored) {
            // 票据数据损坏时继续走备份
        }
        // 3) 玩家 NBT 备份（含维度）
        try {
            CompoundTag backup = MaidBackupManager.read(player, maidId);
            if (backup != null) {
                EntityMaid byBackup = findAroundBackup(server, maidId, backup);
                if (byBackup != null) {
                    return byBackup;
                }
            }
        } catch (Throwable ignored) {
            // 备份损坏时继续走死亡坐标
        }
        // 4) 死亡坐标（可能跨维度）
        try {
            MaidDeathCoordinates.Info death = MaidDeathCoordinates.read(player, maidId);
            if (death != null) {
                ServerLevel level = levelOf(server, death.dimension());
                if (level != null) {
                    return findAt(level, maidId, death.x(), death.z());
                }
            }
        } catch (Throwable ignored) {
            // 最后一步了，失败就返回 null
        }
        return null;
    }

    @Nullable
    private static EntityMaid findAroundBackup(MinecraftServer server, UUID maidId, CompoundTag backup) {
        if (!backup.contains("Pos", Tag.TAG_LIST)) {
            return null;
        }
        ListTag pos = backup.getList("Pos", Tag.TAG_DOUBLE);
        if (pos.size() < 3) {
            return null;
        }
        double x = pos.getDouble(0);
        double z = pos.getDouble(2);
        // 优先按备份记录的维度找，其次全维度兜底
        ServerLevel preferred = backup.contains("BackupDim", Tag.TAG_STRING)
                ? levelOf(server, backup.getString("BackupDim")) : null;
        if (preferred != null) {
            EntityMaid maid = findAt(preferred, maidId, x, z);
            if (maid != null) {
                return maid;
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level == preferred) {
                continue;
            }
            EntityMaid maid = findAt(level, maidId, x, z);
            if (maid != null) {
                return maid;
            }
        }
        return null;
    }

    /** 在 (x,z) 周围 3×3 区块加载后查找实体。 */
    @Nullable
    private static EntityMaid findAt(ServerLevel level, UUID maidId, double x, double z) {
        int cx = ((int) Math.floor(x)) >> 4;
        int cz = ((int) Math.floor(z)) >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunk(cx + dx, cz + dz);
            }
        }
        return level.getEntity(maidId) instanceof EntityMaid maid && maid.isAlive() ? maid : null;
    }

    @Nullable
    private static ServerLevel levelOf(MinecraftServer server, @Nullable String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return null;
        }
        try {
            ResourceLocation id = new ResourceLocation(dimension);
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 该女仆当前是否已经“处于玩家可安心操作的状态”（已加载且同维度）。 */
    public static boolean isLoadedNear(ServerPlayer player, UUID maidId, double maxDistance) {
        for (ServerLevel level : player.server.getAllLevels()) {
            if (level.getEntity(maidId) instanceof EntityMaid maid && maid.isAlive()) {
                return maid.level() == player.level()
                        && maid.distanceToSqr(player) <= maxDistance * maxDistance;
            }
        }
        return false;
    }

    /** 便捷：所有维度里已加载的女仆（不加载区块）。 */
    @Nullable
    public static EntityMaid findLoaded(MinecraftServer server, UUID maidId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(maidId) instanceof EntityMaid maid && maid.isAlive()) {
                return maid;
            }
        }
        return null;
    }

    /** 维度 key（调试用）。 */
    public static String dimensionOf(Level level) {
        return level.dimension().location().toString();
    }
}
