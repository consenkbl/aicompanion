package dev.elena.deepseek.task;

import dev.elena.deepseek.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * 《好奇心》动作：像末影人一样对附近的东西产生兴趣，
 * 主动溜达着把掉落的方块捡进随身背包（建造缺材料时会自动触发，捡到的"杂七杂八"
 * 可以作为类似材料补充进建筑）。持续约 60 秒或被叫停。
 */
public class CuriosityTask {
    private final CompanionEntity mob;
    private final Random random = new Random();
    private int ticksLeft = 1200 + random.nextInt(600);   // 60~90 秒
    private int cooldown = 0;
    /** 末影人同款：可以顺手拿走的方块白名单（自然方块，不拆别人的建筑） */
    private static final java.util.Set<Block> PICKABLE = java.util.Set.of(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL,
            Blocks.STONE, Blocks.COBBLESTONE, Blocks.GRAVEL, Blocks.SAND, Blocks.RED_SAND,
            Blocks.CLAY, Blocks.SNOW_BLOCK, Blocks.SANDSTONE, Blocks.MOSS_BLOCK,
            Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES,
            Blocks.PUMPKIN, Blocks.MELON, Blocks.COBWEB);

    public CuriosityTask(CompanionEntity mob) {
        this.mob = mob;
    }

    public void begin() {
        mob.chatSay("咦？那个是什么…好奇心让我出去转转，捡点有趣的东西！");
    }

    public void cancel() {
        mob.chatSay("好吧好吧，好奇宝宝先回来了~");
    }

    /** 找附近可拾取的白名单方块。 */
    @Nullable
    private BlockPos findPickableBlock() {
        BlockPos center = mob.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-5, -2, -5), center.offset(5, 2, 5))) {
            if (PICKABLE.contains(mob.level.getBlockState(p).getBlock())) {
                return p.immutable();
            }
        }
        return null;
    }

    public void tick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        ticksLeft -= 20;
        if (ticksLeft <= 0) {
            mob.chatSay("逛完啦！捡到的都放进背包了，说不定盖房子能用上~");
            mob.clearCuriosityTask();
            return;
        }
        // 附近有掉落物 → 走过去捡
        List<ItemEntity> items = mob.level.getEntitiesOfClass(ItemEntity.class,
                mob.getBoundingBox().inflate(8.0D), ItemEntity::isAlive);
        if (!items.isEmpty()) {
            ItemEntity target = items.get(random.nextInt(items.size()));
            if (mob.distanceToSqr(target) > 2.25D) {
                mob.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.1D);
                cooldown = 15;
                return;
            }
            ItemStack rest = mob.addStack(target.getItem());
            if (rest.isEmpty()) {
                target.discard();
                if (random.nextInt(3) == 0) {
                    mob.chatSay("捡到了" + target.getItem().getHoverName().getString() + "！说不定有用~");
                }
                if (mob.level instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            mob.getX(), mob.getY() + 1.5D, mob.getZ(), 4, 0.3D, 0.3D, 0.3D, 0.0D);
                }
            } else {
                mob.chatSay("背包满啦，捡不动了…");
                ticksLeft = Math.min(ticksLeft, 100);
            }
            cooldown = 10;
            return;
        }
        // 没有掉落物 → 找末影人同款的"可拾取方块"
        BlockPos pickable = findPickableBlock();
        if (pickable != null) {
            if (mob.distanceToSqr(pickable.getX() + 0.5D, pickable.getY() + 0.5D, pickable.getZ() + 0.5D) > 2.25D) {
                mob.getNavigation().moveTo(pickable.getX() + 0.5D, pickable.getY(), pickable.getZ() + 0.5D, 1.1D);
                cooldown = 15;
                return;
            }
            // 到了：像末影人一样整块拿走
            Block block = mob.level.getBlockState(pickable).getBlock();
            mob.level.destroyBlock(pickable, false);
            ItemStack taken = new ItemStack(block);
            mob.setItemInHand(InteractionHand.MAIN_HAND, taken);   // 手里举着方块
            ItemStack rest = mob.addStack(taken);
            if (!rest.isEmpty()) mob.spawnAtLocation(rest);
            if (mob.level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.PORTAL,
                        pickable.getX() + 0.5D, pickable.getY() + 0.5D, pickable.getZ() + 0.5D,
                        8, 0.3D, 0.3D, 0.3D, 0.05D);
            }
            if (random.nextInt(3) == 0) {
                mob.chatSay("顺手捡了个" + taken.getHoverName().getString() + "，嘿嘿~");
            }
            cooldown = 20;
            return;
        }
        // 什么都没找到 → 随机溜达
        BlockPos center = mob.blockPosition();
        int dx = random.nextInt(17) - 8;
        int dz = random.nextInt(17) - 8;
        int y = mob.level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX() + dx, center.getZ() + dz);
        mob.getNavigation().moveTo(center.getX() + dx + 0.5D, y, center.getZ() + dz + 0.5D, 1.0D);
        cooldown = 60;
    }
}
