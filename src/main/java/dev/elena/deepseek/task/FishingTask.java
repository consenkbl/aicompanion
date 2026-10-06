package dev.elena.deepseek.task;

import dev.elena.deepseek.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * 钓鱼任务：找到附近水面 → 走到水边 → 甩竿（手臂动画+浮标粒子）→ 定时上钩，
 * 鱼进她的随身背包。默认钓 4 条后收工。
 */
public class FishingTask {

    private final CompanionEntity mob;
    private final Random random = new Random();
    @Nullable
    private BlockPos waterPos;
    private int phase = 0;      // 0=找水走过去 1=垂钓中
    private int timer = 0;
    private int catches = 0;
    private int cooldown = 0;

    public FishingTask(CompanionEntity mob) {
        this.mob = mob;
    }

    public void begin() {
        if (mob.escrowCount(Items.FISHING_ROD) <= 0) {
            mob.chatSay("我背包里没有钓鱼竿，给我一根我就去钓！");
            mob.clearFishingTask();
            return;
        }
        waterPos = findWater();
        if (waterPos == null) {
            mob.chatSay("附近没有能钓鱼的水面…带我到河边再试吧！");
            mob.clearFishingTask();
            return;
        }
        mob.chatSay("好，我去钓鱼，钓到什么都会告诉你~");
        mob.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
        walkToWater();
    }

    public void cancel() {
        mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        mob.setWorkingArm(false);
        mob.chatSay("好，不钓了，收竿~");
    }

    public void tick() {
        if (waterPos == null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (phase == 0) {
            double d = mob.distanceToSqr(waterPos.getX() + 0.5D, waterPos.getY(), waterPos.getZ() + 0.5D);
            if (d > 25.0D) {
                if (mob.getNavigation().isDone()) walkToWater();
                cooldown = 10;
                return;
            }
            // 到水边了，开钓
            phase = 1;
            mob.setWorkingArm(true);
            timer = 150 + random.nextInt(150);
            mob.getLookControl().setLookAt(waterPos.getX() + 0.5D, waterPos.getY(), waterPos.getZ() + 0.5D);
            return;
        }
        // 垂钓中
        timer--;
        if (timer % 40 == 20 && waterPos != null) {
            // 偶尔水花
            if (mob.level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SPLASH,
                        waterPos.getX() + 0.5D, waterPos.getY() + 0.2D, waterPos.getZ() + 0.5D,
                        3, 0.2D, 0.1D, 0.2D, 0.01D);
            }
        }
        if (timer <= 0) {
            catchFish();
        }
    }

    private void catchFish() {
        catches++;
        ItemStack fish;
        int roll = random.nextInt(100);
        if (roll < 55) fish = new ItemStack(Items.COD);
        else if (roll < 85) fish = new ItemStack(Items.SALMON);
        else fish = new ItemStack(Items.TROPICAL_FISH);

        ItemStack rest = mob.addStack(fish);
        if (!rest.isEmpty()) mob.spawnAtLocation(rest);
        mob.swing(InteractionHand.MAIN_HAND);
        if (mob.level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SPLASH,
                    waterPos.getX() + 0.5D, waterPos.getY() + 0.3D, waterPos.getZ() + 0.5D,
                    10, 0.3D, 0.2D, 0.3D, 0.02D);
        }
        mob.level.playSound(null, waterPos, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.8F, 1.0F);
        mob.chatSay("上钩啦！钓到了" + fish.getHoverName().getString() + "！");
        timer = 150 + random.nextInt(150);
    }

    private void walkToWater() {
        // 走向水面靠岸的一侧
        mob.getNavigation().moveTo(waterPos.getX() + 0.5D, waterPos.getY() - 1, waterPos.getZ() + 0.5D, 1.05D);
    }

    @Nullable
    private BlockPos findWater() {
        BlockPos center = mob.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-16, -3, -16), center.offset(16, 3, 16))) {
            BlockState st = mob.level.getBlockState(p);
            if (!st.getFluidState().is(FluidTags.WATER)) continue;
            if (!mob.level.getBlockState(p.above()).isAir()) continue;
            // 水面应接近地表，避免钻到湖底
            if (p.getY() < mob.level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ()) - 2) continue;
            double d = mob.distanceToSqr(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }
}
