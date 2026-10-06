package dev.elena.deepseek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elena.deepseek.DeepSeekMod;
import dev.elena.deepseek.entity.CompanionEntity;
import dev.elena.deepseek.client.FishEarsModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 用玩家模型渲染（Elena 下载的 DeepSeek 女仆装皮肤）+ 一条会摆动的 3D 鲸尾。 */
public class CompanionRenderer extends MobRenderer<CompanionEntity, LunaModel> {
    private static final ResourceLocation SKIN =
            new ResourceLocation(DeepSeekMod.MODID, "textures/entity/luna.png");
    private static final ResourceLocation SLEEP_SKIN =
            new ResourceLocation(DeepSeekMod.MODID, "textures/entity/luna_sleep.png");
    private static final ResourceLocation TAIL_TEX =
            new ResourceLocation(DeepSeekMod.MODID, "textures/entity/luna_tail.png");
    private static final ResourceLocation EARS_TEX =
            new ResourceLocation(DeepSeekMod.MODID, "textures/entity/luna_ears.png");

    private final ModelPart tail;
    private final ModelPart tip;
    private final ModelPart fluke;
    private final ModelPart ears;

    public CompanionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new LunaModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        ModelPart root = WhaleTailModel.createLayer().bakeRoot();
        this.tail = root.getChild("tail");
        this.tip = this.tail.getChild("tip");
        this.fluke = this.tip.getChild("fluke");
        this.ears = FishEarsModel.createLayer().bakeRoot().getChild("ears");
    }

    @Override
    public ResourceLocation getTextureLocation(CompanionEntity entity) {
        // 睡觉时切换成闭眼版贴图
        return entity.isSleepingPose() ? SLEEP_SKIN : SKIN;
    }

    @Override
    public void render(CompanionEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float dx = (float) (entity.getX() - entity.xo);
        float dz = (float) (entity.getZ() - entity.zo);
        float speed = Mth.sqrt(dx * dx + dz * dz);
        float t = entity.tickCount + partialTicks;

        if (entity.isSleepingPose()) {
            // 睡觉：鸭子坐贴地（身体下沉）+ 尾巴向后平摊在地上
            poseStack.translate(0.0D, -0.72D, 0.0D);
            this.tail.xRot = 1.55F;
            this.tail.yRot = Mth.sin(t * 0.04F) * 0.06F;
            this.tip.xRot = 0.05F;
            this.tip.yRot = 0.0F;
            this.fluke.yRot = 0.0F;
        } else {
            // 平时：垂尾轻摆；跑动时尾巴向后抬平（流线型不乱飘）
            float rate = 0.06F + speed * 0.15F;
            float amp = Mth.clamp(0.26F - speed * 0.4F, 0.08F, 0.26F);
            this.tail.yRot = Mth.sin(t * rate) * amp;
            this.tail.xRot = 0.5F - Math.min(speed * 1.3F, 0.45F) + Mth.sin(t * 0.07F) * 0.05F;
            this.tip.xRot = 0.55F;
            this.tip.yRot = Mth.sin(t * rate + 0.6F) * amp * 0.7F;
            this.fluke.yRot = Mth.sin(t * rate + 0.9F) * amp * 0.9F;
        }
        this.tail.y = 11.5F;

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);

        if (entity.isSleepingPose()) {
            drawZzz(entity, partialTicks, poseStack, buffer, packedLight);
        }
        // 头顶不画血量：额外生命显示在准星指着她时的 Jade 详细信息框里（JadeExtraHeartsPlugin）

        // 建造定位火把的信标光束（指向天际，建成后熄灭）
        if (entity.hasBeam()) {
            double ex = entity.xOld + (entity.getX() - entity.xOld) * partialTicks;
            double ey = entity.yOld + (entity.getY() - entity.yOld) * partialTicks;
            double ez = entity.zOld + (entity.getZ() - entity.zOld) * partialTicks;
            poseStack.pushPose();
            poseStack.translate(entity.getBeamX() + 0.5D - ex, entity.getBeamY() - ey,
                    entity.getBeamZ() + 0.5D - ez);
            net.minecraft.client.renderer.blockentity.BeaconRenderer.renderBeaconBeam(
                    poseStack, buffer, net.minecraft.client.renderer.blockentity.BeaconRenderer.BEAM_LOCATION,
                    partialTicks, 1.0F, (entity.level != null ? entity.level.getGameTime() : 0L) + (long) t,
                    0, 300, new float[]{0.55F, 0.75F, 1.0F}, 0.18F, 0.3F);
            poseStack.popPose();
        }

        // 关键：复刻原版的身体朝向变换再画尾巴，否则尾巴钉在世界坐标上，转身/奔跑时会飘到身前
        poseStack.pushPose();
        float bodyRot = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        this.setupRotations(entity, poseStack, t, bodyRot, partialTicks);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        if (entity.isSleepingPose()) {
            poseStack.translate(0.0F, 10.0F, 0.0F);   // 略微跟随下沉，让尾巴平贴在地面后方
        }
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TAIL_TEX));
        this.tail.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        // 鱼鳍耳朵：复刻身体变换后再应用头部变换，让耳朵跟着头转
        poseStack.pushPose();
        this.setupRotations(entity, poseStack, t, bodyRot, partialTicks);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        this.getModel().head.translateAndRotate(poseStack);
        VertexConsumer ve = buffer.getBuffer(RenderType.entityCutoutNoCull(EARS_TEX));
        this.ears.render(poseStack, ve, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /** 睡觉时头顶浮出三颗渐渐上升、变淡的 Z（DeepSeek 蓝紫色）。 */
    private void drawZzz(CompanionEntity entity, float partialTicks, PoseStack poseStack,
                         MultiBufferSource buffer, int packedLight) {
        float t = entity.tickCount + partialTicks;
        for (int i = 0; i < 3; i++) {
            float phase = (t / 40.0F + i * 0.37F) % 1.0F;
            float rise = phase * 0.7F;
            float drift = Mth.sin(phase * Mth.PI + i) * 0.18F;
            String s = i == 1 ? "z" : "Z";
            poseStack.pushPose();
            poseStack.translate(drift, 1.75F + rise, -0.25F);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.scale(-0.05F, -0.05F, 0.05F);
            int alpha = (int) ((1.0F - phase) * 255.0F) << 24;
            int color = alpha | 0x6A7BFF;
            this.getFont().drawInBatch(s, -this.getFont().width(s) / 2.0F, 0.0F, color, false,
                    poseStack.last().pose(), buffer, false, 0, packedLight);
            poseStack.popPose();
        }
    }
}
