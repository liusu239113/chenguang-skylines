package com.dshx.game.she

import com.dshx.game.she.world.Building
import com.dshx.game.she.world.World
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * 城市二进制编解码。
 *
 * 为什么不用 JSON：JSON 每格要 80+ 字节，压完还有 20 字节；
 * 二进制 + 位置增量每格只要 5~8 字节，一张二维码能装下绝大多数城市。
 *
 * 只存「种子 + 与种子生成结果不同的格子」：
 * 地形靠种子重建，所以空城市只有几十字节。
 *
 * 编码结果直接产出 SaveManager.load 能读的 JSON，复用已跑通的读档流程。
 */
object CityCodec {

    private const val MAGIC = 0xC5
    private const val VERSION = 3

    private val TERRAINS = listOf("grass", "plain", "forest", "hill", "water")
    private val ZONES = listOf("none", "residential", "commercial", "industrial", "office")
    private val ROADS = listOf("", "dirt", "local", "avenue", "highway", "overpass", "metro", "rail")
    private val SPECS = listOf("", "tourism", "retail", "factory", "campus")
    private val SERVICES = listOf(
        "park", "plaza", "wind_farm", "solar_plant", "coal_plant", "nuclear_plant",
        "water_tower", "pump_station", "landfill", "incinerator", "waste_plant",
        "clinic", "hospital", "school", "middle_school", "university",
        "fire_station", "police", "bus_stop", "metro", "rail_station", "harbor",
        "airport", "sewage", "cemetery", "crematorium", "prison",
        "stock_exchange", "tv_tower", "stadium"
    )

    // ------------------------------------------------------------------
    // 编码
    // ------------------------------------------------------------------

    fun encode(): ByteArray? {
        val s = GameData.current ?: return null
        val w = World.current ?: return null
        val out = ByteArrayOutputStream()

        out.write(MAGIC)
        out.write(VERSION)
        writeInt(out, GameData.seed)
        writeStr(out, s.cityName)
        writeStr(out, s.mayorName)
        writeInt(out, s.funds.toInt())
        out.write(s.year and 0xFF); out.write((s.year shr 8) and 0xFF)
        out.write(s.month); out.write(s.day)
        out.write(s.rankLevel)
        out.write(w.unlockCx); out.write(w.unlockCy); out.write(w.unlockR)

        // 收集差异格（与种子生成结果不同，或玩家加了东西）
        val base = World.generate(GameData.seed)
        val keys = ArrayList<Int>()
        val metas = ArrayList<ByteArray>()
        for (y in 1..w.rows) {
            for (x in 1..w.cols) {
                val t = w.grid[y - 1][x - 1]
                val b = base.grid[y - 1][x - 1]
                val terrainChanged = t.terrain != b.terrain
                val hasContent = t.zone != "none" || t.road != null || t.building != null ||
                    t.pipe || t.cable || t.sewer || t.metro || t.rail || t.spec.isNotEmpty()
                if (!terrainChanged && !hasContent) continue
                keys.add((y - 1) * w.cols + (x - 1))
                metas.add(packTile(t, terrainChanged))
            }
        }
        writeInt(out, keys.size)
        // 位置用增量编码：相邻差值通常 < 128，1 字节即可
        var prev = 0
        for (i in keys.indices) {
            val d = keys[i] - prev
            prev = keys[i]
            if (d < 0x80) {
                out.write(d)
            } else if (d < 0x4000) {
                out.write(0x80 or (d and 0x7F))
                out.write((d shr 7) and 0x7F)
            } else {
                out.write(0x80 or (d and 0x7F))
                out.write(0x80 or ((d shr 7) and 0x7F))
                out.write((d shr 14) and 0xFF)
            }
            out.write(metas[i])
        }
        return out.toByteArray()
    }

    /**
     * 单格打包（极限压缩）。
     *
     * 旧格式每格 7~13 字节，装不下大城。新格式用「类型字节 + 可选扩展」：
     *   首字节高 2 位是类型标签，低 6 位塞常用信息。
     *   绝大多数格子（只有路 / 只有分区）2 字节搞定，带建筑的 4~6 字节。
     */
    private fun packTile(t: com.dshx.game.she.world.Tile, terrainChanged: Boolean): ByteArray {
        val mo = ByteArrayOutputStream()
        val zoneIdx = ZONES.indexOf(t.zone).coerceAtLeast(0)
        val roadIdx = ROADS.indexOf(t.road ?: "").coerceAtLeast(0)
        val specIdx = SPECS.indexOf(t.spec).coerceAtLeast(0)
        val terrainIdx = if (terrainChanged) TERRAINS.indexOf(t.terrain).coerceAtLeast(0) else 0
        var flags = 0
        if (t.pipe) flags = flags or 1
        if (t.cable) flags = flags or 2
        if (t.sewer) flags = flags or 4
        if (t.metro) flags = flags or 8
        if (t.rail) flags = flags or 16
        if (t.bridge) flags = flags or 32

        // 类型字节：bit7=有建筑, bit6=有地形变化, bit5=有管线, 低5位=分区
        var tag = 0
        val bd = t.building
        if (bd != null) tag = tag or 0x80
        if (terrainChanged) tag = tag or 0x40
        if (flags != 0) tag = tag or 0x20
        tag = tag or (zoneIdx and 0x1F)
        mo.write(tag)
        // 道路 + 专精合一个字节（路 4 位 + 专精 4 位）
        mo.write(((roadIdx and 0x0F) shl 4) or (specIdx and 0x0F))
        if (terrainChanged) mo.write(terrainIdx)
        if (flags != 0) mo.write(flags)
        if (bd != null) {
            if (bd.isService) {
                mo.write(1)
                mo.write(SERVICES.indexOf(bd.service).coerceAtLeast(0))
                mo.write(bd.ax); mo.write(bd.ay)
                mo.write(bd.w); mo.write(bd.h)
            } else {
                mo.write(0)
                mo.write(ZONES.indexOf(bd.zone ?: "none").coerceAtLeast(0))
                var lv = bd.level and 0x03
                if (bd.abandoned) lv = lv or 0x04
                if (bd.w > 1 || bd.h > 1) lv = lv or 0x08
                mo.write(lv)
                if (bd.w > 1 || bd.h > 1) {
                    mo.write(bd.ax); mo.write(bd.ay)
                    mo.write(bd.w); mo.write(bd.h)
                }
            }
        }
        return mo.toByteArray()
    }

    // ------------------------------------------------------------------
    // 解码 → 产出 SaveManager.load 能读的存档 JSON
    // ------------------------------------------------------------------

    fun decodeToSave(bytes: ByteArray): JSONObject? {
        return try {
            var p = 0
            fun rb(): Int = bytes[p++].toInt() and 0xFF
            fun ri(): Int {
                val v = (bytes[p].toInt() and 0xFF) or
                    ((bytes[p + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[p + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[p + 3].toInt() and 0xFF) shl 24)
                p += 4
                return v
            }
            fun rs(): String {
                val n = rb()
                val s = String(bytes, p, n, Charsets.UTF_8)
                p += n
                return s
            }
            if (rb() != MAGIC) return null
            if (rb() != VERSION) return null
            val seed = ri()
            val cityName = rs()
            val mayorName = rs()
            val funds = ri()
            val year = rb() or (rb() shl 8)
            val month = rb()
            val day = rb()
            val rank = rb()
            val ucx = rb(); val ucy = rb(); val ur = rb()
            val cols = Config.MAP.cols

            val tiles = JSONArray()
            val n = ri()
            var prevK = 0
            val shared = HashMap<Int, JSONObject>()
            for (i in 0 until n) {
                val b0 = rb()
                val d = if (b0 < 0x80) {
                    b0
                } else {
                    val b1 = rb()
                    if (b1 < 0x80) {
                        (b0 and 0x7F) or (b1 shl 7)
                    } else {
                        val b2 = rb()
                        (b0 and 0x7F) or ((b1 and 0x7F) shl 7) or (b2 shl 14)
                    }
                }
                val k = prevK + d
                prevK = k
                val x = k % cols + 1
                val y = k / cols + 1
                // 类型字节
                val tag = rb()
                val hasBuilding = (tag and 0x80) != 0
                val hasTerrain = (tag and 0x40) != 0
                val hasFlags = (tag and 0x20) != 0
                val zoneIdx = tag and 0x1F
                // 道路 + 专精
                val rs2 = rb()
                val roadIdx = (rs2 shr 4) and 0x0F
                val specIdx = rs2 and 0x0F
                val terrainIdx = if (hasTerrain) rb() else 0
                val flags = if (hasFlags) rb() else 0
                val o = JSONObject().put("x", x).put("y", y)
                if (hasTerrain && terrainIdx in TERRAINS.indices) o.put("terrain", TERRAINS[terrainIdx])
                if (zoneIdx in ZONES.indices) o.put("zone", ZONES[zoneIdx])
                if (roadIdx in ROADS.indices && ROADS[roadIdx].isNotEmpty()) o.put("road", ROADS[roadIdx])
                if (specIdx in SPECS.indices && SPECS[specIdx].isNotEmpty()) o.put("spec", SPECS[specIdx])
                if (flags and 1 != 0) o.put("pipe", true)
                if (flags and 2 != 0) o.put("cable", true)
                if (flags and 4 != 0) o.put("sewer", true)
                if (flags and 8 != 0) o.put("metro", true)
                if (flags and 16 != 0) o.put("rail", true)
                if (flags and 32 != 0) o.put("bridge", true)
                if (hasBuilding) {
                    val isSvc = rb() == 1
                    val bo = JSONObject()
                    if (isSvc) {
                        val si = rb()
                        bo.put("service", SERVICES.getOrNull(si) ?: "park")
                        val ax = rb(); val ay = rb(); val bw = rb(); val bh = rb()
                        bo.put("ax", ax).put("ay", ay).put("w", bw).put("h", bh)
                    } else {
                        val zi = rb()
                        bo.put("zone", ZONES.getOrNull(zi) ?: "residential")
                        val lv = rb()
                        bo.put("level", (lv and 0x03).coerceAtLeast(1))
                        bo.put("abandoned", (lv and 0x04) != 0)
                        if ((lv and 0x08) != 0) {
                            val ax = rb(); val ay = rb(); val bw = rb(); val bh = rb()
                            bo.put("ax", ax).put("ay", ay).put("w", bw).put("h", bh)
                        }
                    }
                    // 多格建筑：非锚点格复用同一份对象，避免读档后变多栋
                    val bw2 = bo.optInt("w", 1)
                    val bh2 = bo.optInt("h", 1)
                    if (bw2 > 1 || bh2 > 1) {
                        val key = bo.optInt("ax") * 1000 + bo.optInt("ay")
                        val exist = shared[key]
                        if (exist != null) {
                            o.put("building", exist)
                            tiles.put(o)
                            continue
                        }
                        shared[key] = bo
                    }
                    o.put("building", bo)
                }
                tiles.put(o)
            }

            val save = JSONObject()
            save.put("seed", seed)
            save.put("difficulty", GameData.difficultyKey)
            save.put("cityName", cityName)
            save.put("mayorName", mayorName)
            save.put("funds", funds.toDouble())
            save.put("year", year)
            save.put("month", month)
            save.put("day", day)
            save.put("rankLevel", rank)
            save.put("unlockCx", ucx)
            save.put("unlockCy", ucy)
            save.put("unlockR", ur)
            save.put("tiles", tiles)
            save.put("dateLabel", "")
            save
        } catch (t: Throwable) {
            null
        }
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun writeStr(out: ByteArrayOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        val n = minOf(b.size, 60)
        out.write(n)
        out.write(b, 0, n)
    }
}
