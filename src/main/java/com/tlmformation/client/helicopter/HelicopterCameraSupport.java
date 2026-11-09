package com.tlmformation.client.helicopter;

import net.minecraft.client.Minecraft;

/** 客户端相机状态查询：只会在 Dist.CLIENT 下被加载，公共代码通过 DistExecutor 引用。 */
public final class HelicopterCameraSupport {
    private HelicopterCameraSupport() {
    }

    /** 当前是否处于第三人称（越肩瞄准相机只在第三人称生效）。 */
    public static boolean isThirdPerson() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options != null && !minecraft.options.getCameraType().isFirstPerson();
    }
}
