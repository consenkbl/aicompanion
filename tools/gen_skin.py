# -*- coding: utf-8 -*-
"""生成露娜的皮肤 (64x64 标准皮肤布局, 宽臂). 输出到 src/main/resources/assets/deepseek/textures/entity/luna.png"""
from PIL import Image

W, H = 64, 64
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

SKIN = (245, 209, 180, 255)
SKIN_SH = (228, 188, 160, 255)
HAIR = (168, 130, 214, 255)
HAIR_D = (140, 105, 190, 255)
JACKET = (150, 100, 200, 255)
JACKET_D = (128, 82, 175, 255)
WHITE = (240, 238, 245, 255)
PANTS = (72, 72, 100, 255)
SHOE = (225, 225, 232, 255)
EYE_W = (250, 250, 250, 255)
EYE_I = (95, 80, 175, 255)
MOUTH = (205, 130, 120, 255)
BLUSH = (240, 170, 160, 255)


def rect(x, y, w, h, c):
    for i in range(x, x + w):
        for j in range(y, y + h):
            px[i, j] = c


# ---------------- 头部 (region 0,0 - 32,16) ----------------
HEAD_FACES = [(0, 8), (8, 8), (16, 8), (24, 8)]  # right/front/left/back
for (fx, fy) in HEAD_FACES:
    rect(fx, fy, 8, 8, SKIN)
# 头顶/头底
rect(8, 0, 8, 8, HAIR)
rect(16, 0, 8, 8, HAIR_D)
# 四个侧面: 顶部3行头发
for (fx, fy) in HEAD_FACES:
    rect(fx, fy, 8, 3, HAIR)
# 前脸刘海第二行锯齿
px[8 + 0, 10] = HAIR
px[8 + 3, 10] = HAIR
px[8 + 4, 10] = HAIR
px[8 + 7, 10] = HAIR
# 侧面下半段长发一点
for (fx, fy) in [(0, 8), (16, 8), (24, 8)]:
    rect(fx, fy + 6, 8, 2, HAIR_D)
# 眼睛: 脸部局部坐标 y=4, 左眼 x1-2 右眼 x5-6
fx, fy = 8, 8
px[fx + 1, fy + 4] = EYE_W
px[fx + 2, fy + 4] = EYE_I
px[fx + 5, fy + 4] = EYE_I
px[fx + 6, fy + 4] = EYE_W
# 嘴
px[fx + 3, fy + 6] = MOUTH
px[fx + 4, fy + 6] = MOUTH
# 腮红
px[fx + 0, fy + 5] = BLUSH
px[fx + 7, fy + 5] = BLUSH

# 帽子层: 头顶盖一层头发色
rect(40, 0, 8, 8, HAIR)

# ---------------- 身体 (region 16,16 - 40,32) ----------------
BODY_FACES = [(16, 20, 4), (20, 20, 8), (28, 20, 4), (32, 20, 8)]  # right/front/left/back (x,y,宽)
for (bx, by, bw) in BODY_FACES:
    rect(bx, by, bw, 12, JACKET)
# 前面: 领口深色 + 底部白条纹
rect(20, 20, 8, 1, JACKET_D)
rect(20, 30, 8, 2, WHITE)
# 后背: 中间一道浅色背带
rect(32 + 3, 20 + 2, 2, 6, WHITE)
# 上/下
rect(20, 16, 8, 4, JACKET)
rect(28, 16, 8, 4, JACKET_D)

# ---------------- 右臂 (region 40,16 - 56,32) & 左臂 (32,48 - 48,64) ----------------
def arm(ox, oy):
    # 侧面: 袖子7行 + 手4行(皮肤)
    for (ax, ay) in [(ox, oy + 4), (ox + 4, oy + 4), (ox + 8, oy + 4), (ox + 12, oy + 4)]:
        rect(ax, ay, 4, 8, JACKET)
        rect(ax, ay + 8, 4, 4, SKIN)
    # 顶/底
    rect(ox + 4, oy, 4, 4, JACKET)
    rect(ox + 8, oy, 4, 4, SKIN_SH)

arm(40, 16)
arm(32, 48)

# ---------------- 右腿 (region 0,16 - 16,32) & 左腿 (region 16,48 - 32,64) ----------------
def leg(ox, oy):
    for (lx, ly) in [(ox, oy + 4), (ox + 4, oy + 4), (ox + 8, oy + 4), (ox + 12, oy + 4)]:
        rect(lx, ly, 4, 9, PANTS)
        rect(lx, ly + 9, 4, 3, SHOE)
    rect(ox + 4, oy, 4, 4, PANTS)
    rect(ox + 8, oy, 4, 4, SHOE)

leg(0, 16)
leg(16, 48)

import os
out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "deepseek", "textures", "entity")
os.makedirs(out, exist_ok=True)
img.save(os.path.join(out, "luna.png"))
print("skin saved:", os.path.abspath(os.path.join(out, "luna.png")))
