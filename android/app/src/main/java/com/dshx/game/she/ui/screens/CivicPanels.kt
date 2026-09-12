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
                "当前职级：" + cur.name + "  Lv." + cur.level,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )
            Text(cur.perk, fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
            Text(
                "这是虚构的城市建设资历，不是现实官职。人口、满意、测评、来信都会推进档案。",
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
                Text("已是最高营造职级。", fontSize = 11.sp, color = C.accentGreen.toColor(), fontFamily = LocalGameFont.current)
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
                Text("市民来信", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
        "bailout" -> "财政告急" to "金库见底。看广告可获得纾困拨款 260 万。"
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
            Text(
                if (SpeedBoost.isActive()) "加速剩余 ${SpeedBoost.remainingSec() / 60} 分 ${SpeedBoost.remainingSec() % 60} 秒" else "2x、3x 都要看广告解锁 20 分钟",
                fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Text("看广告能拿到这些（看完才到账，不是空点）：", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Text("①每周礼包 +120 万  ②税收加倍 12 天  ③民心安抚 满意+8  ④营造拨款 +90 万。缺钱修路时还会弹应急拨款。银行低息贷也在左上【银】。", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
            AdBtn("每周礼包 +120万", !AdOffers.weeklyClaimed, act, "daily")
            AdBtn("税收加倍 12 天", true, act, "doubletax")
            AdBtn("民心安抚 满意+8", true, act, "happy")
            AdBtn("营造拨款 +90万", true, act, "grant")
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
