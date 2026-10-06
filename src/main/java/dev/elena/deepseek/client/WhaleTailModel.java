package dev.elena.deepseek.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * 鲸鱼尾巴 v2：两段式，带真实弧度（根段后仰 → 末段继续弯 → 水平尾鳍展开）。
 * 模型正面是 -Z，所以尾巴挂在 +Z（背后），xRot 为负让尾尖向后上方卷。
 * 贴图 luna_tail.png (32x32)，深蓝色系。
 */
public class WhaleTailModel {

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // 根段：从腰后垂下，向后偏 29°（弧度向下）
        PartDefinition tail = root.addOrReplaceChild("tail",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, 11.5F, 2.6F, 0.5F, 0.0F, 0.0F));
        // 末段：继续向后下弯 32°
        PartDefinition tip = tail.addOrReplaceChild("tip",
                CubeListBuilder.create()
                        .texOffs(0, 8)
                        .addBox(-1.25F, 0.0F, -1.0F, 2.5F, 4.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.55F, 0.0F, 0.0F));
        // 尾鳍：末端两片宽扁鳍叶
        tip.addOrReplaceChild("fluke",
                CubeListBuilder.create()
                        .texOffs(0, 15).addBox(-5.5F, 0.0F, -1.5F, 4.5F, 1.5F, 3.0F)
                        .texOffs(16, 15).addBox(1.0F, 0.0F, -1.5F, 4.5F, 1.5F, 3.0F),
                PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, 0.5F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }
}
