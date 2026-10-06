package dev.elena.deepseek.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elena.deepseek.DeepSeekGson;
import dev.elena.deepseek.entity.CompanionEntity;
import dev.elena.deepseek.task.StructureTemplates;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 图纸语义匹配：把图纸库清单（id | 名称 | 描述）交给大模型，
 * 让它理解玩家的自然语言（"带花园的别墅"、"像公寓一样的房子"）并挑选最合适的图纸。
 * 选不出来时返回空列表 → 由 AI 现场设计蓝图。
 */
public class MatchService {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();

    public interface MatchCallback {
        void run(@Nullable List<String> ids);
    }

    /** 异步匹配。回调在服务端主线程执行；ids 为空 = 库里没有合适的（交给 AI 设计）。 */
    public static void requestMatch(CompanionEntity mob, String want, String brief,
                                    @Nullable String excludeId, MatchCallback callback) {
        mob.setAiBusy(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                return match(want, brief, excludeId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((ids, err) -> {
            MinecraftServer server = mob.getServer();
            if (server == null) {
                mob.setAiBusy(false);
                return;
            }
            server.execute(() -> {
                mob.setAiBusy(false);
                callback.run(err == null ? ids : null);
            });
        });
    }

    private static List<String> match(String want, String brief, @Nullable String excludeId) throws Exception {
        StringBuilder lib = new StringBuilder();
        for (String id : StructureTemplates.knownIds()) {
            if (id.equals(excludeId)) continue;
            dev.elena.deepseek.task.StructureTemplate t = StructureTemplates.get(id);
            if (t == null) continue;
            lib.append(id).append(" | ").append(t.displayName())
               .append(" | ").append(t.desc == null ? "" : t.desc).append('\n');
        }

        String system = """
                你是建造图纸匹配助手。下面是图纸库清单（每行格式：id | 名称 | 描述）：
                """ + lib + """
                玩家想要一座建筑。请从清单里选出最匹配的图纸 id（最多 3 个，按匹配度排序）。
                id 必须从清单里【原样复制】，不要修改。
                只输出 JSON：{"ids":["id1","id2","id3"]}
                如果清单里没有任何合适的图纸，输出 {"ids":[]}
                """;
        String user = "玩家想要一座「" + want + "」。"
                + (brief == null || brief.isBlank() ? "" : "补充描述：" + brief);

        JsonObject body = new JsonObject();
        body.addProperty("model", Config.model());
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", 300);
        JsonArray msgs = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", system);
        JsonObject usr = new JsonObject();
        usr.addProperty("role", "user");
        usr.addProperty("content", user);
        msgs.add(sys);
        msgs.add(usr);
        body.add("messages", msgs);

        HttpRequest req = HttpRequest.newBuilder(URI.create(Config.apiUrl()))
                .header("Authorization", "Bearer " + Config.apiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(DeepSeekGson.GSON.toJson(body)))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + resp.statusCode());
        }
        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        String content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                .getAsJsonObject("message").get("content").getAsString();

        int s = content.indexOf('{');
        int e = content.lastIndexOf('}');
        if (s < 0 || e <= s) return List.of();
        JsonObject obj = JsonParser.parseString(content.substring(s, e + 1)).getAsJsonObject();
        List<String> ids = new ArrayList<>();
        if (obj.has("ids") && obj.get("ids").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("ids")) {
                String id = el.getAsString();
                if (StructureTemplates.get(id) != null && !ids.contains(id)) ids.add(id);
            }
        }
        return ids;
    }
}
