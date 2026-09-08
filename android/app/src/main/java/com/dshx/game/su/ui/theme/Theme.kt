package com.dshx.game.su.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.dshx.game.su.R

val LocalGameFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val font = FontFamily(Font(R.font.zcool_kuaile_regular))
    CompositionLocalProvider(LocalGameFont provides font) {
        content()
    }
}
