package com.tlmformation.vehicle;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tlmformation.TLMFormationMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 女仆上直升机辅助。
 *
 * <p>TLM 自带的 {@code MaidFollowOwnerVehicleTask} 硬编码要求女仆与载具<b>实体原点</b>
 * 距离 &lt; 3 格；而本机 8 个座位在数据里的坐标距原点 3.26~5.29 格，机舱门也差不多这个距离，
 * 所以女仆大部分时候根本走不到判定圈里（偶尔被 TLM 的随机传送丢到原点附近才成功）——
 * 这就是"有时候上不去飞机"。</p>
 *
 * <p>这里补一条服务端登机路径：主人坐在驾驶位时，把 8 格内的同主人女仆直接送上空座位。
 * 只在女仆可骑乘、非家园模式、没有攻击目标、没有坐骑时执行；每次扫描只收一人。</p>
 */
@Mod.EventBusSubscriber(modid = TLMFormationMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HelicopterBoardingHandler {
    /** 上机辅助半径（格）。 */
    private static final double BOARD_RADIUS = 8.0D;
    /** 扫描间隔（tick）。 */
    private static final int SCAN_INTERVAL = 10;

    private static int tickCounter;

    private HelicopterBoardingHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }
        if (++tickCounter % SCAN_INTERVAL != 0) {
            return;
        }
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }
        if (!ModList.get().isLoaded("superbwarfare")) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!(player.getVehicle() instanceof EntityHelicopter helicopter)) {
                continue;
            }
            if (helicopter.getSeatIndex(player) != 0) {
                continue;   // 只有驾驶位玩家负责收拢女仆
            }
            if (helicopter.isRappelling()) {
                continue;
            }
            if (helicopter.getPassengers().size() >= helicopter.getMaxPassengers()) {
                continue;
            }
            List<EntityMaid> maids = level.getEntitiesOfClass(EntityMaid.class,
                    helicopter.getBoundingBox().inflate(BOARD_RADIUS),
                    maid -> maid.isAlive()
                            && !maid.isRemoved()
                            && !maid.isPassenger()
                            && maid.isRideable()
                            && !maid.isHomeModeEnable()
                            && maid.getTarget() == null);
            for (EntityMaid maid : maids) {
                LivingEntity owner = maid.getOwner();
                if (owner == null || owner.getVehicle() != helicopter) {
                    continue;   // 只在主人已在本机里时接人
                }
                if (maid.startRiding(helicopter)) {
                    break;      // 每次扫描只收一个，避免一拥而上
                }
            }
        }
    }
}
