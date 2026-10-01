package com.dshx.game.she.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * 全局 UI 缩放：解决小屏手机 HUD 挤成一团的问题。
 *
 * 思路：以主流机型宽度（412dp）为基准，屏幕更窄时整体缩小 UI 密度，
 * 让同一套固定 dp 布局在小屏上等比收紧，而不是把元素挤爆或溢出。
 * 大屏（≥ 基准宽）不放大，避免界面在大屏上过度拉伸。
 *
 * 用法：在应用根节点用 [ProvideUiScale] 包一层，所有 dp/sp 自动生效。
 */
object UiScale {

    /** 设计基准宽度（dp）：主流手机宽度 */
    const val REF_WIDTH = 412f

    /** 最小缩放：再窄的屏也最多缩到 80%，避免字太小看不清 */
    const val MIN = 0.80f

    /** 当前生效的缩放系数（1.0 = 不缩放） */
    var current: Float = 1.0f
        internal set

    /** 按屏幕宽度算缩放系数：小屏 < 1，大屏 = 1 */
    fun factor(screenWidthDp: Float): Float {
        if (screenWidthDp <= 0f) return 1.0f
        return (screenWidthDp / REF_WIDTH).coerceIn(MIN, 1f)
    }
}

/**
 * 在根节点包一层：按屏幕宽度缩放整个 Compose UI 的密度。
 * 只缩放 density，不叠加 fontScale，保证 dp 与 sp 等比收缩、字距不散。
 */
@Composable
fun ProvideUiScale(screenWidthDp: Float, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val scale = UiScale.factor(screenWidthDp)
    UiScale.current = scale
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * scale, base.fontScale),
        content = content
    )
}
