package com.tlmformation.vehicle;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 实体注册：只保留原步战车 havoc_vehicle。
 */
public final class HavocEntities {
    public static final String MODID = "tlmformation";
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<EntityType<EntityHavoc>> HAVOC =
            ENTITY_TYPES.register("havoc_vehicle", () -> EntityType.Builder
                    .<EntityHavoc>of(EntityHavoc::new, MobCategory.MISC)
                    .sized((float) (2.2D * EntityHavoc.VISUAL_SCALE * EntityHavoc.COLLISION_SCALE
                                    * EntityHavoc.HITBOX_SCALE),
                            (float) (2.0D * EntityHavoc.VISUAL_SCALE * EntityHavoc.COLLISION_SCALE
                                    * EntityHavoc.HITBOX_SCALE))
                    .clientTrackingRange(12)
                    .build(MODID + ":havoc_vehicle"));

    /** 第一期简化版黑鹰直升机。 */
    public static final RegistryObject<EntityType<EntityHelicopter>> HELICOPTER =
            ModList.get().isLoaded("superbwarfare")
                    ? ENTITY_TYPES.register("helicopter", () -> EntityType.Builder
                    .<EntityHelicopter>of(EntityHelicopter::new, MobCategory.MISC)
                    .sized(2.4F, 3.2F)
                    .clientTrackingRange(16)
                    .build(MODID + ":helicopter"))
                    : null;


    private HavocEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(HavocEntities::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(HAVOC.get(), EntityHavoc.createAttributes().build());
    }
}
