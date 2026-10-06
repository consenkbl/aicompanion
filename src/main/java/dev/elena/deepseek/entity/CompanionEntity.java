package dev.elena.deepseek.entity;

import dev.elena.deepseek.ai.AIReply;
import dev.elena.deepseek.ai.ChatService;
import dev.elena.deepseek.task.BuildTask;
import dev.elena.deepseek.task.OrganizeTask;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import dev.elena.deepseek.DeepSeekGson;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CompanionEntity extends PathfinderMob {
    public enum Mode {FOLLOW, STAY, WANDER}

    @Nullable
    private UUID ownerId;
    private Mode mode = Mode.FOLLOW;
    /** 随身背包（54 格，大箱子容量；Shift+右键打开；交付的建造材料也存这里） */
    private final net.minecraft.world.SimpleContainer inventory = new net.minecraft.world.SimpleContainer(54);
    @Nullable
    private BuildTask buildTask;
    @Nullable
    private OrganizeTask organizeTask;
    @Nullable
    private dev.elena.deepseek.task.FishingTask fishingTask;
    @Nullable
    private dev.elena.deepseek.task.FarmingTask farmingTask;
    @Nullable
    private dev.elena.deepseek.task.CuriosityTask curiosityTask;
    private boolean aiBusy = false;
    private boolean workingArm = false;

    // ---- 表现状态（同步到客户端供渲染用）----
    private static final EntityDataAccessor<Boolean> DATA_SLEEPING =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WAVING =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BUILDING =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_PANIC =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BEAM =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BEAM_X =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BEAM_Y =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BEAM_Z =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    /** 已喂鱼数（= 额外生命数），同步到客户端供头顶黄心渲染 */
    private static final EntityDataAccessor<Integer> DATA_FISH_FED =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);

    private String lastUserMessage = "";
    private int waveTicks = 0;
    private int panicTicks = 0;
    private int idleTicks = 0;
    private int ownerAwayTicks = 0;
    /** 随身背包（Shift+右键打开，18 格） */
    /** 打字防抖队列：[ServerPlayer, String, Long 戳] —— 1.19.2 聊天预览会按键连发，停顿后才提交 */
    private final List<Object[]> pendingChats = new ArrayList<>();

    public CompanionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER, -1.0F);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SLEEPING, false);
        this.entityData.define(DATA_WAVING, false);
        this.entityData.define(DATA_BUILDING, false);
        this.entityData.define(DATA_PANIC, false);
        this.entityData.define(DATA_BEAM, false);
        this.entityData.define(DATA_BEAM_X, 0);
        this.entityData.define(DATA_BEAM_Y, 0);
        this.entityData.define(DATA_BEAM_Z, 0);
        this.entityData.define(DATA_FISH_FED, 0);
    }

    public boolean isSleepingPose() {
        return this.entityData.get(DATA_SLEEPING);
    }

    public boolean isWavingPose() {
        return this.entityData.get(DATA_WAVING);
    }

    public boolean isBuildingPose() {
        return this.entityData.get(DATA_BUILDING);
    }

    public boolean isPanicPose() {
        return this.entityData.get(DATA_PANIC);
    }

    private void setFlag(EntityDataAccessor<Boolean> flag, boolean value) {
        if (this.entityData.get(flag) != value) this.entityData.set(flag, value);
    }

    public void startWave() {
        waveTicks = 60;
        idleTicks = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new CryPanicGoal());
        this.goalSelector.addGoal(2, new FollowGoal());
        this.goalSelector.addGoal(3, new StrollGoal());
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() {
                return !isSleepingPose() && super.canUse();
            }
        });
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return !isSleepingPose() && super.canUse();
            }
        });
    }

    /** 挨打后抱头乱跑。 */
    private class CryPanicGoal extends PanicGoal {
        CryPanicGoal() {
            super(CompanionEntity.this, 1.45D);
        }

        @Override
        public boolean shouldPanic() {
            return panicTicks > 0;
        }
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag nbt) {
        if (this.getCustomName() == null) {
            this.setCustomName(Component.literal("DeepSeek"));
            this.setCustomNameVisible(true);
        }
        return super.finalizeSpawn(level, difficulty, reason, data, nbt);
    }

    // ------------------------------------------------------------------ 行为

    private class FollowGoal extends Goal {
        @Override
        public boolean canUse() {
            if (mode != Mode.FOLLOW || isTaskBusy() || panicTicks > 0 || isSleepingPose()) return false;
            Player o = ownerPlayer();
            return o != null && o.level.dimension().equals(level.dimension()) && distanceToSqr(o) > 25.0D;
        }

        @Override
        public boolean canContinueToUse() {
            Player o = ownerPlayer();
            return o != null && mode == Mode.FOLLOW && !isTaskBusy()
                    && distanceToSqr(o) > 16.0D && !getNavigation().isDone();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Player o = ownerPlayer();
            if (o == null) return;
            double d = distanceToSqr(o);
            getLookControl().setLookAt(o, 30.0F, 30.0F);
            if (d > 900.0D) {
                randomTeleport(o.getX() + 1, o.getY(), o.getZ() + 1, false);
            } else if (d > 25.0D && getNavigation().isDone()) {
                getNavigation().moveTo(o, 1.12D);
            }
        }
    }

    private class StrollGoal extends RandomStrollGoal {
        StrollGoal() {
            super(CompanionEntity.this, 0.9D, 80);
        }

        @Override
        public boolean canUse() {
            return mode == Mode.WANDER && !isTaskBusy() && panicTicks <= 0 && !isSleepingPose() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return mode == Mode.WANDER && !isTaskBusy() && panicTicks <= 0 && super.canContinueToUse();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) return;
        // 额外生命是永久的：吸收心被消耗后每秒回满（保持=喂鱼数×1心）
        int fed = getFishFed();
        if (fed > 0 && tickCount % 20 == 0 && getAbsorptionAmount() < fed * 2.0F) {
            setAbsorptionAmount(fed * 2.0F);
        }
        if (tickCount % 10 == 0) pickupNearbyItems();
        if (buildTask != null) buildTask.tick();
        if (organizeTask != null) organizeTask.tick();
        if (fishingTask != null) fishingTask.tick();
        if (farmingTask != null) farmingTask.tick();
        if (curiosityTask != null) curiosityTask.tick();

        if (waveTicks > 0) waveTicks--;
        if (panicTicks > 0) panicTicks--;

        // 打字防抖：消息停顿 0.8 秒后才真正提交给 AI（忙时静默丢弃，不刷屏）
        long now = System.currentTimeMillis();
        Iterator<Object[]> pit = pendingChats.iterator();
        while (pit.hasNext()) {
            Object[] p = pit.next();
            if (now - (Long) p[2] >= 800) {
                pit.remove();
                if (!aiBusy) ChatService.submit(this, (ServerPlayer) p[0], (String) p[1]);
            }
        }

        // 主人远行归来 → 挥手欢迎
        if (mode == Mode.FOLLOW && ownerId != null) {
            Player o = ownerPlayer();
            if (o != null) {
                double d2 = distanceToSqr(o);
                if (d2 > 144.0D) {
                    ownerAwayTicks++;
                } else if (d2 < 25.0D) {
                    if (ownerAwayTicks > 100) {
                        startWave();
                        chatSay("你回来啦！我在这儿等你好久了~");
                    }
                    ownerAwayTicks = 0;
                }
            }
        }

        // 发呆计数：常态下（没活干、没走路、没事情）静止 3 秒就坐下睡觉，不分模式
        boolean busy = buildTask != null || organizeTask != null || aiBusy
                || panicTicks > 0 || waveTicks > 0 || !getNavigation().isDone();
        if (busy) {
            idleTicks = 0;
        } else {
            idleTicks++;
        }
        boolean sleeping = !busy && idleTicks > 60;
        // 跟随模式下主人走远了 → 醒来跟上
        if (sleeping && mode == Mode.FOLLOW && ownerId != null) {
            Player o = ownerPlayer();
            if (o != null && distanceToSqr(o) > 25.0D) {
                idleTicks = 0;
                sleeping = false;
            }
        }
        setFlag(DATA_SLEEPING, sleeping);

        // 睡觉时头顶的 ZZZ 由客户端渲染器绘制（ClientRenderer.drawZzz）
        // 挨打后流眼泪（眼睛两侧滴落水珠）
        if (panicTicks > 0 && panicTicks % 3 == 0) {
            double yawRad = Math.toRadians(getYRot());
            double ox = -Math.cos(yawRad), oz = -Math.sin(yawRad);
            for (int side = -1; side <= 1; side += 2) {
                ((ServerLevel) level).sendParticles(ParticleTypes.FALLING_WATER,
                        getX() + ox * side * 0.18D, getY() + 1.55D, getZ() + oz * side * 0.18D,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        setFlag(DATA_WAVING, waveTicks > 0);
        setFlag(DATA_PANIC, panicTicks > 0);
        setFlag(DATA_BUILDING, (buildTask != null && buildTask.isPlacing()) || workingArm);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (!level.isClientSide && result) {
            panicTicks = 100;
            idleTicks = 0;
            chatSay("呜哇——！吓死我了！");
        }
        return result;
    }

    // ------------------------------------------------------------------ 交互

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Shift+空手右键：切换模式
        if (player.isShiftKeyDown() && stack.isEmpty()) {
            if (!level.isClientSide) cycleMode();
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // Shift+拿东西右键：打开随身背包（54 格）
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inv, p) -> new net.minecraft.world.inventory.ChestMenu(
                                net.minecraft.world.inventory.MenuType.GENERIC_9x6, id, inv, this.inventory, 6),
                        Component.literal(getName().getString() + " 的随身背包")));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return stack.isEmpty() ? InteractionResult.PASS : InteractionResult.CONSUME;
        }
        // ---- 服务端 ----
        // 拿精灵球右键她：收回
        if (stack.getItem() instanceof dev.elena.deepseek.item.PokeBallItem) {
            return dev.elena.deepseek.item.PokeBallItem.tryCapture(player, stack, this)
                    ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        // 拿鱼右键她：喂食
        if (isFishItem(stack.getItem()) && !level.isClientSide) {
            if (ownerId == null) setOwnerId(player.getUUID());
            stack.shrink(1);
            feedFish(player);
            return InteractionResult.CONSUME;
        }
        // 拿东西右键她：交付材料（进随身背包）
        if (!stack.isEmpty()) {
            if (stack.getItem() instanceof dev.elena.deepseek.item.BuildingTorchItem) {
                chatSay("定位火把你自己拿着哦，插在哪我盖在哪~");
                return InteractionResult.CONSUME;
            }
            if (ownerId == null) setOwnerId(player.getUUID());
            ItemStack rest = addStack(stack.copy());
            player.setItemInHand(hand, rest);
            int n = stack.getCount() - rest.getCount();
            String extra = buildTask != null
                    ? buildTask.escrowChanged(player instanceof ServerPlayer sp ? sp : null) : "";
            if (n <= 0) chatSay("我吃饱了，装不下了！" + extra);
            else if (!rest.isEmpty()) chatSay("只装下了 " + stackName(stack.getItem()) + "×" + n + "，我吃饱啦！" + extra);
            else chatSay("收到 " + stackName(stack.getItem()) + "×" + n + "！" + extra);
            return InteractionResult.CONSUME;
        }
        // 空手普通右键：打招呼
        startWave();
        chatSay(pokeLine());
        return InteractionResult.CONSUME;
    }

    private void pickupNearbyItems() {
        if (buildTask == null || !buildTask.waitingForMaterials()) return;
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                getBoundingBox().inflate(2.5D), ItemEntity::isAlive);
        if (items.isEmpty()) return;
        boolean took = false, full = false;
        for (ItemEntity ie : items) {
            ItemStack rest = addStack(ie.getItem());
            if (rest.isEmpty()) {
                ie.discard();
                took = true;
            } else {
                ie.setItem(rest);
                full = true;
            }
        }
        if (took) {
            String extra = buildTask.escrowChanged(null);
            chatSay("脚边的东西我收啦！" + extra);
        } else if (full && tickCount % 100 == 0) {
            chatSay("我吃饱了，装不下了！");
        }
    }

    // ------------------------------------------------------------------ 对话

    public void onPlayerChat(ServerPlayer player, String message) {
        idleTicks = 0;
        lastUserMessage = message;
        if (ownerId == null) {
            setOwnerId(player.getUUID());
            chatSay("初次见面！我是" + getName().getString() + "，以后就跟着你啦。");
        }
        long now = System.currentTimeMillis();
        // 打字防抖：新消息是上一条尚未提交消息的延续（打字预览）时，替换而不是新增
        if (!pendingChats.isEmpty()) {
            Object[] last = pendingChats.get(pendingChats.size() - 1);
            if (message.startsWith((String) last[1])) {
                last[0] = player;
                last[1] = message;
                last[2] = now;
                return;
            }
        }
        pendingChats.add(new Object[]{player, message, now});
    }

    public void handleAIReply(@Nullable ServerPlayer target, @Nullable AIReply reply) {
        aiBusy = false;
        if (reply == null) {
            chatSay("脑子突然一片空白…你能再说一遍吗？");
            return;
        }
        if (reply.say != null && !reply.say.isBlank()) chatSay(reply.say);
        if (reply.actions != null) {
            for (AIReply.Action a : reply.actions) {
                if (a != null && a.type != null) {
                    applyAction(target, a);
                }
            }
        }
        // 关键词兜底：始终检查原消息，确保简单指令不因 AI 失误而遗漏
        if (lastUserMessage != null && !lastUserMessage.isBlank()) {
            AIReply fallback = dev.elena.deepseek.ai.OfflineBrains.reply(this, lastUserMessage);
            if (fallback.actions != null) {
                for (AIReply.Action a : fallback.actions) {
                    if (a != null && a.type != null) applyAction(target, a);
                }
            }
        }
    }

    private void applyAction(@Nullable ServerPlayer target, AIReply.Action a) {
        dev.elena.deepseek.ai.ActionRegistry.dispatch(this, target, a);
    }

    /** 给 LLM 的现场状态摘要（主线程构建，避免异步读实体）。 */
    public String buildAIContext(ServerPlayer player) {
        StringBuilder sb = new StringBuilder();
        sb.append("玩家名字：").append(player.getName().getString()).append("。");
        sb.append("你的模式：").append(modeName()).append("。");
        sb.append("你的生命：").append((int) getHealth()).append("/").append((int) getMaxHealth()).append("。");
        if (buildTask != null) sb.append("你正在：").append(buildTask.describe()).append("。");
        else if (organizeTask != null) sb.append("你正在整理箱子。");
        String bag = inventorySummary();
        if (!bag.isEmpty()) sb.append("你的背包里有：").append(bag).append("。");
        sb.append("维度：").append(level.dimension().location()).append("。");
        return sb.toString();
    }

    public String statusLine() {
        String s = "模式 " + modeName() + "，血量 " + (int) getHealth() + "/" + (int) getMaxHealth();
        if (buildTask != null) s += "，正在" + buildTask.describe();
        String esc = escrowSummary();
        if (!esc.isEmpty()) s += "；保管的材料：" + esc;
        return s + "。";
    }

    // ------------------------------------------------------------------ 任务

    public void startBuild(String structureId, @Nullable ServerPlayer player) {
        if (isTaskBusy()) {
            chatSay("等一下，我手头还有活没干完呢~");
            return;
        }
        dev.elena.deepseek.task.StructureTemplate t = dev.elena.deepseek.task.StructureTemplates.get(structureId);
        if (t == null) {
            chatSay("这个我还不会盖…我会盖：小木屋、瞭望塔、篝火营地。想要别的建筑，跟我说说它长什么样，我来设计！");
            return;
        }
        buildTask = new dev.elena.deepseek.task.BuildTask(this, t, structureId);
        buildTask.begin(player);
        idleTicks = 0;
    }

    /** 智能建造：关键词精确命中 → 直接建；否则 API 语义匹配图纸库；匹配不到 → AI 现设计。 */
    public void startSmartBuild(String name, @Nullable String brief, @Nullable ServerPlayer player) {
        if (name == null || name.isBlank()) {
            chatSay("你想让我盖什么呀？说说建筑的名字~");
            return;
        }
        if (!canStartNewTask()) {
            chatSay("等一下，我手头还有活没干完呢~");
            return;
        }
        dropUncommittedBuild();
        if (name.startsWith("自定义")) {
            startDesignBuild(name.replace("自定义·", "").replace("自定义", ""), brief, player);
            return;
        }
        // 关键词精确匹配（名称/标签完全一致才走快速通道）
        java.util.List<String> exact = dev.elena.deepseek.task.StructureTemplates
                .findCandidates(name, null, 3, 90);
        if (!exact.isEmpty()) {
            startBuildWithCandidates(exact.get(0), exact, name, brief, player);
            return;
        }
        // 模糊匹配需要 API 语义理解
        if (dev.elena.deepseek.ai.Config.apiKey().isBlank()) {
            chatSay("图纸库里没有现成的「" + name + "」，离线模式我也不会画图纸…填上 API Key 我就能自己设计了！");
            return;
        }
        chatSay("「" + name + "」？让我想想图纸库里哪张最合适…");
        dev.elena.deepseek.ai.MatchService.requestMatch(this, name, brief == null ? "" : brief, null, ids -> {
            if (ids == null) {
                chatSay("图纸匹配出了点问题…再说一次？");
                return;
            }
            if (ids.isEmpty()) {
                chatSay("图纸库里没有合适的，我自己来设计「" + name + "」的蓝图！");
                startDesignBuild(name, brief, player);
                return;
            }
            startBuildWithCandidates(ids.get(0), ids, name, brief, player);
        });
    }

    public void startBuildWithCandidates(String id, java.util.List<String> candidates,
                                          String reqName, String reqBrief, @Nullable ServerPlayer player) {
        if (!canStartNewTask()) {
            chatSay("等一下，我手头还有活没干完呢~");
            return;
        }
        dev.elena.deepseek.task.StructureTemplate t = dev.elena.deepseek.task.StructureTemplates.get(id);
        if (t == null) {
            chatSay("图纸丢了…再说一次？");
            return;
        }
        buildTask = new dev.elena.deepseek.task.BuildTask(this, t, id);
        buildTask.setCandidates(candidates);
        buildTask.setRequestInfo(reqName, reqBrief);
        buildTask.begin(player);
        idleTicks = 0;
    }

    /** AI 自主设计建筑：让大模型画蓝图 → 保存为模板 → 走正常建造流程。 */
    public void startDesignBuild(String name, String brief, @Nullable ServerPlayer player) {
        if (aiBusy) {
            chatSay("（图纸还在画呢…稍等）");
            return;
        }
        chatSay("「" + name + "」？好大的口气…让我先画画图纸！");
        dev.elena.deepseek.task.AiArchitect.requestDesign(this, player, name, brief);
    }

    /** 玩家放置了建筑定位火把 → 交给当前建造任务。 */
    public void onTorchPlaced(BlockPos pos) {
        if (buildTask != null) {
            buildTask.onTorchPlaced(pos);
        } else {
            chatSay("现在没有待开工的建筑哦，火把就先插在那儿吧~");
        }
    }

    public void startOrganize() {
        if (!canStartNewTask()) {
            chatSay("等一下，我手头还有活没干完呢~");
            return;
        }
        dropUncommittedBuild();
        organizeTask = new OrganizeTask(this);
        organizeTask.begin();
        idleTicks = 0;
    }

    public void cancelTasks() {
        if (buildTask != null) {
            buildTask.cancel();
            buildTask = null;
        }
        if (organizeTask != null) {
            organizeTask.cancel();
            organizeTask = null;
        }
        if (fishingTask != null) {
            fishingTask.cancel();
            fishingTask = null;
        }
        if (farmingTask != null) {
            farmingTask.cancel();
            farmingTask = null;
        }
        if (curiosityTask != null) {
            curiosityTask.cancel();
            curiosityTask = null;
        }
        setWorkingArm(false);
    }

    public void clearBuildTask() {
        buildTask = null;
    }

    public void clearOrganizeTask() {
        organizeTask = null;
    }

    public void clearFishingTask() {
        setWorkingArm(false);
        fishingTask = null;
    }

    public void clearFarmingTask() {
        farmingTask = null;
    }

    public void startFishing() {
        if (!canStartNewTask()) {
            chatSay("等一下，我手头还有活没干完呢~");
            return;
        }
        dropUncommittedBuild();
        fishingTask = new dev.elena.deepseek.task.FishingTask(this);
        fishingTask.begin();
        idleTicks = 0;
    }

    public void startFarming() {
        if (!canStartNewTask()) {
            chatSay("等一下，我手头还有活没干完呢~");
            return;
        }
        dropUncommittedBuild();
        farmingTask = new dev.elena.deepseek.task.FarmingTask(this);
        farmingTask.begin();
        idleTicks = 0;
    }

    public void startCuriosity() {
        if (!canStartNewTask()) {
            chatSay("等一下，我手头还有活没干完呢~");
            return;
        }
        dropUncommittedBuild();
        curiosityTask = new dev.elena.deepseek.task.CuriosityTask(this);
        curiosityTask.begin();
        idleTicks = 0;
    }

    public void clearCuriosityTask() {
        curiosityTask = null;
    }

    /** 找"类似材料"：背包里有没有同组的替代品（木板换木板、石制换石制…）。 */
    @Nullable
    public Item findSubstitute(Item needed) {
        java.util.List<Item> group = dev.elena.deepseek.task.MaterialSubs.groupOf(needed);
        if (group == null) return null;
        for (Item alt : group) {
            if (alt != needed && escrowCount(alt) > 0) return alt;
        }
        return null;
    }

    public void setWorkingArm(boolean b) {
        this.workingArm = b;
    }

    private static boolean isFishItem(Item item) {
        return item == net.minecraft.world.item.Items.COD
            || item == net.minecraft.world.item.Items.SALMON
            || item == net.minecraft.world.item.Items.TROPICAL_FISH;
    }

    /** 喂鱼：永久额外生命 +1 心（吸收心），不改变生命上限。 */
    private void feedFish(Player player) {
        int fed = getFishFed() + 1;
        this.entityData.set(DATA_FISH_FED, fed);
        setAbsorptionAmount(fed * 2.0F);
        // 反馈
        playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 0.8F, 1.0F);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                    getX(), getY() + 1.5D, getZ(), 5, 0.3D, 0.3D, 0.3D, 0.0D);
        }
        if (fed == 1) {
            chatSay("呜…好吃。才、才不是特意为你做的呢！（额外生命 +1 心）");
        } else {
            chatSay("好吃！（额外生命 " + fed + " 心，共 " + fed + " 条鱼）");
        }
    }

    /** 建造定位火把的光束锚点（客户端画信标光束用）。 */
    public void setBeamAnchor(@Nullable BlockPos pos) {
        if (pos == null) {
            this.entityData.set(DATA_BEAM, false);
        } else {
            this.entityData.set(DATA_BEAM_X, pos.getX());
            this.entityData.set(DATA_BEAM_Y, pos.getY());
            this.entityData.set(DATA_BEAM_Z, pos.getZ());
            this.entityData.set(DATA_BEAM, true);
        }
    }

    public int getFishFed() {
        return this.entityData.get(DATA_FISH_FED);
    }

    public boolean hasBeam() {
        return this.entityData.get(DATA_BEAM);
    }

    public int getBeamX() { return this.entityData.get(DATA_BEAM_X); }
    public int getBeamY() { return this.entityData.get(DATA_BEAM_Y); }
    public int getBeamZ() { return this.entityData.get(DATA_BEAM_Z); }

    public boolean isTaskBusy() {
        return buildTask != null || organizeTask != null || fishingTask != null || farmingTask != null
                || curiosityTask != null;
    }

    /** 是否可以开始新任务：建造任务在光束定位前随时可被替换。 */
    public boolean canStartNewTask() {
        if (organizeTask != null || fishingTask != null || farmingTask != null || curiosityTask != null) {
            return false;
        }
        return buildTask == null || !buildTask.isCommitted();
    }

    private void dropUncommittedBuild() {
        if (buildTask != null && !buildTask.isCommitted()) {
            buildTask = null;
        }
    }

    /** 是否可以开始新任务：建造任务在插火把开工前随时可被替换（Elena 要求）。 */

    /** 丢弃未开工的建造任务（材料本来就在背包里，无损）。 */

    @Nullable
    public BuildTask getBuildTask() {
        return buildTask;
    }

    public void setAiBusy(boolean busy) {
        this.aiBusy = busy;
    }

    /** 随身背包（网络包开 GUI 用）。 */
    public net.minecraft.world.Container getInventory() {
        return inventory;
    }

    // ------------------------------------------------------------------ 随身背包（材料也存这里）

    public int escrowCount(Item item) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && s.getItem() == item) n += s.getCount();
        }
        return n;
    }

    public void takeEscrow(Item item, int count) {
        for (int i = 0; i < inventory.getContainerSize() && count > 0; i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && s.getItem() == item) {
                int take = Math.min(s.getCount(), count);
                s.shrink(take);
                if (s.isEmpty()) inventory.setItem(i, ItemStack.EMPTY);
                count -= take;
            }
        }
    }

    public String inventorySummary() {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty()) parts.add(stackName(s.getItem()) + "×" + s.getCount());
        }
        return String.join("、", parts);
    }

    public String escrowSummary() {
        Map<Item, Integer> pool = escrowCopy();
        if (pool.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Item, Integer> e : pool.entrySet()) {
            if (sb.length() > 0) sb.append("、");
            sb.append(stackName(e.getKey())).append("×").append(e.getValue());
        }
        return sb.toString();
    }

    /** 背包材料快照（供建造任务做合成推演）。 */
    public Map<Item, Integer> escrowCopy() {
        Map<Item, Integer> pool = new HashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty()) pool.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return pool;
    }

    /** 把合成推演结果写回背包（槽位会重排，相当于她自己整理了一遍）。 */
    private void writeBackInventory(Map<Item, Integer> pool) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : pool.entrySet()) {
            int left = e.getValue();
            while (left > 0) {
                int n = Math.min(left, e.getKey().getMaxStackSize());
                stacks.add(new ItemStack(e.getKey(), n));
                left -= n;
            }
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) inventory.setItem(i, ItemStack.EMPTY);
        int slot = 0;
        for (ItemStack s : stacks) {
            if (slot >= inventory.getContainerSize()) {
                this.spawnAtLocation(s);
                continue;
            }
            inventory.setItem(slot++, s);
        }
    }

    /** 用背包里的材料合成 item×count（会消耗原材料，比如原木加工成木板），成功返回 true。 */
    public boolean tryCraftFor(Item item, int count) {
        Map<Item, Integer> pool = escrowCopy();
        if (!dev.elena.deepseek.task.CraftingBook.craft(item, count, pool, 0)) return false;
        writeBackInventory(pool);
        return true;
    }

    /** 往背包塞东西，返回塞不下的剩余部分。 */
    public ItemStack addStack(ItemStack stack) {
        for (int i = 0; i < inventory.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, stack)) {
                int move = Math.min(s.getMaxStackSize() - s.getCount(), stack.getCount());
                if (move > 0) {
                    s.grow(move);
                    stack.shrink(move);
                }
            }
        }
        for (int i = 0; i < inventory.getContainerSize() && !stack.isEmpty(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                inventory.setItem(i, stack.split(Math.min(stack.getMaxStackSize(), stack.getCount())));
            }
        }
        return stack;
    }

    // ------------------------------------------------------------------ 杂项

    public void chatSay(String text) {
        if (level.isClientSide || text == null || text.isBlank()) return;
        Component line = Component.literal("[" + getName().getString() + "] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        if (level instanceof ServerLevel sl) {
            for (ServerPlayer sp : sl.players()) {
                if (sp.distanceToSqr(this) < 4096.0D) sp.sendSystemMessage(line);
            }
        }
    }

    @Nullable
    private Player ownerPlayer() {
        if (ownerId == null || level.isClientSide) return null;
        if (level.getServer() == null) return null;
        return level.getServer().getPlayerList().getPlayer(ownerId);
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID id) {
        this.ownerId = id;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode m) {
        this.mode = m;
    }

    private void cycleMode() {
        switchMode(switch (mode) {
            case FOLLOW -> Mode.STAY;
            case STAY -> Mode.WANDER;
            case WANDER -> Mode.FOLLOW;
        });
        chatSay(switch (mode) {
            case FOLLOW -> "好，我跟着你走！（跟随模式）";
            case STAY -> "好，我就在这儿等你。（停留模式）";
            case WANDER -> "那我在这附近转转~（游走模式）";
        });
    }

    /** 切换模式：立刻醒来、停止当前寻路，保证切换马上生效。 */
    public void switchMode(Mode m) {
        setMode(m);
        idleTicks = 0;
        setFlag(DATA_SLEEPING, false);
        getNavigation().stop();
    }

    public String modeName() {
        return switch (mode) {
            case FOLLOW -> "跟随";
            case STAY -> "停留";
            case WANDER -> "游走";
        };
    }

    @Nullable
    public Mode parseMode(@Nullable String s) {
        if (s == null) return null;
        return switch (s.toLowerCase()) {
            case "follow", "follow_player", "follow_player_goal" -> Mode.FOLLOW;
            case "stay", "stay_here", "stand_still", "hold_position", "sit" -> Mode.STAY;
            case "wander", "wander_around", "free_roam", "roam" -> Mode.WANDER;
            default -> null;
        };
    }

    private String stackName(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    private String pokeLine() {
        String[] lines = {
                "干嘛戳我~有事就说！",
                "在呢在呢！",
                "偷偷告诉你，按住 Shift 右键我可以切换模式哦。",
                "今天想让我干点啥？",
                "盖房子、整理箱子、还是就聊聊天？"
        };
        return lines[random.nextInt(lines.length)];
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty()) {
                this.spawnAtLocation(s);
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putString("ACMode", mode.name());
        tag.put("ACInv", inventory.createTag());
        tag.putInt("FishFed", getFishFed());
        if (buildTask != null) tag.putString("ACBuild", buildTask.serialize());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        inventory.fromTag(tag.getList("ACInv", net.minecraft.nbt.Tag.TAG_COMPOUND));
        int fed = tag.getInt("FishFed");
        if (fed > 0) {
            this.entityData.set(DATA_FISH_FED, fed);
            setAbsorptionAmount(fed * 2.0F);
        }
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        try {
            mode = Mode.valueOf(tag.getString("ACMode"));
        } catch (Exception ignored) {
        }
        if (tag.contains("ACBuild")) buildTask = BuildTask.deserialize(this, tag.getString("ACBuild"));
    }
}
