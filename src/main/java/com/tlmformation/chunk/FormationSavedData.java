package com.tlmformation.chunk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registration data for chunk-loaded maids. Forge persists the actual chunk
 * tickets; this SavedData is the source of truth for "which maids are registered".
 */
public final class FormationSavedData extends SavedData {
    private static final String DATA_NAME = "tlmformation_chunks";
    private static final String RECORDS_TAG = "Records";
    private static final String HOLDS_TAG = "Holds";
    private static final String RALLIES_TAG = "Rallies";

    private final Map<UUID, MaidRecord> records = new HashMap<>();
    private final Map<UUID, HoldRecord> holds = new HashMap<>();
    private final Map<UUID, RallyRecord> rallies = new HashMap<>();

    public static FormationSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new FormationSavedData();
        }
        return overworld.getDataStorage().computeIfAbsent(
                FormationSavedData::load, FormationSavedData::new, DATA_NAME);
    }

    public static FormationSavedData get(Level level) {
        if (level.getServer() == null) {
            return new FormationSavedData();
        }
        return get(level.getServer());
    }

    public static FormationSavedData load(CompoundTag tag) {
        FormationSavedData data = new FormationSavedData();
        ListTag list = tag.getList(RECORDS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MaidRecord record = MaidRecord.fromTag(list.getCompound(i));
            if (record != null) {
                data.records.put(record.maidId(), record);
            }
        }
        ListTag rallies = tag.getList(RALLIES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < rallies.size(); i++) {
            RallyRecord record = RallyRecord.fromTag(rallies.getCompound(i));
            if (record != null) {
                data.rallies.put(record.ticketOwner(), record);
            }
        }
        ListTag holds = tag.getList(HOLDS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < holds.size(); i++) {
            HoldRecord record = HoldRecord.fromTag(holds.getCompound(i));
            if (record != null) {
                data.holds.put(record.ticketOwner(), record);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (MaidRecord record : records.values()) {
            list.add(record.toTag());
        }
        tag.put(RECORDS_TAG, list);
        ListTag rallies = new ListTag();
        for (RallyRecord record : this.rallies.values()) {
            rallies.add(record.toTag());
        }
        tag.put(RALLIES_TAG, rallies);
        ListTag holds = new ListTag();
        for (HoldRecord record : this.holds.values()) {
            holds.add(record.toTag());
        }
        tag.put(HOLDS_TAG, holds);
        return tag;
    }

    public boolean isRegistered(UUID maidId) {
        return records.containsKey(maidId);
    }

    @Nullable
    public MaidRecord get(UUID maidId) {
        return records.get(maidId);
    }

    public void register(MaidRecord record) {
        records.put(record.maidId(), record);
        setDirty();
    }

    public void unregister(UUID maidId) {
        if (records.remove(maidId) != null) {
            setDirty();
        }
    }

    public List<MaidRecord> recordsFor(UUID ownerId) {
        List<MaidRecord> result = new ArrayList<>();
        for (MaidRecord record : records.values()) {
            if (record.ownerId().equals(ownerId)) {
                result.add(record);
            }
        }
        return result;
    }

    public Collection<MaidRecord> allRecords() {
        return new ArrayList<>(records.values());
    }

    public boolean isHoldOwner(UUID ticketOwner) {
        return holds.containsKey(ticketOwner);
    }

    @Nullable
    public HoldRecord getHoldByMaid(UUID maidId) {
        for (HoldRecord record : holds.values()) {
            if (record.maidId().equals(maidId)) {
                return record;
            }
        }
        return null;
    }

    public Collection<HoldRecord> allHolds() {
        return new ArrayList<>(holds.values());
    }

    public void putHold(HoldRecord record) {
        holds.put(record.ticketOwner(), record);
        setDirty();
    }

    public void removeHold(UUID ticketOwner) {
        if (holds.remove(ticketOwner) != null) {
            setDirty();
        }
    }

    public record HoldRecord(UUID ticketOwner, UUID maidId, ResourceLocation dimension,
                             int chunkX, int chunkZ) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Ticket", ticketOwner);
            tag.putUUID("Maid", maidId);
            tag.putString("Dimension", dimension.toString());
            tag.putInt("ChunkX", chunkX);
            tag.putInt("ChunkZ", chunkZ);
            return tag;
        }

        @Nullable
        public static HoldRecord fromTag(CompoundTag tag) {
            if (!tag.hasUUID("Ticket") || !tag.hasUUID("Maid")) {
                return null;
            }
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (dimension == null) {
                return null;
            }
            return new HoldRecord(tag.getUUID("Ticket"), tag.getUUID("Maid"), dimension,
                    tag.getInt("ChunkX"), tag.getInt("ChunkZ"));
        }
    }

    public boolean isRallyOwner(UUID ticketOwner) {
        return rallies.containsKey(ticketOwner);
    }

    @Nullable
    public RallyRecord getRally(UUID playerId, int team) {
        for (RallyRecord record : rallies.values()) {
            if (record.playerId().equals(playerId) && record.team() == team) {
                return record;
            }
        }
        return null;
    }

    public Collection<RallyRecord> allRallies() {
        return new ArrayList<>(rallies.values());
    }

    public void putRally(RallyRecord record) {
        removeRally(record.playerId(), record.team());
        rallies.put(record.ticketOwner(), record);
        setDirty();
    }

    public void removeRally(UUID playerId, int team) {
        rallies.values().removeIf(record ->
                record.playerId().equals(playerId) && record.team() == team);
        setDirty();
    }

    public void removeRally(UUID ticketOwner) {
        if (rallies.remove(ticketOwner) != null) {
            setDirty();
        }
    }

    public record RallyRecord(UUID ticketOwner, UUID playerId, int team,
                              ResourceLocation dimension, int chunkX, int chunkZ) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Ticket", ticketOwner);
            tag.putUUID("Player", playerId);
            tag.putInt("Team", team);
            tag.putString("Dimension", dimension.toString());
            tag.putInt("ChunkX", chunkX);
            tag.putInt("ChunkZ", chunkZ);
            return tag;
        }

        @Nullable
        public static RallyRecord fromTag(CompoundTag tag) {
            if (!tag.hasUUID("Ticket") || !tag.hasUUID("Player")) return null;
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (dimension == null) return null;
            return new RallyRecord(tag.getUUID("Ticket"), tag.getUUID("Player"),
                    tag.getInt("Team"), dimension, tag.getInt("ChunkX"), tag.getInt("ChunkZ"));
        }
    }

    public record MaidRecord(UUID maidId, UUID ownerId, ResourceLocation dimension,
                             int chunkX, int chunkZ) {
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("MaidId", maidId);
            tag.putUUID("OwnerId", ownerId);
            tag.putString("Dimension", dimension.toString());
            tag.putInt("ChunkX", chunkX);
            tag.putInt("ChunkZ", chunkZ);
            return tag;
        }

        @Nullable
        public static MaidRecord fromTag(CompoundTag tag) {
            if (!tag.hasUUID("MaidId") || !tag.hasUUID("OwnerId")) {
                return null;
            }
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (dimension == null) {
                return null;
            }
            return new MaidRecord(tag.getUUID("MaidId"), tag.getUUID("OwnerId"),
                    dimension, tag.getInt("ChunkX"), tag.getInt("ChunkZ"));
        }
    }
}
