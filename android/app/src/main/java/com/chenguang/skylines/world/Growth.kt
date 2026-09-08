package com.chenguang.skylines.world

import com.chenguang.skylines.Config
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
        val normR = max(st.resCap + 40.0, 1.0)
        val normC = max(st.comCap + 30.0, 1.0)
        val normI = max(st.indCap + 40.0, 1.0)
        val r = (st.comCap + st.indCap) * 1.15 + 40 - pop      // 岗位缺口 → 住宅需求
        val c = pop * 0.55 + 25 - st.comCap                    // 消费缺口 + 基础客流 → 商业需求
        val i = pop * 0.45 + 50 - st.indCap                     // 外部订单 → 工业需求
        return Demand(
            r = max(0.0, min(1.0, r / normR)),
            c = max(0.0, min(1.0, c / normC)),
            i = max(0.0, min(1.0, i / normI))
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
        if (upCandidates.isNotEmpty() && Random.nextDouble() < G.upgradeChance) {
            val e = upCandidates[Random.nextInt(upCandidates.size)]
            val zoneDemand = when (e.b.zone) {
                "residential" -> demand.r
                "commercial" -> demand.c
                else -> demand.i
            }
            if (zoneDemand >= G.demandMin && Random.nextDouble() < zoneDemand) {
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
