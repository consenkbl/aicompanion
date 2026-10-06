package dev.elena.deepseek.task;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.List;

/**
 * 类似材料替代表：建造时缺某种方块，可以用同组材料顶替（Elena 要求的"自动补充"）。
 * 组内互换：木板系、原木系、石制系、白色建材系、玻璃系、栅栏墙体系。
 */
public class MaterialSubs {
    private static final List<List<Item>> GROUPS = List.of(
            group(Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.JUNGLE_PLANKS,
                    Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.MANGROVE_PLANKS,
                    Blocks.CRIMSON_PLANKS, Blocks.WARPED_PLANKS),
            group(Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG,
                    Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG),
            group(Blocks.COBBLESTONE, Blocks.STONE, Blocks.STONE_BRICKS, Blocks.ANDESITE,
                    Blocks.GRANITE, Blocks.DIORITE, Blocks.COBBLED_DEEPSLATE, Blocks.BLACKSTONE),
            group(Blocks.QUARTZ_BLOCK, Blocks.WHITE_CONCRETE, Blocks.SMOOTH_QUARTZ,
                    Blocks.WHITE_TERRACOTTA, Blocks.SNOW_BLOCK, Blocks.BONE_BLOCK),
            group(Blocks.GLASS, Blocks.GLASS_PANE),
            group(Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.BIRCH_FENCE, Blocks.OAK_FENCE_GATE,
                    Blocks.COBBLESTONE_WALL, Blocks.STONE_BRICK_WALL),
            group(Blocks.WHITE_WOOL, Blocks.LIGHT_GRAY_WOOL, Blocks.GRAY_WOOL, Blocks.BLACK_WOOL,
                    Blocks.RED_WOOL, Blocks.ORANGE_WOOL, Blocks.YELLOW_WOOL, Blocks.BLUE_WOOL)
    );

    private static List<Item> group(Block... blocks) {
        return Arrays.stream(blocks).map(Block::asItem).toList();
    }

    /** 返回包含 needed 的组（没有则 null）。 */
    public static List<Item> groupOf(Item needed) {
        for (List<Item> g : GROUPS) {
            if (g.contains(needed)) return g;
        }
        return null;
    }

    private MaterialSubs() {
    }
}
