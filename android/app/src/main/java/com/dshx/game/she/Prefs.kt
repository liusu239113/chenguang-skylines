package com.dshx.game.she

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var p: SharedPreferences

    fun init(ctx: Context) {
        p = ctx.applicationContext.getSharedPreferences("dshx_prefs", Context.MODE_PRIVATE)
    }

    var privacyAccepted: Boolean
        get() = p.getBoolean("privacy_accepted", false)
        set(v) { p.edit().putBoolean("privacy_accepted", v).apply() }

    var bgmVolume: Float
        get() {
            if (!p.contains("bgm_vol_v2")) {
                p.edit().putBoolean("bgm_vol_v2", true).putFloat("bgm_vol", 0.22f).apply()
                return 0.22f
            }
            return p.getFloat("bgm_vol", 0.22f)
        }
        set(v) {
            p.edit().putBoolean("bgm_vol_v2", true).putFloat("bgm_vol", v.coerceIn(0f, 1f)).apply()
            Bgm.applyVolume()
        }

    var sfxVolume: Float
        get() = p.getFloat("sfx_vol", 0.85f)
        set(v) { p.edit().putFloat("sfx_vol", v.coerceIn(0f, 1f)).apply() }
}
