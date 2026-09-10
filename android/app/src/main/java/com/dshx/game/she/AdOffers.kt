package com.dshx.game.she

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
                s.funds += 280
                weeklyClaimed = true
                MapRef.view?.setToast("每周营造礼包 +280 万")
            }
            "shortfall" -> {
                val add = maxOf(220, s.lastShortfall)
                s.funds += add
                MapRef.view?.setToast("应急拨款 +${add}万，可继续${s.lastShortAction}")
            }
            "bailout" -> {
                s.funds += 480
                s.bankruptDays = 0
                MapRef.view?.setToast("财政纾困 +480 万")
            }
            "doubletax" -> {
                s.doubleTaxDays = 12
                MapRef.view?.setToast("税收加倍 12 天")
            }
            "happy" -> {
                s.happiness = (s.happiness + 8).coerceAtMost(92.0)
                MapRef.view?.setToast("民心安抚 满意+8")
            }
            "grant" -> {
                s.funds += 220
                MapRef.view?.setToast("营造拨款 +220 万")
            }
        }
        AppState.adOfferOpen = false
        AppState.adOfferKind = ""
        AppState.bumpLive()
    }
}
