package com.tlmformation.sound;

import com.tlmformation.TLMFormationMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 战车专属音效（6 个实际文件）：
 * drive_loop / idle_loop / turret_rotate_loop / turret_stop / hit_metal_3 / autocannon_single。
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TLMFormationMod.MOD_ID);

    public static final RegistryObject<SoundEvent> DRIVE_LOOP = register("havoc.drive");
    public static final RegistryObject<SoundEvent> IDLE_LOOP = register("havoc.idle");
    public static final RegistryObject<SoundEvent> TURRET_ROTATE = register("havoc.turret_rotate");
    public static final RegistryObject<SoundEvent> TURRET_STOP = register("havoc.turret_stop");
    public static final RegistryObject<SoundEvent> HIT_METAL = register("havoc.hit_metal");
    public static final RegistryObject<SoundEvent> AUTOCANNON = register("havoc.autocannon_single");
    /** F9「部署 / 小队撤离」点击时的对讲机咔哒声。 */
    public static final RegistryObject<SoundEvent> RADIO_CLICK = register("radio.click");
    /** 对讲机“嘀”声（点击后 0.15 秒补一声，模拟按下 PTT）。 */
    public static final RegistryObject<SoundEvent> RADIO_BEEP = register("radio.beep");
    /** 直升机旋翼/引擎循环（本模组自有素材，AI 空投与玩家驾驶通用）。 */
    public static final RegistryObject<SoundEvent> HELI_ENGINE = register("heli.engine");
    /** 直升机引擎启动。 */
    public static final RegistryObject<SoundEvent> HELI_ENGINE_START = register("heli.engine_start");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () ->
                SoundEvent.createVariableRangeEvent(new ResourceLocation(TLMFormationMod.MOD_ID, name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
