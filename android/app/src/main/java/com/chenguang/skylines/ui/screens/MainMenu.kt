package com.chenguang.skylines.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenguang.skylines.AppState
import com.chenguang.skylines.Config
import com.chenguang.skylines.GameData
import com.chenguang.skylines.SaveManager
import com.chenguang.skylines.Sfx
import com.chenguang.skylines.ui.theme.LocalGameFont
import com.chenguang.skylines.ui.toColor
import com.chenguang.skylines.world.MapRenderView
import kotlin.math.floor
import kotlin.random.Random

// ============================================================================
// MainMenu — 标题画面 + 新游戏流程 + 存档管理，与端游基础体验对齐
// ============================================================================

private fun startGame(mapView: MapRenderView, name: String, seed: Int, difficulty: String, sandbox: Boolean) {
    GameData.seed = seed
    GameData.difficultyKey = difficulty
    GameData.sandbox = sandbox
    GameData.init(seed, name)
    AppState.overlay = ""
    AppState.mode = "view"
    AppState.paused = false
    mapView.resetCamera()
    mapView.clearSelection()
    AppState.screen = "map"
}

@Composable
fun MainMenuContent(mapView: MapRenderView) {
    when (AppState.menuScreen) {
        "newgame" -> NewGameScreen(mapView)
        "slots" -> SlotsScreen(mapView)
        else -> MainMenuScreen(mapView)
    }
}

@Composable
private fun MainMenuScreen(mapView: MapRenderView) {
    val C = Config.COLORS
    val recent = (0..2).firstOrNull { SaveManager.hasSlot(it) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.uiBackdrop.toColor()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 380.dp)
                .background(C.panelWhite.toColor(), RoundedCornerShape(24.dp))
                .padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                Config.World.country + " · 城建日报",
                fontSize = 10.sp, color = C.textMid.toColor(),
                fontFamily = LocalGameFont.current, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                Config.TITLE, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Text(
                Config.SUBTITLE, fontSize = 10.sp, color = C.textFaint.toColor(),
                fontFamily = LocalGameFont.current, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .height(1.dp)
                    .background(C.textDark.toColor())
            )

            if (recent != null) {
                MenuButton("继续游戏", C.accentGreen.toColor(), true) {
                    Sfx.play("sfx_click")
                    if (SaveManager.load(recent)) {
                        AppState.activeSlot = recent
                        AppState.overlay = ""
                        AppState.mode = "view"
                        mapView.resetCamera()
                        mapView.clearSelection()
                        AppState.screen = "map"
                    }
                }
            }

            MenuButton("新游戏", C.accentBlue.toColor(), true) {
                Sfx.play("sfx_click")
                AppState.newCityName = ""
                AppState.newSeed = 0
                AppState.newDifficulty = "normal"
                AppState.menuScreen = "newgame"
            }

            MenuButton("存档管理", C.chipBg.toColor(), false) {
                Sfx.play("sfx_click")
                AppState.menuScreen = "slots"
            }

            MenuButton("GM 模式（无限资源）", C.chipBg.toColor(), false) {
                Sfx.play("sfx_click")
                startGame(mapView, "沙盒之城", Random.nextInt(1, 100000), "normal", true)
            }

            Text(
                "全虚构世界观 · 玩法模拟经营 · 无现实机构指涉",
                fontSize = 9.sp, color = C.textFaint.toColor(), fontFamily = LocalGameFont.current,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun MenuButton(text: String, bg: Color, whiteText: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(bg, RoundedCornerShape(24.dp))
            .border(1.dp, Config.COLORS.border2.toColor(), RoundedCornerShape(24.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = if (whiteText) Color.White else Config.COLORS.textDark.toColor(),
            fontFamily = LocalGameFont.current
        )
    }
}

@Composable
private fun NewGameScreen(mapView: MapRenderView) {
    val C = Config.COLORS
    var name by remember { mutableStateOf("") }
    var seedText by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("normal") }

    Box(
        modifier = Modifier.fillMaxSize().background(C.uiBackdrop.toColor()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 380.dp)
                .verticalScroll(rememberScrollState())
                .background(C.panelWhite.toColor(), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "新建城市", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )

            // 城市命名
            Text(
                "给城市起个名字", fontSize = 12.sp, color = C.textMid.toColor(),
                fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = name,
                onValueChange = { if (it.length <= 8) name = it },
                placeholder = {
                    Text("晨光市", fontFamily = LocalGameFont.current, fontSize = 14.sp)
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = LocalGameFont.current, fontSize = 14.sp, color = C.textDark.toColor()
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = C.chipBg.toColor(),
                    unfocusedContainerColor = C.chipBg.toColor(),
                    focusedIndicatorColor = C.accentGreen.toColor(),
                    unfocusedIndicatorColor = C.border2.toColor(),
                    cursorColor = C.accentGreen.toColor()
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // 地图种子
            Text(
                "地图种子（不同种子不同地形）", fontSize = 12.sp, color = C.textMid.toColor(),
                fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = seedText,
                    onValueChange = { seedText = it.filter { ch -> ch.isDigit() }.take(9) },
                    placeholder = { Text("随机", fontFamily = LocalGameFont.current, fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = LocalGameFont.current, fontSize = 14.sp, color = C.textDark.toColor()
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = C.chipBg.toColor(),
                        unfocusedContainerColor = C.chipBg.toColor(),
                        focusedIndicatorColor = C.accentGreen.toColor(),
                        unfocusedIndicatorColor = C.border2.toColor(),
                        cursorColor = C.accentGreen.toColor()
                    ),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .background(C.accentSoftBg.toColor(), RoundedCornerShape(14.dp))
                        .clickable { seedText = Random.nextInt(1, 100000).toString() }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        "随机", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = C.accentRed.toColor(), fontFamily = LocalGameFont.current
                    )
                }
            }

            // 难度
            Text(
                "难度", fontSize = 12.sp, color = C.textMid.toColor(),
                fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (d in Config.DIFFICULTIES) {
                    val active = difficulty == d.key
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
                            .clickable { difficulty = d.key }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            d.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (active) C.accentRed.toColor() else C.textDark.toColor(),
                            fontFamily = LocalGameFont.current
                        )
                    }
                }
            }

            // 开始
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(C.accentGreen.toColor(), RoundedCornerShape(24.dp))
                    .clickable {
                        Sfx.play("sfx_click")
                        val cityName = if (name.isBlank()) "晨光市" else name.trim()
                        val seed = if (seedText.isBlank()) Random.nextInt(1, 100000) else seedText.toIntOrNull() ?: Random.nextInt(1, 100000)
                        startGame(mapView, cityName, seed, difficulty, false)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "开始建设", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    fontFamily = LocalGameFont.current
                )
            }

            Text(
                "返回", fontSize = 13.sp, color = C.textMid.toColor(),
                fontFamily = LocalGameFont.current, modifier = Modifier
                    .fillMaxWidth()
                    .clickable { AppState.menuScreen = "main" },
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SlotsScreen(mapView: MapRenderView) {
    val C = Config.COLORS
    val saveTick = AppState.saveTick
    Box(
        modifier = Modifier.fillMaxSize().background(C.uiBackdrop.toColor()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 380.dp)
                .background(C.panelWhite.toColor(), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "存档管理", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )
            for (slot in 0..2) {
                val meta = SaveManager.meta(slot)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(C.chipBg.toColor(), RoundedCornerShape(14.dp))
                        .border(1.dp, C.border2.toColor(), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    if (meta.exists) {
                        Text(
                            "槽位 ${slot + 1} · " + meta.cityName,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                        )
                        Text(
                            "人口 ${meta.population} · 资金 ¥${floor(meta.funds).toInt()}万 · " + meta.dateLabel,
                            fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(C.accentGreen.toColor(), RoundedCornerShape(16.dp))
                                    .clickable {
                                        Sfx.play("sfx_click")
                                        if (SaveManager.load(slot)) {
                                            AppState.activeSlot = slot
                                            AppState.overlay = ""
                                            AppState.mode = "view"
                                            mapView.resetCamera()
                                            mapView.clearSelection()
                                            AppState.screen = "map"
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "载入", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    fontFamily = LocalGameFont.current
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(C.accentRed.toColor(), RoundedCornerShape(16.dp))
                                    .clickable {
                                        SaveManager.delete(slot)
                                        AppState.saveTick++
                                    }
                                    .padding(horizontal = 18.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "删除", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                    fontFamily = LocalGameFont.current
                                )
                            }
                        }
                    } else {
                        Text(
                            "槽位 ${slot + 1} · 空",
                            fontSize = 13.sp, color = C.textFaint.toColor(), fontFamily = LocalGameFont.current
                        )
                    }
                }
            }
            Text(
                "返回", fontSize = 13.sp, color = C.textMid.toColor(),
                fontFamily = LocalGameFont.current, modifier = Modifier
                    .fillMaxWidth()
                    .clickable { AppState.menuScreen = "main" },
                textAlign = TextAlign.Center
            )
        }
    }
}
