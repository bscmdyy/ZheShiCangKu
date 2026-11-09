package com.tlmformation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.tlmformation.TLMFormationMod;
import com.tlmformation.network.NetworkHandler;
import com.tlmformation.network.S2CTeamData;
import com.tlmformation.team.TeamAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MaidTeamScreen extends Screen {
    private static final int W = 520;
    private static final int H = 320;
    private static final int STAGE_X = 8;
    private static final int STAGE_W = 374;
    private static final int RIGHT_X = 390;
    private static final int RIGHT_W = 122;
    private static final int ROW_H = 13;

    private int px, py;
    @Nullable private UUID selectedMaid;
    @Nullable private UUID selectedDisplay;
    @Nullable private UUID pendingRemove;
    private int expandedTeam = -1;
    private boolean autoSelectTeam = true;
    private int pendingDeleteTeam = -1;
    private int teamScroll, maidScroll;
    private final Map<UUID, Entity> fakeModels = new HashMap<>();
    private final Map<UUID, Rect> frames = new HashMap<>();

    public MaidTeamScreen() {
        super(Component.translatable("screen.tlmformation.teams"));
    }

    @Override
    protected void init() {
        super.init();
        fakeModels.clear();
        frames.clear();
        autoSelectTeam = true;
        S2CTeamData cached = TeamClientData.current();
        if (cached != null) selectInitialTeam(cached);
        NetworkHandler.sendTeamAction(TeamAction.REQUEST, 0, null);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        px = (width - W) / 2;
        py = (height - H) / 2;
        g.fill(px, py, px + W, py + H, 0xC0202020);
        g.fill(px, py, px + W, py + 22, 0xC0181818);
        g.drawCenteredString(font, title, px + W / 2, py + 7, 0xFFFFFFFF);

        S2CTeamData data = TeamClientData.current();
        if (data == null) {
            g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.syncing").getString(), px + 12, py + 40, 0xFFAAAAAA, false);
            return;
        }
        if (autoSelectTeam) selectInitialTeam(data);
        frames.clear();
        drawStage(g, data, mouseX, mouseY);
        drawRightLists(g, data, mouseX, mouseY);
        drawBottom(g, data, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void selectInitialTeam(S2CTeamData data) {
        int target = data.activeTeam();
        if (target < 0 || target >= data.teams().size()) target = 0;
        expandedTeam = target;
        autoSelectTeam = false;
    }

    private void drawStage(GuiGraphics g, S2CTeamData data, int mouseX, int mouseY) {
        int sx = px + STAGE_X;
        int sy = py + 30;
        int sw = STAGE_W;
        int sh = H - 64;
        int gap = 3;
        int cellW = (sw - gap * 2) / 3;
        int cellH = (sh - gap) / 2;

        g.fill(sx, sy, sx + sw, sy + sh, 0xA0202020);
        // Flat 2D tactical grid; no 3D geometry, no missing-texture cube.
        TacticalGridRenderer.draw(g, sx, sy, sw, sh, 16, 0x24FFFFFF);
        if (expandedTeam < 0 || expandedTeam >= data.teams().size()) return;

        S2CTeamData.TeamView view = data.teams().get(expandedTeam);
        int count = Math.min(6, view.members().size());
        int leftW = 68;

        for (int i = 0; i < 6; i++) {
            int col = i % 3;
            int row = i / 3;
            int cellX = sx + col * (cellW + gap);
            int cellY = sy + row * (cellH + gap);

            UUID id = i < count ? view.members().get(i) : null;
            S2CTeamData.Member member = id == null ? null : memberOf(data, id);
            boolean sel = id != null && id.equals(selectedDisplay);
            boolean hover = id != null && inside(mouseX, mouseY,
                    cellX, cellY, cellW, cellH);
            if (id != null) frames.put(id, new Rect(cellX, cellY, cellW, cellH));

            g.fill(cellX, cellY, cellX + cellW, cellY + cellH, 0x30101010);
            int border = sel ? 0xFFFFD45C : hover ? 0xFFFFFFFF : 0xFF777777;
            drawInfoBorder(g, cellX, cellY, cellW, cellH,
                    border, hover || sel ? 2 : 1);

            if (id == null) {
                g.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.empty_slot").getString(),
                        cellX + cellW / 2, cellY + cellH / 2 - 4,
                        0xFF888888);
                continue;
            }

            Entity entity = entityOf(data, id);
            String name = displayName(member);
            int modelX = cellX + Math.round(cellW * 0.56F);
            int modelY = cellY + Math.round(cellH * 0.60F);

            // Right half: enlarged model, slightly raised.
            if (entity instanceof LivingEntity living) {
                RenderSystem.disableDepthTest();
                g.pose().pushPose();
                g.pose().translate(modelX, modelY, 0.0F);
                g.pose().mulPose(Axis.XP.rotationDegrees(-20.0F));
                g.pose().mulPose(Axis.YP.rotationDegrees(30.0F));
                g.pose().translate(-modelX, -modelY, 0.0F);
                try {
                    InventoryScreen.renderEntityInInventoryFollowsAngle(
                            g, modelX, modelY, 31, 0.0F, 0.0F, living);
                } catch (RuntimeException ignored) {}
                g.pose().popPose();
                RenderSystem.enableDepthTest();
            }

            // Left column: profession icon + text.
            if (entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
                renderProfessionIcon(g, maid, cellX + 4, cellY + 4);
                g.drawString(font, professionOf(maid), cellX + 22, cellY + 8,
                        0xFF55FFFF, false);
            }

            // Name marquee, left column.
            int nameX = cellX + 4;
            int nameW = leftW - 6;
            int textW = font.width(name);
            int drawX = textW <= nameW ? nameX
                    : nameX - (int) ((System.currentTimeMillis() / 35L)
                    % (textW + 14));
            g.enableScissor(nameX, cellY + 23, cellX + leftW, cellY + 35);
            g.drawString(font, name, drawX, cellY + 24, 0xFFFFFFFF, false);
            g.disableScissor();

            int hpY = cellY + 39;
            int barX = cellX + 12;
            int barW = 60;
            int barRight = barX + barW;

            if (entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
                String hpText = (int) maid.getHealth() + "/"
                        + (int) maid.getMaxHealth();
                g.drawString(font, "❤", cellX + 2, hpY, 0xFFFF5555, false);
                int hp = (int) (barW * maid.getHealth()
                        / Math.max(1.0F, maid.getMaxHealth()));
                g.fill(barX, hpY + 2, barRight, hpY + 7, 0xFF441111);
                g.fill(barX, hpY + 2, barX + hp, hpY + 7, 0xFFDD3333);
                g.drawString(font, hpText, barX, hpY + 10, 0xFFFFFFFF, false);

                int favY = cellY + 65;
                int fav = Math.max(0, Math.min(100, maid.getFavorability()));
                g.fill(barX, favY, barRight, favY + 3, 0xFF331122);
                g.fill(barX, favY, barX + barW * fav / 100, favY + 3,
                        0xFFFF88BB);
                g.drawString(font, fav + "/100", barX, favY + 5,
                        0xFFFFAACC, false);
            }

            // Reserved bottom area: compact action text buttons.
            if (sel) {
                Entity cellEntity = entityOf(data, id);
                boolean allowKick = expandedTeam == data.activeTeam()
                        && cellEntity != null && cellEntity.isAlive();
                drawCellButtons(g, cellX, cellY, cellW, cellH,
                        mouseX, mouseY, allowKick);
            }
        }
    }

    private void drawCellButtons(GuiGraphics g, int cellX, int cellY,
                                 int cellW, int cellH, int mouseX, int mouseY,
                                 boolean allowKick) {
        int w = 28, gap = 2;
        int start = cellX + (cellW - (w * 3 + gap * 2)) / 2;
        int y = cellY + cellH - 17;
        drawButton(g, "管理", start, y, w, 14,
                inside(mouseX, mouseY, start, y, w, 14), false);
        drawButton(g, "传送", start + w + gap, y, w, 14,
                inside(mouseX, mouseY, start + w + gap, y, w, 14), false);
        if (allowKick) {
            drawButton(g, "X", start + (w + gap) * 2, y, w, 14,
                    inside(mouseX, mouseY, start + (w + gap) * 2, y, w, 14),
                    pendingRemove != null && pendingRemove.equals(selectedDisplay));
        }
    }

    private void drawRightLists(GuiGraphics g, S2CTeamData data, int mouseX, int mouseY) {
        int rx = px + RIGHT_X;
        int rr = rx + RIGHT_W;
        int top = py + 30;
        int split = py + 150;
        g.fill(rx - 2, top, rr, split, 0x80181818);
        g.fill(rx - 2, split + 4, rr, py + H - 40, 0x80181818);
        g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.team_label").getString(), rx, top + 4, 0xFFFFD45C, false);
        g.enableScissor(rx, top + 18, rr, split - 4);
        int ty = top + 18 - teamScroll;
        // Fixed five slots; never hide an empty team.
        for (int i = 0; i < 5; i++) {
            S2CTeamData.TeamView view = i < data.teams().size()
                    ? data.teams().get(i) : null;
            int members = view == null ? 0 : view.members().size();
            if (ty + 18 > top + 18 && ty < split - 4) {
                boolean grand = GrandModeClient.isActive();
                boolean active = grand || (data.activeTeam() == i && members > 0);
                g.fill(rx, ty, rr, ty + 18, expandedTeam == i ? 0xFF343434
                        : active ? 0xFF3A3018 : 0xFF2A2A2A);
                g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.team", i + 1).getString(), rx + 3, ty + 4,
                        active ? 0xFFFFD45C : 0xFFDDDDDD, false);
                String status = active ? "[已部署]" : "[部署]";
                int statusX = rx + (RIGHT_W - font.width(status)) / 2;
                g.drawString(font, status, statusX, ty + 4,
                        active ? 0xFF55FFFF : 0xFF777777, false);
                g.drawString(font, members + "/6", rr - 35, ty + 4,
                        active ? 0xFFFFFFFF : 0xFFAAAAAA, false);
                g.fill(rr - 17, ty + 2, rr - 3, ty + 16, 0xFF3A5A3A);
                g.drawCenteredString(font, "+", rr - 10, ty + 4, 0xFFFFFFFF);
            }
            ty += 20;
        }
        g.disableScissor();

        int btnY = py + 158;
        drawButton(g, "战术集结", rx, btnY, RIGHT_W, 16,
                inside(mouseX, mouseY, rx, btnY, RIGHT_W, 16), false);
        drawButton(g, "战术撤退", rx, btnY + 18, RIGHT_W, 16,
                inside(mouseX, mouseY, rx, btnY + 18, RIGHT_W, 16), false);

        g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.unassigned").getString(), rx, py + 202, 0xFFFFD45C, false);
        g.enableScissor(rx, py + 216, rr, py + H - 40);
        int my = py + 216 - maidScroll;
        for (S2CTeamData.Member m : data.allMaids()) {
            if (isInAnyTeam(data, m.uuid())) continue;
            if (my + ROW_H > py + 216 && my < py + H - 40) {
                boolean hover = inside(mouseX, mouseY, rx, my, RIGHT_W, ROW_H);
                if (m.uuid().equals(selectedMaid)) g.fill(rx, my, rr, my + ROW_H, 0x885A4A18);
                else if (hover) g.fill(rx, my, rr, my + ROW_H, 0x33FFFFFF);
                g.drawString(font, font.plainSubstrByWidth(displayName(m), RIGHT_W - 4),
                        rx + 2, my + 1, 0xFFFFFFFF, false);
            }
            my += ROW_H;
        }
        g.disableScissor();
        drawButton(g, "逻辑终端", rr - 70, py + H - 28, 70, 16,
                inside(mouseX, mouseY, rr - 70, py + H - 28, 70, 16), false);
        drawButton(g, "战术面板校准", rx, py + H - 48, RIGHT_W, 16,
                inside(mouseX, mouseY, rx, py + H - 48, RIGHT_W, 16), false);
    }

    private void drawOps(GuiGraphics g, int mouseX, int mouseY) {
        int y = py + H - 64;
        int w = 46, gap = 8;
        int start = px + STAGE_X + STAGE_W / 2 - (w * 3 + gap * 2) / 2;
        drawButton(g, "管理", start, y, w, 16, inside(mouseX, mouseY, start, y, w, 16), false);
        drawButton(g, "传送", start + w + gap, y, w, 16,
                inside(mouseX, mouseY, start + w + gap, y, w, 16), false);
        drawButton(g, "X", start + (w + gap) * 2, y, w, 16,
                inside(mouseX, mouseY, start + (w + gap) * 2, y, w, 16),
                pendingRemove != null && pendingRemove.equals(selectedDisplay));
    }

    private void drawBottom(GuiGraphics g, S2CTeamData data, int mouseX, int mouseY) {
        int x = px + STAGE_X;
        int right = px + STAGE_X + STAGE_W;
        int team = expandedTeam >= 0 ? expandedTeam : data.activeTeam();
        if (team < 0) team = 0;
        String[] labels = {"部署", "小队撤离", "前往据点", "部署据点"};
        int gap = 4;
        int bw = (right - x - gap * 3) / 4;
        int by = py + H - 28;
        boolean deployed = GrandModeClient.isActive() || data.activeTeam() == team;
        for (int i = 0; i < 4; i++) {
            int bx = x + i * (bw + gap);
            if (i == 0 && deployed) {
                g.fill(bx, by, bx + bw, by + 16, 0x80606060);
                g.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.tlmformation.deployed").getString(), bx + bw / 2, by + 4,
                        0xFFAAAAAA);
            } else {
                drawButton(g, labels[i], bx, by, bw, 16,
                        inside(mouseX, mouseY, bx, by, bw, 16), false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        UUID pendingAtClick = pendingRemove;
        pendingRemove = null;
        int pendingDeleteAtClick = pendingDeleteTeam;
        pendingDeleteTeam = -1;
        S2CTeamData data = TeamClientData.current();
        if (data == null) return super.mouseClicked(mx, my, button);

        int btnY = py + 158;
        if (inside(mx, my, px + RIGHT_X, btnY, RIGHT_W, 16)) {
            NetworkHandler.sendTeamAction(TeamAction.TACTICAL_ASSEMBLE, 0, null);
            return true;
        }
        if (inside(mx, my, px + RIGHT_X, btnY + 18, RIGHT_W, 16)) {
            NetworkHandler.sendTeamAction(TeamAction.STRATEGIC_DEFEND, 0, null);
            return true;
        }

        int rx = px + RIGHT_X;
        int rr = rx + RIGHT_W;
        if (inside(mx, my, rr - 70, py + H - 28, 70, 16)) {
            minecraft.setScreen(new LogicTerminalScreen());
            return true;
        }
        if (inside(mx, my, rx, py + H - 48, RIGHT_W, 16)) {
            minecraft.setScreen(new HudEditScreen());
            return true;
        }
        int top = py + 30;
        int split = py + 150;

        int ty = top + 18 - teamScroll;
        for (int i = 0; i < data.teams().size(); i++) {
            if (inside(mx, my, rx, ty, RIGHT_W, 18)) {
                if (inside(mx, my, rr - 17, ty + 2, 14, 14) && selectedMaid != null) {
                    NetworkHandler.sendTeamAction(TeamAction.ADD, i, selectedMaid);
                    selectedMaid = null;
                } else {
                    expandedTeam = expandedTeam == i ? -1 : i;
                    selectedDisplay = null;
                }
                return true;
            }
            ty += 20;
        }

        int myy = py + 216 - maidScroll;
        for (S2CTeamData.Member m : data.allMaids()) {
            if (isInAnyTeam(data, m.uuid())) continue;
            if (inside(mx, my, rx, myy, RIGHT_W, ROW_H)) {
                selectedMaid = m.uuid();
                selectedDisplay = null;
                return true;
            }
            myy += ROW_H;
        }

        // Cell action buttons have absolute priority over the cell hitbox.
        if (selectedDisplay != null) {
            Rect r = frames.get(selectedDisplay);
            if (r != null) {
                int w = 28, gap = 2;
                int start = r.x() + (r.w() - (w * 3 + gap * 2)) / 2;
                int y = r.y() + r.h() - 17;
                if (inside(mx, my, start, y, w, 14)) {
                    NetworkHandler.sendTeamAction(TeamAction.OPEN_BACKPACK,
                            expandedTeam, selectedDisplay);
                    return true;
                }
                if (inside(mx, my, start + w + gap, y, w, 14)) {
                    NetworkHandler.sendTeamAction(TeamAction.TELEPORT_MAID,
                            expandedTeam, selectedDisplay);
                    return true;
                }
                if (inside(mx, my, start + (w + gap) * 2, y, w, 14)) {
                    Entity cellEntity = entityOf(data, selectedDisplay);
                    if (expandedTeam != data.activeTeam()
                            || cellEntity == null || !cellEntity.isAlive()) {
                        return true;
                    }
                    if (pendingAtClick != null
                            && pendingAtClick.equals(selectedDisplay)) {
                        NetworkHandler.sendTeamAction(TeamAction.REMOVE,
                                expandedTeam, selectedDisplay);
                        selectedDisplay = null;
                        pendingRemove = null;
                    } else {
                        pendingRemove = selectedDisplay;
                    }
                    return true;
                }
            }
        }

        UUID rectHit = frameAt(mx, my);
        if (rectHit != null) {
            selectedDisplay = rectHit.equals(selectedDisplay) ? null : rectHit;
            return true;
        }

        if (selectedDisplay != null) {
            int y = py + H - 64;
            int w = 46, gap = 8;
            int start = px + STAGE_X + STAGE_W / 2 - (w * 3 + gap * 2) / 2;
            if (inside(mx, my, start, y, w, 16)) {
                NetworkHandler.sendTeamAction(TeamAction.OPEN_BACKPACK, expandedTeam, selectedDisplay);
                return true;
            }
            if (inside(mx, my, start + w + gap, y, w, 16)) {
                NetworkHandler.sendTeamAction(TeamAction.TELEPORT_MAID, expandedTeam, selectedDisplay);
                return true;
            }
            if (inside(mx, my, start + (w + gap) * 2, y, w, 16)) {
                if (selectedDisplay.equals(pendingAtClick)) {
                    NetworkHandler.sendTeamAction(TeamAction.REMOVE, expandedTeam, selectedDisplay);
                    selectedDisplay = null;
                } else {
                    pendingRemove = selectedDisplay;
                }
                return true;
            }
        }

        int bx = px + STAGE_X;
        int right = bx + STAGE_W;
        int team = expandedTeam >= 0 ? expandedTeam : data.activeTeam();
        if (team < 0) team = 0;
        int by = py + H - 28;
        int gap = 4;
        int bw = (right - bx - gap * 3) / 4;
        boolean deployed = GrandModeClient.isActive() || data.activeTeam() == team;
        for (int i = 0; i < 4; i++) {
            int bxx = bx + i * (bw + gap);
            if (!inside(mx, my, bxx, by, bw, 16)) continue;
            if (i == 0 && deployed) {
                return true;
            }
            switch (i) {
                case 0 -> NetworkHandler.sendTeamAction(TeamAction.SUMMON, team, null);
                case 1 -> NetworkHandler.sendTeamAction(TeamAction.RETURN_ACTIVE_TEAM, team, null);
                case 2 -> NetworkHandler.sendTeamAction(TeamAction.TELEPORT_RALLY, team, null);
                case 3 -> NetworkHandler.sendTeamAction(TeamAction.SET_RALLY, team, null);
            }
            return true;
        }
        selectedDisplay = null;
        return super.mouseClicked(mx, my, button);
    }

    @Nullable
    private UUID frameAt(double mx, double my) {
        for (Map.Entry<UUID, Rect> e : frames.entrySet()) {
            if (e.getValue().contains(mx, my)) return e.getKey();
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        S2CTeamData data = TeamClientData.current();
        if (data == null || mx < px + RIGHT_X) return false;
        int top = py + 30, split = py + 150;
        if (my < split) {
            int max = Math.max(0, data.teams().size() * 20 - (split - top - 24));
            teamScroll = Math.max(0, Math.min(max, teamScroll - (int) (delta * 20)));
        } else {
            int rows = 0;
            for (S2CTeamData.Member m : data.allMaids()) if (!isInAnyTeam(data, m.uuid())) rows++;
            int max = Math.max(0, rows * ROW_H - (H - 40 - 216));
            maidScroll = Math.max(0, Math.min(max, maidScroll - (int) (delta * 13)));
        }
        return true;
    }

    private void renderProfessionIcon(GuiGraphics g,
            com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid,
            int x, int y) {
        String p = professionOf(maid);
        if (p.equals("近战")) g.renderItem(new ItemStack(Items.DIAMOND_SWORD), x, y);
        else if (p.equals("射手")) g.renderItem(new ItemStack(Items.BOW), x, y);
        else if (p.equals("后勤")) g.blit(new ResourceLocation(TLMFormationMod.MOD_ID,
                "textures/gui/task_logistics.png"), x, y, 0, 0, 16, 16, 16, 16);
        else if (p.equals("待定")) g.renderItem(new ItemStack(Items.BREAD), x, y);
        else if (p.equals("枪手")) g.blit(new ResourceLocation(TLMFormationMod.MOD_ID,
                "textures/gui/gun_icon.png"), x, y, 0, 0, 16, 16, 16, 16);
        else g.blit(new ResourceLocation(TLMFormationMod.MOD_ID,
                "textures/gui/magic_icon.png"), x, y, 0, 0, 16, 16, 16, 16);
    }

    private static String professionOf(
            com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        if (maid.getTask() instanceof com.tlmformation.tactical.TacticalLogisticsTask) {
            return "后勤";
        }
        var task = maid.getTask();
        String cls = task.getClass().getSimpleName().toLowerCase();
        String uid = task.getUid().toString().toLowerCase();
        if (cls.contains("spellcombat") || cls.contains("spellcast") || uid.contains("spell")) return "法师";
        if (cls.contains("gun") || cls.contains("tacz") || uid.contains("gun") || uid.contains("tacz")) return "枪手";
        if (cls.contains("archer") || cls.contains("bow") || cls.contains("ranged")
                || uid.contains("archer") || uid.contains("bow")) return "射手";
        if (cls.contains("maidmelee") || cls.contains("meleeattack")
                || cls.contains("attacktask") || uid.contains("melee") || uid.contains("attack")) return "近战";
        return "待定";
    }

    private void drawMarqueeName(GuiGraphics g, String name,
                                 int plateX, int plateY, int plateW) {
        int maxWidth = plateW - 6;
        int textWidth = font.width(name);
        int x;

        if (textWidth <= maxWidth) {
            x = plateX + (plateW - textWidth) / 2;
        } else {
            // Smooth billboard scroll for overlong names.
            int cycle = textWidth + 24;
            int offset = (int) ((System.currentTimeMillis() / 35L) % cycle);
            x = plateX + 3 - offset;
        }

        g.enableScissor(plateX + 2, plateY + 2,
                plateX + plateW - 2, plateY + 15);
        g.drawString(font, name, x, plateY + 7, 0xFFFFFFFF, false);
        g.disableScissor();
    }

    private void drawProfession(GuiGraphics g, com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid,
                                int plateX, int plateW, int modelX, int modelY, int stageX, int stageW) {
        var task = maid.getTask();
        String cls = task.getClass().getSimpleName().toLowerCase();
        String uid = task.getUid().toString().toLowerCase();
        String p;
        if (cls.contains("spellcombat") || cls.contains("spellcast") || uid.contains("spell")) p = "法师";
        else if (cls.contains("gun") || cls.contains("tacz") || uid.contains("gun") || uid.contains("tacz")) p = "枪手";
        else if (cls.contains("archer") || cls.contains("bow") || cls.contains("ranged")
                || uid.contains("archer") || uid.contains("bow")) p = "射手";
        else if (cls.contains("maidmelee") || cls.contains("meleeattack")
                || cls.contains("attacktask") || uid.contains("melee") || uid.contains("attack")) p = "近战";
        else p = "后勤";

        int out = 8;
        int labelX = plateX + plateW + out; // fixed right edge + 8px
        if (labelX + 20 > stageX + stageW - 4) {
            labelX = plateX - out - 20; // only flip at the stage edge
        }
        int labelY = modelY + 10; // plate top edge

        int iconX = labelX;
        int iconY = labelY;
        if (p.equals("近战")) g.renderItem(new ItemStack(Items.DIAMOND_SWORD), iconX, iconY);
        else if (p.equals("射手")) g.renderItem(new ItemStack(Items.BOW), iconX, iconY);
        else if (p.equals("后勤")) g.renderItem(new ItemStack(Items.BREAD), iconX, iconY);
        else if (p.equals("枪手")) g.blit(new ResourceLocation(TLMFormationMod.MOD_ID,
                "textures/gui/gun_icon.png"), iconX, iconY, 0, 0, 16, 16, 16, 16);
        else g.blit(new ResourceLocation(TLMFormationMod.MOD_ID,
                "textures/gui/magic_icon.png"), iconX, iconY, 0, 0, 16, 16, 16, 16);

        // Icon on top, profession characters directly beneath it.
        for (int i = 0; i < p.length(); i++) {
            g.drawString(font, String.valueOf(p.charAt(i)),
                    labelX + 3, labelY + 18 + i * 10, 0xFFFFFFFF, true);
        }
    }

    private static void drawInfoBorder(GuiGraphics g, int x, int y, int w, int h,
                                       int color, int thickness) {
        g.fill(x, y, x + w, y + thickness, color);
        g.fill(x, y + h - thickness, x + w, y + h, color);
        g.fill(x, y, x + thickness, y + h, color);
        g.fill(x + w - thickness, y, x + w, y + h, color);
    }

    private void drawButton(GuiGraphics g, String label, int x, int y, int w, int h,
                            boolean hover, boolean red) {
        int c = red ? (hover ? 0xFFB04040 : 0xFF8A3030)
                : (hover ? 0xFF4A5A6A : 0xFF303A44);
        g.fill(x, y, x + w, y + h, c);
        g.drawCenteredString(font, label, x + w / 2, y + 4, 0xFFFFFFFF);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean isInAnyTeam(S2CTeamData data, UUID id) {
        return data.teams().stream().anyMatch(t -> t.members().contains(id));
    }

    @Nullable
    private static S2CTeamData.Member memberOf(S2CTeamData data, UUID id) {
        return data.allMaids().stream().filter(m -> m.uuid().equals(id)).findFirst().orElse(null);
    }

    /**
     * F9 面板显示名：正常情况下用同步来的女仆名（服务端会从备份里取显示名，
     * 所以区块未加载的成员也能显示真名）；万一名字为空，显示“未加载”而不是问号。
     */
    private static String displayName(@Nullable S2CTeamData.Member member) {
        if (member == null) {
            return Component.translatable("gui.tlmformation.member.unloaded").getString();
        }
        String name = member.name();
        if (name == null || name.isBlank()) {
            return Component.translatable("gui.tlmformation.member.unloaded").getString();
        }
        return name;
    }

    @Nullable
    private Entity entityOf(S2CTeamData data, UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        int entityId = data.allMaids().stream().filter(m -> m.uuid().equals(id))
                .mapToInt(S2CTeamData.Member::entityId).findFirst().orElse(-1);
        Entity live = entityId < 0 ? null : mc.level.getEntity(entityId);
        if (live != null) return live;
        S2CTeamData.Member member = memberOf(data, id);
        CompoundTag backup = member == null ? null : member.backup();
        if (backup == null) return null;
        Entity cached = fakeModels.get(id);
        if (cached != null) return cached;
        Entity fake = EntityType.loadEntityRecursive(backup, mc.level, entity -> entity);
        if (fake != null) fakeModels.put(id, fake);
        return fake;
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}
