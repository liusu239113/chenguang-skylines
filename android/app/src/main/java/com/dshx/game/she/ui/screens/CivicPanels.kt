package com.dshx.game.she.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dshx.game.she.AdOffers
import com.dshx.game.she.Ads
import com.dshx.game.she.AppState
import com.dshx.game.she.Civic
import com.dshx.game.she.Config
import com.dshx.game.she.GameData
import com.dshx.game.she.MapRef
import com.dshx.game.she.Prefs
import com.dshx.game.she.PrivacyDocs
import com.dshx.game.she.Sfx
import com.dshx.game.she.SpeedBoost
import com.dshx.game.she.ui.UIHelper
import com.dshx.game.she.ui.theme.LocalGameFont
import com.dshx.game.she.ui.toColor
import com.dshx.game.she.world.Traffic
import com.dshx.game.she.world.World
import kotlin.math.roundToInt

@Composable
fun CivicPanel() {
    val C = Config.COLORS
    val live = AppState.liveTick
    val s = GameData.current ?: return
    val next = GameData.nextRank()
    val cur = GameData.rankDef()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .clickable { AppState.civicOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp)
                .clickable(enabled = false) {},
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "项目档案", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { AppState.civicOpen = false }.padding(4.dp)
                )
            }
            Text(
                s.mayorName + " · " + Config.World.playerRole,
                fontSize = 13.sp, color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "当前等级：" + cur.name + "  Lv." + cur.level,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )
            Text(cur.perk, fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
            Text(
                "本作是完全架空的都市经营游戏，所有设定均为虚构，与现实无关。人口、幸福、评级、反馈都会推进档案。",
                fontSize = 10.sp, color = C.textFaint.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "升学率 ${(Civic.schoolRate * 100).toInt()}% · 反馈 ${Civic.complaintsHandled} · 评级通过 ${Civic.examPassed} · 资历 ${s.merit.toInt()}",
                fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "外环高速 " + (if (World.current?.highwayConnected == true) "已接通" else "未接通") +
                    " · 繁荣 " + (World.current?.prosperity ?: 0) +
                    " · 路上车辆 " + Traffic.localMoving +
                    " · 外地车 " + Traffic.visitorsToday,
                fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "到港货轮 " + Traffic.shipVisits + " 艘次 · 航班 " + Traffic.flightVisits + " 架次 · 列车 " + Traffic.trainVisits + " 车次 · 今日客运 " +
                    Traffic.passengersToday + " 人 · 今日贸易 " + UIHelper.fmtFunds(s.dayIncomeTrade),
                fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            for (r in Config.RANKS) {
                val done = s.rankLevel >= r.level
                val current = s.rankLevel == r.level
                val popOk = s.population >= r.popReq
                val hapOk = s.happiness >= r.happyReq
                val examNeed = (r.level - 1).coerceAtLeast(0)
                val examOk = Civic.examPassed >= examNeed
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (current) C.accentSoftBg.toColor() else C.chipBg.toColor(),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            if (current) C.accentRed.toColor() else C.border2.toColor(),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        "Lv.${r.level} ${r.name}" + if (done) "  ✓" else "",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                    )
                    Text(r.perk, fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                    Text(
                        "人口 ${s.population.toInt()}/${r.popReq}" + (if (popOk) " ✓" else "") +
                            " · 满意 ${s.happiness.toInt()}/${r.happyReq}" + (if (hapOk) " ✓" else "") +
                            " · 评级 ${Civic.examPassed}/$examNeed" + (if (examOk) " ✓" else ""),
                        fontSize = 10.sp,
                        color = if (done) C.accentGreen.toColor() else C.textMid.toColor(),
                        fontFamily = LocalGameFont.current
                    )
                }
            }
            if (next != null) {
                val popNeed = (next.popReq - s.population).coerceAtLeast(0.0).toInt()
                val hapNeed = (next.happyReq - s.happiness).coerceAtLeast(0.0).toInt()
                val examNeedLeft = ((next.level - 1) - Civic.examPassed).coerceAtLeast(0)
                val ready = popNeed == 0 && hapNeed == 0 && examNeedLeft == 0
                Text(
                    if (ready) "下一职「${next.name}」已达标，点升职立刻到账 ${next.grant} 万并解锁新建设施。"
                    else "下一职「${next.name}」还差：人口 $popNeed · 满意 $hapNeed · 评级 $examNeedLeft 次。",
                    fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                )
                if (next.unlockIds.isNotEmpty()) {
                    Text(
                        "升职解锁：" + next.unlockIds.mapNotNull { World.serviceConfig(it)?.name }.joinToString("、"),
                        fontSize = 10.sp, color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current
                    )
                }
                if (ready) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(C.accentRed.toColor(), RoundedCornerShape(22.dp))
                            .clickable {
                                Sfx.play("sfx_levelup")
                                GameData.refreshRank()
                                AppState.bumpLive()
                                MapRef.view?.setToast("升为「" + GameData.rankDef().name + "」")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "升职为「${next.name}」 · 到账 ${next.grant} 万",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            fontFamily = LocalGameFont.current
                        )
                    }
                }
            } else {
                Text("已是最高项目等级。", fontSize = 11.sp, color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current)
            }
            if (Civic.examActive) {
                val q = Civic.examSession.getOrNull(Civic.examIndex)
                if (q != null) {
                    Text(
                        "项目评级 ${Civic.examIndex + 1}/${Civic.examSession.size}  得分 ${Civic.examScore}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                    )
                    Text(q.q, fontSize = 13.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                    q.options.forEachIndexed { i, opt ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(C.chipBg.toColor(), RoundedCornerShape(12.dp))
                                .border(1.dp, C.border2.toColor(), RoundedCornerShape(12.dp))
                                .clickable {
                                    Sfx.play("sfx_click")
                                    Civic.answer(i)
                                    AppState.bumpLive()
                                }
                                .padding(10.dp)
                        ) {
                            Text(opt, fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                        }
                    }
                }
            } else {
                val can = Civic.canTakeExam()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(if (can) C.accentGreen.toColor() else C.chipBg.toColor(), RoundedCornerShape(22.dp))
                        .clickable(enabled = can) {
                            Sfx.play("sfx_click")
                            Civic.startExam()
                            AppState.bumpLive()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (can) "开始项目评级（5 题）"
                        else if (Civic.examPassed >= (next?.level ?: 1) - 1 && next != null) "评级已过，达标即可升职"
                        else if (Civic.examCooldown > 0) "冷却 ${Civic.examCooldown} 天后再考"
                        else "人口/满意还不够，暂不可考",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (can) Color.White else C.textMid.toColor(),
                        fontFamily = LocalGameFont.current
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.chipBg.toColor(), RoundedCornerShape(20.dp))
                    .clickable { AppState.civicOpen = false },
                contentAlignment = Alignment.Center
            ) { Text("关闭", fontSize = 13.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current) }
            if (live < 0) Text("")
        }
    }
}

@Composable
fun ComplaintPanel() {
    val C = Config.COLORS
    val c = Civic.pending ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text("客户反馈", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable {
                        Civic.pending = null
                        AppState.complaintOpen = false
                    }.padding(4.dp)
                )
            }
            Text("来自 " + c.from, fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
            Text(c.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.accentRed.toColor(), fontFamily = LocalGameFont.current)
            Text(c.body, fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.accentGreen.toColor(), RoundedCornerShape(14.dp))
                    .clickable { Sfx.play("sfx_click"); Civic.resolve(true) }
                    .padding(12.dp)
            ) { Text(c.a, fontSize = 12.sp, color = Color.White, fontFamily = LocalGameFont.current) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.chipBg.toColor(), RoundedCornerShape(14.dp))
                    .border(1.dp, C.border2.toColor(), RoundedCornerShape(14.dp))
                    .clickable { Sfx.play("sfx_click"); Civic.resolve(false) }
                    .padding(12.dp)
            ) { Text(c.b, fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current) }
        }
    }
}

@Composable
fun AchievementPanel() {
    val C = Config.COLORS
    val s = GameData.current ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .clickable { AppState.achievementOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text("项目成就 ${s.achievements.size}/${Config.ACHIEVEMENTS.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { AppState.achievementOpen = false }.padding(4.dp)
                )
            }
            for (a in Config.ACHIEVEMENTS) {
                val done = a.id in s.achievements
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (done) C.accentSoftBg.toColor() else C.chipBg.toColor(), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(a.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
                        Text(a.desc + " · 奖 " + a.reward + "万", fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
                    }
                    Text(if (done) "已达成" else "未达成", fontSize = 11.sp, color = if (done) C.accentGreen.toColor() else C.textFaint.toColor(), fontFamily = LocalGameFont.current)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.chipBg.toColor(), RoundedCornerShape(20.dp))
                    .clickable { AppState.achievementOpen = false },
                contentAlignment = Alignment.Center
            ) { Text("关闭", fontSize = 13.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current) }
        }
    }
}

@Composable
private fun AdBtn(text: String, enabled: Boolean, act: Activity?, kind: String) {
    val C = Config.COLORS
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(if (enabled) C.accentGold.toColor() else C.chipBg.toColor(), RoundedCornerShape(21.dp))
            .clickable(enabled = enabled) {
                if (act == null) return@clickable
                Ads.reward(act, { AdOffers.grant(kind) })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (enabled) "看广告 · $text" else "今日已领",
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else C.textMid.toColor(),
            fontFamily = LocalGameFont.current
        )
    }
}

@Composable
fun AdOfferDialog() {
    val C = Config.COLORS
    val s = GameData.current ?: return
    val act = LocalContext.current as? Activity
    val kind = AppState.adOfferKind
    val (title, body) = when (kind) {
        "daily" -> "每周项目礼包" to "每周一次。看广告金库到账 120 万。"
        "shortfall" -> "资金不够" to (s.lastShortAction + "还差钱。看广告可拿到应急注资。")
        "bailout" -> "账面告急" to "金库见底。看广告可获得纾困注资 260 万。"
        else -> return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text(body, fontSize = 12.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current, textAlign = TextAlign.Center)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(C.accentGold.toColor(), RoundedCornerShape(22.dp))
                    .clickable {
                        if (act == null) return@clickable
                        Ads.reward(act, { AdOffers.grant(kind) })
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("看广告领取", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current)
            }
            Text(
                "先不看",
                fontSize = 12.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                modifier = Modifier.clickable {
                    AppState.adOfferOpen = false
                    AppState.adOfferKind = ""
                }
            )
        }
    }
}

@Composable
fun SettingsPanel() {
    val C = Config.COLORS
    val ctx = LocalContext.current
    val act = ctx as? Activity
    val live = AppState.liveTick
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .clickable { AppState.settingsOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp)
                .clickable(enabled = false) {},
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text("设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd).clickable { AppState.settingsOpen = false }.padding(4.dp)
                )
            }
            var bgm by remember { mutableStateOf(Prefs.bgmVolume) }
            var sfx by remember { mutableStateOf(Prefs.sfxVolume) }
            Text("背景音乐 ${(bgm * 100).roundToInt()}%（默认已调低）", fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Slider(
                value = bgm,
                onValueChange = {
                    bgm = it
                    Prefs.bgmVolume = it
                    AppState.bumpLive()
                },
                valueRange = 0f..1f
            )
            Text("音效 ${(sfx * 100).roundToInt()}%", fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Slider(
                value = sfx,
                onValueChange = {
                    sfx = it
                    Prefs.sfxVolume = it
                    AppState.bumpLive()
                },
                valueRange = 0f..1f
            )
            Text("字体", fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (opt in listOf(false to "手写体（默认）", true to "黑体（清晰）")) {
                    val active = AppState.useSystemFont == opt.first
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (active) C.accentSoftBg.toColor() else C.chipBg.toColor(),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (active) C.accentRed.toColor() else C.border2.toColor(),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                Sfx.play("sfx_click")
                                AppState.useSystemFont = opt.first
                                Prefs.useSystemFont = opt.first
                                MapRef.view?.applyFontTypeface()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            opt.second,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (active) C.accentRed.toColor() else C.textDark.toColor(),
                            fontFamily = LocalGameFont.current
                        )
                    }
                }
            }
            Text(
                if (SpeedBoost.isActive()) "加速剩余 ${SpeedBoost.remainingSec() / 60} 分 ${SpeedBoost.remainingSec() % 60} 秒" else "2x、3x 都要看广告解锁 20 分钟",
                fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            // 分享码：把自己的城市导出，或导入别人的城市
            Text("城市分享码", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text(
                "生成一串分享码发给别人，对方导入后就能进入和你一模一样的城市（地形、建筑、道路全部还原）。",
                fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.accentBlue.toColor(), RoundedCornerShape(20.dp))
                    .clickable {
                        Sfx.play("sfx_click")
                        AppState.settingsOpen = false
                        AppState.shareOpen = true
                    },
                contentAlignment = Alignment.Center
            ) { Text("分享 / 导入城市", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current) }

            Text(
                "广告奖励已移到左侧【福】按钮，那里领更快。银行低息贷在【银】。",
                fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.accentGold.toColor(), RoundedCornerShape(20.dp))
                    .clickable {
                        Sfx.play("sfx_click")
                        AppState.settingsOpen = false
                        AppState.benefitOpen = true
                    },
                contentAlignment = Alignment.Center
            ) { Text("打开福利中心", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.chipBg.toColor(), RoundedCornerShape(20.dp))
                    .clickable {
                        try {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(PrivacyDocs.POLICY_URL)
                                )
                            )
                        } catch (_: Throwable) {}
                    },
                contentAlignment = Alignment.Center
            ) { Text("查看《隐私政策》", fontSize = 13.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.chipBg.toColor(), RoundedCornerShape(20.dp))
                    .clickable { AppState.settingsOpen = false },
                contentAlignment = Alignment.Center
            ) { Text("关闭", fontSize = 13.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current) }
            if (live < 0) Text("")
        }
    }
}

/**
 * 分享 / 导入城市。
 * 导出：把当前城市压成分享码，复制发给别人。
 * 导入：粘贴别人的分享码，写进空闲槽位后载入。
 */
@Composable
fun SharePanel() {
    val C = Config.COLORS
    val ctx = LocalContext.current
    val s = GameData.current
    var importText by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    // 导出码：进入面板时生成一次
    val code = remember { com.dshx.game.she.ShareCode.export() }
    // 二维码位图：进入面板时生成一次（整座城市都在里面）
    val qrBitmap = remember(code) { code?.let { com.dshx.game.she.QrCode.encode(it, 640) } }
    var scanning by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .clickable { AppState.shareOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .heightIn(max = 520.dp)
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "分享 / 导入城市", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .clickable { AppState.shareOpen = false }.padding(4.dp)
                )
            }

            // ---- 导出 ----
            Text("① 导出我的城市", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            if (code == null) {
                Text("当前没有可导出的城市", fontSize = 11.sp, color = C.accentRed.toColor(), fontFamily = LocalGameFont.current)
            } else {
                // 主推：短种子码（纯数字，和开局地图种子同源）
                val seed = com.dshx.game.she.ShareCode.seedCode()
                val entry = seed?.toIntOrNull()?.let { com.dshx.game.she.SeedLib.find(it) }
                Text(
                    "地图种子（推荐分享这个）", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = C.accentGold.toColor(), fontFamily = LocalGameFont.current
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(C.chipBg.toColor(), RoundedCornerShape(10.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        seed ?: "-", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                    )
                }
                if (entry != null) {
                    Text(
                        entry.name + " · " + entry.tag + " · " + entry.desc,
                        fontSize = 10.sp, color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current
                    )
                }
                Text(
                    "对方在开局「地图种子」里填这个数字，就能得到和你一样的地形。",
                    fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(C.accentGreen.toColor(), RoundedCornerShape(19.dp))
                        .clickable {
                            Sfx.play("sfx_click")
                            try {
                                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("seed", seed ?: ""))
                                msg = "地图种子已复制"
                            } catch (t: Throwable) {
                                msg = "复制失败，请手动选择"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("复制地图种子", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current) }

                // 二维码：整座城市（含建筑）都塞进码里，另一台设备扫一下就导入
                Text(
                    "扫码分享（推荐）", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current
                )
                Text(
                    "让对方用本作「扫码导入」扫下面这张码，或存图发论坛，整座城市连同建筑一起还原。",
                    fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                )
                val qr = qrBitmap
                if (qr != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "城市二维码",
                            modifier = Modifier.size(190.dp)
                        )
                    }
                } else {
                    Text(
                        "二维码生成失败（城市数据过大），请改用地图种子分享。",
                        fontSize = 10.sp, color = C.accentRed.toColor(), fontFamily = LocalGameFont.current
                    )
                }
                val cur = GameData.current
                if (cur != null) {
                    Text(
                        "含：" + cur.cityName + " · 人口 " + cur.population.toInt() +
                            " · 幸福 " + kotlin.math.floor(cur.happiness).toInt(),
                        fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                    )
                }
            }

            // ---- 导入 ----
            Text("② 导入别人的城市", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text(
                "填别人的地图种子（数字）即可生成同样的地形；粘贴完整城市码则连建筑一起还原。",
                fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 70.dp, max = 110.dp)
                    .background(C.chipBg.toColor(), RoundedCornerShape(10.dp))
                    .padding(6.dp)
            ) {
                androidx.compose.material3.TextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text("填地图种子数字，或粘贴 CS1. 开头的城市码", fontSize = 11.sp, fontFamily = LocalGameFont.current) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 10.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                    ),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = C.chipBg.toColor(),
                        unfocusedContainerColor = C.chipBg.toColor(),
                        focusedIndicatorColor = C.accentGreen.toColor(),
                        unfocusedIndicatorColor = C.border2.toColor(),
                        cursorColor = C.accentGreen.toColor()
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(C.accentBlue.toColor(), RoundedCornerShape(19.dp))
                    .clickable {
                        Sfx.play("sfx_click")
                        val text = importText.trim()
                        if (text.isEmpty()) {
                            msg = "请先填种子或粘贴城市码"
                            return@clickable
                        }
                        val free = (0 until com.dshx.game.she.SaveManager.SLOT_COUNT)
                            .firstOrNull { !com.dshx.game.she.SaveManager.hasSlot(it) }
                        val target = free ?: AppState.activeSlot

                        if (com.dshx.game.she.ShareCode.looksLikeCode(text)) {
                            // 完整城市码：连建筑一起还原
                            if (com.dshx.game.she.ShareCode.import(text, target)) {
                                if (com.dshx.game.she.SaveManager.load(target)) {
                                    AppState.activeSlot = target
                                    com.dshx.game.she.Prefs.lastSlot = target
                                    AppState.shareOpen = false
                                    AppState.menuOpen = false
                                    MapRef.view?.resetCamera()
                                    MapRef.view?.clearSelection()
                                    MapRef.view?.setToast("已载入分享的城市（槽位 " + (target + 1) + "）")
                                } else {
                                    msg = "导入失败：存档无法载入"
                                }
                            } else {
                                msg = "导入失败：城市码已损坏"
                            }
                        } else {
                            // 短种子：用这个数字新开一座同地形的城市
                            val seed = com.dshx.game.she.ShareCode.parseSeedCode(text)
                            if (seed == null) {
                                msg = "请填 1~9 位数字的种子，或 CS1. 开头的城市码"
                                return@clickable
                            }
                            val e = com.dshx.game.she.SeedLib.find(seed)
                            AppState.shareOpen = false
                            AppState.menuOpen = false
                            com.dshx.game.she.GameData.seed = seed
                            com.dshx.game.she.GameData.init(seed, "星野新城")
                            AppState.activeSlot = target
                            com.dshx.game.she.Prefs.lastSlot = target
                            com.dshx.game.she.SaveManager.save(target)
                            AppState.saveTick++
                            MapRef.view?.resetCamera()
                            MapRef.view?.clearSelection()
                            val tip = if (e != null) e.name + "（" + e.tag + "）" else "种子 " + seed
                            MapRef.view?.setToast("已按 $tip 生成新地图")
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text("按种子开新城 / 导入并载入", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current) }

            // 扫码导入
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(C.accentGreen.toColor(), RoundedCornerShape(19.dp))
                    .clickable {
                        Sfx.play("sfx_click")
                        scanning = true
                    },
                contentAlignment = Alignment.Center
            ) { Text("扫码导入（用相机扫别人的二维码）", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current) }

            msg?.let {
                Text(it, fontSize = 11.sp, color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current)
            }
        }
    }

    // 扫码界面（覆盖在最上层）
    if (scanning) {
        com.dshx.game.she.QrScanScreen(
            onResult = { text ->
                scanning = false
                val free = (0 until com.dshx.game.she.SaveManager.SLOT_COUNT)
                    .firstOrNull { !com.dshx.game.she.SaveManager.hasSlot(it) }
                val target = free ?: AppState.activeSlot
                if (com.dshx.game.she.ShareCode.import(text, target) &&
                    com.dshx.game.she.SaveManager.load(target)
                ) {
                    AppState.activeSlot = target
                    com.dshx.game.she.Prefs.lastSlot = target
                    AppState.shareOpen = false
                    AppState.menuOpen = false
                    MapRef.view?.resetCamera()
                    MapRef.view?.clearSelection()
                    MapRef.view?.setToast("扫码成功，已载入对方城市（槽位 " + (target + 1) + "）")
                } else {
                    msg = "扫码内容不是有效的城市码"
                }
            },
            onClose = { scanning = false }
        )
    }
}

/**
 * 福利中心（广告入口）。
 * 放在左侧「福」按钮，玩家一眼能看到，比藏在设置里转化高得多。
 * 分成两组：经营奖励（给钱/民心）与便利工具（加速/清运/修复/扩圈）。
 */
@Composable
fun BenefitPanel() {
    val C = Config.COLORS
    val act = LocalContext.current as? Activity
    val live = AppState.liveTick
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.veil.toColor())
            .clickable { AppState.benefitOpen = false },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .heightIn(max = 540.dp)
                .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                .padding(16.dp)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "福利中心", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
                Text(
                    "×", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = C.textMid.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .clickable { AppState.benefitOpen = false }.padding(4.dp)
                )
            }
            Text(
                "看广告领取，看完才到账。奖励即时生效，不用等。",
                fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )

            Text("经营奖励", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.accentGold.toColor(), fontFamily = LocalGameFont.current)
            BenefitBtn("每周礼包 +120 万", (if (AdOffers.weeklyClaimed) "本周已领" else "每周一次"), act, "daily", !AdOffers.weeklyClaimed)
            BenefitBtn("招商旺季 · 收益翻倍 12 天", "攒钱冲大工程", act, "boom", true)
            BenefitBtn("民心安抚 +8", "满意度立刻回升", act, "happy", true)
            BenefitBtn("项目注资 +90 万", "应急用", act, "grant", true)

            Text("限时增益（真实时间计时，退出也保留）", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current)
            BenefitBtn(
                "满意度守护",
                (com.dshx.game.she.Buffs.remainText(com.dshx.game.she.Buffs.NO_HAPPY_DROP)?.let { "剩余 " + it } ?: "30 分钟内不下降"),
                act, "buff_happy", true
            )
            BenefitBtn(
                "入住加速",
                (com.dshx.game.she.Buffs.remainText(com.dshx.game.she.Buffs.FAST_GROWTH)?.let { "剩余 " + it } ?: "30 分钟内居民增长翻倍"),
                act, "buff_growth", true
            )
            BenefitBtn(
                "收益提升",
                (com.dshx.game.she.Buffs.remainText(com.dshx.game.she.Buffs.INCOME_BOOST)?.let { "剩余 " + it } ?: "30 分钟内收益 +50%"),
                act, "buff_income", true
            )
            BenefitBtn(
                "免维护费",
                (com.dshx.game.she.Buffs.remainText(com.dshx.game.she.Buffs.NO_UPKEEP)?.let { "剩余 " + it } ?: "30 分钟内不收维护费"),
                act, "buff_upkeep", true
            )

            Text("便利工具", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current)
            BenefitBtn("即刻入住", "空置住宅一次住满，人口立刻涨", act, "movein", true)
            BenefitBtn("升级提速", "全城建筑立刻升一级", act, "upgrade", true)
            BenefitBtn("纾困清运", "清空垃圾并修复废弃建筑", act, "relief", true)
            BenefitBtn("免息周转", "立刻结清全部贷款", act, "nointerest", true)
            BenefitBtn("方案速批", "清空方案冷却，想开就开", act, "fastpolicy", true)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(C.chipBg.toColor(), RoundedCornerShape(19.dp))
                    .clickable { AppState.benefitOpen = false },
                contentAlignment = Alignment.Center
            ) { Text("关闭", fontSize = 13.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current) }
            if (live < 0) Text("")
        }
    }
}

/** 福利中心里的一个广告按钮 */
@Composable
private fun BenefitBtn(title: String, sub: String, act: Activity?, kind: String, enabled: Boolean) {
    val C = Config.COLORS
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (enabled) C.accentGold.toColor() else C.border2.toColor(),
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled) {
                if (act == null) return@clickable
                Sfx.play("sfx_click", 0.5f)
                Ads.reward(act, { AdOffers.grant(kind) })
            }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else C.textMid.toColor(),
                fontFamily = LocalGameFont.current
            )
            Text(
                sub, fontSize = 10.sp,
                color = if (enabled) Color.White.copy(alpha = 0.85f) else C.textFaint.toColor(),
                fontFamily = LocalGameFont.current
            )
        }
    }
}
