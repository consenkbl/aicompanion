package dev.elena.deepseek.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.elena.deepseek.DeepSeekMod;
import dev.elena.deepseek.entity.CompanionEntity;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;
import snownee.jade.overlay.DisplayHelper;
import snownee.jade.overlay.OverlayRenderer;

/**
 * Jade 详细信息框插件：准星指着露娜时，在原版红心后面追加黄色爱心（喂鱼获得的永久额外生命）。
 * 黄心直接取原版 icons.png 的吸收心精灵 (u=160)，与 Jade 自己的红心渲染风格一致。
 * 装了 Jade 才会生效；没装 Jade 时本类不会被加载。
 */
@WailaPlugin
public class JadeExtraHeartsPlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration reg) {
        reg.registerEntityComponent(Provider.INSTANCE, CompanionEntity.class);
    }

    public static class Provider implements IEntityComponentProvider {
        public static final Provider INSTANCE = new Provider();

        @Override
        public ResourceLocation getUid() {
            return new ResourceLocation(DeepSeekMod.MODID, "extra_hearts");
        }

        @Override
        public int getDefaultPriority() {
            // 晚于 Jade 自带的生命显示（默认 1000 左右），保证黄心排在红心后面
            return 5000;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            int fed = accessor.getEntity() instanceof CompanionEntity c ? c.getFishFed() : 0;
            if (fed > 0) {
                tooltip.add(new YellowHeartsElement(Math.min(fed, 30)));
            }
        }
    }

    /** 一组原版样式的黄色吸收心，每行最多 10 颗，最多 3 行。 */
    public static class YellowHeartsElement extends Element {
        private final int hearts;

        public YellowHeartsElement(int hearts) {
            this.hearts = hearts;
            int rows = (hearts + 9) / 10;
            this.size = new Vec2(Math.min(hearts, 10) * 8 - 1, rows * 10 - 1);
        }

        @Override
        public Vec2 getSize() {
            return this.size;
        }

        @Override
        public void render(PoseStack poseStack, float x, float y, float width, float height) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, OverlayRenderer.alpha);
            RenderSystem.setShaderTexture(0, GuiComponent.GUI_ICONS_LOCATION);
            RenderSystem.enableBlend();
            for (int i = 0; i < hearts; i++) {
                int row = i / 10;
                int col = i % 10;
                // 注意：最后两个参数是采样区域宽高（源精灵 9x9），不是贴图总尺寸
                DisplayHelper.drawTexturedModalRect(poseStack, x + col * 8, y + row * 10,
                        160, 0, 9, 9, 9, 9);
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        @Override
        public Component getMessage() {
            return Component.literal("+" + hearts + " 额外生命");
        }
    }
}
