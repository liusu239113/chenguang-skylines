package com.dshx.game.she.world

import com.dshx.game.she.Config
import com.dshx.game.she.GameData
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 市民 Agent（对照拆解文档第十二章）
 * 家 → 通勤 → 工作 → 购物 → 回家 / 娱乐。最多采样 80 人，避免卡顿。
 */
class Citizen {
    var homeX: Int = 1
    var homeY: Int = 1
    var workX: Int = 1
    var workY: Int = 1
    var shopX: Int = 1
    var shopY: Int = 1
    var education: Int = 0          // 0 无 / 1 小学 / 2 中学 / 3 大学
    var state: String = "home"      // home / to_work / work / to_shop / shop / to_home / leisure
    var path: MutableList<Int> = mutableListOf()  // packed (y-1)*cols+(x-1)
    var pathI: Int = 0
    var prog: Float = 0f
    var speed: Float = 1.6f
    var commuteLen: Int = 0
    var color: Int = 0
}

object Citizens {

    val agents: MutableList<Citizen> = mutableListOf()
    var avgCommute: Double = 0.35       // 0 短 / 1 极长
    var employed: Int = 0
    var unemployed: Int = 0
    var shopping: Int = 0

    private const val MAX_AGENTS = 80
    private val DX = intArrayOf(1, -1, 0, 0)
    private val DY = intArrayOf(0, 0, 1, -1)

    fun reset() {
        agents.clear()
        avgCommute = 0.35
        employed = 0
        unemployed = 0
        shopping = 0
    }

    fun cols(): Int = World.current?.cols ?: Config.MAP.cols

    fun pack(x: Int, y: Int): Int = (y - 1) * cols() + (x - 1)

    fun unpackX(p: Int): Int = p % cols() + 1

    fun unpackY(p: Int): Int = p / cols() + 1

    fun nearestRoad(x: Int, y: Int): Pair<Int, Int>? {
        if (World.isRoad(x, y)) return x to y
        for (r in 1..6) {
            for (dy in -r..r) {
                val dx = r - abs(dy)
                if (World.isRoad(x + dx, y + dy)) return (x + dx) to (y + dy)
                if (dx != 0 && World.isRoad(x - dx, y + dy)) return (x - dx) to (y + dy)
            }
        }
        return null
    }

    fun bfsRoad(sx: Int, sy: Int, tx: Int, ty: Int): MutableList<Int> {
        World.current ?: return mutableListOf()
        if (sx == tx && sy == ty) return mutableListOf(pack(sx, sy))
        val start = pack(sx, sy)
        val goal = pack(tx, ty)
        val q = ArrayDeque<Int>()
        val came = HashMap<Int, Int>(256)
        q.add(start)
        came[start] = -1
        var guard = 0
        var found = false
        while (q.isNotEmpty() && guard++ < 2800) {
            val cur = q.removeFirst()
            if (cur == goal) {
                found = true
                break
            }
            val cx = unpackX(cur)
            val cy = unpackY(cur)
            for (k in 0 until 4) {
                val nx = cx + DX[k]
                val ny = cy + DY[k]
                if (!World.isRoad(nx, ny)) continue
                val np = pack(nx, ny)
                if (came.containsKey(np)) continue
                came[np] = cur
                q.addLast(np)
            }
        }
        if (!found) return mutableListOf(start, goal)
        val out = ArrayList<Int>(32)
        var c = goal
        while (c != -1) {
            out.add(c)
            c = came[c] ?: break
            if (out.size > 240) break
        }
        out.reverse()
        return out
    }

    fun setPath(c: Citizen, tx: Int, ty: Int) {
        val from = nearestRoad(c.xTile(), c.yTile()) ?: (c.homeX to c.homeY)
        val to = nearestRoad(tx, ty) ?: (tx to ty)
        c.path = bfsRoad(from.first, from.second, to.first, to.second)
        c.pathI = 0
        c.prog = 0f
        c.commuteLen = max(1, c.path.size)
    }

    private fun Citizen.xTile(): Int {
        if (path.isEmpty()) return homeX
        val i = pathI.coerceIn(0, path.lastIndex)
        return unpackX(path[i])
    }

    private fun Citizen.yTile(): Int {
        if (path.isEmpty()) return homeY
        val i = pathI.coerceIn(0, path.lastIndex)
        return unpackY(path[i])
    }

    fun screenCell(c: Citizen): Pair<Float, Float> {
        if (c.path.isEmpty()) return (c.homeX - 0.5f) to (c.homeY - 0.5f)
        val i = c.pathI.coerceIn(0, c.path.lastIndex)
        val a = c.path[i]
        val b = c.path.getOrElse(i + 1) { a }
        val ax = unpackX(a).toFloat()
        val ay = unpackY(a).toFloat()
        val bx = unpackX(b).toFloat()
        val by = unpackY(b).toFloat()
        return (ax + (bx - ax) * c.prog - 0.5f) to (ay + (by - ay) * c.prog - 0.5f)
    }

    /** 按住宅/岗位重建采样市民（每日或载档后） */
    fun rebuild() {
        agents.clear()
        val homes = World.allBuildings().filter { !it.b.isService && it.b.zone == "residential" && !it.b.abandoned && it.b.residents > 0 }
        val jobs = World.allBuildings().filter {
            !it.b.isService && !it.b.abandoned && (it.b.zone == "commercial" || it.b.zone == "industrial" || it.b.zone == "office")
        }
        val shops = World.allBuildings().filter { !it.b.isService && it.b.zone == "commercial" && !it.b.abandoned }
        if (homes.isEmpty()) {
            employed = 0
            unemployed = 0
            return
        }
        val jobSlots = jobs.toMutableList()
        var jobCursor = 0
        var spawned = 0
        for (h in homes) {
            if (spawned >= MAX_AGENTS) break
            val n = max(1, min(4, max(1, h.b.residents / 4)))
            val edu = educationAt(h.x, h.y)
            repeat(n) {
                if (spawned >= MAX_AGENTS) return@repeat
                val c = Citizen()
                c.homeX = h.x
                c.homeY = h.y
                c.education = edu
                c.speed = 1.3f + Random.nextFloat() * 1.4f
                c.color = Random.nextInt(6)
                c.state = "home"
                if (jobSlots.isNotEmpty()) {
                    val officeJobs = jobSlots.filter { it.b.zone == "office" }
                    val otherJobs = jobSlots.filter { it.b.zone != "office" }
                    val pick = if (edu >= 2 && officeJobs.isNotEmpty()) {
                        officeJobs[jobCursor % officeJobs.size]
                    } else if (otherJobs.isNotEmpty()) {
                        otherJobs[jobCursor % otherJobs.size]
                    } else {
                        null
                    }
                    jobCursor++
                    if (pick != null) {
                        c.workX = pick.x
                        c.workY = pick.y
                    } else {
                        c.workX = h.x
                        c.workY = h.y
                    }
                } else {
                    c.workX = h.x
                    c.workY = h.y
                }
                if (shops.isNotEmpty()) {
                    val sh = shops[Random.nextInt(shops.size)]
                    c.shopX = sh.x
                    c.shopY = sh.y
                } else {
                    c.shopX = h.x
                    c.shopY = h.y
                }
                agents.add(c)
                spawned++
            }
        }
        employed = agents.count { it.workX != it.homeX || it.workY != it.homeY }
        unemployed = max(0, agents.size - employed)
        refreshCommuteStat()
    }

    private fun educationAt(x: Int, y: Int): Int {
        var lv = 0
        for (e in World.allBuildings()) {
            if (!e.b.isService) continue
            val id = e.b.service ?: continue
            val d = abs(e.x - x) + abs(e.y - y)
            if (id == "school" && d <= 8) lv = max(lv, 1)
            if (id == "middle_school" && d <= 10) lv = max(lv, 2)
            if (id == "university" && d <= 14) lv = max(lv, 3)
        }
        return lv
    }

    private fun refreshCommuteStat() {
        if (agents.isEmpty()) {
            avgCommute = 0.2
            return
        }
        var sum = 0.0
        for (c in agents) sum += min(40, c.commuteLen).toDouble()
        avgCommute = max(0.05, min(1.0, (sum / agents.size) / 28.0))
    }

    private fun desiredState(tod: Float): String = when {
        tod < 0.08f -> "home"
        tod < 0.18f -> "to_work"
        tod < 0.50f -> "work"
        tod < 0.58f -> "to_shop"
        tod < 0.66f -> "shop"
        tod < 0.78f -> "to_home"
        tod < 0.90f -> "leisure"
        else -> "home"
    }

    fun tick(dt: Float) {
        if (agents.isEmpty()) {
            rebuild()
            if (agents.isEmpty()) return
        }
        val want = desiredState(GameData.timeOfDay)
        shopping = 0
        val rain = if (GameData.weather == 1) 0.75f else 1f
        val busBoost = 1f + Transit.coverageBoost().toFloat() * 0.55f
        for (c in agents) {
            if (c.state != want) {
                c.state = want
                when (want) {
                    "to_work" -> setPath(c, c.workX, c.workY)
                    "to_shop" -> setPath(c, c.shopX, c.shopY)
                    "to_home", "leisure", "home" -> setPath(c, c.homeX, c.homeY)
                    "work" -> {
                        c.path.clear()
                        c.prog = 0f
                    }
                    "shop" -> shopping++
                }
            }
            if (c.state == "to_work" || c.state == "to_shop" || c.state == "to_home" || c.state == "leisure") {
                advance(c, dt * rain * busBoost)
            }
            if (c.state == "shop") shopping++
        }
        refreshCommuteStat()
    }

    private fun advance(c: Citizen, dt: Float) {
        if (c.path.size <= 1) return
        c.prog += c.speed * dt * 0.55f
        while (c.prog >= 1f && c.pathI < c.path.lastIndex) {
            c.prog -= 1f
            c.pathI++
        }
        if (c.pathI >= c.path.lastIndex) {
            c.prog = 0f
        }
    }
}
