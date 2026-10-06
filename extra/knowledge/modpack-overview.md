# No Flesh Within Chest 整合包资料（给露娜的参考）

整合包：No Flesh Within Chest 1.0.2-DIM，Minecraft 1.19.2 + Forge 43.3.8，共 140 个模组。
核心特色：胸腔器官生存系统 + 大量探索结构与 Boss + Create 机械 + 魔法流派。
回答玩家问题时以下面资料为准；没提到的就说"我不太确定，你可以看看任务书（FTB Quests）"。

## 身体与生存系统（本包最大特色）
- Chest Cavity（胸腔）：玩家的胸腔里有器官系统，受到特定伤害会损伤器官，影响生命上限/能力；可以做器官手术、移植。死亡相关设置和器官掉落有关。
- Cold Sweat（冷汗）：体温系统，穿对衣服、靠近火源/水下会体温变化，过冷过热会掉血，注意带衣物和降温/保暖手段。
- Extra Armor：更多护甲与护甲机制扩展。
- MomLove：与"妈妈"机制相关的趣味内容。
- Keep Curios Inventory：保留饰品栏物品（死亡不掉饰品）。

## 探索与结构
- Twilight Forest（暮色森林）：经典大型探险维度，进阶 Boss 线。
- L_Ender's Cataclysm（灾厄）：大型 Boss 模组（如灾厄铸造Boss、下界BOSS等），装备强力。
- Bosses of Mass Destruction (BOMD)：大型 Boss（夜妖精、虚空姬等）。
- Ice and Fire（冰与火之歌）：龙！火龙/冰龙/闪电龙，龙蛋孵化、龙骨装备；还有美人鱼、独眼巨人等神话生物。iaf_patcher 是它的兼容补丁。
- The Graveyard（墓地）+ Biomes：墓园结构、恐怖生物和两个新生物群系。
- Dungeons Arise + Seven Seas：大型地牢/沉船等探险结构。
- Dim Dungeons：小型副本维度。
- Repurposed Structures、Structory、Structory Towers、Red's More Structures、YUNG's Bridges、Lio's Overhauled Villages、CTOV（ CHOICE'Theorem 村庄大修）：大量新增/改造结构村庄。
- Waystones（传送石碑）：点对点传送网络。
- Nature's Compass（自然指南针）：定位生物群系。

## 战斗与魔法
- Iron's Spells 'n Spellbooks（铁魔法书）：法术流派、法袍、施法系统。irons_spells_js 有 KubeJS 定制。
- Goety：巫师/仆从魔法。
- Hexerei（巫术）：女巫风格魔法，扫帚飞行、炼金。
- Art of Forging：锻造扩展。
- Weapon Master：武器精通。
- Modular Golems：模块化傀儡，可以自己组装战斗傀儡。
- Summoning Rituals：召唤仪式。
- Gateways To Eternity：波次传送门挑战，刷怪守波。
- Meet Your Fight：几个特色 Boss。
- World of Bosses、Invasion Code Red：更多 Boss/入侵事件。
- Tetra：模块化工具武器，可拆装改造。
- Mob Lassos：套索抓取生物。

## 机械与储存
- Create（机械动力）0.5.1：旋转动力、流水线机械。
  - Create Additions：电力扩展。
  - Create Central Kitchen：与农夫乐事联动厨房。
  - Create Crystal Clear：玻璃管道外壳。
  - Create Ore Excavation：矿石采掘机。
- Refined Storage（精致存储）+ RS Infinity Booster：数字存储网络，无限距离卡。
- Functional Storage：抽屉存储。
- Sophisticated Backpacks/Core：可升级背包装备。
- Universal Grid、TrashSlot（垃圾桶槽）、Plonk：物品管理 QoL。
- Custom Machinery：自定义机器（整合包可能用它做了专属机器）。
- Wares：贸易订单系统。
- Lightman's Currency：货币与商店系统。

## 生物与世界
- Alex's Mobs（亚历克斯生物）：大量新生物（雪山、海洋、沙漠等）。
- Unusual Prehistory：史前生物与化石复活。
- Biomancy：生物合成科技。
- Bettas：斗鱼。
- More Geodes Reforged：更多晶洞。
- Biome Backport：新版本生物群系回移植。
- TerraBlender：生物群系 API（多个群系模组的兼容层）。

## 食物与装饰
- Farmer's Delight（农夫乐事）+ Extra Delight + Fruits Delight：烹饪与农作物。
- Supplementaries：大量装饰与实用方块（灯笼、吊牌、 globem 泡泡等）。
- Simple Hats：帽子装饰；Cosmetic Armor Reworked：外观装备分离。

## 任务与多人
- FTB Quests + FTB Chunks/Teams/Library + Quests Additions：任务书是本包玩法主线，护区块/组队。
- MMO Parties：小队系统。
- Lootr（战利品箱）：每个玩家独立开箱，不会抢别人战利品。
- Goblin Traders：地精商人。
- Eccentric Tome（古怪之书）：一本集合所有模组手册的书。

## 实用与性能（不用向玩家详细介绍）
- JEI（查询合成）、Jade（看方块信息）、Spark（性能分析）。
- 性能类：ModernFix、FerriteCore、LazyDFU、Lightspeed。
- KubeJS/Rhino/LootJS/MoreJS/PonderJS/RenderJS/PowerfulJS 及各 *js：本包有大量自定义脚本改动（合成表、掉落、任务联动都可能是脚本改的），所以实际游戏内数值可能和原版模组不同——被问到具体合成/数值时建议玩家查 JEI 或任务书。
