package dev.elena.deepseek.task;

import dev.elena.deepseek.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 整理箱子：走到最近的箱子/木桶，合并堆叠并按类别排序放回。
 */
public class OrganizeTask {
    private final CompanionEntity mob;
    @Nullable
    private BlockPos chestPos;
    private int phase = 0; // 0=走过去 1=整理
    private int cooldown = 0;
    private int mergedCount = 0;

    public OrganizeTask(CompanionEntity mob) {
        this.mob = mob;
    }

    public void begin() {
        chestPos = findChest();
        if (chestPos == null) {
            mob.chatSay("附近没看到箱子…把我放到箱子旁边再说一次吧！");
            mob.clearOrganizeTask();
            return;
        }
        mob.chatSay("找到箱子了，我去收拾~");
        walkTo();
    }

    public void cancel() {
        mob.chatSay("好，不整了。");
    }

    public void tick() {
        if (chestPos == null) {
            mob.clearOrganizeTask();
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        double d = mob.distanceToSqr(chestPos.getX() + 0.5D, chestPos.getY() + 0.5D, chestPos.getZ() + 0.5D);
        if (phase == 0) {
            if (d > 9.0D) {
                if (mob.getNavigation().isDone()) walkTo();
                cooldown = 5;
                return;
            }
            phase = 1;
            mob.getLookControl().setLookAt(chestPos.getX() + 0.5D, chestPos.getY() + 0.5D, chestPos.getZ() + 0.5D);
            mob.swing(InteractionHand.MAIN_HAND);
            cooldown = 15;
            return;
        }
        sortChest();
        mob.clearOrganizeTask();
    }

    // ------------------------------------------------------------------ 内部

    private void walkTo() {
        mob.getNavigation().moveTo(chestPos.getX() + 0.5D, chestPos.getY(), chestPos.getZ() + 0.5D, 1.1D);
    }

    @Nullable
    private BlockPos findChest() {
        BlockPos center = mob.blockPosition();
        int r = 10;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 4, r))) {
            BlockState st = mob.level.getBlockState(pos);
            if (st.getBlock() instanceof ChestBlock || st.getBlock() instanceof BarrelBlock) {
                double d = mob.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                if (d < bestD) {
                    bestD = d;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    private void sortChest() {
        BlockState st = mob.level.getBlockState(chestPos);
        if (!(st.getBlock() instanceof ChestBlock) && !(st.getBlock() instanceof BarrelBlock)) {
            mob.chatSay("咦，箱子怎么不见了？");
            return;
        }
        BlockEntity be = mob.level.getBlockEntity(chestPos);
        if (!(be instanceof Container container)) {
            mob.chatSay("咦，这个箱子打不开…");
            return;
        }
        mob.getLookControl().setLookAt(chestPos.getX() + 0.5D, chestPos.getY() + 0.5D, chestPos.getZ() + 0.5D);
        mob.swing(InteractionHand.MAIN_HAND);

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty()) items.add(s);
        }
        // 合并堆叠
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack s : items) {
            if (s.getMaxStackSize() > 1) {
                for (ItemStack t : merged) {
                    if (s.isEmpty()) break;
                    if (ItemStack.isSameItemSameTags(t, s) && t.getCount() < t.getMaxStackSize()) {
                        int move = Math.min(t.getMaxStackSize() - t.getCount(), s.getCount());
                        t.grow(move);
                        s.shrink(move);
                        mergedCount++;
                    }
                }
            }
            if (!s.isEmpty()) merged.add(s);
        }
        // 分类排序：工具武器 → 盔甲 → 食物 → 方块 → 其他
        merged.sort(Comparator.comparingInt(OrganizeTask::category)
                .thenComparing(s -> s.getHoverName().getString()));
        int slots = container.getContainerSize();
        for (int i = 0; i < slots; i++) {
            container.setItem(i, i < merged.size() ? merged.get(i) : ItemStack.EMPTY);
        }
        // 放不下的丢回地上
        for (int i = slots; i < merged.size(); i++) {
            mob.spawnAtLocation(merged.get(i));
        }
        container.setChanged();
        mob.chatSay("整理完了！合并了 " + mergedCount + " 组，一共 " + Math.min(merged.size(), slots)
                + " 组：工具武器在前面，食物居中，方块靠后。");
        mergedCount = 0;
    }

    private static int category(ItemStack s) {
        Item it = s.getItem();
        if (it instanceof TieredItem || it instanceof ProjectileWeaponItem) return 0;
        if (it instanceof ArmorItem) return 1;
        if (it.isEdible()) return 2;
        if (it instanceof BlockItem) return 3;
        return 4;
    }
}
