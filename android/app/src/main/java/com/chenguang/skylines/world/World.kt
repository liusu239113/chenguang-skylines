package com.chenguang.skylines.world

import com.chenguang.skylines.Config
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ============================================================================
// World — 世界模型（格子数据 + 规则），与 scripts/world/World.lua 1:1 对应
//   建筑分两类：
//     grown   = { zone="residential", level=1..3, born=模拟时刻 }  分区自动生长
//     service = { service="park", ax, ay, w, h }                    玩家放置
//   坐标语义与 Lua 一致：1-based（x: 1..cols, y: 1..rows）
// ============================================================================

class Tile {
    var terrain: String = "grass"
    var zone: String = "none"
    var road: String? = null          // "local" | "avenue"
    var building: Building? = null
}

class Building {
    // grown
    var zone: String? = null          // residential / commercial / industrial
    var level: Int = 1
    var born: Double = 0.0
    var residents: Int = 0            // 住宅当前入住人数
    // service
    var service: String? = null
    var ax: Int = 0
    var ay: Int = 0
    var w: Int = 1
    var h: Int = 1

    val isService: Boolean get() = service != null
}

class RoadLine(
    val name: String,
    val kind: String,
    val segX: IntArray,
    val segY: IntArray,
    val dir: String,                  // "v" | "h"
    val labelX: Int,
    val labelY: Int
)

class WorldStats {
    var resCap: Int = 0
    var comCap: Int = 0
    var indCap: Int = 0
    var resCount: Int = 0
    var comCount: Int = 0
    var indCount: Int = 0
    var pollution: Int = 0
    var roadCount: Int = 0
    var serviceCount: Int = 0
}

class World {
    val cols: Int = Config.MAP.cols
    val rows: Int = Config.MAP.rows
    // 内部 0-based 存储，外部一律 1-based 访问
    val grid: Array<Array<Tile>> = Array(rows) { Array(cols) { Tile() } }
    val elev: Array<IntArray> = Array(rows) { IntArray(cols) }
    val labels: MutableList<Label> = mutableListOf()   // POI 标签（开局为空）
    val roadLines: MutableList<RoadLine> = mutableListOf()
    var spawnX: Int = 1
    var spawnY: Int = 1

    class Label(val x: Float, val y: Float, val text: String, val kind: String)

    companion object {
        var current: World? = null

        private val STREET_PRE = arrayOf(
            "晨光", "望江", "振兴", "青年", "碧湖", "桂香", "和平", "解放",
            "文昌", "梧桐", "朝阳", "临江", "锦绣", "长虹", "育才", "银杏"
        )
        private val STREET_SUF = arrayOf("大道", "大街", "路", "街", "南路", "北路", "东路", "西路")

        private fun pickName(seed: Int, i: Int): String {
            val pre = STREET_PRE[i % STREET_PRE.size]
            val suf = STREET_SUF[(i * 7 + 3) % STREET_SUF.size]
            return pre + suf
        }

        // -------------------------------------------------------------------
        // 确定性值噪声（与 Lua 版本逐位一致，Long 溢出环绕）
        // -------------------------------------------------------------------
        private fun makeNoise(seed: Int): Noise = Noise(seed)

        private class Noise(private val s: Int) {
            private fun hash(ix: Int, iy: Int): Double {
                var n: Long = ix.toLong() * 374761393L +
                    iy.toLong() * 668265263L +
                    (s.toLong() * 1442695040888963407L) % 2147483647L
                n = (n xor (n ushr 13)) * 1274126177L
                n = n xor (n ushr 16)
                return (n and 0x7fffffffL).toDouble() / 0x7fffffffL
            }

            private fun smooth(t: Double): Double = t * t * (3 - 2 * t)

            fun noise(x: Double, y: Double): Double {
                val x0 = floor(x); val y0 = floor(y)
                val tx = smooth(x - x0); val ty = smooth(y - y0)
                val v00 = hash(x0.toInt(), y0.toInt())
                val v10 = hash(x0.toInt() + 1, y0.toInt())
                val v01 = hash(x0.toInt(), y0.toInt() + 1)
                val v11 = hash(x0.toInt() + 1, y0.toInt() + 1)
                val a = v00 + (v10 - v00) * tx
                val b = v01 + (v11 - v01) * tx
                return a + (b - a) * ty
            }

            fun fbm(x: Double, y: Double, oct: Int = 3): Double {
                var amp = 1.0; var freq = 1.0; var sum = 0.0; var norm = 0.0
                for (i in 1..oct) {
                    sum += noise(x * freq, y * freq) * amp
                    norm += amp
                    amp *= 0.5
                    freq *= 2
                }
                return sum / norm
            }
        }

        // -------------------------------------------------------------------
        // 程序化生成底图：地形 + 河湖 + 大道骨架
        // -------------------------------------------------------------------
        fun generate(seed: Int = 20260408): World {
            val w = World()
            val nz = makeNoise(seed)

            for (y in 1..w.rows) {
                for (x in 1..w.cols) {
                    val h = nz.fbm(x * 0.09, y * 0.09, 4)
                    w.elev[y - 1][x - 1] = floor(20 + h * 260).toInt()
                }
            }

            // 河流（靠地图右边缘，不穿市中心）
            val riverX = w.cols - 2
            for (y in 1..w.rows) {
                val rx = riverX + floor(nz.noise(y * 0.12, 5.5) * 6 - 3).toInt()
                val width = 2 + floor(nz.noise(y * 0.08, 9.1) * 2).toInt()
                for (dx in -width..width) {
                    val x = rx + dx
                    if (x >= 1 && x <= w.cols) {
                        w.grid[y - 1][x - 1].terrain = "water"
                        w.elev[y - 1][x - 1] = 8
                    }
                }
            }

            // 湖泊（左上角角落，不占用市中心建设用地）
            val lakeX = 5
            val lakeY = 4
            val lakeR = 4
            for (y in max(1, lakeY - lakeR)..min(w.rows, lakeY + lakeR)) {
                for (x in max(1, lakeX - lakeR)..min(w.cols, lakeX + lakeR)) {
                    if ((x - lakeX) * (x - lakeX) + (y - lakeY) * (y - lakeY) <= lakeR * lakeR) {
                        w.grid[y - 1][x - 1].terrain = "water"
                        w.elev[y - 1][x - 1] = 6
                    }
                }
            }

            // 林地 / 丘陵 / 平原
            for (y in 1..w.rows) {
                for (x in 1..w.cols) {
                    val t = w.grid[y - 1][x - 1]
                    if (t.terrain != "water") {
                        val f = nz.fbm(x * 0.15 + 40, y * 0.15 + 40, 3)
                        if (f > 0.66) {
                            t.terrain = "forest"
                        } else if (w.elev[y - 1][x - 1] > 200) {
                            t.terrain = "hill"
                        } else if (f < 0.34) {
                            t.terrain = "plain"
                        }
                    }
                }
            }

            // 大道骨架（3 纵 3 横，免费，跨河直铺）
            fun setRoad(x: Int, y: Int, kind: String) {
                val t = w.grid[y - 1][x - 1]
                t.road = kind
                t.zone = "none"
            }

            val colAve = intArrayOf(
                floor(w.cols * 0.22).toInt(),
                floor(w.cols * 0.5).toInt(),
                floor(w.cols * 0.78).toInt()
            )
            val rowAve = intArrayOf(
                floor(w.rows * 0.20).toInt(),
                floor(w.rows * 0.55).toInt(),
                floor(w.rows * 0.82).toInt()
            )

            for (i in colAve.indices) {
                val cx = colAve[i]
                val xs = IntArray(w.rows)
                val ys = IntArray(w.rows)
                for (y in 1..w.rows) {
                    setRoad(cx, y, "avenue")
                    xs[y - 1] = cx
                    ys[y - 1] = y
                }
                w.roadLines.add(
                    RoadLine(
                        name = pickName(seed, 10 + (i + 1) * 3),
                        kind = "avenue",
                        segX = xs,
                        segY = ys,
                        dir = "v",
                        labelX = 0,
                        labelY = floor(w.rows * (0.15 + (i + 1) * 0.25)).toInt()
                    )
                )
            }
            for (i in rowAve.indices) {
                val ry = rowAve[i]
                val xs = IntArray(w.cols)
                val ys = IntArray(w.cols)
                for (x in 1..w.cols) {
                    setRoad(x, ry, "avenue")
                    xs[x - 1] = x
                    ys[x - 1] = ry
                }
                w.roadLines.add(
                    RoadLine(
                        name = pickName(seed, 40 + (i + 1) * 5),
                        kind = "avenue",
                        segX = xs,
                        segY = ys,
                        dir = "h",
                        labelX = floor(w.cols * (0.2 + (i + 1) * 0.28)).toInt(),
                        labelY = 0
                    )
                )
            }

            w.spawnX = colAve[0]
            w.spawnY = rowAve[0]
            current = w
            return w
        }

        // -------------------------------------------------------------------
        // 查询
        // -------------------------------------------------------------------
        fun inBounds(x: Int, y: Int): Boolean {
            val w = current ?: return false
            return x >= 1 && x <= w.cols && y >= 1 && y <= w.rows
        }

        fun tile(x: Int, y: Int): Tile? {
            val w = current ?: return null
            if (x < 1 || x > w.cols || y < 1 || y > w.rows) return null
            return w.grid[y - 1][x - 1]
        }

        fun elevation(x: Int, y: Int): Int {
            val w = current ?: return 0
            if (x < 1 || x > w.cols || y < 1 || y > w.rows) return 0
            return w.elev[y - 1][x - 1]
        }

        fun isRoad(x: Int, y: Int): Boolean = tile(x, y)?.road != null

        fun terrainName(x: Int, y: Int): String {
            val t = tile(x, y) ?: return "-"
            return Config.Terrain.from(t.terrain).label
        }

        fun zoneName(x: Int, y: Int): String {
            val t = tile(x, y) ?: return "-"
            t.road?.let { return Config.ROAD[it]?.name ?: it }
            t.building?.let { b ->
                if (b.isService) {
                    return serviceConfig(b.service!!)?.name ?: "设施"
                }
                val g = Config.GROWN[b.zone]
                return (g?.name ?: "建筑") + " L" + b.level
            }
            return Config.ZONE[t.zone]?.name ?: "未规划"
        }

        fun serviceConfig(id: String?): Config.ServiceDef? =
            Config.SERVICES.firstOrNull { it.id == id }

        // -------------------------------------------------------------------
        // 修路
        // -------------------------------------------------------------------
        /** 返回 true 可修；false + msg（无 msg 表示静默跳过） */
        fun canRoad(x: Int, y: Int): Pair<Boolean, String?> {
            val t = tile(x, y) ?: return false to "越界"
            if (t.terrain == "water") return false to "不能铺在水上"
            if (t.building != null) return false to "先拆除这里的建筑"
            if (t.road != null) return false to null       // 静默跳过
            return true to null
        }

        fun setRoad(x: Int, y: Int, kind: String): Boolean {
            val t = tile(x, y) ?: return false
            if (t.terrain == "water" || t.building != null) return false
            t.road = kind
            t.zone = "none"
            return true
        }

        // -------------------------------------------------------------------
        // 分区
        // -------------------------------------------------------------------
        fun canZone(x: Int, y: Int): Pair<Boolean, String?> {
            val t = tile(x, y) ?: return false to "越界"
            if (t.terrain == "water") return false to "水域无法划区"
            if (t.road != null) return false to null
            if (t.building != null) return false to null
            return true to null
        }

        fun setZone(x: Int, y: Int, zone: String): Boolean {
            val t = tile(x, y) ?: return false
            if (t.terrain == "water" || t.road != null || t.building != null) return false
            t.zone = zone
            return true
        }

        // -------------------------------------------------------------------
        // 服务设施
        // -------------------------------------------------------------------
        fun canPlaceService(id: String, x: Int, y: Int): Pair<Boolean, String?> {
            val s = serviceConfig(id) ?: return false to "未知设施"
            for (yy in y until y + s.sizeH) {
                for (xx in x until x + s.sizeW) {
                    val t = tile(xx, yy) ?: return false to "超出地图"
                    if (t.terrain == "water") return false to "不能建在水上"
                    if (t.road != null || t.building != null) return false to "该位置被占用"
                }
            }
            // 基础设施（电/水/垃圾等）必须邻路，否则无法接入路网
            if (s.category != Config.ServiceCat.AMENITY) {
                var adjacent = false
                outer@ for (yy in y - 1..y + s.sizeH) {
                    for (xx in x - 1..x + s.sizeW) {
                        if (isRoad(xx, yy)) {
                            adjacent = true
                            break@outer
                        }
                    }
                }
                if (!adjacent) return false to "需建在道路旁（接入电网/管网）"
            }
            return true to null
        }

        fun placeService(id: String, x: Int, y: Int): Boolean {
            val s = serviceConfig(id) ?: return false
            val w = current ?: return false
            for (yy in y until y + s.sizeH) {
                for (xx in x until x + s.sizeW) {
                    val b = Building()
                    b.service = id
                    b.ax = x; b.ay = y
                    b.w = s.sizeW; b.h = s.sizeH
                    w.grid[yy - 1][xx - 1].building = b
                }
            }
            return true
        }

        // -------------------------------------------------------------------
        // 成长建筑
        // -------------------------------------------------------------------
        fun growBuilding(zone: String, x: Int, y: Int, level: Int, born: Double): Boolean {
            val t = tile(x, y) ?: return false
            if (t.building != null || t.road != null || t.terrain == "water") return false
            val b = Building()
            b.zone = zone
            b.level = level
            b.born = born
            t.building = b
            return true
        }

        fun upgradeBuilding(x: Int, y: Int): Boolean {
            val t = tile(x, y) ?: return false
            val b = t.building ?: return false
            if (b.isService) return false
            val lvDef = Config.GROWN[b.zone]?.levels ?: return false
            if (b.level >= lvDef.size) return false
            b.level += 1
            return true
        }

        /** 全部建筑锚点：grown 逐格，service 仅在锚点 */
        fun allBuildings(): List<BuildingEntry> {
            val w = current ?: return emptyList()
            val out = mutableListOf<BuildingEntry>()
            for (y in 1..w.rows) {
                for (x in 1..w.cols) {
                    val b = w.grid[y - 1][x - 1].building ?: continue
                    if (b.isService) {
                        if (b.ax == x && b.ay == y) out.add(BuildingEntry(x, y, b))
                    } else {
                        out.add(BuildingEntry(x, y, b))
                    }
                }
            }
            return out
        }

        /** 推土机：返回 Pair(kind, id)，kind = "grown" | "service" | "road" | "zone"，未拆到为 null */
        fun bulldoze(x: Int, y: Int): Pair<String, String?>? {
            val t = tile(x, y) ?: return null
            val w = current ?: return null
            t.building?.let { b ->
                if (b.isService) {
                    for (yy in b.ay until b.ay + b.h) {
                        for (xx in b.ax until b.ax + b.w) {
                            w.grid[yy - 1][xx - 1].building = null
                        }
                    }
                    return "service" to b.service
                }
                t.building = null
                return "grown" to b.zone
            }
            t.road?.let { kind ->
                t.road = null
                return "road" to kind
            }
            if (t.zone != "none") {
                t.zone = "none"
                return "zone" to null
            }
            return null
        }

        fun stats(): WorldStats {
            val w = current ?: return WorldStats()
            val s = WorldStats()
            for (y in 1..w.rows) {
                for (x in 1..w.cols) {
                    val t = w.grid[y - 1][x - 1]
                    if (t.road != null) s.roadCount++
                    val b = t.building ?: continue
                    if (b.isService) {
                        s.serviceCount++
                    } else {
                        val lv = Config.GROWN[b.zone]?.levels?.getOrNull(b.level - 1) ?: continue
                        when (b.zone) {
                            "residential" -> { s.resCap += lv.cap; s.resCount++ }
                            "commercial" -> { s.comCap += lv.cap; s.comCount++ }
                            else -> {
                                s.indCap += lv.cap
                                s.indCount++
                                s.pollution += lv.pollution
                            }
                        }
                    }
                }
            }
            return s
        }

        fun cityLevel(): Config.CityLevelDef {
            val pop = current?._pop ?: 0
            for (i in Config.CITY_LEVELS.indices.reversed()) {
                if (pop >= Config.CITY_LEVELS[i].popReq) return Config.CITY_LEVELS[i]
            }
            return Config.CITY_LEVELS[0]
        }

        // -------------------------------------------------------------------
        // 覆盖系统：电/水/垃圾等「沿道路网」传播（设施需邻路才生效）
        // -------------------------------------------------------------------

        /** 沿路网 BFS：从该类设施扩散，返回被覆盖的成长建筑 id 集合（id = y*cols+x） */
        fun bfsCovered(category: String): Set<Int> {
            val w = current ?: return emptySet()
            val result = mutableSetOf<Int>()
            val queue = ArrayDeque<Pair<Int, Int>>()
            val visited = mutableSetOf<Pair<Int, Int>>()
            for (e in allBuildings()) {
                val b = e.b
                if (!b.isService) continue
                val cfg = serviceConfig(b.service) ?: continue
                if (cfg.category != category) continue
                for (yy in e.y until e.y + b.h) {
                    for (xx in e.x until e.x + b.w) {
                        val p = xx to yy
                        if (visited.add(p)) queue.addLast(p)
                    }
                }
            }
            val dx = intArrayOf(1, -1, 0, 0)
            val dy = intArrayOf(0, 0, 1, -1)
            while (queue.isNotEmpty()) {
                val (cx, cy) = queue.removeFirst()
                for (k in 0 until 4) {
                    val nx = cx + dx[k]
                    val ny = cy + dy[k]
                    val t = tile(nx, ny) ?: continue
                    if (t.road != null) {
                        val p = nx to ny
                        if (visited.add(p)) queue.addLast(p)
                    } else if (t.building != null && t.building?.isService != true) {
                        result.add(ny * w.cols + nx)
                    }
                }
            }
            return result
        }

        /** 某格是否被覆盖（沿路网） */
        fun isCoveredBy(x: Int, y: Int, category: String): Boolean {
            val w = current ?: return false
            return (y * w.cols + x) in bfsCovered(category)
        }

        /** 各类别的覆盖比例（0..1，无建筑时视为 1） */
        fun coverage(): Coverage {
            val w = current ?: return Coverage(1f, 1f, 1f, 1f, 1f, 1f)
            val grown = allBuildings().filter { !it.b.isService }
            fun ratio(cat: String): Float {
                if (grown.isEmpty()) return 1f
                val covered = bfsCovered(cat)
                var n = 0
                for (g in grown) if ((g.y * w.cols + g.x) in covered) n++
                return n.toFloat() / grown.size
            }
            return Coverage(
                ratio(Config.ServiceCat.POWER),
                ratio(Config.ServiceCat.WATER),
                ratio(Config.ServiceCat.GARBAGE),
                ratio(Config.ServiceCat.HEALTH),
                ratio(Config.ServiceCat.EDUCATION),
                ratio(Config.ServiceCat.SAFETY)
            )
        }
    }

    /** 当前人口（GameData 每日写入，供成长/晋级使用） */
    var _pop: Int = 0
}

data class BuildingEntry(val x: Int, val y: Int, val b: Building)

/** 各类基础设施覆盖比例（0..1） */
data class Coverage(
    val power: Float,
    val water: Float,
    val garbage: Float,
    val health: Float,
    val education: Float,
    val safety: Float
)
