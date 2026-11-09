package com.tlmformation.client;

import com.tlmformation.TLMFormationMod;
import com.tlmformation.config.HudConfig;
import com.tlmformation.network.S2CTeamData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeamHudRenderer {
    private static final int BASE_W = 220;
    private static final int MICRO_H = 146;
    private static final int GRAND_H = 168;
    public static int lastX, lastY, lastW, lastH;

    private TeamHudRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (HudConfig.uiHidden()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        S2CTeamData data = TeamClientData.current();
        boolean grand = GrandModeClient.isActive();
        int active = data == null ? -1 : data.activeTeam();

        if (data == null || (!grand && active < 0)) {
            drawStandby(event.getGuiGraphics(), mc);
            return;
        }

        int baseH = grand ? GRAND_H : MICRO_H;
        float scale = HudConfig.scale();
        int x = 10 + HudConfig.xOffset();
        int y = mc.getWindow().getGuiScaledHeight()
                - Math.round(baseH * scale) - 8 + HudConfig.yOffset();
        lastX = x;
        lastY = y;
        lastW = Math.round(BASE_W * scale);
        lastH = Math.round(baseH * scale);

        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().translate(ParallaxHud.x() * 1.5F,
                ParallaxHud.y() * 1.5F, 0.0F);
        g.pose().scale(scale, scale, 1.0F);

        g.fill(0, 0, BASE_W, baseH, 0xC0202020);
        outline(g, BASE_W, baseH);
        if (grand) drawGrand(g, data);
        else drawMicro(g, data, active);
        drawLogisticsStatus(g, baseH);

        g.pose().popPose();

        if (HudEditState.isEditing()) {
            g.fill(lastX, lastY, lastX + 14, lastY + 14, 0xD055AAFF);
            g.fill(lastX + lastW - 14, lastY + lastH - 14,
                    lastX + lastW, lastY + lastH, 0xD0FFD45C);
        }
    }

    private static void drawLogisticsStatus(GuiGraphics g, int baseH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int y = baseH - 16;
        boolean has = com.tlmformation.logistics.LogisticsManager
                .hasLogistics(mc.player);

        if (!has) {
            g.drawString(mc.font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.logistics_none").getString(), 8, y,
                    0xFF888888, false);
            return;
        }

        int rescue = com.tlmformation.logistics.LogisticsManager
                .rescueCooldownSeconds(mc.player);
        int barrier = com.tlmformation.logistics.HealingBarrierManager
                .barrierCooldownSeconds(mc.player);

        String rescueText = rescue <= 0
                ? "[后勤] 救援协议: 就绪"
                : "[后勤] 救援协议: 冷却中 (" + rescue + "s)";
        int rescueColor = rescue <= 0 ? 0xFF88FF88 : 0xFFAAAAAA;
        g.drawString(mc.font, rescueText, 8, y, rescueColor, false);

        int x = 8 + mc.font.width(rescueText);
        String barrierText = barrier <= 0
                ? " | 结界: 就绪"
                : " | 结界: 冷却 (" + barrier + "s)";
        int barrierColor = barrier <= 0 ? 0xFF88FF88 : 0xFFAAAAAA;
        g.drawString(mc.font, barrierText, x, y, barrierColor, false);
    }

    private static void drawStandby(GuiGraphics g, Minecraft mc) {
        float scale = HudConfig.scale();
        int x = mc.getWindow().getGuiScaledWidth()
                - Math.round(220 * scale) - 8 + HudConfig.xOffset();
        int y = mc.getWindow().getGuiScaledHeight()
                - Math.round(24 * scale) - 8 + HudConfig.yOffset();
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().translate(ParallaxHud.x() * 1.5F,
                ParallaxHud.y() * 1.5F, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.fill(0, 0, 220, 24, 0xC0202020);
        outline(g, 220, 24);
        g.drawString(mc.font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.hud_standby").getString(), 8, 8, 0xFFAAAAAA, false);
        g.pose().popPose();
    }

    private static void drawMicro(GuiGraphics g, S2CTeamData data, int active) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.hud_squad_status", active + 1).getString(), 8, 8, 0xFFFFD45C, false);
        g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.hud_protocol", TacticalClientState.protocolName()).getString(),
                108, 8, 0xFF55FFFF, false);

        S2CTeamData.TeamView view = active >= 0 && active < data.teams().size()
                ? data.teams().get(active) : null;
        if (view == null) return;

        int y = 25;
        int count = Math.min(6, view.members().size());
        for (int i = 0; i < count; i++) {
            UUID id = view.members().get(i);
            S2CTeamData.Member member = member(data, id);

            if (member != null && member.downed()) {
                g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.hud_downed", member.name()).getString(),
                        8, y, 0xFFFF5555, false);
                y += 17;
                continue;
            }

            g.drawString(font, font.plainSubstrByWidth(
                    member == null ? "?" : member.name(), 58), 8, y, 0xFFFFFFFF, false);

            Entity entity = entity(id, member);
            if (member != null && (member.hasDeath() || member.waitingRevive())) {
                g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.hud_waiting_revive").getString(), 70, y, 0xFFFF7777, false);
            } else if (entity instanceof LivingEntity living) {
                g.drawString(font, profession(living), 70, y, 0xFF55FFFF, false);
                int hp = (int) (44 * living.getHealth()
                        / Math.max(1.0F, living.getMaxHealth()));
                g.fill(104, y + 2, 148, y + 7, 0xFF441111);
                g.fill(104, y + 2, 104 + hp, y + 7, 0xFFDD3333);
                g.drawString(font, (int) living.getHealth() + "/"
                        + (int) living.getMaxHealth(), 152, y, 0xFFFFFFFF, false);
            } else {
                g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.hud_unloaded").getString(), 70, y, 0xFFAAAAAA, false);
            }
            y += 17;
        }
    }

    private static void drawGrand(GuiGraphics g, S2CTeamData data) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.hud_grand").getString(), 8, 8, 0xFF55FFFF, false);

        int dead = 0;
        Map<String, Integer> roles = new HashMap<>();
        for (S2CTeamData.Member member : data.allMaids()) {
            if (member.hasDeath() || member.waitingRevive()) {
                dead++;
                continue;
            }
            Entity entity = entity(member.uuid(), member);
            String role = entity instanceof LivingEntity living
                    ? profession(living) : "未知";
            roles.merge(role, 1, Integer::sum);
        }

        int y = 26;
        for (int i = 0; i < 5; i++) {
            S2CTeamData.TeamView view = i < data.teams().size()
                    ? data.teams().get(i) : null;
            int members = view == null ? 0 : view.members().size();
            boolean deployed = GrandModeClient.isActive() || records(data, i) > 0;
            g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.team", i + 1).getString(), 8, y, 0xFFFFFFFF, false);
            g.drawString(font, deployed ? net.minecraft.network.chat.Component.translatable("gui.tlmformation.deployed_bracket").getString() : net.minecraft.network.chat.Component.translatable("gui.tlmformation.deploy_bracket").getString(),
                    62, y, deployed ? 0xFF55FFFF : 0xFF777777, false);
            g.drawString(font, members + "/6", 172, y, 0xFFAAAAAA, false);
            y += 15;
        }

        y += 4;
        g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.hud_roles").getString(), 8, y, 0xFFAAAAAA, false);
        y += 13;
        StringBuilder rolesText = new StringBuilder();
        for (Map.Entry<String, Integer> e : roles.entrySet()) {
            if (rolesText.length() > 0) rolesText.append("  ");
            rolesText.append(e.getKey()).append(":").append(e.getValue());
        }
        g.drawString(font, rolesText.length() == 0 ? net.minecraft.network.chat.Component.translatable("gui.tlmformation.none").getString() : rolesText.toString(),
                8, y, 0xFFFFFFFF, false);
        g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.hud_dead", dead).getString(), 8, y + 15, 0xFFFF7777, false);
    }

    private static int records(S2CTeamData data, int team) {
        if (team < 0 || team >= data.teams().size()) return 0;
        return data.teams().get(team).members().size();
    }

    @Nullable
    private static S2CTeamData.Member member(S2CTeamData data, UUID id) {
        for (S2CTeamData.Member m : data.allMaids()) {
            if (m.uuid().equals(id)) return m;
        }
        return null;
    }

    @Nullable
    private static Entity entity(UUID id, @Nullable S2CTeamData.Member member) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        int entityId = member == null ? -1 : member.entityId();
        return entityId < 0 ? null : mc.level.getEntity(entityId);
    }

    private static String profession(LivingEntity entity) {
        if (!(entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) {
            return "未知";
        }
        if (maid.getTask() instanceof com.tlmformation.tactical.TacticalLogisticsTask) {
            return "后勤";
        }
        var task = maid.getTask();
        String cls = task.getClass().getSimpleName().toLowerCase();
        String uid = task.getUid().toString().toLowerCase();
        if (cls.contains("spellcombat") || cls.contains("spellcast") || uid.contains("spell")) {
            return "法师";
        }
        if (cls.contains("gun") || cls.contains("tacz")
                || uid.contains("gun") || uid.contains("tacz")) {
            return "枪手";
        }
        if (cls.contains("archer") || cls.contains("bow") || cls.contains("ranged")
                || uid.contains("archer") || uid.contains("bow")) {
            return "射手";
        }
        if (cls.contains("maidmelee") || cls.contains("meleeattack")
                || cls.contains("attacktask") || uid.contains("melee") || uid.contains("attack")) {
            return "近战";
        }
        return "待定";
    }

    private static void outline(GuiGraphics g, int w, int h) {
        g.fill(0, 0, w, 1, 0xFFAAAAAA);
        g.fill(0, h - 1, w, h, 0xFFAAAAAA);
        g.fill(0, 0, 1, h, 0xFFAAAAAA);
        g.fill(w - 1, 0, w, h, 0xFFAAAAAA);
    }
}
