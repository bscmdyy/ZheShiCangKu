package com.tlmformation.team;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.formation.TargetSafety;
import com.tlmformation.network.NetworkHandler;
import com.tlmformation.network.S2CGrandMode;
import com.tlmformation.tactical.TacticalCommand;
import com.tlmformation.tactical.TacticalManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Instant tactical assembly, safe retreat and the F8 grand-formation mode. */
public final class GrandAssembleManager {
    private static final Set<UUID> ACTIVE = new HashSet<>();

    private GrandAssembleManager() {
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.contains(player.getUUID());
    }

    public static void set(ServerPlayer player, boolean active) {
        if (active) ACTIVE.add(player.getUUID());
        else ACTIVE.remove(player.getUUID());
        NetworkHandler.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new S2CGrandMode(active));
    }

    public static void assemble(ServerPlayer player) {
        ServerLevel targetLevel = player.serverLevel();
        List<UUID> ids = MaidTeamManager.allMembers(player);
        Vec3 forward = player.getLookAngle();
        Vec3 flat = new Vec3(forward.x, 0.0D, forward.z);
        if (flat.lengthSqr() < 1.0E-4D) flat = new Vec3(0.0D, 0.0D, 1.0D);
        flat = flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0.0D, flat.x);

        for (int i = 0; i < ids.size(); i++) {
            EntityMaid maid = findMaid(player, ids.get(i));
            if (maid == null || !maid.isAlive()) continue;

            int row = i / 2;
            double side = (i % 2 == 0) ? -1.5D : 1.5D;
            // Row 0: +2, row 1: -1, row 2: -4, row 3: -7...
            double z = row == 0 ? 2.0D
                    : row == 1 ? -1.0D
                    : -1.0D - (row - 1) * 3.0D;
            Vec3 candidate = player.position()
                    .add(right.scale(side))
                    .add(flat.scale(z));

            BlockPos safe = findSafeLocation(targetLevel,
                    BlockPos.containing(candidate), 3);
            if (safe == null) {
                safe = findSafeLocation(targetLevel,
                        player.blockPosition(), 8);
            }
            if (safe == null) {
                TLMFormationMod.LOGGER.warn("无法为女仆找到安全集结落点: {}",
                        maid.getName().getString());
                continue;
            }

            com.tlmformation.vehicle.VehicleMaidRelease.release(maid);
            maid.teleportTo(targetLevel,
                    safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                    java.util.Set.of(), player.getYRot(), 0.0F);
            enableNativeFollow(maid);
            maid.setSchedule(com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule.ALL);
            TacticalManager.clear(maid);
            maid.setTarget(null);
            maid.setHealth(maid.getMaxHealth());
            com.tlmformation.formation.FormationManager.engage(maid);
        }

        // Teleport first, then lock the mode.
        set(player, true);
        MaidTeamManager.sync(player);
        player.displayClientMessage(Component.translatable("message.tlmformation.grand.assembled"), false);
    }

    /**
     * Safe landing: solid block below + two blocks of collision-free space.
     * Searches outward in rings; radius >= 8 starts at 5 blocks as requested.
     */
    @javax.annotation.Nullable
    public static BlockPos findSafeLocation(Level level, BlockPos center, int radius) {
        int start = radius >= 8 ? 5 : 1;
        for (int r = start; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 6; dy >= -8; dy--) {
                        BlockPos feet = center.offset(dx, dy, dz);
                        if (!level.isLoaded(feet)
                                || !level.isLoaded(feet.above())) continue;
                        boolean ground = !level.getBlockState(feet.below())
                                .getCollisionShape(level, feet.below()).isEmpty();
                        boolean feetFree = level.getBlockState(feet)
                                .getCollisionShape(level, feet).isEmpty();
                        boolean headFree = level.getBlockState(feet.above())
                                .getCollisionShape(level, feet.above()).isEmpty();
                        if (ground && feetFree && headFree) {
                            return feet;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static void defend(ServerPlayer player) {
        // The retreat action is the only unlock path.
        set(player, false);

        List<UUID> ids = MaidTeamManager.allMembers(player);
        for (UUID id : ids) {
            EntityMaid maid = findMaid(player, id);
            if (maid == null || !maid.isAlive()) continue;

            int team = MaidTeamManager.getTeamIndex(player, id);
            MaidTeamManager.TeamRally rally = MaidTeamManager.getRally(player, team);

            ServerLevel level = player.serverLevel();
            BlockPos center = player.blockPosition();
            if (rally != null) {
                ServerLevel rallyLevel = player.server.getLevel(rally.dimension());
                if (rallyLevel != null) {
                    level = rallyLevel;
                    center = BlockPos.containing(rally.position());
                }
            }

            BlockPos safe = findSafeLocation(level, center, 32);
            if (safe == null) safe = findSafeLocation(level, center, 8);
            if (safe == null) {
                TLMFormationMod.LOGGER.warn("无法为女仆找到安全撤离落点: {}",
                        maid.getName().getString());
                continue;
            }

            com.tlmformation.vehicle.VehicleMaidRelease.release(maid);
            maid.teleportTo(level, safe.getX() + 0.5D, safe.getY(),
                    safe.getZ() + 0.5D, java.util.Set.of(),
                    player.getYRot(), 0.0F);
            disableNativeFollow(maid);
            maid.setSchedule(com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule.DAY);
            TacticalManager.clear(maid);
            maid.setTarget(null);
            maid.setHealth(maid.getMaxHealth());
            com.tlmformation.formation.FormationManager.engage(maid);
        }

        MaidTeamManager.sync(player);
        player.displayClientMessage(Component.translatable("message.tlmformation.grand.retreated"), false);
    }

    private static void enableNativeFollow(EntityMaid maid) {
        // Native "follow" switch, identical to the maid GUI follow button.
        maid.restrictTo(BlockPos.ZERO,
                com.github.tartaricacid.touhoulittlemaid.config.subconfig
                        .MaidConfig.MAID_NON_HOME_RANGE.get());
        maid.setHomeModeEnable(false);
    }

    private static void disableNativeFollow(EntityMaid maid) {
        // Native "cancel follow / stay home" switch.
        maid.getSchedulePos().setHomeModeEnable(maid, maid.blockPosition());
        maid.setHomeModeEnable(true);
    }

    public static void sync(ServerPlayer player) {        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2CGrandMode(isActive(player)));
    }

    public static void logout(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    @javax.annotation.Nullable
    private static EntityMaid findMaid(ServerPlayer player, UUID id) {
        for (ServerLevel level : player.server.getAllLevels()) {
            if (level.getEntity(id) instanceof EntityMaid maid && maid.isAlive()) {
                return maid;
            }
        }

        CompoundTag backup = MaidBackupManager.read(player, id);
        if (backup != null && backup.contains("Pos", Tag.TAG_LIST)) {
            ListTag pos = backup.getList("Pos", Tag.TAG_DOUBLE);
            if (pos.size() >= 3) {
                double x = pos.getDouble(0);
                double z = pos.getDouble(2);
                for (ServerLevel level : player.server.getAllLevels()) {
                    level.getChunk((int) Math.floor(x) >> 4,
                            (int) Math.floor(z) >> 4);
                    if (level.getEntity(id) instanceof EntityMaid maid
                            && maid.isAlive()) {
                        return maid;
                    }
                }
            }
        }
        return null;
    }
}
