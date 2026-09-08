package com.chenguang.skylines

import com.chenguang.skylines.world.World
import com.chenguang.skylines.world.Growth
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ============================================================================
// GameData — 实时城市模拟，与 scripts/GameData.lua 1:1 对应
//   时间持续流动：tick(dt) 由主循环每帧调用，内部按速度倍率推进
//   每游戏日小额收支；每 30 日自动翻月 → 结算新闻 / 政策倒计时 / 晋级检查
// ============================================================================

data class PolicyActive(val id: String, var daysLeft: Int)

data class NewsItem(val month: String, val headline: String, val body: String, val tag: String)

data class ActiveEvent(val id: String, val name: String, var daysLeft: Int, val happy: Double, val incomeMul: Double)

class CityState {
    var year: Int = 2026
    var month: Int = 4
    var day: Int = 1
    var funds: Double = Config.RESOURCES.fundsStart
    var population: Double = 0.0
    var happiness: Double = Config.RESOURCES.happinessStart
    var totalIncome: Double = 0.0
    var totalSpent: Double = 0.0
    var pollution: Int = 0
    val activePolicies: MutableList<PolicyActive> = mutableListOf()
    val policyCooldowns: MutableMap<String, Int> = mutableMapOf()
    val news: MutableList<NewsItem> = mutableListOf()
    var lastLevel: Int = 0
    // 税收（RCI 三税率，%）
    var taxRes: Int = Config.TAX.default
    var taxCom: Int = Config.TAX.default
    var taxInd: Int = Config.TAX.default
    // 最近一次覆盖统计（数据面板用）
    var lastCoverage: com.chenguang.skylines.world.Coverage? = null
    // 贷款
    var loanDebt: Double = 0.0
    var loanCooldown: Int = 0
    // 成就
    val achievements: MutableSet<String> = mutableSetOf()
    // 进行中的事件
    val activeEvents: MutableList<ActiveEvent> = mutableListOf()
    // 城市名
    var cityName: String = Config.World.city
}

object GameData {

    private val T = Config.TIME
    private val E = Config.ECONOMY

    var current: CityState? = null
        private set

    var speedIdx: Int = 2          // 默认 1x
    var pendingLevelUp: Boolean = false
    var monthFlash: Boolean = false

    var seed: Int = 20260408
    var difficultyKey: String = "normal"
    var sandbox: Boolean = false

    /** 一天内时间：0=清晨，0.25=正午，0.5=黄昏，0.75=夜晚 */
    var timeOfDay: Float = 0.25f

    private var dayAcc: Double = 0.0
    private var eventCooldown: Int = 12

    fun difficultyDef(): Config.DifficultyDef =
        Config.DIFFICULTIES.firstOrNull { it.key == difficultyKey } ?: Config.DIFFICULTIES[1]

    private fun createState(): CityState = CityState()

    fun init(seed: Int = 20260408, cityName: String = Config.World.city) {
        GameData.seed = seed
        World.generate(seed)
        current = createState()
        val s = current!!
        s.cityName = cityName
        World.current?._pop = 0
        speedIdx = 2
        pendingLevelUp = false
        monthFlash = false
        dayAcc = 0.0
        eventCooldown = 12
        pushNews(
            "城市奠基",
            s.cityName + "迎来新任" + Config.World.playerRole +
                "。沿大道修路、划分区，城市将随时间自然生长。",
            "头条"
        )
    }

    fun reset(seed: Int) {
        init(seed)
    }

    // -----------------------------------------------------------------------
    // 时间与速度
    // -----------------------------------------------------------------------
    fun speed(): Int = if (speedIdx in T.speeds.indices) T.speeds[speedIdx] else 1

    fun setSpeed(idx: Int) {
        if (idx in T.speeds.indices) speedIdx = idx
    }

    fun dateLabel(): String {
        val s = current ?: return ""
        return String.format("%04d.%02d.%02d", s.year, s.month, s.day)
    }

    fun monthLabel(): String {
        val s = current ?: return ""
        return String.format("%04d.%02d", s.year, s.month)
    }

    // -----------------------------------------------------------------------
    // 政策效果聚合
    // -----------------------------------------------------------------------
    private fun policyMul(key: String): Double {
        val s = current ?: return 1.0
        var m = 1.0
        for (ap in s.activePolicies) {
            val p = Config.POLICIES.firstOrNull { it.id == ap.id } ?: continue
            when (key) {
                "taxMul" -> m *= p.effect.taxMul
                "incomeMul" -> m *= p.effect.incomeMul
            }
        }
        return m
    }

    // -----------------------------------------------------------------------
    // 每日结算
    // -----------------------------------------------------------------------
    private fun computeHappinessTarget(st: com.chenguang.skylines.world.WorldStats): Double {
        var target = 52.0
        for (e in World.allBuildings()) {
            val b = e.b
            if (!b.isService) continue
            val cfg = World.serviceConfig(b.service) ?: continue
            var covered = 0
            for (dy in -cfg.radius..cfg.radius) {
                for (dx in -cfg.radius..cfg.radius) {
                    val t = World.tile(e.x + dx, e.y + dy)
                    if (t?.building != null && t.building?.isService != true) covered++
                }
            }
            target += cfg.happy * min(1.2, covered / 14.0)
        }
        target -= st.pollution * E.pollutionHappy

        // 基础设施覆盖不足的惩罚（缺电/缺水/垃圾堆积）
        val cov = World.coverage()
        target -= (1 - cov.power) * Config.COVERAGE.powerHappyPenalty
        target -= (1 - cov.water) * Config.COVERAGE.waterHappyPenalty
        target -= (1 - cov.garbage) * Config.COVERAGE.garbageHappyPenalty

        // 税率高于基准的惩罚
        val s = current
        if (s != null) {
            target -= max(0.0, (s.taxRes - Config.TAX.default).toDouble()) * Config.TAX.happyPerPoint
            target -= max(0.0, (s.taxCom - Config.TAX.default).toDouble()) * Config.TAX.happyPerPoint
            target -= max(0.0, (s.taxInd - Config.TAX.default).toDouble()) * Config.TAX.happyPerPoint
            for (ev in s.activeEvents) target += ev.happy
        }

        return max(Config.RESOURCES.happinessMin, min(Config.RESOURCES.happinessMax, target))
    }

    private fun onNewDay() {
        val s = current ?: return
        s.day += 1

        val st = World.stats()
        s.pollution = st.pollution
        val cov = World.coverage()
        s.lastCoverage = cov

        // 人口 = 各住宅入住人数之和；住宅建好即迁入
        val waterMul: Double = if (cov.water < 0.99f) cov.water.toDouble() else 1.0
        val satisMul = max(0.3, min(1.2, s.happiness / 60.0))
        var totalRes = 0
        for (e in World.allBuildings()) {
            val b = e.b
            if (b.isService || b.zone != "residential") continue
            val lv = Config.GROWN["residential"]?.levels?.getOrNull(b.level - 1) ?: continue
            if (b.residents < lv.cap) {
                val migrate = max(1, (lv.cap * 0.2 * satisMul * waterMul).toInt())
                b.residents = min(lv.cap, b.residents + migrate)
            }
            if (s.happiness < 30 && b.residents > 0) {
                b.residents = max(0, b.residents - 1)
            }
            totalRes += b.residents
        }
        s.population = totalRes.toDouble()
        World.current?._pop = totalRes

        // 收支（万/日）：住宅按住宅税率，商业/工业按各自税率；缺电时产业收入打折
        val occRatio = if (st.resCap > 0) s.population / st.resCap else 0.0
        var bizCom = 0.0
        var bizInd = 0.0
        for (e in World.allBuildings()) {
            val b = e.b
            if (b.isService) continue
            val lv = Config.GROWN[b.zone]?.levels?.getOrNull(b.level - 1) ?: continue
            when (b.zone) {
                "commercial" -> bizCom += lv.income * occRatio
                "industrial" -> bizInd += lv.income * occRatio
            }
        }
        val powerMul = Config.COVERAGE.powerIncomeFloor +
            (1 - Config.COVERAGE.powerIncomeFloor) * cov.power
        val bizBase = (bizCom * s.taxCom / 10.0 + bizInd * s.taxInd / 10.0) * powerMul
        val income = (s.population * E.taxPerPopPerDay + E.baseIncomePerDay) *
            policyMul("taxMul") * (s.taxRes / 10.0) +
            bizBase + bizBase * (policyMul("incomeMul") - 1)
        var upkeep = st.roadCount * E.upkeepPerRoadDay
        for (e in World.allBuildings()) {
            if (!e.b.isService) continue
            val cfg = World.serviceConfig(e.b.service)
            if (cfg != null) upkeep += cfg.upkeep / 30.0
        }
        val diff = difficultyDef()
        var eventIncomeMul = 1.0
        for (ev in s.activeEvents) eventIncomeMul *= ev.incomeMul
        val net = (income * diff.incomeMul * eventIncomeMul) - (if (sandbox) 0.0 else upkeep * diff.upkeepMul)
        s.funds += net
        if (net >= 0) s.totalIncome += net else s.totalSpent += -net

        // 贷款还款
        if (s.loanDebt > 0) {
            val repay = min(Config.LOAN.dailyRepay, s.loanDebt)
            s.loanDebt -= repay
            s.funds -= repay
            if (s.loanDebt <= 0) {
                s.loanDebt = 0.0
                s.loanCooldown = Config.LOAN.cooldown
                pushNews("贷款还清", "市政贷款已全部还清。", "财政")
            }
        }
        if (s.loanCooldown > 0) s.loanCooldown -= 1

        // 满意度向目标靠拢
        val target = computeHappinessTarget(st)
        s.happiness += (target - s.happiness) * 0.10

        // 火灾：无消防覆盖的建筑有概率起火被烧毁
        val grown = World.allBuildings().filter { !it.b.isService }
        if (grown.isNotEmpty() && kotlin.random.Random.nextDouble() <
            Config.COVERAGE.fireChancePerDay * diff.eventMul) {
            val victim = grown[kotlin.random.Random.nextInt(grown.size)]
            if (!World.isCoveredBy(victim.x, victim.y, Config.ServiceCat.SAFETY)) {
                World.bulldoze(victim.x, victim.y)
                pushNews("火灾！", "一处建筑因缺乏消防覆盖被烧毁。", "突发")
            } else {
                pushNews("火情解除", "消防站及时扑灭了一起火情。", "突发")
            }
        }

        // 成就检查
        val bldCount = st.resCount + st.comCount + st.indCount + st.serviceCount
        for (a in Config.ACHIEVEMENTS) {
            if (a.id in s.achievements) continue
            val v = when (a.type) {
                "pop" -> s.population
                "buildings" -> bldCount.toDouble()
                "funds" -> s.funds
                "happiness" -> s.happiness
                else -> 0.0
            }
            if (v >= a.threshold) {
                s.achievements.add(a.id)
                s.funds += a.reward
                pushNews("成就解锁：" + a.name, a.desc + "，奖励 " + a.reward + " 万。", "成就")
            }
        }

        // 事件触发（由城市状态触发，不是无脑随机）
        if (eventCooldown <= 0) {
            val candidates = mutableListOf<Config.EventDef>()
            for (ev in Config.EVENTS) {
                val trigger = when (ev.cond) {
                    "power" -> cov.power < 0.5
                    "water" -> cov.water < 0.5
                    "health" -> cov.health < 0.5
                    "happy" -> s.happiness < 45
                    "boom" -> Growth.lastDemand.c > 0.75
                    else -> true
                }
                if (trigger) candidates.add(ev)
            }
            if (candidates.isNotEmpty() && kotlin.random.Random.nextDouble() < 0.25) {
                val ev = candidates[kotlin.random.Random.nextInt(candidates.size)]
                s.activeEvents.add(ActiveEvent(ev.id, ev.name, ev.duration, ev.happy, ev.incomeMul))
                pushNews("事件：" + ev.name, ev.desc, "事件")
                eventCooldown = 15 + kotlin.random.Random.nextInt(15)
            }
        } else {
            eventCooldown -= 1
        }
        // 事件倒计时
        for (i in s.activeEvents.indices.reversed()) {
            s.activeEvents[i].daysLeft -= 1
            if (s.activeEvents[i].daysLeft <= 0) {
                pushNews("事件结束", s.activeEvents[i].name + " 已解除。", "事件")
                s.activeEvents.removeAt(i)
            }
        }

        // 政策倒计时
        for (i in s.activePolicies.indices.reversed()) {
            s.activePolicies[i].daysLeft -= 1
            if (s.activePolicies[i].daysLeft <= 0) s.activePolicies.removeAt(i)
        }
        for ((id, cd) in s.policyCooldowns.toMap()) {
            if (cd > 0) s.policyCooldowns[id] = cd - 1
        }

        // 晋级检查（含里程碑奖励）
        val level = World.cityLevel()
        if (s.lastLevel == 0) s.lastLevel = level.level
        if (level.level > s.lastLevel) {
            s.lastLevel = level.level
            pendingLevelUp = true
            s.funds += level.reward
            pushNews(
                "城市晋级 " + level.name + "！",
                String.format(
                    "人口达到 %d，晨光市升级为%s，获得 %d万 拨款。",
                    s.population.toInt(), level.name, level.reward
                ),
                "头条"
            )
        }

        // 翻月
        if (s.day > 30) {
            s.day = 1
            s.month += 1
            if (s.month > 12) {
                s.month = 1
                s.year += 1
            }
            monthFlash = true
            val net30 = if (net >= 0) "+" else ""
            pushNews(
                monthLabel() + " 城建月报",
                String.format(
                    "人口 %d · 满意度 %d · 本日收支 %s%.1f万 · 建筑 %d 栋",
                    s.population.toInt(), floor(s.happiness).toInt(), net30, net,
                    st.resCount + st.comCount + st.indCount + st.serviceCount
                ),
                "月报"
            )
        }
    }

    /** dt = 真实秒；内部乘速度 */
    fun tick(dt: Float) {
        val s = current ?: return
        val simDt = dt * speed()
        if (simDt <= 0) return
        timeOfDay = (timeOfDay + (simDt / T.daySeconds).toFloat()) % 1f
        dayAcc += simDt
        while (dayAcc >= T.daySeconds) {
            dayAcc -= T.daySeconds
            onNewDay()
        }
    }

    // -----------------------------------------------------------------------
    // 操作（返回 ok, msg）
    // -----------------------------------------------------------------------
    fun placeRoad(x: Int, y: Int, kind: String): Pair<Boolean, String?> {
        val (ok, msg) = World.canRoad(x, y)
        if (!ok) return false to msg
        val r = Config.ROAD[kind] ?: return false to "未知道路"
        val s = current ?: return false to null
        if (!sandbox && s.funds < r.cost) return false to ("资金不足（需 ¥" + r.cost + "万）")
        World.setRoad(x, y, kind)
        if (!sandbox) s.funds -= r.cost
        return true to null
    }

    fun paintZone(x: Int, y: Int, zoneKey: String): Pair<Boolean, String?> {
        val s = current ?: return false to null
        if (zoneKey == "none") {
            val t = World.tile(x, y)
            if (t != null && t.zone != "none") World.setZone(x, y, "none")
            return true to null
        }
        val ok = World.setZone(x, y, zoneKey)
        if (!ok) return true to null                 // 静默跳过建筑/道路
        val cost = Config.ZONE[zoneKey]?.cost ?: 0
        if (!sandbox) s.funds -= cost
        if (!sandbox && s.funds < 0) {
            World.setZone(x, y, "none")
            s.funds += cost
            return false to "资金不足"
        }
        return true to null
    }

    fun bulldoze(x: Int, y: Int): Boolean {
        val res = World.bulldoze(x, y) ?: return false
        val s = current ?: return false
        when (res.first) {
            "service" -> {
                val cfg = World.serviceConfig(res.second)
                if (cfg != null) s.funds += floor(cfg.cost * 0.3)
                pushNews("拆除设施", "退还部分造价。", "城建")
            }
            "grown" -> s.funds += 1
            "road" -> s.funds += 2
            "zone" -> { /* 清除分区不退款 */ }
        }
        return true
    }

    fun placeService(id: String, x: Int, y: Int): Pair<Boolean, String?> {
        val (ok, msg) = World.canPlaceService(id, x, y)
        if (!ok) return false to msg
        val cfg = World.serviceConfig(id) ?: return false to "未知设施"
        val s = current ?: return false to null
        if (!sandbox && cfg.unlockPop > s.population.toInt()) {
            return false to ("人口达到 " + cfg.unlockPop + " 后解锁")
        }
        if (!sandbox && s.funds < cfg.cost) return false to ("资金不足（需 ¥" + cfg.cost + "万）")
        World.placeService(id, x, y)
        if (!sandbox) s.funds -= cfg.cost
        pushNews(
            cfg.name + " 建成",
            String.format("在 (%d,%d) 建成 %s，耗资 %d万。", x, y, cfg.name, cfg.cost),
            "城建"
        )
        return true to null
    }

    fun activatePolicy(pid: String): Pair<Boolean, String?> {
        val s = current ?: return false to "未开始"
        if ((s.policyCooldowns[pid] ?: 0) > 0) return false to "冷却中"
        if (s.activePolicies.any { it.id == pid }) return false to "已生效"
        val p = Config.POLICIES.firstOrNull { it.id == pid } ?: return false to "未知政策"
        if (p.effect.cost > 0 && s.funds < p.effect.cost) return false to "资金不足"
        // 与 Lua 一致：effect 内无 days 字段，实际取 cooldown
        s.activePolicies.add(PolicyActive(pid, p.cooldown))
        s.policyCooldowns[pid] = p.cooldown
        if (p.effect.happy != 0) {
            s.happiness = max(0.0, min(100.0, s.happiness + p.effect.happy))
        }
        if (p.effect.cost > 0) s.funds -= p.effect.cost
        pushNews("新政发布：" + p.name, p.desc, "政策")
        return true to null
    }

    /** 市政贷款：借入 LOAN.amount，按日自动还款 */
    fun borrow(): Pair<Boolean, String?> {
        val s = current ?: return false to null
        if (s.loanDebt > 0) return false to "尚有未还贷款"
        if (s.loanCooldown > 0) return false to ("冷却 " + s.loanCooldown + " 天")
        s.loanDebt = Config.LOAN.amount
        s.funds += Config.LOAN.amount
        pushNews("市政贷款", "借入 " + Config.LOAN.amount.toInt() + " 万，将按日自动还款。", "财政")
        return true to null
    }

    // -----------------------------------------------------------------------
    // 新闻
    // -----------------------------------------------------------------------
    fun pushNews(headline: String, body: String, tag: String) {
        val s = current ?: return
        s.news.add(NewsItem(dateLabel(), headline, body, tag.ifEmpty { "快讯" }))
        if (s.news.size > 60) s.news.removeAt(0)
    }

    fun recentNews(count: Int = 8): List<NewsItem> {
        val s = current ?: return emptyList()
        val from = max(0, s.news.size - count)
        return s.news.subList(from, s.news.size).toList()
    }
}
