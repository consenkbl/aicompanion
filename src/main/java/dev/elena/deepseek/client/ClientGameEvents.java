package dev.elena.deepseek.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.elena.deepseek.DeepSeekMod;
import dev.elena.deepseek.entity.CompanionEntity;
import dev.elena.deepseek.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatPreviewStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 仅客户端：
 * 1. 左 Shift + 右键 DeepSeek = 打开随身背包（潜行键 Elena 绑的是 Ctrl，服务端看不到左 Shift，读原始键盘并发包）。
 * 2. 登录时自动关闭"聊天预览"——1.19.2 的预览包会把没发送出去的消息逐键发给服务端，
 *    严重污染 AI 对话上下文，必须从源头掐掉。
 */
@Mod.EventBusSubscriber(modid = DeepSeekMod.MODID, value = Dist.CLIENT)
public class ClientGameEvents {

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.chatPreview().get() != ChatPreviewStatus.OFF) {
            mc.options.chatPreview().set(ChatPreviewStatus.OFF);
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(
                        "[DeepSeek] 已自动关闭聊天预览（防止没发送出去的消息进入 AI 上下文）")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof CompanionEntity)) return;
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (!InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)) return;

        // 拦下这次右键（不再触发交付/打招呼），改为请求服务端开背包
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Network.INSTANCE.sendToServer(new Network.OpenBagPacket());
    }
}
