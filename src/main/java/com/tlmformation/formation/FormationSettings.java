package com.tlmformation.formation;

import com.tlmformation.config.FormationConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player, server-authoritative settings. Kept separately from ForgeConfigSpec
 * so the menu can change one player's formation without changing the whole server.
 */
public final class FormationSettings extends SavedData {
    private static final String DATA_NAME = "tlmformation_settings";
    private static final String PLAYERS_TAG = "Players";
    private static final String FORMATION_TAG = "FormationEnabled";
    private static final String EXPAND_TAG = "ExpandRange";
    private static final String FOLLOW_RANGE_TAG = "FollowRange";

    private final Map<UUID, PlayerSettings> settings = new HashMap<>();

    public static FormationSettings get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new FormationSettings();
        }
        return overworld.getDataStorage().computeIfAbsent(
                FormationSettings::load, FormationSettings::new, DATA_NAME);
    }

    public static FormationSettings get(Level level) {
        if (level.getServer() == null) {
            return new FormationSettings();
        }
        return get(level.getServer());
    }

    public static FormationSettings load(CompoundTag tag) {
        FormationSettings data = new FormationSettings();
        ListTag list = tag.getList(PLAYERS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Id")) {
                continue;
            }
            UUID id = entry.getUUID("Id");
            int followRange = entry.contains(FOLLOW_RANGE_TAG)
                    ? entry.getInt(FOLLOW_RANGE_TAG)
                    : FormationConfig.FOLLOW_RANGE.get();
            data.settings.put(id, new PlayerSettings(
                    entry.getBoolean(FORMATION_TAG),
                    entry.getInt(EXPAND_TAG),
                    followRange
            ));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        settings.forEach((id, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putBoolean(FORMATION_TAG, value.formationEnabled);
            entry.putInt(EXPAND_TAG, value.expandRange);
            entry.putInt(FOLLOW_RANGE_TAG, value.followRange);
            list.add(entry);
        });
        tag.put(PLAYERS_TAG, list);
        return tag;
    }

    public boolean isFormationEnabled(ServerPlayer player) {
        return get(player.getUUID()).formationEnabled;
    }

    public void setFormationEnabled(ServerPlayer player, boolean enabled) {
        PlayerSettings value = get(player.getUUID());
        value.formationEnabled = enabled;
        settings.put(player.getUUID(), value);
        setDirty();
    }

    public int getExpandRange(ServerPlayer player) {
        return get(player.getUUID()).expandRange;
    }

    public void setExpandRange(ServerPlayer player, int range) {
        int min = 16;
        int max = 192;
        PlayerSettings value = get(player.getUUID());
        value.expandRange = Math.max(min, Math.min(max, range));
        settings.put(player.getUUID(), value);
        setDirty();
    }

    public int getFollowRange(ServerPlayer player) {
        return get(player.getUUID()).followRange;
    }

    public void setFollowRange(ServerPlayer player, int range) {
        int min = 8;
        int max = 128;
        PlayerSettings value = get(player.getUUID());
        value.followRange = Math.max(min, Math.min(max, range));
        settings.put(player.getUUID(), value);
        setDirty();
    }

    public PlayerSettings get(UUID id) {
        return settings.computeIfAbsent(id, ignored ->
                new PlayerSettings(true, FormationConfig.EXPAND_RANGE.get(),
                        FormationConfig.FOLLOW_RANGE.get()));
    }

    public static final class PlayerSettings {
        private boolean formationEnabled;
        private int expandRange;
        private int followRange;

        PlayerSettings(boolean formationEnabled, int expandRange, int followRange) {
            this.formationEnabled = formationEnabled;
            this.expandRange = expandRange;
            this.followRange = followRange;
        }

        public boolean formationEnabled() {
            return formationEnabled;
        }

        public int expandRange() {
            return expandRange;
        }

        public int followRange() {
            return followRange;
        }

        public void setFormationEnabled(boolean formationEnabled) {
            this.formationEnabled = formationEnabled;
        }

        public void setExpandRange(int expandRange) {
            this.expandRange = expandRange;
        }

        public void setFollowRange(int followRange) {
            this.followRange = followRange;
        }
    }
}
