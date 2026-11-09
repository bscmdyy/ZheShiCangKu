package com.tlmformation.voice;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.tactical.TacticalCommand;
import com.tlmformation.team.MaidTeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MaidVoiceManager {

    private static final String[] EMERGENCY = {
            "voice.tlmformation.emergency.0",
            "voice.tlmformation.emergency.1",
            "voice.tlmformation.emergency.2",
            "voice.tlmformation.emergency.3",
            "voice.tlmformation.emergency.4"
    };
    private static final String[] MARK = {
            "voice.tlmformation.mark.0", "voice.tlmformation.mark.1",
            "voice.tlmformation.mark.2", "voice.tlmformation.mark.3",
            "voice.tlmformation.mark.4", "voice.tlmformation.mark.5",
            "voice.tlmformation.mark.6", "voice.tlmformation.mark.7",
            "voice.tlmformation.mark.8", "voice.tlmformation.mark.9"
    };
    private static final String[] LOCK = {
            "voice.tlmformation.lock.0", "voice.tlmformation.lock.1",
            "voice.tlmformation.lock.2", "voice.tlmformation.lock.3",
            "voice.tlmformation.lock.4", "voice.tlmformation.lock.5",
            "voice.tlmformation.lock.6", "voice.tlmformation.lock.7",
            "voice.tlmformation.lock.8", "voice.tlmformation.lock.9"
    };
    private static final String[] FOLLOW = {
            "voice.tlmformation.follow.0", "voice.tlmformation.follow.1",
            "voice.tlmformation.follow.2", "voice.tlmformation.follow.3",
            "voice.tlmformation.follow.4", "voice.tlmformation.follow.5",
            "voice.tlmformation.follow.6", "voice.tlmformation.follow.7",
            "voice.tlmformation.follow.8", "voice.tlmformation.follow.9"
    };
    private static final String[] RETREAT = {
            "voice.tlmformation.retreat.0", "voice.tlmformation.retreat.1",
            "voice.tlmformation.retreat.2", "voice.tlmformation.retreat.3",
            "voice.tlmformation.retreat.4", "voice.tlmformation.retreat.5",
            "voice.tlmformation.retreat.6", "voice.tlmformation.retreat.7"
    };
    private static final String[] GUARD = {
            "voice.tlmformation.guard.0", "voice.tlmformation.guard.1",
            "voice.tlmformation.guard.2", "voice.tlmformation.guard.3",
            "voice.tlmformation.guard.4", "voice.tlmformation.guard.5",
            "voice.tlmformation.guard.6", "voice.tlmformation.guard.7"
    };
    private static final String[] HOLD = {
            "voice.tlmformation.hold.0", "voice.tlmformation.hold.1",
            "voice.tlmformation.hold.2", "voice.tlmformation.hold.3",
            "voice.tlmformation.hold.4", "voice.tlmformation.hold.5",
            "voice.tlmformation.hold.6", "voice.tlmformation.hold.7"
    };

    private MaidVoiceManager() {
    }

    public static void emergencyCall(ServerPlayer player) {
        speakTwo(player, EMERGENCY);
    }

    public static void onCommand(ServerPlayer player, TacticalCommand command) {
        String[] lines = switch (command) {
            case MARK -> MARK;
            case LOCK -> LOCK;
            case FOLLOW -> FOLLOW;
            case RETREAT -> RETREAT;
            case GUARD -> GUARD;
            case HOLD -> HOLD;
            default -> null;
        };
        if (lines != null) speakTwo(player, lines);
    }


    private static void speakTwo(ServerPlayer player, String[] pool) {
        List<EntityMaid> maids = activeMaidEntities(player);
        if (maids.isEmpty()) return;

        Collections.shuffle(maids, new java.util.Random(player.getRandom().nextLong()));
        List<String> lines = new ArrayList<>(List.of(pool));
        Collections.shuffle(lines, new java.util.Random(player.getRandom().nextLong()));

        int count = Math.min(2, Math.min(maids.size(), lines.size()));
        for (int i = 0; i < count; i++) {
            say(player, maids.get(i), lines.get(i), ChatFormatting.AQUA);
        }
    }

    private static List<EntityMaid> activeMaidEntities(ServerPlayer player) {
        List<EntityMaid> result = new ArrayList<>();
        for (UUID id : MaidTeamManager.activeMembers(player)) {
            for (EntityMaid maid : player.serverLevel().getEntitiesOfClass(
                    EntityMaid.class, player.getBoundingBox().inflate(128))) {
                if (maid.getUUID().equals(id) && maid.isAlive()) {
                    result.add(maid);
                    break;
                }
            }
        }
        return result;
    }

    private static void say(ServerPlayer player, EntityMaid maid,
                            String line, ChatFormatting color) {
        // line 现在是语言键（zh/en 由客户端语言决定）
        player.sendSystemMessage(Component.literal("<" + maid.getName().getString() + "> ")
                .append(Component.translatable(line))
                .withStyle(color));
    }
}
