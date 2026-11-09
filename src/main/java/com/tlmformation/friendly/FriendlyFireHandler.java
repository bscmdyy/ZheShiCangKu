package com.tlmformation.friendly;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAttackEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidHurtTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.config.FormationConfig;
import com.tlmformation.config.FormationConfig.OwnerDamageMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Friendly-fire protection. No Mixin is required:
 * - MaidHurtTarget.Pre catches TLM melee at the earliest point.
 * - LivingAttackEvent is the generic fallback for sweep, guns, spells and modded damage.
 * - ProjectileImpactEvent catches arrows, crossbows, tridents and modded projectiles before impact.
 */
@SuppressWarnings("removal")
public final class FriendlyFireHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMaidHurtTarget(MaidHurtTarget.Pre event) {
        if (shouldProtect(event.getMaid(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        EntityMaid attacker = DamageAttribution.getAttackingMaid(event.getSource());
        if (attacker != null && shouldProtect(attacker, event.getEntity())) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof EntityMaid victim
                && event.getSource().getEntity() instanceof Player owner
                && victim.isOwnedBy(owner)
                && FormationConfig.OWNER_DAMAGE_MODE.get() == OwnerDamageMode.CANCEL) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        Entity victim = hit.getEntity();
        if (victim == null) {
            return;
        }

        Entity owner = event.getProjectile().getOwner();
        if (owner instanceof EntityMaid attacker && shouldProtect(attacker, victim)) {
            event.setCanceled(true);
            event.getProjectile().discard();
            return;
        }

        if (owner instanceof Player player
                && victim instanceof EntityMaid maid
                && maid.isOwnedBy(player)
                && FormationConfig.OWNER_DAMAGE_MODE.get() == OwnerDamageMode.CANCEL) {
            event.setCanceled(true);
            event.getProjectile().discard();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof EntityMaid maid
                && event.getSource().getEntity() instanceof Player owner
                && maid.isOwnedBy(owner)
                && FormationConfig.OWNER_DAMAGE_MODE.get() == OwnerDamageMode.REDUCE) {
            event.setAmount(Math.min(event.getAmount(), 1.0F));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMaidAttack(MaidAttackEvent event) {
        if (event.getSource().getEntity() instanceof Player owner
                && event.getMaid().isOwnedBy(owner)
                && FormationConfig.OWNER_DAMAGE_MODE.get() == OwnerDamageMode.CANCEL) {
            event.setCanceled(true);
        }
    }

    private static boolean shouldProtect(EntityMaid attacker, @Nullable Entity victim) {
        if (victim == null || victim == attacker) {
            return false;
        }
        UUID ownerId = attacker.getOwnerUUID();
        if (ownerId == null) {
            return false;
        }

        if (victim instanceof Player player && ownerId.equals(player.getUUID())) {
            return FormationConfig.PROTECT_OWNER.get();
        }
        if (victim instanceof EntityMaid other && ownerId.equals(other.getOwnerUUID())) {
            return FormationConfig.PROTECT_OTHER_MAIDS.get();
        }
        return false;
    }
}
