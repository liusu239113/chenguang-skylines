package com.dshx.game.she

import android.app.Application
import com.arktools.adsdk.AdSdkConfig

class DshxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdSdkConfig.configure(
            appId = 2097866984871575553L,
            rewardVideoId = "2097867950530371585",
            privacyPolicyUrl = "http://yanyususu.online:5555/doushi.html",
            isDebug = false
        )
        Prefs.init(this)
        Bgm.init(this)
        SpeedBoost.init(this)
    }
}
