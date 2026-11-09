package com.tlmformation.voice;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.team.MaidTeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GroupChatManager {
    private static final int INTERVAL = 1800;
    private static final int SPEAK_INTERVAL = 30;
    private static final Map<UUID, Long> NEXT_CHAT = new HashMap<>();
    private static List<String[]> playOrder;
    private static int currentIndex;

    public static final String[][] CHAT_GROUP_INTERACTIONS = new String[][] {
            {"chat.tlmformation.group.0.0", "chat.tlmformation.group.0.1", "chat.tlmformation.group.0.2"},
            {"chat.tlmformation.group.1.0", "chat.tlmformation.group.1.1", "chat.tlmformation.group.1.2"},
            {"chat.tlmformation.group.2.0", "chat.tlmformation.group.2.1", "chat.tlmformation.group.2.2"},
            {"chat.tlmformation.group.3.0", "chat.tlmformation.group.3.1", "chat.tlmformation.group.3.2"},
            {"chat.tlmformation.group.4.0", "chat.tlmformation.group.4.1", "chat.tlmformation.group.4.2"},
            {"chat.tlmformation.group.5.0", "chat.tlmformation.group.5.1", "chat.tlmformation.group.5.2"},
            {"chat.tlmformation.group.6.0", "chat.tlmformation.group.6.1", "chat.tlmformation.group.6.2"},
            {"chat.tlmformation.group.7.0", "chat.tlmformation.group.7.1", "chat.tlmformation.group.7.2"},
            {"chat.tlmformation.group.8.0", "chat.tlmformation.group.8.1", "chat.tlmformation.group.8.2"},
            {"chat.tlmformation.group.9.0", "chat.tlmformation.group.9.1", "chat.tlmformation.group.9.2"},
            {"chat.tlmformation.group.10.0", "chat.tlmformation.group.10.1", "chat.tlmformation.group.10.2", "chat.tlmformation.group.10.3"},
            {"chat.tlmformation.group.11.0", "chat.tlmformation.group.11.1", "chat.tlmformation.group.11.2", "chat.tlmformation.group.11.3"},
            {"chat.tlmformation.group.12.0", "chat.tlmformation.group.12.1", "chat.tlmformation.group.12.2", "chat.tlmformation.group.12.3"},
            {"chat.tlmformation.group.13.0", "chat.tlmformation.group.13.1", "chat.tlmformation.group.13.2", "chat.tlmformation.group.13.3"},
            {"chat.tlmformation.group.14.0", "chat.tlmformation.group.14.1", "chat.tlmformation.group.14.2", "chat.tlmformation.group.14.3"},
            {"chat.tlmformation.group.15.0", "chat.tlmformation.group.15.1", "chat.tlmformation.group.15.2", "chat.tlmformation.group.15.3"},
            {"chat.tlmformation.group.16.0", "chat.tlmformation.group.16.1", "chat.tlmformation.group.16.2", "chat.tlmformation.group.16.3"},
            {"chat.tlmformation.group.17.0", "chat.tlmformation.group.17.1", "chat.tlmformation.group.17.2", "chat.tlmformation.group.17.3"},
            {"chat.tlmformation.group.18.0", "chat.tlmformation.group.18.1", "chat.tlmformation.group.18.2", "chat.tlmformation.group.18.3"},
            {"chat.tlmformation.group.19.0", "chat.tlmformation.group.19.1", "chat.tlmformation.group.19.2", "chat.tlmformation.group.19.3"},
            {"chat.tlmformation.group.20.0", "chat.tlmformation.group.20.1", "chat.tlmformation.group.20.2", "chat.tlmformation.group.20.3", "chat.tlmformation.group.20.4"},
            {"chat.tlmformation.group.21.0", "chat.tlmformation.group.21.1", "chat.tlmformation.group.21.2", "chat.tlmformation.group.21.3", "chat.tlmformation.group.21.4"},
            {"chat.tlmformation.group.22.0", "chat.tlmformation.group.22.1", "chat.tlmformation.group.22.2", "chat.tlmformation.group.22.3", "chat.tlmformation.group.22.4"},
            {"chat.tlmformation.group.23.0", "chat.tlmformation.group.23.1", "chat.tlmformation.group.23.2", "chat.tlmformation.group.23.3", "chat.tlmformation.group.23.4"},
            {"chat.tlmformation.group.24.0", "chat.tlmformation.group.24.1", "chat.tlmformation.group.24.2", "chat.tlmformation.group.24.3", "chat.tlmformation.group.24.4"},
            {"chat.tlmformation.group.25.0", "chat.tlmformation.group.25.1", "chat.tlmformation.group.25.2", "chat.tlmformation.group.25.3", "chat.tlmformation.group.25.4"},
            {"chat.tlmformation.group.26.0", "chat.tlmformation.group.26.1", "chat.tlmformation.group.26.2", "chat.tlmformation.group.26.3", "chat.tlmformation.group.26.4"},
            {"chat.tlmformation.group.27.0", "chat.tlmformation.group.27.1", "chat.tlmformation.group.27.2", "chat.tlmformation.group.27.3", "chat.tlmformation.group.27.4"},
            {"chat.tlmformation.group.28.0", "chat.tlmformation.group.28.1", "chat.tlmformation.group.28.2", "chat.tlmformation.group.28.3", "chat.tlmformation.group.28.4"},
            {"chat.tlmformation.group.29.0", "chat.tlmformation.group.29.1", "chat.tlmformation.group.29.2", "chat.tlmformation.group.29.3", "chat.tlmformation.group.29.4"},
            {"chat.tlmformation.group.30.0", "chat.tlmformation.group.30.1", "chat.tlmformation.group.30.2", "chat.tlmformation.group.30.3", "chat.tlmformation.group.30.4", "chat.tlmformation.group.30.5"},
            {"chat.tlmformation.group.31.0", "chat.tlmformation.group.31.1", "chat.tlmformation.group.31.2", "chat.tlmformation.group.31.3", "chat.tlmformation.group.31.4", "chat.tlmformation.group.31.5"},
            {"chat.tlmformation.group.32.0", "chat.tlmformation.group.32.1", "chat.tlmformation.group.32.2", "chat.tlmformation.group.32.3", "chat.tlmformation.group.32.4", "chat.tlmformation.group.32.5"},
            {"chat.tlmformation.group.33.0", "chat.tlmformation.group.33.1", "chat.tlmformation.group.33.2", "chat.tlmformation.group.33.3", "chat.tlmformation.group.33.4", "chat.tlmformation.group.33.5"},
            {"chat.tlmformation.group.34.0", "chat.tlmformation.group.34.1", "chat.tlmformation.group.34.2", "chat.tlmformation.group.34.3", "chat.tlmformation.group.34.4", "chat.tlmformation.group.34.5"},
            {"chat.tlmformation.group.35.0", "chat.tlmformation.group.35.1", "chat.tlmformation.group.35.2", "chat.tlmformation.group.35.3", "chat.tlmformation.group.35.4", "chat.tlmformation.group.35.5"},
            {"chat.tlmformation.group.36.0", "chat.tlmformation.group.36.1", "chat.tlmformation.group.36.2", "chat.tlmformation.group.36.3", "chat.tlmformation.group.36.4", "chat.tlmformation.group.36.5"},
            {"chat.tlmformation.group.37.0", "chat.tlmformation.group.37.1", "chat.tlmformation.group.37.2", "chat.tlmformation.group.37.3", "chat.tlmformation.group.37.4", "chat.tlmformation.group.37.5"},
            {"chat.tlmformation.group.38.0", "chat.tlmformation.group.38.1", "chat.tlmformation.group.38.2", "chat.tlmformation.group.38.3", "chat.tlmformation.group.38.4", "chat.tlmformation.group.38.5"},
            {"chat.tlmformation.group.39.0", "chat.tlmformation.group.39.1", "chat.tlmformation.group.39.2", "chat.tlmformation.group.39.3", "chat.tlmformation.group.39.4", "chat.tlmformation.group.39.5"},
    };

    private GroupChatManager() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long now = event.getServer().overworld().getGameTime();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Long next = NEXT_CHAT.get(player.getUUID());
            if (next == null) {
                NEXT_CHAT.put(player.getUUID(), now + INTERVAL);
                continue;
            }
            if (now < next) continue;

            NEXT_CHAT.put(player.getUUID(), now + INTERVAL);
            tryChat(player);
        }
    }

    private static boolean tryChat(ServerPlayer player) {
        List<EntityMaid> maids = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (UUID id : MaidTeamManager.activeMembers(player)) {
            for (EntityMaid maid : player.serverLevel().getEntitiesOfClass(
                    EntityMaid.class, player.getBoundingBox().inflate(128))) {
                if (maid.getUUID().equals(id) && maid.isAlive() && seen.add(id)) {
                    maids.add(maid);
                    break;
                }
            }
        }
        if (maids.size() < 3) {
            System.out.println("[语音] 跳过：存活女仆不足 3 人");
            return false;
        }

        List<String[]> order = playOrder();
        for (int guard = 0; guard < order.size(); guard++) {
            if (currentIndex >= order.size()) {
                currentIndex = 0;
                Collections.shuffle(order);
            }

            int playedIndex = currentIndex;
            String[] lines = order.get(currentIndex);
            currentIndex++;

            if (lines.length > maids.size()) {
                continue;
            }

            Collections.shuffle(maids,
                    new java.util.Random(player.getRandom().nextLong()));

            System.out.println("[语音] 播放第 " + (playedIndex + 1)
                    + " 条，共 " + lines.length + " 人");

            for (int i = 0; i < lines.length; i++) {
                final EntityMaid maid = maids.get(i);
                final String line = lines[i];
                player.server.tell(new net.minecraft.server.TickTask(
                        i * SPEAK_INTERVAL,
                        () -> speak(player, maid, line)));
            }
            return true;
        }

        System.out.println("[语音] 本轮没有符合人数的条目");
        return false;
    }

    private static List<String[]> playOrder() {
        if (playOrder == null) {
            playOrder = new ArrayList<>(Arrays.asList(CHAT_GROUP_INTERACTIONS));
            Collections.shuffle(playOrder);
        }
        return playOrder;
    }

    private static void speak(ServerPlayer player, EntityMaid maid, String line) {
        // line 现在是语言键（zh/en 由客户端语言决定）
        player.sendSystemMessage(Component.literal("<"
                + maid.getName().getString() + "> ")
                .append(Component.translatable(line))
                .withStyle(ChatFormatting.GRAY));
    }
}
