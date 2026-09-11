package com.dshx.game.she.world

import com.dshx.game.she.CityState
import com.dshx.game.she.Config
import com.dshx.game.she.GameData
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class EmergencyCar(
    var kind: String,               // garbage | fire | ambulance | hearse | police
    var x: Int, var y: Int,
    var destX: Int, var destY: Int,
    var path: MutableList<Int> = mutableListOf(),
    var pathI: Int = 0,
    var prog: Float = 0f,
    var speed: Float = 2.2f
)

/**
 * 污水 / 垃圾车 / 消防救护车 / 犯罪监狱 / 殡葬 / 分层污染
 */
object CitySystems {

    val cars: MutableList<EmergencyCar> = mutableListOf()
    var selected: EmergencyCar? = null
    var groundPolAvg: Double = 0.0
    var waterPolAvg: Double = 0.0
    var fires: Int = 0

    fun reset() {
        cars.clear()
        selected = null
        groundPolAvg = 0.0
        waterPolAvg = 0.0
        fires = 0
    }

    fun daily(s: CityState, st: WorldStats, cov: Coverage) {
        val w = World.current ?: return
        val sewageN = World.allBuildings().count { it.b.service == "sewage" }
        val cemeteryCap = World.allBuildings().count { it.b.service == "cemetery" } * 80 +
            World.allBuildings().count { it.b.service == "crematorium" } * 200
        val policeN = World.allBuildings().count { it.b.service == "police" }
        val fireN = World.allBuildings().count { it.b.service == "fire_station" }
        val clinicN = World.allBuildings().count {
            it.b.service == "clinic" || it.b.service == "hospital"
        }

        // 污水覆盖：污水管 + 处理厂
        var sewerHits = 0
        var sewerNeed = 0
        var gSum = 0
        var wSum = 0
        var gN = 0
        for (y in 1..w.rows) for (x in 1..w.cols) {
            val t = w.grid[y - 1][x - 1]
            val b = t.building
            if (b != null && !b.isService) {
                sewerNeed++
                if (t.sewer && sewageN > 0) sewerHits++ else {
                    t.waterPol = min(100, t.waterPol + 4)
                    if (t.terrain == "water") t.waterPol = min(100, t.waterPol + 8)
                }
                if (t.sewer && sewageN > 0) t.waterPol = max(0, t.waterPol - 10)
                b.garbage = min(100, b.garbage + 3 + b.occupied() / 8)
                val unemployed = max(0.0, 1.0 - (s.jobs / max(1.0, s.population * 0.62)))
                val eduLow = max(0.0, (40 - s.education) / 40.0)
                val police = World.isCoveredBy(x, y, Config.ServiceCat.SAFETY)
                val crimeGain = ((if (police) 0 else 4) + unemployed * 8 + eduLow * 6).toInt()
                b.crime = max(0, min(100, b.crime + crimeGain - policeN * 2 - (s.budgetSafety - 80) / 10))
                if (b.zone == "industrial") t.groundPol = min(100, t.groundPol + 5)
            }
            if (t.terrain == "forest") t.groundPol = max(0, t.groundPol - 6)
            if (t.terrain == "water") {
                // 顺流：向南扩散一点水污染
                val down = World.tile(x, y + 1)
                if (down != null && down.terrain == "water") {
                    down.waterPol = min(100, down.waterPol + t.waterPol / 8)
                }
            }
            gSum += t.groundPol
            wSum += t.waterPol
            gN++
        }
        s.sewerCoverage = if (sewerNeed == 0) 1.0 else sewerHits.toDouble() / sewerNeed
        groundPolAvg = if (gN == 0) 0.0 else gSum.toDouble() / gN
        waterPolAvg = if (gN == 0) 0.0 else wSum.toDouble() / gN
        s.pollution = ((st.pollution + groundPolAvg * 0.4 + waterPolAvg * 0.3) *
            GameData.policyMul("pollutionMul")).toInt()

        // 垃圾积压
        var trash = 0
        for (e in World.allBuildings()) if (!e.b.isService) trash += e.b.garbage
        s.garbageBacklog = trash
        val dumpN = World.allBuildings().count { it.b.service == "landfill" || it.b.service == "incinerator" }
        if (dumpN > 0 && trash > 20) dispatch("garbage", min(3, dumpN * 2))

        // 死亡 / 殡葬
        val deaths = max(0, (s.population * 0.004 * (1.3 - s.health / 140.0)).toInt())
        s.deathsPending += deaths
        val cremate = World.allBuildings().count { it.b.service == "crematorium" } * 12
        val bury = min(s.deathsPending, cemeteryCap / 4 + cremate)
        s.deathsPending = max(0, s.deathsPending - bury)
        s.cemeteryUsed = min(cemeteryCap, s.cemeteryUsed + max(0, bury - cremate) - cremate)
        if (s.deathsPending > 20) {
            s.health = max(20.0, s.health - 1.5)
            if (s.day % 7 == 0) GameData.pushNews("遗体堆积", "殡葬能力不足，健康下降。", "民生")
        }
        val hearseCap = World.allBuildings().count { it.b.service == "cemetery" } * 1 +
            World.allBuildings().count { it.b.service == "crematorium" } * 2
        if (s.deathsPending > 0 && hearseCap > 0) dispatch("hearse", hearseCap)

        var crimeSum = 0
        var crimeN = 0
        val hasPolice = World.allBuildings().any { it.b.service == "police" }
        for (e in World.allBuildings()) if (!e.b.isService) {
            crimeSum += e.b.crime
            crimeN++
            if (e.b.crime > 70 && Random.nextDouble() < 0.08 && hasPolice) {
                e.b.crime = max(0, e.b.crime - 30)
                dispatchTo("police", e.x, e.y)
            }
        }
        s.crime = if (crimeN == 0) 8.0 else crimeSum.toDouble() / crimeN
        s.prisonUsed = 0

        // 健康：污水 / 水污染 / 医疗预算
        val waterHit = waterPolAvg / 12.0
        val healthBudget = s.budgetHealth / 100.0
        s.health = min(
            100.0,
            max(
                18.0,
                s.health + clinicN * 0.4 * healthBudget - waterHit - (1.0 - s.sewerCoverage) * 3.0
            )
        )
        val ambCap = World.allBuildings().count { it.b.service == "clinic" } * 2 +
            World.allBuildings().count { it.b.service == "hospital" } * 5
        if (s.health < 55 && ambCap > 0) dispatch("ambulance", min(ambCap, 6))

        // 火灾：无消防覆盖的老建筑
        fires = 0
        for (e in World.allBuildings()) {
            val t = World.tile(e.x, e.y) ?: continue
            if (t.onFire) {
                fires++
                if (World.isCoveredBy(e.x, e.y, Config.ServiceCat.SAFETY) && fireN > 0) {
                    t.onFire = false
                    dispatchTo("fire", e.x, e.y)
                } else if (Random.nextDouble() < 0.35) {
                    if (e.b.isService) {
                        t.onFire = false
                    } else {
                        World.bulldoze(e.x, e.y)
                        GameData.pushNews("大火蔓延", "一处建筑被烧毁。", "突发")
                    }
                }
            }
        }
        val fireUnlock = World.serviceConfig("fire_station")?.unlockPop ?: 50
        val fireChance = Config.COVERAGE.fireChancePerDay * GameData.policyMul("fireMul") *
            (if (fireN == 0) 1.6 else 1.0)
        if (s.population.toInt() >= fireUnlock &&
            st.resCount + st.comCount + st.indCount > 0 &&
            Random.nextDouble() < fireChance
        ) {
            val grown = World.allBuildings().filter { !it.b.isService && !it.b.abandoned }
            if (grown.isNotEmpty()) {
                val v = grown[Random.nextInt(grown.size)]
                World.tile(v.x, v.y)?.onFire = true
                if (fireN > 0) dispatchTo("fire", v.x, v.y)
            }
        }

        // 地铁运量
        if (Networks.metroCount > 8) {
            s.congestion = max(0.0, s.congestion - 0.08)
        }
    }

    fun tick(dt: Float) {
        for (i in cars.indices.reversed()) {
            val c = cars[i]
            if (c.path.size <= 1) {
                val from = Citizens.nearestRoad(c.x, c.y) ?: (c.x to c.y)
                val to = Citizens.nearestRoad(c.destX, c.destY) ?: (c.destX to c.destY)
                c.path = Citizens.bfsRoad(from.first, from.second, to.first, to.second)
                c.pathI = 0
                c.prog = 0f
            }
            if (c.path.size <= 1) {
                arrive(c)
                if (selected === c) selected = null
                cars.removeAt(i)
                continue
            }
            c.prog += c.speed * dt * 0.7f
            while (c.prog >= 1f && c.pathI < c.path.lastIndex) {
                c.prog -= 1f
                c.pathI++
                c.x = Citizens.unpackX(c.path[c.pathI])
                c.y = Citizens.unpackY(c.path[c.pathI])
            }
            if (c.pathI >= c.path.lastIndex) {
                arrive(c)
                if (selected === c) selected = null
                cars.removeAt(i)
            }
        }
    }

    private fun arrive(c: EmergencyCar) {
        when (c.kind) {
            "garbage" -> {
                for (e in World.allBuildings()) {
                    if (abs(e.x - c.destX) + abs(e.y - c.destY) <= 3 && !e.b.isService) {
                        e.b.garbage = max(0, e.b.garbage - 40)
                    }
                }
            }
            "fire" -> World.tile(c.destX, c.destY)?.onFire = false
            "ambulance" -> GameData.current?.health = min(100.0, (GameData.current?.health ?: 60.0) + 0.4)
            "hearse" -> GameData.current?.deathsPending = max(0, (GameData.current?.deathsPending ?: 0) - 3)
            "police" -> {
                val t = World.tile(c.destX, c.destY)?.building
                if (t != null) t.crime = max(0, t.crime - 25)
            }
        }
    }

    private fun dispatch(kind: String, n: Int) {
        val stations = World.allBuildings().filter {
            when (kind) {
                "garbage" -> it.b.service == "landfill" || it.b.service == "incinerator"
                "fire" -> it.b.service == "fire_station"
                "ambulance" -> it.b.service == "clinic" || it.b.service == "hospital"
                "hearse" -> it.b.service == "cemetery" || it.b.service == "crematorium"
                "police" -> it.b.service == "police"
                else -> false
            }
        }
        if (stations.isEmpty()) {
            cars.removeAll { it.kind == kind }
            return
        }
        val targets = World.allBuildings().filter { !it.b.isService }
        if (targets.isEmpty()) return
        repeat(min(n, 4)) {
            val from = stations[Random.nextInt(stations.size)]
            val to = when (kind) {
                "garbage" -> targets.maxByOrNull { it.b.garbage } ?: return
                "police" -> targets.maxByOrNull { it.b.crime } ?: return
                else -> targets[Random.nextInt(targets.size)]
            }
            dispatchTo(kind, to.x, to.y, from.x, from.y)
        }
    }

    private fun dispatchTo(kind: String, dx: Int, dy: Int, sx: Int? = null, sy: Int? = null) {
        if (cars.size > 18) return
        val start = if (sx != null && sy != null) sx to sy else {
            val st = World.allBuildings().firstOrNull {
                when (kind) {
                    "garbage" -> it.b.service == "landfill" || it.b.service == "incinerator"
                    "fire" -> it.b.service == "fire_station"
                    "ambulance" -> it.b.service == "clinic" || it.b.service == "hospital"
                    "hearse" -> it.b.service == "cemetery" || it.b.service == "crematorium"
                    else -> it.b.service == "police"
                }
            } ?: return
            st.x to st.y
        }
        cars.add(EmergencyCar(kind, start.first, start.second, dx, dy, speed = 2.0f + Random.nextFloat()))
    }

    fun screenCell(c: EmergencyCar): Pair<Float, Float> {
        if (c.path.isEmpty()) return (c.x - 0.5f) to (c.y - 0.5f)
        val i = c.pathI.coerceIn(0, c.path.lastIndex)
        val a = c.path[i]
        val b = c.path.getOrElse(i + 1) { a }
        val ax = Citizens.unpackX(a).toFloat()
        val ay = Citizens.unpackY(a).toFloat()
        val bx = Citizens.unpackX(b).toFloat()
        val by = Citizens.unpackY(b).toFloat()
        return (ax + (bx - ax) * c.prog - 0.5f) to (ay + (by - ay) * c.prog - 0.5f)
    }

    fun hitTest(wx: Float, wy: Float): EmergencyCar? {
        var best: EmergencyCar? = null
        var bestD = 0.7f
        for (c in cars) {
            val (sx, sy) = screenCell(c)
            val d = abs(sx - wx) + abs(sy - wy)
            if (d < bestD) {
                bestD = d
                best = c
            }
        }
        return best
    }

    fun label(kind: String): String = when (kind) {
        "fire" -> "消防车"
        "ambulance" -> "救护车"
        "police" -> "警车"
        "garbage" -> "垃圾车"
        "hearse" -> "灵车"
        else -> "公务车"
    }

    fun job(kind: String): String = when (kind) {
        "fire" -> "消防队员"
        "ambulance" -> "急救司机"
        "police" -> "巡警"
        "garbage" -> "清运司机"
        "hearse" -> "殡仪司机"
        else -> "市政司机"
    }
}
