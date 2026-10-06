package dev.elena.deepseek.task;

import dev.elena.deepseek.entity.CompanionEntity;
import dev.elena.deepseek.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 建造任务（建筑火把流程，参考剑与王国的建筑师）：
 * 材料凑齐 → DeepSeek 给玩家建筑定位火把 → 玩家放置火把 → 以火把为中心建造。
 * 火把被破坏/被建筑覆盖时任务终止；火把所在格最后才被建筑覆盖。
 * 遇到阻碍方块会直接破坏它推进工程（基岩等不可破坏的跳过）。
 */
public class BuildTask {
    private static final int PHASE_COLLECT = 0;    // 等材料
    private static final int PHASE_WAIT_TORCH = 1; // 等玩家放置火把
    private static final int PHASE_PLACING = 2;    // 建造中

    private final CompanionEntity mob;
    private StructureTemplate template;
    private String id;
    @Nullable
    private java.util.List<String> candidates;   // 换图纸用的候选列表
    private int candidateIndex = 0;
    @Nullable
    private String requestName;
    @Nullable
    private String requestBrief;
    private List<Placement> placements;
    @Nullable
    private BlockPos origin;
    @Nullable
    private BlockPos torchPos;
    private int index = 0;
    private int phase = PHASE_COLLECT;
    private int cooldown = 0;
    private int placed = 0;
    private int waitTicks = 0;
    private boolean saidSubstitute = false;
    private int lastSeenIndex = 0;
    private int stuckTicks = 0;
    @Nullable
    private ServerPlayer lastPlayer;

    private static class Placement {
        final BlockPos pos;
        final BlockState state;

        Placement(BlockPos pos, BlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }

    public BuildTask(CompanionEntity mob, StructureTemplate template, String id) {
        this.mob = mob;
        this.template = template;
        this.id = id;
    }

    public void begin(@Nullable ServerPlayer player) {
        this.lastPlayer = player;
        this.phase = PHASE_COLLECT;
        if (canSatisfy()) {
            giveTorch(player);
        } else {
            mob.chatSay("来盖" + theName() + "！我还缺：" + format(rawNeeds())
                    + "。把东西拿着对我右键，或者丢在我脚边都行~（原始材料就行，我自己会加工）");
        }
    }

    public boolean waitingForMaterials() {
        return phase == PHASE_COLLECT;
    }

    public boolean isPlacing() {
        return phase == PHASE_PLACING;
    }

    /** 已插火把正式开工 = 任务"已承诺"，此时不能被新任务替换。 */
    public boolean isCommitted() {
        return phase == PHASE_PLACING;
    }

    public void setCandidates(java.util.List<String> cands) {
        this.candidates = cands;
        this.candidateIndex = 0;
    }

    public void setRequestInfo(String reqName, String reqBrief) {
        this.requestName = reqName;
        this.requestBrief = reqBrief;
    }

    /** 换一张候选图纸：重置建造状态并重新索料（材料保留）。 */
    public boolean nextBlueprint(@Nullable ServerPlayer player) {
        if (phase != PHASE_COLLECT || candidates == null || candidateIndex + 1 >= candidates.size()) {
            return false;
        }
        candidateIndex++;
        String nextId = candidates.get(candidateIndex);
        StructureTemplate t = StructureTemplates.get(nextId);
        if (t == null) return false;
        template = t;
        id = nextId;
        index = 0;
        placed = 0;
        torchPos = null;
        origin = null;
        placements = null;
        phase = PHASE_COLLECT;
        saidSubstitute = false;
        cooldown = 10;
        mob.setBeamAnchor(null);   // 换图纸，旧光束熄灭
        begin(player);
        return true;
    }

    /** 玩家交材料后调用，返回要追加到聊天里的提示。 */
    public String escrowChanged(@Nullable ServerPlayer player) {
        if (phase != PHASE_COLLECT) return "";
        if (canSatisfy()) {
            giveTorch(player);
            return "";
        }
        return "还缺 " + format(rawNeeds()) + "~";
    }

    /** 材料齐了：把建筑定位火把交给玩家。 */
    private void giveTorch(@Nullable ServerPlayer player) {
        phase = PHASE_WAIT_TORCH;
        if (player != null) {
            ItemStack torch = new ItemStack(ModItems.BUILDING_TORCH.get());
            if (!player.getInventory().add(torch)) player.drop(torch, false);
            mob.chatSay("材料齐了！这是建筑定位火把——插在哪，我就把" + theName()
                    + "盖在哪（以火把为中心）。开工前别弄丢它哦~");
        } else {
            mob.chatSay("材料齐了！跟我说一声「火把」，我就把建筑定位火把给你~");
        }
    }

    /** 玩家索要火把（可以多次要，但一份材料只能盖一次）。 */
    public void requestTorch(@Nullable ServerPlayer player) {
        if (phase == PHASE_PLACING) {
            mob.chatSay("正在盖呢！定位火把别动它~");
            return;
        }
        if (!canSatisfy()) {
            mob.chatSay("材料还没凑齐呢：还缺 " + format(rawNeeds()) + "~");
            return;
        }
        giveTorch(player);
    }

    /** 玩家放置了建筑火把：以火把为中心开工。 */
    public boolean onTorchPlaced(BlockPos pos) {
        if (phase != PHASE_WAIT_TORCH) {
            mob.chatSay("现在没有待开工的建筑哦~");
            return false;
        }        torchPos = pos.immutable();
        mob.setBeamAnchor(torchPos);   // 信标光束锚定
        // 地板与火把同层：火把就插在地板中心（该格最后才被覆盖），不拆火把脚下支撑
        origin = new BlockPos(pos.getX() - template.sizeX() / 2, pos.getY(),
                pos.getZ() - template.sizeZ() / 2);
        placements = computePlacements(mob.level);
        // 火把所在的格子最后才覆盖（火把作为定位锚撑到最后一步）
        for (Placement p : placements) {
            if (p.pos.equals(torchPos)) {
                placements.remove(p);
                placements.add(p);
                break;
            }
        }
        index = 0;
        placed = 0;
        phase = PHASE_PLACING;
        cooldown = 10;
        mob.chatSay("收到！以光束为中心，我来盖" + theName() + "啦~");
        return true;
    }

    /** 建造途中材料接不上 → 退回索料并汇报。 */
    private void requestMissing() {
        if (canSatisfy()) {
            giveTorch(null);
            return;
        }
        mob.chatSay("咦，材料不够了，还缺 " + format(rawNeeds()) + "，麻烦再给我一点~");
    }

    public void tick() {
        if (phase == PHASE_COLLECT) {
            // 材料随时可能够（背包里早就有）：定期自动检查，够了直接给火把
            waitTicks++;
            if (waitTicks % 40 == 10 && canSatisfy()) {
                giveTorch(lastPlayer);
                return;
            }
            // 好奇心联动：等材料超过 30 秒，主动去附近捡方块（可作为类似材料补充）
            waitTicks++;
            if (waitTicks == 600) {
                mob.chatSay("材料还没凑齐…我去附近溜达，捡点方块说不定能派上用场！");
            }
            // 材料随时可能够（背包里早就有/刚捡到）：定期自动检查，够了直接给火把开工
        if (waitTicks % 40 == 10 && canSatisfy()) {
            giveTorch(lastPlayer);
            return;
        }
        if (waitTicks > 600 && cooldown <= 0 && mob.getNavigation().isDone()) {
                java.util.List<ItemEntity> near = mob.level.getEntitiesOfClass(ItemEntity.class,
                        mob.getBoundingBox().inflate(10.0D), ItemEntity::isAlive);
                if (!near.isEmpty()) {
                    ItemEntity t2 = near.get(mob.getRandom().nextInt(near.size()));
                    mob.getNavigation().moveTo(t2.getX(), t2.getY(), t2.getZ(), 1.05D);
                } else {
                    BlockPos c = mob.blockPosition();
                    int dx = mob.getRandom().nextInt(17) - 8;
                    int dz = mob.getRandom().nextInt(17) - 8;
                    int y = mob.level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.getX() + dx, c.getZ() + dz);
                    mob.getNavigation().moveTo(c.getX() + dx + 0.5D, y, c.getZ() + dz + 0.5D, 1.0D);
                }
                cooldown = 60;
            }
            return;
        }
        if (phase != PHASE_PLACING || placements == null || origin == null) return;
        // 卡住检测：长时间没进展 → 砸开挡路方块 → 再不行直接闪现
        if (index != lastSeenIndex) {
            lastSeenIndex = index;
            stuckTicks = 0;
        } else {
            stuckTicks++;
        }
        if (stuckTicks == 250) {
            mob.chatSay("好像被什么东西挡住了…我来开路！");
        }
        if (stuckTicks > 250 && stuckTicks % 25 == 0 && index < placements.size()) {
            BlockPos target = placements.get(index).pos;
            int ddx = Integer.compare(target.getX(), mob.getBlockX());
            int ddz = Integer.compare(target.getZ(), mob.getBlockZ());
            BlockPos feet = mob.blockPosition().offset(ddx, 0, ddz);
            BlockPos head = feet.above();
            for (BlockPos p : new BlockPos[]{feet, head}) {
                BlockState st = mob.level.getBlockState(p);
                if (st.isAir()) continue;
                // 不拆已建好的建筑方块（和图纸 palette 对比）
                boolean isBuildingBlock = false;
                for (String blockId : template.palette.values()) {
                    if (st.getBlock() == net.minecraftforge.registries.ForgeRegistries.BLOCKS
                            .getValue(new net.minecraft.resources.ResourceLocation(blockId))) {
                        isBuildingBlock = true;
                        break;
                    }
                }
                if (isBuildingBlock) continue;
                if (st.getDestroySpeed(mob.level, p) >= 0.0F) {
                    mob.level.destroyBlock(p, true);
                }
            }
            mob.swing(InteractionHand.MAIN_HAND);
        }
        if (stuckTicks > 550) {
            BlockPos target = placements.get(index).pos;
            mob.chatSay("绕不过去了…直接闪现过去！");
            // 传送到目标上方（滞空落地继续建造）
            mob.randomTeleport(target.getX() + 0.5D, target.getY() + 2.0D, target.getZ() + 0.5D, false);
            stuckTicks = 0;
            cooldown = 30;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        while (index < placements.size()) {
            Placement p = placements.get(index);
            Item item = p.state.getBlock().asItem();
            BlockState placeState = p.state;
            if (item != net.minecraft.world.item.Items.AIR
                    && mob.escrowCount(item) <= 0 && !mob.tryCraftFor(item, 1)) {
                // 没有 + 加工不出来 → 找同类材料顶替（Elena 要求的自动补充）
                Item alt = mob.findSubstitute(item);
                if (alt == null || !(alt instanceof net.minecraft.world.item.BlockItem bi)) {
                    phase = PHASE_COLLECT;
                    requestMissing();
                    return;
                }
                placeState = bi.getBlock().defaultBlockState();
                if (!saidSubstitute) {
                    mob.chatSay("缺 " + new ItemStack(item).getHoverName().getString()
                            + "，我先拿手头的 " + new ItemStack(alt).getHoverName().getString() + " 顶着！");
                    saidSubstitute = true;
                }
                item = alt;
            }
            AABB targetBox = new AABB(p.pos);
            if (mob.getBoundingBox().intersects(targetBox)) {
                // 自己站在要施工的位置，先挪开
                BlockPos away = p.pos.relative(mob.getDirection().getOpposite(), 3);
                mob.getNavigation().moveTo(away.getX() + 0.5D, away.getY(), away.getZ() + 0.5D, 1.1D);
                cooldown = 8;
                return;
            }
            double d = mob.distanceToSqr(p.pos.getX() + 0.5D, p.pos.getY() + 0.5D, p.pos.getZ() + 0.5D);
            if (d > 20.25D) {
                mob.getNavigation().moveTo(p.pos.getX() + 0.5D, p.pos.getY() - 1, p.pos.getZ() + 0.5D, 1.05D);
                mob.getLookControl().setLookAt(p.pos.getX() + 0.5D, p.pos.getY() + 0.5D, p.pos.getZ() + 0.5D);
                cooldown = 5;
                return;
            }
            BlockState current = mob.level.getBlockState(p.pos);
            if (!current.isAir() && !current.getMaterial().isReplaceable()) {
                if (p.pos.equals(torchPos)) {
                    // 定位火把本身：最后一步直接覆盖，视为"被建筑消耗"，不算阻碍也不触发停工

                } else if (current.getDestroySpeed(mob.level, p.pos) < 0.0F) {
                    index++;   // 基岩等不可破坏的，绕过
                    continue;
                } else {
                    // 阻碍方块：直接破坏推进工程
                    mob.getLookControl().setLookAt(p.pos.getX() + 0.5D, p.pos.getY() + 0.5D, p.pos.getZ() + 0.5D);
                    mob.swing(InteractionHand.MAIN_HAND);
                    mob.level.destroyBlock(p.pos, true);
                    cooldown = 6 + mob.getRandom().nextInt(3);
                    return;
                }
            }
            mob.getLookControl().setLookAt(p.pos.getX() + 0.5D, p.pos.getY() + 0.5D, p.pos.getZ() + 0.5D);
            mob.swing(InteractionHand.MAIN_HAND);
            mob.level.setBlock(p.pos, placeState, 3);
            mob.level.playSound(null, p.pos, placeState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6F, 1.0F);
            if (item != net.minecraft.world.item.Items.AIR) mob.takeEscrow(item, 1);
            placed++;
            progressChat();
            index++;
            cooldown = 3 + mob.getRandom().nextInt(4);
            return;
        }
        finish();
    }

    private void progressChat() {
        int total = placements.size();
        if (total > 8) {
            if (placed == total / 3) mob.chatSay("框架起来了，别急~");
            else if (placed == total * 2 / 3) mob.chatSay("过半了！再给我一会儿。");
        }
    }

    private void finish() {
        removeTorch();
        mob.chatSay("盖好啦！来看看吧~ 剩下的材料我先帮你收着。");
        mob.clearBuildTask();
    }

    public void cancel() {
        removeTorch();
        mob.chatSay("好，那边我先停了，材料还替你收着。");
    }

    private void removeTorch() {
        mob.setBeamAnchor(null);
    }

    public String describe() {
        return "在盖" + theName();
    }

    /** 玩家要的建筑名（优先）而不是图纸库的原始名字。 */
    public String theName() {
        return requestName != null && !requestName.isBlank() ? requestName : template.displayName();
    }

    // ------------------------------------------------------------------ 内部

    /** 手头材料（含就地加工）够不够盖完整个建筑。 */
    private boolean canSatisfy() {
        Map<Item, Integer> pool = new HashMap<>(mob.escrowCopy());
        for (Map.Entry<Item, Integer> e : template.materialCounts().entrySet()) {
            int have = Math.min(pool.getOrDefault(e.getKey(), 0), e.getValue());
            if (have > 0) pool.merge(e.getKey(), -have, Integer::sum);
            int shortfall = e.getValue() - have;
            if (shortfall > 0 && !CraftingBook.craft(e.getKey(), shortfall, pool, 0)
                    && mob.findSubstitute(e.getKey()) == null) return false;
        }
        return true;
    }

    /** 还需要向玩家索要的原始材料清单（已经扣掉手头有的）。 */
    private Map<Item, Integer> rawNeeds() {
        Map<Item, Integer> pool = new HashMap<>(mob.escrowCopy());
        Map<Item, Integer> request = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : template.materialCounts().entrySet()) {
            int have = Math.min(pool.getOrDefault(e.getKey(), 0), e.getValue());
            if (have > 0) pool.merge(e.getKey(), -have, Integer::sum);
            int shortfall = e.getValue() - have;
            if (shortfall <= 0) continue;
            Map<Item, Double> raw = CraftingBook.rawCost(e.getKey());
            for (Map.Entry<Item, Double> r : raw.entrySet()) {
                int n = (int) Math.ceil(r.getValue() * shortfall);
                if (n > 0) request.merge(r.getKey(), n, Integer::sum);
            }
        }
        // 手头剩下的零头材料直接抵扣索料清单
        for (Map.Entry<Item, Integer> e : pool.entrySet()) {
            if (e.getValue() > 0) {
                request.computeIfPresent(e.getKey(), (k, v) -> Math.max(0, v - e.getValue()));
            }
        }
        request.values().removeIf(v -> v <= 0);
        return request;
    }

    private String format(Map<Item, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Item, Integer> e : map.entrySet()) {
            if (sb.length() > 0) sb.append("、");
            sb.append(new ItemStack(e.getKey()).getHoverName().getString()).append("×").append(e.getValue());
        }
        return sb.toString();
    }

    private List<Placement> computePlacements(Level level) {
        List<Placement> list = new ArrayList<>();
        for (int y = 0; y < template.sizeY(); y++) {
            for (int z = 0; z < template.sizeZ(); z++) {
                for (int x = 0; x < template.sizeX(); x++) {
                    char c = template.charAt(x, y, z);
                    if (c == '.') continue;
                    net.minecraft.world.level.block.Block b = template.blockFor(c);
                    if (b == null) continue;
                    list.add(new Placement(origin.offset(x, y, z), b.defaultBlockState()));
                }
            }
        }
        int cx = template.sizeX() / 2, cz = template.sizeZ() / 2;
        list.sort(Comparator
                .comparingInt((Placement p) -> p.pos.getY())
                .thenComparingInt(p -> Math.abs(p.pos.getX() - origin.getX() - cx) + Math.abs(p.pos.getZ() - origin.getZ() - cz)));
        return list;
    }

    // ------------------------------------------------------------------ 存档

    public String serialize() {
        String o = origin == null ? "-" : origin.getX() + "," + origin.getY() + "," + origin.getZ();
        String t = torchPos == null ? "-" : torchPos.getX() + "," + torchPos.getY() + "," + torchPos.getZ();
        return id + ";" + phase + ";" + index + ";" + o + ";" + t;
    }

    public static BuildTask deserialize(CompanionEntity mob, String data) {
        try {
            String[] parts = data.split(";");
            String id = parts[0];
            StructureTemplate t = StructureTemplates.get(id);
            if (t == null) return null;
            BuildTask task = new BuildTask(mob, t, id);
            task.phase = Integer.parseInt(parts[1]);
            task.index = Integer.parseInt(parts[2]);
            if (!parts[3].equals("-")) {
                String[] o = parts[3].split(",");
                task.origin = new BlockPos(Integer.parseInt(o[0]), Integer.parseInt(o[1]), Integer.parseInt(o[2]));
            }
            if (!parts[4].equals("-")) {
                String[] tt = parts[4].split(",");
                task.torchPos = new BlockPos(Integer.parseInt(tt[0]), Integer.parseInt(tt[1]), Integer.parseInt(tt[2]));
            }

            if (task.phase == PHASE_PLACING) {
                if (task.origin == null) return null;
                task.placements = task.computePlacements(mob.level);
            }
            return task;
        } catch (Exception e) {
            return null;
        }
    }
}
