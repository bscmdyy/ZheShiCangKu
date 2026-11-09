package com.tlmformation.vehicle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 运行时读取黑鹰模型骨骼 pivot。
 *
 * <p>数据源是模组自己打包的 {@code assets/tlmformation/geo/helicopter.geo.json}，
 * 所以以后换模型/挪骨骼，座位、索降绳索锚点会自动跟着模型走，不需要重算任何常数。</p>
 *
 * <p>读取失败时回退到内置兜底表（与当前模型数值一致）。</p>
 */
public final class HelicopterBoneData {
    private static final String RESOURCE = "/assets/tlmformation/geo/helicopter.geo.json";

    private static final Map<String, double[]> FALLBACK = new HashMap<>();
    private static volatile Map<String, double[]> pivots;

    static {
        FALLBACK.put("zuo1", new double[]{11.0D, 11.16274D, -22.65716D});
        FALLBACK.put("zuo2", new double[]{-12.0D, 11.66274D, 8.34284D});
        FALLBACK.put("zuo3", new double[]{11.0D, 11.66274D, 8.34284D});
        FALLBACK.put("zuo4", new double[]{11.0D, 10.66274D, 49.34284D});
        FALLBACK.put("zuo5", new double[]{0.0D, 10.66274D, 49.34284D});
        FALLBACK.put("zuo6", new double[]{0.0D, 11.66274D, 8.34284D});
        FALLBACK.put("zuo7", new double[]{-10.0D, 11.16274D, -22.65716D});
        FALLBACK.put("zuo8", new double[]{-9.0D, 10.66274D, 49.34284D});
        FALLBACK.put("zuo9", new double[]{12.0D, 10.0D, -5.0D});
        FALLBACK.put("zuo10", new double[]{-12.0D, 10.0D, -5.0D});
        FALLBACK.put("gun_az", new double[]{0.0D, 11.4D, -29.0D});
        FALLBACK.put("gun_el", new double[]{0.0D, 8.4D, -29.0D});
        FALLBACK.put("barrel", new double[]{0.0D, 8.4D, -29.0D});
        FALLBACK.put("117", new double[]{-34.5D, 47.08278D, 15.00782D});
        FALLBACK.put("118", new double[]{35.5D, 47.08278D, 13.00782D});
    }

    private HelicopterBoneData() {
    }

    /** 取骨骼 pivot（模型像素）；没有该骨骼返回 null。 */
    @Nullable
    public static double[] pivot(String bone) {
        Map<String, double[]> map = pivots;
        if (map == null) {
            synchronized (HelicopterBoneData.class) {
                if (pivots == null) {
                    pivots = load();
                }
                map = pivots;
            }
        }
        double[] value = map.get(bone);
        if (value != null) {
            return value;
        }
        return FALLBACK.get(bone);
    }

    private static Map<String, double[]> load() {
        Map<String, double[]> map = new HashMap<>();
        try (InputStream in = HelicopterBoneData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return map;
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray geometry = root.getAsJsonArray("minecraft:geometry");
            if (geometry == null || geometry.size() == 0) {
                return map;
            }
            JsonArray bones = geometry.get(0).getAsJsonObject().getAsJsonArray("bones");
            if (bones == null) {
                return map;
            }
            for (JsonElement element : bones) {
                JsonObject bone = element.getAsJsonObject();
                if (bone == null || !bone.has("name") || !bone.has("pivot")) {
                    continue;
                }
                String name = bone.get("name").getAsString();
                if (!isInteresting(name)) {
                    continue;
                }
                JsonArray pivot = bone.getAsJsonArray("pivot");
                if (pivot == null || pivot.size() < 3) {
                    continue;
                }
                map.put(name, new double[]{
                        pivot.get(0).getAsDouble(),
                        pivot.get(1).getAsDouble(),
                        pivot.get(2).getAsDouble()
                });
            }
        } catch (Throwable ignored) {
            // 读不到就用 FALLBACK，不影响游戏
        }
        return map;
    }

    private static boolean isInteresting(String name) {
        return name.startsWith("zuo")
                || "117".equals(name) || "118".equals(name)
                || "gun_az".equals(name) || "gun_el".equals(name) || "barrel".equals(name);
    }
}
