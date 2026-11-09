package com.tlmformation.client.helicopter;

import com.tlmformation.vehicle.EntityHelicopter;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class HelicopterGeoModel extends GeoModel<EntityHelicopter> {
    public static final ResourceLocation MODEL =
            new ResourceLocation("tlmformation", "geo/helicopter.geo.json");
    public static final ResourceLocation TEXTURE =
            new ResourceLocation("tlmformation", "textures/entity/helicopter_original.png");
    public static final ResourceLocation ANIM =
            new ResourceLocation("tlmformation", "animations/helicopter_rotor.animation.json");

    @Override
    public ResourceLocation getModelResource(EntityHelicopter animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(EntityHelicopter animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(EntityHelicopter animatable) {
        return ANIM;
    }
}
