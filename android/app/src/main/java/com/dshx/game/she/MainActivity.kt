package com.dshx.game.she

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.dshx.game.she.ui.screens.AdLoadingOverlay
import com.dshx.game.she.ui.screens.MainMenuContent
import com.dshx.game.she.ui.screens.MapScreen
import com.dshx.game.she.ui.screens.MapScreenContent
import com.dshx.game.she.ui.screens.NewspaperContent
import com.dshx.game.she.ui.screens.PrivacyGate
import com.dshx.game.she.ui.screens.TapLoginGate
import com.dshx.game.she.ui.theme.AppTheme
import com.dshx.game.she.ui.toColor
import com.dshx.game.she.world.Growth
import com.dshx.game.she.world.MapRenderView

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        Sfx.init(this)
        SaveManager.init(this)
        Prefs.init(this)
        Bgm.init(this)
        Ambience.init(this)
        SpeedBoost.init(this)
        Buffs.init(this)
        AppState.privacyOk = Prefs.privacyAccepted
        AppState.useSystemFont = Prefs.useSystemFont
        // 合规：启动时不初始化 Tap 登录 SDK；等用户同意隐私政策、进入登录页时初始化一次
        if (GameData.current == null) {
            GameData.init(20260408)
        }
        // 好友发来的新区存档：在微信/QQ 里点文件选本作打开
        handleCityMapIntent(intent)
        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (AppState.screen == "map" && GameData.current != null) {
            SaveManager.save(AppState.activeSlot)
            Prefs.lastSlot = AppState.activeSlot
        }
        Bgm.pause()
        Ambience.stop()
    }

    override fun onResume() {
        super.onResume()
        Bgm.resume()
    }

    override fun onDestroy() {
        Bgm.stop()
        Ambience.release()
        Sfx.release()
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCityMapIntent(intent)
    }

    /**
     * 好友在微信/QQ 里点 .citymap 文件、选「用本作打开」时走这里。
     * 把存档读进一个空槽位并直接进入新区。
     */
    private fun handleCityMapIntent(intent: android.content.Intent?) {
        if (!CityFile.isCityMapIntent(intent)) return
        val uri = intent?.data ?: return
        // 先解析，再让玩家选槽位（避免覆盖已有存档）
        val j = CityFile.parseFromUri(this, uri)
        if (j == null) {
            MapRef.view?.setToast("新区存档已损坏或格式不符")
        } else {
            PendingImport.save = j
            PendingImport.source = "好友发来的存档"
            AppState.importSlotOpen = true
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context as? MainActivity

    val mapView = remember {
        MapRenderView(context).apply { initView() }
    }

    DisposableEffect(Unit) {
        MapRef.view = mapView
        onDispose { MapRef.view = null }
    }

    LaunchedEffect(Unit) {
        mapView.resetCamera()
    }

    LaunchedEffect(AppState.screen) {
        if (AppState.screen == "map") {
            MapScreen.onShow(
                mapView,
                configuration.screenWidthDp.toFloat(),
                configuration.screenHeightDp.toFloat()
            )
            Bgm.playCity()
        } else if (AppState.loggedIn && Prefs.privacyAccepted) {
            Bgm.playMenu()
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                var dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                if (dt > 0.1f) dt = 0.1f
                if (AppState.screen == "map") {
                    Ambience.tick(dt, GameData.weather, true)
                    GameData.tick(dt)
                    if (!AppState.paused && GameData.speed() > 0) {
                        Growth.tick((dt * GameData.speed()).toDouble()) { name, gain ->
                            Sfx.play(name, gain)
                        }
                    }
                    mapView.update(dt)
                    MapScreen.tick(dt, mapView)
                    mapView.invalidate()
                } else {
                    Ambience.tick(dt, 0, false)
                }
            }
        }
    }

    BackHandler(enabled = AppState.screen != "menu" || AppState.menuScreen != "main") {
        if (AppState.screen == "map" && mapView.tool != null) {
            MapScreen.cancelTool()
        } else if (AppState.screen == "map") {
            AppState.menuOpen = true
        } else if (AppState.menuScreen != "main") {
            AppState.menuScreen = "main"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Config.COLORS.uiBackdrop.toColor())
    ) {
        when {
            !AppState.privacyOk -> PrivacyGate(
                onAccepted = {
                    // 合规：同意时也不初始化 Tap 登录 SDK，等用户点「TapTap 登录」时再初始化
                    AppState.privacyOk = true
                },
                onExit = { activity?.finish() }
            )
            !AppState.loggedIn -> TapLoginGate(onReady = {
                AppState.loggedIn = true
                Bgm.playMenu()
            })
            else -> {
                if (AppState.screen != "menu") {
                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                when (AppState.screen) {
                    "menu" -> MainMenuContent(mapView)
                    "map" -> MapScreenContent(mapView)
                    "newspaper" -> NewspaperContent()
                }
            }
        }
        AdLoadingOverlay(AppState.adLoading)
        // 导入新区时选槽位（跨页面通用）
        if (AppState.importSlotOpen) {
            com.dshx.game.she.ui.screens.ImportSlotPicker()
        }
    }
}
