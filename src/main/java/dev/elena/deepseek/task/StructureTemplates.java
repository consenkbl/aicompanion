package dev.elena.deepseek.task;

import com.google.gson.Gson;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 建筑模板注册表：
 * 1) 内置模板打包在 jar 的 data/deepseek/structures/ 下；
 * 2) 玩家自定义模板：把 json 丢进 config/deepseek/structures/ 即可热加载（无需重启）。
 * knownIds() 供 AI 提示词自动列出可建造的建筑。
 */
public class StructureTemplates {
    private static final Gson GSON = new Gson();
    private static final Map<String, StructureTemplate> CACHE = new HashMap<>();
    private static final List<String> BUILT_IN = List.of("cabin", "watchtower", "camp");
    

    @Nullable
    public static StructureTemplate get(String id) {
        StructureTemplate t = CACHE.get(id);
        if (t != null) return t;
        t = loadFromJar(id);
        if (t == null) t = loadFromConfig(id);
        if (t != null) CACHE.put(id, t);
        return t;
    }

    /** 所有可建造的建筑 id（内置 + 便携配置目录下的自定义模板）。 */
    public static List<String> knownIds() {
        List<String> ids = new ArrayList<>(BUILT_IN);
        Path dir = dev.elena.deepseek.DeepSeekPaths.resolve("structures");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> list = Files.list(dir)) {
                list.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .map(p -> p.getFileName().toString().replace(".json", ""))
                        .filter(name -> !ids.contains(name))
                        .forEach(ids::add);
            } catch (Exception ignored) {
            }
        }
        return ids;
    }

    @Nullable
    private static StructureTemplate loadFromJar(String id) {
        try (InputStream in = StructureTemplates.class.getResourceAsStream(
                "/data/deepseek/structures/" + id + ".json")) {
            if (in == null) return null;
            return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), StructureTemplate.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 图纸库匹配：返回按匹配度排序的候选 id 列表（最多 n 个）。 */
    public static List<String> findCandidates(String want, @Nullable String excludeId, int n, int minScore) {
        if (want == null || want.isBlank()) return List.of();
        String w = want.toLowerCase().trim();
        List<String> candIds = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (String id : knownIds()) {
            if (id.equals(excludeId)) continue;
            StructureTemplate t = get(id);
            if (t == null) continue;
            int score = 0;
            String tn = t.name == null ? "" : t.name.toLowerCase();
            if (tn.equals(w)) score = Math.max(score, 100);
            else if (!tn.isEmpty() && (tn.contains(w) || w.contains(tn))) score = Math.max(score, 60);
            if (t.tags != null) {
                for (String tag : t.tags) {
                    String tt = tag.toLowerCase().trim();
                    if (tt.isEmpty()) continue;
                    if (tt.equals(w)) score = Math.max(score, 90);
                    else if (w.contains(tt) || tt.contains(w)) score = Math.max(score, 40);
                }
            }
            if (score >= minScore) {
                candIds.add(id);
                scores.add(score);
            }
        }
        // 按分数降序排序
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < candIds.size(); i++) order.add(i);
        order.sort((a, b) -> scores.get(b) - scores.get(a));
        List<String> result = new ArrayList<>();
        for (int i : order) {
            result.add(candIds.get(i));
            if (result.size() >= n) break;
        }
        return result;
    }

    /** 给 AI 提示词用的图纸库概况（名称列表，超过 60 个只展示前 60）。 */
    public static String librarySummary() {
        java.util.List<String> ids = knownIds();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String id : ids) {
            StructureTemplate t = get(id);
            if (t != null && t.name != null) names.add(t.name);
        }
        if (names.size() <= 60) return names.size() + " 张：" + String.join("、", names);
        return names.size() + " 张，例如：" + String.join("、", names.subList(0, 60)) + " …";
    }

    @Nullable
    private static StructureTemplate loadFromConfig(String id) {
        try {
            Path p = dev.elena.deepseek.DeepSeekPaths.resolve("structures/" + id + ".json");
            if (!Files.isRegularFile(p)) return null;
            return GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), StructureTemplate.class);
        } catch (Exception e) {
            return null;
        }
    }
}
