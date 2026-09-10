package com.dshx.game.she

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlin.math.max
import kotlin.math.min

// ============================================================================
// Sfx — 轻量音效播放（SoundPool），与 scripts/Sfx.lua 对等
//   音频设备缺失时自动禁用，不影响游戏逻辑
// ============================================================================

object Sfx {

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<String, Int>()
    private var enabled = false

    private val NAMES = listOf(
        "sfx_build", "sfx_click", "sfx_demolish", "sfx_levelup", "sfx_month",
        "sfx_policy", "sfx_save", "sfx_engine", "sfx_cash", "sfx_horn"
    )

    fun init(context: Context) {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val sp = SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(attrs)
                .build()
            val res = context.resources
            val pkg = context.packageName
            for (n in NAMES) {
                val rid = res.getIdentifier(n, "raw", pkg)
                if (rid != 0) {
                    val sid = sp.load(context, rid, 1)
                    ids[n] = sid
                }
            }
            pool = sp
            enabled = true
        } catch (t: Throwable) {
            enabled = false
        }
    }

    fun play(name: String, gain: Float = 0.9f) {
        val sp = pool ?: return
        if (!enabled) return
        try {
            val sid = ids[name] ?: return
            val vol = max(0f, min(1f, gain * Prefs.sfxVolume))
            sp.play(sid, vol, vol, 1, 0, 1f)
        } catch (t: Throwable) {
            // 忽略
        }
    }

    fun release() {
        try {
            pool?.release()
        } catch (t: Throwable) {
            // 忽略
        }
        pool = null
        enabled = false
        ids.clear()
    }
}
