package com.dshx.game.she.world

import com.dshx.game.she.Config
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * 地下管网 + 区划政策（对照拆解文档 6.2 / 第九章）
 * 旧存档可能仍带水管/电缆格子；供电供水已改为设施半径覆盖。区划用格子笔刷。
 */
class District(
    val id: Int,
    var name: String,
    var policy: String = ""          // "" | no_smoke | ev | old_town | industry_plan | high_density
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

    val DISTRICT_POLICIES = listOf(
        Triple("", "无专属政策", "该区沿用全城法令"),
        Triple("no_smoke", "禁烟令", "健康↑ 满意度略降"),
        Triple("ev", "电动车鼓励", "污染↓ 维护费↑"),
        Triple("old_town", "旧城区", "禁止升级，吸引游客"),
        Triple("industry_plan", "工业空间规划", "工业产出↑ 污染↑"),
        Triple("high_density", "高密住宅鼓励", "住宅升级↑ 拥堵↑")
    )

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

    fun ensureDistrict(): District {
        if (districts.isEmpty()) {
            districts.add(District(nextDistrictId++, "一区", ""))
        }
        if (activeDistrict == 0) activeDistrict = districts.first().id
        return districts.firstOrNull { it.id == activeDistrict } ?: districts.first()
    }

    fun addDistrict(name: String): District {
        val d = District(nextDistrictId++, name.ifBlank { "新区" + nextDistrictId }, "")
        districts.add(d)
        activeDistrict = d.id
        return d
    }

    fun paintDistrict(x: Int, y: Int, id: Int = activeDistrict): Boolean {
        val t = World.tile(x, y) ?: return false
        if (t.terrain == "water") return false
        t.district = id
        return true
    }

    fun districtAt(x: Int, y: Int): District? {
        val id = World.tile(x, y)?.district ?: 0
        if (id == 0) return null
        return districts.firstOrNull { it.id == id }
    }

    fun policyAt(x: Int, y: Int): String = districtAt(x, y)?.policy ?: ""

    fun policyName(key: String): String =
        DISTRICT_POLICIES.firstOrNull { it.first == key }?.second ?: "无"

    fun districtMul(key: String, x: Int, y: Int): Double {
        return when (policyAt(x, y)) {
            "no_smoke" -> if (key == "health") 1.12 else if (key == "happy") 0.97 else 1.0
            "ev" -> if (key == "pollution") 0.82 else if (key == "upkeep") 1.10 else 1.0
            "old_town" -> if (key == "upgrade") 0.0 else if (key == "tourism") 1.15 else 1.0
            "industry_plan" -> if (key == "industry") 1.20 else if (key == "pollution") 1.30 else 1.0
            "high_density" -> if (key == "upgrade") 1.80 else if (key == "traffic") 1.20 else if (key == "demandR") 1.18 else 1.0
            else -> 1.0
        }
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
