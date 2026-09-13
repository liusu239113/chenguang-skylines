package com.dshx.game.she.world

import com.dshx.game.she.Config
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * 地下管网。旧档可能仍带水管/电缆/区划字段，读档保留但不玩区划政策。
 */
class District(
    val id: Int,
    var name: String,
    var policy: String = ""
)

object Networks {

    val districts: MutableList<District> = mutableListOf()
    var nextDistrictId: Int = 1
    var activeDistrict: Int = 0
    var pipeCount: Int = 0
    var cableCount: Int = 0
    var sewerCount: Int = 0
    var metroCount: Int = 0
    var railCount: Int = 0

    fun reset() {
        districts.clear()
        nextDistrictId = 1
        activeDistrict = 0
        pipeCount = 0
        cableCount = 0
        sewerCount = 0
        metroCount = 0
        railCount = 0
        val w = World.current
        if (w != null) {
            for (y in 0 until w.rows) for (x in 0 until w.cols) {
                w.grid[y][x].pipe = false
                w.grid[y][x].cable = false
                w.grid[y][x].pipeMask = 0
                w.grid[y][x].cableMask = 0
                w.grid[y][x].sewer = false
                w.grid[y][x].metro = false
                w.grid[y][x].rail = false
                w.grid[y][x].district = 0
                w.grid[y][x].spec = ""
                w.grid[y][x].groundPol = 0
                w.grid[y][x].waterPol = 0
                w.grid[y][x].onFire = false
            }
        }
    }

    fun recount() {
        val w = World.current ?: return
        var p = 0
        var c = 0
        var s = 0
        var m = 0
        var r = 0
        for (y in 0 until w.rows) for (x in 0 until w.cols) {
            if (w.grid[y][x].pipe) p++
            if (w.grid[y][x].cable) c++
            if (w.grid[y][x].sewer) s++
            if (w.grid[y][x].metro) m++
            if (w.grid[y][x].rail) r++
        }
        pipeCount = p
        cableCount = c
        sewerCount = s
        metroCount = m
        railCount = r
    }

    fun canPipe(x: Int, y: Int): Pair<Boolean, String?> {
        val t = World.tile(x, y) ?: return false to "越界"
        if (t.terrain == "water") return false to "水域不能铺水管（先垫地或绕开）"
        if (t.terrain == "hill") return false to "山地不能铺水管（先推平或绕开）"
        return true to null
    }

    fun setPipe(x: Int, y: Int, on: Boolean = true, fromX: Int = -1, fromY: Int = -1): Boolean {
        val t = World.tile(x, y) ?: return false
        if (t.terrain == "water" || t.terrain == "hill") return false
        t.pipe = on
        if (on) linkDir(x, y, fromX, fromY, false)
        invalidateGrid()
        return true
    }

    fun canCable(x: Int, y: Int): Pair<Boolean, String?> {
        val t = World.tile(x, y) ?: return false to "越界"
        if (t.terrain == "water") return false to "水域不能铺电缆（先垫地或绕开）"
        if (t.terrain == "hill") return false to "山地不能铺电缆（先推平或绕开）"
        return true to null
    }

    fun setCable(x: Int, y: Int, on: Boolean = true, fromX: Int = -1, fromY: Int = -1): Boolean {
        val t = World.tile(x, y) ?: return false
        if (t.terrain == "water" || t.terrain == "hill") return false
        t.cable = on
        if (on) linkDir(x, y, fromX, fromY, true)
        invalidateGrid()
        return true
    }

    /**
     * 按拖拽方向记录管线开口：
     * 从 (fromX,fromY) 拖到 (x,y) 时，本格开口朝来向，上一格开口朝去向，
     * 于是只有首尾相接的管线才连成一条；并排的两根互不串通。
     */
    private fun linkDir(x: Int, y: Int, fromX: Int, fromY: Int, power: Boolean) {
        if (fromX < 0 || fromY < 0) return
        val dx = x - fromX
        val dy = y - fromY
        if (dx != 0 && dy != 0) return
        val to = dirBit(dx, dy)          // 上一格 → 当前格 的朝向
        if (to == 0) return
        val back = opposite(to)          // 当前格 → 上一格 的朝向
        val cur = World.tile(x, y) ?: return
        if (power) cur.cableMask = cur.cableMask or back else cur.pipeMask = cur.pipeMask or back
        val prev = World.tile(fromX, fromY) ?: return
        if (power) prev.cableMask = prev.cableMask or to else prev.pipeMask = prev.pipeMask or to
    }

    fun setSewer(x: Int, y: Int, on: Boolean = true): Boolean {
        val t = World.tile(x, y) ?: return false
        t.sewer = on
        return true
    }

    fun setMetro(x: Int, y: Int, on: Boolean = true): Boolean {
        val t = World.tile(x, y) ?: return false
        if (t.terrain == "water") return false
        t.metro = on
        return true
    }

    fun setRail(x: Int, y: Int, on: Boolean = true): Boolean {
        val t = World.tile(x, y) ?: return false
        if (t.terrain == "water") return false
        t.rail = on
        return true
    }

    /** 水厂落成后，沿邻路自动铺一段干管，玩家再往外拖 */
    fun seedPipesAround(ax: Int, ay: Int, bw: Int, bh: Int, radius: Int = 2) {
        for (y in (ay - radius)..(ay + bh - 1 + radius)) {
            for (x in (ax - radius)..(ax + bw - 1 + radius)) {
                val t = World.tile(x, y) ?: continue
                if (t.terrain == "water") continue
                if (t.road != null || (x in ax until ax + bw && y in ay until ay + bh)) {
                    t.pipe = true
                }
            }
        }
        recount()
    }

    fun seedCablesAround(ax: Int, ay: Int, bw: Int, bh: Int, radius: Int = 2) {
        for (y in (ay - radius)..(ay + bh - 1 + radius)) {
            for (x in (ax - radius)..(ax + bw - 1 + radius)) {
                val t = World.tile(x, y) ?: continue
                if (t.terrain == "water") continue
                if (t.road != null || (x in ax until ax + bw && y in ay until ay + bh)) {
                    t.cable = true
                }
            }
        }
        recount()
    }

    fun seedSewersAround(ax: Int, ay: Int, bw: Int, bh: Int, radius: Int = 2) {
        for (y in (ay - radius)..(ay + bh - 1 + radius)) {
            for (x in (ax - radius)..(ax + bw - 1 + radius)) {
                val t = World.tile(x, y) ?: continue
                if (t.road != null || (x in ax until ax + bw && y in ay until ay + bh)) {
                    t.sewer = true
                }
            }
        }
        recount()
    }

    fun seedMetroAround(ax: Int, ay: Int, bw: Int, bh: Int, radius: Int = 1) {
        for (y in (ay - radius)..(ay + bh - 1 + radius)) {
            for (x in (ax - radius)..(ax + bw - 1 + radius)) {
                val t = World.tile(x, y) ?: continue
                if (t.terrain == "water") continue
                if (t.road != null || (x in ax until ax + bw && y in ay until ay + bh)) {
                    t.metro = true
                }
            }
        }
        recount()
    }

    fun seedRailAround(ax: Int, ay: Int, bw: Int, bh: Int, radius: Int = 1) {
        for (y in (ay - radius)..(ay + bh - 1 + radius)) {
            for (x in (ax - radius)..(ax + bw - 1 + radius)) {
                val t = World.tile(x, y) ?: continue
                if (t.terrain == "water") continue
                if (t.road != null || (x in ax until ax + bw && y in ay until ay + bh)) {
                    t.rail = true
                }
            }
        }
        recount()
    }

    // -----------------------------------------------------------------------
    // 管网连通性：电厂/水厂只负责产能，靠「道路预埋管线 + 地下电缆/水管」输送到建筑。
    // 道路天生带电带水；地下电缆/水管用于把偏远厂站接进管网。
    // -----------------------------------------------------------------------
    private val powerTiles = HashSet<Int>()
    private val waterTiles = HashSet<Int>()
    private var gridDirty = true

    private val DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))

    fun invalidateGrid() {
        gridDirty = true
        capCache = -1
    }

    private fun idx(x: Int, y: Int, cols: Int) = (y - 1) * cols + (x - 1)

    /** 方向位：N=1 E=2 S=4 W=8，与 Tile 的 pipeMask/cableMask 一致 */
    private fun dirBit(dx: Int, dy: Int): Int = when {
        dx == 1 -> 2
        dx == -1 -> 8
        dy == 1 -> 4
        dy == -1 -> 1
        else -> 0
    }

    private fun opposite(bit: Int): Int = when (bit) {
        1 -> 4
        4 -> 1
        2 -> 8
        8 -> 2
        else -> 0
    }

    private fun conduitPower(x: Int, y: Int): Boolean {
        val t = World.tile(x, y) ?: return false
        return t.cable || t.road != null
    }

    private fun conduitWater(x: Int, y: Int): Boolean {
        val t = World.tile(x, y) ?: return false
        return t.pipe || t.road != null
    }

    /**
     * 该格在 bit 方向是否开口。
     * 道路自带预埋管线，四向全通；自己铺的管缆按 mask 判定：
     * mask=0（单点/旧档）视为四向全开；否则只有记录的开口方向可通行，
     * 因此两根并排的竖电缆不会互相串通，必须首尾对齐才连得上。
     */
    private fun openAt(x: Int, y: Int, bit: Int, power: Boolean): Boolean {
        val t = World.tile(x, y) ?: return false
        if (t.road != null) return true
        val has = if (power) t.cable else t.pipe
        if (!has) return false
        val m = if (power) t.cableMask else t.pipeMask
        return m == 0 || (m and bit) != 0
    }

    private fun flood(
        cols: Int,
        seeds: ArrayDeque<Int>,
        out: MutableSet<Int>,
        conduit: (Int, Int) -> Boolean,
        power: Boolean
    ) {
        while (seeds.isNotEmpty()) {
            val k = seeds.removeFirst()
            if (!out.add(k)) continue
            val x = k % cols + 1
            val y = k / cols + 1
            for (d in DIRS) {
                val nx = x + d[0]
                val ny = y + d[1]
                if (!World.inBounds(nx, ny)) continue
                if (!conduit(nx, ny)) continue
                val bit = dirBit(d[0], d[1])
                // 两端开口必须对上，才算接通
                if (!openAt(x, y, bit, power)) continue
                if (!openAt(nx, ny, opposite(bit), power)) continue
                val nk = idx(nx, ny, cols)
                if (nk !in out) seeds.add(nk)
            }
        }
    }

    private fun ensureGrid() {
        if (!gridDirty) return
        gridDirty = false
        powerTiles.clear()
        waterTiles.clear()
        val w = World.current ?: return
        val cols = w.cols
        val powerSeeds = ArrayDeque<Int>()
        val waterSeeds = ArrayDeque<Int>()
        for (e in World.allBuildings()) {
            if (!e.b.isService) continue
            val cfg = World.serviceConfig(e.b.service) ?: continue
            val isPower = cfg.powerCap > 0
            val isWater = cfg.waterCap > 0
            if (!isPower && !isWater) continue
            // 厂站必须靠玩家自己铺的电缆/水管接进管网：光挨着马路不算接通，
            // 免得"还没拉线就全城来电"。接进来之后借着马路把水电送到全城。
            for (dy in -1..e.b.h) {
                for (dx in -1..e.b.w) {
                    val x = e.x + dx
                    val y = e.y + dy
                    if (!World.inBounds(x, y)) continue
                    val t = World.tile(x, y) ?: continue
                    if (isPower && t.cable) powerSeeds.add(idx(x, y, cols))
                    if (isWater && t.pipe) waterSeeds.add(idx(x, y, cols))
                }
            }
        }
        flood(cols, powerSeeds, powerTiles, { x, y -> conduitPower(x, y) }, true)
        flood(cols, waterSeeds, waterTiles, { x, y -> conduitWater(x, y) }, false)
    }

    private fun linked(x: Int, y: Int, set: Set<Int>): Boolean {
        val w = World.current ?: return false
        val cols = w.cols
        if (idx(x, y, cols) in set) return true
        for (d in DIRS) {
            val nx = x + d[0]
            val ny = y + d[1]
            if (!World.inBounds(nx, ny)) continue
            if (idx(nx, ny, cols) in set) return true
        }
        return false
    }

    /** 建筑是否接入电网：自身或四邻有已连通的道路/电缆 */
    fun isPowered(x: Int, y: Int): Boolean {
        ensureGrid()
        return linked(x, y, powerTiles)
    }

    /** 建筑是否接入水网 */
    fun isWatered(x: Int, y: Int): Boolean {
        ensureGrid()
        return linked(x, y, waterTiles)
    }

    /**
     * 地铁连通：从每个地铁站出发，沿地下隧道四向扩散，
     * 返回「已接入隧道网的地铁站数量」。没接隧道的地铁站不算运力。
     */
    fun connectedMetroStations(): Int {
        val w = World.current ?: return 0
        val seen = HashSet<Int>()
        var stations = 0
        val tunnels = HashSet<Int>()
        for (e in World.allBuildings()) {
            if (e.b.service != "metro") continue
            var hasAdj = false
            for (dy in -1..e.b.h) {
                for (dx in -1..e.b.w) {
                    val x = e.x + dx
                    val y = e.y + dy
                    if (!World.inBounds(x, y)) continue
                    if (World.tile(x, y)?.metro == true) {
                        hasAdj = true
                        tunnels.add((y - 1) * w.cols + (x - 1))
                    }
                }
            }
            if (hasAdj) stations++
        }
        // 隧道本身也要能连成片（从任一隧道出发扩散）
        val q = ArrayDeque<Int>()
        for (k in tunnels) q.add(k)
        while (q.isNotEmpty()) {
            val k = q.removeFirst()
            if (!seen.add(k)) continue
            val x = k % w.cols + 1
            val y = k / w.cols + 1
            for (d in DIRS) {
                val nx = x + d[0]
                val ny = y + d[1]
                if (!World.inBounds(nx, ny)) continue
                if (World.tile(nx, ny)?.metro != true) continue
                val nk = (ny - 1) * w.cols + (nx - 1)
                if (nk !in seen) q.add(nk)
            }
        }
        // 只保留真正连到同一张隧道网的站点
        var ok = 0
        for (e in World.allBuildings()) {
            if (e.b.service != "metro") continue
            var linked = false
            for (dy in -1..e.b.h) {
                for (dx in -1..e.b.w) {
                    val x = e.x + dx
                    val y = e.y + dy
                    if (!World.inBounds(x, y)) continue
                    if (World.tile(x, y)?.metro == true && ((y - 1) * w.cols + (x - 1)) in seen) {
                        linked = true
                    }
                }
            }
            if (linked) ok++
        }
        return ok
    }

    /** 某座厂站是否已经接线（自己铺的电缆/水管挨着它，或管网已经铺到它脚下） */
    fun plantWired(ax: Int, ay: Int, power: Boolean, bw: Int = 1, bh: Int = 1): Boolean {
        ensureGrid()
        val w = World.current ?: return false
        val cols = w.cols
        val set = if (power) powerTiles else waterTiles
        for (dy in -1..bh) {
            for (dx in -1..bw) {
                val x = ax + dx
                val y = ay + dy
                if (!World.inBounds(x, y)) continue
                if ((y - 1) * cols + (x - 1) in set) return true
            }
        }
        return false
    }

    /**
     * 真正接进管网的产能合计：厂站周围没有自己铺的电缆/水管就等于没接线，
     * 这台机组不算产能（会直接体现为电力/供水不足）。
     */
    fun connectedCapacity(power: Boolean): Int {
        ensureGrid()
        val w = World.current ?: return 0
        val cols = w.cols
        val set = if (power) powerTiles else waterTiles
        var cap = 0
        for (e in World.allBuildings()) {
            if (!e.b.isService) continue
            val cfg = World.serviceConfig(e.b.service) ?: continue
            val c = if (power) cfg.powerCap else cfg.waterCap
            if (c <= 0) continue
            var on = false
            for (dy in -1..e.b.h) {
                if (on) break
                for (dx in -1..e.b.w) {
                    val x = e.x + dx
                    val y = e.y + dy
                    if (!World.inBounds(x, y)) continue
                    if ((y - 1) * cols + (x - 1) in set) {
                        on = true
                        break
                    }
                }
            }
            if (on) cap += c
        }
        return cap
    }

    /** 是否临路：垃圾车/灵车沿路收运的前提 */
    fun isRoadLinked(x: Int, y: Int): Boolean {
        if (World.current == null) return false
        if (World.tile(x, y)?.road != null) return true
        for (d in DIRS) {
            val nx = x + d[0]
            val ny = y + d[1]
            if (!World.inBounds(nx, ny)) continue
            if (World.tile(nx, ny)?.road != null) return true
        }
        return false
    }

    private var capCache = -1
    private var capGarbage = 0
    private var capDeath = 0

    private fun ensureCaps() {
        if (capCache >= 0) return
        var g = 0
        var d = 0
        for (e in World.allBuildings()) {
            val cfg = World.serviceConfig(e.b.service) ?: continue
            g += cfg.garbageCap
            d += cfg.deathCap
        }
        capGarbage = g
        capDeath = d
        capCache = 1
    }

    fun invalidateCaps() {
        capCache = -1
    }

    fun garbageCapacity(): Int {
        ensureCaps()
        return capGarbage
    }

    fun deathCapacity(): Int {
        ensureCaps()
        return capDeath
    }

    fun specCount(id: String): Int {
        val w = World.current ?: return 0
        var n = 0
        for (y in 0 until w.rows) for (x in 0 until w.cols) {
            if (w.grid[y][x].spec == id) n++
        }
        return n
    }

    fun specTotals(): Triple<Double, Double, Double> {
        var income = 0.0
        var happy = 0.0
        var edu = 0.0
        val w = World.current ?: return Triple(0.0, 0.0, 0.0)
        for (y in 0 until w.rows) for (x in 0 until w.cols) {
            val def = Config.specOf(w.grid[y][x].spec) ?: continue
            income += def.income
            happy += def.happy
            edu += def.edu
        }
        return Triple(income, happy, edu)
    }

    fun specPollution(): Double {
        val w = World.current ?: return 0.0
        var p = 0.0
        for (y in 0 until w.rows) for (x in 0 until w.cols) {
            p += Config.specOf(w.grid[y][x].spec)?.pollution ?: 0.0
        }
        return p
    }

    fun toJson(): JSONObject {
        recount()
        val o = JSONObject()
        val ds = JSONArray()
        for (d in districts) {
            ds.put(JSONObject().put("id", d.id).put("name", d.name).put("policy", d.policy))
        }
        o.put("districts", ds)
        o.put("activeDistrict", activeDistrict)
        o.put("nextDistrictId", nextDistrictId)
        return o
    }

    fun fromJson(o: JSONObject?) {
        districts.clear()
        nextDistrictId = 1
        activeDistrict = 0
        if (o == null) return
        val ds = o.optJSONArray("districts")
        if (ds != null) for (i in 0 until ds.length()) {
            val d = ds.getJSONObject(i)
            districts.add(District(d.optInt("id", i + 1), d.optString("name", "区"), d.optString("policy", "")))
            nextDistrictId = max(nextDistrictId, d.optInt("id", i + 1) + 1)
        }
        activeDistrict = o.optInt("activeDistrict", districts.firstOrNull()?.id ?: 0)
        nextDistrictId = max(nextDistrictId, o.optInt("nextDistrictId", nextDistrictId))
        recount()
    }
}
