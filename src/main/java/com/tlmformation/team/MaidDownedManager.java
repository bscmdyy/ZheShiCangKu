package com.tlmformation.team;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.tlmformation.TLMFormationMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidDownedManager {
    private static final Map<UUID, Downed> DOWNED = new HashMap<>();
    private static final Map<UUID, Rescue> RESCUES = new HashMap<>();
    private static final Map<UUID, AABB> ORIGINAL_BOXES = new HashMap<>();
    private static final long DOWNED_TICKS = 1200L;

    private static final String[] TAUNTS = {
            "喂，指挥官，你就打算这么看着？想抛弃小队吗？",
            "呵...这就准备把我丢下了？真让人寒心啊。",
            "别看了，要么赶紧拉我一把，要么直接给我个痛快！",
            "主人，你该不会以为我倒下了就没用了吧？快扶我起来！",
            "想抛弃我？你最好跑得比我快，不然等我起来有你好看的。",
            "别发呆啊，敌人的刀都要砍到你脸上了，还不快把我拽起来！",
            "就这点出息？连自己的队员都护不住，还想当指挥官？",
            "哎...看来我在主人心里的分量，也就这么一点啊...",
            "别假惺惺地看我了，要么救人，要么滚蛋！",
            "我还能打！只要你别像个木头一样杵在那，拉我一把！"
    };



    private static final String[] THANKS = {
            "\u8c22\u8c22\u4f60...\u80fd\u62c9\u4f4f\u6211\u7684\u624b\uff0c\u771f\u597d\u3002",
            "\u547c...\u8fd8\u4ee5\u4e3a\u8981\u6491\u4e0d\u4f4f\u4e86\uff0c\u8c22\u8c22\u4f60\u6765\u6551\u6211\u3002",
            "\u8ba9\u4f60\u62c5\u5fc3\u4e86\uff0c\u4e0b\u6b21\u6211\u4f1a\u5c0f\u5fc3\u7684\u3002",
            "\u4f24\u53e3\u4e0d\u75bc\u4e86\uff0c\u8c22\u8c22\u4f60\u6ca1\u6709\u653e\u5f03\u6211\u3002",
            "\u88ab\u62c9\u8d77\u6765\u7684\u611f\u89c9...\u771f\u7684\u5f88\u8ba9\u4eba\u5b89\u5fc3\u5462\u3002",
            "\u8c22\u8c22\u5927\u5bb6...\u6211\u8fd8\u80fd\u7ee7\u7eed\u548c\u5927\u5bb6\u5e76\u80a9\u4f5c\u6218\u3002",
            "\u591a\u4e8f\u4e86\u4f60\uff0c\u6211\u624d\u80fd\u91cd\u65b0\u7ad9\u8d77\u6765\u3002",
            "\u80fd\u7ee7\u7eed\u7559\u5728\u5c0f\u961f\u91cc\uff0c\u6bd4\u4ec0\u4e48\u90fd\u91cd\u8981\uff0c\u8c22\u8c22\u3002",
            "\u521a\u624d\u771f\u7684\u5f88\u5bb3\u6015\uff0c\u5e78\u597d\u6709\u4f60\u5728\u3002",
            "\u6211\u6ca1\u4e8b\u4e86\uff0c\u63a5\u4e0b\u6765\u6362\u6211\u6765\u63a9\u62a4\u4f60\uff01",
    };

    private MaidDownedManager() {
    }

    private record Downed(UUID owner, long started, IMaidTask task) {
    }

    private record Rescue(UUID maid, long finish, UUID rescuer) {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLethalHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || event.isCanceled()) return;
        if (maid.level().isClientSide
                || !(maid.getOwner() instanceof ServerPlayer owner)) return;

        if (DOWNED.containsKey(maid.getUUID())) {
            event.setCanceled(true);
            maid.setHealth(1.0F);
            lockPose(maid);
            return;
        }
        if (maid.getHealth() - event.getAmount() > 0.0F) return;

        // The logistics rescue pact wins over downing.
        if (com.tlmformation.logistics.LogisticsManager.tryRescue(maid)) {
            event.setCanceled(true);
            return;
        }

        event.setCanceled(true);
        down(owner, maid);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidDeath(MaidDeathEvent event) {
        if (event.isCanceled()) return;
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide
                || !(maid.getOwner() instanceof ServerPlayer owner)) return;
        event.setCanceled(true);
        down(owner, maid);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || event.isCanceled()) return;
        if (maid.level().isClientSide
                || !(maid.getOwner() instanceof ServerPlayer owner)) return;
        event.setCanceled(true);
        down(owner, maid);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)
                || maid.level().isClientSide
                || !DOWNED.containsKey(maid.getUUID())) return;
        lockPose(maid);
    }

    private static void lockPose(EntityMaid maid) {
        maid.setPose(Pose.SLEEPING);
        maid.setSleepingPos(maid.blockPosition());
        maid.setNoAi(true);
        maid.setTarget(null);
        maid.getNavigation().stop();
        maid.setDeltaMovement(0, 0, 0);
        AABB box = ORIGINAL_BOXES.getOrDefault(
                maid.getUUID(), maid.getBoundingBox());
        maid.setBoundingBox(new AABB(
                box.minX + 0.2D, box.minY, box.minZ + 0.2D,
                box.maxX - 0.2D, box.maxY, box.maxZ - 0.2D));
        if (maid.getHealth() > 1.0F) maid.setHealth(1.0F);
    }

    private static void down(ServerPlayer owner, EntityMaid maid) {
        if (DOWNED.containsKey(maid.getUUID())) return;
        IMaidTask oldTask = maid.getTask();
        maid.setHealth(1.0F);
        lockPose(maid);
        DOWNED.put(maid.getUUID(), new Downed(owner.getUUID(),
                maid.level().getGameTime(), oldTask));
        MaidTeamManager.sync(owner);
        // 保险：倒地时在聊天栏播报坐标（复活/存活后状态自然消失）
        net.minecraft.core.BlockPos downPos = maid.blockPosition();
        owner.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§c[救援] " + maid.getName().getString() + " 倒地！位置 X:"
                        + downPos.getX() + " Y:" + downPos.getY() + " Z:" + downPos.getZ()
                        + "（" + maid.level().dimension().location() + "）"));
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof EntityMaid maid)) return;
        Downed d = DOWNED.get(maid.getUUID());
        if (d == null || !d.owner().equals(player.getUUID())) return;
        RESCUES.put(player.getUUID(), new Rescue(maid.getUUID(),
                maid.level().getGameTime() + 140L, player.getUUID()));
        player.displayClientMessage(Component.translatable("message.tlmformation.downed.rescue"), true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().overworld().getGameTime();

        for (UUID id : new ArrayList<>(DOWNED.keySet())) {
            Downed d = DOWNED.get(id);
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(d.owner());
            if (owner == null) continue;
            EntityMaid downed = find(owner, id);
            if (downed == null) continue;

            if (!RESCUES.containsKey(owner.getUUID())) {
                EntityMaid rescuer = null;
                for (EntityMaid m : downed.level().getEntitiesOfClass(
                        EntityMaid.class, downed.getBoundingBox().inflate(50.0D))) {
                    if (m != downed && m.isAlive()
                            && m.getTask() instanceof com.tlmformation.tactical.TacticalLogisticsTask) {
                        rescuer = m;
                        break;
                    }
                }
                if (rescuer == null && owner.distanceToSqr(downed) <= 625.0D) {
                    for (EntityMaid m : downed.level().getEntitiesOfClass(
                            EntityMaid.class, downed.getBoundingBox().inflate(50.0D))) {
                        if (m != downed && m.isAlive()) {
                            rescuer = m;
                            break;
                        }
                    }
                }
                if (rescuer != null) {
                    RESCUES.put(owner.getUUID(), new Rescue(id, 0L, rescuer.getUUID()));
                } else if (owner.distanceToSqr(downed) <= 25.0D) {
                    RESCUES.put(owner.getUUID(), new Rescue(id, now + 140L,
                            owner.getUUID()));
                }
            }

            Rescue r = RESCUES.get(owner.getUUID());
            if (r == null || !r.maid().equals(id)) continue;
            Entity rescuer = r.rescuer().equals(owner.getUUID())
                    ? owner : find(owner, r.rescuer());
            if (rescuer == null || rescuer.distanceToSqr(downed) > 625.0D
                    || rescuer instanceof LivingEntity living && living.hurtTime > 0) {
                RESCUES.remove(owner.getUUID());
                owner.displayClientMessage(Component.literal(
                        "[救援] 救援中断！").withStyle(ChatFormatting.RED), true);
                continue;
            }

            if (r.finish() == 0L) {
                if (rescuer instanceof EntityMaid rescuerMaid) {
                    rescuerMaid.getNavigation().moveTo(downed, 1.0D);
                    rescuerMaid.setTarget(null);
                }
                if (rescuer.distanceToSqr(downed) <= 25.0D) {
                    RESCUES.put(owner.getUUID(), new Rescue(id, now + 140L,
                            r.rescuer()));
                }
                continue;
            }

            if (rescuer instanceof EntityMaid rescuerMaid) {
                rescuerMaid.getNavigation().moveTo(downed, 0.35D);
                rescuerMaid.setTarget(null);
            }

            if (now % 10L == 0L) {
                owner.displayClientMessage(Component.literal(
                        "[危急！正在实行紧急救援，坚持 7 秒！]")
                        .withStyle(ChatFormatting.RED), true);
            }
            if (now >= r.finish()) {
                revive(owner, downed);
                RESCUES.remove(owner.getUUID());
            }
        }

        // Timeout and pose locking.
        for (UUID id : new ArrayList<>(DOWNED.keySet())) {
            Downed d = DOWNED.get(id);
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(d.owner());
            EntityMaid maid = owner == null ? null : find(owner, id);
            if (now - d.started() >= DOWNED_TICKS) {
                if (maid != null && owner != null) {
                    MaidReviveManager.cacheDowned(owner, maid);
                    maid.discard();
                }
                DOWNED.remove(id);
                RESCUES.remove(d.owner());
                if (owner != null) MaidTeamManager.sync(owner);
            } else if (maid != null) {
                lockPose(maid);
                transferAggro(owner, maid);
            }
        }
    }

    private static void transferAggro(ServerPlayer owner, EntityMaid downed) {
        if (owner == null) return;
        LivingEntity fallback = owner;
        for (EntityMaid candidate : owner.serverLevel().getEntitiesOfClass(
                EntityMaid.class, downed.getBoundingBox().inflate(16))) {
            if (candidate == downed || !candidate.isAlive()
                    || DOWNED.containsKey(candidate.getUUID())) continue;
            if (owner.getUUID().equals(candidate.getOwnerUUID())) {
                fallback = candidate;
                break;
            }
        }
        for (net.minecraft.world.entity.monster.Monster monster
                : downed.level().getEntitiesOfClass(
                net.minecraft.world.entity.monster.Monster.class,
                downed.getBoundingBox().inflate(16))) {
            if (monster.getTarget() == downed) {
                monster.setTarget(fallback);
            }
        }
    }

    private static void revive(ServerPlayer player, EntityMaid maid) {
        Downed d = DOWNED.remove(maid.getUUID());
        maid.setPose(Pose.STANDING);
        maid.setNoAi(false);
        maid.setHealth(maid.getMaxHealth() * 0.3F);
        maid.invulnerableTime = 60;
        maid.setTarget(null);
        maid.getNavigation().stop();
        maid.setInvulnerable(false);
        AABB original = ORIGINAL_BOXES.remove(maid.getUUID());
        if (original != null) maid.setBoundingBox(original);
        if (d != null && d.task() != null) {
            maid.setTask(d.task());
        }

        player.displayClientMessage(Component.literal(
                "[救援] 成功扶起 " + maid.getName().getString() + "！")
                .withStyle(ChatFormatting.GREEN), true);
        player.sendSystemMessage(Component.literal("<"
                + maid.getName().getString() + "> "
                + THANKS[player.getRandom().nextInt(THANKS.length)])
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        MaidTeamManager.sync(player);
    }

    public static boolean hasDowned(ServerPlayer player) {
        for (Downed d : DOWNED.values()) {
            if (d.owner().equals(player.getUUID())) return true;
        }
        return false;
    }

    public static void warnRetreat(ServerPlayer player) {
        player.displayClientMessage(Component.literal(
                "[警告] 队伍中有重伤员，无法撤退！")
                .withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean recovered = false;
        for (UUID id : new ArrayList<>(DOWNED.keySet())) {
            Downed d = DOWNED.get(id);
            if (!d.owner().equals(player.getUUID())) continue;
            EntityMaid maid = find(player, id);
            if (maid != null) {
                // 玩家死亡：倒地女仆进入「待睡眠复活」流程（不要直接判死/丢实体）
                com.tlmformation.team.MaidReviveManager.cacheDowned(player, maid);
                maid.discard();
                recovered = true;
            }
            DOWNED.remove(id);
        }
        RESCUES.remove(player.getUUID());
        if (recovered) {
            player.sendSystemMessage(Component.literal(
                    "[战败] 指挥官已阵亡，全体重伤员已强制回收。")
                    .withStyle(ChatFormatting.GRAY));
            MaidTeamManager.sync(player);
        }
    }


    /** 撤退时对倒地女仆执行「睡一觉复活」：满血、清负面、解除倒地，返回原任务。 */
    public static void forceSleepRevive(ServerPlayer owner, EntityMaid maid) {
        Downed d = DOWNED.remove(maid.getUUID());
        maid.setPose(net.minecraft.world.entity.Pose.STANDING);
        maid.setNoAi(false);
        maid.setHealth(maid.getMaxHealth());
        maid.clearFire();
        for (net.minecraft.world.effect.MobEffectInstance effect
                : new ArrayList<>(maid.getActiveEffects())) {
            maid.removeEffect(effect.getEffect());
        }
        AABB original = ORIGINAL_BOXES.remove(maid.getUUID());
        if (original != null) {
            maid.setBoundingBox(original);
        }
        if (d != null && d.task() != null) {
            maid.setTask(d.task());
        }
        MaidTeamManager.sync(owner);
    }

    public static boolean isDowned(UUID maid) {
        return DOWNED.containsKey(maid);
    }

    public static int downedSeconds(UUID maid, long now) {
        Downed d = DOWNED.get(maid);
        if (d == null) return 0;
        long left = 1200L - (now - d.started());
        return (int) Math.max(0, (left + 19) / 20);
    }

    private static EntityMaid find(ServerPlayer player, UUID id) {
        return player.serverLevel().getEntitiesOfClass(EntityMaid.class,
                        player.getBoundingBox().inflate(128))
                .stream().filter(m -> m.getUUID().equals(id)).findFirst().orElse(null);
    }
}
