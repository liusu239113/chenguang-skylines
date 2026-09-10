package com.dshx.game.she

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
        if (pending == null && Random.nextDouble() < 0.28) {
            val real = realComplaints()
            if (real.isNotEmpty()) {
                pending = real[Random.nextInt(real.size)]
                AppState.complaintOpen = true
            }
        }
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
        if (homes.any { !World.isCoveredBy(it.x, it.y, Config.ServiceCat.POWER) }) {
            out.add(COMPLAINTS.first { it.id == "power" })
        }
        if (homes.any { !World.isCoveredBy(it.x, it.y, Config.ServiceCat.WATER) }) {
            out.add(COMPLAINTS.first { it.id == "water" })
        }
        val pop = GameData.current?.population ?: 0.0
        if (homes.isNotEmpty() && !hasSchool && pop >= 20) out.add(COMPLAINTS.first { it.id == "school" })
        if (homes.isNotEmpty() && !hasClinic && pop >= 25) out.add(COMPLAINTS.first { it.id == "clinic" })
        if (homes.any { !World.isCoveredBy(it.x, it.y, Config.ServiceCat.GARBAGE) } && pop >= 40) {
            out.add(COMPLAINTS.first { it.id == "trash" })
        }
        if (shops.any { !World.isCoveredBy(it.x, it.y, Config.ServiceCat.POWER) }) {
            out.add(COMPLAINTS.first { it.id == "shop" })
        }
        if (factories.any { !World.isCoveredBy(it.x, it.y, Config.ServiceCat.POWER) }) {
            out.add(COMPLAINTS.first { it.id == "factory" })
        }
        return out
    }

    fun canTakeExam(): Boolean {
        val s = GameData.current ?: return false
        if (examActive) return true
        if (examCooldown > 0) return false
        val next = Config.RANKS.firstOrNull { it.level == s.rankLevel + 1 } ?: return false
        return s.population >= next.popReq * 0.6 && s.happiness >= next.happyReq - 8
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
            GameData.pushNews("营造测评通过", "你以 $examScore/5 通过营造测评，晋升通道已打开。", "营造")
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
        pending = null
        AppState.complaintOpen = false
        GameData.pushNews("市民来信已处理", c.title + " · 已给出营造答复。", "来信")
        AppState.bumpLive()
    }

    val EXAMS: List<ExamQuestion> = listOf(
        ExamQuestion("路旁住宅不长楼，首先检查什么？", listOf("把税率加到最高", "分区是否邻路且已通电通水", "立刻推平整片地", "关掉所有公交"), 1, "邻路 + 供电供水是入住前提。"),
        ExamQuestion("抽水站应该靠近哪里？", listOf("山顶", "河流或水源", "高速公路中央", "公园草坪"), 1, "抽水站必须靠水。"),
        ExamQuestion("工业区噪音和污染偏高时优先？", listOf("把住宅紧贴工厂", "用绿化带和区划把住工分开", "关掉全部工厂", "提高商业税"), 1, "空间隔离比硬关厂更稳。"),
        ExamQuestion("拥堵上升但人口没变，常见原因是？", listOf("道路等级不够或断头路", "天气不好", "日期显示错误", "简报字体太小"), 0, "道路容量和连通性决定拥堵。"),
        ExamQuestion("满意度长期偏低，不该优先做的是？", listOf("补医疗教育覆盖", "无脑加税到上限", "处理积压投诉", "降低污染"), 1, "加税会继续压满意度。"),
        ExamQuestion("升学率主要跟哪项市政投入有关？", listOf("港口贸易", "学校覆盖与教育预算", "高速路长度", "墓园数量"), 1, "学校和服务预算拉动升学。"),
        ExamQuestion("电缆和水管的作用是？", listOf("装饰用", "把电厂/水厂能力接到分区", "替代道路", "只给公园用"), 1, "管网把供给送到格子。"),
        ExamQuestion("废弃建筑最常见原因？", listOf("楼名不好听", "长期断电缺水或缺岗", "日期是双数", "相机拉太远"), 1, "服务中断过久会弃楼。"),
        ExamQuestion("办公区主要吸收哪类需求？", listOf("工业货车", "白领岗位与办公需求", "农田", "墓园排队"), 1, "办公区分担商办岗位。"),
        ExamQuestion("处理市民来信的目标是？", listOf("全部无视", "在成本和满意度之间做取舍", "每次都发贷款", "拆掉学校"), 1, "来信是日常城建取舍，不是现实政治。")
    )

    val COMPLAINTS: List<Complaint> = listOf(
        Complaint("power", "梧桐小区业主", "家里没电", "住宅还没接上电缆，晚上黑灯瞎火。", "立刻铺电缆（-80万，满意+4）", "先发应急灯（满意+1）", 4.0, -80.0, 0.0, 1.0, -10.0, 0.0),
        Complaint("water", "望江里居民", "水龙头没水", "水管没接到小区，做饭洗衣都难。", "铺设水管（-90万，满意+4）", "先送桶装水（满意+1）", 4.0, -90.0, 0.0, 1.0, -15.0, 0.0),
        Complaint("school", "晨曦家长会", "附近没有学校", "孩子没处上学，家长很着急。", "答应规划小学（满意+3）", "先发接送补贴（-40万，满意+1）", 3.0, 0.0, 2.0, 1.0, -40.0, 0.0),
        Complaint("clinic", "社区居民", "看病要跑很远", "附近没有诊所，发烧都没处看。", "答应规划诊所（满意+3）", "先设巡诊点（-50万，满意+1）", 3.0, 0.0, 0.0, 1.0, -50.0, 0.0),
        Complaint("trash", "沿街住户", "垃圾堆到门口", "清运覆盖不到，生活垃圾堆路边。", "加开清运（-70万，满意+3）", "先发垃圾袋（满意+1）", 3.0, -70.0, 0.0, 1.0, -10.0, 0.0),
        Complaint("shop", "金源超市", "店里没电开不了门", "商业区断电，客人进不来。", "给商铺接电（-60万，满意+2）", "先减一天商税（税收略降）", 2.0, -60.0, 0.0, 1.0, -25.0, 0.0),
        Complaint("factory", "联华车间", "车间没电停工", "工业区断电，工人没法开工。", "给厂房接电（-80万）", "先放假一天（满意-1）", 1.0, -80.0, 0.0, -1.0, 0.0, 0.0)
    )
}
