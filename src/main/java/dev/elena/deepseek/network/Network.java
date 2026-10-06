package dev.elena.deepseek.network;

import dev.elena.deepseek.DeepSeekMod;
import dev.elena.deepseek.entity.CompanionEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端→服务端网络通道：检测"左Shift+右键"后请求打开 DeepSeek 的随身背包。
 * 服务端只认潜行键（Elena 绑定的是 Ctrl），左 Shift 只能客户端自己读。
 */
public class Network {
    private static final String VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DeepSeekMod.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static int id = 0;

    public static void register() {
        INSTANCE.registerMessage(id++, OpenBagPacket.class,
                OpenBagPacket::encode, OpenBagPacket::decode, OpenBagPacket::handle);
    }

    /** 打开背包请求（无字段）。 */
    public static class OpenBagPacket {
        public OpenBagPacket() {
        }

        public static void encode(OpenBagPacket packet, FriendlyByteBuf buf) {
        }

        public static OpenBagPacket decode(FriendlyByteBuf buf) {
            return new OpenBagPacket();
        }

        public static void handle(OpenBagPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                CompanionEntity c = nearestOwned(player);
                if (c == null) return;
                player.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x6, id, inv, c.getInventory(), 6),
                        Component.literal(c.getName().getString() + " 的随身背包")));
            });
            ctx.get().setPacketHandled(true);
        }

        @org.jetbrains.annotations.Nullable
        private static CompanionEntity nearestOwned(ServerPlayer player) {
            List<CompanionEntity> list = player.getLevel().getEntitiesOfClass(CompanionEntity.class,
                    player.getBoundingBox().inflate(6.0D));
            CompanionEntity best = null;
            for (CompanionEntity c : list) {
                boolean owner = player.getUUID().equals(c.getOwnerId());
                if (!owner && !player.hasPermissions(2)) continue;
                if (best == null || c.distanceToSqr(player) < best.distanceToSqr(player)) best = c;
            }
            return best;
        }
    }
}
