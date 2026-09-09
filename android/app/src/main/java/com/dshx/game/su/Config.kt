package com.dshx.game.su

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
    const val TITLE = "模拟市长：城市经营"
    const val SUBTITLE = "模拟市长 · 晨光市城建日报"

    object World {
        const val country = "星辰联邦"
        const val city = "晨光市"
        const val playerRole = "营造主管"
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
        const val daySeconds = 8.0f          // 1x 下一天的现实秒数（缓慢推进）
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
        "residential" to ZoneDef("residential", "住宅区", 6, COLORS.zoneResidential),
        "commercial" to ZoneDef("commercial", "商业区", 8, COLORS.zoneCommercial),
        "industrial" to ZoneDef("industrial", "工业区", 7, COLORS.zoneIndustrial),
        "office" to ZoneDef("office", "办公区", 10, COLORS.zoneOffice),
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
        val OFF_PRE = listOf("星辉", "云台", "启明", "瀚海", "银座", "通衢")
        val OFF_SUF = listOf("大厦", "写字楼", "中心", "塔")
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
        "dirt" to RoadDef("dirt", "泥土路", 0, 6, 30, 1, 0.002),
        "local" to RoadDef("local", "两车道", 8, 14, 40, 2, 0.006),
        "avenue" to RoadDef("avenue", "四车道", 20, 28, 60, 4, 0.012),
        "highway" to RoadDef("highway", "高速路", 40, 48, 100, 7, 0.022),
        "metro" to RoadDef("metro", "地铁隧", 6, 36, 70, 1, 0.010),
        "rail" to RoadDef("rail", "铁轨", 8, 20, 80, 3, 0.014)
    )

    // -----------------------------------------------------------------------
    // 成长建筑
    // -----------------------------------------------------------------------
    data class LevelDef(val cap: Int, val income: Int, val pollution: Int = 0)
    data class GrownDef(val name: String, val levels: List<LevelDef>)

    val GROWN: Map<String, GrownDef> = mapOf(
        "residential" to GrownDef("住宅", listOf(
            LevelDef(4, 2), LevelDef(8, 5), LevelDef(16, 10)
        )),
        "commercial" to GrownDef("商铺", listOf(
            LevelDef(3, 6), LevelDef(8, 14), LevelDef(16, 28)
        )),
        "industrial" to GrownDef("工厂", listOf(
            LevelDef(4, 7, 2), LevelDef(10, 16, 4), LevelDef(20, 32, 7)
        )),
        "office" to GrownDef("写字楼", listOf(
            LevelDef(6, 8), LevelDef(12, 18), LevelDef(24, 36)
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
        const val TRANSIT = "transit"     // 公交
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
        val waterCap: Int = 0             // 供水容量
    )

    val SERVICES: List<ServiceDef> = listOf(
        // ---- 生活品质 ----
        ServiceDef("park", "公园", 260, 10, 4, 5, true, 1, 1,
            "绿地吸收污染，抬升周边地价与满意度。", ServiceCat.AMENITY, 0),
        ServiceDef("plaza", "广场", 900, 18, 5, 8, true, 2, 2,
            "市民广场，显著提升满意度与地价。", ServiceCat.AMENITY, 500),
        // ---- 电力（容量决定能否撑住全城） ----
        ServiceDef("wind_farm", "风电场", 500, 18, 6, 0, false, 1, 1,
            "清洁风电。按半径覆盖周边，容量 18。", ServiceCat.POWER, 0, 0, 18, 0),
        ServiceDef("solar_plant", "太阳能电站", 800, 16, 7, 0, false, 2, 2,
            "光伏电站。按半径覆盖，容量 36，无污染。", ServiceCat.POWER, 300, 0, 36, 0),
        ServiceDef("coal_plant", "燃煤电厂", 1200, 42, 10, -3, false, 2, 2,
            "容量 70，覆盖更广，稳定但污染重。", ServiceCat.POWER, 0, 8, 70, 0),
        ServiceDef("nuclear_plant", "核电站", 4200, 45, 14, 0, false, 3, 3,
            "容量 180，覆盖最广，维护昂贵。", ServiceCat.POWER, 4000, 1, 180, 0),
        // ---- 供水 ----
        ServiceDef("water_tower", "水塔", 300, 12, 5, 0, false, 1, 1,
            "抽取地下水。按半径覆盖周边。", ServiceCat.WATER, 0, 0, 0, 18),
        ServiceDef("pump_station", "抽水站", 600, 18, 8, 0, false, 1, 1,
            "必须靠河。覆盖半径更大。", ServiceCat.WATER, 0, 0, 0, 40),
        // ---- 垃圾 ----
        ServiceDef("landfill", "垃圾场", 350, 22, 6, 0, false, 1, 1,
            "填埋生活垃圾，满载后污染加重。", ServiceCat.GARBAGE, 40, 3),
        ServiceDef("incinerator", "焚烧厂", 900, 36, 8, -2, false, 2, 2,
            "烧掉垃圾并发电，有空气污染。", ServiceCat.GARBAGE, 300, 5, 12, 0),
        // ---- 医疗 ----
        ServiceDef("clinic", "诊所", 640, 28, 7, 5, false, 1, 1,
            "基础医疗，覆盖区健康与满意度提升。", ServiceCat.HEALTH, 25),
        ServiceDef("hospital", "医院", 900, 36, 8, 6, true, 2, 2,
            "大型医疗，覆盖更广。", ServiceCat.HEALTH, 700),
        // ---- 教育（驱动产业升级） ----
        ServiceDef("school", "小学", 520, 32, 7, 4, true, 2, 2,
            "基础教育，缓慢提升受教育人口。", ServiceCat.EDUCATION, 20),
        ServiceDef("middle_school", "中学", 700, 28, 8, 4, true, 2, 2,
            "中等教育，商业与制造业需要。", ServiceCat.EDUCATION, 300),
        ServiceDef("university", "大学", 1800, 48, 10, 6, true, 3, 3,
            "高等教育，解锁高科技工厂。", ServiceCat.EDUCATION, 600),
        // ---- 安全 ----
        ServiceDef("fire_station", "消防站", 600, 24, 7, 0, false, 1, 1,
            "扑灭火灾，无覆盖则建筑会烧毁。", ServiceCat.SAFETY, 50),
        ServiceDef("police", "警察局", 700, 28, 8, 3, false, 1, 1,
            "降低犯罪，提升安全感与地价。", ServiceCat.SAFETY, 80),
        // ---- 公交 ----
        ServiceDef("bus_stop", "公交站", 220, 10, 6, 4, false, 1, 1,
            "缓解拥堵，缩短通勤。", ServiceCat.TRANSIT, 150),
        ServiceDef("metro", "地铁站", 1600, 40, 10, 6, true, 2, 2,
            "大运量，显著降低拥堵。", ServiceCat.TRANSIT, 600),
        ServiceDef("rail_station", "火车站", 2000, 44, 10, 8, true, 2, 2,
            "连接城外，货运与游客。", ServiceCat.TRANSIT, 1500),
        ServiceDef("harbor", "港口", 1800, 15, 8, 6, true, 2, 2,
            "滨水货运码头，工业出口加成。", ServiceCat.TRANSIT, 1500),
        ServiceDef("airport", "机场", 5000, 80, 12, 10, true, 3, 3,
            "航空枢纽，旅游收入与满意度。", ServiceCat.TRANSIT, 4000),
        // ---- 排污 / 殡葬 / 监狱 ----
        ServiceDef("sewage", "污水处理厂", 1100, 36, 8, 0, false, 2, 2,
            "处理污水。未覆盖则污染水源、市民生病。", ServiceCat.WATER, 60, 2, 0, 0),
        ServiceDef("cemetery", "墓地", 400, 4, 6, -1, false, 2, 2,
            "存放遗体。满载后污染周边。", ServiceCat.DEATH, 80),
        ServiceDef("crematorium", "火葬场", 900, 12, 8, 0, false, 1, 1,
            "焚化遗体，无堆积。需派出灵车。", ServiceCat.DEATH, 300),
        ServiceDef("prison", "监狱", 1600, 20, 8, 0, false, 2, 2,
            "关押罪犯。容量满则犯人被释放。", ServiceCat.SAFETY, 400),
        // ---- 独特建筑 ----
        ServiceDef("stock_exchange", "证券交易所", 3500, 18, 10, 8, true, 3, 3,
            "全城商业税收 +12%，地价上升。", ServiceCat.LANDMARK, 1000),
        ServiceDef("tv_tower", "电视塔", 2800, 14, 12, 10, true, 2, 2,
            "地标观光，满意度与旅游收入上升。", ServiceCat.LANDMARK, 1500),
        ServiceDef("stadium", "体育场", 3200, 22, 10, 8, true, 3, 3,
            "赛事吸引游客，周末消费加成。", ServiceCat.LANDMARK, 2000)
    )

    // -----------------------------------------------------------------------
    // 资源 / 成长参数
    // -----------------------------------------------------------------------
    object RESOURCES {
        const val fundsStart = 1500.0
        const val happinessStart = 60.0
        const val happinessMin = 0.0
        const val happinessMax = 100.0
    }

    object GROWTH {
        const val tickSeconds = 2.2
        const val spawnChance = 0.32
        const val upgradeChance = 0.18
        const val demandMin = 0.18
        const val abandonHappy = 28.0
        const val upgradeAgeDays = 12
        const val landValueUpgrade = 8
    }

    // -----------------------------------------------------------------------
    // 财政
    // -----------------------------------------------------------------------
    object ECONOMY {
        const val taxPerPopPerDay = 0.04
        const val baseIncomePerDay = 0.15
        const val upkeepPerRoadDay = 0.018
        const val happinessDecayDay = 0.10
        const val pollutionHappy = 0.045
        const val occupancyPerDay = 0.02
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
        const val amount = 400.0      // 借款额（万）
        const val dailyRepay = 18.0   // 每日自动还款（万）
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
        AchievementDef("pop2000", "城区气象", "人口达到 2000", 2500, "pop", 2000.0),
        AchievementDef("pop4000", "都市气象", "人口达到 4000", 5000, "pop", 4000.0),
        AchievementDef("pop6000", "都市崛起", "人口达到 6000", 7000, "pop", 6000.0),
        AchievementDef("pop10000", "万人大都会", "人口达到 10000", 10000, "pop", 10000.0),
        AchievementDef("bld50", "拔地而起", "建成 50 栋建筑", 500, "buildings", 50.0),
        AchievementDef("bld200", "百业兴旺", "建成 200 栋建筑", 1500, "buildings", 200.0),
        AchievementDef("bld300", "高楼林立", "建成 300 栋建筑", 2500, "buildings", 300.0),
        AchievementDef("funds10000", "家底殷实", "资金达到 10000 万", 1000, "funds", 10000.0),
        AchievementDef("funds30000", "富可敌国", "资金达到 30000 万", 2000, "funds", 30000.0),
        AchievementDef("happy70", "和谐宜居", "满意度达到 70", 600, "happiness", 70.0),
        AchievementDef("happy80", "人间乐土", "满意度达到 80", 1200, "happiness", 80.0),
        AchievementDef("happy85", "安居乐业", "满意度达到 85", 1000, "happiness", 85.0),
        AchievementDef("happy95", "人间天堂", "满意度达到 95", 2500, "happiness", 95.0),
        AchievementDef("exam1", "持证上岗", "通过 1 次营造测评", 400, "exam", 1.0),
        AchievementDef("exam3", "考核能手", "累计通过 3 次营造测评", 900, "exam", 3.0),
        AchievementDef("mail8", "有求必应", "处理 8 封市民来信", 700, "mail", 8.0),
        AchievementDef("school60", "书香城区", "升学率达到 60%", 800, "school", 60.0),
        AchievementDef("school80", "学风鼎盛", "升学率达到 80%", 1600, "school", 80.0),
        AchievementDef("road80", "路网成型", "道路达到 80 格", 600, "roads", 80.0),
        AchievementDef("svc12", "设施齐全", "建成 12 座服务设施", 900, "services", 12.0)
    )

    // -----------------------------------------------------------------------
    // 随机事件（由城市状态触发，不是无脑随机）
    //   cond: power(缺电) / water(缺水) / health(缺医疗) / happy(满意度低)
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
        EventDef("blackout", "居民断电", "住宅没通电，居民来信要求接电缆。", 3, -8.0, 0.92, "power"),
        EventDef("pipe", "居民缺水", "住宅没通水，生活用水告急。", 3, -8.0, 0.95, "water"),
        EventDef("clinic", "看病排队", "附近没有诊所/医院，居民看病困难。", 4, -6.0, 1.0, "health"),
        EventDef("school", "学位告急", "附近没有学校，家长反映孩子没处上学。", 4, -5.0, 1.0, "school"),
        EventDef("trash", "垃圾堆门前", "清运覆盖不足，生活垃圾堆到路边。", 3, -5.0, 0.98, "garbage"),
        EventDef("shop", "商铺没人气", "商业区缺电或缺水，店门冷清。", 4, -3.0, 0.90, "shop"),
        EventDef("factory", "工厂停工", "工业区缺电，车间开不了工。", 4, -4.0, 0.82, "factory")
    )

    // -----------------------------------------------------------------------
    // 市政任务（限时目标，完成刷新）
    // -----------------------------------------------------------------------
    data class QuestDef(val type: String, val name: String, val reward: Int, val target: Double)

    val QUESTS: List<QuestDef> = listOf(
        QuestDef("pop", "人口达到", 800, 300.0),
        QuestDef("pop", "人口达到", 1500, 800.0),
        QuestDef("buildings", "建成建筑", 600, 40.0),
        QuestDef("buildings", "建成建筑", 1200, 100.0),
        QuestDef("funds", "资金达到", 600, 12000.0),
        QuestDef("happy", "满意度达到", 800, 70.0),
        QuestDef("happy", "满意度达到", 1500, 88.0)
    )

    // -----------------------------------------------------------------------
    // 政策（对照《详细拆解文档》：启用后必须持续改数值，不能只弹一次新闻）
    // -----------------------------------------------------------------------
    data class PolicyEffect(
        val happy: Int = 0,                 // 生效期内每日叠加到满意度目标
        val taxMul: Double = 1.0,           // 税收倍率
        val incomeMul: Double = 1.0,        // 产业收入倍率
        val cost: Int = 0,                  // 一次性财政支出
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
        PolicyDef("cut_tax", "减负降税", "30 日：税率收入 -25%，满意度目标 +12，住宅需求 +15%",
            PolicyEffect(happy = 12, taxMul = 0.75, demandR = 1.15), 30, 45),
        PolicyDef("raise_tax", "增收节支", "30 日：税收 +30%，满意度 -10，三项需求 -12%",
            PolicyEffect(happy = -10, taxMul = 1.30, demandR = 0.88, demandC = 0.88, demandI = 0.88), 30, 45),
        PolicyDef("greening", "绿化行动", "30 日：污染 -40%，满意度 +8。一次性支出 200 万",
            PolicyEffect(happy = 8, cost = 200, pollutionMul = 0.60), 30, 40),
        PolicyDef("bizboost", "营商激励", "45 日：商/工收入 +25%，商业需求 +20%",
            PolicyEffect(incomeMul = 1.25, demandC = 1.20, demandI = 1.10), 45, 70),
        PolicyDef("welfare", "民生改善", "30 日：满意度 +15。一次性支出 400 万",
            PolicyEffect(happy = 15, cost = 400), 30, 50),
        PolicyDef("smoke_alarm", "烟雾检测", "60 日：火灾风险 -70%。一次性支出 180 万",
            PolicyEffect(cost = 180, fireMul = 0.30), 60, 50),
        PolicyDef("free_transit", "免费公交", "40 日：拥堵 -35%，满意度 +6，维护费 +18%",
            PolicyEffect(happy = 6, trafficMul = 0.65, upkeepMul = 1.18), 40, 55),
        PolicyDef("power_save", "电力节约", "40 日：用电 -15%，满意度 -4",
            PolicyEffect(happy = -4, powerUseMul = 0.85), 40, 40),
        PolicyDef("ev_boost", "电动车鼓励", "45 日：污染 -20%，拥堵 -10%，维护费 +12%",
            PolicyEffect(pollutionMul = 0.80, trafficMul = 0.90, upkeepMul = 1.12), 45, 50),
        PolicyDef("high_density", "高密住宅鼓励", "40 日：住宅升级 +80%，住宅需求 +25%，拥堵 +20%",
            PolicyEffect(demandR = 1.25, upgradeMul = 1.80, trafficMul = 1.20), 40, 60),
        PolicyDef("industry_plan", "工业空间规划", "40 日：工业产出 +20%，工业需求 +15%，污染 +25%",
            PolicyEffect(incomeMul = 1.12, demandI = 1.15, pollutionMul = 1.25), 40, 55),
        PolicyDef("night_econ", "夜间经济", "30 日：商业收入 +18%，满意度 -5",
            PolicyEffect(happy = -5, incomeMul = 1.18, demandC = 1.15), 30, 45)
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

    // 虚构市政职级（全城建设成就，无现实机构）
    data class RankDef(val level: Int, val name: String, val popReq: Int, val happyReq: Int, val perk: String)

    val RANKS: List<RankDef> = listOf(
        RankDef(1, "见习营造", 0, 0, "起步权限"),
        RankDef(2, "街区营造", 80, 50, "贷款额度提升 · 通过营造测评"),
        RankDef(3, "城区营造", 300, 55, "独特建筑预告 · 通过营造测评"),
        RankDef(4, "都会营造", 800, 60, "维护费 -6% · 通过营造测评"),
        RankDef(5, "总营造师", 2000, 65, "贸易收入 +8% · 通过营造测评"),
        RankDef(6, "荣誉营造", 5000, 70, "满意度目标 +4 · 通过营造测评")
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
        val roadDirt = RGBA(186, 168, 132, 255)
        val roadLocal = RGBA(214, 210, 198, 255)
        val roadAvenue = RGBA(230, 197, 104, 255)
        val roadHighway = RGBA(96, 102, 110, 255)

        // 分区
        val zoneResidential = RGBA(233, 217, 166, 255)
        val zoneCommercial = RGBA(224, 200, 200, 255)
        val zoneIndustrial = RGBA(196, 188, 168, 255)
        val zoneOffice = RGBA(186, 210, 228, 255)

        // 建筑体块
        val bResidential = RGBA(236, 214, 150, 255)
        val bCommercial = RGBA(226, 176, 150, 255)
        val bIndustrial = RGBA(200, 190, 165, 255)
        val bOffice = RGBA(168, 196, 224, 255)
        val bAbandoned = RGBA(150, 142, 132, 255)
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
