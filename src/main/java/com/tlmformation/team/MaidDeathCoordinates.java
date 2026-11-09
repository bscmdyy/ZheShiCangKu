package com.tlmformation.team;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.chunk.MaidChunkTicketManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Death is deliberately NOT cancelled. TLM creates its normal tombstone; we
 * only record where the maid died, in her own NBT and in the owner's player
 * data (the latter keeps the value readable after the entity is gone).
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidDeathCoordinates {
    private static final String TAG_ROOT = "TLMDeathCoords";

    private MaidDeathCoordinates() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof EntityMaid maid)
                || !(maid.getOwner() instanceof ServerPlayer player)
                || MaidReviveManager.isWaiting(player, maid.getUUID())) {
            return;
        }

        CompoundTag tag = maid.getPersistentData();
        tag.putDouble("LastDeathX", maid.getX());
        tag.putDouble("LastDeathY", maid.getY());
        tag.putDouble("LastDeathZ", maid.getZ());
        if (maid.level() instanceof net.minecraft.server.level.ServerLevel level) {
            MaidChunkTicketManager.release(level, maid.getUUID());
        }
        tag.putString("LastDeathDim", maid.level().dimension().location().toString());

        CompoundTag all = player.getPersistentData().getCompound(TAG_ROOT);
        CompoundTag entry = new CompoundTag();
        entry.putDouble("X", maid.getX());
        entry.putDouble("Y", maid.getY());
        entry.putDouble("Z", maid.getZ());
        entry.putString("Dim", maid.level().dimension().location().toString());
        entry.putString("Name", maid.getDisplayName().getString());
        all.put(maid.getUUID().toString(), entry);
        player.getPersistentData().put(TAG_ROOT, all);
        MaidTeamManager.sync(player);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof EntityMaid maid)
                || !maid.isAlive()
                || !(maid.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        // TLM revival / film re-creation produces a living maid. Clear any
        // stale death marker so the team card returns to the alive state.
        clear(player, maid);
    }

    /**
     * Safety net for shrine/film revivals and any modded revival path that
     * does not fire EntityJoinLevelEvent in the expected order. Once per
     * second, check every death-marked UUID; if a living maid with that UUID
     * exists, clear the marker and resync F9.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            CompoundTag all = player.getPersistentData().getCompound(TAG_ROOT);
            if (all.isEmpty()) continue;

            List<String> keys = new ArrayList<>(all.getAllKeys());
            for (String key : keys) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                EntityMaid alive = findAlive(event.getServer(), id);
                if (alive != null) {
                    clear(player, alive);
                }
            }
        }
    }

    @Nullable
    private static EntityMaid findAlive(MinecraftServer server, UUID id) {
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            net.minecraft.world.entity.Entity entity = level.getEntity(id);
            if (entity instanceof EntityMaid maid && maid.isAlive()) {
                return maid;
            }
        }
        return null;
    }

    public static void clear(ServerPlayer player, EntityMaid maid) {
        CompoundTag tag = maid.getPersistentData();
        tag.remove("LastDeathX");
        tag.remove("LastDeathY");
        tag.remove("LastDeathZ");
        tag.remove("LastDeathDim");
        tag.remove("TLMDead");

        CompoundTag all = player.getPersistentData().getCompound(TAG_ROOT);
        if (all.contains(maid.getUUID().toString(), CompoundTag.TAG_COMPOUND)) {
            all.remove(maid.getUUID().toString());
            player.getPersistentData().put(TAG_ROOT, all);
        }
        MaidTeamManager.sync(player);
    }

    @Nullable
    public static Info read(ServerPlayer player, UUID uuid) {
        CompoundTag all = player.getPersistentData().getCompound(TAG_ROOT);
        if (!all.contains(uuid.toString(), CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = all.getCompound(uuid.toString());
        return new Info(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                tag.getString("Dim"), tag.getString("Name"));
    }

    @Nullable
    public static Info read(EntityMaid maid) {
        CompoundTag tag = maid.getPersistentData();
        if (!tag.contains("LastDeathX")) {
            return null;
        }
        return new Info(tag.getDouble("LastDeathX"), tag.getDouble("LastDeathY"),
                tag.getDouble("LastDeathZ"), tag.getString("LastDeathDim"),
                maid.getDisplayName().getString());
    }

    public record Info(double x, double y, double z, String dimension, String name) {
    }
}
