package dev.elena.deepseek.task;

import dev.elena.deepseek.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 种地收菜任务（参考原版村民农夫的 AI）：
 * 1) 优先收割周围成熟的作物（收成进她的随身背包）
 * 2) 没有成熟作物时，找耕地用背包里的种子补种（小麦/胡萝卜/土豆/甜菜根）
 * 全部干完后收工，可随时让她停下。
 */
public class FarmingTask {
    private static final Map<Item, Block> SEEDS = new HashMap<>();

    static {
        SEEDS.put(Items.WHEAT_SEEDS, Blocks.WHEAT);
        SEEDS.put(Items.CARROT, Blocks.CARROTS);
        SEEDS.put(Items.POTATO, Blocks.POTATOES);
        SEEDS.put(Items.BEETROOT_SEEDS, Blocks.BEETROOTS);
        SEEDS.put(Items.PUMPKIN_SEEDS, Blocks.PUMPKIN_STEM);
        SEEDS.put(Items.MELON_SEEDS, Blocks.MELON_STEM);
    }

    private final CompanionEntity mob;
    @Nullable
    private BlockPos workPos;   // 要处理的作物/耕地
    private boolean harvesting; // true=收割 false=补种
    private int cooldown = 0;
    private int idleScans = 0;

    public FarmingTask(CompanionEntity mob) {
        this.mob = mob;
    }

    public void begin() {
        mob.chatSay("好，我去看看田地~");
        findWork();
    }

    public void cancel() {
        mob.chatSay("好，农活先停一停。");
    }

    public void tick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (workPos == null) {
            findWork();
            return;
        }
        double d = mob.distanceToSqr(workPos.getX() + 0.5D, workPos.getY() + 0.5D, workPos.getZ() + 0.5D);
        if (d > 12.25D) {
            if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(workPos.getX() + 0.5D, workPos.getY(), workPos.getZ() + 0.5D, 1.05D);
            }
            cooldown = 10;
            return;
        }
        // 到位了，动手
        mob.getLookControl().setLookAt(workPos.getX() + 0.5D, workPos.getY() + 0.5D, workPos.getZ() + 0.5D);
        mob.swing(InteractionHand.MAIN_HAND);
        if (harvesting) {
            BlockState st = mob.level.getBlockState(workPos);
            mob.level.destroyBlock(workPos, true);
            pickupDrops();
        } else {
            Block seedBlock = seedForPos(workPos);
            if (seedBlock != null && consumeSeed(seedBlock)) {
                mob.level.setBlock(workPos.above(), seedBlock.defaultBlockState(), 3);
            }
        }
        workPos = null;
        cooldown = 8;
    }

    /** 收割后把掉落物吸进背包。 */
    private void pickupDrops() {
        for (var ie : mob.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                mob.getBoundingBox().inflate(3.5D), net.minecraft.world.entity.item.ItemEntity::isAlive)) {
            ItemStack rest = mob.addStack(ie.getItem());
            if (rest.isEmpty()) ie.discard();
            else ie.setItem(rest);
        }
    }

    /** 找下一件农活：先收割，再补种。 */
    private void findWork() {
        BlockPos center = mob.blockPosition();
        int r = 12;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 4, r))) {
            BlockState st = mob.level.getBlockState(p);
            if (st.getBlock() instanceof CropBlock crop && crop.isMaxAge(st)) {
                double d = mob.distanceToSqr(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        if (best != null) {
            workPos = best;
            harvesting = true;
            idleScans = 0;
            return;
        }
        // 没有成熟作物 → 补种
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 4, r))) {
            BlockState st = mob.level.getBlockState(p);
            if (st.getBlock() instanceof FarmBlock
                    && mob.level.getBlockState(p.above()).isAir()
                    && seedForPos(p) != null) {
                workPos = p.immutable();
                harvesting = false;
                idleScans = 0;
                return;
            }
        }
        // 没活干了
        idleScans++;
        if (idleScans >= 2) {
            mob.chatSay(mob.escrowCopy().isEmpty()
                    ? "附近没找到能种的地，我背包里也没有种子…"
                    : "12 格内没有成熟作物或可种的耕地，农活先到这儿~");
            mob.clearFarmingTask();
        }
        cooldown = 40;
    }

    /** 该耕地能种什么（依据她背包里的种子）。 */
    @Nullable
    private Block seedForPos(BlockPos farmland) {
        for (Map.Entry<Item, Block> e : SEEDS.entrySet()) {
            if (mob.escrowCount(e.getKey()) > 0) return e.getValue();
        }
        return null;
    }

    private boolean consumeSeed(Block crop) {
        Item seed = null;
        for (Map.Entry<Item, Block> e : SEEDS.entrySet()) {
            if (e.getValue() == crop && mob.escrowCount(e.getKey()) > 0) {
                seed = e.getKey();
                break;
            }
        }
        if (seed == null) return false;
        mob.takeEscrow(seed, 1);
        return true;
    }
}
