package com.dshx.game.su

import com.dshx.game.su.world.World
import com.dshx.game.su.world.Growth
import com.dshx.game.su.world.Citizens
import com.dshx.game.su.world.Transit
import com.dshx.game.su.world.CitySystems
import com.dshx.game.su.world.Networks
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

data class Quest(var type: String, var name: String, var target: Double, var reward: Int, var done: Boolean = false)

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
    var taxOff: Int = Config.TAX.default
    // 最近一次覆盖统计（数据面板用）
    var lastCoverage: com.dshx.game.su.world.Coverage? = null
    // 贷款
    var loanDebt: Double = 0.0
    var loanCooldown: Int = 0
    // 成就
    val achievements: MutableSet<String> = mutableSetOf()
    // 进行中的事件
    val activeEvents: MutableList<ActiveEvent> = mutableListOf()
    // 当前市政任务
    var quest: Quest? = null
    // 城市名
    var cityName: String = Config.World.city
    // 市民慢变量（对照拆解文档：教育/健康/就业驱动长线循环）
    var education: Double = 18.0
    var health: Double = 62.0
    var jobs: Int = 0
    var lastIncome: Double = 0.0
    var lastUpkeep: Double = 0.0
    var lastNet: Double = 0.0
    var dayIncomeTax: Double = 0.0
    var dayIncomeBiz: Double = 0.0
    var dayIncomeTrade: Double = 0.0
    var congestion: Double = 0.0
    var bankruptDays: Int = 0
    var playSeconds: Double = 0.0
    var lastSavedLabel: String = ""
    var crime: Double = 8.0
    var deathsPending: Int = 0
    var prisonUsed: Int = 0
    var cemeteryUsed: Int = 0
    var garbageBacklog: Int = 0
    var sewerCoverage: Double = 1.0
    var budgetHealth: Int = 100
    var budgetEdu: Int = 100
    var budgetSafety: Int = 100
    var budgetTransit: Int = 100
    var rankLevel: Int = 1
    var merit: Double = 0.0
    var doubleTaxDays: Int = 0
    var lastShortfall: Int = 0
    var lastShortAction: String = ""
}

object GameData {

    private val T = Config.TIME
    private val E = Config.ECONOMY

    var current: CityState? = null
        private set

    var speedIdx: Int = 1          // 默认 1x；2x/3x 需广告解锁
    var pendingLevelUp: Boolean = false
    var monthFlash: Boolean = false

    var seed: Int = 20260408
    var difficultyKey: String = "normal"
    var sandbox: Boolean = false

    /** 一天内时间：0=清晨，0.25=正午，0.5=黄昏，0.75=夜晚 */
    var timeOfDay: Float = 0.25f

    /** 天气：0=晴 1=雨 2=雾 */
    var weather: Int = 0

    private var dayAcc: Double = 0.0
    private var eventCooldown: Int = 12

    fun difficultyDef(): Config.DifficultyDef =
        Config.DIFFICULTIES.firstOrNull { it.key == difficultyKey } ?: Config.DIFFICULTIES[1]

    private fun createState(): CityState = CityState()

    fun init(seed: Int = 20260408, cityName: String = Config.World.city) {
        GameData.seed = seed
        World.generate(seed)
        Growth.reset()
        Citizens.reset()
        Transit.reset()
        Networks.reset()
        CitySystems.reset()
        Civic.reset()
        AdOffers.reset()
        current = createState()
        val s = current!!
        s.cityName = cityName
        World.current?._pop = 0
        speedIdx = 1
        pendingLevelUp = false
        monthFlash = false
        dayAcc = 0.0
        eventCooldown = 12
        timeOfDay = 0.25f
        weather = 0
        pushNews(
            "城市奠基",
            s.cityName + "迎来新任" + Config.World.playerRole +
                "。沿大道修路、划分区，城市将随时间自然生长。",
            "头条"
        )
        ensureQuest()
    }

    fun reset(seed: Int) {
        init(seed)
    }

    // -----------------------------------------------------------------------
    // 时间与速度
    // -----------------------------------------------------------------------
    fun speed(): Int = if (speedIdx in T.speeds.indices) T.speeds[speedIdx] else 1

    fun setSpeed(idx: Int) {
        if (idx !in T.speeds.indices) return
        if (!SpeedBoost.allow(idx)) {
            speedIdx = 1
            return
        }
        speedIdx = idx
    }

    fun dateLabel(): String {
        val s = current ?: return ""
        return String.format("%04d.%02d.%02d", s.year, s.month, s.day)
    }

    fun monthLabel(): String {
        val s = current ?: return ""
        return String.format("%04d.%02d", s.year, s.month)
    }

    fun rankDef(): Config.RankDef {
        val s = current ?: return Config.RANKS.first()
        return Config.RANKS.firstOrNull { it.level == s.rankLevel } ?: Config.RANKS.first()
    }

    fun nextRank(): Config.RankDef? {
        val s = current ?: return Config.RANKS.getOrNull(1)
        return Config.RANKS.firstOrNull { it.level == s.rankLevel + 1 }
    }

    fun refreshRank() {
        val s = current ?: return
        var lv = 1
        for (r in Config.RANKS) {
            if (s.population >= r.popReq && s.happiness >= r.happyReq) lv = r.level
        }
        val examOk = Civic.examPassed >= lv - 1
        if (lv > s.rankLevel && examOk) {
            s.rankLevel = lv
            val r = rankDef()
            pushNews("职级晋升", "你被星辰联邦市政委员会授予「" + r.name + "」。" + r.perk + "。", "市政")
        }
    }

    // -----------------------------------------------------------------------
    // 政策效果聚合（生效期内每天都吃到，而不是启用瞬间冲一次）
    // -----------------------------------------------------------------------
    fun policyMul(key: String): Double {
        val s = current ?: return 1.0
        var m = 1.0
        for (ap in s.activePolicies) {
            val p = Config.POLICIES.firstOrNull { it.id == ap.id } ?: continue
            val e = p.effect
            m *= when (key) {
                "taxMul" -> e.taxMul
                "incomeMul" -> e.incomeMul
                "pollutionMul" -> e.pollutionMul
                "demandR" -> e.demandR
                "demandC" -> e.demandC
                "demandI" -> e.demandI
                "demandO" -> e.demandO
                "powerUseMul" -> e.powerUseMul
                "trafficMul" -> e.trafficMul
                "fireMul" -> e.fireMul
                "upgradeMul" -> e.upgradeMul
                "upkeepMul" -> e.upkeepMul
                else -> 1.0
            }
        }
        return m
    }

    fun policyHappyBonus(): Double {
        val s = current ?: return 0.0
        var h = 0.0
        for (ap in s.activePolicies) {
            val p = Config.POLICIES.firstOrNull { it.id == ap.id } ?: continue
            h += p.effect.happy
        }
        return h
    }

    fun policyStatusLine(): String {
        val s = current ?: return "暂无生效政策"
        if (s.activePolicies.isEmpty()) return "暂无生效政策"
        return s.activePolicies.joinToString(" · ") { ap ->
            val p = Config.POLICIES.firstOrNull { it.id == ap.id }
            (p?.name ?: ap.id) + " 剩" + ap.daysLeft + "天"
        }
    }

    // -----------------------------------------------------------------------
    // 每日结算
    // -----------------------------------------------------------------------
    data class HappyBreakdown(
        val base: Double, val service: Double, val pollution: Double,
        val coveragePenalty: Double, val taxPenalty: Double, val event: Double,
        val policy: Double, val commute: Double, val jobs: Double, val target: Double
    )

    /** 满意度分解（对照拆解文档：健康/教育/通勤/就业/犯罪/政策） */
    fun happinessBreakdown(): HappyBreakdown {
        val s = current ?: return HappyBreakdown(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val st = World.stats()
        val base = 48.0
        var service = 0.0
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
            service += cfg.happy * min(1.2, covered / 14.0)
        }
        val pollution = -st.pollution * E.pollutionHappy * policyMul("pollutionMul")
        val cov = s.lastCoverage ?: World.coverage()
        val coveragePenalty =
            -(1 - cov.power) * Config.COVERAGE.powerHappyPenalty -
                (1 - cov.water) * Config.COVERAGE.waterHappyPenalty -
                (1 - cov.garbage) * Config.COVERAGE.garbageHappyPenalty
        val taxPenalty =
            -max(0.0, (s.taxRes - Config.TAX.default).toDouble()) * Config.TAX.happyPerPoint -
                max(0.0, (s.taxCom - Config.TAX.default).toDouble()) * Config.TAX.happyPerPoint -
                max(0.0, (s.taxInd - Config.TAX.default).toDouble()) * Config.TAX.happyPerPoint -
                max(0.0, (s.taxOff - Config.TAX.default).toDouble()) * Config.TAX.happyPerPoint
        var event = 0.0
        for (ev in s.activeEvents) event += ev.happy
        val policy = policyHappyBonus()
        val commute = -s.congestion * 8.0 - Citizens.avgCommute * 12.0 + Transit.coverageBoost() * 8.0
        val labor = max(1.0, s.population * 0.62)
        val jobRate = min(1.2, s.jobs / labor)
        val crimePen = -s.crime * 0.12
        val sewerPen = -(1.0 - s.sewerCoverage) * 8.0
        val rankHappy = if (s.rankLevel >= 6) 4.0 else 0.0
        val jobs = (jobRate - 0.85) * 16.0 + (s.education - 40) * 0.08 + (s.health - 55) * 0.06 + crimePen + sewerPen + rankHappy
        val target = max(
            Config.RESOURCES.happinessMin,
            min(
                Config.RESOURCES.happinessMax,
                base + service + pollution + coveragePenalty + taxPenalty + event + policy + commute + jobs
            )
        )
        return HappyBreakdown(base, service, pollution, coveragePenalty, taxPenalty, event, policy, commute, jobs, target)
    }

    private fun computeHappinessTarget(st: com.dshx.game.su.world.WorldStats): Double =
        happinessBreakdown().target

    private fun onNewDay() {
        val s = current ?: return
        s.day += 1

        val st = World.stats()
        s.pollution = (st.pollution * policyMul("pollutionMul")).toInt()
        // 天气随机切换
        if (kotlin.random.Random.nextInt(100) < 8) {
            weather = kotlin.random.Random.nextInt(3)
        }
        var cov = World.coverage()
        // 电力/供水供需：容量 vs 建筑数，不足则覆盖比例打折
        var powerCap = 0
        var waterCap = 0
        var eduScore = 0.0
        var healthScore = 0.0
        var transitScore = 0.0
        var tradeIncome = 0.0
        for (e in World.allBuildings()) {
            if (!e.b.isService) continue
            val cfg = World.serviceConfig(e.b.service) ?: continue
            powerCap += cfg.powerCap
            waterCap += cfg.waterCap
            when (cfg.category) {
                Config.ServiceCat.EDUCATION -> eduScore += cfg.radius * 0.8
                Config.ServiceCat.HEALTH -> healthScore += cfg.radius * 0.7
                Config.ServiceCat.TRANSIT -> {
                    transitScore += cfg.radius * 0.5
                    when (cfg.id) {
                        "harbor", "rail_station" -> tradeIncome += 8.0
                        "airport" -> tradeIncome += 22.0
                        "metro" -> tradeIncome += 4.0
                    }
                }
            }
        }
        for (d in Networks.districts) {
            if (d.policy == "old_town") tradeIncome += 6.0
        }
        val bldN = max(1, st.resCount + st.comCount + st.indCount + st.offCount)
        val powerNeed = bldN * policyMul("powerUseMul")
        val supplyFactor = min(1.0, powerCap.toDouble() / powerNeed)
        val waterFactor = min(1.0, waterCap.toDouble() / bldN)
        cov = cov.copy(power = cov.power * supplyFactor.toFloat(), water = cov.water * waterFactor.toFloat())
        s.lastCoverage = cov
        s.education = min(100.0, s.education + (eduScore * 0.08) * (if (cov.education > 0.3f) 1.0 else 0.2) - 0.04)
        s.health = min(100.0, max(20.0, 50.0 + healthScore * 0.6 - s.pollution * 0.35 + (cov.health * 18)))
        s.jobs = st.comCap + st.indCap + st.offCap
        val trafficLoad = s.population / 12.0 + st.comCount * 1.6 + st.indCount * 2.0 + st.offCount * 1.4
        val cap = max(12.0, st.roadCapacity.toDouble())
        s.congestion = max(
            0.0,
            min(
                1.0,
                trafficLoad / cap * policyMul("trafficMul") *
                    (1.0 - min(0.55, transitScore / 40.0 + Transit.coverageBoost()))
            )
        )
        if (s.day % 3 == 1) Citizens.rebuild()
        CitySystems.daily(s, st, cov)

        // 人口 / 岗位：住宅迁入，商工办入驻；缺服务或低满意则废弃
        val satisMul = max(0.35, min(1.2, s.happiness / 55.0))
        var totalRes = 0
        for (e in World.allBuildings()) {
            val b = e.b
            if (b.isService) continue
            b.ageDays += 1
            val lv = Config.GROWN[b.zone]?.levels?.getOrNull(b.level - 1) ?: continue
            val powered = World.isCoveredBy(e.x, e.y, Config.ServiceCat.POWER)
            val watered = World.isCoveredBy(e.x, e.y, Config.ServiceCat.WATER)
            val land = World.landValue(e.x, e.y)
            val shouldAbandon = (!powered && b.ageDays > 45) ||
                (!watered && b.zone == "residential" && b.ageDays > 50) ||
                (s.happiness < Config.GROWTH.abandonHappy && b.ageDays > 60 && land < 4 && b.residents == 0)
            if (shouldAbandon && !b.abandoned) {
                b.abandoned = true
                b.residents = 0
                b.workers = 0
                pushNews("建筑废弃", "一处" + (Config.GROWN[b.zone]?.name ?: "建筑") + "因长期断电/缺水被弃置。", "城建")
            }
            if (b.abandoned) {
                if (powered || watered || s.happiness > 35 || b.ageDays < 45) {
                    b.abandoned = false
                    if (b.zone == "residential" && b.residents == 0) {
                        b.residents = max(2, lv.cap / 6)
                    }
                } else continue
            }
            when (b.zone) {
                "residential" -> {
                    val powerMulLocal = if (powered) 1.0 else 0.35
                    val waterMulLocal = if (watered) 1.0 else 0.45
                    if (b.residents < lv.cap) {
                        val migrate = max(1, (lv.cap * 0.22 * satisMul * powerMulLocal * waterMulLocal).toInt())
                        b.residents = min(lv.cap, b.residents + migrate)
                    }
                    if (!powered && b.residents > 2 && b.ageDays > 20) {
                        b.residents = max(1, b.residents - 1)
                    }
                    if (s.happiness < 22 && b.residents > 1) b.residents = max(1, b.residents - 1)
                    totalRes += b.residents
                }
                else -> {
                    val fill = max(1, (lv.cap * 0.22 * (if (powered) 1.0 else 0.3)).toInt())
                    b.workers = min(lv.cap, b.workers + fill)
                }
            }
        }
        s.population = totalRes.toDouble()
        World.current?._pop = totalRes
        if (s.population > 0 && Citizens.agents.isEmpty()) Citizens.rebuild()

        val occRatio = if (st.resCap > 0) s.population / st.resCap else 0.0
        var bizCom = 0.0
        var bizInd = 0.0
        var bizOff = 0.0
        for (e in World.allBuildings()) {
            val b = e.b
            if (b.isService || b.abandoned) continue
            val lv = Config.GROWN[b.zone]?.levels?.getOrNull(b.level - 1) ?: continue
            val fill = if (lv.cap > 0) b.occupied().toDouble() / lv.cap else occRatio
            when (b.zone) {
                "commercial" -> bizCom += lv.income * fill
                "industrial" -> bizInd += lv.income * fill * Networks.districtMul("industry", e.x, e.y)
                "office" -> bizOff += lv.income * fill
            }
        }
        val powerMul = Config.COVERAGE.powerIncomeFloor +
            (1 - Config.COVERAGE.powerIncomeFloor) * cov.power
        val eduMul = 0.85 + s.education / 250.0
        val congMul = 1.0 - s.congestion * 0.35
        val landmarkCom = if (World.hasLandmark("stock_exchange")) 1.12 else 1.0
        val landmarkTour = (if (World.hasLandmark("tv_tower")) 10.0 else 0.0) +
            (if (World.hasLandmark("stadium")) 8.0 else 0.0)
        val bizBase = (bizCom * s.taxCom / 10.0 * landmarkCom + bizInd * s.taxInd / 10.0 + bizOff * s.taxOff / 10.0) *
            powerMul * eduMul * congMul
        val taxIncome = (s.population * E.taxPerPopPerDay + E.baseIncomePerDay) *
            policyMul("taxMul") * (s.taxRes / 10.0)
        val bizIncome = bizBase * policyMul("incomeMul")
        val rankTrade = if (s.rankLevel >= 5) 1.08 else 1.0
        val taxBoost = if (s.doubleTaxDays > 0) 2.0 else 1.0
        val income = (taxIncome * taxBoost) + bizIncome + (tradeIncome + landmarkTour) * rankTrade
        var upkeep = 0.0
        val ww = World.current
        if (ww != null) {
            for (y in 1..ww.rows) for (x in 1..ww.cols) {
                val kind = ww.grid[y - 1][x - 1].road ?: continue
                upkeep += Config.ROAD[kind]?.upkeep ?: E.upkeepPerRoadDay
            }
        }
        for (e in World.allBuildings()) {
            if (!e.b.isService) continue
            val cfg = World.serviceConfig(e.b.service)
            if (cfg != null) {
                val budget = when (cfg.category) {
                    Config.ServiceCat.HEALTH -> s.budgetHealth
                    Config.ServiceCat.EDUCATION -> s.budgetEdu
                    Config.ServiceCat.SAFETY -> s.budgetSafety
                    Config.ServiceCat.TRANSIT -> s.budgetTransit
                    else -> 100
                }
                upkeep += cfg.upkeep / 30.0 * (budget / 100.0)
            }
        }
        val rankUpkeep = if (s.rankLevel >= 4) 0.94 else 1.0
        upkeep *= policyMul("upkeepMul") * (0.9 + Networks.districts.count { it.policy == "ev" } * 0.04) * rankUpkeep
        val diff = difficultyDef()
        var eventIncomeMul = 1.0
        for (ev in s.activeEvents) eventIncomeMul *= ev.incomeMul
        val gross = income * diff.incomeMul * eventIncomeMul
        val spend = if (sandbox) 0.0 else upkeep * diff.upkeepMul
        val net = gross - spend
        s.dayIncomeTax = taxIncome
        s.dayIncomeBiz = bizIncome
        s.dayIncomeTrade = tradeIncome
        s.lastIncome = gross
        s.lastUpkeep = spend
        s.lastNet = net
        s.funds += net
        if (net >= 0) s.totalIncome += net else s.totalSpent += -net
        if (s.funds < 0) {
            s.bankruptDays += 1
            if (s.bankruptDays == 1) pushNews("财政告急", "金库见底，公共服务将收缩。尽快扩税基或贷款。", "财政")
        } else {
            s.bankruptDays = 0
        }

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
        if (s.doubleTaxDays > 0) s.doubleTaxDays -= 1
        s.merit += max(0.0, s.lastNet * 0.02 + s.population * 0.001)
        Civic.tickDay(s)
        AdOffers.tickDay(s)
        refreshRank()

        // 满意度向目标靠拢（没人时回到中性，不为空城硬扣）
        val target = if (s.population < 1) 52.0 else computeHappinessTarget(st)
        s.happiness += (target - s.happiness) * 0.12

        // 火灾：无消防覆盖的建筑有概率起火被烧毁
        val grown = World.allBuildings().filter { !it.b.isService }
        if (grown.isNotEmpty() && kotlin.random.Random.nextDouble() <
            Config.COVERAGE.fireChancePerDay * diff.eventMul * policyMul("fireMul")) {
            val victim = grown[kotlin.random.Random.nextInt(grown.size)]
            if (!World.isCoveredBy(victim.x, victim.y, Config.ServiceCat.SAFETY)) {
                World.bulldoze(victim.x, victim.y)
                pushNews("火灾！", "一处建筑因缺乏消防覆盖被烧毁。", "突发")
            } else {
                pushNews("火情解除", "消防站及时扑灭了一起火情。", "突发")
            }
        }

        // 成就检查
        val bldCount = st.resCount + st.comCount + st.indCount + st.offCount + st.serviceCount
        for (a in Config.ACHIEVEMENTS) {
            if (a.id in s.achievements) continue
            val v = when (a.type) {
                "pop" -> s.population
                "buildings" -> bldCount.toDouble()
                "funds" -> s.funds
                "happiness" -> s.happiness
                "exam" -> Civic.examPassed.toDouble()
                "mail" -> Civic.complaintsHandled.toDouble()
                "school" -> Civic.schoolRate * 100.0
                "roads" -> st.roadCount.toDouble()
                "services" -> st.serviceCount.toDouble()
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

        // 市政任务检查
        ensureQuest()
        val q = s.quest
        if (q != null && !q.done && questValue(q.type) >= q.target) {
            q.done = true
            s.funds += q.reward
            pushNews("任务完成：" + q.name, "达成目标，奖励 " + q.reward + " 万。", "任务")
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
                    st.resCount + st.comCount + st.indCount + st.offCount + st.serviceCount
                ),
                "月报"
            )
        }
    }

    /** dt = 真实秒；内部乘速度 */
    fun tick(dt: Float) {
        val s = current ?: return
        if (speedIdx >= 2 && !SpeedBoost.isActive()) speedIdx = 1
        val simDt = dt * speed()
        s.playSeconds += dt.toDouble()
        // 昼夜循环独立于游戏速度：固定 120 秒一轮（避免闪烁）
        timeOfDay = (timeOfDay + dt / 120f) % 1f
        if (simDt <= 0) return
        dayAcc += simDt
        while (dayAcc >= T.daySeconds) {
            dayAcc -= T.daySeconds
            onNewDay()
        }
        Citizens.tick(simDt)
        Transit.tick(simDt)
        CitySystems.tick(simDt)
    }

    // -----------------------------------------------------------------------
    // 操作（返回 ok, msg）
    // -----------------------------------------------------------------------
    fun placeRoad(x: Int, y: Int, kind: String): Pair<Boolean, String?> {
        val (ok, msg) = World.canRoad(x, y, kind)
        if (!ok) return false to msg
        val r = Config.ROAD[kind] ?: return false to "未知道路"
        val s = current ?: return false to null
        val exist = World.tile(x, y)?.road
        val oldCost = if (exist != null) Config.ROAD[exist]?.cost ?: 0 else 0
        val pay = max(0, r.cost - oldCost)
        if (!sandbox && s.funds < pay) {
            AdOffers.offerShortfall(pay.toInt(), "修路")
            return false to ("资金不足（需 ¥" + pay + "万）")
        }
        World.setRoad(x, y, kind)
        if (!sandbox) s.funds -= pay
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
            AdOffers.offerShortfall(cost, "划区")
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
        if (!sandbox && s.funds < cfg.cost) {
            AdOffers.offerShortfall(cfg.cost, "建造" + cfg.name)
            return false to ("资金不足（需 ¥" + cfg.cost + "万）")
        }
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
        if (s.activePolicies.any { it.id == pid }) return false to "已生效"
        if ((s.policyCooldowns[pid] ?: 0) > 0) {
            return false to ("冷却中，还需 " + (s.policyCooldowns[pid] ?: 0) + " 天")
        }
        val p = Config.POLICIES.firstOrNull { it.id == pid } ?: return false to "未知政策"
        if (p.effect.cost > 0 && !sandbox && s.funds < p.effect.cost) return false to "资金不足"
        s.activePolicies.add(PolicyActive(pid, p.days))
        s.policyCooldowns[pid] = p.days + p.cooldown
        if (p.effect.cost > 0 && !sandbox) {
            s.funds -= p.effect.cost
            s.totalSpent += p.effect.cost
        }
        pushNews(
            "新政发布：" + p.name,
            p.desc + " 生效 " + p.days + " 天。今日起税收/需求/污染按政策结算。",
            "政策"
        )
        return true to ("已启用：" + p.name + " · 生效 " + p.days + " 天")
    }

    fun paintPipe(x: Int, y: Int): Pair<Boolean, String?> {
        val (ok, msg) = Networks.canPipe(x, y)
        if (!ok) return false to msg
        val t = World.tile(x, y) ?: return false to "越界"
        if (t.pipe) return true to null
        val s = current ?: return false to null
        val cost = 2
        if (!sandbox && s.funds < cost) return false to "资金不足（水管 2 万/格）"
        Networks.setPipe(x, y, true)
        if (!sandbox) s.funds -= cost
        Networks.recount()
        return true to null
    }

    fun paintCable(x: Int, y: Int): Pair<Boolean, String?> {
        val t = World.tile(x, y) ?: return false to "越界"
        if (t.terrain == "water") return false to "水域无法铺电缆"
        if (t.cable) return true to null
        val s = current ?: return false to null
        val cost = 2
        if (!sandbox && s.funds < cost) return false to "资金不足（电缆 2 万/格）"
        Networks.setCable(x, y, true)
        if (!sandbox) s.funds -= cost
        Networks.recount()
        return true to null
    }

    fun paintDistrict(x: Int, y: Int): Pair<Boolean, String?> {
        Networks.ensureDistrict()
        Networks.paintDistrict(x, y)
        return true to null
    }

    fun tapBusStop(x: Int, y: Int): Pair<Boolean, String?> = Transit.addDraftStop(x, y)

    fun paintSewer(x: Int, y: Int): Pair<Boolean, String?> {
        val t = World.tile(x, y) ?: return false to "越界"
        if (t.sewer) return true to null
        val s = current ?: return false to null
        if (!sandbox && s.funds < 2) return false to "资金不足（污水管 2 万/格）"
        Networks.setSewer(x, y, true)
        if (!sandbox) s.funds -= 2
        Networks.recount()
        return true to null
    }

    fun paintMetro(x: Int, y: Int): Pair<Boolean, String?> {
        val t = World.tile(x, y) ?: return false to "越界"
        if (t.terrain == "water") return false to "水域无法挖地铁"
        if (t.metro) return true to null
        val s = current ?: return false to null
        if (!sandbox && s.funds < 6) return false to "资金不足（地铁隧道 6 万/格）"
        Networks.setMetro(x, y, true)
        if (!sandbox) s.funds -= 6
        Networks.recount()
        return true to null
    }

    fun plantTree(x: Int, y: Int): Pair<Boolean, String?> {
        val s = current ?: return false to null
        if (!sandbox && s.funds < 1) return false to "资金不足"
        if (!World.plantTree(x, y)) return false to "这里不能种树"
        if (!sandbox) s.funds -= 1
        return true to null
    }

    fun raiseLand(x: Int, y: Int): Pair<Boolean, String?> {
        val s = current ?: return false to null
        if (!sandbox && s.funds < 3) return false to "资金不足"
        if (!World.raiseLand(x, y)) return false to "不能抬升占用格"
        if (!sandbox) s.funds -= 3
        return true to null
    }

    fun lowerLand(x: Int, y: Int): Pair<Boolean, String?> {
        val s = current ?: return false to null
        if (!sandbox && s.funds < 3) return false to "资金不足"
        if (!World.lowerLand(x, y)) return false to "不能降低占用格"
        if (!sandbox) s.funds -= 3
        return true to null
    }

    /** 市政贷款：借入 LOAN.amount，按日自动还款 */
    fun borrow(): Pair<Boolean, String?> {
        val s = current ?: return false to null
        if (s.loanDebt > 0) return false to "尚有未还贷款"
        if (s.loanCooldown > 0) return false to ("冷却 " + s.loanCooldown + " 天")
        val amount = Config.LOAN.amount * (if (s.rankLevel >= 2) 1.4 else 1.0)
        s.loanDebt = amount
        s.funds += amount
        pushNews("市政贷款", "借入 " + amount.toInt() + " 万，将按日自动还款。", "财政")
        return true to null
    }

    /** 市政任务当前进度值 */
    fun questValue(type: String): Double {
        val s = current ?: return 0.0
        val st = World.stats()
        return when (type) {
            "pop" -> s.population
            "funds" -> s.funds
            "buildings" -> (st.resCount + st.comCount + st.indCount + st.offCount).toDouble()
            "happy" -> s.happiness
            else -> 0.0
        }
    }

    /** 无任务或已完成时生成新任务 */
    fun ensureQuest() {
        val s = current ?: return
        if (s.quest == null || s.quest!!.done) {
            val q = Config.QUESTS[kotlin.random.Random.nextInt(Config.QUESTS.size)]
            s.quest = Quest(q.type, q.name, q.target, q.reward)
        }
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
