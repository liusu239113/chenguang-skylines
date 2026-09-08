package com.chenguang.skylines

import android.content.Context
import com.chenguang.skylines.world.Building
import com.chenguang.skylines.world.Growth
import com.chenguang.skylines.world.World
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ============================================================================
// SaveManager — 本地存档（JSON 序列化到应用私有目录）
//   3 个槽位；存档内容：世界格子改动 + 经济状态 + 城市名 + 种子 + 难度
// ============================================================================

data class SlotMeta(
    val exists: Boolean,
    val cityName: String,
    val population: Int,
    val funds: Double,
    val dateLabel: String,
    val lastSaved: Long
)

object SaveManager {

    private lateinit var dir: File

    fun init(context: Context) {
        dir = context.filesDir
    }

    private fun path(slot: Int): File = File(dir, "save_slot_$slot.json")

    fun hasSlot(slot: Int): Boolean = path(slot).exists()

    fun delete(slot: Int) {
        path(slot).delete()
    }

    fun meta(slot: Int): SlotMeta {
        val f = path(slot)
        if (!f.exists()) return SlotMeta(false, "", 0, 0.0, "", 0L)
        return try {
            val j = JSONObject(f.readText())
            SlotMeta(
                true,
                j.optString("cityName", "晨光市"),
                j.optInt("population", 0),
                j.optDouble("funds", 0.0),
                j.optString("dateLabel", ""),
                f.lastModified()
            )
        } catch (t: Throwable) {
            SlotMeta(false, "", 0, 0.0, "", 0L)
        }
    }

    fun save(slot: Int) {
        val s = GameData.current ?: return
        val w = World.current ?: return
        val json = JSONObject()
        json.put("seed", GameData.seed)
        json.put("difficulty", GameData.difficultyKey)
        json.put("sandbox", GameData.sandbox)
        json.put("speedIdx", GameData.speedIdx)
        json.put("simTime", Growth.simTime)
        json.put("cityName", s.cityName)
        json.put("population", s.population.toInt())
        json.put("funds", s.funds)
        json.put("dateLabel", GameData.dateLabel())
        json.put("year", s.year)
        json.put("month", s.month)
        json.put("day", s.day)
        json.put("happiness", s.happiness)
        json.put("totalIncome", s.totalIncome)
        json.put("totalSpent", s.totalSpent)
        json.put("pollution", s.pollution)
        json.put("lastLevel", s.lastLevel)
        json.put("taxRes", s.taxRes)
        json.put("taxCom", s.taxCom)
        json.put("taxInd", s.taxInd)
        json.put("loanDebt", s.loanDebt)
        json.put("loanCooldown", s.loanCooldown)

        val ap = JSONArray()
        for (p in s.activePolicies) {
            ap.put(JSONObject().put("id", p.id).put("daysLeft", p.daysLeft))
        }
        json.put("activePolicies", ap)

        val cd = JSONObject()
        for ((id, v) in s.policyCooldowns) cd.put(id, v)
        json.put("policyCooldowns", cd)

        val ach = JSONArray()
        for (a in s.achievements) ach.put(a)
        json.put("achievements", ach)

        val evs = JSONArray()
        for (ev in s.activeEvents) {
            evs.put(
                JSONObject().put("id", ev.id).put("name", ev.name)
                    .put("daysLeft", ev.daysLeft).put("happy", ev.happy).put("incomeMul", ev.incomeMul)
            )
        }
        json.put("activeEvents", evs)

        val news = JSONArray()
        for (n in s.news) {
            news.put(
                JSONObject()
                    .put("month", n.month).put("headline", n.headline)
                    .put("body", n.body).put("tag", n.tag)
            )
        }
        json.put("news", news)

        // 世界格子改动
        val tiles = JSONArray()
        for (y in 1..w.rows) {
            for (x in 1..w.cols) {
                val t = w.grid[y - 1][x - 1]
                if (t.zone == "none" && t.road == null && t.building == null) continue
                val o = JSONObject().put("x", x).put("y", y).put("zone", t.zone)
                t.road?.let { o.put("road", it) }
                t.building?.let { b ->
                    val bo = JSONObject().put("level", b.level).put("born", b.born)
                        .put("residents", b.residents)
                    b.zone?.let { bo.put("zone", it) }
                    b.service?.let {
                        bo.put("service", it).put("ax", b.ax).put("ay", b.ay)
                            .put("w", b.w).put("h", b.h)
                    }
                    o.put("building", bo)
                }
                tiles.put(o)
            }
        }
        json.put("tiles", tiles)

        path(slot).writeText(json.toString())
    }

    fun load(slot: Int): Boolean {
        val f = path(slot)
        if (!f.exists()) return false
        val json = try {
            JSONObject(f.readText())
        } catch (t: Throwable) {
            return false
        }

        GameData.seed = json.optInt("seed", 20260408)
        GameData.difficultyKey = json.optString("difficulty", "normal")
        GameData.sandbox = json.optBoolean("sandbox", false)

        // 重建地形 + 空状态
        GameData.init(GameData.seed)

        // 覆盖格子
        val tiles = json.optJSONArray("tiles")
        if (tiles != null) {
            for (i in 0 until tiles.length()) {
                val o = tiles.getJSONObject(i)
                val x = o.getInt("x")
                val y = o.getInt("y")
                val t = World.tile(x, y) ?: continue
                t.zone = o.optString("zone", "none")
                if (o.has("road")) t.road = o.optString("road") else t.road = null
                t.building = null
                if (o.has("building")) {
                    val bo = o.getJSONObject("building")
                    val b = Building()
                    b.level = bo.optInt("level", 1)
                    b.born = bo.optDouble("born", 0.0)
                    b.residents = bo.optInt("residents", 0)
                    if (bo.has("zone")) b.zone = bo.optString("zone")
                    if (bo.has("service")) {
                        b.service = bo.optString("service")
                        b.ax = bo.optInt("ax", x)
                        b.ay = bo.optInt("ay", y)
                        b.w = bo.optInt("w", 1)
                        b.h = bo.optInt("h", 1)
                    }
                    t.building = b
                }
            }
        }

        // 恢复状态
        val s = GameData.current!!
        s.cityName = json.optString("cityName", Config.World.city)
        s.year = json.optInt("year", 2026)
        s.month = json.optInt("month", 4)
        s.day = json.optInt("day", 1)
        s.funds = json.optDouble("funds", Config.RESOURCES.fundsStart)
        s.population = json.optDouble("population", 0.0)
        s.happiness = json.optDouble("happiness", Config.RESOURCES.happinessStart)
        s.totalIncome = json.optDouble("totalIncome", 0.0)
        s.totalSpent = json.optDouble("totalSpent", 0.0)
        s.pollution = json.optInt("pollution", 0)
        s.lastLevel = json.optInt("lastLevel", 0)
        s.taxRes = json.optInt("taxRes", Config.TAX.default)
        s.taxCom = json.optInt("taxCom", Config.TAX.default)
        s.taxInd = json.optInt("taxInd", Config.TAX.default)
        s.loanDebt = json.optDouble("loanDebt", 0.0)
        s.loanCooldown = json.optInt("loanCooldown", 0)

        s.activePolicies.clear()
        val ap = json.optJSONArray("activePolicies")
        if (ap != null) for (i in 0 until ap.length()) {
            val p = ap.getJSONObject(i)
            s.activePolicies.add(PolicyActive(p.getString("id"), p.getInt("daysLeft")))
        }

        s.policyCooldowns.clear()
        val cd = json.optJSONObject("policyCooldowns")
        if (cd != null) {
            val keys = cd.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                s.policyCooldowns[k] = cd.getInt(k)
            }
        }

        s.achievements.clear()
        val ach = json.optJSONArray("achievements")
        if (ach != null) for (i in 0 until ach.length()) s.achievements.add(ach.getString(i))

        s.activeEvents.clear()
        val evs = json.optJSONArray("activeEvents")
        if (evs != null) for (i in 0 until evs.length()) {
            val o = evs.getJSONObject(i)
            s.activeEvents.add(
                ActiveEvent(
                    o.optString("id"), o.optString("name"), o.optInt("daysLeft"),
                    o.optDouble("happy", 0.0), o.optDouble("incomeMul", 1.0)
                )
            )
        }

        s.news.clear()
        val news = json.optJSONArray("news")
        if (news != null) for (i in 0 until news.length()) {
            val n = news.getJSONObject(i)
            s.news.add(
                NewsItem(
                    n.optString("month"), n.optString("headline"),
                    n.optString("body"), n.optString("tag", "快讯")
                )
            )
        }

        GameData.speedIdx = json.optInt("speedIdx", 2)
        Growth.simTime = json.optDouble("simTime", 0.0)
        World.current?._pop = s.population.toInt()
        return true
    }
}
