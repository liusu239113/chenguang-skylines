package com.arktools.adsdk

import android.app.Activity
import com.tosin.sdk.loadAd.rewardvideo.RewardVideoAd
import com.tosin.sdk.loadAd.rewardvideo.config.RewardVideoConfig
import com.tosin.sdk.loadAd.rewardvideo.RewardVideoListener
import com.tosin.sdk.loadAd.model.AdError
import com.tosin.sdk.initsdk.init.InitListener

/**
 * 广告管理器（单例）
 * 负责激励视频的加载和展示
 */
class AdManager private constructor() {

    private var rewardVideoAd: RewardVideoAd? = null
    private var rewardVideoListener: RewardVideoListener? = null

    companion object {
        @Volatile
        private var instance: AdManager? = null

        fun getInstance(): AdManager {
            return instance ?: synchronized(this) {
                instance ?: AdManager().also { instance = it }
            }
        }
    }

    interface RewardCallback {
        fun onRewardVerify()
        fun onVideoComplete()
        fun onAdClose()
        fun onLoadFail(error: String?)
        fun onLoadSuccess()
    }

    private fun ensureSdkInitialized(activity: Activity, onReady: () -> Unit) {
        if (TosinAdInitializer.isSdkInitialized) {
            onReady()
            return
        }

        TosinAdInitializer.getInstance().init(activity.application as android.app.Application, object : InitListener {
            override fun onInitFail(fail: String?) {
                onReady()
            }

            override fun onInitSuccess() {
                onReady()
            }
        })
    }

    /**
     * 加载激励视频广告
     */
    fun loadRewardVideo(activity: Activity, callback: RewardCallback) {
        ensureSdkInitialized(activity) {
            rewardVideoAd?.destory()
            rewardVideoAd = null

            if (AdSdkConfig.rewardVideoId.isEmpty()) {
                callback.onLoadFail("rewardVideoId 未配置")
                return@ensureSdkInitialized
            }

            val config = RewardVideoConfig.Builder()
                .codeId(AdSdkConfig.rewardVideoId)
                .build()

            rewardVideoAd = RewardVideoAd(activity, config)
            val listener = object : RewardVideoListener {
                override fun onRewardVerify() {
                    callback.onRewardVerify()
                }

                override fun onVideoComplete() {
                    callback.onVideoComplete()
                }

                override fun onAdClose() {
                    callback.onAdClose()
                }

                override fun onExposure() {}

                override fun onADShowError(fail: String) {}

                override fun onADShow() {}

                override fun onLoadSuccess() {
                    callback.onLoadSuccess()
                }

                override fun onLoadFail(adError: AdError?) {
                    callback.onLoadFail(adError?.errorMsg)
                }

                override fun onADClick() {}
            }
            rewardVideoListener = listener

            rewardVideoAd?.loadRewardVideo(listener)
        }
    }

    /**
     * 展示已加载的激励视频
     */
    fun showRewardVideo(activity: Activity) {
        rewardVideoAd?.showAd(activity)
    }

    /**
     * 销毁激励视频资源
     */
    fun destroyRewardVideo() {
        rewardVideoAd?.destory()
        rewardVideoAd = null
        rewardVideoListener = null
    }
}
