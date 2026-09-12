package com.dshx.game.she.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.dshx.game.she.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import com.dshx.game.she.Ads
import com.dshx.game.she.AppState
import com.dshx.game.she.Civic
import com.dshx.game.she.Config
import com.dshx.game.she.GameData
import com.dshx.game.she.MapRef
import com.dshx.game.she.SaveManager
import com.dshx.game.she.Sfx
import com.dshx.game.she.SpeedBoost
import com.dshx.game.she.ui.UIHelper
import com.dshx.game.she.ui.toColor
import com.dshx.game.she.ui.theme.LocalGameFont
import com.dshx.game.she.world.Citizens
import com.dshx.game.she.world.CitySystems
import com.dshx.game.she.world.Growth
import com.dshx.game.she.world.MapRenderView
import com.dshx.game.she.world.Networks
import com.dshx.game.she.world.Tool
import com.dshx.game.she.world.Traffic
import com.dshx.game.she.world.Transit
import com.dshx.game.she.world.World
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

// ============================================================================
// MapScreen — 主界面，与 scripts/Screens/MapScreen.lua 1:1 对应
//   顶部：资金/人口/满意/日期 + RCI 需求条 + 时间速度 + 简报
//   底部：道路 | 住宅区 | 商业区 | 工业区 | 推平 | 服务 | 查看
// ============================================================================

object MapScreen {

    private var uiTimer = 0f

    fun syncTool() {
        val t = when (AppState.mode) {
            "road" -> Tool("road", roadKind = AppState.roadKind)
            "zone" -> Tool("zone", zoneKey = AppState.zoneKey)
            "bulldoze" -> Tool("bulldoze")
            "service" -> AppState.selService?.let { Tool("service", id = it) }
            "bus" -> Tool("bus")
            "district" -> Tool("district")
            "tree" -> Tool("tree")
            "raise" -> Tool("raise")
            "lower" -> Tool("lower")
            else -> null
        }
        MapRef.view?.tool = t
        AppState.bumpMap()
    }

    fun selectMode(m: String) {
        Sfx.play("sfx_click")
        if (m == "service") {
            AppState.mode = "service"
            AppState.serviceOpen = true
            AppState.roadOpen = false
            AppState.planOpen = false
        } else if (m == "road") {
            AppState.mode = "road"
            AppState.roadOpen = true
            AppState.serviceOpen = false
            AppState.planOpen = false
        } else if (m == "plan") {
            AppState.planOpen = !AppState.planOpen
            AppState.serviceOpen = false
            AppState.roadOpen = false
        } else if (AppState.mode == m && m != "zone") {
            AppState.mode = "view"
            AppState.serviceOpen = false
            AppState.roadOpen = false
            AppState.planOpen = false
        } else {
            AppState.mode = m
            AppState.serviceOpen = false
            AppState.roadOpen = false
            AppState.planOpen = false
        }
        syncTool()
    }

    fun setZoneKey(zk: String) {
        Sfx.play("sfx_click", 0.6f)
        if (AppState.zoneKey != zk) GameData.clearZoneDraft()
        AppState.zoneKey = zk
        AppState.mode = "zone"
        AppState.serviceOpen = false
        AppState.planOpen = false
        syncTool()
    }

    fun cancelTool() {
        GameData.clearZoneDraft()
        GameData.clearServiceDraft()
        AppState.mode = "view"
        AppState.serviceOpen = false
        AppState.roadOpen = false
        AppState.planOpen = false
        syncTool()
    }

    fun onShow(view: MapRenderView, wDp: Float, hDp: Float) {
        view.setViewport(wDp, hDp, 108f, 96f)
        view.onTileChanged = {
            AppState.bumpMap()
            AppState.bumpLive()
        }
        if (!AppState.tutShown) {
            AppState.helpOpen = true
            AppState.tutShown = true
        }
        syncTool()
        AppState.bumpLive()
        AppState.bumpMap()
    }

    fun tick(dt: Float, view: MapRenderView?) {
        uiTimer += dt
        if (uiTimer >= 0.5f) {
            uiTimer = 0f
            if (AppState.screen == "map") AppState.bumpLive()
        }
        if (GameData.pendingLevelUp) {
            GameData.pendingLevelUp = false
            Sfx.play("sfx_levelup")
            view?.setToast("城市晋级 " + World.cityLevel().name + "！")
        } else if (GameData.monthFlash) {
            GameData.monthFlash = false
            Sfx.play("sfx_month")
            view?.setToast(GameData.monthLabel() + " 月度结算完成")
            SaveManager.save(AppState.activeSlot)   // 每月自动存档
            AppState.saveTick++
        }
    }

    fun goNewspaper() {
        Sfx.play("sfx_click")
        AppState.screen = "newspaper"
    }
}

@Composable
fun MapScreenContent(mapView: MapRenderView) {
    val C = Config.COLORS
    val live = AppState.liveTick
    val version = AppState.mapVersion
    val s = GameData.current
    mapView.overlay = AppState.overlay

    Box(modifier = Modifier.fillMaxSize()) {

        // ---------------- 顶部资源条 ----------------
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 36.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth()
                .noRippleClickable { }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadowCard(18.dp, C.panelWhite.toColor())
                    .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        (s?.cityName ?: "晨光市") + " · " + (s?.mayorName ?: "未署名") + " · " + GameData.rankDef().name,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                Sfx.play("sfx_click")
                                AppState.civicOpen = true
                            }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            GameData.dateLabel() + "  " + GameData.clockLabel(),
                            fontSize = 11.sp, color = C.textMid.toColor(),
                            fontFamily = LocalGameFont.current
                        )
                        Box(
                            modifier = Modifier
                                .background(C.accentSoftBg.toColor(), RoundedCornerShape(10.dp))
                                .clickable { MapScreen.goNewspaper() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("简报", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.accentRed.toColor(), fontFamily = LocalGameFont.current)
                        }
                    }
                }
                if (s != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HudStat("资金", UIHelper.fmtFunds(s.funds), C.accentGold.toColor())
                        HudStat("人口", UIHelper.fmtPop(floor(s.population).toInt()), C.textDark.toColor())
                        Box(modifier = Modifier.clickable {
                            Sfx.play("sfx_click", 0.4f)
                            AppState.happyOpen = true
                        }) {
                            HudStat(
                                "满意", floor(s.happiness).toInt().toString(),
                                if (s.happiness >= 55) C.accentGreen.toColor() else C.accentRed.toColor()
                            )
                        }
                        HudStat(
                            "繁荣",
                            (World.current?.prosperity ?: 0).toString(),
                            C.accentBlue.toColor()
                        )
                    }
                    val need = World.nextUnlockPop()
                    val have = s.population.toInt()
                    val left = (need - have).coerceAtLeast(0)
                    Text(
                        if (left <= 0) "城区已尽量向外展开"
                        else "黑色区域：再增加 $left 人自动解锁（目标 $need 人）",
                        fontSize = 10.sp,
                        color = C.textMid.toColor(),
                        fontFamily = LocalGameFont.current,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // 行2：RCI 需求条 + 速度控制
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // RCI 需求：条越长=当前越缺这类分区，点开看详情
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(C.panelWhite.toColor(), RoundedCornerShape(14.dp))
                        .clickable {
                            Sfx.play("sfx_click", 0.4f)
                            AppState.demandOpen = true
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DemandBar("住", Growth.lastDemand.r, C.accentGreen.toColor(), C.accentGreen.toColor(), live)
                    DemandBar("商", Growth.lastDemand.c, C.accentBlue.toColor(), C.accentBlue.toColor(), live)
                    DemandBar("工", Growth.lastDemand.i, C.accentGold.toColor(), C.accentGold.toColor(), live)
                    DemandBar("办", Growth.lastDemand.o, C.accentBlue.toColor(), C.accentBlue.toColor(), live)
                }
                // 速度
                Row(
                    modifier = Modifier
                        .background(C.panelWhite.toColor(), RoundedCornerShape(14.dp))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val speeds = listOf("‖" to 0, "1x" to 1, "2x" to 2, "3x" to 3)
                    val act = LocalContext.current as? Activity
                    for (sp in speeds) {
                        val pausedNow = AppState.paused || GameData.speedIdx == 0
                        val active = if (sp.second == 0) pausedNow else (!AppState.paused && GameData.speedIdx == sp.second)
                        val locked = sp.second >= 2 && !SpeedBoost.isActive()
                        Box(
                            modifier = Modifier
                                .width(if (sp.second == 0) 30.dp else 36.dp)
                                .height(26.dp)
                                .background(
                                    if (active) C.accentSoftBg.toColor() else Color.Transparent,
                                    RoundedCornerShape(9.dp)
                                )
                                .clickable {
                                    Sfx.play("sfx_click", 0.5f)
                                    if (sp.second == 0) {
                                        AppState.paused = true
                                        GameData.setSpeed(0)
                                        MapRef.view?.setToast("已暂停时间，仍可划区修路")
                                        AppState.bumpLive()
                                    } else if (locked) {
                                        if (act != null) {
                                            Ads.reward(act, {
                                                SpeedBoost.activate()
                                                AppState.paused = false
                                                GameData.setSpeed(sp.second)
                                                MapRef.view?.setToast("加速已解锁 20 分钟")
                                                AppState.bumpLive()
                                            })
                                        }
                                    } else {
                                        AppState.paused = false
                                        GameData.setSpeed(sp.second)
                                        AppState.bumpLive()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (locked) sp.first + "锁" else sp.first,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = if (active) C.accentRed.toColor() else C.textMid.toColor(),
                                fontFamily = LocalGameFont.current
                            )
                        }
                    }
                }
            }
        }

        val overlayOpen = AppState.policyOpen || AppState.helpOpen || AppState.dataOpen || AppState.menuOpen ||
            AppState.settingsOpen || AppState.civicOpen || AppState.complaintOpen || AppState.achievementOpen ||
            AppState.adOfferOpen || AppState.happyOpen || AppState.demandOpen || AppState.bankOpen || AppState.ledgerOpen
        if (!overlayOpen) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 10.dp, top = 168.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UIHelper.RoundButton("数", size = 40.dp, fontSize = 16.sp) {
                    Sfx.play("sfx_click", 0.6f)
                    AppState.dataOpen = !AppState.dataOpen
                }
                UIHelper.RoundButton("?", size = 40.dp, fontSize = 20.sp) {
                    Sfx.play("sfx_click", 0.6f)
                    AppState.helpOpen = !AppState.helpOpen
                }
                UIHelper.RoundButton("策", size = 40.dp, fontSize = 15.sp) {
                    Sfx.play("sfx_click", 0.6f)
                    AppState.policyOpen = !AppState.policyOpen
                }
                UIHelper.RoundButton("银", size = 40.dp, fontSize = 15.sp) {
                    Sfx.play("sfx_click", 0.6f)
                    AppState.bankOpen = !AppState.bankOpen
                }
                UIHelper.RoundButton("账", size = 40.dp, fontSize = 15.sp) {
                    Sfx.play("sfx_click", 0.6f)
                    AppState.ledgerOpen = !AppState.ledgerOpen
                }
            }
        }

        val showMenuBtn = !overlayOpen && !(AppState.mode == "view" && mapView.selectedX > 0)
        if (showMenuBtn) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 168.dp, end = 12.dp)
            ) {
                UIHelper.RoundButton("≡", size = 40.dp, fontSize = 20.sp) {
                    Sfx.play("sfx_click", 0.6f)
                    AppState.menuOpen = !AppState.menuOpen
                }
            }
        }

        // ---------------- 信息卡（查看模式） ----------------
        if (AppState.mode == "view" && mapView.selectedX > 0 && version >= 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp, start = 10.dp, end = 10.dp)
                    .fillMaxWidth()
                    .noRippleClickable { }
            ) {
                UIHelper.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    radius = 16.dp,
                    paddingTop = 8.dp,
                    paddingBottom = 10.dp,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "× 关闭",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .clickable {
                                    Sfx.play("sfx_click", 0.5f)
                                    mapView.clearSelection()
                                    AppState.bumpMap()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    val sel = mapView.selectedX to mapView.selectedY
                    val car = Traffic.selected
                    val train = Traffic.selectedTrain
                    val plane = Traffic.selectedPlane
                    val svcCar = CitySystems.selected
                    when {
                        svcCar != null -> {
                            Text(
                                CitySystems.label(svcCar.kind),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                            )
                            UIHelper.InfoRow("职务", CitySystems.job(svcCar.kind), C.accentGold.toColor())
                            UIHelper.InfoRow("任务", "前往 (" + svcCar.destX + "," + svcCar.destY + ")")
                            UIHelper.InfoRow("身份", "市政公务车 · 可点查看")
                        }
                        car != null -> {
                            val d = car.driver
                            Text(
                                d.name + " · " + d.carType,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                            )
                            UIHelper.InfoRow("车牌", d.plate, C.accentBlue.toColor())
                            UIHelper.InfoRow("来自", d.from + " · " + d.homeName)
                            UIHelper.InfoRow("年龄", d.age.toString() + " 岁")
                            UIHelper.InfoRow("学历", d.education)
                            UIHelper.InfoRow("职业", d.job, C.accentGold.toColor())
                            UIHelper.InfoRow("上班", d.workplace)
                            UIHelper.InfoRow(
                                "身份",
                                when (car.kind) {
                                    "visitor" -> "外地游客（高速接入）"
                                    "freight" -> "城际货运"
                                    "through" -> "外环过路车"
                                    else -> "本市住户 · 一户一车"
                                }
                            )
                        }
                        train != null -> {
                            Text(
                                train.name + " 列车",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                            )
                            UIHelper.InfoRow("类型", "城际列车")
                            UIHelper.InfoRow("铁轨", Networks.railCount.toString() + " 格")
                            UIHelper.InfoRow("说明", "火车站 + 铁轨才会发车")
                        }
                        plane != null -> {
                            Text(
                                plane.flight + " 航班",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                            )
                            UIHelper.InfoRow("类型", "民航客机")
                            UIHelper.InfoRow("起降", "机场上空盘旋进出")
                            UIHelper.InfoRow("说明", "建机场后才会有飞机")
                        }
                        else -> {
                    Text(
                        "(" + sel.first + ", " + sel.second + ")  " + GameData.dateLabel(),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                    )
                    UIHelper.InfoRow("海拔", World.elevation(sel.first, sel.second).toString() + "m")
                    UIHelper.InfoRow("地形", World.terrainName(sel.first, sel.second))
                    World.roadNameAt(sel.first, sel.second)?.let { UIHelper.InfoRow("路名", it, C.accentGold.toColor()) }
                    UIHelper.InfoRow("现状", World.zoneName(sel.first, sel.second), C.accentBlue.toColor())
                    UIHelper.InfoRow("地价", World.landValue(sel.first, sel.second).toString())
                    UIHelper.InfoRow("噪音", World.noiseAt(sel.first, sel.second).toString())
                    val tb = World.tile(sel.first, sel.second)?.building
                    if (tb != null && !tb.isService) {
                        val cap = tb.cap()
                        val name = Traffic.grownName(tb.zone ?: "residential", sel.first, sel.second)
                        UIHelper.InfoRow("名称", name, C.accentGold.toColor())
                        if (tb.abandoned) {
                            UIHelper.InfoRow("状态", "废弃", C.accentRed.toColor())
                        } else if (tb.zone == "residential") {
                            UIHelper.InfoRow("入住", tb.residents.toString() + "/" + cap + " 人")
                            val folks = Traffic.cars.filter { it.kind == "local" && it.houseKey == "${sel.first},${sel.second}" }
                            if (folks.isNotEmpty()) {
                                val d = folks.first().driver
                                UIHelper.InfoRow("住户", d.name + " " + d.age + "岁")
                                UIHelper.InfoRow("职业", d.job + " · " + d.workplace)
                                UIHelper.InfoRow("车牌", d.plate)
                            } else if (tb.residents > 0) {
                                UIHelper.InfoRow("住户", "有人在家，车停在车库")
                            }
                        } else {
                            UIHelper.InfoRow("在岗", tb.workers.toString() + "/" + cap)
                            val staff = Traffic.cars.filter { it.kind == "local" && it.driver.workX == sel.first && it.driver.workY == sel.second }
                            if (staff.isNotEmpty()) {
                                UIHelper.InfoRow("店员/员工", staff.take(2).joinToString("、") { it.driver.name })
                            }
                        }
                        val popNow = GameData.current?.population?.toInt() ?: 0
                        fun unlocked(id: String) = popNow >= (World.serviceConfig(id)?.unlockPop ?: 0)
                        coverInfo(sel.first, sel.second, Config.ServiceCat.POWER, "供电")
                        coverInfo(sel.first, sel.second, Config.ServiceCat.WATER, "供水")
                        if (unlocked("landfill")) coverInfo(sel.first, sel.second, Config.ServiceCat.GARBAGE, "垃圾")
                        if (unlocked("clinic")) coverInfo(sel.first, sel.second, Config.ServiceCat.HEALTH, "医疗")
                        if (unlocked("school")) coverInfo(sel.first, sel.second, Config.ServiceCat.EDUCATION, "教育")
                        if (unlocked("fire_station") || unlocked("police")) {
                            coverInfo(sel.first, sel.second, Config.ServiceCat.SAFETY, "治安消防")
                        }
                        if (tb.zone == "office") {
                            val eduOk = (GameData.current?.education ?: 0.0) >= 28.0 || Civic.schoolRate >= 0.48
                            UIHelper.InfoRow(
                                "办公入职",
                                if (eduOk) "有中学以上学历的居民才能进写字楼" else "学历不够，白领进不来，先建小学/中学",
                                if (eduOk) C.accentGreen.toColor() else C.accentRed.toColor()
                            )
                        }
                    } else if (tb != null && tb.isService) {
                        val cfg = World.serviceConfig(tb.service)
                        UIHelper.InfoRow("设施", cfg?.name ?: tb.service ?: "-")
                        UIHelper.InfoRow("说明", cfg?.desc ?: "")
                        if (tb.service == "clinic") UIHelper.InfoRow("救护车", "2 辆 · 有人病了会出车")
                        if (tb.service == "hospital") UIHelper.InfoRow("救护车", "5 辆")
                        if (tb.service == "crematorium" || tb.service == "cemetery") UIHelper.InfoRow("灵车", "有人去世会出车接人")
                        if (cfg != null) {
                            UIHelper.InfoRow("覆盖半径", World.coverRadius(cfg).toString() + " 格")
                            if (cfg.powerCap > 0) {
                                val s0 = GameData.current
                                UIHelper.InfoRow(
                                    "发电容量",
                                    cfg.powerCap.toString() + "（全城 " + (s0?.powerCap ?: 0) + "/" + (s0?.powerNeed ?: 0) + "）"
                                )
                            }
                            if (cfg.waterCap > 0) {
                                val s0 = GameData.current
                                UIHelper.InfoRow(
                                    "供水容量",
                                    cfg.waterCap.toString() + "（全城 " + (s0?.waterCap ?: 0) + "/" + (s0?.waterNeed ?: 0) + "）"
                                )
                            }
                        }
                    }
                    val tile = World.tile(sel.first, sel.second)
                    if (tile?.metro == true) UIHelper.InfoRow("地铁隧", "已挖")
                    if (tile?.rail == true) UIHelper.InfoRow("铁轨", "已铺")
                    Networks.districtAt(sel.first, sel.second)?.let { d ->
                        UIHelper.InfoRow("区划", d.name + " · " + Networks.policyName(d.policy))
                    }
                    if (!World.isUnlocked(sel.first, sel.second) && World.tile(sel.first, sel.second)?.road != "highway") {
                        UIHelper.InfoRow("解锁", World.lockedHint(), C.accentRed.toColor())
                    }
                    if (World.current?.highwayConnected == true) {
                        UIHelper.InfoRow("外环高速", "已接通 · 繁荣 " + (World.current?.prosperity ?: 0))
                    } else if (World.tile(sel.first, sel.second)?.road == "highway") {
                        UIHelper.InfoRow("外环高速", "未接通城区，外地车进不来", C.accentRed.toColor())
                    }
                        }
                    }
                    }
                }
            }
        }

        // ---------------- 抽屉（服务 / 道路 / 规划） ----------------
        if ((AppState.mode == "service" && AppState.serviceOpen) ||
            (AppState.mode == "road" && AppState.roadOpen) ||
            AppState.planOpen
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 10.dp, end = 10.dp)
                    .fillMaxWidth()
                    .noRippleClickable { }
            ) {
                DrawerContent(mapView)
            }
        }

        // ---------------- 覆盖热力图提示条 ----------------
        if (AppState.overlay.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 176.dp)
                    .background(C.panelWhite.toColor(), RoundedCornerShape(16.dp))
                    .noRippleClickable { }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column {
                    Text(
                        "覆盖图 · " + overlayLabel(AppState.overlay),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current
                    )
                    Text(
                        overlayHint(AppState.overlay),
                        fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                    )
                }
                Box(
                    modifier = Modifier
                        .background(C.accentRed.toColor(), RoundedCornerShape(10.dp))
                        .clickable { AppState.overlay = "" }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "✕", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, fontFamily = LocalGameFont.current
                    )
                }
            }
        }

        // ---------------- 进行中事件提示条 ----------------
        if (s != null && s.activeEvents.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 214.dp)
                    .noRippleClickable { },
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (ev in s.activeEvents) {
                    Row(
                        modifier = Modifier
                            .background(C.accentSoftBg.toColor(), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⚠ " + ev.name + " · 剩" + ev.daysLeft + "天",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = C.accentRed.toColor(), fontFamily = LocalGameFont.current
                        )
                    }
                }
            }
        }

        // ---------------- 底部工具栏 ----------------
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth()
                .shadowCard(22.dp, C.panelWhite.toColor())
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .noRippleClickable { },
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Triple("road", "道路", null as String?),
                Triple("zone", "住宅", "residential"),
                Triple("zone", "商业", "commercial"),
                Triple("zone", "工业", "industrial"),
                Triple("zone", "办公", "office"),
                Triple("bulldoze", "推平", null),
                Triple("service", "服务", null),
                Triple("plan", "规划", null)
            )
            for (it in items) {
                val active = when {
                    it.first == "zone" -> AppState.mode == "zone" && AppState.zoneKey == it.third
                    it.first == "plan" -> AppState.planOpen ||
                        AppState.mode in listOf("bus", "district", "tree", "raise", "lower")
                    else -> AppState.mode == it.first
                }
                UIHelper.ToolItem(it.second, active, width = 40.dp) {
                    if (it.first == "zone") {
                        if (AppState.mode == "zone" && AppState.zoneKey == it.third) {
                            MapScreen.selectMode("view")
                        } else {
                            MapScreen.setZoneKey(it.third!!)
                        }
                    } else {
                        MapScreen.selectMode(it.first)
                    }
                }
            }
        }

        val showZoneConfirm = AppState.mode == "zone" && GameData.zoneDraft.isNotEmpty() && !overlayOpen
        val showServiceConfirm = AppState.mode == "service" && GameData.serviceDraftId != null && !overlayOpen
        if (showZoneConfirm) {
            val n = GameData.zoneDraft.size
            val cost = GameData.zoneDraftCost()
            val zname = Config.ZONE[GameData.zoneDraftKey]?.name ?: "分区"
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 78.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth()
                    .shadowCard(16.dp, C.panelWhite.toColor())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "待确认划区 $n 格 · $zname",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                    )
                    Text(
                        "再点同一格可撤销 · 确认后扣 $cost 万",
                        fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .background(C.chipBg.toColor(), RoundedCornerShape(12.dp))
                            .clickable {
                                Sfx.play("sfx_click", 0.5f)
                                GameData.clearZoneDraft()
                                AppState.bumpLive()
                                AppState.bumpMap()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("取消", fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                    }
                    Box(
                        modifier = Modifier
                            .background(C.accentGreen.toColor(), RoundedCornerShape(12.dp))
                            .clickable {
                                val (ok, msg) = GameData.confirmZoneDraft()
                                if (ok) Sfx.play("sfx_build", 0.5f) else Sfx.play("sfx_click", 0.4f)
                                if (msg != null) MapRef.view?.setToast(msg)
                                AppState.bumpLive()
                                AppState.bumpMap()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("确认划区", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current)
                    }
                }
            }
        }
        if (showServiceConfirm) {
            val sid = GameData.serviceDraftId
            val sc = World.serviceConfig(sid)
            val sname = sc?.name ?: "设施"
            val cost = if (sid != null) GameData.serviceCost(sid) else 0
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 78.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth()
                    .shadowCard(16.dp, C.panelWhite.toColor())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "待确认建造 · $sname",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                    )
                    Text(
                        "再点地图可改位置 · 确认后扣 $cost 万",
                        fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .background(C.chipBg.toColor(), RoundedCornerShape(12.dp))
                            .clickable {
                                Sfx.play("sfx_click", 0.5f)
                                GameData.clearServiceDraft()
                                AppState.bumpLive()
                                AppState.bumpMap()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("取消", fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                    }
                    Box(
                        modifier = Modifier
                            .background(C.accentGreen.toColor(), RoundedCornerShape(12.dp))
                            .clickable {
                                val (ok, msg) = GameData.confirmServiceDraft()
                                if (ok) Sfx.play("sfx_build") else Sfx.play("sfx_click", 0.4f)
                                if (msg != null) MapRef.view?.setToast(msg)
                                AppState.bumpLive()
                                AppState.bumpMap()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("确认建造", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current)
                    }
                }
            }
        }

        if (AppState.mode != "view" && !overlayOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 148.dp)
                    .size(52.dp)
                    .shadow(6.dp, RoundedCornerShape(26.dp))
                    .background(C.panelWhite.toColor(), RoundedCornerShape(26.dp))
                    .clickable {
                        Sfx.play("sfx_click")
                        MapScreen.cancelTool()
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_hand_pan),
                    contentDescription = "拖动地图",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (AppState.policyOpen) PolicyPanel()
        if (AppState.helpOpen) HelpPanel()
        if (AppState.dataOpen) DataPanel()
        if (AppState.happyOpen) HappyPanel()
        if (AppState.demandOpen) DemandPanel()
        if (AppState.civicOpen) CivicPanel()
        if (AppState.achievementOpen) AchievementPanel()
        if (AppState.settingsOpen) SettingsPanel()
        if (AppState.complaintOpen && Civic.pending != null) ComplaintPanel()
        if (AppState.adOfferOpen) AdOfferDialog()
        if (AppState.bankOpen) BankPanel()
        if (AppState.ledgerOpen) LedgerPanel()
        if (AppState.menuOpen) PausePanel()
    }
}

@Composable
private fun HudStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Config.COLORS.textMid.toColor(), fontFamily = LocalGameFont.current)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = LocalGameFont.current)
    }
}

@Composable
private fun DemandBar(label: String, v: Double, labelColor: Color, fillColor: Color, live: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 10.sp, color = labelColor, fontFamily = LocalGameFont.current)
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(6.dp)
                .background(Color(200 / 255f, 200 / 255f, 190 / 255f), RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width((34 * (kotlin.math.max(4, floor(v * 100).toInt()) / 100f)).dp)
                    .background(fillColor, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun DrawerContent(mapView: MapRenderView) {
    val C = Config.COLORS
    val s = GameData.current ?: return
    UIHelper.Card(
        modifier = Modifier.fillMaxWidth(),
        radius = 16.dp,
        padding = 14.dp,
        paddingTop = 12.dp,
        paddingBottom = 12.dp,
        horizontalAlignment = Alignment.Start
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                modifier = Modifier.align(Alignment.CenterEnd).clickable {
                    AppState.serviceOpen = false
                    AppState.roadOpen = false
                    AppState.planOpen = false
                    MapScreen.cancelTool()
                }.padding(4.dp)
            )
        }
        if (AppState.mode == "service") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 330.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "选择设施（放进城区里，覆盖周边）", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                )
                val groups = listOf(
                    "生活品质" to Config.ServiceCat.AMENITY,
                    "电力" to Config.ServiceCat.POWER,
                    "供水" to Config.ServiceCat.WATER,
                    "垃圾" to Config.ServiceCat.GARBAGE,
                    "医疗" to Config.ServiceCat.HEALTH,
                    "教育" to Config.ServiceCat.EDUCATION,
                    "消防" to Config.ServiceCat.SAFETY,
                    "交通" to Config.ServiceCat.TRANSIT,
                    "殡葬" to Config.ServiceCat.DEATH,
                    "地标" to Config.ServiceCat.LANDMARK
                )
                for ((title, cat) in groups) {
                    val items = Config.SERVICES.filter { it.category == cat }
                    if (items.isEmpty()) continue
                    Text(
                        title, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                    )
                    items.chunked(3).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { sv ->
                                val locked = sv.unlockPop > s.population.toInt()
                                val price = GameData.serviceCost(sv.id)
                                val poor = s.funds < price
                                UIHelper.PickChip(
                                    text = sv.name,
                                    sub = if (locked) "人口" + sv.unlockPop else "¥" + price + "万",
                                    selected = AppState.selService == sv.id,
                                    disabled = locked || poor,
                                    subColor = if (locked) C.textFaint.toColor()
                                    else if (poor) C.accentRed.toColor() else C.textMid.toColor(),
                                    width = 92.dp
                                ) {
                                    when {
                                        locked -> mapView.setToast("人口达到 " + sv.unlockPop + " 后解锁")
                                        poor -> mapView.setToast("资金不足")
                                        else -> {
                                            AppState.selService = sv.id
                                            GameData.clearServiceDraft()
                                            AppState.serviceOpen = false
                                            MapScreen.syncTool()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                AppState.selService?.let { id ->
                    val sc = World.serviceConfig(id)
                    if (sc != null) {
                        Text(
                            sc.desc + " 覆盖半径 " + sc.radius + " 格。",
                            fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                        )
                    }
                }
            }
        } else if (AppState.mode == "road") {
            Text(
                "道路类型", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (rk in listOf("dirt", "local", "avenue", "highway")) {
                    val r = Config.ROAD[rk]!!
                    val price = GameData.roadCost(rk)
                    UIHelper.PickChip(
                        text = r.name,
                        sub = "¥" + price + " 容" + r.capacity,
                        selected = AppState.roadKind == rk,
                        width = 90.dp
                    ) {
                        AppState.roadKind = rk
                        MapScreen.syncTool()
                        AppState.roadOpen = false
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val hasMetro = World.hasService("metro")
                val hasRail = World.hasService("rail_station")
                UIHelper.PickChip(
                    text = "地铁隧",
                    sub = if (hasMetro) "¥6万/格" else "先建地铁站",
                    selected = AppState.roadKind == "metro",
                    disabled = !hasMetro,
                    width = 110.dp
                ) {
                    if (!hasMetro) {
                        mapView.setToast("先在【服务】里建地铁站")
                    } else {
                        AppState.roadKind = "metro"
                        MapScreen.syncTool()
                        AppState.roadOpen = false
                    }
                }
                UIHelper.PickChip(
                    text = "铁轨",
                    sub = if (hasRail) "¥8万/格" else "先建火车站",
                    selected = AppState.roadKind == "rail",
                    disabled = !hasRail,
                    width = 110.dp
                ) {
                    if (!hasRail) {
                        mapView.setToast("先在【服务】里建火车站")
                    } else {
                        AppState.roadKind = "rail"
                        MapScreen.syncTool()
                        AppState.roadOpen = false
                    }
                }
            }
            Text(
                "开局十字就是两车道。四车道有双黄线和并排车。选完路型后面板会关，可在地图上拖着画。地铁/铁轨要先建对应车站。",
                fontSize = 10.sp, color = C.textFaint.toColor(), fontFamily = LocalGameFont.current
            )
        } else if (AppState.planOpen) {
            PlanDrawer(mapView)
        }
    }
}

@Composable
private fun PlanDrawer(mapView: MapRenderView) {
    val C = Config.COLORS
    fun pickAndClose(mode: String, overlay: String = "") {
        AppState.mode = mode
        if (overlay.isNotEmpty()) AppState.overlay = overlay
        AppState.planOpen = false
        MapScreen.syncTool()
        mapView.setToast(
            when (mode) {
                "bus" -> "点公交站连线，面板已关，可看地图"
                "district" -> "在地图上涂区划，面板已关"
                "tree" -> "点空地点树"
                "raise" -> "点空地抬升地形"
                "lower" -> "点空地降低地形"
                else -> "可以在地图上操作了"
            }
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "规划 · 选完即关，去地图上画",
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = C.textDark.toColor(), fontFamily = LocalGameFont.current
        )
        Text(
            "电和水按设施半径覆盖，不用再铺电缆水管。地铁隧/铁轨在【道路】里，要先建地铁站/火车站。",
            fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UIHelper.PickChip("公交线", "${Transit.draft.size}站", AppState.mode == "bus", width = 86.dp) {
                pickAndClose("bus")
            }
            UIHelper.PickChip(
                "区划",
                Networks.districts.firstOrNull()?.name ?: "一区",
                AppState.mode == "district",
                width = 86.dp
            ) {
                Networks.ensureDistrict()
                pickAndClose("district", "district")
            }
            UIHelper.PickChip("种树", "1万/格", AppState.mode == "tree", width = 86.dp) {
                pickAndClose("tree")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UIHelper.PickChip("抬升", "3万/格", AppState.mode == "raise", width = 86.dp) {
                pickAndClose("raise")
            }
            UIHelper.PickChip("降低", "3万/格", AppState.mode == "lower", width = 86.dp) {
                pickAndClose("lower")
            }
            UIHelper.PickChip("确认公交", "≥2站", false, width = 86.dp) {
                val (ok, msg) = Transit.confirmDraft()
                mapView.setToast(msg ?: if (ok) "已开通" else "失败")
                AppState.bumpLive()
            }
        }
        Text(
            "公交草稿 " + Transit.draft.size + " 站 · 线路 " + Transit.lines.size + " 条",
            fontSize = 10.sp, color = C.textFaint.toColor(), fontFamily = LocalGameFont.current
        )
    }
}

@Composable
private fun PolicyPanel() {
    val C = Config.COLORS
    val s = GameData.current ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .noRippleClickable { AppState.policyOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val live = AppState.liveTick
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "营造政策", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { AppState.policyOpen = false }.padding(4.dp)
                )
            }
            Text(
                GameData.policyStatusLine(),
                fontSize = 11.sp, color = C.accentGreen.toColor(),
                fontFamily = LocalGameFont.current,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Text(
                "启用后立刻改税收/需求/污染/拥堵，并持续到倒计时结束。",
                fontSize = 10.sp, color = C.textMid.toColor(),
                fontFamily = LocalGameFont.current,
                modifier = Modifier.fillMaxWidth()
            )
            for (pol in Config.POLICIES) {
                val cd = s.policyCooldowns[pol.id] ?: 0
                val active = s.activePolicies.any { it.id == pol.id }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(C.chipBg.toColor(), RoundedCornerShape(12.dp))
                        .border(1.dp, C.border2.toColor(), RoundedCornerShape(12.dp))
                        .clickable {
                            val (ok, msg) = GameData.activatePolicy(pol.id)
                            if (!ok) {
                                MapRef.view?.setToast(msg ?: "无法启用")
                            } else {
                                Sfx.play("sfx_policy")
                                MapRef.view?.setToast(msg ?: "政策已生效")
                            }
                            AppState.bumpLive()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            pol.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                        )
                        Text(
                            pol.desc, fontSize = 10.sp, color = C.textMid.toColor(),
                            fontFamily = LocalGameFont.current
                        )
                    }
                    Text(
                        if (active) {
                            val left = s.activePolicies.firstOrNull { it.id == pol.id }?.daysLeft ?: 0
                            "生效中 ${left}天"
                        } else if (cd > 0) "冷却 ${cd}天" else "启用",
                        fontSize = 11.sp,
                        color = if (active) C.accentGreen.toColor()
                        else if (cd > 0) C.textFaint.toColor() else C.accentRed.toColor(),
                        fontFamily = LocalGameFont.current,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            // ---- 税率（RCI 三档） ----
            Text(
                "税率（% 越高收入越多，满意度与需求越低）",
                fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            TaxSlider("住宅", s.taxRes) { s.taxRes = it; AppState.bumpLive() }
            TaxSlider("商业", s.taxCom) { s.taxCom = it; AppState.bumpLive() }
            TaxSlider("工业", s.taxInd) { s.taxInd = it; AppState.bumpLive() }
            TaxSlider("办公", s.taxOff) { s.taxOff = it; AppState.bumpLive() }
            Text(
                "贷款已移到左上【银】银行。广告低息、手动高息。",
                fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.accentGreen.toColor(), RoundedCornerShape(20.dp))
                    .clickable { AppState.policyOpen = false },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "关闭", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    fontFamily = LocalGameFont.current
                )
            }
            if (live < 0) Text("")
        }
    }
}

@Composable
private fun HelpPanel() {
    val C = Config.COLORS
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .noRippleClickable { AppState.helpOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .verticalScroll(rememberScrollState())
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(18.dp)
                .noRippleClickable { },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "新手指引 · 模拟市长，经营一座虚构都市", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { AppState.helpOpen = false }.padding(4.dp)
                )
            }
            HelpRow("手", "手掌在确认条上方，点它退出建造并拖地图。划区/设施都要点底部「确认」才扣费。")
            HelpRow("职", "点顶栏营造职级打开营造档案。人口、满意度和测评都达标才会晋升，不是现实官职。")
            HelpRow("路", "开局十字是【两车道】，和建造菜单里同一种。泥土路无标线；两车道一条中虚线；四车道中央双黄、两侧白虚线，车分内外道并排。外环高速全天有过路车；接进城后才会进游客。")
            HelpRow("铁", "先在【服务】建火车站，再在【道路】里选铁轨去地图上画。地铁同理，先建地铁站。机场建好会有飞机。")
            HelpRow("区", "【住宅/商业/工业/办公】在路旁点格子进草稿，点「确认划区」才扣费。设施会清掉底下分区，不会被后长出来的楼盖掉。小学点在占地内任意一格即可。【办公】要中学以上学历才进得去，收益比商业高。【推平】拆楼会连底下分区一起清掉。")
            HelpRow("电", "风电/煤电按造价和占地覆盖一片区域，不用铺电缆。点【数】开电力热力图能看到圈。")
            HelpRow("水", "水塔/抽水站按半径抽取地下水供水，不必靠河。点地图只是预览，底部「确认建造」才扣费。诊所人口 25 解锁，垃圾场 40 解锁。")
            HelpRow("规", "【规划】只选公交/区划/种树。选完面板会关，才能在地图上点。")
            HelpRow("策", "【数/?/策/银/账】在状态栏左下。【银】贷款；【账】看每天每月收支。人口过 180 后维护和造价逐步加重。暂停用顶栏 ‖；1x 比以前慢一半；2x/3x 都要看广告。右上 ≡ 在顶栏下方，点开建筑详情时会先藏起来。")
            HelpRow("存", "每月结算和切出游戏都会自动写入当前槽位。主菜单「继续游戏」读最近一档。右上【≡】也可手动保存。")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(C.accentGreen.toColor(), RoundedCornerShape(21.dp))
                    .clickable { AppState.helpOpen = false },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "开始建设", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    fontFamily = LocalGameFont.current
                )
            }
        }
    }
}

@Composable
private fun HelpRow(no: String, text: String) {
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Text(
            no, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = Config.COLORS.accentRed.toColor(), fontFamily = LocalGameFont.current
        )
        Text(
            text, fontSize = 12.sp, color = Config.COLORS.textDark.toColor(),
            fontFamily = LocalGameFont.current, lineHeight = 18.sp, modifier = Modifier.weight(1f)
        )
    }
}

private fun overlayLabel(cat: String): String = when (cat) {
    Config.ServiceCat.POWER -> "电力"
    Config.ServiceCat.WATER -> "供水"
    Config.ServiceCat.GARBAGE -> "垃圾"
    Config.ServiceCat.HEALTH -> "医疗"
    Config.ServiceCat.EDUCATION -> "教育"
    Config.ServiceCat.SAFETY -> "治安消防"
    Config.ServiceCat.TRANSIT -> "公交轨道"
    Config.ServiceCat.DEATH -> "殡葬"
    Config.ServiceCat.AMENITY -> "公园广场"
    Config.ServiceCat.LANDMARK -> "地标"
    "traffic" -> "拥堵"
    "landvalue" -> "地价"
    "metro" -> "地铁"
    "rail" -> "铁轨"
    "district" -> "区划"
    else -> cat
}

private fun overlayHint(cat: String): String = when (cat) {
    "traffic" -> "绿畅行 · 黄缓行 · 红拥堵"
    "landvalue" -> "绿高地价 · 红受污染拉低"
    "metro", "rail", "district" -> "只显示对应网络"
    else -> "绿=已覆盖 · 红=未覆盖 · 圈=该座设施半径，圈内哪坨归哪座一看就明"
}

@Composable
private fun HappyPanel() {
    val C = Config.COLORS
    val s = GameData.current ?: return
    val bd = GameData.happinessBreakdown()
    val live = AppState.liveTick
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .noRippleClickable { AppState.happyOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp)
                .noRippleClickable { },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "满意度从哪来", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { AppState.happyOpen = false }.padding(4.dp)
                )
            }
            Text(
                "当前 ${floor(s.happiness).toInt()} · 目标 ${bd.target.toInt()}（每天慢慢靠拢目标）",
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (s.happiness >= 55) C.accentGreen.toColor() else C.accentRed.toColor(),
                fontFamily = LocalGameFont.current
            )
            Text("基础分 ${bd.base.toInt()}：城市底子，没有设施时也有这么多。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("公园/广场/学校/诊所等服务 ${if (bd.service >= 0) "+" else ""}${bd.service.toInt()}：多建公园、广场、学校、诊所会涨。公园会吸收污染、抬地价。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("污染 ${bd.pollution.toInt()}：工厂、电厂、垃圾堆会拉低。绿化、种树、把住工分开能缓解。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("覆盖 ${bd.coveragePenalty.toInt()}：住宅要在电/水/垃圾圈里，圈外会扣分。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("税率 ${bd.taxPenalty.toInt()}：税率高于 10% 会扣分。点【策】可调。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("事件 ${if (bd.event >= 0) "+" else ""}${bd.event.toInt()}：市民来信和城建事件。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("政策 ${if (bd.policy >= 0) "+" else ""}${bd.policy.toInt()}：民生改善、绿化行动等会加分。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("通勤 ${if (bd.commute >= 0) "+" else ""}${bd.commute.toInt()}：路堵会扣，公交/地铁能加。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("就业/健康/教育 ${if (bd.jobs >= 0) "+" else ""}${bd.jobs.toInt()}：商工办岗位、医院学校、治安都算在这里。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.accentGreen.toColor(), RoundedCornerShape(20.dp))
                    .clickable { AppState.happyOpen = false },
                contentAlignment = Alignment.Center
            ) {
                Text("关闭", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current)
            }
            if (live < 0) Text("")
        }
    }
}

@Composable
private fun DemandPanel() {
    val C = Config.COLORS
    val d = Growth.lastDemand
    val st = World.stats()
    val s = GameData.current
    val pop = s?.population?.toInt() ?: 0
    val live = AppState.liveTick
    fun tip(v: Double): String = when {
        v >= 0.7 -> "很缺，赶紧划这类区"
        v >= 0.4 -> "有需求，可以再划一点"
        v >= 0.2 -> "基本够用"
        else -> "暂时饱和，先别狂划"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .noRippleClickable { AppState.demandOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp)
                .noRippleClickable { },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "住商工办需求", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { AppState.demandOpen = false }.padding(4.dp)
                )
            }
            Text(
                "顶栏那一排是 RCI 需求，不是进度。条越长=市场上越缺这类楼，邻路空地才会按这个长楼。",
                fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Text("住 ${(d.r * 100).toInt()}% · ${tip(d.r)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current)
            Text("人口 $pop / 住宅容量 ${st.resCap}。岗位多、满意度高时住房需求涨；房子盖太多会回落。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("商 ${(d.c * 100).toInt()}% · ${tip(d.c)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current)
            Text("人口多了才要店。现有商业容量 ${st.comCap}。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("工 ${(d.i * 100).toInt()}% · ${tip(d.i)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.accentGold.toColor(), fontFamily = LocalGameFont.current)
            Text("工厂提供岗位，但会污染。现有工业容量 ${st.indCap}。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("办 ${(d.o * 100).toInt()}% · ${tip(d.o)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current)
            Text("教育越高、白领越多，办公需求越大。现有办公容量 ${st.offCap}。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("税率过高会压需求。通电通水的路旁住宅才会进人，进人后黑色区域每 20 人扩一圈。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.accentGreen.toColor(), RoundedCornerShape(20.dp))
                    .clickable { AppState.demandOpen = false },
                contentAlignment = Alignment.Center
            ) {
                Text("关闭", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current)
            }
            if (live < 0) Text("")
        }
    }
}

@Composable
private fun BankPanel() {
    val C = Config.COLORS
    val s = GameData.current ?: return
    val act = LocalContext.current as? Activity
    val live = AppState.liveTick
    val (can, why) = GameData.bankCanBorrow()
    val rankMul = if (s.rankLevel >= 2) 1.25 else 1.0
    val manAmt = (Config.LOAN.manualAmount * rankMul).toInt()
    val adAmt = (Config.LOAN.adAmount * rankMul).toInt()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .noRippleClickable { AppState.bankOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp)
                .noRippleClickable { },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "营造银行", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { AppState.bankOpen = false }.padding(4.dp)
                )
            }
            Text(
                if (s.loanDebt > 0) {
                    val kind = if (s.loanKind == "ad") "广告低息" else "手动高息"
                    "在还：$kind · 剩余 ${floor(s.loanDebt).toInt()} 万 · 日还 ${s.loanDaily.toInt()} 万"
                } else if (s.loanCooldown > 0) "冷却 ${s.loanCooldown} 天后再借"
                else "当前无贷款，可选一种借出。",
                fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "手动贷到账多但每天还得多；看广告贷到账略少、每天还得少。同一时间只能有一笔。",
                fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.chipBg.toColor(), RoundedCornerShape(14.dp))
                    .clickable(enabled = can) {
                        val (ok, msg) = GameData.borrowBank("manual")
                        if (ok) Sfx.play("sfx_cash") else Sfx.play("sfx_click", 0.4f)
                        if (msg != null) MapRef.view?.setToast(msg)
                        AppState.bumpLive()
                    }
                    .padding(12.dp)
            ) {
                Column {
                    Text("手动高息贷 · 到账 ${manAmt} 万", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.accentRed.toColor(), fontFamily = LocalGameFont.current)
                    Text("每天自动还 ${Config.LOAN.manualDaily.toInt()} 万，大约半个月还清。不看广告。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.accentSoftBg.toColor(), RoundedCornerShape(14.dp))
                    .clickable(enabled = can) {
                        if (act == null) return@clickable
                        Ads.reward(act, {
                            val (ok, msg) = GameData.borrowBank("ad")
                            if (ok) Sfx.play("sfx_cash")
                            if (msg != null) MapRef.view?.setToast(msg)
                            AppState.bumpLive()
                        })
                    }
                    .padding(12.dp)
            ) {
                Column {
                    Text("看广告低息贷 · 到账 ${adAmt} 万", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current)
                    Text("看完广告才到账。每天只还 ${Config.LOAN.adDaily.toInt()} 万，压力小很多。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                }
            }
            if (!can) Text(why ?: "", fontSize = 11.sp, color = C.accentRed.toColor(), fontFamily = LocalGameFont.current)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.accentGreen.toColor(), RoundedCornerShape(20.dp))
                    .clickable { AppState.bankOpen = false },
                contentAlignment = Alignment.Center
            ) {
                Text("关闭", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current)
            }
            if (live < 0) Text("")
        }
    }
}

@Composable
private fun ledgerMoney(v: Double, income: Boolean): String {
    val n = floor(kotlin.math.abs(v)).toInt()
    val body = if (n >= 10000) String.format("%.1f亿", n / 10000.0) else n.toString() + "万"
    return (if (income) "+" else "−") + body
}

@Composable
private fun LedgerRow(name: String, amount: Double, income: Boolean) {
    val C = Config.COLORS
    if (kotlin.math.abs(amount) < 0.05) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
        Text(
            ledgerMoney(amount, income),
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (income) C.accentGreen.toColor() else C.accentRed.toColor(),
            fontFamily = LocalGameFont.current
        )
    }
}

@Composable
private fun LedgerPanel() {
    val C = Config.COLORS
    val s = GameData.current ?: return
    val live = AppState.liveTick
    val today = GameData.dateLabel()
    val todayLines = s.dayBook.filter { it.date == today }.asReversed()
    val month = s.monthBooks.lastOrNull()
    val inTotal = s.dayIncomeTax + s.dayIncomeBiz + s.dayIncomeTrade
    val outTotal = s.lastRoadUpkeep + s.lastServiceUpkeep + s.lastGrownUpkeep + s.lastLoanRepay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .noRippleClickable { AppState.ledgerOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp)
                .noRippleClickable { },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "财务报表", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { AppState.ledgerOpen = false }.padding(4.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(C.chipBg.toColor(), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text("金库", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                    Text(UIHelper.fmtFunds(s.funds), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.accentGold.toColor(), fontFamily = LocalGameFont.current)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(C.chipBg.toColor(), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text("本日净", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                    Text(
                        ledgerMoney(kotlin.math.abs(s.lastNet), s.lastNet >= 0),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = if (s.lastNet >= 0) C.accentGreen.toColor() else C.accentRed.toColor(),
                        fontFamily = LocalGameFont.current
                    )
                }
            }
            Text("今日", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("收入 +" + floor(inTotal).toInt() + "万", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current)
                    LedgerRow("居民税", s.dayIncomeTax, true)
                    LedgerRow("工商税", s.dayIncomeBiz, true)
                    LedgerRow("贸易观光", s.dayIncomeTrade, true)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("支出 −" + floor(outTotal).toInt() + "万", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.accentRed.toColor(), fontFamily = LocalGameFont.current)
                    LedgerRow("道路维护", s.lastRoadUpkeep, false)
                    LedgerRow("设施运营", s.lastServiceUpkeep, false)
                    LedgerRow("城区养护", s.lastGrownUpkeep, false)
                    LedgerRow("贷款还款", s.lastLoanRepay, false)
                }
            }
            if (month \!= null) {
                Text(
                    "${month.year}年${month.month}月",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LedgerRow("居民税", month.tax, true)
                        LedgerRow("工商税", month.biz, true)
                        LedgerRow("贸易观光", month.trade, true)
                        LedgerRow("贷款入账", month.loanIn, true)
                        LedgerRow("其他收入", month.otherIn, true)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LedgerRow("道路维护", month.road, false)
                        LedgerRow("设施运营", month.service, false)
                        LedgerRow("建造", month.build, false)
                        LedgerRow("划区", month.zone, false)
                        LedgerRow("还贷", month.loanOut, false)
                        LedgerRow("其他支出", month.otherOut, false)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("本月净", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                    Text(
                        ledgerMoney(kotlin.math.abs(month.net()), month.net() >= 0),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (month.net() >= 0) C.accentGreen.toColor() else C.accentRed.toColor(),
                        fontFamily = LocalGameFont.current
                    )
                }
            }
            Text("今日流水", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            if (todayLines.isEmpty()) {
                Text("过日结算、建造和贷款会记在这里。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
            } else {
                for (line in todayLines.take(18)) {
                    LedgerRow(line.name, line.amount, line.kind == "income")
                }
            }
            if (s.monthBooks.size > 1) {
                Text("近几个月", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                for (b in s.monthBooks.asReversed().take(6)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${b.year}.${b.month.toString().padStart(2, '0')}", fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                        Text(
                            ledgerMoney(kotlin.math.abs(b.net()), b.net() >= 0),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (b.net() >= 0) C.accentGreen.toColor() else C.accentRed.toColor(),
                            fontFamily = LocalGameFont.current
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.accentGreen.toColor(), RoundedCornerShape(20.dp))
                    .clickable { AppState.ledgerOpen = false },
                contentAlignment = Alignment.Center
            ) {
                Text("关闭", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current)
            }
            if (live < 0) Text("")
        }
    }
}

@Composable
private fun coverInfo(x: Int, y: Int, cat: String, label: String) {
    val C = Config.COLORS
    val fac = World.coveringFacility(x, y, cat)
    if (fac != null) {
        val name = World.serviceConfig(fac.b.service)?.name ?: "设施"
        UIHelper.InfoRow(label, "由「$name」覆盖", C.accentGreen.toColor())
    } else {
        UIHelper.InfoRow(label, "不在覆盖圈内", C.accentRed.toColor())
    }
}

@Composable
private fun DataPanel() {
    val C = Config.COLORS
    val s = GameData.current ?: return
    val cov = s.lastCoverage
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .noRippleClickable { AppState.dataOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .noRippleClickable { },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val live = AppState.liveTick
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "城市数据", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { AppState.dataOpen = false }.padding(4.dp)
                )
            }

            // 覆盖率
            if (cov != null) {
                CovBar("电力", cov.power)
                Text(
                    "发电 " + s.powerCap + " / 需求 " + s.powerNeed +
                        " · 打开覆盖图可看到每座电站的圈和绿坨",
                    fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                )
                CovBar("供水", cov.water)
                Text(
                    "供水 " + s.waterCap + " / 需求 " + s.waterNeed + " · 容量不够时圈内也会按比例缺水",
                    fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                )
                CovBar("垃圾", cov.garbage)
                CovBar("医疗", cov.health)
                CovBar("教育", cov.education)
                CovBar("治安", cov.safety)
                CovBar("殡葬", cov.death)
                Text(
                    "覆盖按设施半径对齐，不是电缆。点下方按钮可单独看每一类圈到了哪一坨建筑。",
                    fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                )
            }
            Text(
                "职级 " + GameData.rankDef().name + " · " + GameData.rankDef().perk +
                    (GameData.nextRank()?.let { " → 下一级 " + it.name + "（人口" + it.popReq + "/满意" + it.happyReq + "）" } ?: " · 已满级"),
                fontSize = 11.sp, color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "升学率 ${(Civic.schoolRate * 100).toInt()}% · 营造测评通过 ${Civic.examPassed} · 来信 ${Civic.complaintsHandled}",
                fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "犯罪 ${s.crime.toInt()} · 垃圾积压 ${s.garbageBacklog} · 污水覆盖 ${(s.sewerCoverage * 100).toInt()}% · 待安葬 ${s.deathsPending}",
                fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Text("服务预算", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
            TaxSlider("医疗预算", s.budgetHealth, 50, 150) { s.budgetHealth = it; AppState.bumpLive() }
            TaxSlider("教育预算", s.budgetEdu, 50, 150) { s.budgetEdu = it; AppState.bumpLive() }
            TaxSlider("治安预算", s.budgetSafety, 50, 150) { s.budgetSafety = it; AppState.bumpLive() }
            TaxSlider("公交预算", s.budgetTransit, 50, 150) { s.budgetTransit = it; AppState.bumpLive() }

            // 满意度根因
            val bd = GameData.happinessBreakdown()
            Text(
                "满意度 ${floor(s.happiness).toInt()}（目标 ${bd.target.toInt()}）",
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (s.happiness >= 55) C.accentGreen.toColor() else C.accentRed.toColor(),
                fontFamily = LocalGameFont.current
            )
            Text(
                "基础 ${bd.base.toInt()} · 服务 ${bd.service.toInt()} · 污染 ${bd.pollution.toInt()} · 覆盖 ${bd.coveragePenalty.toInt()} · 税 ${bd.taxPenalty.toInt()} · 事件 ${bd.event.toInt()} · 政策 ${bd.policy.toInt()} · 通勤 ${bd.commute.toInt()} · 就业 ${bd.jobs.toInt()}",
                fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "教育 ${s.education.toInt()} · 健康 ${s.health.toInt()} · 岗位 ${s.jobs} · 拥堵 ${(s.congestion * 100).toInt()}%",
                fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "在岗 " + Citizens.employed +
                    " · 通勤拥堵 " + (Citizens.avgCommute * 100).toInt() +
                    "% · 公交 " + Transit.lines.size + " 条/" + Transit.ridership + " 客",
                fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "今日 税 ${UIHelper.fmtFunds(s.dayIncomeTax)} · 产业 ${UIHelper.fmtFunds(s.dayIncomeBiz)} · 贸易 ${UIHelper.fmtFunds(s.dayIncomeTrade)} · 维护 -${UIHelper.fmtFunds(s.lastUpkeep)} · 净 ${UIHelper.fmtFunds(s.lastNet)}",
                fontSize = 10.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                GameData.policyStatusLine(),
                fontSize = 11.sp, color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current
            )

            // 市政任务
            s.quest?.let { q ->
                val v = GameData.questValue(q.type)
                Text(
                    "营造任务：" + q.name + " " + v.toInt() + "/" + q.target.toInt() +
                        "（奖励 " + q.reward + " 万）" + if (q.done) " ✓" else "",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = C.accentGold.toColor(), fontFamily = LocalGameFont.current
                )
            }

            // 需求
            val d = Growth.lastDemand
            Text(
                "RCI 需求", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "住宅 ${(d.r * 100).toInt()}% · 商业 ${(d.c * 100).toInt()}% · 工业 ${(d.i * 100).toInt()}% · 办公 ${(d.o * 100).toInt()}%",
                fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )

            // 收支
            Text(
                "本月累计 收入 ${UIHelper.fmtFunds(s.totalIncome)} · 支出 ${UIHelper.fmtFunds(s.totalSpent)}",
                fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )

            // 贷款 / 成就
            Text(
                "贷款 " + (if (s.loanDebt > 0) "剩余 ${floor(s.loanDebt).toInt()} 万" else "无") +
                    " · 成就 ${s.achievements.size}/${Config.ACHIEVEMENTS.size}",
                fontSize = 12.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )

            // 热力图切换
            Text(
                "覆盖热力图", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            val cats = listOf(
                Config.ServiceCat.POWER, Config.ServiceCat.WATER, Config.ServiceCat.GARBAGE,
                Config.ServiceCat.HEALTH, Config.ServiceCat.EDUCATION, Config.ServiceCat.SAFETY,
                Config.ServiceCat.TRANSIT, Config.ServiceCat.DEATH, Config.ServiceCat.AMENITY,
                Config.ServiceCat.LANDMARK, "traffic", "landvalue", "metro", "rail", "district"
            )
            cats.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { cat ->
                        val active = AppState.overlay == cat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (active) C.accentSoftBg.toColor() else C.chipBg.toColor(),
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (active) C.accentRed.toColor() else C.border2.toColor(),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    Sfx.play("sfx_click", 0.5f)
                                    AppState.overlay = if (active) "" else cat
                                    AppState.dataOpen = false
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                overlayLabel(cat), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (active) C.accentRed.toColor() else C.textDark.toColor(),
                                fontFamily = LocalGameFont.current
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.accentGreen.toColor(), RoundedCornerShape(20.dp))
                    .clickable { AppState.dataOpen = false },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "关闭", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    fontFamily = LocalGameFont.current
                )
            }
            if (live < 0) Text("")
        }
    }
}

@Composable
private fun CovBar(label: String, ratio: Float) {
    val C = Config.COLORS
    val pct = (ratio * 100).toInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label, fontSize = 12.sp, color = C.textDark.toColor(),
            fontFamily = LocalGameFont.current, modifier = Modifier.width(34.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(Color(200 / 255f, 200 / 255f, 190 / 255f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .fillMaxWidth(pct / 100f)
                    .background(
                        if (pct >= 80) C.accentGreen.toColor()
                        else if (pct >= 40) C.accentGold.toColor() else C.accentRed.toColor(),
                        RoundedCornerShape(4.dp)
                    )
            )
        }
        Text(
            "$pct%", fontSize = 11.sp, color = C.textMid.toColor(),
            fontFamily = LocalGameFont.current, modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun TaxSlider(
    label: String,
    value: Int,
    minV: Int = Config.TAX.min,
    maxV: Int = Config.TAX.max,
    onChange: (Int) -> Unit
) {
    val C = Config.COLORS
    val v = value.coerceIn(minV, maxV)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label, fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "$v%", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (v > (minV + maxV) / 2) C.accentRed.toColor() else C.textDark.toColor(),
                fontFamily = LocalGameFont.current
            )
        }
        Slider(
            value = v.toFloat(),
            onValueChange = {
                onChange(it.roundToInt().coerceIn(minV, maxV))
                AppState.bumpLive()
            },
            valueRange = minV.toFloat()..maxV.toFloat(),
            steps = max(0, maxV - minV - 1),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        )
    }
}

@Composable
private fun PausePanel() {
    val C = Config.COLORS
    val s = GameData.current ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .noRippleClickable { AppState.menuOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .noRippleClickable { },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { AppState.menuOpen = false }.padding(4.dp)
                )
            }
            Text(
                s.cityName + " · " + World.cityLevel().name,
                fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                textAlign = TextAlign.Center
            )
            Text(
                GameData.dateLabel() + " · 人口 " + s.population.toInt() + " · 满意 " + floor(s.happiness).toInt(),
                fontSize = 12.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                s.mayorName + " · 职级 " + GameData.rankDef().name + " · " + GameData.rankDef().perk,
                fontSize = 12.sp, color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "外环高速 " + (if (World.current?.highwayConnected == true) "已接通" else "未接通") +
                    " · 繁荣 " + (World.current?.prosperity ?: 0),
                fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "当前槽位 " + (AppState.activeSlot + 1) +
                    if (s.lastSavedLabel.isNotEmpty()) " · 上次 " + s.lastSavedLabel else " · 尚未手动保存",
                fontSize = 11.sp, color = C.accentGold.toColor(), fontFamily = LocalGameFont.current
            )
            PauseBtn("继续游戏", C.accentGreen.toColor(), Color.White) {
                Sfx.play("sfx_click")
                AppState.menuOpen = false
            }
            PauseBtn("银行贷款", C.accentGold.toColor(), Color.White) {
                Sfx.play("sfx_click")
                AppState.menuOpen = false
                AppState.bankOpen = true
            }
            PauseBtn("看广告领奖励（礼包+280万/加倍税/满意+8/拨款+220万）", C.accentGold.toColor(), Color.White) {
                Sfx.play("sfx_click")
                AppState.menuOpen = false
                AppState.settingsOpen = true
            }
            PauseBtn("设置 · 音量/广告", C.chipBg.toColor(), C.textDark.toColor()) {
                Sfx.play("sfx_click")
                AppState.menuOpen = false
                AppState.settingsOpen = true
            }
            PauseBtn("营造档案 / 测评", C.chipBg.toColor(), C.textDark.toColor()) {
                Sfx.play("sfx_click")
                AppState.menuOpen = false
                AppState.civicOpen = true
            }
            PauseBtn("营造成就", C.chipBg.toColor(), C.textDark.toColor()) {
                Sfx.play("sfx_click")
                AppState.menuOpen = false
                AppState.achievementOpen = true
            }
            PauseBtn("保存到槽位 " + (AppState.activeSlot + 1), C.chipBg.toColor(), C.textDark.toColor()) {
                Sfx.play("sfx_save")
                SaveManager.save(AppState.activeSlot)
                AppState.saveTick++
                MapRef.view?.setToast("已写入槽位 " + (AppState.activeSlot + 1) + " · " + s.cityName)
            }
            PauseBtn("保存并返回主菜单", C.chipBg.toColor(), C.textDark.toColor()) {
                Sfx.play("sfx_save")
                SaveManager.save(AppState.activeSlot)
                AppState.saveTick++
                AppState.menuOpen = false
                AppState.screen = "menu"
                AppState.menuScreen = "slots"
            }
        }
    }
}

@Composable
private fun PauseBtn(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(bg, RoundedCornerShape(22.dp))
            .border(1.dp, Config.COLORS.border2.toColor(), RoundedCornerShape(22.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg,
            fontFamily = LocalGameFont.current
        )
    }
}

private val CardShadow = Color(90 / 255f, 100 / 255f, 90 / 255f, 70 / 255f)

private fun Modifier.shadowCard(radius: Dp, color: Color): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(4.dp, shape, ambientColor = CardShadow, spotColor = CardShadow)
        .background(color, shape)
}

/** 拦截触摸但无涟漪（等价于 Lua 的 pointerEvents = "auto"） */
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = null,
            indication = null,
            onClick = onClick
        )
    )
