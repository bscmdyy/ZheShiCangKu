package com.tlmformation.tactical;

import com.tlmformation.TLMFormationMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;

/**
 * Six tactical commands, arranged around the radial wheel. Angles are degrees
 * in Minecraft GUI space: -90 = up, 90 = down, 210 = upper-left.
 */
public enum TacticalCommand {
    MARK("mark", "tactical.tlmformation.mark", "tactical.tlmformation.mark.desc",
            "textures/gui/lightning_icon.png", null, -90.0F),
    LOCK("lock", "tactical.tlmformation.lock", "tactical.tlmformation.lock.desc",
            "textures/gui/tactical/gather.png", null, -30.0F),
    GUARD("guard", "tactical.tlmformation.guard", "tactical.tlmformation.guard.desc",
            "textures/gui/tactical/alert.png", null, 30.0F),
    FOLLOW("follow", "tactical.tlmformation.follow", "tactical.tlmformation.follow.desc",
            "textures/gui/tactical/follow.png", null, 90.0F),
    HOLD("hold", "tactical.tlmformation.hold", "tactical.tlmformation.hold.desc",
            "textures/gui/tactical/hold.png", null, 150.0F),
    RETREAT("retreat", "tactical.tlmformation.retreat", "tactical.tlmformation.retreat.desc",
            "textures/gui/shield_icon.png", null, 210.0F),
    MARCH("march", "tactical.tlmformation.march", "tactical.tlmformation.march.desc",
            "textures/gui/tactical/follow.png", null, -90.0F);

    private final String id;
    private final String translationKey;
    private final String descriptionKey;
    private final ResourceLocation texture;
    private final Item fallbackItem;
    private final float angle;

    TacticalCommand(String id, String translationKey, String descriptionKey,
                    @Nullable String texture, @Nullable Item fallbackItem, float angle) {
        this.id = id;
        this.translationKey = translationKey;
        this.descriptionKey = descriptionKey;
        this.texture = texture == null ? null : new ResourceLocation(TLMFormationMod.MOD_ID, texture);
        this.fallbackItem = fallbackItem;
        this.angle = angle;
    }

    public String getId() {
        return id;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public Component description() {
        return Component.translatable(descriptionKey);
    }

    @Nullable
    public ResourceLocation texture() {
        return texture;
    }

    @Nullable
    public Item fallbackItem() {
        return fallbackItem;
    }

    public float angleDegrees() {
        return angle;
    }

    public static TacticalCommand byOrdinal(int ordinal) {
        TacticalCommand[] values = values();
        return ordinal < 0 || ordinal >= values.length ? FOLLOW : values[ordinal];
    }
}
