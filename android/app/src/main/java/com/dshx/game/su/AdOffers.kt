package com.dshx.game.su

object AdOffers {
    var dailyClaimed: Boolean = false
    var dailyDay: Int = -1

    fun reset() {
        dailyClaimed = false
        dailyDay = -1
        AppState.adOfferOpen = false
        AppState.adOfferKind = ""
    }

    fun tickDay(s: CityState) {
        val key = s.year * 400 + s.month * 32 + s.day
        if (dailyDay != key) {
            dailyDay = key
            dailyClaimed = false
            if (!AppState.adOfferOpen && s.day % 2 == 1) {
                AppState.adOfferKind = "daily"
                AppState.adOfferOpen = true
            }
        }
        if (!AppState.adOfferOpen && s.bankruptDays >= 2 && s.funds < 80) {
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
                dailyClaimed = true
                MapRef.view?.setToast("每日市政礼包 +280 万")
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
                MapRef.view?.setToast("市政拨款 +220 万")
            }
        }
        AppState.adOfferOpen = false
        AppState.adOfferKind = ""
        AppState.bumpLive()
    }
}
