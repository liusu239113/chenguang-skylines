package com.dshx.game.su.ui.screens

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
import com.dshx.game.su.Ads
import com.dshx.game.su.AppState
import com.dshx.game.su.Civic
import com.dshx.game.su.Config
import com.dshx.game.su.GameData
import com.dshx.game.su.MapRef
import com.dshx.game.su.Prefs
import com.dshx.game.su.Sfx
import com.dshx.game.su.SpeedBoost
import com.dshx.game.su.ui.theme.LocalGameFont
import com.dshx.game.su.ui.toColor
import kotlin.math.roundToInt

@Composable
fun CivicPanel() {
    val C = Config.COLORS
    val s = GameData.current ?: return
    val next = GameData.nextRank()
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
            Text("市政任职", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text("当前职级：" + GameData.rankDef().name, fontSize = 13.sp, color = C.accentBlue.toColor(), fontFamily = LocalGameFont.current)
            Text(GameData.rankDef().perk, fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
            Text("升学率 ${(Civic.schoolRate * 100).toInt()}% · 来信已处理 ${Civic.complaintsHandled} · 测评通过 ${Civic.examPassed}", fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current)
            if (next != null) {
                Text("下一职级 ${next.name}：人口 ${next.popReq} / 满意 ${next.happyReq}，并通过任职测评。", fontSize = 11.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            }
            if (Civic.examActive) {
                val q = Civic.examSession.getOrNull(Civic.examIndex)
                if (q != null) {
                    Text("任职测评 ${Civic.examIndex + 1}/${Civic.examSession.size}  得分 ${Civic.examScore}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
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
                        if (can) "开始任职测评（5 题）" else if (Civic.examCooldown > 0) "冷却 ${Civic.examCooldown} 天" else "条件未达，暂不可考",
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
            if (s.population < 0) Text("")
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
            Text("市民来信", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
            Text("市政成就 ${s.achievements.size}/${Config.ACHIEVEMENTS.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
fun SettingsPanel() {
    val C = Config.COLORS
    val ctx = LocalContext.current
    val act = ctx as? Activity
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
            Text("设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.textDark.toColor(), fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            var bgm by remember { mutableStateOf(Prefs.bgmVolume) }
            var sfx by remember { mutableStateOf(Prefs.sfxVolume) }
            Text("背景音乐 ${(bgm * 100).roundToInt()}%", fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Slider(value = bgm, onValueChange = { bgm = it; Prefs.bgmVolume = it }, valueRange = 0f..1f)
            Text("音效 ${(sfx * 100).roundToInt()}%", fontSize = 12.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current)
            Slider(value = sfx, onValueChange = { sfx = it; Prefs.sfxVolume = it }, valueRange = 0f..1f)
            Text(
                if (SpeedBoost.isActive()) "加速剩余 ${SpeedBoost.remainingSec() / 60} 分 ${SpeedBoost.remainingSec() % 60} 秒" else "2x/3x 加速需看广告解锁 20 分钟",
                fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(C.accentGold.toColor(), RoundedCornerShape(22.dp))
                    .clickable {
                        if (act == null) return@clickable
                        Ads.reward(act, {
                            GameData.current?.let { it.funds += 220 }
                            MapRef.view?.setToast("市政拨款 +220 万")
                            AppState.bumpLive()
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("看广告领取市政拨款", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(C.chipBg.toColor(), RoundedCornerShape(20.dp))
                    .clickable { AppState.settingsOpen = false },
                contentAlignment = Alignment.Center
            ) { Text("关闭", fontSize = 13.sp, color = C.textDark.toColor(), fontFamily = LocalGameFont.current) }
        }
    }
}


