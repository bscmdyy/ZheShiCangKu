package com.tlmformation.team;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.tlmformation.formation.FormationManager;
import com.tlmformation.chunk.MaidChunkTicketManager;
import com.tlmformation.chunk.SquadChunkTicketManager;
import com.tlmformation.formation.TargetSafety;
import com.tlmformation.config.FormationConfig;
import com.tlmformation.chunk.RallyChunkManager;
import com.tlmformation.network.S2CTeamData;
import com.tlmformation.sound.ModSounds;
import com.tlmformation.tactical.TacticalManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MaidTeamManager {
    public static final int TEAM_COUNT = 5;
    public static final int TEAM_SIZE = 6;
    private static final String TAG_ROOT = "TLMTeams";
    private static final Map<UUID, PlayerTeams> CACHE = new HashMap<>();

    private MaidTeamManager() {
    }

    public static void handle(ServerPlayer player, TeamAction action, int team, @Nullable UUID maid) {
        PlayerTeams data = get(player);
        if (GrandAssembleManager.isActive(player)
                && action != TeamAction.TACTICAL_ASSEMBLE
                && action != TeamAction.STRATEGIC_DEFEND
                && action != TeamAction.REQUEST) {
            player.displayClientMessage(Component.literal(
                    "集团体模式已锁定，请使用战术撤退"), true);
            return;
        }
        // 最高指令：撤回/前往据点 不受任何倒地状态限制（规则：逃兵机制已删除）
        switch (action) {
            case REQUEST -> {
            }
            case ADD_TEAM -> {
                if (data.teamCount < TEAM_COUNT) {
                    data.teamCount++;
                }
            }
            case DELETE_TEAM -> {
                int target = team >= 0 && team < data.teamCount
                        ? team : data.teamCount - 1;
                if (data.teamCount <= 1) {
                    return;
                }
                for (UUID id : data.teams[target].members) {
                    MaidChunkTicketManager.release(player.server, id);
                }
                RallyChunkManager.releaseRally(player.server, player.getUUID(), target);
                for (int i = target; i < data.teamCount - 1; i++) {
                    data.teams[i].members.clear();
                    data.teams[i].members.addAll(data.teams[i + 1].members);
                    data.teams[i].rally = data.teams[i + 1].rally;
                    data.teams[i].rallyDimension = data.teams[i + 1].rallyDimension;
                }
                data.teams[data.teamCount - 1].members.clear();
                data.teams[data.teamCount - 1].rally = null;
                data.teams[data.teamCount - 1].rallyDimension = null;
                data.teamCount--;
                if (data.active == target) data.active = -1;
                else if (data.active > target) data.active--;
            }
            case ADD -> {
                if (maid == null || team < 0 || team >= data.teamCount) {
                    return;
                }
                EntityMaid entity = resolve(player.server, maid);
                if (entity == null || !entity.isOwnedBy(player)) {
                    return;
                }
                data.removeFromAll(maid);
                if (data.teams[team].members.size() < TEAM_SIZE) {
                    data.teams[team].members.add(maid);
                    MaidBackupManager.save(player, entity);
                }
            }
            case REMOVE -> {
                if (maid == null) return;
                // 允许移除未加载/倒地/待复活的女仆（否则会卡死无法清理）
                data.removeFromAll(maid);
                if (player.serverLevel().getEntity(maid) == null) {
                    MaidDownedManager.isDowned(maid);
                }

            }
            case TACTICAL_ASSEMBLE -> {
                // 空投小队还在场时不允许集团集结（先把他们接走）
                if (com.tlmformation.vehicle.AirdropManager.hasDeployedSquad(player)) {
                    com.tlmformation.vehicle.AirdropManager.notifySquadDeployed(player);
                    return;
                }
                // 允许时仍然是原来的“瞬间传送集结”，不做任何改动
                GrandAssembleManager.assemble(player);
            }
            case STRATEGIC_DEFEND -> GrandAssembleManager.defend(player);
            case SET_RALLY -> {
                if (team < 0 || team >= data.teamCount) {
                    return;
                }
                data.teams[team].rally = player.position();
                data.teams[team].rallyDimension = player.level().dimension();
                RallyChunkManager.setRally(player, team, player.position(),
                        player.level().dimension());
                player.displayClientMessage(Component.literal(
                        "队伍" + (team + 1) + "据点已设置"), true);
            }
            case SUMMON -> {
                // 按下「部署」：先给对讲机反馈音（即使接下来被拦截也能听到）
                playRadioClick(player);
                // 上一支空投小队还没被直升机接走：拦截，绝不生成第二架直升机，
                // 也不能回落到下面的传送召唤（那会直接刷一队人出来）。
                if (com.tlmformation.vehicle.AirdropManager.hasDeployedSquad(player)) {
                    com.tlmformation.vehicle.AirdropManager.notifySquadDeployed(player);
                    return;
                }
                // F9「部署」：优先走直升机自动空投；找不到可空投的女仆时
                // 回退到原来的“传送召唤”，保证旧功能不丢。
                boolean started = false;
                try {
                    started = com.tlmformation.vehicle.AirdropManager.tryStart(player, team);
                } catch (Throwable throwable) {
                    com.tlmformation.TLMFormationMod.LOGGER.error(
                            "[Airdrop] tryStart failed", throwable);
                    // 已经生成直升机就别再传送召唤（否则会把刚登机的女仆拽出来）
                    started = com.tlmformation.vehicle.AirdropManager.isActive(player);
                }
                if (!started) {
                    summon(player, data, team);
                }
            }
            case TELEPORT_RALLY -> {
                TeamRally rally = getRally(player, team);
                if (rally == null) {
                    player.displayClientMessage(Component.translatable("message.tlmformation.team.no_rally_set")
                            .withStyle(ChatFormatting.RED), true);
                } else {
                    ServerLevel level = player.server.getLevel(rally.dimension());
                    if (level != null) {
                        player.teleportTo(level, rally.position().x, rally.position().y,
                                rally.position().z,
                                java.util.Set.<RelativeMovement>of(),
                                player.getYRot(), player.getXRot());
                    }
                    // 前往据点 = 最高级撤退：全队强制回基地（倒地者睡一觉复活）
                    if (team >= 0 && team < data.teamCount) {
                        for (EntityMaid memberMaid : resolveAll(player.server, data.teams[team].members)) {
                            forceRetreat(player, memberMaid, data.teams[team]);
                        }
                    }
                }
            }
            case TELEPORT_DEATH -> {
                MaidDeathCoordinates.Info info = maid == null ? null
                        : MaidDeathCoordinates.read(player, maid);
                if (info == null) {
                    player.displayClientMessage(Component.translatable("message.tlmformation.team.no_death_coords")
                            .withStyle(ChatFormatting.RED), true);
                } else {
                    ServerLevel level = player.server.getLevel(ResourceKey.create(
                            Registries.DIMENSION, new ResourceLocation(info.dimension())));
                    if (level != null) {
                        RallyChunkManager.tempLoadDeathChunk(level,
                                BlockPos.containing(info.x(), info.y(), info.z()));
                        Vec3 safe = findPlayerSafe(level, player, new Vec3(
                                info.x(), info.y(), info.z()));
                        player.teleportTo(level, safe.x, safe.y, safe.z,
                                java.util.Set.<RelativeMovement>of(),
                                player.getYRot(), player.getXRot());
                    }
                }
            }
            case TELEPORT_MAID -> {
                if (maid == null) {
                    return;
                }
                EntityMaid living = resolve(player.server, maid);
                if (living != null && living.isAlive()) {
                    ServerLevel level = (ServerLevel) living.level();
                    Vec3 safe = findPlayerSafe(level, player, living.position());
                    player.teleportTo(level, safe.x, safe.y, safe.z,
                            java.util.Set.<RelativeMovement>of(),
                            player.getYRot(), player.getXRot());
                } else {
                    MaidDeathCoordinates.Info info = MaidDeathCoordinates.read(player, maid);
                    if (info == null) {
                        player.displayClientMessage(Component.translatable("message.tlmformation.team.no_teleport_pos")
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    ServerLevel level = player.server.getLevel(ResourceKey.create(
                            Registries.DIMENSION, new ResourceLocation(info.dimension())));
                    if (level != null) {
                        RallyChunkManager.tempLoadDeathChunk(level,
                                BlockPos.containing(info.x(), info.y(), info.z()));
                        Vec3 safe = findPlayerSafe(level, player, new Vec3(
                                info.x(), info.y(), info.z()));
                        player.teleportTo(level, safe.x, safe.y, safe.z,
                                java.util.Set.<RelativeMovement>of(),
                                player.getYRot(), player.getXRot());
                    }
                }
            }
            case OPEN_BACKPACK -> {
                EntityMaid backpackMaid = resolve(player.server, maid);
                if (backpackMaid != null) {
                    // Opens TLM's full MaidMainGui (equipment, task, schedule,
                    // backpack tabs) exactly like right-clicking the maid.
                    backpackMaid.openMaidGui(player, TabIndex.MAIN);
                }
            }
            case RETURN_ACTIVE_TEAM -> {
                // 按下「小队撤离」：同样是先播对讲机反馈音
                playRadioClick(player);
                returnActiveTeam(player, data);
            }
        }
        save(player, data);
        sync(player);
    }

    /**
     * F9「部署 / 小队撤离」按下的对讲机反馈音：先一声咔哒，0.15 秒后再补一声「嘀」，
     * 模拟按下 PTT。只在玩家位置播放，不影响世界其它地方。
     */
    private static void playRadioClick(ServerPlayer player) {
        try {
            ServerLevel level = player.serverLevel();
            level.playSound(null, player.blockPosition(), ModSounds.RADIO_CLICK.get(),
                    SoundSource.PLAYERS, 0.9F, 1.0F);
            player.server.tell(new TickTask(3, () -> {
                if (player.isAlive() && !player.hasDisconnected()) {
                    level.playSound(null, player.blockPosition(), ModSounds.RADIO_BEEP.get(),
                            SoundSource.PLAYERS, 0.7F, 1.0F);
                }
            }));
        } catch (Throwable ignored) {
            // 音效失败不影响指令
        }
    }

    public static PlayerTeams get(ServerPlayer player) {
        PlayerTeams cached = CACHE.get(player.getUUID());
        if (cached != null) {
            return cached;
        }
        PlayerTeams loaded = load(player);
        CACHE.put(player.getUUID(), loaded);
        return loaded;
    }

    public static int getTeamIndex(ServerPlayer player, UUID maid) {
        PlayerTeams data = get(player);
        for (int i = 0; i < TEAM_COUNT; i++) {
            if (data.teams[i].members.contains(maid)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isActiveTeamMember(ServerPlayer player, EntityMaid maid) {
        PlayerTeams data = get(player);
        if (data.active < 0 || data.active >= data.teamCount) {
            return false;
        }
        return data.teams[data.active].members.contains(maid.getUUID());
    }

    public static java.util.List<UUID> activeMembers(ServerPlayer player) {
        PlayerTeams data = get(player);
        if (data.active < 0 || data.active >= data.teamCount) {
            return java.util.List.of();
        }
        return java.util.List.copyOf(data.teams[data.active].members);
    }

    /** Every maid in the five fixed teams, regardless of active selection. */
    public static java.util.List<UUID> allMembers(ServerPlayer player) {
        PlayerTeams data = get(player);
        java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>();
        for (int i = 0; i < TEAM_COUNT; i++) {
            ids.addAll(data.teams[i].members);
        }
        return java.util.List.copyOf(ids);
    }

    /** 队伍数量（固定 {@link #TEAM_COUNT}，保留给空投等外部调用方）。 */
    public static int teamCount(ServerPlayer player) {
        return get(player).teamCount;
    }

    /** 当前已部署（激活）的队伍下标；-1 = 没有。 */
    public static int activeTeam(ServerPlayer player) {
        return get(player).active;
    }

    /**
     * 清空“已部署”状态（硬救援后调用）：active = -1、airdropDeployed = false，并保存 + 同步 F9。
     *
     * <p>单独抽出来是为了让硬救援（{@link SquadRescueManager}）能在“人已经全部传回来”之后
     * 一次性把状态收干净，避免出现“人回来了但 F9 还以为小队在场、按钮全灰”的死结。</p>
     */
    public static void clearActiveState(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerTeams data = get(player);
        data.active = -1;
        data.airdropDeployed = false;
        SquadChunkTicketManager.release(player);
        save(player, data);
        sync(player);
    }

    /** 该玩家是否还有“已部署”状态的队伍（active 有效）。 */
    public static boolean hasActiveSquad(ServerPlayer player) {
        return player != null && get(player).active >= 0;
    }

    /** 指定队伍的成员 UUID 快照（顺序 = 队伍列表顺序，即 F9 显示顺序）。 */
    public static List<UUID> teamMemberIds(ServerPlayer player, int team) {
        PlayerTeams data = get(player);
        if (team < 0 || team >= data.teamCount) {
            return List.of();
        }
        return List.copyOf(data.teams[team].members);
    }

    /**
     * 空投开始后登记队伍：把<b>实际登机</b>的女仆写入指定队伍并激活该队。
     *
     * <p>与 {@link #summon} 不同：不传送、不复活女仆（她们已经在直升机座位上），
     * 只更新队伍数据 + 备份 + 同步 F9 界面。</p>
     *
     * <p>如果之前有别的队伍处于部署状态，先按老规矩撤回据点，避免两支队伍同时在场。</p>
     */
    public static void registerAirdropSquad(ServerPlayer player, int team, List<UUID> maids) {
        registerAirdropSquad(player, team, maids, false);
    }

    /**
     * @param keepOldSquad true = 交换部署（swap）：旧小队暂不回据点，等本次空投落地后登上同一架直升机飞走
     */
    public static void registerAirdropSquad(ServerPlayer player, int team, List<UUID> maids,
                                            boolean keepOldSquad) {
        if (player == null || maids == null || maids.isEmpty()) {
            return;
        }
        PlayerTeams data = get(player);
        int target = (team >= 0 && team < data.teamCount) ? team : data.active;
        if (target < 0 || target >= data.teamCount) {
            target = 0;
        }
        // 旧队伍撤回（跳过正在乘坐载具的女仆：可能正是本次空投人员）。
        // swap 时跳过这一段：旧小队要留在地面等登机，这里撤回会把她们直接传回据点。
        if (!keepOldSquad
                && data.active >= 0 && data.active < data.teamCount && data.active != target) {
            TeamSlot old = data.teams[data.active];
            boolean retreated = false;
            for (EntityMaid maid : resolveAll(player.server, old.members)) {
                if (maid.isPassenger()) {
                    continue;
                }
                Vec3 home = teleportToRally(player.server, maid, old);
                sendHome(maid, home);
                retreated = true;
            }
            if (retreated) {
                player.displayClientMessage(Component.translatable("message.tlmformation.team.retreated_n", data.active + 1)
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }

        TeamSlot slot = data.teams[target];
        slot.members.clear();
        for (UUID id : maids) {
            if (slot.members.size() >= TEAM_SIZE) {
                break;
            }
            if (id != null && !slot.members.contains(id)) {
                slot.members.add(id);
            }
        }
        data.active = target;
        data.airdropDeployed = true;   // 空投部署：等直升机来接人之前不允许再空投
        // 部署期小队区块常加载（配置 squadChunks.enabled 控制）
        SquadChunkTicketManager.activate(player);
        for (UUID id : slot.members) {
            EntityMaid maid = resolve(player.server, id);
            if (maid != null && maid.isAlive()) {
                MaidBackupManager.save(player, maid);
            }
        }
        save(player, data);
        sync(player);
    }

    @Nullable
    public static TeamRally getRally(ServerPlayer player, int team) {
        PlayerTeams data = get(player);
        if (team < 0 || team >= data.teamCount) {
            return null;
        }
        TeamSlot slot = data.teams[team];
        if (slot.rally == null || slot.rallyDimension == null) {
            return null;
        }
        return new TeamRally(slot.rally, slot.rallyDimension);
    }

    public static void summon(ServerPlayer player, PlayerTeams data, int team) {
        if (team < 0 || team >= data.teamCount) {
            return;
        }
        if (data.active == team) {
            player.displayClientMessage(Component.translatable("message.tlmformation.team.already_present")
                    .withStyle(ChatFormatting.YELLOW), false);
            return;
        }

        TeamSlot next = data.teams[team];
        if (next.members.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tlmformation.team.no_maid_to_summon_n", team + 1)
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        if (data.active >= 0) {
            TeamSlot old = data.teams[data.active];
            if (old.rally == null || old.rallyDimension == null) {
                player.displayClientMessage(Component.literal(
                                "队伍" + (data.active + 1) + "未设置据点，已送回出生点")
                        .withStyle(ChatFormatting.RED), false);
            }
            for (EntityMaid maid : resolveAll(player.server, old.members)) {
                Vec3 home = teleportToRally(player.server, maid, old);
                sendHome(maid, home);
            }
            player.displayClientMessage(Component.translatable("message.tlmformation.team.retreated_n", data.active + 1)
                    .withStyle(ChatFormatting.GOLD), false);
        }

        int index = 0;
        for (UUID id : next.members) {
            EntityMaid maid = resolveOrSpawn(player, id);
            if (maid == null) {
                continue;
            }
            double angle = Math.PI * 2.0D * index / Math.max(1, next.members.size());
            Vec3 candidate = player.position().add(
                    Math.cos(angle) * 2.2D, 0.0D, Math.sin(angle) * 2.2D);
            Vec3 safe = TargetSafety.findSafe(player.serverLevel(), maid, candidate);
            Vec3 dest = safe != null ? safe : player.position();
            forceAllSchedule(maid);
            com.tlmformation.vehicle.VehicleMaidRelease.release(maid);
            maid.teleportTo(dest.x, dest.y, dest.z);
            FormationManager.unpark(maid);
                maid.restrictTo(BlockPos.ZERO, MaidConfig.MAID_NON_HOME_RANGE.get());
            maid.setHomeModeEnable(false);
            TacticalManager.clear(maid);
            maid.setTarget(null);
            FormationManager.engage(maid);
            player.serverLevel().sendParticles(ParticleTypes.PORTAL,
                    dest.x, dest.y + 0.8D, dest.z, 12, 0.3D, 0.5D, 0.3D, 0.02D);
            index++;
        }

        if (index == 0) {
            player.displayClientMessage(Component.translatable("message.tlmformation.team.no_maid_to_summon_n", team + 1)
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        data.active = team;
        data.airdropDeployed = false;   // 传送召唤：不是空投小队，不触发“尚未撤离”拦截
        player.displayClientMessage(Component.translatable("message.tlmformation.team.replaced_n", team + 1)
                .withStyle(ChatFormatting.GREEN), false);
    }

    /**
     * 立即增援（呼救器等“必须马上到场”的场景）：跳过直升机空投，直接走旧的传送召唤。
     *
     * <p>F9「部署」按钮走的是 {@link #handle} → {@code SUMMON} → {@code AirdropManager.tryStart}，
     * 而呼救器需要瞬间把人拉到身边，不能被 30 秒的空投动画替代。</p>
     */
    public static void summonInstantly(ServerPlayer player, int team) {
        // 与 handle() 一致：集团体模式锁定期间不允许重新部署
        if (GrandAssembleManager.isActive(player)) {
            player.displayClientMessage(Component.literal(
                    "集团体模式已锁定，请使用战术撤退"), true);
            return;
        }
        PlayerTeams data = get(player);
        summon(player, data, team);
        save(player, data);
        sync(player);
    }

    private static EntityMaid findLoadedMaid(ServerPlayer player, UUID id) {
        for (ServerLevel level : player.server.getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity instanceof EntityMaid maid) return maid;
        }
        return null;
    }

    private static void forceAllSchedule(EntityMaid maid) {
        if (!maid.getPersistentData().contains("TLMOriginalSchedule")) {
            maid.getPersistentData().putString("TLMOriginalSchedule",
                    maid.getSchedule().name());
        }
        maid.setSchedule(MaidSchedule.ALL);
    }

    private static void restoreSchedule(EntityMaid maid) {
        // Off-duty: always send the squad home on DAY schedule.
        maid.setSchedule(MaidSchedule.DAY);
        maid.getPersistentData().remove("TLMOriginalSchedule");
    }

    private static void sendHome(EntityMaid maid, @Nullable Vec3 home) {
        TacticalManager.clear(maid);
        maid.setTarget(null);
        restoreSchedule(maid);
        if (home != null) {
            maid.getSchedulePos().setHomeModeEnable(maid, BlockPos.containing(home));
        }
        maid.setHomeModeEnable(true);
        if (maid.getOwner() instanceof ServerPlayer owner) {
            MaidBackupManager.save(owner, maid);
        }
    }


    /** 最高指令：强制解除一切状态（含坐骑），倒地的先“睡一觉复活”，再传送回基地。 */
    private static void forceRetreat(ServerPlayer player, EntityMaid maid, TeamSlot slot) {
        if (MaidDownedManager.isDowned(maid.getUUID())) {
            MaidDownedManager.forceSleepRevive(player, maid);
        }
        if (maid.isPassenger()) {
            maid.stopRiding();
        }
        sendHome(maid, teleportToRally(player.server, maid, slot));
    }

    /**
     * F9「小队撤离」（战术撤退）。
     *
     * <p>优先让直升机沿 +Z 进场、悬停抛绳、把女仆接走（索升），飞离 120 格后整队送回据点；
     * 直升机来不了时（地下 / 没装卓前 / 没有在线女仆）回退到原来的瞬间传送撤离。</p>
     */
    private static void returnActiveTeam(ServerPlayer player, PlayerTeams data) {
        if (data.active < 0 || data.active >= data.teamCount) {
            player.displayClientMessage(Component.translatable(
                    "message.tlmformation.team.no_active").withStyle(ChatFormatting.RED), false);
            return;
        }
        int team = data.active;
        boolean started = false;
        try {
            started = com.tlmformation.vehicle.AirdropManager.tryStartExtraction(player, team);
        } catch (Throwable throwable) {
            com.tlmformation.TLMFormationMod.LOGGER.error(
                    "[Airdrop] tryStartExtraction failed", throwable);
            started = com.tlmformation.vehicle.AirdropManager.isActive(player);
        }
        if (started) {
            player.displayClientMessage(Component.translatable(
                    "message.tlmformation.team.airlift_inbound")
                    .withStyle(ChatFormatting.AQUA), false);
            return;
        }
        // 兜底 1：硬救援（可跨维度 / 超远距离 / 未加载，走区块加载 + 备份坐标定位）
        if (FormationConfig.SQUAD_RESCUE_ENABLED.get()) {
            SquadRescueManager.Report report = SquadRescueManager.hardRescue(player, "f9");
            if (report.any()) {
                SquadReconcileManager.markRescued(player);
                return;
            }
        }
        // 兜底 2：保留下来的原始瞬间传送撤离（只对已加载的同维度女仆有效）
        returnTeamInstantly(player);
    }

    /**
     * 原始的「战术撤退」：整队瞬间传送回据点（含倒地的先睡一觉复活）。
     *
     * <p>仍然作为直升机接人失败时的兜底，也是 {@link #finishAirdropPickup} 的内部实现基础。</p>
     *
     * @return true = 已执行撤离；false = 没有激活队伍 / 没设据点（与原逻辑一致，不做任何传送）
     */
    public static boolean returnTeamInstantly(ServerPlayer player) {
        PlayerTeams data = get(player);
        if (data.active < 0 || data.active >= data.teamCount) {
            player.displayClientMessage(Component.translatable(
                    "message.tlmformation.team.no_active").withStyle(ChatFormatting.RED), false);
            return false;
        }
        TeamSlot slot = data.teams[data.active];
        if (slot.rally == null || slot.rallyDimension == null) {
            player.displayClientMessage(Component.translatable(
                    "message.tlmformation.team.no_rally").withStyle(ChatFormatting.RED), false);
            return false;
        }
        int returnedTeam = data.active;
        int teleported = 0;
        for (EntityMaid maid : resolveAll(player.server, slot.members)) {
            try {
                forceRetreat(player, maid, slot);
                teleported++;
            } catch (Throwable throwable) {
                com.tlmformation.TLMFormationMod.LOGGER.error(
                        "[Team] instant retreat failed for {}", maid.getUUID(), throwable);
            }
        }
        if (teleported == 0) {
            // 一个都没传到（女仆全在未加载区块 / 别的维度）：不要谎报成功清状态，
            // 否则 F9 会显示“没有激活队伍”，小队永久搁浅。交给硬救援或玩家重试。
            player.displayClientMessage(Component.literal(
                    "[队伍" + (returnedTeam + 1) + "] 没有可传送的女仆（可能未加载或在其它维度），"
                            + "正在尝试硬救援…").withStyle(ChatFormatting.RED), false);
            if (FormationConfig.SQUAD_RESCUE_ENABLED.get()) {
                SquadRescueManager.Report report = SquadRescueManager.hardRescue(player, "instant");
                if (report.any()) {
                    SquadReconcileManager.markRescued(player);
                    return true;
                }
            }
            return false;
        }
        // The squad is now home, not active. Clearing this is required so the
        // same team can be summoned again immediately.
        data.active = -1;
        data.airdropDeployed = false;
        SquadChunkTicketManager.release(player);
        player.displayClientMessage(Component.translatable(
                "message.tlmformation.team.retreat_done", returnedTeam + 1)
                .withStyle(ChatFormatting.GOLD), false);
        save(player, data);
        sync(player);
        return true;
    }

    /**
     * 直升机接走作业完成：整队（含刚登机的女仆）瞬间送回据点待命，并清空部署状态。
     *
     * <p>与 {@link #returnTeamInstantly} 的区别：不要求必须设置过据点
     * （{@code teleportToRally} 会退回世界出生点），因为空投小队可能是在野外被接走的。</p>
     */
    public static void finishAirdropPickup(ServerPlayer player, int team) {
        PlayerTeams data = get(player);
        int index = (team >= 0 && team < data.teamCount) ? team : data.active;
        if (index < 0 || index >= data.teamCount) {
            data.airdropDeployed = false;
            SquadChunkTicketManager.release(player);
            save(player, data);
            sync(player);
            return;
        }
        TeamSlot slot = data.teams[index];
        for (EntityMaid maid : resolveAll(player.server, slot.members)) {
            forceRetreat(player, maid, slot);
        }
        data.active = -1;
        data.airdropDeployed = false;
        SquadChunkTicketManager.release(player);
        // 注意：这里不再发“[队伍N] 小队已登机撤离”聊天。
        // 该消息改在直升机刚开始飞离时就发（AirdropManager.enterExtract），
        // 否则要等飞离 120 格（约 10 秒）才刷出来，常常正好撞上玩家按下一次「部署」，
        // 看起来像“召唤时触发了撤离”。
        save(player, data);
        sync(player);
    }

    /**
     * 交换空投收尾：把旧小队送回据点/出生点。
     *
     * <p>两种情况都会处理：<b>已经坐上直升机</b>的（先 stopRiding 再传送）和
     * <b>没赶上登机</b>的（先在区块里定位，再传送）。定位失败返回 0，调用方负责提示。</p>
     *
     * @param teamIndex 旧小队下标，仅用于取据点坐标；-1 时退回世界出生点
     * @return 实际送回的女仆数量
     */
    public static int returnSquadHome(ServerPlayer player, List<UUID> ids, int teamIndex) {
        if (player == null || ids == null || ids.isEmpty()) {
            return 0;
        }
        PlayerTeams data = get(player);
        TeamSlot slot = (teamIndex >= 0 && teamIndex < data.teamCount)
                ? data.teams[teamIndex] : new TeamSlot();
        int count = 0;
        for (UUID id : ids) {
            EntityMaid maid = MaidLocator.findOrLoad(player.server, player, id);
            if (maid == null) {
                continue;
            }
            try {
                // 释放个人区块票据（她马上要离开这块区域回家）
                MaidChunkTicketManager.release(player.server, id);
                forceRetreat(player, maid, slot);
                count++;
            } catch (Throwable throwable) {
                com.tlmformation.TLMFormationMod.LOGGER.error(
                        "[Squad] swap return failed for {}", id, throwable);
            }
        }
        return count;
    }

    /** 该玩家当前激活队伍是否由直升机空投部署（还没被接走）。 */
    public static boolean isAirdropDeployed(ServerPlayer player) {        if (player == null) {
            return false;
        }
        PlayerTeams data = get(player);
        return data.airdropDeployed && data.active >= 0 && data.active < data.teamCount;
    }

    /** 当前激活队伍里已经加载、还活着的女仆（空投接人用）。 */
    public static List<EntityMaid> loadedTeamMaids(ServerPlayer player, int team) {
        List<EntityMaid> result = new ArrayList<>();
        for (UUID id : teamMemberIds(player, team)) {
            Entity entity = player.serverLevel().getEntity(id);
            if (entity instanceof EntityMaid maid && maid.isAlive() && !maid.isRemoved()) {
                result.add(maid);
            }
        }
        return result;
    }

    private static Vec3 findPlayerSafe(ServerLevel level, ServerPlayer player, Vec3 center) {
        for (int radius = 0; radius <= 4; radius++) {
            int samples = Math.max(8, radius * 2);
            for (int i = 0; i < samples; i++) {
                double angle = Math.PI * 2.0D * i / samples;
                Vec3 candidate = center.add(Math.cos(angle) * radius,
                        0.0D, Math.sin(angle) * radius);
                BlockPos feet = BlockPos.containing(candidate);
                level.getChunk(feet.getX() >> 4, feet.getZ() >> 4);
                if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        || !level.getBlockState(feet.above()).getCollisionShape(
                        level, feet.above()).isEmpty()
                        || level.getBlockState(feet.below()).getCollisionShape(
                        level, feet.below()).isEmpty()) {
                    continue;
                }
                AABB moved = player.getBoundingBox()
                        .move(candidate.subtract(player.position()));
                if (level.noCollision(player, moved)) {
                    return new Vec3(candidate.x, feet.getY(), candidate.z);
                }
            }
        }
        return center;
    }

    private static Vec3 teleportToRally(MinecraftServer server, EntityMaid maid, TeamSlot slot) {
        ServerLevel level = null;
        Vec3 center = null;
        if (slot.rally != null && slot.rallyDimension != null) {
            level = server.getLevel(slot.rallyDimension);
            center = slot.rally;
        }
        if (level == null || center == null) {
            level = maid.level() instanceof ServerLevel serverLevel ? serverLevel : null;
            if (level == null) {
                return null;
            }
            BlockPos spawn = level.getSharedSpawnPos();
            center = Vec3.atCenterOf(spawn);
        }

        // Spread the group over a 3-5 block radius and validate each landing.
        Vec3 destination = null;
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = maid.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = 3.0D + maid.getRandom().nextDouble() * 2.0D;
            Vec3 candidate = center.add(Math.cos(angle) * radius, 0.0D,
                    Math.sin(angle) * radius);
            BlockPos pos = BlockPos.containing(candidate);
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            Vec3 safe = TargetSafety.findSafe(level, maid, candidate);
            if (safe != null) {
                destination = safe;
                break;
            }
        }
        if (destination == null) {
            destination = center;
        }

        com.tlmformation.vehicle.VehicleMaidRelease.release(maid);
        if (maid.level() == level) {
            maid.teleportTo(destination.x, destination.y, destination.z);
        } else {
            maid.teleportTo(level, destination.x, destination.y, destination.z,
                    java.util.Set.<RelativeMovement>of(), maid.getYRot(), maid.getXRot());
        }
        level.sendParticles(ParticleTypes.PORTAL,
                destination.x, destination.y + 0.8D, destination.z,
                16, 0.35D, 0.5D, 0.35D, 0.02D);
        return destination;
    }

    @Nullable
    private static EntityMaid resolve(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof EntityMaid maid) {
                return maid;
            }
        }
        return null;
    }

    @Nullable
    private static EntityMaid resolveOrSpawn(ServerPlayer player, UUID uuid) {
        EntityMaid existing = resolve(player.server, uuid);
        if (existing != null && existing.isAlive()) {
            return existing;
        }

        CompoundTag backup = MaidBackupManager.read(player, uuid);
        if (backup == null) {
            return null;
        }

        ServerLevel level = player.serverLevel();
        final Vec3 landing = player.position();
        net.minecraft.world.entity.Entity spawned =
                net.minecraft.world.entity.EntityType.loadEntityRecursive(
                        backup, level, entity -> {
                            entity.moveTo(landing.x, landing.y, landing.z,
                                    player.getYRot(), 0.0F);
                            entity.setUUID(uuid);
                            level.addFreshEntityWithPassengers(entity);
                            return entity;
                        });
        if (!(spawned instanceof EntityMaid maid)) {
            return null;
        }
        maid.setUUID(uuid);
        maid.setOwnerUUID(player.getUUID());
        maid.setNoAi(false);
        maid.setHomeModeEnable(false);
        maid.setHealth(maid.getMaxHealth());
        return maid;
    }

    private static List<EntityMaid> resolveAll(MinecraftServer server, List<UUID> ids) {
        List<EntityMaid> result = new ArrayList<>();
        for (UUID id : ids) {
            EntityMaid maid = resolve(server, id);
            if (maid != null && maid.isAlive()) {
                result.add(maid);
            }
        }
        return result;
    }

    public static void backupAll(ServerPlayer player) {
        PlayerTeams data = get(player);
        for (int i = 0; i < data.teamCount; i++) {
            for (UUID id : data.teams[i].members) {
                EntityMaid maid = resolve(player.server, id);
                if (maid != null && maid.isAlive()) {
                    MaidBackupManager.save(player, maid);
                }
            }
        }
    }

    public static void sync(ServerPlayer player) {
        PlayerTeams data = get(player);
        Map<UUID, S2CTeamData.Member> members = new java.util.LinkedHashMap<>();
        for (EntityMaid maid : player.serverLevel().getEntitiesOfClass(
                EntityMaid.class, player.getBoundingBox().inflate(128.0D),
                maid -> maid.isOwnedBy(player))) {
            MaidDeathCoordinates.Info death = MaidDeathCoordinates.read(maid);
            if (death == null) {
                death = MaidDeathCoordinates.read(player, maid.getUUID());
            }
            members.put(maid.getUUID(), member(player, maid.getUUID(), maid.getId(),
                    maid.getDisplayName().getString(), death));
        }

        // Dead or unloaded team members still need a name/death-coordinate row.
        // 名字来源顺序：死亡坐标记录里的名字 → 女仆备份里的显示名（区块未加载时也能显示真名）→ 空字符串
        // （客户端会把空名字渲染成“未加载”，而不是一个问号）。
        for (TeamSlot slot : data.teams) {
            for (UUID id : slot.members) {
                members.computeIfAbsent(id, uuid -> {
                    MaidDeathCoordinates.Info death = MaidDeathCoordinates.read(player, uuid);
                    String name = death != null ? death.name() : null;
                    if (name == null || name.isBlank()) {
                        name = MaidBackupManager.readName(player, uuid);
                    }
                    return member(player, uuid, -1, name == null ? "" : name, death);
                });
            }
        }

        List<S2CTeamData.TeamView> teams = new ArrayList<>();
        for (int i = 0; i < data.teamCount; i++) {
            TeamSlot slot = data.teams[i];
            S2CTeamData.Rally rally = null;
            if (slot.rally != null && slot.rallyDimension != null) {
                rally = new S2CTeamData.Rally(slot.rally.x, slot.rally.y, slot.rally.z,
                        slot.rallyDimension.location().toString());
            }
            teams.add(new S2CTeamData.TeamView(new ArrayList<>(slot.members), rally));
        }
        com.tlmformation.network.NetworkHandler.sendTeamData(
                player, new S2CTeamData(data.active, new ArrayList<>(members.values()), teams));
    }

    private static S2CTeamData.Member member(ServerPlayer player, UUID id, int entityId,
                                             String name,
                                             @Nullable MaidDeathCoordinates.Info death) {
        boolean waiting = MaidReviveManager.isWaiting(player, id);
        if (waiting) death = null;
        return new S2CTeamData.Member(id, entityId, name,
                death != null, waiting,
                MaidDownedManager.isDowned(id),
                MaidDownedManager.downedSeconds(id,
                        player.server.overworld().getGameTime()),
                death == null ? 0 : death.x(),
                death == null ? 0 : death.y(),
                death == null ? 0 : death.z(),
                death == null ? "" : death.dimension(),
                entityId < 0 ? MaidBackupManager.read(player, id) : null);
    }

    private static PlayerTeams load(ServerPlayer player) {
        PlayerTeams data = new PlayerTeams();
        CompoundTag root = player.getPersistentData().getCompound(TAG_ROOT);
        data.active = root.contains("Active") ? root.getInt("Active") : -1;
        data.airdropDeployed = root.getBoolean("AirdropDeployed");
        data.teamCount = root.contains("Count") ? root.getInt("Count") : -1;
        ListTag teams = root.getList("Teams", Tag.TAG_COMPOUND);
        if (data.teamCount < 1) {
            data.teamCount = TEAM_COUNT;
        }
        data.teamCount = TEAM_COUNT;
        if (data.active >= data.teamCount) {
            data.active = -1;
        }
        for (int i = 0; i < Math.min(teams.size(), TEAM_COUNT); i++) {
            CompoundTag tag = teams.getCompound(i);
            TeamSlot slot = data.teams[i];
            ListTag members = tag.getList("Members", Tag.TAG_STRING);
            for (int j = 0; j < members.size() && j < TEAM_SIZE; j++) {
                try {
                    slot.members.add(UUID.fromString(members.getString(j)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (tag.getBoolean("HasRally")) {
                slot.rally = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
                String dim = tag.getString("Dim");
                if (!dim.isEmpty()) {
                    slot.rallyDimension = ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            new ResourceLocation(dim));
                }
            }
        }
        return data;
    }

    private static void save(ServerPlayer player, PlayerTeams data) {
        CompoundTag root = new CompoundTag();
        root.putInt("Active", data.active);
        root.putInt("Count", data.teamCount);
        root.putBoolean("AirdropDeployed", data.airdropDeployed);
        ListTag teams = new ListTag();
        for (int i = 0; i < data.teamCount; i++) {
            TeamSlot slot = data.teams[i];
            CompoundTag tag = new CompoundTag();
            ListTag members = new ListTag();
            for (UUID member : slot.members) {
                members.add(StringTag.valueOf(member.toString()));
            }
            tag.put("Members", members);
            tag.putBoolean("HasRally", slot.rally != null && slot.rallyDimension != null);
            if (slot.rally != null && slot.rallyDimension != null) {
                tag.putDouble("X", slot.rally.x);
                tag.putDouble("Y", slot.rally.y);
                tag.putDouble("Z", slot.rally.z);
                tag.putString("Dim", slot.rallyDimension.location().toString());
            }
            teams.add(tag);
        }
        root.put("Teams", teams);
        player.getPersistentData().put(TAG_ROOT, root);
    }

    public record TeamRally(Vec3 position, ResourceKey<Level> dimension) {
    }

    public static final class TeamSlot {
        private final List<UUID> members = new ArrayList<>();
        @Nullable
        private Vec3 rally;
        @Nullable
        private ResourceKey<Level> rallyDimension;
    }

    public static final class PlayerTeams {
        private final TeamSlot[] teams = new TeamSlot[TEAM_COUNT];
        private int teamCount = TEAM_COUNT;
        private int active = -1;
        /** true = 当前激活队伍是直升机空投部署、还没被接走（用于拦截重复空投）。 */
        private boolean airdropDeployed;

        private PlayerTeams() {
            for (int i = 0; i < TEAM_COUNT; i++) {
                teams[i] = new TeamSlot();
            }
        }

        private void removeFromAll(UUID maid) {
            for (TeamSlot team : teams) {
                team.members.remove(maid);
            }
        }
    }
}
