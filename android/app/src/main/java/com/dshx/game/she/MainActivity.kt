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
        SpeedBoost.init(this)
        AppState.privacyOk = Prefs.privacyAccepted
        if (GameData.current == null) {
            GameData.init(20260408)
        }
        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Bgm.pause()
    }

    override fun onResume() {
        super.onResume()
        Bgm.resume()
    }

    override fun onDestroy() {
        Bgm.stop()
        Sfx.release()
        super.onDestroy()
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
                    if (!AppState.paused) {
                        GameData.tick(dt)
                        Growth.tick((dt * GameData.speed()).toDouble()) { name, gain ->
                            Sfx.play(name, gain)
                        }
                        mapView.update(dt)
                        MapScreen.tick(dt, mapView)
                    }
                    mapView.invalidate()
                }
            }
        }
    }

    BackHandler(enabled = AppState.screen != "menu" || AppState.menuScreen != "main") {
        if (AppState.screen == "map" && mapView.tool != null) {
            MapScreen.cancelTool()
        } else if (AppState.screen == "map") {
            AppState.paused = true
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
                    AppState.privacyOk = true
                    TapSdkInitializer.ensureInitialized(context)
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
    }
}
