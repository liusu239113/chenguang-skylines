package com.dshx.game.she

import android.content.Context
import android.content.SharedPreferences

/**
 * 限时增益：看广告获得的临时效果，按真实时间计时。
 * 与 SpeedBoost 一样存在 SharedPreferences，退出游戏也不丢。
 */
object Buffs {

    // 增益类型
    const val NO_HAPPY_DROP = "no_happy_drop"   // 满意度不再下降
    const val FAST_GROWTH = "fast_growth"       // 居民增长速度提升
    const val INCOME_BOOST = "income_boost"     // 项目收益提升
    const val NO_UPKEEP = "no_upkeep"           // 免维护费

    private lateinit var p: SharedPreferences

    fun init(ctx: Context) {
        p = ctx.applicationContext.getSharedPreferences("buffs", Context.MODE_PRIVATE)
    }

    /** 发放增益；已在生效中则从当前到期时间往后叠加 */
    fun grant(kind: String, minutes: Int) {
        val now = System.currentTimeMillis()
        val base = maxOf(now, p.getLong(kind, 0L))
        p.edit().putLong(kind, base + minutes * 60_000L).apply()
    }

    fun isActive(kind: String): Boolean = System.currentTimeMillis() < p.getLong(kind, 0L)

    fun remainingSec(kind: String): Int {
        val r = p.getLong(kind, 0L) - System.currentTimeMillis()
        return if (r > 0) (r / 1000L).toInt() else 0
    }

    /** 剩余时间的可读文本，未生效返回 null */
    fun remainText(kind: String): String? {
        val s = remainingSec(kind)
        if (s <= 0) return null
        return if (s >= 60) (s / 60).toString() + " 分 " + (s % 60) + " 秒"
        else s.toString() + " 秒"
    }

    fun reset() {
        p.edit().clear().apply()
    }
}
