package dev.elena.deepseek.item;

import dev.elena.deepseek.entity.CompanionEntity;
import dev.elena.deepseek.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 精灵球（参考 Cobblemon 的"球即容器"机制）：
 * - 拿球右键空气/方块：放出（球里有她则还原全部状态；空球则生成一只新的并绑定）
 * - 拿球普通右键她：收回（全部数据存入球，实体移除）
 * - Shift+右键她仍是切换模式（interactLivingEntity 里让位）
 */
public class PokeBallItem extends Item {

    public PokeBallItem(Properties props) {
        super(props);
    }

    // ------------------------------------------------------------ 放出

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack ball = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(ball);
        return release(level, player, ball);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        ItemStack ball = ctx.getItemInHand();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() == null) return InteractionResult.PASS;
        return release(level, ctx.getPlayer(), ball).getResult();
    }

    private static InteractionResultHolder<ItemStack> release(Level level, Player player, ItemStack ball) {
        MinecraftServer server = level.getServer();
        if (server == null) return InteractionResultHolder.pass(ball);
        CompoundTag tag = ball.getTag();

        boolean hasStored = tag != null && tag.contains("StoredEntity");
        boolean bound = tag != null && tag.hasUUID("BoundUUID");
        // 一个球只对应一只鲸鱼娘：已绑定且球里没存她 = 她在外面，拒绝重复召唤
        if (bound && !hasStored) {
            Entity existing = findEntity(server, tag.getUUID("BoundUUID"));
            if (existing != null && existing.isAlive()) {
                player.sendSystemMessage(Component.literal("§d[DeepSeek]§f 她已经在外面陪着你啦！"));
                return InteractionResultHolder.fail(ball);
            }
            // 找不到实体（她已经死了）→ 允许重新生成一只并重新绑定
        }

        // 放出位置：视线落点，没瞄准就放身前
        HitResult hit = player.pick(6.0D, 0.0F, false);
        Vec3 look = player.getLookAngle();
        BlockPos pos;
        if (hit.getType() != HitResult.Type.MISS) {
            pos = new BlockPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
        } else {
            pos = new BlockPos(player.getX() + look.x * 3, player.getY() + 1, player.getZ() + look.z * 3);
        }

        CompoundTag stored = hasStored ? tag.getCompound("StoredEntity") : null;
        CompanionEntity c = ModEntities.COMPANION.get().create(level);
        if (c == null) return InteractionResultHolder.fail(ball);
        if (stored != null && !stored.isEmpty()) {
            c.load(stored);   // 还原模式/背包/主人/名字等全部状态
        } else {
            c.finalizeSpawn((ServerLevel) level, level.getCurrentDifficultyAt(pos),
                    MobSpawnType.COMMAND, null, null);
        }
        c.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0.0F);
        if (c.getOwnerId() == null) c.setOwnerId(player.getUUID());
        level.addFreshEntity(c);

        // 绑定终身：球记住她，不再清空
        CompoundTag tag2 = ball.getOrCreateTag();
        tag2.remove("StoredEntity");
        tag2.putUUID("BoundUUID", c.getUUID());

        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, c.getX(), c.getY() + 1.0D, c.getZ(), 15, 0.3D, 0.5D, 0.3D, 0.02D);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.9F, 0.6F);
        c.chatSay("出来啦！有什么吩咐~");
        return InteractionResultHolder.consume(ball);
    }

    // ------------------------------------------------------------ 收回

    @Override
    public InteractionResult interactLivingEntity(ItemStack ball, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof CompanionEntity c)) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) return InteractionResult.PASS;   // Shift 留给模式切换
        if (player.level.isClientSide) return InteractionResult.SUCCESS;
        return tryCapture(player, ball, c) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    /** 收回：把 DeepSeek 的全部状态存入球。成功返回 true。 */
    public static boolean tryCapture(Player player, ItemStack ball, CompanionEntity c) {
        if (c.getOwnerId() != null && !c.getOwnerId().equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§d[DeepSeek]§f 这不是你的伙伴哦！"));
            return false;
        }
        CompoundTag tag = ball.getTag();
        if (tag != null && tag.hasUUID("BoundUUID")) {
            MinecraftServer server = player.level.getServer();
            Entity existing = server == null ? null : findEntity(server, tag.getUUID("BoundUUID"));
            if (existing != null && existing.isAlive() && existing != c) {
                player.sendSystemMessage(Component.literal("§d[DeepSeek]§f 球里已经有一只了！"));
                return false;
            }
        }

        // 全部状态存入球
        CompoundTag data = c.saveWithoutId(new CompoundTag());
        data.putUUID("UUID", c.getUUID());
        CompoundTag tag2 = ball.getOrCreateTag();
        tag2.put("StoredEntity", data);
        tag2.putUUID("BoundUUID", c.getUUID());
        c.discard();

        if (player.level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, c.getX(), c.getY() + 1.0D, c.getZ(), 15, 0.3D, 0.5D, 0.3D, 0.02D);
        }
        player.level.playSound(null, c.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.9F, 1.2F);
        player.sendSystemMessage(Component.literal("§d[DeepSeek]§f 回来吧！拿着球右键地面就能再放出来。"));
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("StoredEntity")) {
            return Component.translatable("item.deepseek.poke_ball.filled");
        }
        return super.getName(stack);
    }

    /** 1.19.2 的 MinecraftServer 没有 getEntity(UUID)，遍历各维度查找。 */
    @Nullable
    private static Entity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity e = lvl.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }
}
