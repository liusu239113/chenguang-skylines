package com.chenguang.skylines.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenguang.skylines.AppState
import com.chenguang.skylines.Config
import com.chenguang.skylines.GameData
import com.chenguang.skylines.Sfx
import com.chenguang.skylines.ui.theme.LocalGameFont
import com.chenguang.skylines.ui.toColor
import com.chenguang.skylines.world.MapRenderView

// ============================================================================
// MainMenu — 标题画面，与 scripts/Screens/MainMenu.lua 1:1 对应
// ============================================================================

@Composable
fun MainMenuContent(mapView: MapRenderView) {
    val C = Config.COLORS
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
                .background(C.panelWhite.toColor(), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                .padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                Config.World.country + " · " + Config.World.city + " · 城建日报",
                fontSize = 10.sp, color = C.textMid.toColor(),
                fontFamily = LocalGameFont.current, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(C.textDark.toColor())
                    .padding(top = 6.dp, bottom = 2.dp)
            )
            Text(
                Config.TITLE, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Text(
                Config.SUBTITLE, fontSize = 10.sp, color = C.textFaint.toColor(),
                fontFamily = LocalGameFont.current, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            // 双线刊头
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(C.textDark.toColor()))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .padding(top = 2.dp)
                        .background(Color.Transparent)
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.textDark.toColor()))
            }

            Text(
                "你被任命为" + Config.World.city + "的首席" + Config.World.playerRole + "。",
                fontSize = 12.sp, color = C.textDark.toColor(),
                fontFamily = LocalGameFont.current, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "规划用地、铺设路网、招商引资、控制污染，",
                fontSize = 12.sp, color = C.textDark.toColor(),
                fontFamily = LocalGameFont.current, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "一步步把小城建成你的都市天际线。",
                fontSize = 12.sp, color = C.textDark.toColor(),
                fontFamily = LocalGameFont.current, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // 开始规划
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(top = 18.dp)
                    .background(C.accentGreen.toColor(), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    .clickable {
                        Sfx.play("sfx_click")
                        AppState.screen = "map"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "开始规划", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    fontFamily = LocalGameFont.current
                )
            }

            // 重建新城
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(C.chipBg.toColor(), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .border(1.dp, C.border2.toColor(), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .clickable {
                        Sfx.play("sfx_click", 0.8f)
                        GameData.reset((System.currentTimeMillis() / 1000 % 100000).toInt())
                        mapView.resetCamera()
                        AppState.mode = "view"
                        AppState.selService = null
                        AppState.screen = "map"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "重建新城", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                )
            }

            Text(
                "全虚构世界观 · 玩法模拟经营 · 无现实机构指涉",
                fontSize = 9.sp, color = C.textFaint.toColor(), fontFamily = LocalGameFont.current,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Suppress("unused")
private val unusedWidth = Modifier.width(0.dp)
