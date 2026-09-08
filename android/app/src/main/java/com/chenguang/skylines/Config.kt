package com.chenguang.skylines

import kotlin.math.max
import kotlin.math.min

// ============================================================================
// 游戏配置 (Game Config) — 与 scripts/Config.lua 1:1 对应
// 《都市天际线：晨光》— 手机简化版城市建造
// 核心循环（对标都市天际线）：修路 → 划分区 → 时间实时流动 → 分区自动长楼
// ============================================================================

/** 颜色：与 Lua 端 {r,g,b,a}（0..255）保持一致 */
data class RGBA(val r: Int, val g: Int, val b: Int, val a: Int = 255) {
    fun argb(alphaOverride: Int = a): Int =
        android.graphics.Color.argb(alphaOverride, r, g, b)

    /** 明暗缩放（对应 MapView.shade） */
    fun shade(k: Double): RGBA = RGBA(
        min(255, (r * k).toInt()),
        min(255, (g * k).toInt()),
        min(255, (b * k).toInt()),
        a
    )

    companion object {
        fun of(r: Int, g: Int, b: Int, a: Int = 255) = RGBA(r, g, b, a)
        val TRANSPARENT = RGBA(0, 0, 0, 0)
    }
}

object Config {

    // -----------------------------------------------------------------------
    // 世界信息（纯虚构）
    // -----------------------------------------------------------------------
    const val TITLE = "都市建设模拟：山水之间"
    const val SUBTITLE = "山水之间 · 晨光市城建日报"

    object World {
        const val country = "星辰联邦"
        const val city = "晨光市"
        const val playerRole = "规划师"
    }

    // -----------------------------------------------------------------------
    // 难度
    // -----------------------------------------------------------------------
    data class DifficultyDef(
        val key: String,
        val name: String,
        val incomeMul: Double,
        val upkeepMul: Double,
        val eventMul: Double
    )

    val DIFFICULTIES: List<DifficultyDef> = listOf(
        DifficultyDef("easy", "轻松", 1.20, 0.80, 0.5),
        DifficultyDef("normal", "标准", 1.00, 1.00, 1.0),
        DifficultyDef("hard", "困难", 0.80, 1.30, 2.0)
    )

    // -----------------------------------------------------------------------
    // 地图
    // -----------------------------------------------------------------------
    object MAP {
        const val cols = 48
        const val rows = 80
        const val baseCell = 20f
        const val minScale = 0.6f
        const val maxScale = 2.6f
    }

    // -----------------------------------------------------------------------
    // 实时时间
    // -----------------------------------------------------------------------
    object TIME {
        const val daySeconds = 2.0f
        val speeds = intArrayOf(0, 1, 2, 4)   // 暂停 / 1x / 2x / 4x
    }

    // -----------------------------------------------------------------------
    // 地形
    // -----------------------------------------------------------------------
    enum class Terrain(val key: String, val label: String) {
        GRASS("grass", "草地"),
        FOREST("forest", "林地"),
        WATER("water", "水域"),
        PLAIN("plain", "平原"),
        HILL("hill", "丘陵");

        companion object {
            fun from(key: String): Terrain = entries.firstOrNull { it.key == key } ?: GRASS
        }
    }

    // -----------------------------------------------------------------------
    // 分区
    // -----------------------------------------------------------------------
    data class ZoneDef(val key: String, val name: String, val cost: Int, val color: RGBA?)

    enum class ZoneKey(val key: String, val label: String, val cost: Int) {
        RESIDENTIAL("residential", "住宅区", 3),
        COMMERCIAL("commercial", "商业区", 4),
        INDUSTRIAL("industrial", "工业区", 3),
        NONE("none", "清除分区", 0);

        companion object {
            fun from(key: String): ZoneKey = entries.firstOrNull { it.key == key } ?: NONE
        }
    }

    val ZONE: Map<String, ZoneDef> = mapOf(
        "residential" to ZoneDef("residential", "住宅区", 3, COLORS.zoneResidential),
        "commercial" to ZoneDef("commercial", "商业区", 4, COLORS.zoneCommercial),
        "industrial" to ZoneDef("industrial", "工业区", 3, COLORS.zoneIndustrial),
        "none" to ZoneDef("none", "清除分区", 0, null)
    )

    // -----------------------------------------------------------------------
    // 建筑名字池（成长建筑按坐标确定性取名）
    // -----------------------------------------------------------------------
    object NAMES {
        val RES_PRE = listOf("翠湖", "梧桐", "晨曦", "望江", "桂香", "青藤", "云溪", "暖阳")
        val RES_SUF = listOf("小区", "公寓", "家园", "里弄", "新村")
        val COM_PRE = listOf("兴旺", "百汇", "惠民", "大众", "老街", "新街", "中心", "金源")
        val COM_SUF = listOf("超市", "百货", "食府", "咖啡", "药房", "书店", "面馆")
        val IND_PRE = listOf("永盛", "恒达", "联华", "宏远", "振华", "顺达")
        val IND_SUF = listOf("工厂", "制造", "五金", "纺织", "食品厂")
    }

    // -----------------------------------------------------------------------
    // 道路
    // -----------------------------------------------------------------------
    data class RoadDef(val key: String, val name: String, val cost: Int)

    val ROAD: Map<String, RoadDef> = mapOf(
        "local" to RoadDef("local", "单车道", 8),
        "avenue" to RoadDef("avenue", "双车道", 20)
    )

    // -----------------------------------------------------------------------
    // 成长建筑
    // -----------------------------------------------------------------------
    data class LevelDef(val cap: Int, val income: Int, val pollution: Int = 0)
    data class GrownDef(val name: String, val levels: List<LevelDef>)

    val GROWN: Map<String, GrownDef> = mapOf(
        "residential" to GrownDef("住宅", listOf(
            LevelDef(12, 4), LevelDef(36, 11), LevelDef(90, 26)
        )),
        "commercial" to GrownDef("商铺", listOf(
            LevelDef(8, 14), LevelDef(22, 38), LevelDef(50, 84)
        )),
        "industrial" to GrownDef("工厂", listOf(
            LevelDef(10, 20, 2), LevelDef(26, 50, 4), LevelDef(60, 110, 7)
        ))
    )

    // -----------------------------------------------------------------------
    // 服务设施
    // -----------------------------------------------------------------------
    // 设施类别（决定覆盖热力图与负面效果分组）
    object ServiceCat {
        const val AMENITY = "amenity"     // 公园/广场（满意度/地价）
        const val POWER = "power"         // 电力
        const val WATER = "water"         // 供水
        const val GARBAGE = "garbage"     // 垃圾处理
        const val HEALTH = "health"       // 医疗
        const val EDUCATION = "education" // 教育
        const val SAFETY = "safety"       // 消防/安全
    }

    data class ServiceDef(
        val id: String,
        val name: String,
        val cost: Int,
        val upkeep: Int,
        val radius: Int,
        val happy: Int,
        val landValue: Boolean,
        val sizeW: Int,
        val sizeH: Int,
        val desc: String,
        val category: String = ServiceCat.AMENITY,
        val unlockPop: Int = 0,           // 人口达到后解锁（里程碑）
        val pollution: Int = 0            // 设施自身污染（燃煤电厂等）
    )

    val SERVICES: List<ServiceDef> = listOf(
        // ---- 生活品质（开局可用） ----
        ServiceDef("park", "公园", 260, 4, 4, 5, true, 1, 1,
            "小绿地，提升周边满意度与地价。", ServiceCat.AMENITY, 0),
        ServiceDef("plaza", "广场", 900, 8, 5, 8, true, 2, 2,
            "市民广场，显著提升满意度。", ServiceCat.AMENITY, 500),
        // ---- 电力 ----
        ServiceDef("wind_farm", "风电场", 500, 6, 6, 0, false, 1, 1,
            "清洁风电，覆盖周边供电。", ServiceCat.POWER, 0),
        ServiceDef("solar_plant", "太阳能电站", 800, 5, 7, 0, false, 2, 2,
            "大面积光伏，供电半径更大。", ServiceCat.POWER, 300),
        ServiceDef("coal_plant", "燃煤电厂", 1200, 18, 10, -3, false, 2, 2,
            "供电半径大，但产生污染。", ServiceCat.POWER, 0, 8),
        // ---- 供水 ----
        ServiceDef("water_tower", "水塔", 300, 4, 5, 0, false, 1, 1,
            "为周边城区供水。", ServiceCat.WATER, 0),
        ServiceDef("pump_station", "水泵站", 600, 7, 8, 0, false, 1, 1,
            "大范围供水。", ServiceCat.WATER, 150),
        // ---- 垃圾 ----
        ServiceDef("landfill", "垃圾场", 350, 7, 6, 0, false, 1, 1,
            "收集周边生活垃圾，防止堆积。", ServiceCat.GARBAGE, 100),
        // ---- 医疗 ----
        ServiceDef("clinic", "诊所", 640, 12, 7, 5, false, 1, 1,
            "基础医疗，覆盖区满意度提升。", ServiceCat.HEALTH, 0),
        ServiceDef("hospital", "医院", 900, 15, 8, 6, true, 2, 2,
            "大型医疗，覆盖更广、满意度更高。", ServiceCat.HEALTH, 700),
        // ---- 教育 ----
        ServiceDef("school", "学校", 520, 10, 7, 4, true, 2, 2,
            "教育覆盖，满意度与地价提升。", ServiceCat.EDUCATION, 0),
        ServiceDef("middle_school", "中学", 700, 9, 8, 4, true, 2, 2,
            "进阶教育，提升商业与工业效益。", ServiceCat.EDUCATION, 300),
        // ---- 安全 ----
        ServiceDef("fire_station", "消防站", 600, 10, 7, 0, false, 1, 1,
            "扑灭火灾，保护城区。", ServiceCat.SAFETY, 300)
    )

    // -----------------------------------------------------------------------
    // 资源 / 成长参数
    // -----------------------------------------------------------------------
    object RESOURCES {
        const val fundsStart = 5000.0
        const val happinessStart = 60.0
        const val happinessMin = 0.0
        const val happinessMax = 100.0
    }

    object GROWTH {
        const val tickSeconds = 1.6
        const val spawnChance = 0.55
        const val upgradeChance = 0.30
        const val demandMin = 0.15
    }

    // -----------------------------------------------------------------------
    // 财政
    // -----------------------------------------------------------------------
    object ECONOMY {
        const val taxPerPopPerDay = 0.04
        const val baseIncomePerDay = 2.5
        const val upkeepPerRoadDay = 0.006
        const val happinessDecayDay = 0.10
        const val pollutionHappy = 0.045
        const val occupancyPerDay = 0.06
    }

    // -----------------------------------------------------------------------
    // 税收（RCI 三税率；10% 为基准，上下限 5~15）
    // -----------------------------------------------------------------------
    object TAX {
        const val min = 5
        const val max = 15
        const val default = 10
        const val happyPerPoint = 1.2   // 税率每超基准 1 点，满意度目标 -1.2
    }

    // -----------------------------------------------------------------------
    // 覆盖系统的负面惩罚
    // -----------------------------------------------------------------------
    object COVERAGE {
        const val powerHappyPenalty = 15.0   // 完全缺电时满意度惩罚
        const val waterHappyPenalty = 15.0
        const val garbageHappyPenalty = 10.0
        const val powerIncomeFloor = 0.4     // 缺电时商业/工业收入下限比例
        const val fireChancePerDay = 0.02    // 每日无消防覆盖建筑起火概率
    }

    // -----------------------------------------------------------------------
    // 市政贷款
    // -----------------------------------------------------------------------
    object LOAN {
        const val amount = 800.0      // 借款额（万）
        const val dailyRepay = 30.0   // 每日自动还款（万）
        const val cooldown = 90       // 还清后冷却天数
    }

    // -----------------------------------------------------------------------
    // 成就（长期目标，达成发新闻 + 奖金）
    // -----------------------------------------------------------------------
    data class AchievementDef(
        val id: String,
        val name: String,
        val desc: String,
        val reward: Int,
        val type: String,           // pop | buildings | funds | happiness
        val threshold: Double
    )

    val ACHIEVEMENTS: List<AchievementDef> = listOf(
        AchievementDef("pop100", "初具规模", "人口达到 100", 300, "pop", 100.0),
        AchievementDef("pop500", "集镇兴起", "人口达到 500", 800, "pop", 500.0),
        AchievementDef("pop1000", "千人之城", "人口达到 1000", 1500, "pop", 1000.0),
        AchievementDef("pop4000", "都市气象", "人口达到 4000", 5000, "pop", 4000.0),
        AchievementDef("pop10000", "万人大都会", "人口达到 10000", 10000, "pop", 10000.0),
        AchievementDef("bld50", "拔地而起", "建成 50 栋建筑", 500, "buildings", 50.0),
        AchievementDef("bld200", "百业兴旺", "建成 200 栋建筑", 1500, "buildings", 200.0),
        AchievementDef("funds10000", "家底殷实", "资金达到 10000 万", 1000, "funds", 10000.0),
        AchievementDef("happy85", "安居乐业", "满意度达到 85", 1000, "happiness", 85.0),
        AchievementDef("happy95", "人间天堂", "满意度达到 95", 2500, "happiness", 95.0)
    )

    // -----------------------------------------------------------------------
    // 政策
    // -----------------------------------------------------------------------
    data class PolicyEffect(
        val happy: Int = 0,
        val taxMul: Double = 1.0,
        val incomeMul: Double = 1.0,
        val cost: Int = 0
    )

    data class PolicyDef(
        val id: String,
        val name: String,
        val desc: String,
        val effect: PolicyEffect,
        val days: Int,
        val cooldown: Int
    )

    val POLICIES: List<PolicyDef> = listOf(
        PolicyDef("cut_tax", "减负降税", "满意度 +12，30 日内税收 -25%",
            PolicyEffect(happy = 12, taxMul = 0.75), 30, 60),
        PolicyDef("raise_tax", "增收节支", "30 日内税收 +30%，满意度 -10",
            PolicyEffect(happy = -10, taxMul = 1.30), 30, 60),
        PolicyDef("greening", "绿化行动", "满意度 +8，支出 200 万",
            PolicyEffect(happy = 8, cost = 200), 1, 45),
        PolicyDef("bizboost", "营商激励", "45 日内商业/工业收入 +25%",
            PolicyEffect(incomeMul = 1.25), 45, 90),
        PolicyDef("welfare", "民生改善", "满意度 +15，支出 400 万",
            PolicyEffect(happy = 15, cost = 400), 1, 60)
    )

    // -----------------------------------------------------------------------
    // 城市阶段
    // -----------------------------------------------------------------------
    data class CityLevelDef(val level: Int, val name: String, val popReq: Int, val reward: Int = 0)

    val CITY_LEVELS: List<CityLevelDef> = listOf(
        CityLevelDef(1, "村庄", 0, 0),
        CityLevelDef(2, "小镇", 150, 600),
        CityLevelDef(3, "集镇", 500, 1500),
        CityLevelDef(4, "城区", 1500, 4000),
        CityLevelDef(5, "都市", 4000, 10000),
        CityLevelDef(6, "大都会", 10000, 25000)
    )

    // -----------------------------------------------------------------------
    // 配色
    // -----------------------------------------------------------------------
    object COLORS {
        // UI 基底（报纸风）
        val uiBackdrop = RGBA(223, 216, 199, 255)
        val panelWhite = RGBA(248, 244, 232, 248)
        val panelShadow = RGBA(96, 86, 66, 70)
        val textDark = RGBA(44, 40, 34, 255)
        val textMid = RGBA(110, 102, 88, 255)
        val textFaint = RGBA(152, 144, 128, 255)
        val accentGreen = RGBA(74, 112, 76, 255)
        val accentRed = RGBA(172, 56, 50, 255)
        val accentGold = RGBA(176, 140, 62, 255)
        val accentBlue = RGBA(68, 100, 158, 255)
        val accentSoftBg = RGBA(244, 224, 210, 255)
        val border2 = RGBA(220, 212, 194, 255)
        val chipBg = RGBA(250, 246, 236, 255)
        val veil = RGBA(46, 40, 32, 130)

        // 地形
        val grass = RGBA(168, 196, 158, 255)
        val grassAlt = RGBA(158, 188, 148, 255)
        val forest = RGBA(120, 158, 116, 255)
        val water = RGBA(112, 168, 196, 255)
        val waterAlt = RGBA(100, 156, 186, 255)
        val plain = RGBA(186, 198, 168, 255)
        val hill = RGBA(176, 186, 156, 255)

        // 道路
        val roadLocal = RGBA(214, 210, 198, 255)
        val roadAvenue = RGBA(230, 197, 104, 255)

        // 分区
        val zoneResidential = RGBA(233, 217, 166, 255)
        val zoneCommercial = RGBA(224, 200, 200, 255)
        val zoneIndustrial = RGBA(196, 188, 168, 255)

        // 建筑体块
        val bResidential = RGBA(236, 214, 150, 255)
        val bCommercial = RGBA(226, 176, 150, 255)
        val bIndustrial = RGBA(200, 190, 165, 255)
        val bService = RGBA(130, 176, 128, 255)
        val bCivic = RGBA(150, 170, 205, 255)

        // 选中 / 预览
        val selectFill = RGBA(172, 56, 50, 40)
        val selectStroke = RGBA(172, 56, 50, 255)
        val hoverStroke = RGBA(255, 255, 255, 160)
        val ghostOk = RGBA(96, 200, 120, 120)
        val ghostBad = RGBA(200, 70, 70, 120)
    }

    // 微立体（建筑伪 3D）
    object BUILD {
        const val heightScale = 0.20
        const val sideShade = 0.60        // 侧面明暗（越小越暗，立体感越强）
        const val roofLight = 1.15        // 顶面提亮
        const val minPx = 3
    }

    // 工具函数 -------------------------------------------------------------
    fun clamp(v: Float, lo: Float, hi: Float): Float = max(lo, min(hi, v))
    fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))
    fun clamp(v: Int, lo: Int, hi: Int): Int = max(lo, min(hi, v))
}
