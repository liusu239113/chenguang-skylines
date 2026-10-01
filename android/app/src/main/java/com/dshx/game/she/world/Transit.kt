package com.dshx.game.she.world

import com.dshx.game.she.GameData
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

/**
 * 车马线路（对照拆解文档 7.1）
 * 点击车马站按顺序连线 → 确认成环/往返 → 车马车沿路网跑。
 */
class BusStopRef(val x: Int, val y: Int)

class BusLine(
    val id: Int,
    var name: String,
    val stops: MutableList<BusStopRef> = mutableListOf(),
    var buses: Int = 2
)

class BusVehicle(
    var lineId: Int,
    var path: MutableList<Int> = mutableListOf(),
    var pathI: Int = 0,
    var prog: Float = 0f,
    var speed: Float = 1.8f,
    var forward: Boolean = true,
    var dir: Int = -1
)

object Transit {

    val lines: MutableList<BusLine> = mutableListOf()
    val draft: MutableList<BusStopRef> = mutableListOf()
    val vehicles: MutableList<BusVehicle> = mutableListOf()
    var nextId: Int = 1
    var ridership: Int = 0

    fun reset() {
        lines.clear()
        draft.clear()
        vehicles.clear()
        nextId = 1
        ridership = 0
    }

    fun isStopCell(x: Int, y: Int): Boolean {
        val b = World.tile(x, y)?.building ?: return false
        val id = b.service ?: return false
        return id == "bus_stop" || id == "metro"
    }

    fun stopAnchor(x: Int, y: Int): BusStopRef? {
        val b = World.tile(x, y)?.building ?: return null
        val id = b.service ?: return null
        if (id != "bus_stop" && id != "metro") return null
        return BusStopRef(b.ax, b.ay)
    }

    fun addDraftStop(x: Int, y: Int): Pair<Boolean, String?> {
        val a = stopAnchor(x, y) ?: return false to "请点在车马站或地道口上"
        if (draft.any { it.x == a.x && it.y == a.y }) return false to "该站已在草稿线路里"
        draft.add(a)
        return true to ("已加入站点 " + draft.size)
    }

    fun clearDraft() {
        draft.clear()
    }

    /** 撤销草稿里最后一个站点（点错站不用整条重来） */
    fun undoDraftStop(): Pair<Boolean, String?> {
        if (draft.isEmpty()) return false to "草稿里还没有站点"
        draft.removeAt(draft.size - 1)
        return true to ("已移除上一个站点，剩 " + draft.size + " 站")
    }

    /** 拆除一条已开通线路：车一并撤掉，站点设施保留 */
    fun removeLine(id: Int): Pair<Boolean, String?> {
        val idx = lines.indexOfFirst { it.id == id }
        if (idx < 0) return false to "线路不存在"
        val line = lines.removeAt(idx)
        vehicles.removeAll { it.lineId == id }
        GameData.pushNews("车马停运", line.name + " 已拆除，车马已撤回。", "交通")
        return true to (line.name + " 已拆除")
    }

    /**
     * 车马站/地道口被推平时调用：把该站从所有线路里摘掉。
     * 掉到不足 2 站的线路自动停运（返回线路名，交给调用方提示）。
     */
    fun onStopRemoved(x: Int, y: Int): String? {
        draft.removeAll { it.x == x && it.y == y }
        var deadLine: String? = null
        val it = lines.iterator()
        while (it.hasNext()) {
            val line = it.next()
            if (!line.stops.removeAll { s -> s.x == x && s.y == y }) continue
            if (line.stops.size < 2) {
                it.remove()
                vehicles.removeAll { v -> v.lineId == line.id }
                deadLine = line.name
            } else {
                spawnBuses(line)
            }
        }
        return deadLine
    }

    /** 线路列表（面板展示用）：名字 + 站数 */
    fun summary(): List<Triple<Int, String, Int>> =
        lines.map { Triple(it.id, it.name, it.stops.size) }

    fun confirmDraft(): Pair<Boolean, String?> {
        if (draft.size < 2) return false to "至少需要 2 个站点"
        if (lines.size >= 6) return false to "线路已满（最多 6 条）"
        val id = nextId++
        val line = BusLine(id, "线路$id", draft.map { BusStopRef(it.x, it.y) }.toMutableList(), 2)
        lines.add(line)
        spawnBuses(line)
        draft.clear()
        GameData.pushNews("车马开通", line.name + " 投入运转，缓解街市拥挤。", "交通")
        return true to (line.name + " 已开通")
    }

    private fun spawnBuses(line: BusLine) {
        vehicles.removeAll { it.lineId == line.id }
        val path = buildLinePath(line)
        if (path.size < 2) return
        repeat(line.buses) { i ->
            val v = BusVehicle(line.id, path.toMutableList(), 0, i * 0.2f, 1.6f + i * 0.15f, true)
            v.pathI = (i * (path.size / max(1, line.buses))) % path.size
            v.dir = headingFromPath(v).let { if (it >= 0) it else World.roadHeadingAt(
                Citizens.unpackX(path[v.pathI.coerceIn(0, path.lastIndex)]),
                Citizens.unpackY(path[v.pathI.coerceIn(0, path.lastIndex)])
            ) }
            vehicles.add(v)
        }
    }

    private fun buildLinePath(line: BusLine): List<Int> {
        val out = mutableListOf<Int>()
        for (i in 0 until line.stops.size - 1) {
            val a = line.stops[i]
            val b = line.stops[i + 1]
            val ra = Citizens.nearestRoad(a.x, a.y) ?: (a.x to a.y)
            val rb = Citizens.nearestRoad(b.x, b.y) ?: (b.x to b.y)
            val seg = Citizens.bfsRoad(ra.first, ra.second, rb.first, rb.second)
            if (out.isNotEmpty() && seg.isNotEmpty() && out.last() == seg.first()) {
                out.addAll(seg.drop(1))
            } else {
                out.addAll(seg)
            }
        }
        return out
    }

    fun tick(dt: Float) {
        if (vehicles.isEmpty() && lines.isNotEmpty()) {
            for (l in lines) spawnBuses(l)
        }
        var moving = 0
        for (v in vehicles) {
            if (v.path.size < 2) continue
            v.prog += v.speed * dt * 0.5f
            while (v.prog >= 1f) {
                v.prog -= 1f
                if (v.forward) {
                    if (v.pathI >= v.path.lastIndex) {
                        v.forward = false
                    } else {
                        v.pathI++
                    }
                } else {
                    if (v.pathI <= 0) {
                        v.forward = true
                    } else {
                        v.pathI--
                    }
                }
                val h = headingFromPath(v)
                if (h >= 0) v.dir = h
            }
            moving++
        }
        ridership = moving * 18
    }

    /** 0..1，线路越多、站点越密，通勤越轻松 */
    fun coverageBoost(): Double {
        if (lines.isEmpty()) return 0.0
        val stops = lines.sumOf { it.stops.size }
        val free = if (GameData.policyMul("trafficMul") < 0.8) 1.35 else 1.0
        return max(0.0, min(0.7, (stops * 0.08 + lines.size * 0.12) * free))
    }

    fun vehicleCell(v: BusVehicle): Pair<Float, Float> {
        if (v.path.isEmpty()) return 0f to 0f
        val i = v.pathI.coerceIn(0, v.path.lastIndex)
        val a = v.path[i]
        val b = if (v.forward) v.path.getOrElse(i + 1) { a } else v.path.getOrElse(i - 1) { a }
        val ax = Citizens.unpackX(a).toFloat()
        val ay = Citizens.unpackY(a).toFloat()
        val bx = Citizens.unpackX(b).toFloat()
        val by = Citizens.unpackY(b).toFloat()
        return (ax + (bx - ax) * v.prog - 0.5f) to (ay + (by - ay) * v.prog - 0.5f)
    }

    fun heading(v: BusVehicle): Int {
        val fromPath = headingFromPath(v)
        if (fromPath >= 0) {
            v.dir = fromPath
            return fromPath
        }
        val ax = if (v.path.isNotEmpty()) {
            Citizens.unpackX(v.path[v.pathI.coerceIn(0, v.path.lastIndex)])
        } else 0
        val ay = if (v.path.isNotEmpty()) {
            Citizens.unpackY(v.path[v.pathI.coerceIn(0, v.path.lastIndex)])
        } else 0
        val along = World.roadHeadingAt(ax, ay, v.dir)
        v.dir = along
        return along
    }

    private fun headingFromPath(v: BusVehicle): Int {
        if (v.path.size < 2) return -1
        val i = v.pathI.coerceIn(0, v.path.lastIndex)
        val a = v.path[i]
        val atEnd = (v.forward && i >= v.path.lastIndex) || (!v.forward && i <= 0)
        val b = if (v.forward) {
            if (i < v.path.lastIndex) v.path[i + 1] else v.path[i - 1]
        } else {
            if (i > 0) v.path[i - 1] else v.path[i + 1]
        }
        var dx = Citizens.unpackX(b) - Citizens.unpackX(a)
        var dy = Citizens.unpackY(b) - Citizens.unpackY(a)
        if (atEnd) {
            dx = -dx
            dy = -dy
        }
        if (dx == 0 && dy == 0) return -1
        return when {
            kotlin.math.abs(dx) >= kotlin.math.abs(dy) -> if (dx >= 0) 0 else 2
            else -> if (dy >= 0) 1 else 3
        }
    }

    fun toJson(): JSONArray {
        val arr = JSONArray()
        for (l in lines) {
            val o = JSONObject().put("id", l.id).put("name", l.name).put("buses", l.buses)
            val st = JSONArray()
            for (s in l.stops) st.put(JSONObject().put("x", s.x).put("y", s.y))
            o.put("stops", st)
            arr.put(o)
        }
        return arr
    }

    fun fromJson(arr: JSONArray?) {
        reset()
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val line = BusLine(o.optInt("id", i + 1), o.optString("name", "线路"), mutableListOf(), o.optInt("buses", 2))
            val st = o.optJSONArray("stops")
            if (st != null) for (k in 0 until st.length()) {
                val s = st.getJSONObject(k)
                line.stops.add(BusStopRef(s.getInt("x"), s.getInt("y")))
            }
            lines.add(line)
            nextId = max(nextId, line.id + 1)
            spawnBuses(line)
        }
    }
}
