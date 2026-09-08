package com.chenguang.skylines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chenguang.skylines.world.MapRenderView

// ============================================================================
// AppState — 全局 UI 状态（对应 Lua 各 Screen 的模块局部变量）
// ============================================================================
object AppState {
    /** menu | map | newspaper */
    var screen by mutableStateOf("menu")

    /** view | road | zone | bulldoze | service */
    var mode by mutableStateOf("view")
    var zoneKey by mutableStateOf("residential")
    var roadKind by mutableStateOf("local")
    var selService by mutableStateOf<String?>(null)

    var serviceOpen by mutableStateOf(false)
    var roadOpen by mutableStateOf(false)
    var policyOpen by mutableStateOf(false)
    var helpOpen by mutableStateOf(false)

    /** 数据面板 */
    var dataOpen by mutableStateOf(false)

    /** 覆盖热力图（"" | power/water/garbage/health/education/safety） */
    var overlay by mutableStateOf("")

    /** 实时数值节流刷新（0.5s 一次） */
    var liveTick by mutableStateOf(0)

    /** 工具 / 选中格变化 */
    var mapVersion by mutableStateOf(0)

    var tutShown = false

    fun bumpLive() {
        liveTick++
    }

    fun bumpMap() {
        mapVersion++
    }
}

/** 地图 View 引用（供 UI 层调用 setTool / setToast） */
object MapRef {
    var view: MapRenderView? = null
}
