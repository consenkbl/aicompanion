package dev.elena.deepseek;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置/数据文件路径解析：
 * 1) 模组 jar 旁边如果有 deepseek-config 文件夹（首次启动自动生成），所有配置和数据都优先放/读这里
 *    —— 这样 jar 和文件夹一起拷走，就等于把全部配置带走了（换电脑/换整合包通用）；
 * 2) 没有便携文件夹时，回落到实例的 config/deepseek/ 目录（标准行为）。
 */
public class DeepSeekPaths {
    private static final String FOLDER = "deepseek-config";
    private static volatile Path portable;
    private static volatile boolean scanned = false;

    /** 模组 jar 旁边的便携配置文件夹（不存在则自动创建并写入模板）。可能为 null（定位失败时）。 */
    @Nullable
    public static Path portableDir() {
        if (!scanned) {
            scanned = true;
            try {
                var container = ModList.get().getModContainerById(DeepSeekMod.MODID);
                if (container.isPresent()) {
                    Path jar = container.get().getModInfo().getOwningFile().getFile().getFilePath();
                    Path dir = jar.toAbsolutePath().getParent().resolve(FOLDER);
                    if (!Files.isDirectory(dir)) {
                        Files.createDirectories(dir);
                        Files.writeString(dir.resolve("deepseek-common.toml"), TEMPLATE_TOML, StandardCharsets.UTF_8);
                        Files.writeString(dir.resolve("使用说明.txt"), GUIDE, StandardCharsets.UTF_8);
                    }
                    portable = dir;
                }
            } catch (Throwable t) {
                portable = null;   // 定位/创建失败（只读目录等），回落实例配置
            }
        }
        return portable;
    }

    /** 解析配置/数据文件位置：便携文件夹优先。 */
    public static Path resolve(String relative) {
        Path p = portableDir();
        if (p != null) return p.resolve(relative);
        return FMLPaths.CONFIGDIR.get().resolve("deepseek/" + relative);
    }

    private static final String TEMPLATE_TOML = """
            # ═══════════════════════════════════════
            #  DeepSeek 鲸鱼娘AI助手 · 配置文件
            # ═══════════════════════════════════════
            # 只需要改两行：
            #   1. apiKey：去 open.bigmodel.cn 申请（免费），把 key 粘贴进引号里
            #   2. model：默认 glm-4-flash 免费；想更强改成 glm-4-plus
            # 保存后立刻生效，无需重启。
            # （本文件夹和模组 jar 放在一起，一起拷到别的整合包也一样生效）

            [ai]
            apiKey = ""
            apiUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
            model = "glm-4-flash"

            [behavior]
            # 玩家在她多少格内说话会触发回复
            triggerRadius = 12
            # 每个玩家保留的最大对话轮数
            maxHistory = 10
            """;

    private static final String GUIDE = """
            ══════════════════════════════════════════
               DeepSeek 鲸鱼娘AI助手 · 设置说明
            ══════════════════════════════════════════

            【让 AI 变聪明只需两步（免费）】

            第一步：申请 API Key
              1. 浏览器打开  open.bigmodel.cn （智谱开放平台），手机号/微信登录
              2. 右上角「控制台」→ 左侧「API Keys」→ 新建并复制一串钥匙

            第二步：填进本文件夹里的 deepseek-common.toml
              1. 用记事本打开本文件夹的  deepseek-common.toml
              2. 找到这一行：  apiKey = ""
              3. 把 key 粘贴进引号里 →  apiKey = "粘贴到这里"
              4. 想换更强的模型就把 model = "glm-4-flash" 改成别的
              5. 保存（立即生效，无需重启）

            【完成！】
              · 填了 key = 完全体：自由聊天、傲娇人设、回答整合包问题
              · 默认模型 glm-4-flash 是免费的，不产生任何费用
              · 本文件夹和 jar 一起拷到任何整合包都同样生效

            ══════════════════════════════════════════

            【玩法速查】
              · 空手 Ctrl+右键她        切换 跟随/停下/游走
              · 空手普通右键她          打招呼
              · 左 Shift+右键她         打开她的随身背包
              · 拿物品右键她            交付材料（她自带合成台，原木也能加工）
              · 拿精灵球右键地面        放出 / 拿球右键她 收回
              · 聊天栏对她说            "跟着我" "盖小屋" "整理箱子" "去钓鱼" "去种地"…
              · 静止 3 秒               她会坐下睡着（头顶冒 ZZZ）
              · 打她一下                她会哭着乱跑（别欺负她！）

            【常见问题】
              · 她不理人？   站近一点（12 格内）；联机时服务端也要装模组并填 key
              · 错误码 401   key 复制错了/多了空格
              · 错误码 429   请求太频繁（正常玩不会）
              · 她是离线模式？  就是 apiKey 还没填，填完就变聪明

            ══════════════════════════════════════════
            """;
}
