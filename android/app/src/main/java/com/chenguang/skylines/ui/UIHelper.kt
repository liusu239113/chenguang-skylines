package com.chenguang.skylines.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenguang.skylines.Config
import com.chenguang.skylines.RGBA
import com.chenguang.skylines.ui.theme.LocalGameFont
import kotlin.math.floor

// ============================================================================
// UIHelper — 与 scripts/UIHelper.lua 对应的 Compose 组件工厂
//   圆角白卡 + 轻投影，报纸风
// ============================================================================

fun RGBA.toColor(): Color = Color(r / 255f, g / 255f, b / 255f, a / 255f)

object UIHelper {

    /** 资金格式化：>=10000 万显示 "x.x万" */
    fun fmtMoney(v: Double): String =
        if (v >= 10000 || v <= -10000) String.format("%.1f万", v / 10000) else floor(v).toInt().toString()

    fun fmtPop(v: Int): String =
        if (v >= 10000) String.format("%.1f万", v / 10000.0) else v.toString()

    @Composable
    private fun gameText(
        text: String,
        fontSize: TextUnit,
        color: Color,
        bold: Boolean = false,
        align: TextAlign = TextAlign.Start,
        modifier: Modifier = Modifier,
        lineHeight: TextUnit = TextUnit.Unspecified
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = LocalGameFont.current,
            textAlign = align,
            lineHeight = lineHeight,
            modifier = modifier
        )
    }

    /** 通用圆角白卡 */
    @Composable
    fun Card(
        modifier: Modifier = Modifier,
        radius: Dp = 18.dp,
        bg: Color = Config.COLORS.panelWhite.toColor(),
        padding: Dp = 14.dp,
        paddingTop: Dp = 8.dp,
        paddingBottom: Dp = 8.dp,
        horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
        content: @Composable ColumnScope.() -> Unit
    ) {
        val shape = RoundedCornerShape(radius)
        Column(
            modifier = modifier
                .shadow(elevation = 4.dp, shape = shape, ambientColor = ShadowColor, spotColor = ShadowColor)
                .background(bg, shape)
                .padding(start = padding, end = padding, top = paddingTop, bottom = paddingBottom),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }

    /** 资源小胶囊 */
    @Composable
    fun Chip(label: String, value: String, bold: Boolean = true, color: Color = Config.COLORS.textDark.toColor()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (label.isNotEmpty()) {
                gameText(label, 10.sp, Config.COLORS.textMid.toColor())
            }
            gameText(value, 13.sp, color, bold = bold)
        }
    }

    /** 圆形浮动按钮 */
    @Composable
    fun RoundButton(
        text: String,
        size: Dp = 44.dp,
        fontSize: TextUnit = 22.sp,
        color: Color = Config.COLORS.textDark.toColor(),
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(4.dp, RoundedCornerShape(size / 2), ambientColor = ShadowColor, spotColor = ShadowColor)
                .background(Config.COLORS.panelWhite.toColor(), RoundedCornerShape(size / 2))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            gameText(text, fontSize, color, bold = true, align = TextAlign.Center)
        }
    }

    /** 底部工具栏项（纯文字，active 时高亮底色） */
    @Composable
    fun ToolItem(text: String, active: Boolean, width: Dp = 44.dp, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .width(width)
                .height(52.dp)
                .background(
                    if (active) Config.COLORS.accentSoftBg.toColor() else Color.Transparent,
                    RoundedCornerShape(14.dp)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            gameText(
                text, 15.sp,
                if (active) Config.COLORS.accentRed.toColor() else Config.COLORS.textMid.toColor(),
                bold = true, align = TextAlign.Center
            )
        }
    }

    /** 信息卡行（label 左 / value 右） */
    @Composable
    fun InfoRow(label: String, value: String, color: Color = Config.COLORS.textDark.toColor()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            gameText(label, 12.sp, Config.COLORS.textMid.toColor())
            gameText(value, 12.sp, color)
        }
    }

    /** 建筑 / 工具选择小卡 */
    @Composable
    fun PickChip(
        text: String,
        sub: String,
        selected: Boolean,
        disabled: Boolean = false,
        subColor: Color = Config.COLORS.textMid.toColor(),
        width: Dp = 82.dp,
        onClick: () -> Unit
    ) {
        val alpha = if (disabled) 0.45f else 1f
        Column(
            modifier = Modifier
                .width(width)
                .background(Config.COLORS.panelWhite.toColor().copy(alpha = alpha), RoundedCornerShape(12.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Config.COLORS.accentRed.toColor() else Config.COLORS.border2.toColor(),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { onClick() }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            gameText(text, 12.sp, Config.COLORS.textDark.toColor(), bold = true, align = TextAlign.Center)
            gameText(sub, 10.sp, subColor, align = TextAlign.Center)
        }
    }

    @Composable
    fun Label(
        text: String,
        fontSize: TextUnit = 12.sp,
        color: Color = Config.COLORS.textDark.toColor(),
        bold: Boolean = false,
        align: TextAlign = TextAlign.Start,
        modifier: Modifier = Modifier,
        lineHeight: TextUnit = TextUnit.Unspecified
    ) = gameText(text, fontSize, color, bold, align, modifier, lineHeight)
}

/** 阴影颜色：与 Lua boxShadow { 90,100,90,70 } 一致 */
private val ShadowColor: Color = Color(90 / 255f, 100 / 255f, 90 / 255f, 70 / 255f)
