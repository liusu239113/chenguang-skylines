package com.dshx.game.she.world

import com.dshx.game.she.Config
import com.dshx.game.she.GameData
import kotlin.math.abs
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
    var road: String? = null          // "dirt" | "local" | "avenue" | "highway"
    var building: Building? = null
    var pipe: Boolean = false         // 地下水管
    var cable: Boolean = false        // 地下电缆
    var sewer: Boolean = false        // 污水管
    var metro: Boolean = false        // 地铁隧道
    var rail: Boolean = false         // 地面铁轨
    var district: Int = 0             // 区划 id，0=未划
    var groundPol: Int = 0            // 地面污染 0-100
    var waterPol: Int = 0             // 水污染 0-100
    var onFire: Boolean = false
}

class Building {
    // grown
    var zone: String? = null          // residential / commercial / industrial / office
    var level: Int = 1
    var born: Double = 0.0
    var residents: Int = 0            // 住宅当前入住人数
    var workers: Int = 0              // 商/工/办公在岗人数
    var abandoned: Boolean = false
    var ageDays: Int = 0
    var garbage: Int = 0              // 建筑垃圾堆积
    var crime: Int = 0                // 建筑犯罪热度
    // service
    var service: String? = null
    var ax: Int = 0
    var ay: Int = 0
    var w: Int = 1
    var h: Int = 1

    val isService: Boolean get() = service != null
    fun cap(): Int = Config.GROWN[zone]?.levels?.getOrNull(level - 1)?.cap ?: 0
    fun occupied(): Int = if (zone == "residential") residents else workers
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
    var offCap: Int = 0
    var resCount: Int = 0
    var comCount: Int = 0
    var indCount: Int = 0
    var offCount: Int = 0
    var pollution: Int = 0
    var roadCount: Int = 0
    var serviceCount: Int = 0
    var roadCapacity: Int = 0
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
    var highwayConnected: Boolean = false
    var prosperity: Int = 0
    var unlockCx: Int = 8
    var unlockCy: Int = 8
    var unlockR: Int = 9

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

            // 河流位置随种子变化
            val riverOnRight = seed % 2 == 0
            val riverX = if (riverOnRight) w.cols - 3 else 4
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

            val lakeX = 6 + abs(seed * 7) % (w.cols - 12)
            val lakeY = 5 + abs(seed * 11) % 18
            val lakeR = 3 + abs(seed) % 3
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

            // 开局十字：和建造菜单里的「两车道」同一类，其余靠解锁后自己修
            fun setRoad(x: Int, y: Int, kind: String) {
                if (x !in 1..w.cols || y !in 1..w.rows) return
                val t = w.grid[y - 1][x - 1]
                if (t.terrain == "water") return
                t.road = kind
                t.zone = "none"
            }

            var startX = 8 + abs(seed * 3) % 10
            var startY = 10 + abs(seed * 5) % 12
            fun landOk(x: Int, y: Int): Boolean {
                if (x !in 4..w.cols - 4 || y !in 6..w.rows - 6) return false
                return w.grid[y - 1][x - 1].terrain != "water"
            }
            var tries = 0
            while (!landOk(startX, startY) && tries++ < 40) {
                startX = 6 + (startX * 7 + seed + tries) % (w.cols - 12)
                startY = 8 + (startY * 5 + seed + tries * 3) % (w.rows - 16)
            }
            w.unlockCx = startX
            w.unlockCy = startY
            w.unlockR = 10
            w.spawnX = startX
            w.spawnY = startY

            val xsV = IntArray(7)
            val ysV = IntArray(7)
            for (i in 0..6) {
                val y = startY - 3 + i
                setRoad(startX, y, "local")
                xsV[i] = startX
                ysV[i] = y
            }
            w.roadLines.add(
                RoadLine(
                    name = pickName(seed, 13),
                    kind = "local",
                    segX = xsV,
                    segY = ysV,
                    dir = "v",
                    labelX = 0,
                    labelY = startY
                )
            )
            val xsH = IntArray(7)
            val ysH = IntArray(7)
            for (i in 0..6) {
                val x = startX - 3 + i
                setRoad(x, startY, "local")
                xsH[i] = x
                ysH[i] = startY
            }
            w.roadLines.add(
                RoadLine(
                    name = pickName(seed, 45),
                    kind = "local",
                    segX = xsH,
                    segY = ysH,
                    dir = "h",
                    labelX = startX,
                    labelY = 0
                )
            )

            // 外环高速：贴地图边缘，不直接进城区。玩家把城区路接到高速后才会进外地车。
            fun setHwy(x: Int, y: Int) {
                val t = w.grid[y - 1][x - 1]
                if (t.terrain == "water") return
                t.road = "highway"
                t.zone = "none"
            }
            val hx0 = 2
            val hx1 = w.cols - 1
            val hy0 = 2
            val hy1 = w.rows - 1
            for (x in hx0..hx1) {
                setHwy(x, hy0)
                setHwy(x, hy1)
            }
            for (y in hy0..hy1) {
                setHwy(hx0, y)
                setHwy(hx1, y)
            }
            w.roadLines.add(
                RoadLine(
                    name = "外环高速",
                    kind = "highway",
                    segX = IntArray(hx1 - hx0 + 1) { hx0 + it },
                    segY = IntArray(hx1 - hx0 + 1) { hy0 },
                    dir = "h",
                    labelX = floor(w.cols * 0.5).toInt(),
                    labelY = 0
                )
            )

            current = w
            return w
        }

        fun isHighway(x: Int, y: Int): Boolean = tile(x, y)?.road == "highway"

        fun refreshHighwayLink() {
            val w = current ?: return
            var linked = false
            val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
            outer@ for (y in 1..w.rows) {
                for (x in 1..w.cols) {
                    if (w.grid[y - 1][x - 1].road != "highway") continue
                    for (d in dirs) {
                        val nx = x + d[0]
                        val ny = y + d[1]
                        val r = tile(nx, ny)?.road ?: continue
                        if (r != "highway") {
                            linked = true
                            break@outer
                        }
                    }
                }
            }
            w.highwayConnected = linked
        }

        fun highwayRamps(): List<Pair<Int, Int>> {
            val w = current ?: return emptyList()
            val out = mutableListOf<Pair<Int, Int>>()
            val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
            for (y in 1..w.rows) {
                for (x in 1..w.cols) {
                    if (w.grid[y - 1][x - 1].road != "highway") continue
                    for (d in dirs) {
                        val nx = x + d[0]
                        val ny = y + d[1]
                        val r = tile(nx, ny)?.road ?: continue
                        if (r != "highway") {
                            out.add(x to y)
                            break
                        }
                    }
                }
            }
            return out
        }

        // -------------------------------------------------------------------
        // 查询
        // -------------------------------------------------------------------
        fun inBounds(x: Int, y: Int): Boolean {
            val w = current ?: return false
            return x >= 1 && x <= w.cols && y >= 1 && y <= w.rows
        }

        fun unlockStep(): Int = Config.GROWTH.unlockPerPop

        fun unlockRadius(): Int {
            val w = current ?: return 10
            val extra = (w._pop / unlockStep()).coerceIn(0, 48)
            return (w.unlockR + extra).coerceAtMost(max(w.cols, w.rows))
        }

        fun isUnlocked(x: Int, y: Int): Boolean {
            val w = current ?: return false
            return abs(x - w.unlockCx) + abs(y - w.unlockCy) <= unlockRadius()
        }

        fun nextUnlockPop(): Int {
            val w = current ?: return unlockStep()
            val extra = (w._pop / unlockStep()).coerceIn(0, 48)
            if (extra >= 48) return w._pop
            return (extra + 1) * unlockStep()
        }

        fun lockedHint(): String {
            val need = nextUnlockPop()
            val have = current?._pop ?: 0
            val left = (need - have).coerceAtLeast(0)
            return "黑色区域随人口自动解锁。再增加 $left 人会向外扩一圈（目标 $need 人）。路旁划住宅、通电通水后人口会涨。"
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

        /** 0-based 世界坐标双线性高度，相邻格子平滑过渡 */
        fun heightAt(wx: Float, wy: Float): Float {
            val w = current ?: return 20f
            val fx = wx.coerceIn(0f, (w.cols - 1).toFloat())
            val fy = wy.coerceIn(0f, (w.rows - 1).toFloat())
            val x0 = floor(fx).toInt()
            val y0 = floor(fy).toInt()
            val x1 = min(w.cols - 1, x0 + 1)
            val y1 = min(w.rows - 1, y0 + 1)
            val tx = fx - x0
            val ty = fy - y0
            val h00 = w.elev[y0][x0].toFloat()
            val h10 = w.elev[y0][x1].toFloat()
            val h01 = w.elev[y1][x0].toFloat()
            val h11 = w.elev[y1][x1].toFloat()
            return h00 * (1 - tx) * (1 - ty) + h10 * tx * (1 - ty) + h01 * (1 - tx) * ty + h11 * tx * ty
        }

        fun waterFrac(wx: Float, wy: Float): Float {
            val w = current ?: return 0f
            var n = 0f
            var water = 0f
            for (dy in -1..1) for (dx in -1..1) {
                val x = floor(wx + dx * 0.55f).toInt()
                val y = floor(wy + dy * 0.55f).toInt()
                if (x !in 0 until w.cols || y !in 0 until w.rows) continue
                n += 1f
                if (w.grid[y][x].terrain == "water") water += 1f
            }
            return if (n <= 0f) 0f else water / n
        }

        /** 地表绘制高度：水域压到统一水平面，岸边插值浅滩 */
        fun surfaceAt(wx: Float, wy: Float): Float {
            val raw = heightAt(wx, wy)
            val wf = waterFrac(wx, wy)
            return if (wf <= 0f) raw else raw * (1f - wf) + 8f * wf
        }

        fun isRoad(x: Int, y: Int): Boolean = tile(x, y)?.road != null

        /** 当前格路向：竖路朝下(1)，横路朝右(0)。有记忆朝向时优先用记忆。 */
        fun roadHeadingAt(x: Int, y: Int, remembered: Int = -1): Int {
            val h = isRoad(x - 1, y) || isRoad(x + 1, y)
            val v = isRoad(x, y - 1) || isRoad(x, y + 1)
            return when {
                v && !h -> 1
                h && !v -> 0
                remembered in 0..3 -> remembered
                v -> 1
                else -> 0
            }
        }

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
        private val ROAD_RANK = mapOf(
            "dirt" to 0, "local" to 1, "avenue" to 2, "highway" to 3, "metro" to 2, "rail" to 2
        )

        fun hasService(id: String): Boolean = allBuildings().any { it.b.service == id }

        fun canRoad(x: Int, y: Int, kind: String = "local"): Pair<Boolean, String?> {
            val t = tile(x, y) ?: return false to "越界"
            if (!isUnlocked(x, y) && kind != "highway") return false to lockedHint()
            if (kind == "metro" && !hasService("metro")) return false to "先建地铁站才能挖隧道"
            if (kind == "rail" && !hasService("rail_station")) return false to "先建火车站才能铺铁轨"
            if (t.terrain == "water" && kind != "metro") return false to "不能铺在水上"
            if (t.building != null) return false to "先拆除这里的建筑"
            val exist = t.road
            if (exist != null) {
                val a = ROAD_RANK[exist] ?: 0
                val b = ROAD_RANK[kind] ?: 0
                if (kind != "metro" && kind != "rail" && b <= a) return false to null
            }
            return true to null
        }

        fun setRoad(x: Int, y: Int, kind: String): Boolean {
            val t = tile(x, y) ?: return false
            if (t.building != null) return false
            if (t.terrain == "water" && kind != "metro") return false
            if (kind == "metro") {
                t.metro = true
                return true
            }
            if (kind == "rail") {
                t.rail = true
                t.zone = "none"
                return true
            }
            t.road = kind
            t.zone = "none"
            nameNewStreet(x, y, kind)
            return true
        }

        fun roadNameAt(x: Int, y: Int): String? {
            val w = current ?: return null
            if (tile(x, y)?.road == null) return null
            for (line in w.roadLines) {
                val n = min(line.segX.size, line.segY.size)
                for (i in 0 until n) {
                    if (line.segX[i] == x && line.segY[i] == y) return line.name
                }
            }
            return null
        }

        /** 新修街道自动命名：接到已有路则沿用路名，否则开一条新街 */
        private fun nameNewStreet(x: Int, y: Int, kind: String) {
            if (kind == "metro" || kind == "rail") return
            val w = current ?: return
            val existing = roadNameAt(x, y)
            if (existing != null) return
            val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
            for (d in dirs) {
                val nx = x + d[0]
                val ny = y + d[1]
                val neighbor = tile(nx, ny)?.road
                if (neighbor == null || neighbor == "metro") continue
                val name = roadNameAt(nx, ny) ?: continue
                for (line in w.roadLines) {
                    if (line.name != name) continue
                    val n = min(line.segX.size, line.segY.size)
                    val xs = IntArray(n + 1)
                    val ys = IntArray(n + 1)
                    for (i in 0 until n) {
                        xs[i] = line.segX[i]
                        ys[i] = line.segY[i]
                    }
                    xs[n] = x
                    ys[n] = y
                    val idx = w.roadLines.indexOf(line)
                    if (idx >= 0) {
                        w.roadLines[idx] = RoadLine(
                            name = line.name,
                            kind = if (kind == "highway" || line.kind == "highway") "highway"
                            else if (kind == "avenue" || line.kind == "avenue") "avenue"
                            else line.kind,
                            segX = xs,
                            segY = ys,
                            dir = line.dir,
                            labelX = line.labelX,
                            labelY = line.labelY
                        )
                    }
                    return
                }
            }
            val horiz = (tile(x - 1, y)?.road != null) || (tile(x + 1, y)?.road != null)
            val name = pickName(GameData.seed, w.roadLines.size * 11 + x * 3 + y)
            w.roadLines.add(
                RoadLine(
                    name = name,
                    kind = kind,
                    segX = intArrayOf(x),
                    segY = intArrayOf(y),
                    dir = if (horiz) "h" else "v",
                    labelX = if (horiz) x else 0,
                    labelY = if (horiz) 0 else y
                )
            )
        }

        fun roadCapacity(x: Int, y: Int): Int = Config.ROAD[tile(x, y)?.road]?.capacity ?: 0

        fun noiseAt(x: Int, y: Int): Int {
            var n = 0
            for (dy in -2..2) for (dx in -2..2) {
                val t = tile(x + dx, y + dy) ?: continue
                n += Config.ROAD[t.road]?.noise ?: 0
            }
            return n
        }

        // -------------------------------------------------------------------
        // 分区
        // -------------------------------------------------------------------
        fun canZone(x: Int, y: Int): Pair<Boolean, String?> {
            val t = tile(x, y) ?: return false to "越界"
            if (!isUnlocked(x, y)) return false to lockedHint()
            if (t.terrain == "water") return false to "水域无法划区"
            if (t.road != null) return false to null
            if (t.building != null) return false to null
            return true to null
        }

        fun setZone(x: Int, y: Int, zone: String): Boolean {
            val t = tile(x, y) ?: return false
            if (!isUnlocked(x, y)) return false
            if (t.terrain == "water" || t.road != null || t.building != null) return false
            t.zone = zone
            return true
        }

        // -------------------------------------------------------------------
        // 服务设施
        // -------------------------------------------------------------------
        private fun tileBlocksService(t: Tile, id: String): Boolean {
            if (t.terrain == "water" && id != "harbor") return true
            if (t.road != null) return true
            val b = t.building ?: return false
            if (b.isService) return true
            return !b.abandoned
        }

        fun footprintFree(id: String, x: Int, y: Int): Boolean {
            val s = serviceConfig(id) ?: return false
            for (yy in y until y + s.sizeH) {
                for (xx in x until x + s.sizeW) {
                    val t = tile(xx, yy) ?: return false
                    if (tileBlocksService(t, id)) return false
                    if (!isUnlocked(xx, yy)) return false
                }
            }
            return true
        }

        /** 点在占地范围内任意一格，找能放下的锚点，避免 2×2 小学被当成占用 */
        fun findServiceAnchor(id: String, tapX: Int, tapY: Int): Pair<Int, Int>? {
            val s = serviceConfig(id) ?: return null
            if (s.sizeW == 1 && s.sizeH == 1) {
                return if (footprintFree(id, tapX, tapY)) tapX to tapY else null
            }
            for (dy in 0 until s.sizeH) {
                for (dx in 0 until s.sizeW) {
                    val ax = tapX - dx
                    val ay = tapY - dy
                    if (footprintFree(id, ax, ay)) return ax to ay
                }
            }
            return null
        }

        fun canPlaceService(id: String, x: Int, y: Int): Pair<Boolean, String?> {
            val s = serviceConfig(id) ?: return false to "未知设施"
            val anchor = findServiceAnchor(id, x, y)
            val ax = anchor?.first ?: x
            val ay = anchor?.second ?: y
            if (!isUnlocked(ax, ay)) return false to lockedHint()
            for (yy in ay until ay + s.sizeH) {
                for (xx in ax until ax + s.sizeW) {
                    val t = tile(xx, yy) ?: return false to "超出地图"
                    if (t.terrain == "water" && id != "harbor") {
                        return false to "不能建在水上"
                    }
                    if (t.road != null) return false to "该位置被占用"
                    val b = t.building
                    if (b != null && (b.isService || !b.abandoned)) return false to "该位置被占用"
                }
            }
            if (s.category != Config.ServiceCat.AMENITY) {
                var adjacent = false
                outer@ for (yy in ay - 1..ay + s.sizeH) {
                    for (xx in ax - 1..ax + s.sizeW) {
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
            val anchor = findServiceAnchor(id, x, y) ?: return false
            val ax = anchor.first
            val ay = anchor.second
            for (yy in ay until ay + s.sizeH) {
                for (xx in ax until ax + s.sizeW) {
                    val t = w.grid[yy - 1][xx - 1]
                    t.building = null
                    t.zone = "none"
                    t.onFire = false
                    val b = Building()
                    b.service = id
                    b.ax = ax; b.ay = ay
                    b.w = s.sizeW; b.h = s.sizeH
                    t.building = b
                }
            }
            if (s.id == "metro") Networks.seedMetroAround(ax, ay, s.sizeW, s.sizeH)
            if (s.id == "rail_station") Networks.seedRailAround(ax, ay, s.sizeW, s.sizeH)
            return true
        }

        // -------------------------------------------------------------------
        // 成长建筑
        // -------------------------------------------------------------------
        fun growBuilding(zone: String, x: Int, y: Int, level: Int, born: Double): Boolean {
            val t = tile(x, y) ?: return false
            if (!isUnlocked(x, y)) return false
            if (t.building != null || t.road != null || t.terrain == "water") return false
            if (t.zone == "none") return false
            val b = Building()
            b.zone = zone
            b.level = level
            b.born = born
            if (zone == "residential") b.residents = 2
            else b.workers = 1
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
                            val cell = w.grid[yy - 1][xx - 1]
                            cell.building = null
                            cell.zone = "none"
                        }
                    }
                    return "service" to b.service
                }
                t.building = null
                t.zone = "none"
                return "grown" to b.zone
            }
            t.road?.let { kind ->
                t.road = null
                t.metro = false
                t.rail = false
                return "road" to kind
            }
            if (t.metro) {
                t.metro = false
                return "road" to "metro"
            }
            if (t.rail) {
                t.rail = false
                return "road" to "rail"
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
                    if (t.road != null) {
                        s.roadCount++
                        s.roadCapacity += Config.ROAD[t.road]?.capacity ?: 10
                    }
                    val b = t.building ?: continue
                    if (b.isService) {
                        if (b.ax == x && b.ay == y) {
                            s.serviceCount++
                            s.pollution += serviceConfig(b.service)?.pollution ?: 0
                        }
                    } else {
                        val lv = Config.GROWN[b.zone]?.levels?.getOrNull(b.level - 1) ?: continue
                        if (b.abandoned) continue
                        when (b.zone) {
                            "residential" -> { s.resCap += lv.cap; s.resCount++ }
                            "commercial" -> { s.comCap += lv.cap; s.comCount++ }
                            "office" -> { s.offCap += lv.cap; s.offCount++ }
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

        /** 地价：滨水/绿地/教育抬升，工业/污染拉低（建筑升级门槛） */
        fun landValue(x: Int, y: Int): Int {
            var v = 4
            for (dy in -3..3) {
                for (dx in -3..3) {
                    val t = tile(x + dx, y + dy) ?: continue
                    if (t.terrain == "water") v += 2
                    if (t.terrain == "forest") v += 1
                    val b = t.building ?: continue
                    if (b.isService) {
                        val cfg = serviceConfig(b.service)
                        if (cfg?.landValue == true) v += 2
                        if ((cfg?.pollution ?: 0) > 0) v -= 2
                    } else if (b.zone == "industrial") {
                        v -= 1
                    }
                    if (b.abandoned) v -= 2
                }
            }
            return v
        }

        // -------------------------------------------------------------------
        // 覆盖系统：按设施半径（造价/体量越大半径越大）
        // -------------------------------------------------------------------

        fun coverRadius(cfg: Config.ServiceDef): Int =
            max(cfg.radius, 2 + cfg.sizeW + cfg.cost / 400)

        /** 覆盖圈中心：设施占地的几何中心，和画面上的圈对齐 */
        fun coverCenter(e: BuildingEntry): Pair<Float, Float> {
            return (e.x - 1f + e.b.w / 2f) to (e.y - 1f + e.b.h / 2f)
        }

        fun isCoveredBy(x: Int, y: Int, category: String): Boolean {
            for (e in allBuildings()) {
                if (!e.b.isService) continue
                val cfg = serviceConfig(e.b.service) ?: continue
                if (cfg.category != category) continue
                val (cx, cy) = coverCenter(e)
                val dx = (x - 1f + 0.5f) - cx
                val dy = (y - 1f + 0.5f) - cy
                val r = coverRadius(cfg) + 0.5f
                if (dx * dx + dy * dy <= r * r) return true
            }
            return false
        }

        fun coveringFacility(x: Int, y: Int, category: String): BuildingEntry? {
            var best: BuildingEntry? = null
            var bestD = Float.MAX_VALUE
            for (e in allBuildings()) {
                if (!e.b.isService) continue
                val cfg = serviceConfig(e.b.service) ?: continue
                if (cfg.category != category) continue
                val (cx, cy) = coverCenter(e)
                val dx = (x - 1f + 0.5f) - cx
                val dy = (y - 1f + 0.5f) - cy
                val r = coverRadius(cfg) + 0.5f
                val d = dx * dx + dy * dy
                if (d <= r * r && d < bestD) {
                    bestD = d
                    best = e
                }
            }
            return best
        }

        fun bfsCovered(category: String): Set<Int> {
            val w = current ?: return emptySet()
            val result = mutableSetOf<Int>()
            for (g in allBuildings()) {
                if (g.b.isService) continue
                if (isCoveredBy(g.x, g.y, category)) result.add(g.y * w.cols + g.x)
            }
            return result
        }

        /** 各类别的覆盖比例（0..1，无建筑时视为 1） */
        fun coverage(): Coverage {
            val w = current ?: return Coverage(1f, 1f, 1f, 1f, 1f, 1f, 1f)
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
                ratio(Config.ServiceCat.SAFETY),
                ratio(Config.ServiceCat.DEATH)
            )
        }

        fun hasLandmark(id: String): Boolean =
            allBuildings().any { it.b.service == id }

        fun plantTree(x: Int, y: Int): Boolean {
            val t = tile(x, y) ?: return false
            if (!isUnlocked(x, y)) return false
            if (t.terrain == "water" || t.road != null || t.building != null) return false
            t.terrain = "forest"
            t.groundPol = max(0, t.groundPol - 18)
            return true
        }

        fun raiseLand(x: Int, y: Int): Boolean {
            val w = current ?: return false
            if (!inBounds(x, y) || !isUnlocked(x, y)) return false
            val t = w.grid[y - 1][x - 1]
            if (t.building != null || t.road != null) return false
            w.elev[y - 1][x - 1] = min(280, w.elev[y - 1][x - 1] + 18)
            if (t.terrain == "water") t.terrain = "plain"
            if (w.elev[y - 1][x - 1] > 200) t.terrain = "hill"
            return true
        }

        fun lowerLand(x: Int, y: Int): Boolean {
            val w = current ?: return false
            if (!inBounds(x, y) || !isUnlocked(x, y)) return false
            val t = w.grid[y - 1][x - 1]
            if (t.building != null || t.road != null) return false
            w.elev[y - 1][x - 1] = max(4, w.elev[y - 1][x - 1] - 18)
            if (w.elev[y - 1][x - 1] < 16) t.terrain = "water"
            return true
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
    val safety: Float,
    val death: Float = 1f
)
