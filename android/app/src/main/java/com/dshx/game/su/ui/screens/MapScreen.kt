package com.dshx.game.su.ui.screens

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
import com.dshx.game.su.R
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
import com.dshx.game.su.Ads
import com.dshx.game.su.AppState
import com.dshx.game.su.Civic
import com.dshx.game.su.Config
import com.dshx.game.su.GameData
import com.dshx.game.su.MapRef
import com.dshx.game.su.SaveManager
import com.dshx.game.su.Sfx
import com.dshx.game.su.SpeedBoost
import com.dshx.game.su.ui.UIHelper
import com.dshx.game.su.ui.toColor
import com.dshx.game.su.ui.theme.LocalGameFont
import com.dshx.game.su.world.Citizens
import com.dshx.game.su.world.Growth
import com.dshx.game.su.world.MapRenderView
import com.dshx.game.su.world.Networks
import com.dshx.game.su.world.Tool
import com.dshx.game.su.world.Transit
import com.dshx.game.su.world.World
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
            "pipe" -> Tool("pipe")
            "cable" -> Tool("cable")
            "bus" -> Tool("bus")
            "district" -> Tool("district")
            "sewer" -> Tool("sewer")
            "metro" -> Tool("metro")
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
        AppState.zoneKey = zk
        AppState.mode = "zone"
        AppState.serviceOpen = false
        AppState.planOpen = false
        syncTool()
    }

    fun cancelTool() {
        AppState.mode = "view"
        AppState.serviceOpen = false
        AppState.roadOpen = false
        AppState.planOpen = false
        syncTool()
    }

    fun onShow(view: MapRenderView, wDp: Float, hDp: Float) {
        view.setViewport(wDp, hDp, 108f, 96f)
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
            // 行1：资源胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadowCard(18.dp, C.panelWhite.toColor())
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (s != null) {
                    UIHelper.Chip("", s.cityName)
                    ChipValue("职", GameData.rankDef().name, C.accentBlue.toColor(), live)
                    ChipValue("资金", "¥" + UIHelper.fmtMoney(s.funds) + "万", C.accentGold.toColor(), live)
                    ChipValue("人口", UIHelper.fmtPop(floor(s.population).toInt()), C.textDark.toColor(), live)
                    ChipValue(
                        "满意", floor(s.happiness).toInt().toString(),
                        if (s.happiness >= 55) C.accentGreen.toColor() else C.accentRed.toColor(), live
                    )
                    ChipValue("", GameData.dateLabel(), C.textDark.toColor(), live)
                }
                // 简报按钮
                Box(
                    modifier = Modifier
                        .background(C.accentSoftBg.toColor(), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable { MapScreen.goNewspaper() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "简报", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = C.accentRed.toColor(), fontFamily = LocalGameFont.current
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
                // RCI
                Row(
                    modifier = Modifier
                        .background(C.panelWhite.toColor(), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
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
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val speeds = listOf("‖" to 0, "▶" to 1, "▶▶" to 2, "▶▶▶" to 3)
                    val act = LocalContext.current as? Activity
                    for (sp in speeds) {
                        val active = GameData.speedIdx == sp.second
                        val locked = sp.second >= 2 && !SpeedBoost.isActive()
                        Box(
                            modifier = Modifier
                                .width(if (sp.second == 0) 34.dp else 44.dp)
                                .height(26.dp)
                                .background(
                                    if (active) C.accentSoftBg.toColor() else Color.Transparent,
                                    RoundedCornerShape(9.dp)
                                )
                                .clickable {
                                    Sfx.play("sfx_click", 0.5f)
                                    if (locked) {
                                        if (act != null) {
                                            Ads.reward(act, {
                                                SpeedBoost.activate()
                                                GameData.setSpeed(sp.second)
                                                MapRef.view?.setToast("加速已解锁 20 分钟")
                                                AppState.bumpLive()
                                            })
                                        }
                                    } else {
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

        // 详情/抽屉/弹层打开时藏左侧按钮，避免挡住信息卡
        val overlayOpen = AppState.policyOpen || AppState.helpOpen || AppState.dataOpen || AppState.paused ||
            AppState.settingsOpen || AppState.civicOpen || AppState.complaintOpen || AppState.achievementOpen
        val infoOpen = AppState.mode == "view" && mapView.selectedX > 0
        val drawerOpen = (AppState.mode == "service" && AppState.serviceOpen) ||
            (AppState.mode == "road" && AppState.roadOpen) ||
            AppState.planOpen
        if (!overlayOpen && !infoOpen && !drawerOpen) {
        // ---------------- 政策按钮 ----------------
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 84.dp)
        ) {
            UIHelper.RoundButton("策", size = 40.dp, fontSize = 15.sp) {
                Sfx.play("sfx_click", 0.6f)
                AppState.policyOpen = !AppState.policyOpen
            }
        }

        // ---------------- 帮助按钮 ----------------
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 134.dp)
        ) {
            UIHelper.RoundButton("?", size = 40.dp, fontSize = 20.sp) {
                Sfx.play("sfx_click", 0.6f)
                AppState.helpOpen = !AppState.helpOpen
            }
        }

        // ---------------- 数据按钮 ----------------
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 184.dp)
        ) {
            UIHelper.RoundButton("数", size = 40.dp, fontSize = 16.sp) {
                Sfx.play("sfx_click", 0.6f)
                AppState.dataOpen = !AppState.dataOpen
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
                    modifier = Modifier.fillMaxWidth(),
                    radius = 16.dp,
                    paddingTop = 10.dp,
                    paddingBottom = 10.dp,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val sel = mapView.selectedX to mapView.selectedY
                    Text(
                        "(" + sel.first + ", " + sel.second + ")  " + GameData.dateLabel(),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                    )
                    UIHelper.InfoRow("海拔", World.elevation(sel.first, sel.second).toString() + "m")
                    UIHelper.InfoRow("地形", World.terrainName(sel.first, sel.second))
                    UIHelper.InfoRow("现状", World.zoneName(sel.first, sel.second), C.accentBlue.toColor())
                    UIHelper.InfoRow("地价", World.landValue(sel.first, sel.second).toString())
                    UIHelper.InfoRow("噪音", World.noiseAt(sel.first, sel.second).toString())
                    val tb = World.tile(sel.first, sel.second)?.building
                    if (tb != null && !tb.isService) {
                        val cap = tb.cap()
                        if (tb.abandoned) {
                            UIHelper.InfoRow("状态", "废弃", C.accentRed.toColor())
                        } else if (tb.zone == "residential") {
                            UIHelper.InfoRow("入住", tb.residents.toString() + "/" + cap)
                        } else {
                            UIHelper.InfoRow("在岗", tb.workers.toString() + "/" + cap)
                        }
                        UIHelper.InfoRow("供电", if (World.isCoveredBy(sel.first, sel.second, Config.ServiceCat.POWER)) "已通" else "断电")
                        UIHelper.InfoRow("供水", if (World.isCoveredBy(sel.first, sel.second, Config.ServiceCat.WATER)) "已通" else "缺水")
                    }
                    val tile = World.tile(sel.first, sel.second)
                    if (tile?.pipe == true) UIHelper.InfoRow("水管", "已铺")
                    if (tile?.cable == true) UIHelper.InfoRow("电缆", "已铺")
                    Networks.districtAt(sel.first, sel.second)?.let { d ->
                        UIHelper.InfoRow("区划", d.name + " · " + Networks.policyName(d.policy))
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

        // ---------------- 暂停按钮 ----------------
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 168.dp, end = 12.dp)
        ) {
            UIHelper.RoundButton("≡", size = 40.dp, fontSize = 20.sp) {
                Sfx.play("sfx_click", 0.6f)
                AppState.paused = !AppState.paused
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
                Text(
                    "覆盖图 · " + overlayLabel(AppState.overlay),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current
                )
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
                        AppState.mode in listOf("pipe", "cable", "bus", "district", "sewer", "metro", "tree", "raise", "lower")
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

        if (AppState.mode != "view" && !overlayOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 78.dp)
                    .size(56.dp)
                    .shadow(6.dp, RoundedCornerShape(28.dp))
                    .background(C.panelWhite.toColor(), RoundedCornerShape(28.dp))
                    .clickable {
                        Sfx.play("sfx_click")
                        MapScreen.cancelTool()
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_hand_pan),
                    contentDescription = "拖动地图",
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        if (AppState.policyOpen) PolicyPanel()
        if (AppState.helpOpen) HelpPanel()
        if (AppState.dataOpen) DataPanel()
        if (AppState.civicOpen) CivicPanel()
        if (AppState.achievementOpen) AchievementPanel()
        if (AppState.settingsOpen) SettingsPanel()
        if (AppState.complaintOpen && Civic.pending != null) ComplaintPanel()
        if (AppState.paused) PausePanel()
    }
}

@Composable
private fun ChipValue(label: String, value: String, color: Color, live: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (label.isNotEmpty()) {
            Text(
                label, fontSize = 10.sp, color = Config.COLORS.textMid.toColor(),
                fontFamily = LocalGameFont.current
            )
        }
        Text(
            value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color,
            fontFamily = LocalGameFont.current
        )
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
                                val poor = s.funds < sv.cost
                                UIHelper.PickChip(
                                    text = sv.name,
                                    sub = if (locked) "人口" + sv.unlockPop else "¥" + sv.cost + "万",
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
                    UIHelper.PickChip(
                        text = r.name,
                        sub = "¥" + r.cost + " 容" + r.capacity,
                        selected = AppState.roadKind == rk,
                        width = 90.dp
                    ) {
                        AppState.roadKind = rk
                        MapScreen.syncTool()
                        AppState.roadOpen = false
                    }
                }
            }
            Text(
                "按住拖拽连续修路；分区内邻路才会长楼。",
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
    val live = AppState.liveTick
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "规划 · 管网 / 公交 / 区划",
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = C.textDark.toColor(), fontFamily = LocalGameFont.current
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UIHelper.PickChip("水管", "2万/格", AppState.mode == "pipe", width = 86.dp) {
                AppState.mode = "pipe"
                AppState.overlay = "pipe"
                MapScreen.syncTool()
            }
            UIHelper.PickChip("电缆", "2万/格", AppState.mode == "cable", width = 86.dp) {
                AppState.mode = "cable"
                AppState.overlay = "cable"
                MapScreen.syncTool()
            }
            UIHelper.PickChip("污水管", "2万/格", AppState.mode == "sewer", width = 86.dp) {
                AppState.mode = "sewer"
                AppState.overlay = "sewer"
                MapScreen.syncTool()
            }
            UIHelper.PickChip("地铁隧", "6万/格", AppState.mode == "metro", width = 86.dp) {
                AppState.mode = "metro"
                AppState.overlay = "metro"
                MapScreen.syncTool()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UIHelper.PickChip("公交线", "${Transit.draft.size}站", AppState.mode == "bus", width = 86.dp) {
                AppState.mode = "bus"
                MapScreen.syncTool()
            }
            UIHelper.PickChip(
                "区划",
                Networks.districts.firstOrNull()?.name ?: "一区",
                AppState.mode == "district",
                width = 86.dp
            ) {
                Networks.ensureDistrict()
                AppState.mode = "district"
                AppState.overlay = "district"
                MapScreen.syncTool()
            }
        }
        Text(
            "水管 " + Networks.pipeCount + " 格 · 电缆 " + Networks.cableCount +
                " 格 · 公交 " + Transit.lines.size + " 条 · 乘客 " + Transit.ridership,
            fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
        )
        if (AppState.mode == "bus" || Transit.draft.isNotEmpty()) {
            Text(
                "按顺序点公交站/地铁站连线。草稿 " + Transit.draft.size + " 站。",
                fontSize = 10.sp, color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UIHelper.PickChip("确认开通", "≥2站", false, width = 110.dp) {
                    val (ok, msg) = Transit.confirmDraft()
                    mapView.setToast(msg ?: if (ok) "已开通" else "失败")
                    AppState.bumpLive()
                    AppState.bumpMap()
                }
                UIHelper.PickChip("清空草稿", "", false, width = 90.dp) {
                    Transit.clearDraft()
                    AppState.bumpLive()
                }
            }
        }
        Text(
            "区划政策（涂色后再选）",
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = C.textMid.toColor(), fontFamily = LocalGameFont.current
        )
        val d = Networks.districts.firstOrNull { it.id == Networks.activeDistrict }
            ?: Networks.districts.firstOrNull()
        if (d != null) Networks.DISTRICT_POLICIES.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    UIHelper.PickChip(
                        text = p.second,
                        sub = p.third,
                        selected = d.policy == p.first,
                        width = 140.dp
                    ) {
                        d.policy = p.first
                        AppState.bumpLive()
                    }
                }
            }
        }
        UIHelper.PickChip("新建区划", "再涂一块地", false, width = 140.dp) {
            Networks.addDistrict("新区" + Networks.nextDistrictId)
            AppState.mode = "district"
            MapScreen.syncTool()
            AppState.bumpLive()
        }
        Text(
            "美化 / 地形",
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = C.textMid.toColor(), fontFamily = LocalGameFont.current
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UIHelper.PickChip("种树", "1万/格", AppState.mode == "tree", width = 86.dp) {
                AppState.mode = "tree"; MapScreen.syncTool()
            }
            UIHelper.PickChip("抬升", "3万/格", AppState.mode == "raise", width = 86.dp) {
                AppState.mode = "raise"; MapScreen.syncTool()
            }
            UIHelper.PickChip("降低", "3万/格", AppState.mode == "lower", width = 86.dp) {
                AppState.mode = "lower"; MapScreen.syncTool()
            }
        }
        Text(
            "在岗 " + Citizens.employed +
                " · 通勤拥堵 " + (Citizens.avgCommute * 100).toInt() +
                "% · 污水管 " + Networks.sewerCount + " · 地铁 " + Networks.metroCount,
            fontSize = 10.sp, color = C.textFaint.toColor(), fontFamily = LocalGameFont.current
        )
        if (live < 0) Text("")
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
            Text(
                "市政政策", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
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

            // ---- 市政贷款 ----
            val loanState = when {
                s.loanDebt > 0 -> "还款中：剩余 " + floor(s.loanDebt).toInt() + " 万"
                s.loanCooldown > 0 -> "贷款冷却 " + s.loanCooldown + " 天"
                else -> "市政贷款 · 借 " + Config.LOAN.amount.toInt() + " 万"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(C.chipBg.toColor(), RoundedCornerShape(14.dp))
                    .border(1.dp, C.border2.toColor(), RoundedCornerShape(14.dp))
                    .clickable {
                        val (ok, msg) = GameData.borrow()
                        if (!ok) MapRef.view?.setToast(msg ?: "无法贷款") else Sfx.play("sfx_click")
                        AppState.bumpLive()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    loanState, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (s.loanDebt > 0) C.accentGold.toColor()
                    else if (s.loanCooldown > 0) C.textFaint.toColor() else C.accentRed.toColor(),
                    fontFamily = LocalGameFont.current
                )
            }

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
            Text(
                "新手指引 · 设身其中，经营一座虚构都市", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            HelpRow("手", "右下角手掌图标=退出建造并拖地图。修完路一定要点它，否则会继续铺路。")
            HelpRow("职", "顶栏【职】是虚构市政职级。人口和满意度达标后，还要通过任职测评才能晋升。暂停菜单可考试、处理市民来信。")
            HelpRow("路", "【道路】从大道边按住拖。泥土/两车道/四车道/高速可升级覆盖。路上只跑车辆，不显示行人。")
            HelpRow("区", "【住宅/商业/工业/办公】在路旁涂色，邻路才会长楼。房子建好就会迁入人口。")
            HelpRow("电", "先【服务】放风电/煤电（必须靠路）。再【规划】→电缆把电接到分区，数据面板开「电力」看绿/红色块。")
            HelpRow("水", "抽水站必须靠河。水塔可随处放。再用【规划】→水管接到房子，开「供水」热力图检查。")
            HelpRow("污", "污水处理厂 + 污水管。不接污水，水源会脏、健康下降。")
            HelpRow("策", "【策】里税率滑条可拖；政策启用后持续改税/需求/污染。点【数】看覆盖色块。")
            HelpRow("存", "右上【≡】保存到当前槽位。主菜单「存档管理」能看到城市名、人口、日期。")
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
    Config.ServiceCat.SAFETY -> "消防"
    "traffic" -> "拥堵"
    "landvalue" -> "地价"
    "pipe" -> "水管"
    "cable" -> "电缆"
    "sewer" -> "污水"
    "metro" -> "地铁"
    "district" -> "区划"
    else -> cat
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
            Text(
                "城市数据", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )

            // 覆盖率
            if (cov != null) {
                CovBar("电力", cov.power)
                CovBar("供水", cov.water)
                CovBar("垃圾", cov.garbage)
                CovBar("医疗", cov.health)
                CovBar("教育", cov.education)
                CovBar("消防", cov.safety)
                CovBar("殡葬", cov.death)
            }
            Text(
                "职级 " + GameData.rankDef().name + " · " + GameData.rankDef().perk +
                    (GameData.nextRank()?.let { " → 下一级 " + it.name + "（人口" + it.popReq + "/满意" + it.happyReq + "）" } ?: " · 已满级"),
                fontSize = 11.sp, color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "升学率 ${(Civic.schoolRate * 100).toInt()}% · 任职测评通过 ${Civic.examPassed} · 来信 ${Civic.complaintsHandled}",
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
                "今日 税 ${UIHelper.fmtMoney(s.dayIncomeTax)} · 产业 ${UIHelper.fmtMoney(s.dayIncomeBiz)} · 贸易 ${UIHelper.fmtMoney(s.dayIncomeTrade)} · 维护 -${UIHelper.fmtMoney(s.lastUpkeep)} · 净 ${UIHelper.fmtMoney(s.lastNet)}万",
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
                    "市政任务：" + q.name + " " + v.toInt() + "/" + q.target.toInt() +
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
                "本月累计 收入 ¥${UIHelper.fmtMoney(s.totalIncome)}万 · 支出 ¥${UIHelper.fmtMoney(s.totalSpent)}万",
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
                "traffic", "landvalue", "pipe", "cable", "sewer", "metro", "district"
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
            onValueChange = { onChange(it.roundToInt().coerceIn(minV, maxV)) },
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
            .noRippleClickable { AppState.paused = false },
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
                "职级 " + GameData.rankDef().name + " · " + GameData.rankDef().perk,
                fontSize = 12.sp, color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "当前槽位 " + (AppState.activeSlot + 1) +
                    if (s.lastSavedLabel.isNotEmpty()) " · 上次 " + s.lastSavedLabel else " · 尚未手动保存",
                fontSize = 11.sp, color = C.accentGold.toColor(), fontFamily = LocalGameFont.current
            )
            PauseBtn("继续游戏", C.accentGreen.toColor(), Color.White) {
                Sfx.play("sfx_click")
                AppState.paused = false
            }
            PauseBtn("设置 · 音量/广告", C.chipBg.toColor(), C.textDark.toColor()) {
                Sfx.play("sfx_click")
                AppState.paused = false
                AppState.settingsOpen = true
            }
            PauseBtn("市政任职 / 测评", C.chipBg.toColor(), C.textDark.toColor()) {
                Sfx.play("sfx_click")
                AppState.paused = false
                AppState.civicOpen = true
            }
            PauseBtn("市政成就", C.chipBg.toColor(), C.textDark.toColor()) {
                Sfx.play("sfx_click")
                AppState.paused = false
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
                AppState.paused = false
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
