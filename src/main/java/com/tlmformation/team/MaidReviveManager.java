package com.tlmformation.team;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTombstoneEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.formation.TargetSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidReviveManager {
    private static final String TAG_PENDING = "TLMDeadMaids";
    private static final String TAG_NBT = "MaidNbt";
    private static final String TAG_ID = "MaidId";
    private static final String TAG_TEAM = "Team";
    private static final String TAG_TIME = "DeathTime";
    private static final String TAG_NAME = "MaidName";
    private static final long WAIT_TICKS = 12000L;

    /** Authoritative in-memory cache; not a local variable. */
    private static final Map<UUID, PendingMaid> PENDING = new ConcurrentHashMap<>();

    private MaidReviveManager() {
    }

    public record PendingMaid(UUID owner, UUID maidId, int team,
                              long deathTime, String name, CompoundTag nbt) {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMaidDeath(MaidDeathEvent event) {
        if (event.isCanceled()) return;
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide
                || !(maid.getOwner() instanceof ServerPlayer player)) return;

        capture(player, maid);
        // Cancel before super.die(): no drops, no tombstone, no death loot.
        event.setCanceled(true);
        maid.discard();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)
                || maid.level().isClientSide
                || !(maid.getOwner() instanceof ServerPlayer player)
                || isWaiting(player, maid.getUUID())) {
            return;
        }
        capture(player, maid);
        event.setCanceled(true);
        maid.discard();
    }

    @SubscribeEvent
    public static void onTombstone(MaidTombstoneEvent event) {
        event.setCanceled(true);
    }

    public static void cacheDowned(ServerPlayer player, EntityMaid maid) {
        capture(player, maid);
    }

    private static void capture(ServerPlayer player, EntityMaid maid) {
        CompoundTag nbt = new CompoundTag();
        maid.saveWithoutId(nbt);
        nbt.putUUID("UUID", maid.getUUID());
        nbt.putString("id", EntityType.getKey(maid.getType()).toString());

        int team = MaidTeamManager.getTeamIndex(player, maid.getUUID());
        long now = player.server.overworld().getGameTime();
        String name = maid.getName().getString();

        PendingMaid pending = new PendingMaid(player.getUUID(), maid.getUUID(),
                team, now, name, nbt.copy());
        PENDING.put(maid.getUUID(), pending);

        CompoundTag entry = new CompoundTag();
        entry.putUUID(TAG_ID, maid.getUUID());
        entry.putInt(TAG_TEAM, team);
        entry.putLong(TAG_TIME, now);
        entry.putString(TAG_NAME, name);
        entry.put(TAG_NBT, nbt.copy());

        ListTag list = player.getPersistentData().getList(TAG_PENDING, Tag.TAG_COMPOUND);
        boolean exists = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).getUUID(TAG_ID).equals(maid.getUUID())) {
                exists = true;
                break;
            }
        }
        if (!exists) list.add(entry);
        player.getPersistentData().put(TAG_PENDING, list);

        TLMFormationMod.LOGGER.info("保存死亡女仆 NBT: " + name);
        TLMFormationMod.LOGGER.info("死亡缓存数量: {}", PENDING.size());

        MaidDeathCoordinates.clear(player, maid);
        MaidTeamManager.sync(player);
    }

    /** Restores the static cache after a server restart from player persistent data. */
    private static void hydrate(ServerPlayer player) {
        ListTag list = player.getPersistentData().getList(TAG_PENDING, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID id = entry.getUUID(TAG_ID);
            if (PENDING.containsKey(id)) continue;
            PENDING.put(id, new PendingMaid(player.getUUID(), id,
                    entry.getInt(TAG_TEAM), entry.getLong(TAG_TIME),
                    entry.getString(TAG_NAME), entry.getCompound(TAG_NBT).copy()));
            TLMFormationMod.LOGGER.info("从玩家存档恢复死亡缓存: {}", entry.getString(TAG_NAME));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long now = event.getServer().overworld().getGameTime();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            hydrate(player);
            revive(player, now, false);
        }
    }

    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        TLMFormationMod.LOGGER.info("触发睡眠复活检测");
        MinecraftServer server = level.getServer();
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            hydrate(player);
            revive(player, now, true);
        }
    }

    public static boolean isWaiting(ServerPlayer player, UUID maidId) {
        if (PENDING.containsKey(maidId)) return true;
        ListTag list = player.getPersistentData().getList(TAG_PENDING, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).getUUID(TAG_ID).equals(maidId)) return true;
        }
        return false;
    }

    private static void revive(ServerPlayer player, long now, boolean sleepRevive) {
        List<PendingMaid> targets = new ArrayList<>();
        for (PendingMaid pending : PENDING.values()) {
            if (!pending.owner().equals(player.getUUID())) continue;
            if (sleepRevive || now - pending.deathTime() >= WAIT_TICKS) {
                targets.add(pending);
            }
        }
        if (targets.isEmpty()) return;

        TLMFormationMod.LOGGER.info("复活检测: 玩家={}, 数量={}, 原因={}",
                player.getName().getString(), targets.size(),
                sleepRevive ? "睡眠" : "10分钟");

        for (PendingMaid pending : targets) {
            if (spawn(player, pending)) {
                PENDING.remove(pending.maidId());
                removeNbtEntry(player, pending.maidId());
                TLMFormationMod.LOGGER.info("女仆复活成功: {}", pending.name());
            } else {
                TLMFormationMod.LOGGER.warn("女仆复活失败，保留缓存: {}", pending.name());
            }
        }
        MaidTeamManager.sync(player);
    }

    private static void removeNbtEntry(ServerPlayer player, UUID maidId) {
        ListTag list = player.getPersistentData().getList(TAG_PENDING, Tag.TAG_COMPOUND);
        ListTag remaining = new ListTag();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.getUUID(TAG_ID).equals(maidId)) remaining.add(entry);
        }
        player.getPersistentData().put(TAG_PENDING, remaining);
    }

    private static boolean spawn(ServerPlayer player, PendingMaid pending) {
        CompoundTag nbt = pending.nbt().copy();
        UUID id = pending.maidId();
        int team = pending.team();

        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        MaidTeamManager.TeamRally rally = MaidTeamManager.getRally(player, team);
        if (rally != null) {
            ServerLevel rallyLevel = player.server.getLevel(rally.dimension());
            if (rallyLevel != null) {
                level = rallyLevel;
                center = rally.position();
            }
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos centerPos = BlockPos.containing(center);
                level.getChunk((centerPos.getX() >> 4) + dx,
                        (centerPos.getZ() >> 4) + dz);
            }
        }

        Vec3 safe = findSafeSpawn(level, center);
        final Vec3 landing = safe != null ? safe : center;
        TLMFormationMod.LOGGER.info("尝试在坐标 {} {} {} 复活女仆 {}",
                landing.x, landing.y, landing.z, pending.name());

        final ServerLevel spawnLevel = level;
        Entity spawned = EntityType.loadEntityRecursive(nbt, spawnLevel, entity -> {
            entity.moveTo(landing.x, landing.y, landing.z, 0.0F, 0.0F);
            entity.setUUID(id);
            return entity;
        });
        if (!(spawned instanceof EntityMaid maid)) {
            TLMFormationMod.LOGGER.warn("EntityType.loadEntityRecursive 返回空/非女仆");
            return false;
        }

        // Explicitly add to world; the loader callback above only configures it.
        if (!spawnLevel.addFreshEntity(spawned)) {
            TLMFormationMod.LOGGER.warn("addFreshEntity 失败: {}", pending.name());
            return false;
        }

        maid.setUUID(id);
        maid.setOwnerUUID(player.getUUID());
        maid.setNoAi(false);
        maid.setHomeModeEnable(false);
        maid.setHealth(maid.getMaxHealth());
        MaidDeathCoordinates.clear(player, maid);

        spawnLevel.sendParticles(ParticleTypes.PORTAL, landing.x,
                landing.y + 1.0D, landing.z, 24, 0.4D, 0.6D, 0.4D, 0.03D);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.tlmformation.revive.at_rally"), true);
        return true;
    }

    @Nullable
    private static Vec3 findSafeSpawn(ServerLevel level, Vec3 center) {
        EntityMaid probe = EntityMaid.TYPE.create(level);
        if (probe == null) return null;
        for (int radius = 0; radius <= 32; radius += 2) {
            int samples = Math.max(8, radius * 2);
            for (int i = 0; i < samples; i++) {
                double angle = Math.PI * 2.0D * i / samples;
                Vec3 candidate = center.add(Math.cos(angle) * radius, 0.0D,
                        Math.sin(angle) * radius);
                BlockPos pos = BlockPos.containing(candidate);
                level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                Vec3 safe = TargetSafety.findSafe(level, probe, candidate);
                if (safe != null) return safe;
            }
        }
        return null;
    }
}
