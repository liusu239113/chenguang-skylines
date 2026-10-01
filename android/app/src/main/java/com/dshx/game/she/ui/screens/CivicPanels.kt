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
                    "营造档案", fontSize = 16.sp, fontWeight = FontWeight.Bold,
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
                "本作是完全架空的都市经营游戏，所有设定均为虚构，与现实无关。人口、幸福、测评、反馈都会推进档案。",
                fontSize = 10.sp, color = C.textFaint.toColor(), fontFamily = LocalGameFont.current
            )
            Text(
                "升学率 ${(Civic.schoolRate * 100).toInt()}% · 来信 ${Civic.complaintsHandled} · 测评通过 ${Civic.examPassed} · 资历 ${s.merit.toInt()}",
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
                            " · 测评 ${Civic.examPassed}/$examNeed" + (if (examOk) " ✓" else ""),
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
                    if (ready) "下一职「${next.name}」已达标，点晋升立刻到账 ${next.grant} 万并解锁新建设施。"
                    else "下一职「${next.name}」还差：人口 $popNeed · 满意 $hapNeed · 测评 $examNeedLeft 次。",
                    fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                )
                if (next.unlockIds.isNotEmpty()) {
                    Text(
                        "晋升解锁：" + next.unlockIds.mapNotNull { World.serviceConfig(it)?.name }.joinToString("、"),
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
                            "晋升为「${next.name}」 · 到账 ${next.grant} 万",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            fontFamily = LocalGameFont.current
                        )
                    }
                }
            } else {
                Text("已是最高营造等级。", fontSize = 11.sp, color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current)
            }
            if (Civic.examActive) {
                val q = Civic.examSession.getOrNull(Civic.examIndex)
                if (q != null) {
                    Text(
                        "营造测评 ${Civic.examIndex + 1}/${Civic.examSession.size}  得分 ${Civic.examScore}",
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
                        if (can) "开始营造测评（5 题）"
                        else if (Civic.examPassed >= (next?.level ?: 1) - 1 && next != null) "测评已过，达标即可晋升"
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
                Text("居民反馈", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
                Text("营造成就 ${s.achievements.size}/${Config.ACHIEVEMENTS.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
        "daily" -> "每周营造礼包" to "每周一次。看广告金库到账 120 万。"
        "shortfall" -> "资金不够" to (s.lastShortAction + "还差钱。看广告可拿到应急拨款。")
        "bailout" -> "账面告急" to "金库见底。看广告可获得纾困拨款 260 万。"
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
                Text(
                    "把下面这串码发给别人，对方在「导入城市」里粘贴即可进入你的城市。",
                    fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 130.dp)
                        .background(C.chipBg.toColor(), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        code, fontSize = 9.sp, color = C.textDark.toColor(),
                        fontFamily = LocalGameFont.current
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(C.accentGreen.toColor(), RoundedCornerShape(19.dp))
                        .clickable {
                            Sfx.play("sfx_click")
                            try {
                                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("sharecode", code))
                                msg = "分享码已复制到剪贴板"
                            } catch (t: Throwable) {
                                msg = "复制失败，请手动长按选择"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("复制分享码", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current) }
                if (s \!= null) {
                    Text(
                        "含：${s.cityName} · 人口 ${s.population.toInt()} · 幸福 ${kotlin.math.floor(s.happiness).toInt()}",
                        fontSize = 10.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                    )
                }
            }

            // ---- 导入 ----
            Text("② 导入别人的城市", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text(
                "粘贴别人给的分享码，会占用一个空存档槽（最多 6 个）。",
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
                    placeholder = { Text("在此粘贴分享码（CS1. 开头）", fontSize = 11.sp, fontFamily = LocalGameFont.current) },
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
                            msg = "请先粘贴分享码"
                            return@clickable
                        }
                        if (\!com.dshx.game.she.ShareCode.looksLikeCode(text)) {
                            msg = "分享码格式不对（应以 CS1. 开头）"
                            return@clickable
                        }
                        val free = (0 until com.dshx.game.she.SaveManager.SLOT_COUNT)
                            .firstOrNull { \!com.dshx.game.she.SaveManager.hasSlot(it) }
                        val target = free ?: AppState.activeSlot
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
                            msg = "导入失败：分享码已损坏"
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text("导入并载入", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current) }

            msg?.let {
                Text(it, fontSize = 11.sp, color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current)
            }
        }
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
            BenefitBtn("每周礼包 +120 万", if (AdOffers.weeklyClaimed) "本周已领" else "每周一次", act, "daily", \!AdOffers.weeklyClaimed)
            BenefitBtn("经营收入加倍 12 天", "短期进项翻倍", act, "doubletax", true)
            BenefitBtn("民心安抚 +8", "幸福度立刻回升", act, "happy", true)
            BenefitBtn("营造拨款 +90 万", "应急用", act, "grant", true)

            Text("便利工具", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current)
            BenefitBtn("3x 加速 30 分钟", if (SpeedBoost.isActive()) "生效中 · 剩余 ${SpeedBoost.remainingSec() / 60} 分" else "省时间", act, "speed", true)
            BenefitBtn("全城垃圾清运", "立刻清空积压", act, "cleartrash", true)
            BenefitBtn("修复废弃建筑", "恢复入住", act, "repair", true)
            BenefitBtn("解锁圈外扩一圈", "立刻扩地", act, "unlock", true)

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
