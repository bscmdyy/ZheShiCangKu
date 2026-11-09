package com.tlmformation.client;

/**
 * 视差 HUD 已停用。
 *
 * <p>旧实现会在 {@code RenderGuiOverlayEvent.Pre} 里给<b>所有</b>原版/模组 HUD 叠一个
 * 随视角变化平移的矩阵（为了"视差"效果），结果物品栏、小队状态栏等全都跟着载具/视角抖动，
 * 晕得没法玩。现在一律固定在屏幕坐标上：</p>
 * <ul>
 *   <li>不再注册任何 GUI 事件（不再平移任何 overlay 的 pose）</li>
 *   <li>{@link #x()} / {@link #y()} 恒为 0，调用方（小队状态栏）也不会有额外偏移</li>
 * </ul>
 */
public final class ParallaxHud {
    private ParallaxHud() {
    }

    /** 固定屏幕坐标：不再产生横向偏移。 */
    public static float x() {
        return 0.0F;
    }

    /** 固定屏幕坐标：不再产生纵向偏移。 */
    public static float y() {
        return 0.0F;
    }
}
