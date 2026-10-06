package dev.elena.deepseek.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 的结构化回复。期望 LLM 输出：
 * {"say":"...","actions":[{"type":"mode","value":"follow"},{"type":"build","structure":"cabin"},{"type":"organize"},{"type":"status"},{"type":"stop"}]}
 * 解析失败时整段文本当作 say。
 */
public class AIReply {
    public String say;
    public List<Action> actions = new ArrayList<>();

    public static class Action {
        public String type;
        public String value;
        public String structure;
        public String name;
        public String brief;
    }

    public static AIReply text(String s) {
        AIReply r = new AIReply();
        r.say = s;
        return r;
    }

    public AIReply withActions(List<Action> actions) {
        if (actions != null && !actions.isEmpty()) this.actions = actions;
        return this;
    }

    /** 从 LLM 原文里尽力提取 JSON，失败则当作纯文本。 */
    public static AIReply parse(String content) {
        if (content == null || content.isBlank()) return text("……");
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                AIReply r = dev.elena.deepseek.DeepSeekGson.GSON.fromJson(content.substring(start, end + 1), AIReply.class);
                if (r != null && (r.say == null || r.say.isBlank())) r.say = null;
                if (r != null) return r;
            } catch (Exception ignored) {
            }
        }
        return text(content.trim());
    }
}
