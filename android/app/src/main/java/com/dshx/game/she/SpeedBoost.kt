package com.dshx.game.she

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

    /** 指定分钟数解锁（福利广告用）。若已在生效中则从当前到期时间往后叠加。 */
    fun grant(minutes: Int) {
        val now = System.currentTimeMillis()
        val base = maxOf(now, expireAt())
        p.edit().putLong("expire", base + minutes * 60_000L).apply()
    }

    fun allow(idx: Int): Boolean {
        if (idx <= 1) return true
        return isActive()
    }
}
