package com.tlmformation.vehicle;

import com.tlmformation.TLMFormationMod;
import com.tlmformation.network.NetworkHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** F10 载具终端：槽位 0 = 战术小车。 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArmorReinforcementManager {
    public static final int SLOT_HAVOC = 0;
    public static final int SLOT_COUNT = 4;

    private static final Map<UUID, UUID> DEPLOYED = new HashMap<>();
    private static final Map<UUID, Integer> FOLLOW_DISTANCE = new HashMap<>();
    private static int tickCounter;

    private ArmorReinforcementManager() {
    }

    public static int getFollowDistance(ServerPlayer player) {
        return FOLLOW_DISTANCE.getOrDefault(player.getUUID(), EntityHavoc.DEFAULT_FOLLOW_DISTANCE);
    }

    public static void handleVehicleCommand(ServerPlayer player, int action) {
        EntityHavoc vehicle = deployed(player);
        if (vehicle == null || vehicle.isRecalled()) return;
        vehicle.setVehicleState(action == 0 ? VehicleState.FOLLOW : VehicleState.PATROL);
        sync(player);
    }

    public static void handleConfig(ServerPlayer player, int distance) {
        int clamped = Mth.clamp(distance, EntityHavoc.MIN_FOLLOW_DISTANCE, EntityHavoc.MAX_FOLLOW_DISTANCE);
        FOLLOW_DISTANCE.put(player.getUUID(), clamped);
        EntityHavoc vehicle = deployed(player);
        if (vehicle != null && !vehicle.isRecalled()) vehicle.setFollowDistance(clamped);
        sync(player);
    }

    public static void handle(ServerPlayer player, int slot) {
        try {
            handleInternal(player, slot);
        } catch (Throwable throwable) {
            TLMFormationMod.LOGGER.error("[Armor] handle failed slot " + slot, throwable);
            sync(player);
        }
    }

    private static void handleInternal(ServerPlayer player, int slot) {
        if (player.isSpectator()) return;
        if (slot == -1) { sync(player); return; }
        if (slot != SLOT_HAVOC) {
            player.displayClientMessage(Component.translatable("message.tlmformation.armor.slot_empty"), true);
            return;
        }
        EntityHavoc deployed = deployed(player);
        if (deployed != null) {
            player.displayClientMessage(Component.translatable("message.tlmformation.armor.unit_deployed"), true);
            sync(player);
            return;
        }
        summon(player);
    }

    private static Vec3 spawnPosition(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        net.minecraft.core.BlockPos pos = player.blockPosition().offset(
                (int) Math.round(look.x * 7.0D), 0, (int) Math.round(look.z * 7.0D));
        return new Vec3(pos.getX() + 0.5D, player.getY(), pos.getZ() + 0.5D);
    }

    private static Entity findEntity(ServerPlayer player, UUID uuid) {
        return player.serverLevel().getEntity(uuid);
    }

    private static void summon(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 spawn = spawnPosition(player);
        EntityHavoc havoc = HavocEntities.HAVOC.get().create(level);
        if (havoc == null) return;
        havoc.moveTo(spawn.x, spawn.y, spawn.z, player.getYRot(), 0.0F);
        havoc.setYBodyRot(player.getYRot());
        havoc.setYHeadRot(player.getYRot());
        havoc.setOwner(player);
        havoc.setRecalled(false);
        havoc.setNoAi(false);
        if (level.addFreshEntity(havoc)) {
            DEPLOYED.put(player.getUUID(), havoc.getUUID());
            FOLLOW_DISTANCE.put(player.getUUID(), havoc.getFollowDistance());
        }
        sync(player);
    }

    /** 4 = 部署（活着传送到身边，死了新建） 5 = 撤离（直接消失）。 */
    public static void handleUnitAction(ServerPlayer player, int slot, int action) {
        if (player == null || player.isSpectator()) return;
        if (action == 4) {
            EntityHavoc havoc = deployed(player);
            if (havoc != null && havoc.isAlive()) {
                Vec3 spawn = spawnPosition(player);
                havoc.teleportTo(spawn.x, spawn.y, spawn.z);
                havoc.setRecalled(false);
                havoc.setNoAi(false);
            } else {
                summon(player);
            }
        } else if (action == 5) {
            EntityHavoc havoc = deployed(player);
            if (havoc != null) {
                havoc.discard();
                DEPLOYED.remove(player.getUUID());
            }
        }
        sync(player);
    }

    @Nullable
    public static EntityHavoc deployed(ServerPlayer player) {
        UUID tracked = DEPLOYED.get(player.getUUID());
        if (tracked != null) {
            Entity entity = findEntity(player, tracked);
            if (entity instanceof EntityHavoc havoc && havoc.isAlive()) {
                FOLLOW_DISTANCE.putIfAbsent(player.getUUID(), havoc.getFollowDistance());
                return havoc;
            }
            DEPLOYED.remove(player.getUUID());
        }
        EntityHavoc adopted = player.serverLevel().getEntitiesOfClass(EntityHavoc.class,
                        player.getBoundingBox().inflate(64.0D),
                        havoc -> havoc.isAlive() && havoc.isOwnedBy(player))
                .stream().findFirst().orElse(null);
        if (adopted != null) DEPLOYED.put(player.getUUID(), adopted.getUUID());
        return adopted;
    }

    public static int deployedMask(ServerPlayer player) {
        return deployed(player) != null ? (1 << SLOT_HAVOC) : 0;
    }

    public static void sync(ServerPlayer player) {
        NetworkHandler.sendArmorState(player, deployedMask(player), getFollowDistance(player));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (++tickCounter % 20 != 0 || DEPLOYED.isEmpty()) return;
        Iterator<Map.Entry<UUID, UUID>> it = DEPLOYED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            Entity entity = findEntity(player, entry.getValue());
            if (!(entity instanceof EntityHavoc) || !entity.isAlive()) {
                it.remove();
                sync(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
}
