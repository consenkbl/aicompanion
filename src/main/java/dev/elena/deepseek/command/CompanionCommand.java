package dev.elena.deepseek.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.elena.deepseek.entity.CompanionEntity;
import dev.elena.deepseek.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * /companion spawn|remove（需要管理员）
 * /companion mode|build（玩家操作自己的伙伴即可）
 */
public class CompanionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("companion")
                .then(Commands.literal("spawn")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            CompanionEntity c = ModEntities.COMPANION.get().create(p.getLevel());
                            if (c == null) {
                                ctx.getSource().sendFailure(Component.literal("生成失败"));
                                return 0;
                            }
                            c.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0F);
                            c.finalizeSpawn(p.getLevel(), p.getLevel().getCurrentDifficultyAt(c.blockPosition()),
                                    net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
                            p.getLevel().addFreshEntity(c);
                            c.setOwnerId(p.getUUID());
                            c.chatSay("嗨，我是 DeepSeek！以后就跟着你啦。想让我干活就直接在聊天里说~");
                            ctx.getSource().sendSuccess(Component.literal("已生成 AI 伙伴"), true);
                            return 1;
                        }))
                .then(Commands.literal("remove")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            CompanionEntity c = nearest(p);
                            if (c == null) {
                                ctx.getSource().sendFailure(Component.literal("8 格内没有找到 AI 伙伴"));
                                return 0;
                            }
                            c.cancelTasks();
                            c.discard();
                            ctx.getSource().sendSuccess(Component.literal("已移除 AI 伙伴"), true);
                            return 1;
                        }))
                .then(Commands.literal("ball").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    net.minecraft.world.item.ItemStack ball = new net.minecraft.world.item.ItemStack(
                            dev.elena.deepseek.registry.ModItems.POKE_BALL.get());
                    if (!p.getInventory().add(ball)) {
                        p.drop(ball, false);
                    }
                    ctx.getSource().sendSuccess(Component.literal(
                            "已获得精灵球：右键地面放出，拿着球右键她收回（Shift+右键她 = 切换模式）"), false);
                    return 1;
                }))
                .then(Commands.literal("mode")
                        .then(Commands.literal("follow").executes(ctx -> setMode(ctx.getSource(), "follow")))
                        .then(Commands.literal("stay").executes(ctx -> setMode(ctx.getSource(), "stay")))
                        .then(Commands.literal("wander").executes(ctx -> setMode(ctx.getSource(), "wander"))))
                .then(Commands.literal("torch").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    CompanionEntity c = nearest(p);
                    if (c == null) {
                        ctx.getSource().sendFailure(Component.literal("8 格内没有找到 AI 伙伴"));
                        return 0;
                    }
                    if (!owns(p, c)) {
                        ctx.getSource().sendFailure(Component.literal("这不是你的伙伴"));
                        return 0;
                    }
                    if (c.getBuildTask() != null) {
                        c.getBuildTask().requestTorch(p);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("它现在没有建造任务"));
                    }
                    return 1;
                }))
                .then(Commands.literal("build")
                        .then(Commands.argument("structure", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    CompanionEntity c = nearest(p);
                                    if (c == null) {
                                        ctx.getSource().sendFailure(Component.literal("8 格内没有找到 AI 伙伴"));
                                        return 0;
                                    }
                                    if (!owns(p, c)) {
                                        ctx.getSource().sendFailure(Component.literal("这不是你的伙伴"));
                                        return 0;
                                    }
                                    c.startBuild(StringArgumentType.getString(ctx, "structure"), p);
                                    return 1;
                                }))));
    }

    private static boolean owns(ServerPlayer p, CompanionEntity c) {
        return p.getUUID().equals(c.getOwnerId()) || p.hasPermissions(2);
    }

    private static int setMode(CommandSourceStack source, String mode) {
        ServerPlayer p;
        try {
            p = source.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }
        CompanionEntity c = nearest(p);
        if (c == null) {
            source.sendFailure(Component.literal("8 格内没有找到 AI 伙伴"));
            return 0;
        }
        if (!owns(p, c)) {
            source.sendFailure(Component.literal("这不是你的伙伴"));
            return 0;
        }
        c.switchMode(switch (mode) {
            case "stay" -> CompanionEntity.Mode.STAY;
            case "wander" -> CompanionEntity.Mode.WANDER;
            default -> CompanionEntity.Mode.FOLLOW;
        });
        source.sendSuccess(Component.literal("模式已切换"), true);
        return 1;
    }

    private static CompanionEntity nearest(ServerPlayer p) {
        List<CompanionEntity> list = p.getLevel().getEntitiesOfClass(CompanionEntity.class,
                p.getBoundingBox().inflate(8.0D));
        CompanionEntity best = null;
        for (CompanionEntity c : list) {
            if (best == null || c.distanceToSqr(p) < best.distanceToSqr(p)) best = c;
        }
        return best;
    }
}
