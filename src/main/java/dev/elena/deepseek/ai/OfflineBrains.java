package dev.elena.deepseek.ai;

import dev.elena.deepseek.entity.CompanionEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 离线兜底：没配 API Key（或断网）时用关键词理解简单指令，保证模组在纯离线也能用。
 */
public class OfflineBrains {

    public static AIReply reply(CompanionEntity entity, String message) {
        String m = message == null ? "" : message.toLowerCase();
        List<AIReply.Action> actions = new ArrayList<>();

        addIf(actions, contains(m, "别建了", "取消", "停一下", "停止"));
        if (contains(m, "取消", "别建了", "停止")) {
            return AIReply.text("好，那我先停了。").withActions(actions);
        }
        if (contains(m, "跟着", "跟随", "跟我走", "跟上来")) return modeReply(actions, "follow", "好嘞，我跟上你！");
        if (contains(m, "留下", "停下", "站住", "待在这", "别动", "在这等")) return modeReply(actions, "stay", "好，我在这儿等你。");
        if (contains(m, "逛逛", "随便走", "自由活动", "散散步")) return modeReply(actions, "wander", "那我到处转转，别走远~");
        if (contains(m, "别墅", "洋房")) return buildReply(actions, "别墅", "别墅是吧，有排面！材料不够会管你要~");
        if (contains(m, "农田", "农场", "菜地")) return buildReply(actions, "农田", "开一块农田，麦子种下去慢慢长~");
        if (contains(m, "灯塔")) return buildReply(actions, "灯塔", "灯塔安排上，晚上贼亮！");
        if (contains(m, "城堡", "堡垒", "要塞")) return buildReply(actions, "城堡", "城堡都能盖，我真是太厉害了~");
        if (contains(m, "教堂")) return buildReply(actions, "教堂", "教堂盖好后记得虔诚点。");
        if (contains(m, "酒馆", "旅店", "客栈")) return buildReply(actions, "酒馆", "盖个酒馆，以后请你喝一杯（口头的）。");
        if (contains(m, "桥")) return buildReply(actions, "小桥", "修桥补路，积德行善~");
        if (contains(m, "水井")) return buildReply(actions, "水井", "挖口井，取水方便。");
        if (contains(m, "小屋", "房子", "木屋", "盖个家", "盖个房")) return buildReply(actions, "小木屋", "我来盖个小木屋，材料不够会管你要的！");
        if (contains(m, "塔", "瞭望")) return buildReply(actions, "瞭望塔", "瞭望塔是吧，包在我身上！");
        if (contains(m, "篝火", "营地")) return buildReply(actions, "篝火营地", "搭个篝火营地，晚上一起烤东西~");
        if (contains(m, "好奇心", "捡东西")) {
            AIReply.Action a = new AIReply.Action();
            a.type = "curiosity";
            actions.add(a);
            return AIReply.text("好奇心发作！我去附近捡点宝贝~").withActions(actions);
        }
        if (contains(m, "整理", "收拾", "箱子")) {
            AIReply.Action a = new AIReply.Action();
            a.type = "organize";
            actions.add(a);
            return AIReply.text("我来整理附近的箱子！").withActions(actions);
        }
        if (contains(m, "钓鱼", "钓会鱼", "钓个鱼")) {
            AIReply.Action a = new AIReply.Action();
            a.type = "fishing";
            actions.add(a);
            return AIReply.text("好，我去钓鱼，晚上加餐~").withActions(actions);
        }
        if (contains(m, "种地", "种田", "收菜", "收获", "农场")) {
            AIReply.Action a = new AIReply.Action();
            a.type = "farming";
            actions.add(a);
            return AIReply.text("包在我身上，收成会放进我背包~").withActions(actions);
        }
        if (contains(m, "状态", "背包", "身上有啥")) {
            AIReply.Action a = new AIReply.Action();
            a.type = "status";
            actions.add(a);
            return AIReply.text("看一下哈：").withActions(actions);
        }
        if (contains(m, "挥手", "打招呼", "say hi")) {
            AIReply.Action a = new AIReply.Action();
            a.type = "wave";
            actions.add(a);
            return AIReply.text("嗨~（挥手）").withActions(actions);
        }
        if (contains(m, "火把")) {
            AIReply.Action a = new AIReply.Action();
            a.type = "torch";
            actions.add(a);
            return AIReply.text("好，建筑定位火把拿好~").withActions(actions);
        }
        if (contains(m, "你好", "在吗", "嗨", "hello", "hi")) {
            AIReply.Action a = new AIReply.Action();
            a.type = "wave";
            actions.add(a);
            return AIReply.text("在呢在呢！想让我干嘛？跟你说，现在我是离线脑子，只会简单指令；在 config 里填上 AI Key 我就变聪明了~").withActions(actions);
        }
        return AIReply.text("我现在是离线模式，听得懂：跟随 / 停下 / 逛逛 / 盖小屋 / 盖塔 / 篝火营地 / 整理箱子。填了 API Key 之后你想聊啥都行~");
    }

    private static AIReply modeReply(List<AIReply.Action> actions, String value, String say) {
        AIReply.Action a = new AIReply.Action();
        a.type = "mode";
        a.value = value;
        actions.add(a);
        return AIReply.text(say).withActions(actions);
    }

    private static AIReply buildReply(List<AIReply.Action> actions, String name, String say) {
        AIReply.Action a = new AIReply.Action();
        a.type = "build";
        a.name = name;
        actions.add(a);
        return AIReply.text(say).withActions(actions);
    }

    private static void addIf(List<AIReply.Action> actions, boolean b) {
        if (b) {
            AIReply.Action a = new AIReply.Action();
            a.type = "stop";
            actions.add(a);
        }
    }

    private static boolean contains(String m, String... keys) {
        for (String k : keys) if (m.contains(k)) return true;
        return false;
    }
}
