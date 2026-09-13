package com.dshx.game.she.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.dshx.game.she.AppState
import com.dshx.game.she.R

val LocalGameFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // 设置里可切换：手写体（默认）/ 系统黑体，切换后立刻生效
    val useSystem = AppState.useSystemFont
    val font = remember(useSystem) {
        if (useSystem) FontFamily.Default else FontFamily(Font(R.font.zcool_kuaile_regular))
    }
    CompositionLocalProvider(LocalGameFont provides font) {
        content()
    }
}
