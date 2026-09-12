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
        if (t.terrain == "water") return false to "水域无法铺管"
        return true to null
    }

    fun setPipe(x: Int, y: Int, on: Boolean = true): Boolean {
        val t = World.tile(x, y) ?: return false
        if (t.terrain == "water") return false
        t.pipe = on
        return true
    }

    fun setCable(x: Int, y: Int, on: Boolean = true): Boolean {
        val t = World.tile(x, y) ?: return false
        if (t.terrain == "water") return false
        t.cable = on
        return true
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
