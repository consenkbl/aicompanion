package dev.elena.deepseek.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elena.deepseek.DeepSeekGson;
import dev.elena.deepseek.entity.CompanionEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 对话服务：主线程收集上下文 → 异步调 LLM → 回主线程执行动作。
 * 没配 key 时走 OfflineBrains 关键词模式。
 */
public class ChatService {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private static final Map<UUID, List<JsonObject>> HISTORY = new HashMap<>();

    public static void submit(CompanionEntity entity, ServerPlayer player, String message) {
        entity.setAiBusy(true);
        String context = entity.buildAIContext(player);
        MinecraftServer server = entity.getServer();
        CompletableFuture.supplyAsync(() -> ask(entity, player, message, context))
                .whenComplete((reply, err) -> {
                    if (server != null) {
                        server.execute(() -> entity.handleAIReply(player, err == null ? reply : null));
                    }
                });
    }

    private static AIReply ask(CompanionEntity entity, ServerPlayer player, String message, String context) {
        String key = Config.apiKey();
        if (key == null || key.isBlank()) return OfflineBrains.reply(entity, message);

        try {
            List<JsonObject> history = HISTORY.computeIfAbsent(player.getUUID(),
                    k -> Collections.synchronizedList(new ArrayList<>()));
            JsonObject body = new JsonObject();
            body.addProperty("model", Config.model());
            body.addProperty("temperature", 0.8);
            body.addProperty("max_tokens", 500);
            JsonArray msgs = new JsonArray();
            msgs.add(msg("system", buildSystemPrompt(entity, player, context)));
            synchronized (history) {
                history.forEach(msgs::add);
            }
            msgs.add(msg("user", message));
            body.add("messages", msgs);

            HttpRequest req = HttpRequest.newBuilder(URI.create(Config.apiUrl()))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(DeepSeekGson.GSON.toJson(body)))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return AIReply.text("（那边好像出问题了，错误码 " + resp.statusCode() + "）晚点再聊吧…");
            }
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            String content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();

            synchronized (history) {
                history.add(msg("user", message));
                history.add(msg("assistant", content));
                int max = Config.maxHistory() * 2;
                while (history.size() > max) history.remove(0);
            }
            return AIReply.parse(content);
        } catch (Exception e) {
            return AIReply.text("（信号不太好…）你刚刚说什么？再说一遍？");
        }
    }

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    private static String buildSystemPrompt(CompanionEntity entity, ServerPlayer player, String context) {
        // 人设来自 config/deepseek/persona.md（可编辑，自动重读）；
        // 动作清单来自 ActionRegistry 注册表（新功能注册即自动暴露给 AI）；
        // 整合包资料自动读取当前世界加载的模组，手写 md 仅作补充。
        return PersonaDoc.get() + "\n\n"
                + """
                【规则】
                1. 只输出一个 JSON 对象：{"say":"你要说的话","actions":[...]}，不要输出 JSON 以外的内容。
                2. say 用简短口语（50字以内），严格遵守你的人设。
                3. 玩家让你做事时，在 actions 里给出动作，系统会替你执行：
                """ + ActionRegistry.promptLines() + """
                4. 日常闲聊、提问就留空 actions。
                5. 回答整合包的问题时，以下面的资料为准；资料里没有的就老实说不知道，别编。

                【当前状态与资料】
                """ + context + "\n\n【整合包资料】\n" + KnowledgeBase.digest();
    }
}
