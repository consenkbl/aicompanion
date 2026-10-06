package dev.elena.deepseek.task;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elena.deepseek.DeepSeekGson;
import dev.elena.deepseek.ai.Config;
import dev.elena.deepseek.entity.CompanionEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 建筑师：让大模型自己设计建筑蓝图（palette + 分层字符画），
 * 校验并保存为建筑模板，然后走正常的"索要材料→火把定位→逐块搭建"流程。
 * 玩家说"盖白宫"，AI 就真的能盖一座它自己设计的白宫。
 */
public class AiArchitect {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private static final int MAX_SIDE = 12;
    private static final int MAX_LAYERS = 12;
    private static final int MAX_BLOCKS = 500;

    /** 发起异步设计请求：AI 画图 → 校验保存 → 交给正常建造流程。 */
    public static void requestDesign(CompanionEntity mob, @Nullable ServerPlayer player,
                                     String name, String brief) {
        mob.setAiBusy(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                return design(name, brief);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((tpl, err) -> {
                    if (mob.getServer() == null) {
                        mob.setAiBusy(false);
                        return;
                    }
                    mob.getServer().execute(() -> {
                        mob.setAiBusy(false);
                        if (err != null || tpl == null) {
                            mob.chatSay("图纸没画出来…换个简单点的要求再试一次？（例如：一座白色两层宫殿，正面有柱子）");
                            return;
                        }
                        String slug = slugify(name);
                        saveTemplate(slug, tpl);
                        if (mob.isTaskBusy()) {
                            mob.chatSay("「" + tpl.displayName() + "」的图画好了！等我手头的活干完就开工。");
                            return;
                        }
                        mob.startBuild(slug, player);
                    });
                });
    }

    // ------------------------------------------------------------ 设计

    private static StructureTemplate design(String name, String brief) throws Exception {
        String system = """
                你是 Minecraft 建筑设计师。根据玩家的要求设计一座建筑，只输出严格的 JSON（不要任何解释文字）：
                {"name":"建筑名","palette":{"A":"minecraft:方块id","B":"minecraft:方块id"},"rows":[["第0层第0行","第0层第1行"],["第1层第0行",...],...]}
                格式规则：
                - rows 从下到上每层一个字符串数组，每层内所有字符串长度相同，字符必须在 palette 中定义，'.' 表示空气
                - 第 0 层是地板，必须铺满；墙体留 2 格高的门口；内部挖空；顶层封顶
                - 宽和深不超过 12 格，层数 5~12，总方块数不超过 500
                - palette 只用原版方块 id（minecraft: 前缀），推荐：quartz_block、white_concrete、smooth_stone、stone_bricks、chiseled_stone_bricks、oak_planks、spruce_planks、dark_oak_planks、oak_log、glass_pane、cobblestone_wall
                - 门窗用 '.' 留空，装饰用玻璃板和墙
                """;
        String user = "设计一座「" + name + "」。"
                + (brief == null || brief.isBlank() ? "" : "外观要求：" + brief);

        JsonObject body = new JsonObject();
        body.addProperty("model", Config.model());
        body.addProperty("temperature", 0.7);
        body.addProperty("max_tokens", 2500);
        com.google.gson.JsonArray msgs = new com.google.gson.JsonArray();
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
        return parse(content, name);
    }

    private static StructureTemplate parse(String content, String fallbackName) {
        int s = content.indexOf('{');
        int e = content.lastIndexOf('}');
        if (s < 0 || e <= s) throw new IllegalArgumentException("AI 没有输出 JSON");
        JsonObject root = JsonParser.parseString(content.substring(s, e + 1)).getAsJsonObject();

        String displayName = fallbackName;
        if (root.has("name") && !root.get("name").getAsString().isBlank()) {
            displayName = root.get("name").getAsString();
        }

        Map<String, Block> charMap = new LinkedHashMap<>();
        if (root.has("palette") && root.get("palette").isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : root.getAsJsonObject("palette").entrySet()) {
                String ch = en.getKey().trim();
                if (ch.length() != 1 || ch.equals(".")) continue;
                Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(en.getValue().getAsString()));
                if (b == null || b.defaultBlockState().isAir()) continue;
                charMap.put(ch, b);
            }
        }
        if (charMap.isEmpty()) throw new IllegalArgumentException("palette 无效");

        if (!root.has("rows") || !root.get("rows").isJsonArray()) throw new IllegalArgumentException("rows 无效");
        JsonArray layers = root.getAsJsonArray("rows");
        if (layers.size() < 3 || layers.size() > MAX_LAYERS) {
            throw new IllegalArgumentException("层数需在 3~" + MAX_LAYERS + " 层");
        }
        int sizeZ = 0, sizeX = 0;
        for (JsonElement layerEl : layers) {
            if (!layerEl.isJsonArray()) throw new IllegalArgumentException("层格式错误");
            JsonArray la = layerEl.getAsJsonArray();
            if (la.size() > MAX_SIDE) throw new IllegalArgumentException("太深了");
            sizeZ = Math.max(sizeZ, la.size());
            for (JsonElement rowEl : la) {
                if (!rowEl.isJsonPrimitive()) throw new IllegalArgumentException("行格式错误");
                sizeX = Math.max(sizeX, rowEl.getAsString().length());
            }
        }
        if (sizeX > MAX_SIDE) throw new IllegalArgumentException("太宽了");

        String[][] rows = new String[layers.size()][sizeZ];
        int total = 0;
        for (int y = 0; y < layers.size(); y++) {
            JsonArray la = layers.get(y).getAsJsonArray();
            for (int z = 0; z < sizeZ; z++) {
                StringBuilder row = new StringBuilder();
                if (z < la.size()) row.append(la.get(z).getAsString());
                while (row.length() < sizeX) row.append('.');
                StringBuilder cleaned = new StringBuilder();
                for (int x = 0; x < sizeX; x++) {
                    char c = row.charAt(x);
                    cleaned.append(charMap.containsKey(c) ? c : '.');
                }
                rows[y][z] = cleaned.toString();
                for (int x = 0; x < sizeX; x++) {
                    if (cleaned.charAt(x) != '.') total++;
                }
            }
        }
        if (total == 0) throw new IllegalArgumentException("设计是空的");
        if (total > MAX_BLOCKS) throw new IllegalArgumentException("超过 " + MAX_BLOCKS + " 块上限");

        Map<String, String> palette = new LinkedHashMap<>();
        for (Map.Entry<String, Block> en : charMap.entrySet()) {
            ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(en.getValue());
            if (rl != null) palette.put(en.getKey(), rl.toString());
        }

        StructureTemplate tpl = new StructureTemplate();
        tpl.name = displayName;
        tpl.palette = palette;
        tpl.rows = rows;
        return tpl;
    }

    private static void saveTemplate(String slug, StructureTemplate tpl) {
        try {
            java.nio.file.Path p = dev.elena.deepseek.DeepSeekPaths.resolve("structures/" + slug + ".json");
            Files_createDirectories(p.getParent());
            java.nio.file.Files.writeString(p, DeepSeekGson.GSON.toJson(tpl), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static String slugify(String name) {
        String s = name == null ? "" : name.trim().replaceAll("[^0-9A-Za-z\\u4e00-\\u9fa5]+", "_");
        if (s.isBlank()) s = "custom";
        return "ai_" + s;
    }

    private static void Files_createDirectories(java.nio.file.Path p) throws java.io.IOException {
        java.nio.file.Files.createDirectories(p);
    }
}
