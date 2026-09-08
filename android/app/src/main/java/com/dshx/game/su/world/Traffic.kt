package com.dshx.game.su.world

import com.dshx.game.su.Config
import com.dshx.game.su.GameData
import com.dshx.game.su.RGBA
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class DriverCard(
    val name: String,
    val age: Int,
    val job: String,
    val workplace: String,
    val homeName: String,
    val homeX: Int,
    val homeY: Int,
    val workX: Int,
    val workY: Int,
    val education: String,
    val from: String,
    val plate: String,
    val carType: String
)

class TrafficCar(
    var kind: String,                 // local | visitor | freight | through
    var x: Int,
    var y: Int,
    var dir: Int,
    var prog: Float,
    var cruise: Float,
    var maxSpeed: Float,
    var color: RGBA,
    var destX: Int,
    var destY: Int,
    var homeX: Int,
    var homeY: Int,
    var parked: Boolean = true,
    var wait: Float = 0f,
    var driver: DriverCard,
    var houseKey: String = ""
)

class TrainCar(
    var x: Int,
    var y: Int,
    var dir: Int,
    var prog: Float,
    var speed: Float,
    var name: String
)

class PlaneCraft(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alt: Float,
    var phase: Float,
    var ax: Int,
    var ay: Int,
    var flight: String
)

object Traffic {

    val cars: MutableList<TrafficCar> = mutableListOf()
    val trains: MutableList<TrainCar> = mutableListOf()
    val planes: MutableList<PlaneCraft> = mutableListOf()
    var selected: TrafficCar? = null
    var selectedTrain: TrainCar? = null
    var selectedPlane: PlaneCraft? = null
    var visitorsToday: Int = 0
    var localMoving: Int = 0

    private val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(0, -1))
    private val carColors = listOf(
        RGBA(242, 240, 236), RGBA(198, 92, 78), RGBA(96, 128, 182),
        RGBA(234, 194, 88), RGBA(134, 170, 134), RGBA(96, 98, 104),
        RGBA(210, 160, 120), RGBA(70, 90, 120)
    )
    private val SURNAMES = listOf("陈", "林", "黄", "周", "吴", "徐", "孙", "马", "朱", "胡", "郭", "何", "高", "罗", "郑", "梁")
    private val GIVEN = listOf("晓晨", "雨桐", "嘉宁", "子安", "明轩", "思远", "若溪", "清扬", "予安", "一诺", "景行", "乐言", "晚晴", "书衡")
    private val JOBS_OFF = listOf("设计师", "会计", "编辑", "程序员", "经纪人")
    private val JOBS_COM = listOf("店员", "厨师", "药师", "咖啡师", "收银")
    private val JOBS_IND = listOf("技工", "仓管", "质检", "司机", "电工")
    private val JOBS_HOME = listOf("居家看店", "自由撰稿", "待业")
    private val VISITOR_FROM = listOf("望江县", "桂香镇", "临湖市", "青藤港", "云溪区", "银杏乡")
    private val CAR_TYPES = listOf("家用轿车", "两厢代步", "旅行车", "小货车")
    private val roadFlow = IntArray(Config.MAP.cols * Config.MAP.rows)

    fun flowAt(x: Int, y: Int): Int {
        val w = World.current ?: return 0
        if (x !in 1..w.cols || y !in 1..w.rows) return 0
        return roadFlow[(y - 1) * w.cols + (x - 1)]
    }

    fun reset() {
        cars.clear()
        trains.clear()
        planes.clear()
        selected = null
        selectedTrain = null
        selectedPlane = null
        visitorsToday = 0
        localMoving = 0
        for (i in roadFlow.indices) roadFlow[i] = 0
    }

    fun tick(dt: Float) {
        World.refreshHighwayLink()
        syncHouseholdCars()
        syncVisitors()
        syncThroughTraffic()
        syncTrains()
        syncPlanes()
        driveCars(dt)
        driveTrains(dt)
        flyPlanes(dt)
        decayFlow()
    }

    // ------------------------------------------------------------------
    // 一户一车：住宅长出来才有本地车，没接通高速时街上几乎没车
    // ------------------------------------------------------------------
    private fun syncHouseholdCars() {
        val homes = World.allBuildings().filter {
            !it.b.isService && it.b.zone == "residential" && !it.b.abandoned && it.b.residents > 0
        }
        val wanted = homes.associateBy { "${it.x},${it.y}" }
        cars.removeAll { it.kind == "local" && it.houseKey !in wanted }
        for (h in homes) {
            val key = "${h.x},${h.y}"
            if (cars.any { it.kind == "local" && it.houseKey == key }) continue
            val road = Citizens.nearestRoad(h.x, h.y) ?: continue
            val job = pickJob(h.x, h.y)
            val driver = makeDriver(h.x, h.y, job.first, job.second, job.third, job.fourth, local = true)
            val dir = pickDir(road.first, road.second)
            cars.add(
                TrafficCar(
                    kind = "local",
                    x = road.first, y = road.second, dir = dir,
                    prog = 0.2f, cruise = 0f,
                    maxSpeed = 1.35f + Random.nextFloat() * 0.25f,
                    color = carColors[abs(h.x * 11 + h.y * 19) % carColors.size],
                    destX = job.second, destY = job.third,
                    homeX = h.x, homeY = h.y,
                    parked = true,
                    driver = driver,
                    houseKey = key
                )
            )
        }
        val tod = GameData.timeOfDay
        val commute = tod in 0.08f..0.20f || tod in 0.50f..0.80f
        for (c in cars) {
            if (c.kind != "local") continue
            val samePlace = abs(c.driver.workX - c.homeX) + abs(c.driver.workY - c.homeY) <= 1
            if (commute && !samePlace) {
                if (c.parked && tod < 0.50f) {
                    c.parked = false
                    c.destX = c.driver.workX
                    c.destY = c.driver.workY
                    val road = Citizens.nearestRoad(c.homeX, c.homeY)
                    if (road != null) {
                        c.x = road.first; c.y = road.second; c.prog = 0.15f
                    }
                } else if (c.parked && tod >= 0.50f) {
                    c.parked = false
                    val shop = World.allBuildings().firstOrNull { it.b.zone == "commercial" && !it.b.abandoned }
                    if (shop != null && tod < 0.66f) {
                        c.destX = shop.x; c.destY = shop.y
                    } else {
                        c.destX = c.homeX; c.destY = c.homeY
                    }
                    val road = Citizens.nearestRoad(c.homeX, c.homeY)
                    if (road != null) {
                        c.x = road.first; c.y = road.second; c.prog = 0.15f
                    }
                }
            } else if (!c.parked) {
                c.destX = c.homeX
                c.destY = c.homeY
                if (abs(c.x - c.homeX) + abs(c.y - c.homeY) <= 2) {
                    c.parked = true
                    c.cruise = 0f
                }
            }
        }
    }

    private fun pickJob(hx: Int, hy: Int): Quadruple {
        val jobs = World.allBuildings().filter {
            !it.b.isService && !it.b.abandoned &&
                (it.b.zone == "commercial" || it.b.zone == "industrial" || it.b.zone == "office")
        }
        if (jobs.isEmpty()) {
            return Quadruple(JOBS_HOME[Random.nextInt(JOBS_HOME.size)], hx, hy, grownName("residential", hx, hy))
        }
        val pick = jobs[(hx * 13 + hy * 7) % jobs.size]
        val job = when (pick.b.zone) {
            "office" -> JOBS_OFF[(hx + hy) % JOBS_OFF.size]
            "industrial" -> JOBS_IND[(hx + hy) % JOBS_IND.size]
            else -> JOBS_COM[(hx + hy) % JOBS_COM.size]
        }
        val wp = grownName(pick.b.zone ?: "commercial", pick.x, pick.y)
        return Quadruple(job, pick.x, pick.y, wp)
    }

    private class Quadruple(val first: String, val second: Int, val third: Int, val fourth: String)

    private fun makeDriver(
        hx: Int, hy: Int, job: String, wx: Int, wy: Int, workplace: String, local: Boolean
    ): DriverCard {
        val seed = hx * 97 + hy * 53 + job.hashCode()
        val r = Random(seed)
        val name = SURNAMES[abs(seed) % SURNAMES.size] + GIVEN[abs(seed / 7) % GIVEN.size]
        val eduLv = educationAt(hx, hy)
        val edu = when (eduLv) {
            3 -> "大学"
            2 -> "中学"
            1 -> "小学"
            else -> "自学"
        }
        val plate = String.format(
            "晨%s%c%03d",
            if (local) "A" else "B",
            'A' + abs(seed) % 26,
            abs(seed) % 900 + 100
        )
        return DriverCard(
            name = name,
            age = 22 + abs(seed) % 38,
            job = job,
            workplace = workplace,
            homeName = if (local) grownName("residential", hx, hy) else VISITOR_FROM[abs(seed) % VISITOR_FROM.size],
            homeX = hx, homeY = hy,
            workX = wx, workY = wy,
            education = edu,
            from = if (local) "本市" else VISITOR_FROM[abs(seed / 3) % VISITOR_FROM.size],
            plate = plate,
            carType = CAR_TYPES[abs(seed) % CAR_TYPES.size]
        )
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

    fun grownName(zone: String, x: Int, y: Int): String {
        val pre: List<String>
        val suf: List<String>
        when (zone) {
            "residential" -> { pre = Config.NAMES.RES_PRE; suf = Config.NAMES.RES_SUF }
            "commercial" -> { pre = Config.NAMES.COM_PRE; suf = Config.NAMES.COM_SUF }
            "office" -> { pre = Config.NAMES.OFF_PRE; suf = Config.NAMES.OFF_SUF }
            else -> { pre = Config.NAMES.IND_PRE; suf = Config.NAMES.IND_SUF }
        }
        val i = (x * 7 + y * 13) % pre.size
        val j = (x * 5 + y * 11) % suf.size
        return pre[i] + suf[j]
    }

    // ------------------------------------------------------------------
    // 外地车：必须接通外环高速，数量跟繁荣度走
    // ------------------------------------------------------------------
    private fun syncVisitors() {
        val w = World.current ?: return
        val s = GameData.current
        val pop = s?.population?.toInt() ?: 0
        val happy = s?.happiness ?: 50.0
        val st = World.stats()
        val score = (pop / 8) + (happy.toInt() * 2) + (st.comCount + st.offCount) * 4 +
            st.serviceCount * 6 + (if (w.highwayConnected) 40 else 0)
        w.prosperity = score
        val ramps = World.highwayRamps()
        if (!w.highwayConnected || ramps.isEmpty()) {
            cars.removeAll { it.kind == "visitor" || it.kind == "freight" }
            visitorsToday = cars.count { it.kind == "through" }
            return
        }
        val wantVisitors = min(10, max(0, score / 90))
        val wantFreight = min(4, st.indCount / 6)
        val haveV = cars.count { it.kind == "visitor" }
        val haveF = cars.count { it.kind == "freight" }
        if (haveV < wantVisitors) spawnVisitor(ramps, freight = false)
        if (haveF < wantFreight) spawnVisitor(ramps, freight = true)
        visitorsToday = cars.count { it.kind == "visitor" || it.kind == "freight" || it.kind == "through" }
    }

    private fun highwayLoop(): List<Pair<Int, Int>> {
        val w = World.current ?: return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        for (y in 1..w.rows) for (x in 1..w.cols) {
            if (w.grid[y - 1][x - 1].road == "highway") out.add(x to y)
        }
        return out
    }

    private fun syncThroughTraffic() {
        val loop = highwayLoop()
        if (loop.size < 8) {
            cars.removeAll { it.kind == "through" }
            return
        }
        val want = 8
        val have = cars.count { it.kind == "through" }
        if (have > want) {
            val extra = cars.filter { it.kind == "through" }.drop(want)
            cars.removeAll(extra.toSet())
            return
        }
        repeat(want - have) {
            val start = loop[Random.nextInt(loop.size)]
            var dest = loop[Random.nextInt(loop.size)]
            var guard = 0
            while (abs(dest.first - start.first) + abs(dest.second - start.second) < 8 && guard++ < 8) {
                dest = loop[Random.nextInt(loop.size)]
            }
            val from = VISITOR_FROM[Random.nextInt(VISITOR_FROM.size)]
            val driver = makeDriver(start.first, start.second, "过路司机", dest.first, dest.second, "外环高速", local = false)
                .copy(from = from, homeName = from, carType = if (Random.nextFloat() < 0.25f) "厢式货车" else "过路轿车")
            cars.add(
                TrafficCar(
                    kind = "through",
                    x = start.first, y = start.second,
                    dir = pickDir(start.first, start.second, "through"),
                    prog = Random.nextFloat() * 0.8f,
                    cruise = 2.0f,
                    maxSpeed = 2.4f,
                    color = carColors[Random.nextInt(carColors.size)],
                    destX = dest.first, destY = dest.second,
                    homeX = dest.first, homeY = dest.second,
                    parked = false,
                    driver = driver,
                    houseKey = ""
                )
            )
        }
    }

    private fun spawnVisitor(ramps: List<Pair<Int, Int>>, freight: Boolean) {
        val ramp = ramps[Random.nextInt(ramps.size)]
        val dests = World.allBuildings().filter {
            !it.b.abandoned && (
                if (freight) it.b.zone == "industrial" || it.b.service == "harbor" || it.b.service == "rail_station"
                else it.b.zone == "commercial" || it.b.service == "plaza" || it.b.service == "stadium" || it.b.service == "tv_tower"
                )
        }
        val dest = if (dests.isNotEmpty()) dests[Random.nextInt(dests.size)] else return
        val from = VISITOR_FROM[Random.nextInt(VISITOR_FROM.size)]
        val job = if (freight) "货运司机" else "外地游客"
        val driver = makeDriver(ramp.first, ramp.second, job, dest.x, dest.y, grownName(dest.b.zone ?: "commercial", dest.x, dest.y), local = false)
            .copy(from = from, homeName = from, carType = if (freight) "厢式货车" else "自驾轿车")
        cars.add(
            TrafficCar(
                kind = if (freight) "freight" else "visitor",
                x = ramp.first, y = ramp.second,
                dir = pickDir(ramp.first, ramp.second),
                prog = 0.1f, cruise = 1.4f,
                maxSpeed = if (freight) 1.2f else 1.6f,
                color = carColors[Random.nextInt(carColors.size)],
                destX = dest.x, destY = dest.y,
                homeX = ramp.first, homeY = ramp.second,
                parked = false,
                driver = driver,
                houseKey = ""
            )
        )
    }

    // ------------------------------------------------------------------
    // 开车：跟车匀速贴尾，不急刹急冲
    // ------------------------------------------------------------------
    private fun driveCars(dt: Float) {
        val w = World.current ?: return
        val redNow = (Growth.simTime * 1.05).toInt() % 4 < 2
        val rain = if (GameData.weather == 1) 0.82f else 1f
        val moving = cars.filter { !it.parked }
        localMoving = moving.count { it.kind == "local" }
        val gone = mutableListOf<TrafficCar>()
        for (c in moving) {
            if (!isRoad(c.x, c.y, c.kind)) {
                if (c.kind == "through") {
                    val loop = highwayLoop()
                    if (loop.isEmpty()) {
                        gone.add(c)
                        continue
                    }
                    val n = loop[Random.nextInt(loop.size)]
                    c.x = n.first; c.y = n.second; c.prog = 0.2f
                } else {
                    val nr = Citizens.nearestRoad(c.x, c.y)
                    if (nr == null) {
                        if (c.kind != "local") gone.add(c)
                        else c.parked = true
                        continue
                    }
                    c.x = nr.first; c.y = nr.second; c.prog = 0.2f
                }
            }
            if (abs(c.x - c.destX) + abs(c.y - c.destY) <= 2) {
                when (c.kind) {
                    "through" -> {
                        val loop = highwayLoop()
                        if (loop.isNotEmpty()) {
                            val n = loop[Random.nextInt(loop.size)]
                            c.destX = n.first; c.destY = n.second
                            c.homeX = n.first; c.homeY = n.second
                        }
                    }
                    "visitor", "freight" -> {
                        if (c.destX == c.homeX && c.destY == c.homeY) {
                            gone.add(c)
                            continue
                        } else {
                            c.wait += dt
                            if (c.wait > 1.2f) {
                                c.wait = 0f
                                val ramps = World.highwayRamps()
                                if (ramps.isNotEmpty()) {
                                    val r = ramps[Random.nextInt(ramps.size)]
                                    c.destX = r.first; c.destY = r.second
                                    c.homeX = r.first; c.homeY = r.second
                                }
                            }
                        }
                    }
                    "local" -> {
                        if (c.destX == c.homeX && c.destY == c.homeY) {
                            c.parked = true
                            c.cruise = 0f
                            continue
                        }
                    }
                }
            }
            if (c.kind == "local" && c.cruise < 0.04f &&
                c.destX == c.homeX && c.destY == c.homeY &&
                !isCrossroad(c.x, c.y)
            ) {
                c.wait += dt
                if (c.wait > 4f) {
                    c.parked = true
                    c.cruise = 0f
                    c.wait = 0f
                    continue
                }
            } else if (c.kind == "local" && c.cruise > 0.04f) {
                c.wait = 0f
            }
            val nx = c.x + dirs[c.dir][0]
            val ny = c.y + dirs[c.dir][1]
            val atLight = c.kind != "through" && isCrossroad(nx, ny) && c.prog > 0.52f && redNow &&
                World.tile(c.x, c.y)?.road != "highway"
            val lead = nearestAhead(c, moving)
            val gap = lead?.first ?: 8f
            val leadCruise = lead?.second?.cruise ?: c.maxSpeed
            val roadSpd = roadMax(c)
            var desired = roadSpd * rain
            if (atLight) desired = 0f
            else if (gap < 0.95f) {
                val t = ((gap - 0.42f) / 0.53f).coerceIn(0f, 1f)
                desired = min(desired, leadCruise * (0.55f + 0.45f * t) + t * 0.15f)
                if (gap < 0.48f) desired = min(desired, leadCruise * 0.92f)
                if (gap < 0.38f) desired = 0f
            }
            val k = if (desired < c.cruise) 3.2f else 1.6f
            c.cruise += (desired - c.cruise) * min(1f, dt * k)
            if (c.cruise < 0.04f) c.cruise = 0f
            if (c.cruise > 0f) {
                c.prog += c.cruise * dt * 0.62f
            }
            var guard = 0
            while (c.prog >= 1f && guard++ < 4) {
                c.prog -= 1f
                val v = dirs[c.dir]
                val tx = c.x + v[0]
                val ty = c.y + v[1]
                if (!isRoad(tx, ty, c.kind)) {
                    c.prog = 0.92f
                    turnToward(c)
                    break
                }
                c.x = tx; c.y = ty
                turnToward(c)
            }
            if (c.x in 1..w.cols && c.y in 1..w.rows) {
                roadFlow[(c.y - 1) * w.cols + (c.x - 1)] += 8
            }
        }
        if (gone.isNotEmpty()) cars.removeAll(gone.toSet())
        selected = selected?.takeIf { it in cars }
    }

    private fun nearestAhead(c: TrafficCar, moving: List<TrafficCar>): Pair<Float, TrafficCar>? {
        var bestGap = 9f
        var best: TrafficCar? = null
        for (o in moving) {
            if (o === c) continue
            if (o.dir != c.dir) continue
            val same = o.x == c.x && o.y == c.y && o.prog > c.prog
            val nx = c.x + dirs[c.dir][0]
            val ny = c.y + dirs[c.dir][1]
            val next = o.x == nx && o.y == ny
            if (!same && !next) continue
            val gap = if (same) o.prog - c.prog else (1f - c.prog) + o.prog
            if (gap in 0.02f..bestGap) {
                bestGap = gap
                best = o
            }
        }
        return if (best != null) bestGap to best else null
    }

    private fun roadMax(c: TrafficCar): Float {
        val kind = World.tile(c.x, c.y)?.road
        val base = when (kind) {
            "highway" -> 2.4f
            "avenue" -> 1.7f
            "local" -> 1.35f
            else -> 1.05f
        }
        return min(c.maxSpeed, base)
    }

    private fun turnToward(c: TrafficCar) {
        fun ok(d: Int): Boolean {
            val v = dirs[d]
            return isRoad(c.x + v[0], c.y + v[1], c.kind)
        }
        val rev = (c.dir + 2) % 4
        if (ok(c.dir) && Random.nextFloat() > 0.22f) {
            val opts = mutableListOf<Int>()
            for (d in 0..3) if (d != rev && d != c.dir && ok(d)) opts.add(d)
            if (opts.isNotEmpty() && Random.nextFloat() < 0.35f) {
                c.dir = opts.minByOrNull { d ->
                    abs(c.x + dirs[d][0] - c.destX) + abs(c.y + dirs[d][1] - c.destY)
                } ?: c.dir
            }
            return
        }
        if (!ok(c.dir)) {
            val opts = mutableListOf<Int>()
            for (d in 0..3) if (d != rev && ok(d)) opts.add(d)
            c.dir = when {
                opts.isNotEmpty() -> opts.minByOrNull { d ->
                    abs(c.x + dirs[d][0] - c.destX) + abs(c.y + dirs[d][1] - c.destY)
                } ?: opts.first()
                ok(rev) -> rev
                else -> c.dir
            }
        }
    }

    private fun isRoad(x: Int, y: Int, kind: String? = null): Boolean {
        val t = World.tile(x, y) ?: return false
        if (t.road == null) return false
        if (kind == "through") return t.road == "highway"
        return true
    }

    private fun pickDir(x: Int, y: Int, kind: String? = null): Int {
        val opts = mutableListOf<Int>()
        for (d in dirs.indices) {
            if (isRoad(x + dirs[d][0], y + dirs[d][1], kind)) opts.add(d)
        }
        return if (opts.isNotEmpty()) opts[Random.nextInt(opts.size)] else 0
    }

    private fun isCrossroad(x: Int, y: Int): Boolean {
        val t = World.tile(x, y) ?: return false
        if (t.road == null) return false
        val h = World.tile(x - 1, y)?.road != null || World.tile(x + 1, y)?.road != null
        val v = World.tile(x, y - 1)?.road != null || World.tile(x, y + 1)?.road != null
        return h && v
    }

    private fun decayFlow() {
        for (i in roadFlow.indices) if (roadFlow[i] > 0) roadFlow[i] = (roadFlow[i] * 0.88).toInt()
    }

    fun screenPos(c: TrafficCar): Pair<Float, Float> {
        val v = dirs[c.dir]
        return (c.x - 1 + v[0] * c.prog + 0.5f) to (c.y - 1 + v[1] * c.prog + 0.5f)
    }

    fun hitTest(wx: Float, wy: Float): TrafficCar? {
        var best: TrafficCar? = null
        var bestD = 0.55f
        for (c in cars) {
            if (c.parked) continue
            val (sx, sy) = screenPos(c)
            val d = abs(sx - wx) + abs(sy - wy)
            if (d < bestD) {
                bestD = d
                best = c
            }
        }
        return best
    }

    fun hitTrain(wx: Float, wy: Float): TrainCar? {
        var best: TrainCar? = null
        var bestD = 0.7f
        for (t in trains) {
            val sx = t.x - 1 + dirs[t.dir][0] * t.prog + 0.5f
            val sy = t.y - 1 + dirs[t.dir][1] * t.prog + 0.5f
            val d = abs(sx - wx) + abs(sy - wy)
            if (d < bestD) {
                bestD = d
                best = t
            }
        }
        return best
    }

    fun hitPlane(wx: Float, wy: Float): PlaneCraft? {
        var best: PlaneCraft? = null
        var bestD = 1.2f
        for (p in planes) {
            val d = abs(p.x - wx) + abs(p.y - wy)
            if (d < bestD) {
                bestD = d
                best = p
            }
        }
        return best
    }

    // ------------------------------------------------------------------
    // 火车：铺了铁轨并建了火车站才会跑
    // ------------------------------------------------------------------
    private fun syncTrains() {
        val stations = World.allBuildings().filter { it.b.service == "rail_station" }
        if (stations.isEmpty() || Networks.railCount < 6) {
            trains.clear()
            return
        }
        if (trains.isEmpty()) {
            val start = railCells().firstOrNull() ?: return
            trains.add(
                TrainCar(start.first, start.second, 0, 0.1f, 1.15f, "晨光号")
            )
            if (Networks.railCount > 18) {
                trains.add(
                    TrainCar(start.first, start.second, 2, 0.4f, 1.05f, "望江号")
                )
            }
        }
    }

    private fun railCells(): List<Pair<Int, Int>> {
        val w = World.current ?: return emptyList()
        val out = mutableListOf<Pair<Int, Int>>()
        for (y in 1..w.rows) for (x in 1..w.cols) {
            if (w.grid[y - 1][x - 1].rail) out.add(x to y)
        }
        return out
    }

    private fun isRail(x: Int, y: Int) = World.tile(x, y)?.rail == true

    private fun driveTrains(dt: Float) {
        for (t in trains) {
            if (!isRail(t.x, t.y)) {
                val cell = railCells().firstOrNull() ?: continue
                t.x = cell.first; t.y = cell.second
            }
            fun ok(d: Int): Boolean {
                val v = dirs[d]
                return isRail(t.x + v[0], t.y + v[1])
            }
            t.prog += t.speed * dt * 0.55f
            while (t.prog >= 1f) {
                t.prog -= 1f
                if (ok(t.dir)) {
                    t.x += dirs[t.dir][0]
                    t.y += dirs[t.dir][1]
                } else {
                    val rev = (t.dir + 2) % 4
                    val opts = (0..3).filter { it != rev && ok(it) }
                    t.dir = opts.firstOrNull() ?: if (ok(rev)) rev else t.dir
                    if (ok(t.dir)) {
                        t.x += dirs[t.dir][0]
                        t.y += dirs[t.dir][1]
                    }
                }
            }
        }
        selectedTrain = selectedTrain?.takeIf { it in trains }
    }

    // ------------------------------------------------------------------
    // 飞机：建了机场才会飞进飞出
    // ------------------------------------------------------------------
    private fun syncPlanes() {
        val air = World.allBuildings().filter { it.b.service == "airport" }
        if (air.isEmpty()) {
            planes.clear()
            return
        }
        val want = min(3, 1 + air.size)
        while (planes.size < want) {
            val a = air[planes.size % air.size]
            val ang = Random.nextFloat() * 6.28f
            planes.add(
                PlaneCraft(
                    x = a.x + 8f * cos(ang),
                    y = a.y + 8f * sin(ang),
                    vx = 0f, vy = 0f,
                    alt = 3.2f,
                    phase = Random.nextFloat() * 6.28f,
                    ax = a.x, ay = a.y,
                    flight = "CX" + (800 + Random.nextInt(199))
                )
            )
        }
        if (planes.size > want) {
            while (planes.size > want) planes.removeAt(planes.lastIndex)
        }
    }

    private fun flyPlanes(dt: Float) {
        for (p in planes) {
            p.phase += dt * 0.28f
            val r = 7.5f + 1.4f * sin(p.phase * 0.5f)
            val tx = p.ax + r * cos(p.phase)
            val ty = p.ay + r * sin(p.phase)
            p.vx = (tx - p.x) * 0.9f
            p.vy = (ty - p.y) * 0.9f
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.alt = 2.6f + 0.6f * sin(p.phase * 1.7f)
        }
        selectedPlane = selectedPlane?.takeIf { it in planes }
    }

    fun clearSelection() {
        selected = null
        selectedTrain = null
        selectedPlane = null
    }
}
