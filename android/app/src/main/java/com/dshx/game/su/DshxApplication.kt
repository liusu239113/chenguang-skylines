package com.dshx.game.su

import android.app.Application
import com.arktools.adsdk.AdSdkConfig

class DshxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdSdkConfig.configure(
            appId = 2097238423626203138L,
            rewardVideoId = "2097240478721998850",
            privacyPolicyUrl = "http://yanyususu.online:5555/doushi.html",
            isDebug = false
        )
        Prefs.init(this)
        Bgm.init(this)
        SpeedBoost.init(this)
    }
}
