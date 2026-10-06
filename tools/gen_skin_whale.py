# -*- coding: utf-8 -*-
"""
鲸鱼娘皮肤 (64x64 宽臂标准布局) —— 配色取自上善无形鲸鱼娘立绘的实测主色:
  深蓝紫主体 #4C578B / 高光 #7080B0 / 深处 #202050 / 白 #F0F0F0
  DeepSeek 品牌蓝 #4D6BFE 作点缀
输出: src/main/resources/assets/deepseek/textures/entity/luna.png
"""
from PIL import Image
import os

W, H = 64, 64
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

# ---- 调色板（实测自鲸鱼娘立绘）----
NAVY_D  = (22, 26, 58, 255)    # 最深处/描边  #161A3A
NAVY    = (38, 46, 96, 255)    # 头发暗部     #262E60
INDIGO  = (70, 82, 140, 255)   # 蓝紫主体     #46528C ~ #4C578B
PERI    = (122, 138, 186, 255) # 浅紫蓝高光   #7A8ABA ~ #7080B0
DSBLUE  = (77, 107, 254, 255)  # DeepSeek 蓝  #4D6BFE
WHITE   = (242, 242, 246, 255) # 白           #F2F2F6
WHITE_SH= (216, 218, 230, 255) # 白的阴影
SKIN    = (247, 216, 189, 255)
SKIN_SH = (230, 194, 166, 255)
BLUSH   = (240, 168, 156, 255)
MOUTH   = (204, 124, 118, 255)
EYE_W   = (252, 252, 252, 255)
EYE_D   = (30, 40, 90, 255)    # 瞳孔深色


def rect(x, y, w, h, c):
    for i in range(x, x + w):
        for j in range(y, y + h):
            px[i, j] = c


# ================= 头部 (region 0,0-32,16) =================
HEAD = [(0, 8), (8, 8), (16, 8), (24, 8)]  # 右/前/左/后
for (fx, fy) in HEAD:
    rect(fx, fy, 8, 8, SKIN)
    # 头发: 顶部4行渐变 NAVY->INDIGO
    rect(fx, fy, 8, 2, NAVY)
    rect(fx, fy + 2, 8, 1, INDIGO)
    rect(fx, fy + 3, 8, 1, INDIGO)
# 刘海锯齿: 第4行几个发尖
fx0, fy0 = 8, 8
for cx in (0, 2, 3, 5, 6):
    px[fx0 + cx, fy0 + 3] = NAVY
# 两侧长发: 侧面中下部垂发
for (fx, fy) in [(0, 8), (16, 8)]:
    rect(fx, fy + 4, 2, 4, INDIGO)   # 耳侧发束
    rect(fx + 6, fy + 4, 2, 4, INDIGO)
    px[fx + 1, fy + 7] = PERI        # 发梢高光
# 后脑勺大面积长发
rect(24, 8, 8, 8, INDIGO)
rect(24, 8, 8, 3, NAVY)
rect(24, 15, 8, 1, PERI)             # 发梢高光一排
# 头顶
rect(8, 0, 8, 8, INDIGO)
rect(8, 0, 8, 2, NAVY)
rect(8, 2, 8, 1, PERI)               # 中缝高光
rect(16, 0, 8, 8, NAVY)
# 呆毛: 头顶中间一根
px[11, 0] = PERI
px[12, 0] = PERI

# 前脸五官 (face at 8,8)
fx0, fy0 = 8, 8
# 眼睛: 大眼 y=4, 左 x1-2 / 右 x5-6
px[fx0 + 1, fy0 + 4] = EYE_W
px[fx0 + 2, fy0 + 4] = DSBLUE
px[fx0 + 2, fy0 + 3] = EYE_D  # 上睫毛下的瞳孔
px[fx0 + 5, fy0 + 4] = DSBLUE
px[fx0 + 5, fy0 + 3] = EYE_D
px[fx0 + 6, fy0 + 4] = EYE_W
px[fx0 + 2, fy0 + 5] = EYE_D  # 下眼线
px[fx0 + 5, fy0 + 5] = EYE_D
# 高光
px[fx0 + 1, fy0 + 3] = EYE_W
# 嘴 + 腮红
px[fx0 + 3, fy0 + 6] = MOUTH
px[fx0 + 4, fy0 + 6] = MOUTH
px[fx0 + 0, fy0 + 5] = BLUSH
px[fx0 + 7, fy0 + 5] = BLUSH

# ================= 帽子层: 鲸鱼头套 (region 32,0-64,16) =================
# 头套顶 (40,0): 圆润鲸背
rect(40, 0, 8, 8, INDIGO)
rect(40, 0, 8, 1, PERI)
rect(43, 0, 2, 8, PERI)              # 顶部中脊高光
rect(40, 7, 8, 1, NAVY)
# 头套底 (48,0): 透明留空 -> 不画
# 头套四周 (32,8)(40,8)(48,8)(56,8)
# 前脸帽檐: 只留顶部一圈边, 不遮脸
rect(40, 8, 8, 1, INDIGO)
px[42, 9] = INDIGO; px[43, 9] = INDIGO; px[44, 9] = INDIGO; px[45, 9] = INDIGO
px[41, 8] = PERI; px[46, 8] = PERI
# 左右侧: 鱼鳍耳
for (hx, hy) in [(32, 8), (48, 8)]:
    rect(hx, hy, 8, 3, INDIGO)       # 帽体上沿
    rect(hx + 2, hy + 3, 4, 4, PERI) # 鳍耳 (圆)
    px[hx + 2, hy + 7] = NAVY; px[hx + 3, hy + 7] = NAVY
    px[hx + 4, hy + 7] = NAVY; px[hx + 5, hy + 7] = NAVY
    px[hx + 3, hy + 4] = WHITE       # 鳍耳高光点
# 后脑: 鲸鱼尾巴从帽后翘起
rect(56, 8, 8, 3, INDIGO)
# 尾柄
rect(59, 11, 2, 3, INDIGO)
# 尾鳍两瓣 (倒 V)
px[57, 14] = PERI; px[58, 14] = INDIGO; px[58, 13] = PERI
px[62, 14] = PERI; px[61, 14] = INDIGO; px[61, 13] = PERI
px[59, 14] = INDIGO; px[60, 14] = INDIGO
# 喷水孔?! 顶部小水柱点
px[43, 1] = DSBLUE; px[44, 0] = DSBLUE

# ================= 身体 (region 16,16-40,32) =================
BODY = [(16, 20, 4), (20, 20, 8), (28, 20, 4), (32, 20, 8)]
for (bx, by, bw) in BODY:
    rect(bx, by, bw, 8, WHITE)       # 白色连帽衫
    rect(bx, by + 8, bw, 4, NAVY)    # 蓝紫裙
# 领口
rect(20, 20, 8, 1, INDIGO)
px[22, 21] = DSBLUE; px[25, 21] = DSBLUE   # 抽绳
px[22, 22] = DSBLUE; px[25, 22] = DSBLUE
# 裙摆高光
rect(20, 30, 8, 1, INDIGO)
for (bx, by, bw) in [(16, 30, 4), (28, 30, 4), (32, 30, 8)]:
    rect(bx, by, bw, 1, INDIGO)
# 后背鲸鱼尾巴印花
bx, by = 32, 20
rect(bx + 3, by + 4, 2, 3, PERI)     # 尾柄
px[bx + 1, by + 7] = PERI; px[bx + 2, by + 7] = INDIGO
px[bx + 6, by + 7] = PERI; px[bx + 5, by + 7] = INDIGO
px[bx + 3, by + 7] = INDIGO; px[bx + 4, by + 7] = INDIGO
# 身体上/下
rect(20, 16, 8, 4, WHITE)
rect(28, 16, 8, 4, NAVY_D)

# ================= 手臂 (右 40,16-56,32 / 左 32,48-48,64) =================
def arm(ox, oy):
    for (ax, ay) in [(ox, oy + 4), (ox + 4, oy + 4), (ox + 8, oy + 4), (ox + 12, oy + 4)]:
        rect(ax, ay, 4, 6, WHITE)         # 白袖
        rect(ax, ay + 6, 4, 2, INDIGO)    # 袖口蓝紫
        rect(ax, ay + 8, 4, 4, SKIN)      # 手
    rect(ox + 4, oy, 4, 4, WHITE)         # 肩顶
    rect(ox + 8, oy, 4, 4, SKIN_SH)       # 手底
arm(40, 16)
arm(32, 48)

# ================= 腿 (右 0,16-16,32 / 左 16,48-32,64) =================
def leg(ox, oy):
    for (lx, ly) in [(ox, ly0 := oy + 4), (ox + 4, ly0), (ox + 8, ly0), (ox + 12, ly0)]:
        rect(lx, ly, 4, 2, NAVY)          # 裙影
        rect(lx, ly + 2, 4, 7, WHITE)     # 白色过膝袜
        rect(lx, ly + 9, 4, 3, NAVY)      # 蓝紫鞋
        px[lx, ly + 2] = WHITE_SH         # 袜子侧影
        px[lx + 3, ly + 2] = WHITE_SH
        px[lx + 1, ly + 11] = DSBLUE      # 鞋上 DeepSeek 点缀
    rect(ox + 4, oy, 4, 4, NAVY)          # 裤顶
    rect(ox + 8, oy, 4, 4, NAVY_D)        # 鞋底
leg(0, 16)
leg(16, 48)

out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets",
                   "deepseek", "textures", "entity")
os.makedirs(out, exist_ok=True)
img.save(os.path.join(out, "luna.png"))
print("鲸鱼娘皮肤已生成:", os.path.abspath(os.path.join(out, "luna.png")))
