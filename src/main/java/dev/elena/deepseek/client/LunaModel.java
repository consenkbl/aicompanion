package dev.elena.deepseek.client;

import dev.elena.deepseek.entity.CompanionEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * 露娜的姿势层：在原版玩家动画之上叠加状态姿势。
 * 优先级：睡觉 > 挥手 > 挨打乱跑 > 建造敲方块。
 */
public class LunaModel extends PlayerModel<CompanionEntity> {

    public LunaModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(CompanionEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.body.xRot = 0.0F;
        this.head.zRot = 0.0F;
        if (entity.isSleepingPose()) {
            // 鸭子坐打盹：两腿向前岔开、身体下沉、手臂搭在腿间、头低下去
            this.head.xRot = Mth.clamp(this.head.xRot + 0.65F, 0.0F, 1.4F);
            this.head.zRot = 0.0F;
            this.body.xRot = 0.12F;
            this.rightArm.xRot = -0.55F;
            this.leftArm.xRot = -0.55F;
            this.rightArm.zRot = -0.12F;
            this.leftArm.zRot = 0.12F;
            this.rightLeg.xRot = -1.45F;
            this.leftLeg.xRot = -1.45F;
            // 腿指向前方后，用 yRot 让两条腿向两侧岔开（zRot 只会让腿自转看不出分开）
            this.rightLeg.yRot = 0.45F;
            this.leftLeg.yRot = -0.45F;
        } else if (entity.isWavingPose()) {
            // 挥手打招呼：右臂举高左右摆
            this.rightArm.xRot = -2.4F;
            this.rightArm.zRot = Mth.cos(ageInTicks * 0.45F) * 0.55F - 0.4F;
        } else if (entity.isPanicPose()) {
            // 撒腿狂奔：身体前倾、双臂向后甩、头向后仰尖叫
            this.head.xRot = -0.55F;
            this.body.xRot = 0.3F;
            this.rightArm.xRot = 0.95F + Mth.sin(ageInTicks * 0.8F) * 0.3F;
            this.leftArm.xRot = 0.95F - Mth.sin(ageInTicks * 0.8F) * 0.3F;
            this.rightArm.zRot = -0.15F;
            this.leftArm.zRot = 0.15F;
        } else if (entity.isBuildingPose()) {
            // 建造：右臂像锤子一样一下一下敲
            this.rightArm.xRot = -1.7F + Mth.sin(ageInTicks * 0.55F) * 0.75F;
        }
        // 刘海（帽子层）跟随头部角度，否则低头/仰头时会和头分离
        this.hat.copyFrom(this.head);
    }
}
