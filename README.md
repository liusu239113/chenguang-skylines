# 都市建设模拟：山水之间

手机简化版城市建造模拟，玩法对标《都市天际线》核心循环，基于 UrhoX Lua 引擎开发的竖屏手游。

## 玩法

```
修路（拖拽） → 划分区（涂色） → 时间实时流动 → 分区自动长楼 → 小楼自动长高
```

- **道路**：从大道边按住拖拽修路，分区内只有邻路的格子才会长楼
- **分区**：住宅 / 商业 / 工业，涂色后随时间自动生长，需求越高长得越快
- **RCI 需求**：顶部住/商/工三条需求条实时反映缺口，驱动城市成长
- **时间**：实时流动（暂停 / 1x / 2x / 4x），每日结算收支、每月发布城建简报
- **服务设施**：公园 / 学校 / 诊所 / 广场，影响范围圈提升满意度与地价
- **推平**：拆楼、拆路、重规划
- **城市晋级**：村庄 → 小镇 → 集镇 → 城区 → 都市 → 大都会
- 建筑出生带弹跳动画；道路上有动态车流；市政政策系统；报纸式 UI 配站酷快乐体

## Android 原生版（Kotlin）

`android/` 下是 1:1 移植的 Android 原生应用（Kotlin + Jetpack Compose），玩法、数值、渲染细节与 Lua 版逐项对齐。

- 地图渲染：自定义 `MapRenderView`，Android Canvas 2D 还原 NanoVG 绘制（地形/装饰/路网/车流/微立体建筑/标签/幽灵预览）
- 手势：单指拖动平移、双指捏合缩放、工具拖拽笔刷（修路 / 涂分区 / 推平 / 放设施）
- UI 叠层：Compose 复刻报纸风三屏（主菜单 / 地图主界面 / 晨光简报）
- 字体：站酷快乐体（assets + res/font 各一份，Canvas 与 Compose 共用）
- 音效：SoundPool 播放 ogg（build / click / demolish / levelup / month）
- 构建：`android` 目录标准 Gradle 工程，minSdk 26 / targetSdk 35；推送到 main 会触发 `.github/workflows/android.yml` 产出 debug APK

## 技术要点

- 引擎：UrhoX（Lua 5.4），raw NanoVG 俯视格子地图 + urhox-libs/UI 叠层
- 地图 48×80 程序化生成：值噪声地形、河流湖泊、大道骨架
- 微立体（pseudo-3D）建筑渲染、生长动画、窗格点阵
- 事件驱动输入（TouchBegin/Move/End + Mouse 事件，触摸平台可靠）
- 站酷快乐体（OFL 许可，随包发布）+ MiSans/Twemoji 字符回退链

## 目录结构

```
scripts/            游戏代码（入口 main.lua）
  main.lua            启动、事件订阅、主循环
  Config.lua          全部玩法配置（时间/经济/成长/服务/配色）
  GameData.lua        实时城市模拟（tick 驱动）
  world/World.lua     世界模型：地形/路网/分区/建筑存储与规则
  world/Growth.lua    RCI 需求 + 分区自动成长引擎
  world/MapView.lua   NanoVG 地图渲染 + 相机 + 事件驱动输入
  Screens/            MapScreen（主界面）/ MainMenu / NewspaperScreen
  UIHelper.lua        报纸风 UI 组件工厂
  Sfx.lua             音效播放
assets/
  Fonts/              站酷快乐体
  Sounds/             音效（ogg）
.project/            项目配置
```

## 许可

- 游戏代码：仅供学习交流
- 字体：站酷快乐体（SIL OFL 1.1）
- 音效：AI 生成，可自由使用
