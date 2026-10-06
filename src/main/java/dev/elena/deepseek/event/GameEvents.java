package dev.elena.deepseek.event;

import dev.elena.deepseek.DeepSeekMod;
import dev.elena.deepseek.ai.Config;
import dev.elena.deepseek.command.CompanionCommand;
import dev.elena.deepseek.entity.CompanionEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 总线事件：聊天触发 AI、注册指令。
 */
@Mod.EventBusSubscriber(modid = DeepSeekMod.MODID)
public class GameEvents {

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        int radius = Config.triggerRadius();
        CompanionEntity found = null;
        for (ServerLevel lvl : server.getAllLevels()) {
            for (CompanionEntity c : lvl.getEntitiesOfClass(CompanionEntity.class,
                    player.getBoundingBox().inflate(radius))) {
                if (found == null || c.distanceToSqr(player) < found.distanceToSqr(player)) found = c;
            }
        }
        if (found != null) {
            found.onPlayerChat(player, event.getMessage().getString());
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        CompanionCommand.register(event.getDispatcher());
    }
}
