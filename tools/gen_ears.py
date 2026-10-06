# -*- coding: utf-8 -*-
"""生成 luna_ears.png（32x32）：鲸鳍侧耳贴图（缩小版）。
贴图对应 FishEarsModel（尺寸减半的子像素盒子）：
右鳍: 基段(1.5,1.5,0.5)@(0,8) 中段(1.5,1,0.5)@(8,8) 鳍尖(1,0.5,0.5)@(0,12)；左鳍整体 +16。
样式不变：深蓝鳍面+鳍条+白色鳍缘（顶部一行白，鳍面一行蓝，鳍尖外缘白）。
"""
from PIL import Image
import os

img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
px = img.load()

FIN    = (45, 50, 88, 255)     # 鳍面深蓝（同露娜发色）
FIN_D  = (33, 37, 66, 255)     # 鳍面暗部
FIN_L  = (66, 73, 116, 255)    # 鳍条浅蓝
RIM    = (226, 232, 246, 255)  # 白色鳍缘
RIM_S  = (188, 200, 228, 255)  # 鳍缘阴影


def rect(x, y, w, h, c):
    for i in range(x, x + w):
        for j in range(y, y + h):
            px[i, j] = c


def paint_fin_half(uo):
    # 基段/中段：采样区域 4x2（v8 行=白缘+顶面，v9 行=鳍面鳍条）
    for base in (uo + 0, uo + 8):
        rect(base, 8, 4, 1, RIM)
        for i in range(4):
            px[base + i, 9] = FIN_L if i % 2 == 0 else FIN
        rect(base, 10, 4, 1, FIN_D)
    # 鳍尖：采样区域 3x2（v12 行=白外缘，v13 行=暗部收边）
    rect(uo + 0, 12, 3, 1, RIM)
    px[uo + 0, 13] = RIM_S
    px[uo + 1, 13] = FIN_D
    px[uo + 2, 13] = RIM_S


paint_fin_half(0)    # 右鳍
paint_fin_half(16)   # 左鳍

out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "deepseek", "textures", "entity")
os.makedirs(out, exist_ok=True)
img.save(os.path.join(out, "luna_ears.png"))
print("ears texture ok: 32x32 whale fin (half size)")
