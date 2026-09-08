package com.arktools.adsdk

import android.app.Application
import android.content.Context
import com.tosin.sdk.initsdk.init.CustomController
import com.tosin.sdk.initsdk.init.InitListener
import com.tosin.sdk.initsdk.init.TosinInitConfig
import com.tosin.sdk.initsdk.init.TosinSDK

/**
 * Tosin 广告 SDK 初始化管理器
 * 在 Application.onCreate 中调用 init()
 */
class TosinAdInitializer private constructor() {

    companion object {
        @Volatile
        private var instance: TosinAdInitializer? = null

        fun getInstance(): TosinAdInitializer {
            return instance ?: synchronized(this) {
                instance ?: TosinAdInitializer().also { instance = it }
            }
        }

        @Volatile
        var isSdkInitialized: Boolean = false
            private set
    }

    /**
     * 初始化 Tosin SDK
     * @param application Application 实例
     * @param listener 初始化回调
     */
    fun init(application: Application, listener: InitListener? = null) {
        if (isSdkInitialized) {
            listener?.onInitSuccess()
            return
        }

        if (AdSdkConfig.appId == 0L) {
            throw IllegalStateException("AdSdkConfig.appId 未配置，请先调用 AdSdkConfig.configure()")
        }

        // 隐私合规：通过 CustomController 关闭广告 SDK 对 IMEI 等敏感个人信息的采集
        // （TapTap 审核指出 SDK 多次读取 IMEI 超范围收集）。
        // 保留 OAID/AndroidID 等合规的广告归因标识，不影响广告变现。
        val config = TosinInitConfig.Builder()
            .appId(AdSdkConfig.appId)
            .isDebug(AdSdkConfig.isDebug)
            .customController(object : CustomController() {
                // 禁止读取手机状态/IMEI（本次审核被点名的核心问题）
                // 注：can* 开关返回 false 后，SDK 不会再去读取对应的 IMEI/MAC 等值，
                //     因此无需（也不能）重写 imei/macAddress 等属性 getter
                override fun canUsePhoneState(): Boolean = false
                // 禁止读取 MAC 地址
                override fun canUseMacAddress(): Boolean = false
                // 禁止定位（休闲游戏无定位需求）
                override fun canReadLocation(): Boolean = false
                // 禁止读取已安装应用列表
                override fun canGetInstallPackages(): Boolean = false
                // 禁止录音权限相关采集
                override fun canUsePermissionRecordAudio(): Boolean = false
                // 保留合规的广告标识：OAID（IMEI 的合规替代）/ AndroidID / WiFi 状态
                override fun canUseOaid(): Boolean = true
                override fun canUseAndroidId(): Boolean = true
                override fun canUseWifiState(): Boolean = true
            })
            .build()

        TosinSDK.instance.init(application, config, object : InitListener {
            override fun onInitFail(fail: String?) {
                listener?.onInitFail(fail)
            }

            override fun onInitSuccess() {
                isSdkInitialized = true
                listener?.onInitSuccess()
            }
        })
    }
}
