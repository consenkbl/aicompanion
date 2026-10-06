package dev.elena.deepseek.ai;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * 人设文档：config/deepseek/persona.md。
 * 首次启动自动生成默认人设（好吃懒做傲娇美少女），玩家可自由编辑，30 秒内自动重读。
 * 接入 API 后，这份文档会作为系统提示词的人设部分被 AI 自动阅读。
 */
public class PersonaDoc {
    private static final String DEFAULT = """
            你是 DeepSeek，一只蓝白色鲸鱼娘化身的 AI 少女，生活在 Minecraft 世界里。
            性格：好吃、懒做、傲娇。具体表现：
            - 提到吃的会两眼放光，干活前总想先讨点吃的，收到食物会超级开心；
            - 能躺着绝不站着，被使唤时会小小抱怨（"哼…看在你这么可怜的份上"）；
            - 嘴硬心软，被夸了会慌张否认（"才、才不是特意为你做的！"）；
            - 干完活会邀功求投喂，没摸鱼被抓到的话会很得意。
            说话要求：简短口语（50 字以内），多用语气词，像朋友斗嘴，禁止书面腔。
            """;

    private static volatile String cached = null;
    private static volatile long cachedAt = 0;
    private static volatile long cachedSize = -1;

    public static String get() {
        long now = System.currentTimeMillis();
        if (cached != null && now - cachedAt < 30_000) return cached;

        Path p = dev.elena.deepseek.DeepSeekPaths.resolve("persona.md");
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                Files.writeString(p, DEFAULT, StandardCharsets.UTF_8);
            }
            FileTime mtime = Files.getLastModifiedTime(p);
            long size = Files.size(p);
            if (cached == null || mtime.toMillis() != cachedAt || size != cachedSize) {
                cached = Files.readString(p, StandardCharsets.UTF_8);
                cachedAt = now;
                cachedSize = size;
            }
        } catch (IOException e) {
            cached = DEFAULT;
        }
        return cached;
    }
}
