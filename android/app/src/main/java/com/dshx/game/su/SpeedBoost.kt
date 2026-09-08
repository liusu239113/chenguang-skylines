package com.dshx.game.su

import android.content.Context
import android.content.SharedPreferences

object SpeedBoost {
    const val DURATION_MS = 20 * 60 * 1000L
    private lateinit var p: SharedPreferences

    fun init(ctx: Context) {
        p = ctx.applicationContext.getSharedPreferences("speed_boost", Context.MODE_PRIVATE)
    }

    fun expireAt(): Long = p.getLong("expire", 0L)

    fun isActive(): Boolean = System.currentTimeMillis() < expireAt()

    fun remainingSec(): Int {
        val r = expireAt() - System.currentTimeMillis()
        return if (r > 0) (r / 1000L).toInt() else 0
    }

    fun activate() {
        p.edit().putLong("expire", System.currentTimeMillis() + DURATION_MS).apply()
    }

    fun allow(idx: Int): Boolean {
        if (idx <= 1) return true
        return isActive()
    }
}
