package com.dshx.game.she

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

    fun mix(other: RGBA, t: Float): RGBA {
        val k = t.coerceIn(0f, 1f)
        return RGBA(
            (r + (other.r - r) * k).toInt(),
            (g + (other.g - g) * k).toInt(),
            (b + (other.b - b) * k).toInt(),
            (a + (other.a - a) * k).toInt()
        )
    }

    fun withAlpha(alpha: Int): RGBA = RGBA(r, g, b, alpha.coerceIn(0, 255))

    companion object {
        fun of(r: Int, g: Int, b: Int, a: Int = 255) = RGBA(r, g, b, a)
        val TRANSPARENT = RGBA(0, 0, 0, 0)
    }
}

object Config {

    // -----------------------------------------------------------------------
    // 世界信息（纯虚构）
    // -----------------------------------------------------------------------
    const val TITLE = "城守模拟：古邦沙盘"
    const val SUBTITLE = "城守模拟 · 云川城营造志"

    object World {
        const val country = "大衍王朝"
        const val city = "云川城"
        const val playerRole = "城主"
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
        const val daySeconds = 16.0f         // 1x 下一天的现实秒数（比原先慢一半）
        val speeds = intArrayOf(0, 1, 2, 3)   // 暂停 / 1x / 2x / 3x
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
        RESIDENTIAL("residential", "住宅区", 6),
        COMMERCIAL("commercial", "商业区", 8),
        INDUSTRIAL("industrial", "工业区", 7),
        OFFICE("office", "办公区", 10),
        NONE("none", "清除分区", 0);

        companion object {
            fun from(key: String): ZoneKey = entries.firstOrNull { it.key == key } ?: NONE
        }
    }

    val ZONE: Map<String, ZoneDef> = mapOf(
        "residential" to ZoneDef("residential", "民坊", 6, COLORS.zoneResidential),
        "commercial" to ZoneDef("commercial", "市肆", 8, COLORS.zoneCommercial),
        "industrial" to ZoneDef("industrial", "工坊", 7, COLORS.zoneIndustrial),
        "office" to ZoneDef("office", "官署区", 10, COLORS.zoneOffice),
        "none" to ZoneDef("none", "清除坊界", 0, null)
    )

    // -----------------------------------------------------------------------
    // 建筑名字池（成长建筑按坐标确定性取名）
    // -----------------------------------------------------------------------
    object NAMES {
        val RES_PRE = listOf("翠湖", "梧桐", "晨曦", "望江", "桂香", "青藤", "云溪", "暖阳")
        val RES_SUF = listOf("里巷", "宅院", "小院", "胡同", "民居")
        val COM_PRE = listOf("兴旺", "百汇", "惠民", "大众", "老街", "新街", "中心", "金源")
        val COM_SUF = listOf("杂货", "绸庄", "酒楼", "茶肆", "药铺", "书坊", "面馆")
        val IND_PRE = listOf("永盛", "恒达", "联华", "宏远", "振华", "顺达")
        val IND_SUF = listOf("作坊", "织坊", "铁铺", "染坊", "糕饼坊")
        val OFF_PRE = listOf("星辉", "云台", "启明", "瀚海", "银座", "通衢")
        val OFF_SUF = listOf("府衙", "官署", "公廨", "楼")
    }

    // -----------------------------------------------------------------------
    // 道路
    // -----------------------------------------------------------------------
    data class RoadDef(
        val key: String,
        val name: String,
        val cost: Int,
        val capacity: Int,
        val speed: Int,
        val noise: Int,
        val upkeep: Double
    )

    val ROAD: Map<String, RoadDef> = mapOf(
        "dirt" to RoadDef("dirt", "土路", 0, 6, 30, 1, 0.008),
        "local" to RoadDef("local", "石板巷", 8, 14, 40, 2, 0.018),
        "avenue" to RoadDef("avenue", "石板大街", 20, 28, 60, 4, 0.032),
        "highway" to RoadDef("highway", "官道", 40, 48, 100, 7, 0.055),
        "overpass" to RoadDef("overpass", "虹桥", 55, 46, 90, 4, 0.062),
        "metro" to RoadDef("metro", "地道", 6, 36, 70, 1, 0.022),
        "rail" to RoadDef("rail", "石轨", 8, 20, 80, 3, 0.028)
    )

    // -----------------------------------------------------------------------
    // 成长建筑
    // -----------------------------------------------------------------------
    data class LevelDef(val cap: Int, val income: Int, val pollution: Int = 0)
    data class GrownDef(val name: String, val levels: List<LevelDef>)

    val GROWN: Map<String, GrownDef> = mapOf(
        "residential" to GrownDef("民居", listOf(
            LevelDef(4, 1), LevelDef(8, 2), LevelDef(16, 4)
        )),
        "commercial" to GrownDef("商铺", listOf(
            LevelDef(3, 2), LevelDef(8, 4), LevelDef(16, 8)
        )),
        "industrial" to GrownDef("作坊", listOf(
            LevelDef(4, 2, 2), LevelDef(10, 5, 4), LevelDef(20, 9, 7)
        )),
        "office" to GrownDef("官署", listOf(
            LevelDef(6, 5), LevelDef(12, 11), LevelDef(24, 20)
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
        const val TRANSIT = "transit"     // 车马
        const val DEATH = "death"         // 殡葬
        const val LANDMARK = "landmark"   // 独特建筑
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
        val pollution: Int = 0,           // 设施自身污染（燃煤电厂等）
        val powerCap: Int = 0,            // 发电容量（建筑数）
        val waterCap: Int = 0,            // 供水容量
        val garbageCap: Int = 0,          // 垃圾处理容量（按建筑收运量计）
        val deathCap: Int = 0,            // 殡葬容量
        val pollutionRadius: Int = 0,     // 污染扩散半径（靠距离衰减，不再压格子）
        val nearWater: Boolean = false,   // 必须建在水域旁（抽水/排污）
        val needsRoad: Boolean = true     // 是否需要临路（只有要派车出入的设施才需要）
    )

    val SERVICES: List<ServiceDef> = listOf(
        // ---- 生活品质 ----
        ServiceDef("park", "园林", 260, 10, 4, 5, true, 1, 1,
            "园林草木吸收浊气，抬升周边地价与民心。", ServiceCat.AMENITY, 0),
        ServiceDef("plaza", "鼓楼广场", 1400, 28, 5, 8, true, 2, 2,
            "城中鼓楼广场，显著提升民心与地价。", ServiceCat.AMENITY, 500),
        // ---- 电力（容量决定能否撑住全城） ----
        ServiceDef("wind_farm", "风车坊", 500, 18, 6, 0, false, 1, 1,
            "借风力推磨生电。只产灵力，靠街巷预埋灵线/地脉送到屋舍，容量 18。", ServiceCat.POWER, 0, 0, 18, 0, needsRoad = false),
        ServiceDef("solar_plant", "日曜台", 1600, 28, 7, 0, false, 2, 2,
            "聚日精为电。只产灵力，容量 36，无浊气，靠街巷送电。", ServiceCat.POWER, 300, 0, 36, 0, needsRoad = false),
        ServiceDef("coal_plant", "炭窑坊", 1800, 56, 10, -3, false, 2, 2,
            "烧炭生火，容量 70。只产灵力，靠街巷送电，可放远郊。", ServiceCat.POWER, 0, 8, 70, 0, 0, 0, 9, needsRoad = false),
        ServiceDef("nuclear_plant", "天火坛", 9800, 120, 14, 0, false, 3, 3,
            "引天火之力，容量 180，供养昂贵。只产灵力，靠街巷送电。", ServiceCat.POWER, 4000, 1, 180, 0, 0, 0, 12, needsRoad = false),
        // ---- 供水（必须建在水域旁取水，靠水管管网送水） ----
        ServiceDef("water_tower", "水楼", 300, 12, 5, 0, false, 1, 1,
            "须临水而建，容量 18，靠水渠管网送水。", ServiceCat.WATER, 0, 0, 0, 18, 0, 0, 0, true, needsRoad = false),
        ServiceDef("pump_station", "水车坊", 600, 18, 8, 0, false, 1, 1,
            "须临水而建，以水车引活水，容量 40，靠水渠管网送水。", ServiceCat.WATER, 0, 0, 0, 40, 0, 0, 0, true, needsRoad = false),
        // ---- 垃圾 ----
        ServiceDef("landfill", "秽物场", 350, 22, 6, 0, false, 1, 1,
            "收运 60 户。秽气按距离衰减，可放远郊。", ServiceCat.GARBAGE, 40, 3, 0, 0, 60, 0, 5),
        ServiceDef("incinerator", "焚秽窑", 2200, 52, 8, -2, false, 2, 2,
            "收运 160 户并产灵力 12，烟尘按距离衰减。", ServiceCat.GARBAGE, 300, 5, 12, 0, 160, 0, 7),
        ServiceDef("waste_plant", "净秽院", 6500, 115, 10, -3, false, 3, 3,
            "大型净秽之所：收运 420 户并产灵力 20。城大了就靠它，烟尘按距离衰减，适合放远郊。",
            ServiceCat.GARBAGE, 900, 4, 20, 0, 420, 0, 8),
        // ---- 医疗 ----
        ServiceDef("clinic", "医馆", 640, 28, 7, 5, false, 1, 1,
            "坐堂问诊，覆盖区康健与民心提升。", ServiceCat.HEALTH, 25),
        ServiceDef("hospital", "医署", 2800, 72, 8, 6, true, 2, 2,
            "官办医署，覆盖更广。", ServiceCat.HEALTH, 700),
        // ---- 教育（驱动产业升级） ----
        ServiceDef("school", "蒙学", 520, 32, 7, 4, true, 2, 2,
            "开蒙识字，缓慢提升识字人口。", ServiceCat.EDUCATION, 20),
        ServiceDef("middle_school", "书院", 1600, 48, 8, 4, true, 2, 2,
            "讲学之所，市肆与工坊需要。", ServiceCat.EDUCATION, 300),
        ServiceDef("university", "国子监", 4200, 96, 10, 6, true, 3, 3,
            "最高学府，解锁高等技艺作坊。", ServiceCat.EDUCATION, 600),
        // ---- 安全 ----
        ServiceDef("fire_station", "水龙局", 600, 24, 7, 0, false, 1, 1,
            "扑灭火患，无覆盖则屋舍焚毁。", ServiceCat.SAFETY, 50),
        ServiceDef("police", "捕房", 700, 28, 8, 3, false, 1, 1,
            "缉盗安民，提升安全感与地价。", ServiceCat.SAFETY, 80),
        // ---- 车马 ----
        ServiceDef("bus_stop", "车马站", 220, 10, 6, 4, false, 1, 1,
            "供车马停歇，缓解街市拥挤。", ServiceCat.TRANSIT, 150),
        ServiceDef("metro", "地道口", 4800, 88, 10, 6, true, 2, 2,
            "地下通道，大运量，显著疏解人潮。", ServiceCat.TRANSIT, 600),
        ServiceDef("rail_station", "驿站", 6200, 96, 10, 8, true, 2, 2,
            "连通城外，货运与客旅。", ServiceCat.TRANSIT, 1500),
        ServiceDef("harbor", "码头", 5600, 72, 8, 6, true, 2, 2,
            "滨水货运码头，工坊外销加成。", ServiceCat.TRANSIT, 1500),
        ServiceDef("airport", "飞舟坞", 16000, 180, 12, 10, true, 3, 3,
            "飞舟起落之所，游历收入与民心。", ServiceCat.TRANSIT, 4000),
        // ---- 排污 / 殡葬 / 监狱 ----
        ServiceDef("sewage", "净水渠", 2400, 58, 8, 0, false, 2, 2,
            "须临水而建，与污水渠共用管网，净全城污水。", ServiceCat.WATER, 60, 2, 0, 0, 0, 0, 6, true, needsRoad = false),
        ServiceDef("cemetery", "义庄", 400, 4, 6, -1, false, 2, 2,
            "安葬 80。阴气按距离衰减，可放城郊。", ServiceCat.DEATH, 80, 0, 0, 0, 0, 80, 3),
        ServiceDef("crematorium", "火化场", 900, 12, 8, 0, false, 1, 1,
            "火化 200，无堆积。烟尘按距离衰减。", ServiceCat.DEATH, 300, 0, 0, 0, 0, 200, 4),
        ServiceDef("prison", "牢狱", 3600, 48, 8, 0, false, 2, 2,
            "关押人犯。容量满则人犯被放出。", ServiceCat.SAFETY, 400),
        // ---- 独特建筑 ----
        ServiceDef("stock_exchange", "商帮会馆", 8800, 64, 10, 8, true, 3, 3,
            "全城市肆赋税 +12%，地价上升。", ServiceCat.LANDMARK, 1000),
        ServiceDef("tv_tower", "观星台", 7200, 52, 12, 10, true, 2, 2,
            "登台远眺，民心与游历收入上升。", ServiceCat.LANDMARK, 1500, needsRoad = false),
        ServiceDef("stadium", "演武场", 8600, 78, 10, 8, true, 3, 3,
            "比武赛会吸引来客，节庆消费加成。", ServiceCat.LANDMARK, 2000)
    )

    // -----------------------------------------------------------------------
    // 资源 / 成长参数
    // -----------------------------------------------------------------------
    object RESOURCES {
        const val fundsStart = 4000.0
        const val happinessStart = 60.0
        const val happinessMin = 0.0
        const val happinessMax = 100.0
    }

    /** 沙盒 GM：临时测试用，资金拉满、人口锁定、满意度不掉 */
    object SANDBOX {
        const val FUNDS = 999999.0
        const val pop = 5000
        const val happy = 99.0
    }

    object GROWTH {
        const val tickSeconds = 1.6
        const val spawnChance = 0.52
        const val upgradeChance = 0.22
        const val demandMin = 0.12
        const val abandonHappy = 28.0
        const val upgradeAgeDays = 12
        const val landValueUpgrade = 8
        const val unlockPerPop = 20   // 每增加这么多人，解锁圈外扩一环
    }

    // -----------------------------------------------------------------------
    // 府库
    // -----------------------------------------------------------------------
    object ECONOMY {
        const val taxPerPopPerDay = 0.010
        const val baseIncomePerDay = 0.02
        const val upkeepPerRoadDay = 0.070
        const val happinessDecayDay = 0.10
        /** 人口到此之前维护/造价按前期，之后逐步加重 */
        const val midPop = 180
        const val latePop = 700
        const val pollutionHappy = 0.045
        const val occupancyPerDay = 0.06
    }

    // -----------------------------------------------------------------------
    // 赋税（三税赋；10% 为基准，上下限 5~15）
    // -----------------------------------------------------------------------
    object TAX {
        const val min = 5
        const val max = 15
        const val default = 10
        const val happyPerPoint = 1.2   // 赋税每超基准 1 点，民心目标 -1.2
    }

    // -----------------------------------------------------------------------
    // 覆盖系统的负面惩罚
    // -----------------------------------------------------------------------
    object COVERAGE {
        const val powerHappyPenalty = 15.0   // 完全缺电时满意度惩罚
        const val waterHappyPenalty = 15.0
        const val garbageHappyPenalty = 10.0
        const val powerIncomeFloor = 0.4     // 缺电时商业/工业收入下限比例
        const val fireChancePerDay = 0.012    // 每日无消防覆盖建筑起火概率（人口够解锁消防后才发生）
    }

    // -----------------------------------------------------------------------
    // 官贷
    // -----------------------------------------------------------------------
    object LOAN {
        const val manualAmount = 500.0
        const val manualDaily = 32.0     // 手动贷：高息，约 16 天还清
        const val adAmount = 420.0
        const val adDaily = 12.0         // 广告贷：低息，约 35 天还清
        const val cooldown = 45
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
        AchievementDef("pop2000", "市井气象", "人口达到 2000", 2500, "pop", 2000.0),
        AchievementDef("pop4000", "通都气象", "人口达到 4000", 5000, "pop", 4000.0),
        AchievementDef("pop6000", "通都大邑", "人口达到 6000", 7000, "pop", 6000.0),
        AchievementDef("pop10000", "万人大邑", "人口达到 10000", 10000, "pop", 10000.0),
        AchievementDef("bld50", "起屋架梁", "建成 50 栋屋舍", 500, "buildings", 50.0),
        AchievementDef("bld200", "百业兴旺", "建成 200 栋屋舍", 1500, "buildings", 200.0),
        AchievementDef("bld300", "楼台林立", "建成 300 栋屋舍", 2500, "buildings", 300.0),
        AchievementDef("funds10000", "家底殷实", "府库达到 10000 万", 1000, "funds", 10000.0),
        AchievementDef("funds30000", "富可敌国", "府库达到 30000 万", 2000, "funds", 30000.0),
        AchievementDef("happy70", "安居乐业", "民心达到 70", 600, "happiness", 70.0),
        AchievementDef("happy80", "丰衣足食", "民心达到 80", 1200, "happiness", 80.0),
        AchievementDef("happy85", "太平盛世", "民心达到 85", 1000, "happiness", 85.0),
        AchievementDef("happy95", "人间乐土", "民心达到 95", 2500, "happiness", 95.0),
        AchievementDef("exam1", "初通营造", "通过 1 次营造考评", 400, "exam", 1.0),
        AchievementDef("exam3", "营造能手", "累计通过 3 次营造考评", 900, "exam", 3.0),
        AchievementDef("mail8", "有求必应", "处理 8 封百姓上书", 700, "mail", 8.0),
        AchievementDef("school60", "书香满城", "识字率达到 60%", 800, "school", 60.0),
        AchievementDef("school80", "文风鼎盛", "识字率达到 80%", 1600, "school", 80.0),
        AchievementDef("road80", "街巷纵横", "道路达到 80 格", 600, "roads", 80.0),
        AchievementDef("svc12", "百工齐备", "建成 12 座营造设施", 900, "services", 12.0)
    )

    // -----------------------------------------------------------------------
    // 随机事件（由城市状态触发，不是无脑随机）
    //   cond: power(缺电) / water(缺水) / health(缺医疗) / happy(民心低)
    //         boom(商业需求高) / random(常态随机)
    // -----------------------------------------------------------------------
    data class EventDef(
        val id: String,
        val name: String,
        val desc: String,
        val duration: Int,
        val happy: Double,
        val incomeMul: Double,
        val cond: String
    )

    val EVENTS: List<EventDef> = listOf(
        EventDef("blackout", "民宅断电", "民居未接灵线或灵力不足，百姓上书求送电。", 3, -8.0, 0.92, "power"),
        EventDef("pipe", "民宅缺水", "民居未通水渠，日用之水告急。", 3, -8.0, 0.95, "water"),
        EventDef("clinic", "求医无门", "附近没有医馆/医署，百姓看病困难。", 4, -6.0, 1.0, "health"),
        EventDef("school", "蒙学难求", "附近没有学堂，孩童无处开蒙。", 4, -5.0, 1.0, "school"),
        EventDef("trash", "秽物堆积", "清运不周，秽物堆到街边。", 3, -5.0, 0.98, "garbage"),
        EventDef("shop", "市肆冷清", "市肆缺电缺水，门可罗雀。", 4, -3.0, 0.90, "shop"),
        EventDef("factory", "工坊停工", "工坊缺电，炉火难起。", 4, -4.0, 0.82, "factory")
    )

    // -----------------------------------------------------------------------
    // 城政任务（限时目标，完成刷新）
    // -----------------------------------------------------------------------
    data class QuestDef(val type: String, val name: String, val reward: Int, val target: Double)

    val QUESTS: List<QuestDef> = listOf(
        QuestDef("pop", "人口达到", 500, 300.0),
        QuestDef("pop", "人口达到", 900, 800.0),
        QuestDef("pop", "人口达到", 2000, 1500.0),
        QuestDef("buildings", "建成建筑", 600, 40.0),
        QuestDef("buildings", "建成建筑", 1200, 100.0),
        QuestDef("funds", "资金达到", 600, 12000.0),
        QuestDef("happy", "民心达到", 800, 70.0),
        QuestDef("happy", "民心达到", 1500, 88.0),
        QuestDef("edu", "识字达到", 700, 55.0),
        QuestDef("edu", "识字达到", 1200, 75.0),
        QuestDef("jobs", "岗位达到", 900, 600.0),
        QuestDef("trade", "月通商额达到", 1200, 1500.0)
    )

    // -----------------------------------------------------------------------
    // 产业专精（刷在已有分区上，立刻扣费、格子变色、第二天账本增收）
    // -----------------------------------------------------------------------
    data class SpecDef(
        val id: String,
        val name: String,
        val zone: String,
        val cost: Int,
        val income: Double,
        val happy: Double,
        val pollution: Double,
        val edu: Double,
        val color: RGBA,
        val desc: String
    )

    val SPECS: List<SpecDef> = listOf(
        SpecDef(
            "tourism", "文旅街区", "residential", 8, 0.22, 0.08, 0.0, 0.0,
            RGBA(210, 140, 55), "刷住宅区。游客消费进账，满意微升。"
        ),
        SpecDef(
            "retail", "商圈", "commercial", 10, 0.36, 0.02, 0.0, 0.0,
            RGBA(55, 115, 200), "刷商业区。店铺加营业额，当天账本能看见。"
        ),
        SpecDef(
            "factory", "工业园", "industrial", 12, 0.48, -0.04, 0.9, 0.0,
            RGBA(155, 85, 40), "刷工业区。产出高，污染也上去。"
        ),
        SpecDef(
            "campus", "科教园", "office", 14, 0.30, 0.04, 0.0, 0.06,
            RGBA(55, 130, 95), "刷办公区。教育每天涨，办公收入升。"
        )
    )

    fun specOf(id: String): SpecDef? = if (id.isEmpty()) null else SPECS.firstOrNull { it.id == id }

    // -----------------------------------------------------------------------
    // 政令（启用后必须持续改数值，不能只弹一次简报）
    // -----------------------------------------------------------------------
    data class PolicyEffect(
        val happy: Int = 0,                 // 生效期内每日叠加到满意度目标
        val taxMul: Double = 1.0,           // 赋税倍率
        val incomeMul: Double = 1.0,        // 产业收入倍率
        val cost: Int = 0,                  // 一次性府库支出
        val pollutionMul: Double = 1.0,     // 污染倍率
        val demandR: Double = 1.0,          // 住宅需求倍率
        val demandC: Double = 1.0,
        val demandI: Double = 1.0,
        val demandO: Double = 1.0,
        val powerUseMul: Double = 1.0,      // 用电倍率
        val trafficMul: Double = 1.0,       // 拥堵倍率
        val fireMul: Double = 1.0,          // 火灾概率倍率
        val upgradeMul: Double = 1.0,       // 升级速度倍率
        val upkeepMul: Double = 1.0         // 维护费倍率
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
        PolicyDef("cut_tax", "轻徭薄赋", "30 日：赋赋税入 -25%，民心目标 +12，民坊需求 +15%",
            PolicyEffect(happy = 12, taxMul = 0.75, demandR = 1.15), 30, 45),
        PolicyDef("raise_tax", "开源节流", "30 日：赋税 +30%，民心 -10，三项需求 -12%",
            PolicyEffect(happy = -10, taxMul = 1.30, demandR = 0.88, demandC = 0.88, demandI = 0.88), 30, 45),
        PolicyDef("greening", "植木造林", "30 日：浊气 -40%，民心 +8。一次性支出 200 万",
            PolicyEffect(happy = 8, cost = 200, pollutionMul = 0.60), 30, 40),
        PolicyDef("bizboost", "通商惠工", "45 日：市肆/工坊进项 +25%，市肆需求 +20%",
            PolicyEffect(incomeMul = 1.25, demandC = 1.20, demandI = 1.10), 45, 70),
        PolicyDef("welfare", "赈济安民", "30 日：民心 +15。一次性支出 400 万",
            PolicyEffect(happy = 15, cost = 400), 30, 50),
        PolicyDef("smoke_alarm", "巡夜查火", "60 日：火患 -70%。一次性支出 180 万",
            PolicyEffect(cost = 180, fireMul = 0.30), 60, 50),
        PolicyDef("free_transit", "车马免资", "40 日：街市拥挤 -35%，民心 +6，供养费 +18%",
            PolicyEffect(happy = 6, trafficMul = 0.65, upkeepMul = 1.18), 40, 55),
        PolicyDef("power_save", "省用灵电", "40 日：用电 -15%，民心 -4",
            PolicyEffect(happy = -4, powerUseMul = 0.85), 40, 40),
        PolicyDef("ev_boost", "轻车代步", "45 日：浊气 -20%，拥挤 -10%，供养费 +12%",
            PolicyEffect(pollutionMul = 0.80, trafficMul = 0.90, upkeepMul = 1.12), 45, 50),
        PolicyDef("high_density", "广建宅院", "40 日：民居升格 +80%，民坊需求 +25%，拥挤 +20%",
            PolicyEffect(demandR = 1.25, upgradeMul = 1.80, trafficMul = 1.20), 40, 60),
        PolicyDef("industry_plan", "整饬工坊", "40 日：工坊产出 +20%，工坊需求 +15%，浊气 +25%",
            PolicyEffect(incomeMul = 1.12, demandI = 1.15, pollutionMul = 1.25), 40, 55),
        PolicyDef("night_econ", "夜市兴商", "30 日：市肆进项 +18%，民心 -5",
            PolicyEffect(happy = -5, incomeMul = 1.18, demandC = 1.15), 30, 45)
    )

    // -----------------------------------------------------------------------
    // 城市阶段
    // -----------------------------------------------------------------------
    data class CityLevelDef(val level: Int, val name: String, val popReq: Int, val reward: Int = 0)

    val CITY_LEVELS: List<CityLevelDef> = listOf(
        CityLevelDef(1, "村落", 0, 0),
        CityLevelDef(2, "集镇", 150, 600),
        CityLevelDef(3, "县城", 500, 1500),
        CityLevelDef(4, "州城", 1500, 4000),
        CityLevelDef(5, "府城", 4000, 10000),
        CityLevelDef(6, "都城", 10000, 25000)
    )

    // 虚构官阶（全城营造成就，无现实机构）
    data class RankDef(
        val level: Int,
        val name: String,
        val popReq: Int,
        val happyReq: Int,
        val perk: String,
        val grant: Int = 0,
        val unlockIds: List<String> = emptyList()
    )

    val RANKS: List<RankDef> = listOf(
        RankDef(1, "里正", 0, 0, "起步：园林、风车坊、水楼、医馆、蒙学"),
        RankDef(
            2, "坊正", 80, 52, "解锁鼓楼广场/书院/车马站 · 借贷额度提升 · 到账 180 万",
            grant = 180, unlockIds = listOf("plaza", "middle_school", "bus_stop", "incinerator")
        ),
        RankDef(
            3, "县丞", 300, 58, "解锁医署/国子监/地道口 · 奇观预告 · 到账 420 万",
            grant = 420, unlockIds = listOf("hospital", "university", "metro", "solar_plant")
        ),
        RankDef(
            4, "县令", 800, 62, "解锁驿站/码头/商帮会馆 · 供养费 -6% · 到账 900 万",
            grant = 900, unlockIds = listOf("rail_station", "harbor", "stock_exchange", "prison")
        ),
        RankDef(
            5, "知府", 2000, 68, "解锁飞舟坞/观星台 · 通商收入 +8% · 到账 1800 万",
            grant = 1800, unlockIds = listOf("airport", "tv_tower", "nuclear_plant")
        ),
        RankDef(
            6, "节度使", 5000, 75, "解锁演武场 · 民心目标 +4 · 到账 3600 万",
            grant = 3600, unlockIds = listOf("stadium")
        )
    )

    fun rankUnlocksService(id: String, rankLevel: Int): Boolean {
        val need = RANKS.filter { it.unlockIds.contains(id) }.minByOrNull { it.level }?.level ?: return true
        return rankLevel >= need
    }

    // -----------------------------------------------------------------------
    // 配色
    // -----------------------------------------------------------------------
    object COLORS {
        // UI 基底（报纸风）
        // 古风 UI：宣纸底 + 朱漆点缀 + 墨色文字
        val uiBackdrop = RGBA(214, 202, 178, 255)      // 旧纸底
        val panelWhite = RGBA(243, 234, 214, 250)      // 宣纸面板
        val panelShadow = RGBA(96, 86, 66, 70)
        val textDark = RGBA(38, 32, 26, 255)           // 墨色
        val textMid = RGBA(96, 82, 64, 255)            // 褐墨
        val textFaint = RGBA(140, 126, 104, 255)
        val accentGreen = RGBA(78, 106, 72, 255)       // 竹青
        val accentRed = RGBA(160, 48, 42, 255)         // 朱漆
        val accentGold = RGBA(184, 142, 56, 255)       // 鎏金
        val accentBlue = RGBA(62, 88, 128, 255)        // 靛青
        val accentSoftBg = RGBA(238, 220, 196, 255)    // 米黄
        val border2 = RGBA(198, 182, 154, 255)         // 木框
        val chipBg = RGBA(246, 238, 220, 255)          // 纸签
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
        // 古风路面：土路 / 青石板巷 / 打磨石板大街 / 夯土官道
        val roadDirt = RGBA(190, 172, 138, 255)      // 土路
        val roadLocal = RGBA(176, 172, 162, 255)     // 青石板巷
        val roadAvenue = RGBA(160, 156, 148, 255)    // 石板大街
        val roadHighway = RGBA(146, 138, 124, 255)   // 夯土官道

        // 分区
        val zoneResidential = RGBA(233, 217, 166, 255)
        val zoneCommercial = RGBA(224, 200, 200, 255)
        val zoneIndustrial = RGBA(196, 188, 168, 255)
        val zoneOffice = RGBA(186, 210, 228, 255)

        // 建筑体块
        // 古风墙体：民居粉墙、市肆朱漆、工坊青砖、官署灰瓦
        val bResidential = RGBA(238, 226, 202, 255)   // 粉墙（米白）
        val bCommercial = RGBA(206, 156, 122, 255)    // 朱漆木墙
        val bIndustrial = RGBA(176, 176, 168, 255)    // 青砖
        val bOffice = RGBA(196, 190, 176, 255)        // 官署灰白
        val bAbandoned = RGBA(146, 138, 126, 255)
        val bService = RGBA(146, 170, 132, 255)
        val bCivic = RGBA(178, 164, 148, 255)

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
        // 古风屋顶：青瓦 / 琉璃 / 茅草
        val roofTile = RGBA(74, 92, 106, 255)      // 青瓦
        val roofGlaze = RGBA(122, 146, 128, 255)   // 琉璃（绿）
        val roofThatch = RGBA(168, 148, 104, 255)  // 茅草
        val roofWood = RGBA(120, 78, 58, 255)      // 木构
        val wallWood = RGBA(150, 96, 66, 255)      // 木柱
    }

    // 工具函数 -------------------------------------------------------------
    fun clamp(v: Float, lo: Float, hi: Float): Float = max(lo, min(hi, v))
    fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))
    fun clamp(v: Int, lo: Int, hi: Int): Int = max(lo, min(hi, v))
}
