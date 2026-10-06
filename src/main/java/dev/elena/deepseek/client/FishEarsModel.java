package dev.elena.deepseek.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 鲸鳍侧耳（缩小版，约为原设计的一半）：位置不变（x ±3.5、y -4.5 眼高），角度/形状不变，
 * 长/厚/段间距×0.5，鳍面高度再加宽 1/4（更肥润）。三段逐级下弯（zRot 0.15 → +0.3 → +0.3），
 * 深蓝鳍面+白色鳍缘，随头部转动。
 * 注意：左鳍的段盒子沿 -x 延伸（与右鳍真正镜像）。
 */
public class FishEarsModel {

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition ears = root.addOrReplaceChild("ears", CubeListBuilder.create(), PartPose.ZERO);
        // 右鳍：基段外伸 → 中段下弯 → 鳍尖再下弯（均缩小一半）
        PartDefinition right = ears.addOrReplaceChild("right", CubeListBuilder.create()
                .texOffs(0, 8)
                .addBox(0.0F, -0.9375F, -0.25F, 1.5F, 1.875F, 0.5F),
                PartPose.offsetAndRotation(3.5F, -4.5F, 0.0F, 0.0F, 0.0F, 0.15F));
        PartDefinition rightMid = right.addOrReplaceChild("mid", CubeListBuilder.create()
                .texOffs(8, 8)
                .addBox(0.0F, -0.625F, -0.25F, 1.5F, 1.25F, 0.5F),
                PartPose.offsetAndRotation(1.5F, 0.25F, 0.0F, 0.0F, 0.0F, 0.3F));
        rightMid.addOrReplaceChild("tip", CubeListBuilder.create()
                .texOffs(0, 12)
                .addBox(0.0F, -0.3125F, -0.25F, 1.0F, 0.625F, 0.5F),
                PartPose.offsetAndRotation(1.5F, 0.25F, 0.0F, 0.0F, 0.0F, 0.3F));
        // 左鳍镜像：盒子沿 -x 延伸，zRot 取反
        PartDefinition left = ears.addOrReplaceChild("left", CubeListBuilder.create()
                .texOffs(16, 8)
                .addBox(-1.5F, -0.9375F, -0.25F, 1.5F, 1.875F, 0.5F),
                PartPose.offsetAndRotation(-3.5F, -4.5F, 0.0F, 0.0F, 0.0F, -0.15F));
        PartDefinition leftMid = left.addOrReplaceChild("mid", CubeListBuilder.create()
                .texOffs(24, 8)
                .addBox(-1.5F, -0.625F, -0.25F, 1.5F, 1.25F, 0.5F),
                PartPose.offsetAndRotation(-1.5F, 0.25F, 0.0F, 0.0F, 0.0F, -0.3F));
        leftMid.addOrReplaceChild("tip", CubeListBuilder.create()
                .texOffs(16, 12)
                .addBox(-1.0F, -0.3125F, -0.25F, 1.0F, 0.625F, 0.5F),
                PartPose.offsetAndRotation(-1.5F, 0.25F, 0.0F, 0.0F, 0.0F, -0.3F));
        return LayerDefinition.create(mesh, 32, 32);
    }
}
