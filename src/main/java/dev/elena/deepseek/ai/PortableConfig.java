package dev.elena.deepseek.ai;

import com.electronwill.nightconfig.core.file.FileConfig;
import dev.elena.deepseek.DeepSeekPaths;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * 便携配置读取：mods 文件夹里 jar 旁边的 deepseek-config/deepseek-common.toml。
 * 该文件存在时优先级最高（覆盖实例 config），朋友填的 key/model 直接映射生效。
 * 文件修改后 5 秒内自动重读。
 */
public class PortableConfig {
    private static long cachedMtime = -1;
    private static String apiKey = null;
    private static String apiUrl = null;
    private static String model = null;
    private static Integer triggerRadius = null;
    private static Integer maxHistory = null;

    /** 便携配置存在且内容有效时返回覆盖值，否则返回 null（调用方回落到实例配置）。 */
    @Nullable
    public static String apiKey() {
        load();
        return blank(apiKey) ? null : apiKey;
    }

    @Nullable
    public static String apiUrl() {
        load();
        return blank(apiUrl) ? null : apiUrl;
    }

    @Nullable
    public static String model() {
        load();
        return blank(model) ? null : model;
    }

    @Nullable
    public static Integer triggerRadius() {
        load();
        return triggerRadius;
    }

    @Nullable
    public static Integer maxHistory() {
        load();
        return maxHistory;
    }

    private static boolean blank(@Nullable String s) {
        return s == null || s.isBlank();
    }

    private static void load() {
        Path dir = DeepSeekPaths.portableDir();
        if (dir == null) return;
        Path file = dir.resolve("deepseek-common.toml");
        if (!Files.isRegularFile(file)) return;
        long mtime;
        try {
            mtime = Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            return;
        }
        if (cachedMtime == mtime) return;   // 没改过，用缓存
        try (FileConfig fc = FileConfig.of(file)) {
            fc.load();
            apiKey = stringOrNull(fc, "ai.apiKey");
            apiUrl = stringOrNull(fc, "ai.apiUrl");
            model = stringOrNull(fc, "ai.model");
            Integer r = intOrNull(fc, "behavior.triggerRadius");
            triggerRadius = r;
            Integer h = intOrNull(fc, "behavior.maxHistory");
            maxHistory = h;
            cachedMtime = mtime;
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private static String stringOrNull(FileConfig fc, String key) {
        Object v = fc.get(key);
        return v == null ? null : String.valueOf(v);
    }

    @Nullable
    private static Integer intOrNull(FileConfig fc, String key) {
        Object v = fc.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
