package dev.elena.deepseek.ai;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 知识库（自动读取当前世界的整合包内容，无需手写资料）：
 * 1) 自动枚举当前加载的全部模组：名称 + mods.toml 里的简介；
 * 2) 可选补充：config/deepseek/knowledge/ 下的手写 .md/.txt（想要更细的资料再放）。
 * 回答整合包问题时作为唯一事实来源注入系统提示词。
 */
public class KnowledgeBase {
    private static volatile String cached = "";
    private static volatile long cachedAt = 0;
    private static final int MAX_MODLIST_CHARS = 14000;
    private static final int MAX_MANUAL_CHARS = 6000;
    private static final int MAX_DESC_CHARS = 140;

    public static String digest() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < 30_000) return cached;

        StringBuilder sb = new StringBuilder();

        // 1) 自动：当前加载的模组（名称 + 简介）
        StringBuilder mods = new StringBuilder();
        try {
            for (IModInfo info : ModList.get().getMods()) {
                String name = info.getDisplayName();
                String desc = clean(info.getDescription());
                if (desc.isEmpty()) {
                    mods.append(name).append("；");
                } else {
                    mods.append(name).append("（").append(desc).append("）；");
                }
                if (mods.length() > MAX_MODLIST_CHARS) {
                    mods.append("……等（模组太多，剩余略）");
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        sb.append("【当前世界已加载的模组（自动读取，共 ").append(modCount()).append(" 个）】\n")
          .append(mods).append('\n');

        // 2) 可选手写补充资料
        Path dir = dev.elena.deepseek.DeepSeekPaths.resolve("knowledge");
        if (Files.isDirectory(dir)) {
            StringBuilder manual = new StringBuilder();
            try (Stream<Path> list = Files.list(dir)) {
                list.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.endsWith(".md") || n.endsWith(".txt");
                }).sorted().forEach(p -> {
                    try {
                        String content = Files.readString(p, StandardCharsets.UTF_8);
                        if (manual.length() + content.length() > MAX_MANUAL_CHARS) return;
                        manual.append("【").append(p.getFileName().toString()).append("】\n")
                              .append(content).append("\n\n");
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
            if (manual.length() > 0) {
                sb.append("【玩家手写补充资料】\n").append(manual);
            }
        }

        String s = sb.toString();
        if (s.length() > MAX_MODLIST_CHARS + MAX_MANUAL_CHARS + 200) {
            s = s.substring(0, MAX_MODLIST_CHARS + MAX_MANUAL_CHARS + 200);
        }
        cached = s;
        cachedAt = now;
        return s;
    }

    private static int modCount() {
        try {
            return ModList.get().getMods().size();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String clean(@Nullable String s) {
        if (s == null) return "";
        String d = s.replaceAll("<[^>]*>", " ")   // 去掉简易 HTML 标签
                    .replaceAll("\\s+", " ")
                    .trim();
        if (d.length() > MAX_DESC_CHARS) d = d.substring(0, MAX_DESC_CHARS) + "…";
        return d;
    }
}
