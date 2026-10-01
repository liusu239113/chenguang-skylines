package com.dshx.game.she

import com.dshx.game.she.world.BuildingEntry
import com.dshx.game.she.world.CitySystems
import com.dshx.game.she.world.World
import kotlin.math.max
import kotlin.random.Random

data class ExamQuestion(
    val q: String,
    val options: List<String>,
    val answer: Int,
    val explain: String
)

data class Complaint(
    val id: String,
    val from: String,
    val title: String,
    val body: String,
    val a: String,
    val b: String,
    val aHappy: Double,
    val aFunds: Double,
    val aEdu: Double = 0.0,
    val bHappy: Double,
    val bFunds: Double,
    val bEdu: Double = 0.0
)

object Civic {
    var examPassed: Int = 0
    var examCooldown: Int = 0
    var schoolRate: Double = 0.42
    var complaintsHandled: Int = 0
    /** 上书冷却（天）：处理完一封隔几天再来，避免天天弹窗 */
    var complaintCooldown: Int = 0
    /** 缺覆盖的容忍比例：超过这个比例才算"服务不到位" */
    private const val COVER_GAP_TOLERANCE = 0.15
    var pending: Complaint? = null
    var examSession: List<ExamQuestion> = emptyList()
    var examIndex: Int = 0
    var examScore: Int = 0
    var examActive: Boolean = false

    fun reset() {
        examPassed = 0
        examCooldown = 0
        schoolRate = 0.42
        complaintsHandled = 0
        complaintCooldown = 0
        pending = null
        examSession = emptyList()
        examIndex = 0
        examScore = 0
        examActive = false
    }

    fun tickDay(s: CityState) {
        if (examCooldown > 0) examCooldown -= 1
        val edu = s.education / 100.0
        val health = s.health / 100.0
        val happy = s.happiness / 100.0
        val target = 0.28 + edu * 0.42 + health * 0.12 + happy * 0.12 + (if (s.lastCoverage?.education ?: 0f > 0.4f) 0.08 else 0.0)
        schoolRate += (target.coerceIn(0.15, 0.96) - schoolRate) * 0.18
        s.merit += schoolRate * 1.2 + s.education * 0.01
        if (complaintCooldown > 0) complaintCooldown -= 1
        // 服务基本到位就别再弹上书了：人口太少不弹、刚处理过要冷却
        if (pending == null && complaintCooldown <= 0 && s.population >= 25 && Random.nextDouble() < 0.28) {
            val real = realComplaints()
            if (real.isNotEmpty()) {
                pending = real[Random.nextInt(real.size)]
                AppState.complaintOpen = true
            }
        }
    }


    /**
     * 缺覆盖的建筑占比。只有整体服务明显不到位才发上书，
     * 个别边缘地块没接到管线/马路不该天天弹窗。
     */
    private fun uncoveredRatio(list: List<BuildingEntry>, cat: String): Double {
        if (list.isEmpty()) return 0.0
        var bad = 0
        for (e in list) if (!World.isCoveredBy(e.x, e.y, cat)) bad++
        return bad.toDouble() / list.size
    }

    private fun realComplaints(): List<Complaint> {
        val homes = World.allBuildings().filter { !it.b.isService && it.b.zone == "residential" && !it.b.abandoned }
        val shops = World.allBuildings().filter { !it.b.isService && it.b.zone == "commercial" && !it.b.abandoned }
        val factories = World.allBuildings().filter { !it.b.isService && it.b.zone == "industrial" && !it.b.abandoned }
        val hasSchool = World.allBuildings().any {
            it.b.service == "school" || it.b.service == "middle_school" || it.b.service == "university"
        }
        val hasClinic = World.allBuildings().any { it.b.service == "clinic" || it.b.service == "hospital" }
        val out = mutableListOf<Complaint>()
        if (uncoveredRatio(homes, Config.ServiceCat.POWER) > COVER_GAP_TOLERANCE) {
            out.add(COMPLAINTS.first { it.id == "power" })
        }
        if (uncoveredRatio(homes, Config.ServiceCat.WATER) > COVER_GAP_TOLERANCE) {
            out.add(COMPLAINTS.first { it.id == "water" })
        }
        val pop = GameData.current?.population ?: 0.0
        fun unlocked(id: String): Boolean {
            val need = World.serviceConfig(id)?.unlockPop ?: 0
            return pop >= need
        }
        if (homes.isNotEmpty() && !hasSchool && unlocked("school")) out.add(COMPLAINTS.first { it.id == "school" })
        if (homes.isNotEmpty() && !hasClinic && unlocked("clinic")) out.add(COMPLAINTS.first { it.id == "clinic" })
        if (uncoveredRatio(homes, Config.ServiceCat.GARBAGE) > COVER_GAP_TOLERANCE && unlocked("landfill")) {
            out.add(COMPLAINTS.first { it.id == "trash" })
        }
        val hasFire = World.allBuildings().any { it.b.service == "fire_station" }
        val hasPolice = World.allBuildings().any { it.b.service == "police" }
        if (unlocked("fire_station") && !hasFire) out.add(COMPLAINTS.first { it.id == "fire" })
        if (unlocked("police") && !hasPolice) out.add(COMPLAINTS.first { it.id == "police" })
        if (uncoveredRatio(shops, Config.ServiceCat.POWER) > COVER_GAP_TOLERANCE) {
            out.add(COMPLAINTS.first { it.id == "shop" })
        }
        if (uncoveredRatio(factories, Config.ServiceCat.POWER) > COVER_GAP_TOLERANCE) {
            out.add(COMPLAINTS.first { it.id == "factory" })
        }
        // 噪音/秽气：污染设施贴民居太近，百姓上书
        if (CitySystems.noisyHomes >= 3 && unlocked("landfill")) {
            out.add(COMPLAINTS.first { it.id == "noise" })
        }
        if (CitySystems.noiseAvg >= 45.0) {
            out.add(COMPLAINTS.first { it.id == "foul" })
        }
        return out
    }

    fun canTakeExam(): Boolean {
        val s = GameData.current ?: return false
        if (examActive) return true
        if (examCooldown > 0) return false
        val next = Config.RANKS.firstOrNull { it.level == s.rankLevel + 1 } ?: return false
        if (examPassed >= next.level - 1) return false
        return s.population >= next.popReq * 0.85 && s.happiness >= next.happyReq - 4
    }

    fun startExam() {
        examSession = EXAMS.shuffled().take(5)
        examIndex = 0
        examScore = 0
        examActive = true
    }

    fun answer(i: Int): Boolean {
        val q = examSession.getOrNull(examIndex) ?: return false
        val ok = i == q.answer
        if (ok) examScore += 1
        examIndex += 1
        if (examIndex >= examSession.size) {
            finishExam()
        }
        return ok
    }

    private fun finishExam() {
        examActive = false
        examCooldown = 12
        val s = GameData.current ?: return
        if (examScore >= 4) {
            examPassed = max(examPassed, s.rankLevel)
            s.merit += 18
            GameData.pushNews("营造测评通过", "你以 $examScore/5 通过营造测评。人口和满意够了就会立刻升职。", "营造")
            GameData.refreshRank()
        } else {
            GameData.pushNews("营造测评未过", "本次 $examScore/5。可在冷却后重考。", "营造")
        }
    }

    fun resolve(chooseA: Boolean) {
        val c = pending ?: return
        val s = GameData.current ?: return
        if (chooseA) {
            s.happiness = (s.happiness + c.aHappy).coerceIn(20.0, 100.0)
            s.funds += c.aFunds
            s.education = (s.education + c.aEdu).coerceIn(0.0, 100.0)
        } else {
            s.happiness = (s.happiness + c.bHappy).coerceIn(20.0, 100.0)
            s.funds += c.bFunds
            s.education = (s.education + c.bEdu).coerceIn(0.0, 100.0)
        }
        complaintsHandled += 1
        complaintCooldown = 5
        pending = null
        AppState.complaintOpen = false
        GameData.pushNews("百姓上书已处理", c.title + " · 已给出营造答复。", "上书")
        AppState.bumpLive()
    }

    val EXAMS: List<ExamQuestion> = listOf(
        ExamQuestion("街巷旁民居不盖屋，首先检查什么？", listOf("把赋税加到最高", "坊界是否邻街且已通电通水", "立刻推平整片地", "关掉所有车马"), 1, "邻街 + 供电供水是入住前提。"),
        ExamQuestion("水车坊和水塔靠什么供水？", listOf("必须贴着河岸", "抽取地下水，建在覆盖区内即可", "必须建在山顶", "只能建在公园里"), 1, "水车坊抽的是地下水，不必靠河。"),
        ExamQuestion("工坊噪音和浊气偏高时优先？", listOf("把民居紧贴工坊", "用林木带和坊界把民居与工坊分开", "关掉全部工坊", "提高市肆赋税"), 1, "空间隔离比硬关坊更稳。"),
        ExamQuestion("拥堵上升但人口没变，常见原因是？", listOf("道路等级不够或断头路", "天气不好", "日期显示错误", "简报字体太小"), 0, "道路容量和连通性决定拥堵。"),
        ExamQuestion("民心长期偏低，不该优先做的是？", listOf("补医署学塾覆盖", "无脑加赋税到上限", "处理积压上书", "减少浊气"), 1, "加赋税会继续压民心。"),
        ExamQuestion("识字率主要跟哪项城政投入有关？", listOf("码头通商", "学塾覆盖与学塾用度", "官道长度", "义庄数量"), 1, "学塾和供养用度拉动识字。"),
        ExamQuestion("炭窑坊和水楼怎么给分区送电送水？", listOf("只看覆盖半径", "只产能，靠道路预埋管线/地下管缆接到建筑", "替代道路", "只给公园用"), 1, "厂站只负责产能，接不接得上是管线的事；容量不足全城打折。"),
        ExamQuestion("废弃建筑最常见原因？", listOf("楼名不好听", "长期断电缺水或缺岗", "日期是双数", "相机拉太远"), 1, "服务中断过久会弃楼。"),
        ExamQuestion("办公区主要吸收哪类需求？", listOf("工业骡车", "白领岗位与办公需求", "农田", "墓园排队"), 1, "办公区分担商办岗位。"),
        ExamQuestion("处理百姓上书的目标是？", listOf("全部无视", "在耗费与民心之间做取舍", "每次都借官贷", "拆掉学塾"), 1, "上书是日常营造取舍，与真实政务无关。")
    )

    val COMPLAINTS: List<Complaint> = listOf(
        Complaint("power", "梧桐里住户", "家中无电", "里巷未接灵线：要么附近无街，要么灵线没连到风车坊。", "牵灵线接入（-80万，民心+4）", "先发应急灯（民心+1）", 4.0, -80.0, 0.0, 1.0, -10.0, 0.0),
        Complaint("water", "望江里住户", "水缸见底", "里巷未接水渠：街巷没通到，或水楼还没建。", "铺水渠接入（-90万，民心+4）", "先送水车（民心+1）", 4.0, -90.0, 0.0, 1.0, -15.0, 0.0),
        Complaint("school", "晨曦里父老", "附近没有蒙学", "孩童无处开蒙，父老很是着急。", "答应设蒙学（民心+3）", "先请塾师走教（-40万，民心+1）", 3.0, 0.0, 2.0, 1.0, -40.0, 0.0),
        Complaint("clinic", "坊间百姓", "求医要跑很远", "附近没有医馆，发热都无处诊治。", "答应设医馆（民心+3）", "先设巡诊点（-50万，民心+1）", 3.0, 0.0, 0.0, 1.0, -50.0, 0.0),
        Complaint("trash", "沿街住户", "秽物堆到门口", "清运不周，秽物堆在街边。", "加派清运（-70万，民心+3）", "先发秽桶（民心+1）", 3.0, -70.0, 0.0, 1.0, -10.0, 0.0),
        Complaint("shop", "金源绸庄", "铺面点不了灯", "市肆这一片还没接进灵线，客人不肯进门。", "牵灵线接入（-60万，民心+2）", "先免一日商赋（赋税略降）", 2.0, -60.0, 0.0, 1.0, -25.0, 0.0),
        Complaint("factory", "联华织坊", "织机停转", "坊区没接进灵线，工匠没法开工。", "牵灵线接入（-80万）", "先歇工一日（民心-1）", 1.0, -80.0, 0.0, -1.0, 0.0, 0.0),
        Complaint("fire", "沿街住户", "附近没有水龙局", "城中已现火情，可人口够建水龙局了。", "答应设水龙局（民心+3）", "先发水桶（-40万，民心+1）", 3.0, 0.0, 0.0, 1.0, -40.0, 0.0),
        Complaint("police", "坊间百姓", "夜里不太安心", "人口到了，该设捕房了，如今街上没有巡夜。", "答应设捕房（民心+3）", "先加巡夜灯（-50万，民心+1）", 3.0, 0.0, 0.0, 1.0, -50.0, 0.0),
        Complaint("noise", "临街住户", "夜里吵得睡不着", "秽物场/作坊/炭窑坊离民居太近，机器和清运昼夜不停，窗户都不敢开。", "把污秽设施挪远/加隔音林（民心+3）", "先发隔音窗钱（-60万，民心+1）", 3.0, 0.0, 0.0, 1.0, -60.0, 0.0),
        Complaint("foul", "河畔住户", "秽气熏天", "净水渠/秽物场离民居太近，风一吹全是味道。", "迁走污秽设施（民心+4）", "先做除秽（-70万，民心+1）", 4.0, 0.0, 0.0, 1.0, -70.0, 0.0)
    )
}
