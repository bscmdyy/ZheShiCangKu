package com.tlmformation.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 「头顶能不能看见天」检测（空投 / 撤离 / 呼救器的通信条件）。
 *
 * <p>规则：从实体头顶上方 2 格起，沿垂直列向上检查 {@link #CHECK_HEIGHT} 格；
 * 只要遇到<b>不透光的实心方块</b>（{@link BlockState#isSolidRender}）就判定为
 * “在地下 / 洞穴 / 室内”，直升机无法建立通信与索降空域。</p>
 *
 * <p>为什么用 {@code isSolidRender} 而不是 {@code !isAir()}：树叶、玻璃、水、雪层、
 * 草等“能透过去”的方块不会挡住直升机，用 {@code !isAir()} 会让森林和玻璃温室里
 * 完全无法呼叫支援，过于严格；而石头 / 泥土 / 木板等真正的天花板会挡住。</p>
 */
public final class SkyVisibility {
    /** 向上检查的格数（= 空投生成高度 {@link AirdropManager#SPAWN_HEIGHT}）。 */
    public static final int CHECK_HEIGHT = 40;

    private SkyVisibility() {
    }

    /** 玩家头顶是否有开阔天空（服务端）。 */
    public static boolean hasOpenSky(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return hasOpenSky(player.serverLevel(), player.blockPosition());
    }

    /** 指定坐标头顶是否有开阔天空。 */
    public static boolean hasOpenSky(Level level, BlockPos base) {
        if (level == null || base == null) {
            return false;
        }
        // 跳过脚部与头部两格本身，只检查“头顶以上”
        int startY = base.getY() + 2;
        int top = Math.min(level.getMaxBuildHeight() - 1, startY + CHECK_HEIGHT);
        for (int y = startY; y <= top; y++) {
            BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
            if (!level.isLoaded(pos)) {
                continue;   // 未加载的区块无法判定，按“没挡住”处理
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (isOpaqueCeiling(state, level, pos)) {
                return false;
            }
        }
        return true;
    }

    /** 是否是会挡住直升机的“天花板”。 */
    private static boolean isOpaqueCeiling(BlockState state, BlockGetter level, BlockPos pos) {
        try {
            return state.isSolidRender(level, pos);
        } catch (Throwable ignored) {
            // 个别模组方块在 isSolidRender 里可能抛异常：按“不挡”处理，避免误封
            return false;
        }
    }
}
