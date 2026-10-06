package dev.elena.deepseek.task;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 建筑模板：rows[y][z] = 一行字符（x 方向），'.' 表示不放置。
 * palette 把字符映射到方块 id。JSON 放在 data/deepseek/structures/ 下，玩家可自行扩展。
 */
public class StructureTemplate {
    public String name;
    public Map<String, String> palette;
    public String[][] rows;
    /** 图纸库标识：关键词列表（AI 与匹配器据此识别这张图纸是什么建筑） */
    public java.util.List<String> tags;
    /** 给 AI 和玩家看的描述 */
    public String desc;

    public int sizeX() {
        return rows[0][0].length();
    }

    public int sizeY() {
        return rows.length;
    }

    public int sizeZ() {
        return rows[0].length;
    }

    public char charAt(int x, int y, int z) {
        return rows[y][z].charAt(x);
    }

    public Block blockFor(char c) {
        if (palette == null) return null;
        String id = palette.get(String.valueOf(c));
        if (id == null) return null;
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        return b == null ? null : b;
    }

    /** 模板需要的材料清单。 */
    public Map<Item, Integer> materialCounts() {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (int y = 0; y < sizeY(); y++) {
            for (int z = 0; z < sizeZ(); z++) {
                for (int x = 0; x < sizeX(); x++) {
                    char c = charAt(x, y, z);
                    if (c == '.') continue;
                    Block b = blockFor(c);
                    if (b == null) continue;
                    Item it = b.asItem();
                    if (it == net.minecraft.world.item.Items.AIR) continue;   // 水、岩浆等无物品方块不算材料
                    counts.merge(it, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    public String displayName() {
        return name == null ? "建筑" : name;
    }
}
