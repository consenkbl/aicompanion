package dev.elena.deepseek.item;

import dev.elena.deepseek.entity.CompanionEntity;
import dev.elena.deepseek.task.BuildTask;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class BuildingTorchItem extends Item {

    public BuildingTorchItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        return triggerBuild(level, player, ctx.getClickedPos().above());
    }

    private static InteractionResult triggerBuild(Level level, Player player, BlockPos center) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        CompanionEntity companion = findCompanion(sp);
        if (companion == null) {
            player.sendSystemMessage(Component.literal("§d[DeepSeek]§f 你身边没有 DeepSeek，先召唤一只吧！"));
            return InteractionResult.CONSUME;
        }
        BuildTask task = companion.getBuildTask();
        if (task == null) {
            player.sendSystemMessage(Component.literal("§d[DeepSeek]§f 现在没有待开工的建筑，先跟她说「盖小屋」之类的吧！"));
            return InteractionResult.CONSUME;
        }
        boolean ok = task.onTorchPlaced(center);
        return ok ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Nullable
    private static CompanionEntity findCompanion(ServerPlayer player) {
        for (var c : player.level.getEntitiesOfClass(CompanionEntity.class,
                player.getBoundingBox().inflate(32.0D))) {
            if (player.getUUID().equals(c.getOwnerId())) return c;
        }
        return null;
    }
}
