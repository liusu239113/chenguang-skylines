package com.arktools.adsdk

/**
 * 广告 SDK 配置
 * 使用方必须在 Application 中初始化
 */
object AdSdkConfig {
    // Tosin SDK App ID
    var appId: Long = 0L

    // 激励视频广告位 ID
    var rewardVideoId: String = ""

    // 开屏广告位 ID
    var splashId: String = ""

    // 插屏广告位 ID
    var interstitialId: String = ""

    // Banner 广告位 ID
    var bannerId: String = ""

    // Feed 信息流广告位 ID
    var feedId: String = ""

    // 隐私政策链接
    var privacyPolicyUrl: String = ""

    // 是否调试模式
    var isDebug: Boolean = true

    /**
     * 快速配置方法
     */
    fun configure(
        appId: Long,
        rewardVideoId: String = "",
        splashId: String = "",
        interstitialId: String = "",
        bannerId: String = "",
        feedId: String = "",
        privacyPolicyUrl: String = "",
        isDebug: Boolean = false
    ) {
        this.appId = appId
        this.rewardVideoId = rewardVideoId
        this.splashId = splashId
        this.interstitialId = interstitialId
        this.bannerId = bannerId
        this.feedId = feedId
        this.privacyPolicyUrl = privacyPolicyUrl
        this.isDebug = isDebug
    }
}
