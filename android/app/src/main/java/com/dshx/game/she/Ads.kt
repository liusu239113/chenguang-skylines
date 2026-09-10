package com.dshx.game.she

import android.app.Activity
import com.arktools.adsdk.AdHelper

object Ads {
    fun reward(activity: Activity, onOk: () -> Unit, onFail: (() -> Unit)? = null) {
        AppState.adLoading = true
        AdHelper.showRewardAd(
            activity,
            onRewarded = { onOk() },
            onFailed = {
                AppState.adLoading = false
                MapRef.view?.setToast("广告暂不可用")
                onFail?.invoke()
            },
            onComplete = { AppState.adLoading = false },
            onCooldown = { ms ->
                AppState.adLoading = false
                val msg = if (ms <= 0L) "今日广告次数已用尽" else "请稍后再看广告"
                MapRef.view?.setToast(msg)
                onFail?.invoke()
            }
        )
    }
}
