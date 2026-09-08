package com.chenguang.skylines

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.chenguang.skylines.ui.screens.MainMenuContent
import com.chenguang.skylines.ui.screens.MapScreen
import com.chenguang.skylines.ui.screens.MapScreenContent
import com.chenguang.skylines.ui.screens.NewspaperContent
import com.chenguang.skylines.ui.theme.ChenguangTheme
import com.chenguang.skylines.world.Growth
import com.chenguang.skylines.world.MapRenderView

// ============================================================================
// 《都市天际线：晨光》入口，与 scripts/main.lua 对应
//   渲染管线：原生 Canvas 地图（底层） → Compose UI 叠层（上层）
// ============================================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Sfx.init(this)
        if (GameData.current == null) {
            GameData.init(20260408)
        }
        setContent {
            ChenguangTheme {
                AppRoot()
            }
        }
    }

    override fun onDestroy() {
        Sfx.release()
        super.onDestroy()
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val mapView = remember {
        MapRenderView(context).apply { initView() }
    }

    DisposableEffect(Unit) {
        MapRef.view = mapView
        onDispose { MapRef.view = null }
    }

    // 初始相机：居中到出生城区
    LaunchedEffect(Unit) {
        mapView.resetCamera()
    }

    // 进入地图界面
    LaunchedEffect(AppState.screen) {
        if (AppState.screen == "map") {
            MapScreen.onShow(
                mapView,
                configuration.screenWidthDp.toFloat(),
                configuration.screenHeightDp.toFloat()
            )
        }
    }

    // 主循环：实时模拟 → 城市成长 → 地图输入/动画 → UI 节流刷新
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                var dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                if (dt > 0.1f) dt = 0.1f          // 防止切后台回来大跳
                if (AppState.screen == "map") {
                    GameData.tick(dt)
                    Growth.tick((dt * GameData.speed()).toDouble()) { name, gain ->
                        Sfx.play(name, gain)
                    }
                    mapView.update(dt)
                    MapScreen.tick(dt, mapView)
                    mapView.invalidate()
                }
            }
        }
    }

    // 返回键：有工具先收工具，否则回菜单
    BackHandler(enabled = AppState.screen != "menu") {
        if (AppState.screen == "map" && mapView.tool != null) {
            MapScreen.cancelTool()
        } else {
            AppState.screen = "menu"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Config.COLORS.uiBackdrop.toColor())
    ) {
        // 地图层（始终在最底）
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        when (AppState.screen) {
            "menu" -> MainMenuContent(mapView)
            "map" -> MapScreenContent(mapView)
            "newspaper" -> NewspaperContent()
        }
    }
}
