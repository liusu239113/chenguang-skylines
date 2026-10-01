package com.dshx.game.she

import com.dshx.game.she.world.World

object AdOffers {
    var weeklyClaimed: Boolean = false
    var weeklyKey: Int = -1
    var lastBailoutDay: Int = -1

    fun reset() {
        weeklyClaimed = false
        weeklyKey = -1
        lastBailoutDay = -1
        AppState.adOfferOpen = false
        AppState.adOfferKind = ""
    }

    fun tickDay(s: CityState) {
        val week = s.year * 60 + s.month * 5 + ((s.day - 1) / 7)
        if (weeklyKey != week) {
            weeklyKey = week
            weeklyClaimed = false
            if (!AppState.adOfferOpen && s.day == 1) {
                AppState.adOfferKind = "daily"
                AppState.adOfferOpen = true
            }
        }
        val dayKey = s.year * 400 + s.month * 32 + s.day
        if (!AppState.adOfferOpen && s.bankruptDays >= 3 && s.funds < 50 && lastBailoutDay != dayKey) {
            lastBailoutDay = dayKey
            AppState.adOfferKind = "bailout"
            AppState.adOfferOpen = true
        }
    }

    fun offerShortfall(need: Int, action: String) {
        val s = GameData.current ?: return
        s.lastShortfall = need
        s.lastShortAction = action
        AppState.adOfferKind = "shortfall"
        AppState.adOfferOpen = true
    }

    fun grant(kind: String) {
        val s = GameData.current ?: return
        when (kind) {
            "daily" -> {
                s.funds += 120
                GameData.book("income", "other", "每周礼包", 120.0)
                weeklyClaimed = true
                MapRef.view?.setToast("每周营造礼包 +120 万")
            }
            "shortfall" -> {
                val add = maxOf(120, s.lastShortfall)
                s.funds += add
                GameData.book("income", "other", "应急拨款", add.toDouble())
                MapRef.view?.setToast("应急拨款 +${add}万，可继续${s.lastShortAction}")
            }
            "bailout" -> {
                s.funds += 260
                GameData.book("income", "other", "账面纾困", 260.0)
                s.bankruptDays = 0
                MapRef.view?.setToast("账面纾困 +260 万")
            }
            "doubletax" -> {
                s.doubleTaxDays = 12
                MapRef.view?.setToast("经营收入加倍 12 天")
            }
            "happy" -> {
                s.happiness = (s.happiness + 8).coerceAtMost(92.0)
                MapRef.view?.setToast("民心安抚 满意+8")
            }
            "grant" -> {
                s.funds += 90
                GameData.book("income", "other", "营造拨款", 90.0)
                MapRef.view?.setToast("营造拨款 +90 万")
            }
            // ---- 便利型：即时可用的小工具，转化更好 ----
            "speed" -> {
                SpeedBoost.grant(30)
                MapRef.view?.setToast("已解锁 3x 加速 30 分钟")
            }
            "cleartrash" -> {
                // 立刻清空全城垃圾积压，省得等清运
                var n = 0
                for (e in World.allBuildings()) {
                    val b = e.b
                    if (b.isService || b.garbage <= 0) continue
                    n += b.garbage
                    b.garbage = 0
                }
                s.garbageBacklog = 0
                MapRef.view?.setToast("全城垃圾已清运（清掉 $n 单位）")
            }
            "repair" -> {
                // 修复全部废弃建筑，立刻恢复入住
                var n = 0
                for (e in World.allBuildings()) {
                    val b = e.b
                    if (b.isService || !b.abandoned) continue
                    b.abandoned = false
                    n++
                }
                MapRef.view?.setToast(if (n > 0) "已修复 $n 栋废弃建筑" else "当前没有废弃建筑")
            }
            "unlock" -> {
                // 立刻向外扩一圈解锁圈
                val w = World.current
                if (w != null) {
                    w.unlockR += 1
                    MapRef.view?.setToast("解锁圈向外扩了一圈")
                }
            }
            "taxfree" -> {
                s.doubleTaxDays = 12
                MapRef.view?.setToast("经营收入加倍 12 天")
            }
        }
        AppState.adOfferOpen = false
        AppState.adOfferKind = ""
        AppState.bumpLive()
    }
}
