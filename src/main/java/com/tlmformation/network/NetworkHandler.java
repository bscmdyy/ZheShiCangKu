package com.tlmformation.network;

import com.tlmformation.TLMFormationMod;
import com.tlmformation.tactical.TacticalCommand;
import com.tlmformation.team.TeamAction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

public final class NetworkHandler {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TLMFormationMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private NetworkHandler() {
    }

    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(id++, C2SAction.class,
                C2SAction::encode, C2SAction::decode, C2SAction::handle);
        CHANNEL.registerMessage(id++, C2STacticalCommand.class,
                C2STacticalCommand::encode, C2STacticalCommand::decode,
                C2STacticalCommand::handle);
        CHANNEL.registerMessage(id++, S2CTacticalState.class,
                S2CTacticalState::encode, S2CTacticalState::decode,
                S2CTacticalState::handle);
        CHANNEL.registerMessage(id++, C2STeamAction.class,
                C2STeamAction::encode, C2STeamAction::decode,
                C2STeamAction::handle);
        CHANNEL.registerMessage(id++, S2CTeamData.class,
                S2CTeamData::encode, S2CTeamData::decode,
                S2CTeamData::handle);
        CHANNEL.registerMessage(id++, S2CLogicData.class,
                S2CLogicData::encode, S2CLogicData::decode,
                S2CLogicData::handle);
        CHANNEL.registerMessage(id++, S2CGrandMode.class,
                S2CGrandMode::encode, S2CGrandMode::decode,
                S2CGrandMode::handle);
        CHANNEL.registerMessage(id++, C2SArmorAction.class,
                C2SArmorAction::encode, C2SArmorAction::decode,
                C2SArmorAction::handle);
        CHANNEL.registerMessage(id++, S2CArmorState.class,
                S2CArmorState::encode, S2CArmorState::decode,
                S2CArmorState::handle);
        CHANNEL.registerMessage(id++, C2SArmorConfig.class,
                C2SArmorConfig::encode, C2SArmorConfig::decode,
                C2SArmorConfig::handle);
        CHANNEL.registerMessage(id++, C2SVehicleCommand.class,
                C2SVehicleCommand::encode, C2SVehicleCommand::decode,
                C2SVehicleCommand::handle);
        CHANNEL.registerMessage(id++, S2CHavocEffect.class,
                S2CHavocEffect::encode, S2CHavocEffect::decode,
                S2CHavocEffect::handle);
        CHANNEL.registerMessage(id++, C2SRappelAction.class,
                C2SRappelAction::encode, C2SRappelAction::decode,
                C2SRappelAction::handle);
        CHANNEL.registerMessage(id++, S2CRappelState.class,
                S2CRappelState::encode, S2CRappelState::decode,
                S2CRappelState::handle);
    }

    /** C2S：MQ-9 远程操控输入（每 tick 最多一包）。 */

    /** S2C：MQ-9 实体 id / 阶段 / 剩余弹药（F10 弹药槽 + 镜头接管共用）。 */

    /** C2S：F10 左侧 MQ-9 弹药槽取/放。 */

    /** C2S：索降指令（见 {@link C2SRappelAction}）。 */
    public static void sendRappelAction(int action) {
        CHANNEL.sendToServer(new C2SRappelAction(action));
    }

    /** S2C：把当前索降阶段单独发给某个玩家（中途加入追踪范围时补发）。 */
    public static void sendRappelState(ServerPlayer player,
                                       com.tlmformation.vehicle.EntityHelicopter heli) {
        net.minecraft.world.phys.Vec3 lock = heli.getSyncedLockPos();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CRappelState(heli.getId(), heli.getRappelPhase(), heli.getRappelRemain(),
                        lock.x, lock.y, lock.z));
    }


    public static void sendAction(int action) {
        CHANNEL.sendToServer(new C2SAction(action));
    }

    public static void openFormationMenu() {
        CHANNEL.sendToServer(new C2SAction(8));
    }

    public static void sendTacticalCommand(TacticalCommand command, @Nullable BlockPos target) {
        CHANNEL.sendToServer(new C2STacticalCommand(command, target));
    }

    public static void sendTacticalState(ServerPlayer player, Map<Integer, Integer> states,
                                         @Nullable BlockPos marker,
                                         int lockedTargetEntityId, int commandOrdinal) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CTacticalState(states, marker, lockedTargetEntityId, commandOrdinal));
    }

    public static void sendTeamAction(TeamAction action, int team, @Nullable UUID maid) {
        CHANNEL.sendToServer(new C2STeamAction(action, team, maid));
    }

    public static void sendTeamData(ServerPlayer player, S2CTeamData data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), data);
    }

    public static void sendArmorAction(int slot) {
        CHANNEL.sendToServer(new C2SArmorAction(slot));
    }

    /** F10 单位指令：1=设置基地，2=前往基地，3=撤回单位。 */
    public static void sendUnitAction(int slot, int action) {
        CHANNEL.sendToServer(new C2SArmorAction(100 + action * 10 + slot));
    }

    public static void sendArmorState(ServerPlayer player, int deployedMask, int followDistance) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CArmorState(deployedMask, followDistance,
                        com.tlmformation.vehicle.TankData.isUnlocked(player),
                        com.tlmformation.vehicle.TankData.stock(player)));
    }

    public static void sendArmorConfig(int followDistance) {
        CHANNEL.sendToServer(new C2SArmorConfig(followDistance));
    }

    public static void sendVehicleCommand(int action) {
        CHANNEL.sendToServer(new C2SVehicleCommand(action));
    }

    /** 广播战车自定义特效：0=曳光, 1=枪口闪光, 2=爆炸。 */
    public static void sendHavocEffect(com.tlmformation.vehicle.EntityHavoc vehicle,
                                       int type,
                                       net.minecraft.world.phys.Vec3 a,
                                       net.minecraft.world.phys.Vec3 b) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> vehicle),
                new S2CHavocEffect(type, a.x, a.y, a.z, b.x, b.y, b.z));
    }

}
