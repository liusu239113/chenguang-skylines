package com.chenguang.skylines.world

import com.chenguang.skylines.Config
import com.chenguang.skylines.GameData
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ============================================================================
// Growth — 城市成长引擎，与 scripts/world/Growth.lua 1:1 对应
//   分区内、邻路的空地，按 RCI 需求自动长出建筑；
//   已有成长楼在需求高 + 地价高（服务覆盖）时升级 1→3 级
// ============================================================================

object Growth {

    private val G = Config.GROWTH

    private var acc: Double = 0.0

    /** 累计模拟时间（秒，含速度倍率），生长动画用 */
    var simTime: Double = 0.0

    data class Demand(var r: Double, var c: Double, var i: Double)

    var lastDemand: Demand = Demand(0.5, 0.3, 0.4)

    /** 本次 tick 长了几栋 */
    var grewCount: Int = 0

    fun reset() {
        acc = 0.0
        simTime = 0.0
        grewCount = 0
        lastDemand = Demand(0.5, 0.3, 0.4)
    }

    fun computeDemand(st: WorldStats, pop: Int): Demand {
        val s = GameData.current
        val edu = s?.education ?: 18.0
        val happy = s?.happiness ?: 60.0
        val taxRes = s?.taxRes ?: Config.TAX.default
        val taxCom = s?.taxCom ?: Config.TAX.default
        val taxInd = s?.taxInd ?: Config.TAX.default
        val taxDragR = 1.0 - max(0, taxRes - Config.TAX.default) * 0.04
        val taxDragC = 1.0 - max(0, taxCom - Config.TAX.default) * 0.04
        val taxDragI = 1.0 - max(0, taxInd - Config.TAX.default) * 0.04
        val overzoneR = max(0, st.resCount - 8) * 1.6
        val overzoneC = max(0, st.comCount - 6) * 1.4
        val overzoneI = max(0, st.indCount - 6) * 1.4
        val attract = (happy - 50) * 0.35 + (edu - 20) * 0.12
        val r = ((st.comCap + st.indCap) * 1.15 + 40 + attract - pop - overzoneR) * taxDragR
        val c = (pop * (0.50 + edu / 400.0) + 25 - st.comCap - overzoneC) * taxDragC
        val i = (pop * 0.45 + 50 + edu * 0.2 - st.indCap - overzoneI) * taxDragI
        val normR = max(st.resCap + 40.0, 1.0)
        val normC = max(st.comCap + 30.0, 1.0)
        val normI = max(st.indCap + 40.0, 1.0)
        return Demand(
            r = max(0.0, min(1.0, r / normR * GameData.policyMul("demandR"))),
            c = max(0.0, min(1.0, c / normC * GameData.policyMul("demandC"))),
            i = max(0.0, min(1.0, i / normI * GameData.policyMul("demandI")))
        )
    }

    private fun growthStep(sfx: ((String, Float) -> Unit)?) {
        val w = World.current ?: return
        val st = World.stats()
        val pop = w._pop
        val demand = computeDemand(st, pop)
        lastDemand = demand

        // 按需求权重选本步要生长的类型
        val pool = mutableListOf<Pair<String, Double>>()
        if (demand.r >= G.demandMin) pool.add("residential" to demand.r)
        if (demand.c >= G.demandMin) pool.add("commercial" to demand.c)
        if (demand.i >= G.demandMin) pool.add("industrial" to demand.i)

        grewCount = 0
        if (pool.isEmpty()) return

        // 收集候选空地：分区匹配 + 邻路
        val want = pool.map { it.first }.toSet()
        val candidates = mutableListOf<Triple<Int, Int, String>>()
        for (y in 2 until w.rows) {
            for (x in 2 until w.cols) {
                val t = w.grid[y - 1][x - 1]
                if (t.zone in want && t.building == null && t.road == null && t.terrain != "water") {
                    if (World.isRoad(x + 1, y) || World.isRoad(x - 1, y) ||
                        World.isRoad(x, y + 1) || World.isRoad(x, y - 1)
                    ) {
                        candidates.add(Triple(x, y, t.zone))
                    }
                }
            }
        }
        if (candidates.isNotEmpty() && Random.nextDouble() < G.spawnChance) {
            val pick = candidates[Random.nextInt(candidates.size)]
            if (World.growBuilding(pick.third, pick.first, pick.second, 1, simTime)) {
                grewCount++
                sfx?.invoke("sfx_build", 0.35f)
            }
        }

        // 升级判定
        val upCandidates = World.allBuildings().filter {
            !it.b.isService && it.b.level < (Config.GROWN[it.b.zone]?.levels?.size ?: 1)
        }
        val upgradeChance = G.upgradeChance * GameData.policyMul("upgradeMul")
        if (upCandidates.isNotEmpty() && Random.nextDouble() < upgradeChance) {
            val e = upCandidates[Random.nextInt(upCandidates.size)]
            val zoneDemand = when (e.b.zone) {
                "residential" -> demand.r
                "commercial" -> demand.c
                else -> demand.i
            }
            val ageOk = (simTime - e.b.born) >= G.upgradeAgeDays * Config.TIME.daySeconds
            val land = World.landValue(e.x, e.y)
            val covOk = World.isCoveredBy(e.x, e.y, Config.ServiceCat.POWER) &&
                World.isCoveredBy(e.x, e.y, Config.ServiceCat.WATER)
            if (zoneDemand >= G.demandMin && ageOk && land >= G.landValueUpgrade && covOk &&
                Random.nextDouble() < zoneDemand
            ) {
                World.upgradeBuilding(e.x, e.y)
            }
        }
    }

    /** dt：已含速度倍率的模拟时间（秒） */
    fun tick(dt: Double, sfx: ((String, Float) -> Unit)?) {
        simTime += dt
        acc += dt
        if (acc >= G.tickSeconds) {
            acc -= G.tickSeconds
            growthStep(sfx)
        }
    }
}
