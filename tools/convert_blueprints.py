# -*- coding: utf-8 -*-
"""剑与王国（MineColonies/Structurize）.blueprint 图纸 → DeepSeek JSON 模板转换器
用法: py convert_blueprints.py analyze|convert
"""
import nbtlib
import os
import glob
import json
import io
import collections
import statistics
import sys

VALID_BLOCKS = set()
for line in io.open(r"D:\MCModDev\aicompanion\tools\block_ids_1192.txt", encoding="utf-8"):
    line = line.strip()
    if line:
        VALID_BLOCKS.add("minecraft:" + line)

SRC = r"C:\Users\Elena\AppData\Roaming\.minecraft\versions\剑与王国\blueprints\剑与王国"
DST = r"C:\Users\Elena\AppData\Roaming\.minecraft\versions\No Flesh Within Chest\mods\deepseek-config\structures"

MAX_SIDE = 24
MAX_LAYERS = 32
MAX_BLOCKS = 4000
MIN_RATIO = 0.9

# domum 装饰方块名 → 原版近似方块（按名称关键词启发式映射）
DOMUM_HINTS = [
    ("stone_bricks", "minecraft:stone_bricks"),
    ("bricks", "minecraft:bricks"),
    ("panel", "minecraft:oak_planks"),
    ("shingle", "minecraft:spruce_planks"),
    ("roof", "minecraft:dark_oak_planks"),
    ("beam", "minecraft:oak_log"),
    ("paper_wall", "minecraft:white_concrete"),
    ("wall", "minecraft:cobblestone_wall"),
    ("slab", "minecraft:smooth_stone_slab"),
    ("fence", "minecraft:oak_fence"),
]


def load_blueprints():
    files = sorted(glob.glob(os.path.join(SRC, "**", "*.blueprint"), recursive=True))
    out = []
    for f in files:
        try:
            nbt = nbtlib.load(f)
            out.append((f, nbt))
        except Exception as e:
            print("解析失败:", os.path.basename(f), e)
    return out


def category_of(path):
    rel = os.path.relpath(path, SRC)
    parts = rel.split(os.sep)
    return parts[0] if len(parts) > 1 else "其他"


def pick_texture_skin(skin_counter):
    """domum 换皮方块最常见的原版贴图 → 直接当作方块 id。"""
    for texture, _ in skin_counter.most_common(1):
        tex = texture.replace("block/", "")
        if ":" not in tex:
            tex = "minecraft:" + tex
        if tex in VALID_BLOCKS:
            return tex
    return None


def map_block_id(name, skin_counter):
    """调色板方块名 → 1.19.2 原版方块 id；无法映射返回 None。"""
    ns = name.split(":")[0]
    short = name.split(":")[1] if ":" in name else name
    if ns == "minecraft":
        if short == "air" or name == "minecraft:air":
            return None
        return name if name in VALID_BLOCKS else None
    if ns == "domum_ornamentum":
        if skin_counter:
            tex = pick_texture_skin(skin_counter)
            if tex:
                return tex
        low = short.lower()
        for kw, vanilla in DOMUM_HINTS:
            if kw in low:
                return vanilla
        return None
    if ns == "structurize":
        # 填充占位方块：MineColonies 建造者会用指定方块填充，这里统一映射为圆石
        if "substitution" in short:
            return "minecraft:cobblestone"
        return None
    return None


def analyze(files):
    unmapped_ns = collections.Counter()
    unmapped_names = collections.Counter()
    viable = 0
    skipped_big = 0
    ratios = []
    for f, nbt in files:
        sx, sy, sz = int(nbt["size_x"]), int(nbt["size_y"]), int(nbt["size_z"])
        if max(sx, sy, sz) > MAX_SIDE or sy > MAX_LAYERS:
            skipped_big += 1
            continue
        pal = [str(p.get("Name", "")) for p in nbt.get("palette", [])]
        skin = collections.defaultdict(collections.Counter)
        for te in nbt.get("tile_entities", []):
            if not hasattr(te, "keys"):
                continue
            td = te.get("textureData")
            if td is None or not hasattr(td, "items"):
                continue
            bid = str(te.get("id", ""))
            for k, v in td.items():
                skin[bid][str(v)] += 1
        state_count = collections.Counter()
        blocks = nbt.get("blocks", None)
        if blocks is None:
            continue
        shorts = []
        for enc in blocks:
            v = int(enc)
            shorts.append((v >> 16) & 0xFFFF)
            shorts.append(v & 0xFFFF)
        for state in shorts[:sx * sy * sz]:
            state_count[state] += 1   # Structurize 编码：状态占低 12 位
        mappable = unmappable = 0
        for state, cnt in state_count.items():
            if state >= len(pal):
                continue
            name = pal[state]
            if map_block_id(name, skin.get(name)) is None:
                unmappable += cnt
                unmapped_ns[name.split(":")[0]] += cnt
                if cnt >= 15:
                    unmapped_names[name] += cnt
            else:
                mappable += cnt
        total_blocks = mappable + unmappable
        if total_blocks == 0:
            continue
        ratio = mappable / total_blocks
        ratios.append(ratio)
        if ratio >= MIN_RATIO and total_blocks <= MAX_BLOCKS:
            viable += 1
    print(f"总图纸 {len(files)} | 可转换 {viable} | 尺寸超限 {skipped_big}")
    if ratios:
        print(f"可映射率: 中位数 {statistics.median(ratios)*100:.0f}% 平均 {sum(ratios)/len(ratios)*100:.0f}%")
    print("不可映射命名空间:", dict(unmapped_ns.most_common(5)))
    print("最多出现的不可映射方块:")
    for n, c in unmapped_names.most_common(8):
        print("   ", n, c)


def convert(files):
    os.makedirs(DST, exist_ok=True)
    converted = 0
    skipped = 0
    for f, nbt in files:
        sx, sy, sz = int(nbt["size_x"]), int(nbt["size_y"]), int(nbt["size_z"])
        if max(sx, sy, sz) > MAX_SIDE or sy > MAX_LAYERS:
            skipped += 1
            continue
        pal = [str(p.get("Name", "")) for p in nbt.get("palette", [])]
        skin = collections.defaultdict(collections.Counter)
        for te in nbt.get("tile_entities", []):
            if not hasattr(te, "keys"):
                continue
            td = te.get("textureData")
            if td is None or not hasattr(td, "items"):
                continue
            bid = str(te.get("id", ""))
            for k, v in td.items():
                skin[bid][str(v)] += 1

        char_map = {}      # 调色板索引 -> 字符
        palette = {}       # 字符 -> 方块 id
        next_char = 65     # 'A'
        state_count = collections.Counter()
        blocks = nbt.get("blocks", None)
        if blocks is None:
            skipped += 1
            continue
        shorts = []
        for enc in blocks:
            v = int(enc)
            shorts.append((v >> 16) & 0xFFFF)
            shorts.append(v & 0xFFFF)
        for state in shorts[:sx * sy * sz]:
            state_count[state] += 1   # Structurize 编码：状态占低 12 位

        for state in list(state_count.keys()):
            if state >= len(pal):
                continue
            name = pal[state]
            block_id = map_block_id(name, skin.get(name))
            if block_id is None:
                continue    # 不可映射：该状态的方块全部跳过
            ch = chr(next_char)
            next_char += 1
            char_map[state] = ch
            palette[ch] = block_id

        size_x, size_y, size_z = sx, sy, sz
        layers = [[["." for _ in range(size_x)] for _ in range(size_z)] for _ in range(size_y)]
        total = 0
        unmapped_solids = 0
        idx = 0
        for y in range(size_y):
            for z in range(size_z):
                for x in range(size_x):
                    state = shorts[idx]
                    idx += 1
                    if state not in char_map:
                        # 空气不算扣分；实心方块映射失败才算完整度损失
                        if state < len(pal) and pal[state] != "minecraft:air":
                            unmapped_solids += 1
                        continue
                    layers[y][z][x] = char_map[state]
                    total += 1
        if total == 0 or total > MAX_BLOCKS:
            skipped += 1
            continue
        ratio = total / max(1, total + unmapped_solids)
        if ratio < MIN_RATIO:
            skipped += 1
            continue

        name = str(nbt.get("name", os.path.basename(f).replace(".blueprint", "")))
        base = os.path.basename(f).replace(".blueprint", "")
        cat = category_of(f)
        out = {
            "name": name if name and not name.endswith(".blueprint") else base,
            "desc": f"{cat}分类建筑，{total} 个方块，原版材料完整度 {round(ratio*100)}%",
            "tags": [cat, base, name.replace(".blueprint", "")],
            "palette": palette,
            "rows": [["".join(row) for row in layer] for layer in layers],
        }
        slug = cat + "_" + base
        out_path = os.path.join(DST, slug + ".json")
        io.open(out_path, "w", encoding="utf-8").write(
            json.dumps(out, ensure_ascii=False, indent=1))
        converted += 1
    print(f"转换完成: {converted} 张 → {DST}")
    print(f"跳过: {skipped} 张")


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "analyze"
    bps = load_blueprints()
    if mode == "convert":
        convert(bps)
    else:
        analyze(bps)
