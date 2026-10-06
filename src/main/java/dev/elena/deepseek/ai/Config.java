package dev.elena.deepseek.ai;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> API_URL;
    public static final ForgeConfigSpec.ConfigValue<String> MODEL;
    public static final ForgeConfigSpec.ConfigValue<Integer> TRIGGER_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<Integer> MAX_HISTORY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("ai");
        API_KEY = b.comment("LLM API Key（推荐智谱开放平台 open.bigmodel.cn 申请，免费模型 glm-4-flash）。留空则使用离线关键词模式")
                .define("apiKey", "");
        API_URL = b.comment("OpenAI 兼容的 chat/completions 接口地址，可换成其他厂商")
                .define("apiUrl", "https://open.bigmodel.cn/api/paas/v4/chat/completions");
        MODEL = b.comment("模型名，例如 glm-4-flash / glm-4-plus / deepseek-chat 等")
                .define("model", "glm-4-flash");
        b.pop();
        b.push("behavior");
        TRIGGER_RADIUS = b.comment("玩家在 companion 多少格内说话会触发 AI 回复")
                .defineInRange("triggerRadius", 12, 3, 64);
        MAX_HISTORY = b.comment("每个玩家保留的最大对话轮数")
                .defineInRange("maxHistory", 10, 0, 50);
        b.pop();
        SPEC = b.build();
    }

    // ------------------------------------------------------------ 读取入口（便携配置优先）

    /** API Key：便携文件夹（jar 旁 deepseek-config）优先，其次实例 config。 */
    public static String apiKey() {
        String v = PortableConfig.apiKey();
        return v != null ? v : API_KEY.get();
    }

    public static String apiUrl() {
        String v = PortableConfig.apiUrl();
        return v != null ? v : API_URL.get();
    }

    public static String model() {
        String v = PortableConfig.model();
        return v != null ? v : MODEL.get();
    }

    public static int triggerRadius() {
        Integer v = PortableConfig.triggerRadius();
        return v != null ? v : TRIGGER_RADIUS.get();
    }

    public static int maxHistory() {
        Integer v = PortableConfig.maxHistory();
        return v != null ? v : MAX_HISTORY.get();
    }
}
