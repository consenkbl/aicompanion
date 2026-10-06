package dev.elena.deepseek.ai;

import dev.elena.deepseek.entity.CompanionEntity;
import dev.elena.deepseek.task.StructureTemplates;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 能力接口：DeepSeek 所有可供 AI 调用的动作都在这里注册。
 * 注册表自动生成系统提示词里的动作说明（AI 能调什么由这里决定），
 * 新功能只需 new 一个 Action 加进 ALL，AI 与离线关键词即可使用。
 */
public final class ActionRegistry {

    public interface Executor {
        void run(CompanionEntity mob, @Nullable ServerPlayer player, AIReply.Action raw);
    }

    public static class Action {
        public final String type;
        public final String prompt;
        private final Executor exec;

        public Action(String type, String prompt, Executor exec) {
            this.type = type;
            this.prompt = prompt;
            this.exec = exec;
        }

        public void run(CompanionEntity mob, @Nullable ServerPlayer player, AIReply.Action raw) {
            exec.run(mob, player, raw);
        }
    }

    public static final List<Action> ALL = List.of(
            new Action("mode",
                    "{\"type\":\"mode\",\"value\":\"follow|stay|wander\"} 切换跟随/停下/游走",
                    (mob, p, a) -> {
                        dev.elena.deepseek.entity.CompanionEntity.Mode m = mob.parseMode(a.value);
                        if (m != null) mob.switchMode(m);
                    }),
            new Action("stop",
                    "{\"type\":\"stop\"} 停止当前手头的活",
                    (mob, p, a) -> mob.cancelTasks()),
            new Action("build",
                    "{\"type\":\"build\",\"name\":\"<建筑名>\",\"brief\":\"<外观一句话，可选>\"} 建造任何建筑。图纸库现有 "
                            + dev.elena.deepseek.task.StructureTemplates.librarySummary()
                            + "。玩家要的建筑在库里就照抄库里的名字；不在库里她会自己设计蓝图。玩家明确说想自己设计/自定义时，name 开头加「自定义·」",
                    (mob, p, a) -> mob.startSmartBuild(
                            a.name == null || a.name.isBlank() ? a.structure : a.name,
                            a.brief == null ? "" : a.brief, p)),
            new Action("curiosity",
                    "{\"type\":\"curiosity\"} 好奇心发作：去附近闲逛，把有趣的掉落物捡进随身背包（缺建造材料时特别有用，捡到的同类方块可以顶替建材）",
                    (mob, p, a) -> mob.startCuriosity()),
            new Action("organize",
                    "{\"type\":\"organize\"} 整理附近的箱子",
                    (mob, p, a) -> mob.startOrganize()),
            new Action("fishing",
                    "{\"type\":\"fishing\"} 去附近水边钓鱼（钓到的鱼进她的随身背包）",
                    (mob, p, a) -> mob.startFishing()),
            new Action("farming",
                    "{\"type\":\"farming\"} 去种地：收割成熟作物、用她背包里的种子补种",
                    (mob, p, a) -> mob.startFarming()),
            new Action("torch",
                    "{\"type\":\"torch\"} 把建筑定位火把递给玩家（材料齐全时可索要）",
                    (mob, p, a) -> {
                        if (mob.getBuildTask() != null) mob.getBuildTask().requestTorch(p);
                        else mob.chatSay("现在没有建造任务哦~ 先说「盖小屋」之类的让我开工！");
                    }),
            new Action("status",
                    "{\"type\":\"status\"} 汇报自己的状态",
                    (mob, p, a) -> mob.chatSay(mob.statusLine())),
            new Action("change_blueprint",
                    "{\"type\":\"change_blueprint\"} 玩家觉得当前图纸的材料太难凑，换一张同类图纸（重报材料清单）",
                    (mob, p, a) -> {
                        dev.elena.deepseek.task.BuildTask bt = mob.getBuildTask();
                        if (bt == null || !bt.nextBlueprint(p)) {
                            mob.chatSay("现在没有在等材料的建造任务，换不了图纸~");
                        }
                    }),
            new Action("wave",
                    "{\"type\":\"wave\"} 挥手打招呼",
                    (mob, p, a) -> mob.startWave())
    );

    /** 按 type 分发动作；未知的如实说不会。 */
    public static void dispatch(CompanionEntity mob, @Nullable ServerPlayer player, AIReply.Action raw) {
        if (raw == null || raw.type == null) return;
        for (Action a : ALL) {
            if (a.type.equals(raw.type)) {
                a.run(mob, player, raw);
                return;
            }
        }
        mob.chatSay("这个我还没学会…");
    }

    /** 自动生成系统提示词里的动作说明段落。 */
    public static String promptLines() {
        String structures = String.join("|", StructureTemplates.knownIds());
        StringBuilder sb = new StringBuilder();
        for (Action a : ALL) {
            sb.append("                   - ")
              .append(a.prompt.replace("<structures>", structures)).append('\n');
        }
        return sb.toString();
    }

    private ActionRegistry() {
    }
}
