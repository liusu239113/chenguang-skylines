package com.dshx.game.su.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dshx.game.su.AppState
import com.dshx.game.su.Config
import com.dshx.game.su.GameData
import com.dshx.game.su.NewsItem
import com.dshx.game.su.Sfx
import com.dshx.game.su.ui.theme.LocalGameFont
import com.dshx.game.su.ui.toColor
import com.dshx.game.su.world.World
import kotlin.math.floor

// ============================================================================
// NewspaperScreen — 城市简报，与 scripts/Screens/NewspaperScreen.lua 1:1 对应
// ============================================================================

@Composable
fun NewspaperContent() {
    val C = Config.COLORS
    val s = GameData.current
    val level = World.cityLevel()
    val live = AppState.liveTick

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.uiBackdrop.toColor())
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .background(C.panelWhite.toColor(), RoundedCornerShape(18.dp))
                    .clickable {
                        Sfx.play("sfx_click", 0.6f)
                        AppState.screen = "map"
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "← 返回地图", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                )
            }
            Text(
                (s?.cityName ?: "晨光市") + "简报", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = C.textDark.toColor(), fontFamily = LocalGameFont.current
            )
            Box(modifier = Modifier.width(84.dp))   // 占位平衡
        }

        // 概览卡
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
                .background(C.panelWhite.toColor(), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (s != null) {
                Text(
                    level.name + " · " + GameData.monthLabel() + " 概况",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "财政 ¥" + floor(s.funds).toInt() + "万", fontSize = 12.sp,
                        color = C.accentGold.toColor(), fontFamily = LocalGameFont.current
                    )
                    Text(
                        "人口 " + s.population.toInt(), fontSize = 12.sp,
                        color = C.textDark.toColor(), fontFamily = LocalGameFont.current
                    )
                    Text(
                        "满意度 " + floor(s.happiness).toInt(), fontSize = 12.sp,
                        color = if (s.happiness >= 60) C.accentGreen.toColor() else C.accentRed.toColor(),
                        fontFamily = LocalGameFont.current
                    )
                }
            }
        }

        // 新闻滚动
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val news = GameData.recentNews(20)
            if (news.isEmpty()) {
                NewsCard(
                    NewsItem(GameData.monthLabel(), "暂无报道", "城市还在建设中。", "头条")
                )
            } else {
                for (i in news.indices.reversed()) {
                    NewsCard(news[i])
                }
            }
        }
    }
}

@Composable
private fun NewsCard(n: NewsItem) {
    val C = Config.COLORS
    val urgent = n.tag == "头条"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.chipBg.toColor(), RoundedCornerShape(12.dp))
            .border(1.dp, if (urgent) C.accentRed.toColor() else C.border2.toColor(), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                n.tag, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = if (urgent) C.accentRed.toColor() else C.accentGreen.toColor(),
                fontFamily = LocalGameFont.current
            )
            Text(
                n.month, fontSize = 10.sp, color = C.textFaint.toColor(),
                fontFamily = LocalGameFont.current
            )
        }
        Text(
            n.headline, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = C.textDark.toColor(), fontFamily = LocalGameFont.current,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            n.body, fontSize = 11.sp, color = C.textMid.toColor(),
            fontFamily = LocalGameFont.current, lineHeight = 17.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Suppress("unused")
private val unusedColor: Color = Color.Transparent
