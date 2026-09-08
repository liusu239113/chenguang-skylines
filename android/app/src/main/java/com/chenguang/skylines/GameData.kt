package com.chenguang.skylines

import com.chenguang.skylines.world.World
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
}

object GameData {

    private val T = Config.TIME
    private val E = Config.ECONOMY

    var current: CityState? = null
        private set

    var speedIdx: Int = 2          // 默认 1x
    var pendingLevelUp: Boolean = false
    var monthFlash: Boolean = false

    private var dayAcc: Double = 0.0

    private fun createState(): CityState = CityState()

    fun init(seed: Int = 20260408) {
        World.generate(seed)
        current = createState()
        World.current?._pop = 0
        speedIdx = 2
        pendingLevelUp = false
        monthFlash = false
        dayAcc = 0.0
        pushNews(
            "城市奠基",
            Config.World.city + "迎来新任" + Config.World.playerRole +
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

        // 人口向"容量×占用率"靠拢（占用率受满意度驱动；缺水时人口增长停滞）
        val waterMul = if (cov.water < 0.99f) cov.water else 1.0
        val occTarget = floor(st.resCap * max(0.25, min(1.0, s.happiness / 100.0)) * waterMul)
        if (s.population < occTarget) {
            s.population = min(
                occTarget,
                s.population + max(1.0, floor((occTarget - s.population) * E.occupancyPerDay))
            )
        } else {
            s.population = max(
                occTarget,
                s.population - max(1.0, floor((s.population - occTarget) * 0.15))
            )
        }
        World.current?._pop = s.population.toInt()

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
        val net = income - upkeep
        s.funds += net
        if (net >= 0) s.totalIncome += net else s.totalSpent += -net

        // 满意度向目标靠拢
        val target = computeHappinessTarget(st)
        s.happiness += (target - s.happiness) * 0.10

        // 火灾：无消防覆盖的建筑有概率起火被烧毁
        val grown = World.allBuildings().filter { !it.b.isService }
        if (grown.isNotEmpty() && kotlin.random.Random.nextDouble() < Config.COVERAGE.fireChancePerDay) {
            val victim = grown[kotlin.random.Random.nextInt(grown.size)]
            if (!World.isCoveredBy(victim.x, victim.y, Config.ServiceCat.SAFETY)) {
                World.bulldoze(victim.x, victim.y)
                pushNews("火灾！", "一处建筑因缺乏消防覆盖被烧毁。", "突发")
            } else {
                pushNews("火情解除", "消防站及时扑灭了一起火情。", "突发")
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
        if (s.funds < r.cost) return false to ("资金不足（需 ¥" + r.cost + "万）")
        World.setRoad(x, y, kind)
        s.funds -= r.cost
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
        s.funds -= cost
        if (s.funds < 0) {
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
        }
        return true
    }

    fun placeService(id: String, x: Int, y: Int): Pair<Boolean, String?> {
        val (ok, msg) = World.canPlaceService(id, x, y)
        if (!ok) return false to msg
        val cfg = World.serviceConfig(id) ?: return false to "未知设施"
        val s = current ?: return false to null
        if (cfg.unlockPop > s.population.toInt()) {
            return false to ("人口达到 " + cfg.unlockPop + " 后解锁")
        }
        if (s.funds < cfg.cost) return false to ("资金不足（需 ¥" + cfg.cost + "万）")
        World.placeService(id, x, y)
        s.funds -= cfg.cost
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
