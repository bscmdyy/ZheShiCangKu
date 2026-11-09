package com.tlmformation.team;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.TLMFormationMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/** Full EntityMaid NBT backup used only when a summon cannot find the entity. */
public final class MaidBackupManager {
    private static final String TAG_ROOT = "TLMMaidBackups";

    private MaidBackupManager() {
    }

    public static void save(ServerPlayer player, EntityMaid maid) {
        CompoundTag backup = new CompoundTag();
        maid.saveWithoutId(backup);
        backup.putUUID("UUID", maid.getUUID());
        backup.putString("id", net.minecraft.world.entity.EntityType
                .getKey(maid.getType()).toString());
        backup.putUUID("BackupMaid", maid.getUUID());
        backup.putInt("BackupTeam", MaidTeamManager.getTeamIndex(player, maid.getUUID()));
        backup.putString("BackupDim", maid.level().dimension().location().toString());
        // 备份显示名：F9 面板在女仆所在区块未加载时也能显示名字（而不是“?”）
        try {
            backup.putString("BackupName", maid.getName().getString());
        } catch (Throwable ignored) {
            // 名字取不到就不写，读取端会退回 CustomName
        }

        CompoundTag root = player.getPersistentData().getCompound(TAG_ROOT);
        root.put(maid.getUUID().toString(), backup);
        player.getPersistentData().put(TAG_ROOT, root);
    }

    @Nullable
    public static CompoundTag read(ServerPlayer player, UUID maidId) {
        CompoundTag root = player.getPersistentData().getCompound(TAG_ROOT);
        return root.contains(maidId.toString(), CompoundTag.TAG_COMPOUND)
                ? root.getCompound(maidId.toString()).copy() : null;
    }

    /**
     * 从备份里取女仆显示名（供 F9 面板显示未加载成员）。
     *
     * <p>优先 {@code BackupName}（新版本写入）；老备份退回解析 {@code CustomName}（JSON 文本）；
     * 都没有返回 null，由调用方决定显示什么。</p>
     */
    @Nullable
    public static String readName(ServerPlayer player, UUID maidId) {
        CompoundTag backup = read(player, maidId);
        if (backup == null) {
            return null;
        }
        if (backup.contains("BackupName", net.minecraft.nbt.Tag.TAG_STRING)) {
            String name = backup.getString("BackupName");
            if (!name.isBlank()) {
                return name;
            }
        }
        if (backup.contains("CustomName", net.minecraft.nbt.Tag.TAG_STRING)) {
            try {
                net.minecraft.network.chat.Component component =
                        net.minecraft.network.chat.Component.Serializer
                                .fromJson(backup.getString("CustomName"));
                if (component != null) {
                    String name = component.getString();
                    if (!name.isBlank()) {
                        return name;
                    }
                }
            } catch (Throwable ignored) {
                // 名字 JSON 损坏：返回 null
            }
        }
        return null;
    }
}
