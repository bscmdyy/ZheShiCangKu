package com.tlmformation.tactical;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import com.tlmformation.logistics.HealingBarrierManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TacticalLogisticsTask implements IMaidTask {
    public static final ResourceLocation UID =
            new ResourceLocation("touhou_little_maid", "tactical_logistics");

    private static final Map<UUID, Long> FLEE_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> FEED_READY = new HashMap<>();

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE);
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return List.of();
    }

    public static void tick(EntityMaid maid, ServerPlayer owner) {
        if (maid.level().isClientSide || owner == null) return;

        System.out.println("[Debug] 后勤任务运行中，主手物品: "
                + maid.getMainHandItem());

        // Logistics never attacks.
        if (maid.getTarget() != null) {
            maid.setTarget(null);
        }
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);

        long now = maid.level().getGameTime();
        LivingEntity attacker = maid.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && maid.hurtTime > 0) {
            FLEE_UNTIL.put(maid.getUUID(), now + 100L);
        }

        Long fleeUntil = FLEE_UNTIL.get(maid.getUUID());
        if (attacker != null && attacker.isAlive()
                && fleeUntil != null && now < fleeUntil) {
            Vec3 away = maid.position().subtract(attacker.position());
            if (away.lengthSqr() < 0.01D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            Vec3 flee = maid.position().add(away.normalize().scale(12.0D));
            // 1.0 is the vanilla path-speed multiplier; no attribute or buff change.
            maid.getNavigation().moveTo(
                    flee.x, flee.y, flee.z, 1.0D);
        }

        HealingBarrierManager.tryCreate(owner, maid, now);

        long feedReady = FEED_READY.getOrDefault(maid.getUUID(), 0L);
        if (now >= feedReady && owner.getFoodData().getFoodLevel() < 20) {
            for (int i = 0; i < maid.getMaidInv().getSlots(); i++) {
                ItemStack stack = maid.getMaidInv().getStackInSlot(i);
                FoodProperties food = stack.getItem().getFoodProperties();
                if (food == null || stack.isEmpty()) continue;

                owner.getFoodData().setFoodLevel(Math.min(20,
                        owner.getFoodData().getFoodLevel() + food.getNutrition()));
                owner.getFoodData().setSaturation(Math.min(20.0F,
                        owner.getFoodData().getSaturationLevel()
                                + food.getSaturationModifier()));
                stack.shrink(1);
                FEED_READY.put(maid.getUUID(), now + 1200L);
                owner.displayClientMessage(Component.literal(
                        "[后勤支援] 已为你补充食物。"), true);
                break;
            }
        }
    }

    @Override
    public String getMaidActionSummary() {
        return "战术后勤：不主动攻击，受击逃跑，展开救援结界";
    }
}
