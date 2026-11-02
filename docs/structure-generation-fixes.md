# 原版结构生成修复

DeerFoliaPlus 26.3 为一组原版结构生成缺陷提供修复，包括无法连接的建筑片段、营地和帐篷中被自身方块阻挡的树木，以及部分缺失的装置和装饰。

配置位于 `config/deer-folia-plus.yml` 的 `structure-generation-fixes` 节。**总开关和 44 个独立开关默认全部为 `true`。**

## 开关与生效范围

某项修复生效的条件是：总开关 `enabled` 和该项开关同时为 `true`。

- 总开关为 `false`：全部使用原版生成逻辑，保留各分项设置，重新开启总开关时继续使用这些分项设置。
- 某项为 `false`：该项保持原版行为，其他已开启的修复继续生效。这里的“关闭”是关闭修复，不是禁止原版中本来能生成的整个村庄、营地或试炼密室。
- 配置在启动时生成不可变快照。**修改后需要重启服务器**；修改磁盘文件或执行数据包重载不会热更新这些开关。
- 正常首次启动会补齐默认开启的配置。已有配置先进行严格 YAML 语法检查，并验证本节的开关确实为布尔值；`'false'` 字符串、数字和空值不会被当成默认开启。验证失败时保留原文件，本次启动关闭这组生成修复并记录错误，避免用不完整配置改变世界生成。其他功能的配置项沿用原有类型处理。
- 修复影响后续的结构生成，不会扫描或覆盖既有区块，也不会删除已经生成的建筑。已保存了片段列表的在建结构可能继续沿用旧布局。
- 开启表示允许正确的连接、片段或内容参与生成；普通的随机权重、地形、碰撞和距离限制仍然适用，不保证每一处村庄或营地都有这些内容。
- 数据包覆盖的模板和世界 `generated/` 中的自定义模板保留其优先级，不会仅因使用了同名 `minecraft:` 路径而被当作原版模板修正。
- 上述隔离依据资源包和文件来源；插件通过 API 直接注册同名内存模板或改写世界生成注册表时，需要通过对应开关协调生成规则。

## 完整默认配置

以下示例列出了所有开关。字段名遵循现有配置框架的 kebab-case 规则，末尾数字前没有额外连字符。

```yaml
structure-generation-fixes:
  enabled: true
  villages:
    snowy-shepherds-house1: true
    plains-small-house8: true
    plains-fisher-cottage1: true
    desert-tannery1: true
    desert-large-farm1: true
    savanna-fisher-cottage1: true
    savanna-shepherd1: true
    savanna-animal-pen2: true
    savanna-large-farm2: true
    savanna-small-farm: true
    hay-pile: true
    pumpkin-pile: true
    ice-pile: true
    melon-pile: true
    snow-pile: true
  trail-ruins:
    group-upper1: true
    group-upper2: true
    group-upper3: true
    group-upper4: true
    group-upper5: true
    road-end1: true
  trial-chambers:
    corridor-wall: true
    walkway-with-bridge1: true
    chamber-disposal8: true
  bastions:
    pathway-end-wall0: true
  ancient-cities:
    intact-horizontal-wall-bridge: true
    camp1: true
    camp2: true
    camp3: true
  abandoned-camps:
    campsite-pale-garden2: true
    trees:
      campsite-old-growth-pine-taiga1: true
      campsite-old-growth-spruce-taiga4: true
      campsite-snowy-taiga3: true
      tent-cherry-grove1: true
      tent-dappled-forest1: true
      tent-dappled-forest2: true
      tent-dappled-forest3: true
      tent-dappled-forest4: true
      tent-dappled-forest6: true
      tent-savanna1: true
      tent-snowy-taiga10: true
      tent-taiga2: true
      tent-taiga7: true
      tent-windswept-forest6: true
```

例如，只保持雪原牧羊人房屋的原版行为，可以设置：

```yaml
structure-generation-fixes:
  enabled: true
  villages:
    snowy-shepherds-house1: false
```

未写出的选项仍采用其已有值或首次补齐的默认值。总开关关闭时，所有分项都不会生效。

## 结构与装饰配置目录

下面的配置键均省略 `structure-generation-fixes.` 前缀，资源路径均省略 `minecraft:` 前缀和 `.nbt` 后缀。每一行是一个独立开关。

| 配置键 | 修复内容或资源 | 对应问题 |
|---|---|---|
| `villages.snowy-shepherds-house1` | `village/snowy/houses/snowy_shepherds_house_1` 的入口拼写；普通和僵尸雪原村庄均使用该模板 | [MC-170550](https://bugs.mojang.com/browse/MC/issues/MC-170550) |
| `villages.plains-small-house8` | `village/plains/houses/plains_small_house_8` 的道路内连接空间 | [MC-309529](https://bugs.mojang.com/browse/MC/issues/MC-309529) |
| `villages.plains-fisher-cottage1` | `village/plains/houses/plains_fisher_cottage_1` 的道路内连接空间 | 同上 |
| `villages.desert-tannery1` | `village/desert/houses/desert_tannery_1` 的道路内连接空间 | [MC-310673](https://bugs.mojang.com/browse/MC/issues/MC-310673) |
| `villages.desert-large-farm1` | `village/desert/houses/desert_large_farm_1` 的道路内连接空间 | 同上 |
| `villages.savanna-fisher-cottage1` | `village/savanna/houses/savanna_fisher_cottage_1` 的道路内连接空间 | [MC-306596](https://bugs.mojang.com/browse/MC/issues/MC-306596)及独立生成复现 |
| `villages.savanna-shepherd1` | `village/savanna/houses/savanna_shepherd_1` 的道路内连接空间 | 同上 |
| `villages.savanna-animal-pen2` | `village/savanna/houses/savanna_animal_pen_2` 的道路内连接空间 | 同上 |
| `villages.savanna-large-farm2` | `village/savanna/houses/savanna_large_farm_2` 的道路内连接空间 | 同上 |
| `villages.savanna-small-farm` | `village/savanna/houses/savanna_small_farm` 的道路内连接空间 | 同上 |
| `villages.hay-pile` | 浅超平坦世界底部的村庄干草堆放置 | [MC-309531](https://bugs.mojang.com/browse/MC/issues/MC-309531) |
| `villages.pumpkin-pile` | 浅超平坦世界底部的村庄南瓜堆放置 | 同上 |
| `villages.ice-pile` | 浅超平坦世界底部的村庄冰堆放置 | 同上 |
| `villages.melon-pile` | 同一高度检查影响的原版村庄西瓜堆 | 与 MC-309531 同源，独立特征对照验证 |
| `villages.snow-pile` | 同一高度检查影响的原版村庄雪堆 | 与 MC-309531 同源，独立特征对照验证 |
| `trail-ruins.group-upper1` | `trail_ruins/buildings/group_upper_1` 的连接 | [MC-264093](https://bugs.mojang.com/browse/MC/issues/MC-264093) |
| `trail-ruins.group-upper2` | `trail_ruins/buildings/group_upper_2` 的连接 | 同上 |
| `trail-ruins.group-upper3` | `trail_ruins/buildings/group_upper_3` 的连接 | 同上 |
| `trail-ruins.group-upper4` | `trail_ruins/buildings/group_upper_4` 的连接 | 同上 |
| `trail-ruins.group-upper5` | `trail_ruins/buildings/group_upper_5` 的连接 | 同上 |
| `trail-ruins.road-end1` | `trail_ruins/roads/road_end_1` 与道路空闲端的连接 | 原版资源连接分析；暂无已确认的官方问题编号 |
| `trial-chambers.corridor-wall` | `trial_chambers/corridor/addon/wall` 附加墙段的连接 | [MC-266778](https://bugs.mojang.com/browse/MC/issues/MC-266778) |
| `trial-chambers.walkway-with-bridge1` | `trial_chambers/chamber/addon/walkway_with_bridge_1` 带桥走道的连接 | 同上 |
| `trial-chambers.chamber-disposal8` | `trial_chambers/chamber/chamber_8` 上层漏斗、木桶组合的生成池 | [MC-312911](https://bugs.mojang.com/browse/MC/issues/MC-312911) |
| `bastions.pathway-end-wall0` | `bastion/units/pathways/pathway_wall_0` 末端墙体连接 | [MC-312914](https://bugs.mojang.com/browse/MC/issues/MC-312914) |
| `ancient-cities.intact-horizontal-wall-bridge` | `ancient_city/walls/intact_horizontal_wall_bridge` 东端后续墙体连接 | [MC-262023](https://bugs.mojang.com/browse/MC/issues/MC-262023) |
| `ancient-cities.camp1` | 独立的 `ancient_city/structures/camp_1` 样式 | [MC-255639](https://bugs.mojang.com/browse/MC/issues/MC-255639) |
| `ancient-cities.camp2` | 独立的 `ancient_city/structures/camp_2` 样式 | 同上 |
| `ancient-cities.camp3` | 独立的 `ancient_city/structures/camp_3` 样式 | 同上 |
| `abandoned-camps.campsite-pale-garden2` | `abandoned_camp/camp/pale_garden/campsite_pale_garden_2` 与帐篷的连接 | [MC-312909](https://bugs.mojang.com/browse/MC/issues/MC-312909) |

### 远古城市三个营地开关

原版把 `camp_1`、`camp_2`、`camp_3` 注册成一个复合候选，并叠在同一位置。

本修复保留该候选原来的总权重。**只有原组合营地候选被抽中之后**，才等概率选取 1、2、3 中的一个编号：对应开关为 `true` 时使用该独立模板，为 `false` 时保留原版三模板叠加布局。

- 三项都为 `true`：三种独立样式各有三分之一的选择概率。
- 例如只有 `camp1` 为 `true`：抽到 1 时生成独立 1 号；抽到 2 或 3 时仍生成原版叠加布局。
- 三项都为 `false`，或总开关关闭：原候选保持不变，也不为这项修复额外消耗随机数。

这些概率仅针对已经抽中的原营地候选，不代表营地在整个远古城市中的出现概率。

### 浅超平坦世界中的堆放装饰

原版村庄的干草、南瓜、冰、西瓜、雪五类堆放装饰共用一项要求“放置点至少高于世界底部 5 格”的检查。本修复仅对已开启的原版村庄装饰调用，将最低安全距离调整为 1 格，保留地面支撑、方块可替换性等原检查。其他世界装饰、数据包自定义特征和独立树木不使用这项放宽。

放置点位于世界底部本身时仍拒绝，避免向世界外放置；足够高的正常地形继续沿用相同生成规则。

### 村庄道路内部的连接空间

上表九项房屋、农场和围栏修复针对一种具体的拼图放置条件：地形适配道路（`terrain_matching`）的内部连接点需要把子结构向下放置一格，但道路内部可用空间没有包含这一格，导致候选被拒绝。

修复仅对已开启的指定原版模板补足下方一格，并且该空间必须属于当前结构上下文原本可用的空间。外围和已占用空间的碰撞检查、世界最低高度限制仍然保留；不会抬高房屋、移动道路或修改地面。超平坦容易触发这一条件，普通世界满足同样条件时也使用修复，开关并非仅限于超平坦世界。

MC-306596 的历史报告还列出了 `savanna_medium_house_1`；独立对照中该模板原版能够生成，因此没有为它增加修复或开关。

## 营地和帐篷中的树木

以下 14 个开关均位于 `structure-generation-fixes.abandoned-camps.trees`。它们仅修复对应营地或帐篷内部的树，关闭不会禁止营地本体，也不会全局改变普通树林的生成规则。

| 配置键 | 模板资源路径 | 对应问题 |
|---|---|---|
| `campsite-old-growth-pine-taiga1` | `abandoned_camp/camp/old_growth_pine_taiga/campsite_old_growth_pine_taiga_1` | [MC-312912](https://bugs.mojang.com/browse/MC/issues/MC-312912) |
| `campsite-old-growth-spruce-taiga4` | `abandoned_camp/camp/old_growth_spruce_taiga/campsite_old_growth_spruce_taiga_4` | 同上 |
| `campsite-snowy-taiga3` | `abandoned_camp/camp/snowy_taiga/campsite_snowy_taiga_3` | 同上 |
| `tent-cherry-grove1` | `abandoned_camp/tent/cherry_grove/tent_cherry_grove_1` | [MC-312913](https://bugs.mojang.com/browse/MC/issues/MC-312913) |
| `tent-dappled-forest1` | `abandoned_camp/tent/dappled_forest/tent_dappled_forest_1` | 同上 |
| `tent-dappled-forest2` | `abandoned_camp/tent/dappled_forest/tent_dappled_forest_2` | 同上 |
| `tent-dappled-forest3` | `abandoned_camp/tent/dappled_forest/tent_dappled_forest_3` | 同上 |
| `tent-dappled-forest4` | `abandoned_camp/tent/dappled_forest/tent_dappled_forest_4` | 同上 |
| `tent-dappled-forest6` | `abandoned_camp/tent/dappled_forest/tent_dappled_forest_6` | 同上 |
| `tent-savanna1` | `abandoned_camp/tent/savanna/tent_savanna_1` | 同上 |
| `tent-snowy-taiga10` | `abandoned_camp/tent/snowy_taiga/tent_snowy_taiga_10` | 同上 |
| `tent-taiga2` | `abandoned_camp/tent/taiga/tent_taiga_2` | 同上 |
| `tent-taiga7` | `abandoned_camp/tent/taiga/tent_taiga_7` | 同上 |
| `tent-windswept-forest6` | `abandoned_camp/tent/windswept_forest/tent_windswept_forest_6` | 同上 |

原始松木针叶林 2、3 号营地没有配置巨树；4 号营地和斑驳森林 7 号帐篷在问题报告中是树木能生成的对照，不属于这组修复。

## 范围与版本边界

此清单针对 Java 26.3 的实际资源。MC-170550 虽已在官方问题追踪器标记修复，其修复版本是 26.4 Snapshot 5，因此 26.3 仍需处理。MC-266778 的历史清单中已有删除或修好的文件，本补丁只针对 26.3 中仍存在的两个缺连接模板；带桥走道可能是开发遗留设计，本核心选择将它作为可关闭的附加片段恢复。

以下内容不在修复范围：

- MC-276199 的 Pedestal 四分区：官方判定为 Works As Intended。
- 未找到应当启用依据的旧远古城市墙角、村庄草地衰败模板、雪原村庄旧路口。
- 26.3 前已修复的林地府邸 `1x1_b5`、旧试炼片段以及已移除资源；基岩版专属问题。
- MC-306596 中原版已经能生成的 `savanna_medium_house_1`，以及没有复现到对应放置缺陷的其他村庄模板。九项已复现的道路内部连接问题在上表逐一列出，不对所有房屋统一放宽碰撞。

## 验证

补丁已通过重新应用和完整 Gradle 测试。本次新增 141 项回归测试全部通过；全项目共 10,774 项测试，10,683 项通过，91 项既有测试跳过，无失败。可在仓库根目录使用 Java 25 执行：

```sh
./gradlew applyAllPatches
./gradlew test createPaperclipJar
```

验证包括：

- 配置默认值、44 个独立开关、总开关、旧配置补齐、错误语法/类型/空值拒绝、原文件保留及启动快照。生成的 45 个布尔字段与本文默认 YAML 一致。
- 13 项模板修复在 4 个旋转、3 个种子下的 156 组真实 `JigsawPlacement` 开关对照；保留连接方向、碰撞和候选权重。测试隔离其他候选，以验证目标连接可达，不用于估计整个世界中的出现概率。
- 14 个树木模板在 4 个旋转、8 个种子下的 448 组真实树木放置对照；原版失败、修复成功，同时验证装饰保留、完整巨树树干及实心方块仍阻挡树干。
- 五种堆放装饰的高度边界、同种子方块和随机数一致性，以及嵌套、异常和并发调用后的作用域清理。
- 村庄基础空间的碰撞、重复占用、世界上下界和自定义资源保护。另对 1,200 个原版村庄进行独立生成对照，九种目标建筑恢复出现；关闭修复时，模板、位置、旋转和包围盒指纹与原版一致。
- 134 个 NBT、40 个 JSON 原版基线，以及模板和树木依赖被数据包替换时跳过修复。
- 使用最终 Paperclip 核心分别启动全新的开启、关闭测试世界，各通过 284 项检查：39 个新增注册表资源全部加载，27 个实际模板随总开关正确修复或恢复原版，并在真实区块线程放置营地、确认原有坐垫实体生成。两轮均正常停止且无 ERROR 日志。

单独使用 `/place template` 只能验证模板外观，不能替代上述拼图连接与树木特征测试；这些有限种子测试也不保证所有自然地形都能放下任意候选。
