package com.dshx.game.she.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.dshx.game.she.R
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
import com.dshx.game.she.AppState
import com.dshx.game.she.Config
import com.dshx.game.she.GameData
import com.dshx.game.she.SaveManager
import com.dshx.game.she.Sfx
import com.dshx.game.she.ui.theme.LocalGameFont
import com.dshx.game.she.ui.toColor
import com.dshx.game.she.world.MapRenderView
import kotlin.math.floor
import kotlin.random.Random

// ============================================================================
// MainMenu — 标题画面 + 新游戏流程 + 存档管理，与端游基础体验对齐
// ============================================================================

private fun startGame(
    mapView: MapRenderView,
    name: String,
    mayor: String,
    seed: Int,
    difficulty: String,
    slot: Int,
    sandbox: Boolean = false
) {
    GameData.seed = seed
    GameData.difficultyKey = difficulty
    GameData.sandbox = sandbox
    GameData.init(seed, name, mayor)
    AppState.activeSlot = slot
    com.dshx.game.she.Prefs.lastSlot = slot
    SaveManager.save(slot)
    AppState.saveTick++
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
    val saveTick = AppState.saveTick
    val recent = (0 until com.dshx.game.she.SaveManager.SLOT_COUNT).firstOrNull { it == com.dshx.game.she.Prefs.lastSlot && SaveManager.hasSlot(it) }
        ?: (0 until com.dshx.game.she.SaveManager.SLOT_COUNT).firstOrNull { SaveManager.hasSlot(it) }
    val recentMeta = recent?.let { SaveManager.meta(it) }
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg_main_menu),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x33000000))
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_title_logo),
                contentDescription = Config.TITLE,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(horizontal = 12.dp),
                contentScale = ContentScale.Fit
            )
            if (recent != null && recentMeta != null && recentMeta.exists) {
                MenuButton(
                    "继续游戏 · " + recentMeta.cityName,
                    C.accentGreen.toColor(), true
                ) {
                    Sfx.play("sfx_click")
                    if (SaveManager.load(recent)) {
                        AppState.activeSlot = recent
                        com.dshx.game.she.Prefs.lastSlot = recent
                        AppState.overlay = ""
                        AppState.mode = "view"
                        mapView.resetCamera()
                        mapView.clearSelection()
                        AppState.screen = "map"
                    }
                }
                Text(
                    recentMeta.levelName + " · 人口 " + recentMeta.population +
                        " · " + recentMeta.dateLabel + " · 槽位 " + (recent + 1),
                    fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f),
                    fontFamily = LocalGameFont.current, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            MenuButton("新游戏", C.accentBlue.toColor(), true) {
                Sfx.play("sfx_click")
                AppState.newCityName = ""
                AppState.newSeed = 0
                AppState.newDifficulty = "normal"
                AppState.menuScreen = "newgame"
            }
            MenuButton("存档管理", C.panelWhite.toColor(), false) {
                Sfx.play("sfx_click")
                AppState.menuScreen = "slots"
            }
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf("") }
    var mayor by remember { mutableStateOf("") }
    // 合规：玩家输入实时校验（TapTap 1.1 要求有屏蔽词）
    val nameError = com.dshx.game.she.TextFilter.validate(name)
    val mayorError = com.dshx.game.she.TextFilter.validate(mayor)
    var seedText by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("normal") }
    var slot by remember { mutableStateOf((0..2).firstOrNull { !SaveManager.hasSlot(it) } ?: 0) }
    var sandbox by remember { mutableStateOf(false) }
    val saveTick = AppState.saveTick

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
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it, fontSize = 10.sp, color = C.accentRed.toColor(), fontFamily = LocalGameFont.current) } },
                placeholder = {
                    Text("星野新城", fontFamily = LocalGameFont.current, fontSize = 14.sp)
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

            Text(
                "开发商姓名", fontSize = 12.sp, color = C.textMid.toColor(),
                fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = mayor,
                onValueChange = { if (it.length <= 8) mayor = it },
                isError = mayorError != null,
                supportingText = mayorError?.let { { Text(it, fontSize = 10.sp, color = C.accentRed.toColor(), fontFamily = LocalGameFont.current) } },
                placeholder = {
                    Text("未署名", fontFamily = LocalGameFont.current, fontSize = 14.sp)
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
                "地图种子（同一种子必定生成同一张地图，可分享给他人）", fontSize = 12.sp, color = C.textMid.toColor(),
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

            // 直接导入别人的城市：扫码或从相册选二维码图
            var scanOpen by remember { mutableStateOf(false) }
            var importMsg by remember { mutableStateOf<String?>(null) }
            val pickQr = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                val ctx2 = ctx
                val txt = com.dshx.game.she.QrCode.decodeFromUri(ctx2, uri)
                if (txt == null) {
                    importMsg = "这张图里没识别到城市码"
                } else if (com.dshx.game.she.ShareCode.feed(txt)) {
                    val free = (0 until com.dshx.game.she.SaveManager.SLOT_COUNT)
                        .firstOrNull { !com.dshx.game.she.SaveManager.hasSlot(it) } ?: 0
                    if (com.dshx.game.she.ShareCode.finishInto(free) &&
                        com.dshx.game.she.SaveManager.load(free)
                    ) {
                        AppState.activeSlot = free
                        com.dshx.game.she.Prefs.lastSlot = free
                        AppState.overlay = ""
                        AppState.mode = "view"
                        mapView.resetCamera()
                        mapView.clearSelection()
                        AppState.screen = "map"
                    } else {
                        importMsg = "导入失败"
                    }
                } else {
                    val (got, all) = com.dshx.game.she.ShareCode.progress()
                    importMsg = "已收集 $got/$all 张，请继续选下一张二维码图"
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(C.accentGreen.toColor(), RoundedCornerShape(20.dp))
                        .clickable {
                            Sfx.play("sfx_click")
                            com.dshx.game.she.ShareCode.resetCollect()
                            scanOpen = true
                        },
                    contentAlignment = Alignment.Center
                ) { Text("扫码导入城市", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(C.accentBlue.toColor(), RoundedCornerShape(20.dp))
                        .clickable {
                            Sfx.play("sfx_click")
                            com.dshx.game.she.ShareCode.resetCollect()
                            pickQr.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) { Text("相册导入", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = LocalGameFont.current) }
            }
            importMsg?.let {
                Text(it, fontSize = 10.sp, color = C.accentRed.toColor(), fontFamily = LocalGameFont.current)
            }
            if (scanOpen) {
                var scanHint by remember { mutableStateOf("把镜头对准二维码") }
                com.dshx.game.she.QrScanScreen(
                    onResult = { txt ->
                        if (com.dshx.game.she.ShareCode.feed(txt)) {
                            scanOpen = false
                            val free = (0 until com.dshx.game.she.SaveManager.SLOT_COUNT)
                                .firstOrNull { !com.dshx.game.she.SaveManager.hasSlot(it) } ?: 0
                            if (com.dshx.game.she.ShareCode.finishInto(free) &&
                                com.dshx.game.she.SaveManager.load(free)
                            ) {
                                AppState.activeSlot = free
                                com.dshx.game.she.Prefs.lastSlot = free
                                AppState.overlay = ""
                                AppState.mode = "view"
                                mapView.resetCamera()
                                mapView.clearSelection()
                                AppState.screen = "map"
                            } else {
                                scanHint = "扫码内容不是有效的城市码"
                            }
                        } else {
                            val (got, all) = com.dshx.game.she.ShareCode.progress()
                            scanHint = "已扫 $got/$all 张，请继续扫下一张"
                        }
                    },
                    onClose = { scanOpen = false },
                    hintText = scanHint
                )
            }

            // 精选种子：点一下直接套用，方便论坛分享地形
            var featuredOpen by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "精选地形（官方挑选，可分享给他人）", fontSize = 12.sp, color = C.textMid.toColor(),
                    fontFamily = LocalGameFont.current
                )
                Box(
                    modifier = Modifier
                        .background(C.chipBg.toColor(), RoundedCornerShape(10.dp))
                        .clickable { featuredOpen = !featuredOpen }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (featuredOpen) "收起" else "展开",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = C.accentRed.toColor(), fontFamily = LocalGameFont.current
                    )
                }
            }
            val picked = com.dshx.game.she.SeedLib.parse(seedText)
            val pickedEntry = picked?.let { com.dshx.game.she.SeedLib.find(it) }
            if (pickedEntry != null) {
                Text(
                    "当前：" + pickedEntry.name + " · " + pickedEntry.tag + " · " + pickedEntry.desc,
                    fontSize = 10.sp, color = C.accentGreen.toColor(),
                    fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth()
                )
            }
            if (featuredOpen) {
                for (row in com.dshx.game.she.SeedLib.FEATURED.chunked(2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (e in row) {
                            val on = picked == e.seed
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (on) C.accentSoftBg.toColor() else C.chipBg.toColor(),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (on) C.accentRed.toColor() else C.border2.toColor(),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        Sfx.play("sfx_click", 0.5f)
                                        seedText = e.seed.toString()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Column {
                                    Text(
                                        e.name, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = if (on) C.accentRed.toColor() else C.textDark.toColor(),
                                        fontFamily = LocalGameFont.current
                                    )
                                    Text(
                                        e.tag + " · " + e.seed,
                                        fontSize = 9.sp, color = C.textMid.toColor(),
                                        fontFamily = LocalGameFont.current
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                "存档槽位（新游戏会立刻写入该槽）", fontSize = 12.sp, color = C.textMid.toColor(),
                fontFamily = LocalGameFont.current, modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in 0 until com.dshx.game.she.SaveManager.SLOT_COUNT) {
                    val meta = SaveManager.meta(i)
                    val active = slot == i
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
                            .clickable { slot = i }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (meta.exists) "槽${i + 1}\n覆盖" else "槽${i + 1}\n空",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = if (active) C.accentRed.toColor() else C.textDark.toColor(),
                            fontFamily = LocalGameFont.current, textAlign = TextAlign.Center
                        )
                    }
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                d.name, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (active) C.accentRed.toColor() else C.textDark.toColor(),
                                fontFamily = LocalGameFont.current
                            )
                            Text(
                                when (d.key) {
                                    "easy" -> "收入×1.2 维护×0.8"
                                    "hard" -> "收入×0.8 维护×1.3"
                                    else -> "收入×1.0 维护×1.0"
                                },
                                fontSize = 9.sp,
                                color = C.textMid.toColor(),
                                fontFamily = LocalGameFont.current
                            )
                        }
                    }
                }
            }

            // 沙盒 GM（测试用）：暂时隐藏，需要时把下面这段注释放开
            //             // 沙盒 GM（测试用）
            //             Row(
            //                 modifier = Modifier
            //                     .fillMaxWidth()
            //                     .background(
            //                         if (sandbox) C.accentSoftBg.toColor() else C.chipBg.toColor(),
            //                         RoundedCornerShape(12.dp)
            //                     )
            //                     .border(
            //                         1.dp,
            //                         if (sandbox) C.accentRed.toColor() else C.border2.toColor(),
            //                         RoundedCornerShape(12.dp)
            //                     )
            //                     .clickable { Sfx.play("sfx_click"); sandbox = !sandbox }
            //                     .padding(12.dp),
            //                 verticalAlignment = Alignment.CenterVertically,
            //                 horizontalArrangement = Arrangement.SpaceBetween
            //             ) {
            //                 Column(modifier = Modifier.weight(1f)) {
            //                     Text(
            //                         "沙盒 GM 模式" + if (sandbox) "（已开启）" else "",
            //                         fontSize = 13.sp, fontWeight = FontWeight.Bold,
            //                         color = if (sandbox) C.accentRed.toColor() else C.textDark.toColor(),
            //                         fontFamily = LocalGameFont.current
            //                     )
            //                     Text(
            //                         "测试用：资金拉满、人口锁 5000、幸福度不掉、建造不扣钱",
            //                         fontSize = 9.sp, color = C.textMid.toColor(),
            //                         fontFamily = LocalGameFont.current
            //                     )
            //                 }
            //                 Box(
            //                     modifier = Modifier
            //                         .background(
            //                             if (sandbox) C.accentGreen.toColor() else C.border2.toColor(),
            //                             RoundedCornerShape(10.dp)
            //                         )
            //                         .padding(horizontal = 10.dp, vertical = 5.dp)
            //                 ) {
            //                     Text(
            //                         if (sandbox) "开" else "关",
            //                         fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
            //                         fontFamily = LocalGameFont.current
            //                     )
            //                 }
            //             }

            // 开始
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(C.accentGreen.toColor(), RoundedCornerShape(24.dp))
                    .clickable {
                        Sfx.play("sfx_click")
                        // 合规：含屏蔽词的名称不允许开始
                        val ne = com.dshx.game.she.TextFilter.validate(name)
                        val me = com.dshx.game.she.TextFilter.validate(mayor)
                        if (ne != null || me != null) {
                            mapView.setToast(ne ?: me ?: "名称含不适宜的词")
                            return@clickable
                        }
                        val cityName = if (name.isBlank()) "星野新城" else name.trim()
                        val mayorName = if (mayor.isBlank()) "未署名" else mayor.trim()
                        val seed = if (seedText.isBlank()) Random.nextInt(1, 100000) else seedText.toIntOrNull() ?: Random.nextInt(1, 100000)
                        startGame(mapView, cityName, mayorName, seed, difficulty, slot, sandbox)
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
            for (slot in 0 until com.dshx.game.she.SaveManager.SLOT_COUNT) {
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
                            "槽位 ${slot + 1} · " + meta.cityName + " · " + meta.levelName,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                        )
                        Text(
                            "人口 ${meta.population} · 资金 ¥${floor(meta.funds).toInt()}万 · " + meta.dateLabel,
                            fontSize = 11.sp, color = C.textMid.toColor(), fontFamily = LocalGameFont.current
                        )
                        Text(
                            "游玩 ${meta.playMinutes} 分钟 · 点载入进入该档",
                            fontSize = 10.sp, color = C.textFaint.toColor(), fontFamily = LocalGameFont.current
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
                                            com.dshx.game.she.Prefs.lastSlot = slot
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
