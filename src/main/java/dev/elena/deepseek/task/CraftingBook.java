package dev.elena.deepseek.task;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * DeepSeek 自带的"合成台"：一个内置配方书。
 * 玩家只需要给原始材料（原木、圆石、泥土……），她会自动加工成木板/木棍/圆石墙等建房子要用的东西。
 */
public class CraftingBook {
    private record Recipe(Item out, int outCount, Map<Item, Integer> in) {
    }

    private static final List<Recipe> RECIPES = new ArrayList<>();

    private static Item item(String id) {
        try {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            return it == null || it == Items.AIR ? null : it;
        } catch (Exception e) {
            return null;
        }
    }

    private static void add(Item out, int outCount, Map<Item, Integer> in) {
        RECIPES.add(new Recipe(out, outCount, in));
    }

    private static void addWood(String planksName, String logName) {
        Item log = item(logName);
        Item planks = item(planksName);
        if (log == null || planks == null) return;
        add(planks, 4, Map.of(log, 1));
        add(Items.STICK, 4, Map.of(planks, 2));
    }

    static {
        addWood("oak_planks", "oak_log");
        addWood("spruce_planks", "spruce_log");
        addWood("birch_planks", "birch_log");
        addWood("jungle_planks", "jungle_log");
        addWood("acacia_planks", "acacia_log");
        addWood("dark_oak_planks", "dark_oak_log");
        addWood("mangrove_planks", "mangrove_log");
        addWood("crimson_planks", "crimson_stem");
        addWood("warped_planks", "warped_stem");

        add(Blocks.COBBLESTONE_WALL.asItem(), 1, Map.of(Items.COBBLESTONE, 6));
        add(Blocks.STONE_BRICK_WALL.asItem(), 1, Map.of(Items.STONE_BRICKS, 6));
        add(Items.GLASS_PANE, 16, Map.of(Items.GLASS, 6));
        add(Blocks.COARSE_DIRT.asItem(), 2, Map.of(Items.DIRT, 1, Items.GRAVEL, 1));
        add(Blocks.HAY_BLOCK.asItem(), 1, Map.of(Items.WHEAT, 9));

        // 营火：木棍3 + 煤/木炭1 + 原木3（每种原木各注册一条）
        for (String lg : new String[]{"oak_log", "spruce_log", "birch_log", "jungle_log",
                "acacia_log", "dark_oak_log", "mangrove_log"}) {
            Item log = item(lg);
            if (log == null) continue;
            add(Blocks.CAMPFIRE.asItem(), 1, Map.of(Items.STICK, 3, Items.COAL, 1, log, 3));
            add(Blocks.CAMPFIRE.asItem(), 1, Map.of(Items.STICK, 3, Items.CHARCOAL, 1, log, 3));
        }
    }

    private static Recipe recipeFor(Item item) {
        for (Recipe r : RECIPES) {
            if (r.out().equals(item)) return r;
        }
        return null;
    }

    /**
     * 确保 pool 里有 count 个 item；不足时用配方合成（消耗 pool 中的材料，含递归加工半成品）。
     * 成功返回 true（pool 被更新）；失败 pool 保持原样。
     */
    public static boolean craft(Item item, int count, Map<Item, Integer> pool, int depth) {
        if (depth > 5) return false;
        if (pool.getOrDefault(item, 0) >= count) return true;
        Recipe r = recipeFor(item);
        if (r == null) return false;
        int batches = (int) Math.ceil(count / (double) r.outCount());
        Map<Item, Integer> backup = new HashMap<>(pool);
        for (Map.Entry<Item, Integer> e : r.in().entrySet()) {
            if (!craft(e.getKey(), e.getValue() * batches, pool, depth + 1)) {
                pool.clear();
                pool.putAll(backup);
                return false;
            }
        }
        for (Map.Entry<Item, Integer> e : r.in().entrySet()) {
            pool.merge(e.getKey(), -e.getValue() * batches, Integer::sum);
        }
        pool.merge(item, r.outCount() * batches, Integer::sum);
        return true;
    }

    /**
     * 合成 1 个 item 需要的"原始材料"成本（递归展开到没有配方的材料为止）。
     * key=原始材料, value=每单位所需数量（可为小数）。item 本身没配方时返回 {item: 1}。
     */
    public static Map<Item, Double> rawCost(Item item) {
        Map<Item, Double> out = new LinkedHashMap<>();
        rawCostInner(item, 1.0D, out, 0);
        return out;
    }

    private static void rawCostInner(Item item, double amount, Map<Item, Double> out, int depth) {
        Recipe r = depth > 5 ? null : recipeFor(item);
        if (r == null) {
            out.merge(item, amount, Double::sum);
            return;
        }
        for (Map.Entry<Item, Integer> e : r.in().entrySet()) {
            rawCostInner(e.getKey(), amount * e.getValue() / (double) r.outCount(), out, depth + 1);
        }
    }
}
