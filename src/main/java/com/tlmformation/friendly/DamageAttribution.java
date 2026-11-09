package com.tlmformation.friendly;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;

import javax.annotation.Nullable;

public final class DamageAttribution {
    private DamageAttribution() {
    }

    @Nullable
    public static EntityMaid getAttackingMaid(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();

        EntityMaid maid = asMaid(direct);
        if (maid == null) {
            maid = asMaid(causing);
        }
        if (maid == null && direct instanceof OwnableEntity ownable
                && ownable.getOwner() instanceof EntityMaid ownerMaid) {
            maid = ownerMaid;
        }
        return maid;
    }

    @Nullable
    private static EntityMaid asMaid(@Nullable Entity entity) {
        if (entity instanceof EntityMaid maid) {
            return maid;
        }
        if (entity instanceof Projectile projectile
                && projectile.getOwner() instanceof EntityMaid maid) {
            return maid;
        }
        return null;
    }
}
