package com.dshx.game.she

import android.app.Activity
import android.util.Log
import com.taptap.sdk.compliance.TapTapCompliance
import com.taptap.sdk.compliance.TapTapComplianceCallback
import com.taptap.sdk.compliance.constants.ComplianceMessage

object ComplianceManager {
    private const val TAG = "Compliance"

    interface Listener {
        fun onLoginSuccess()
        fun onBlocked(reason: String)
        fun onSwitchAccount()
    }

    private var listener: Listener? = null

    fun register(l: Listener) {
        listener = l
        TapTapCompliance.registerComplianceCallback(
            callback = object : TapTapComplianceCallback {
                override fun onComplianceResult(code: Int, extra: Map<String, Any>?) {
                    Log.d(TAG, "code=$code extra=$extra")
                    when (code) {
                        ComplianceMessage.LOGIN_SUCCESS -> l.onLoginSuccess()
                        ComplianceMessage.EXITED -> l.onBlocked("已退出认证")
                        ComplianceMessage.SWITCH_ACCOUNT -> l.onSwitchAccount()
                        ComplianceMessage.PERIOD_RESTRICT -> l.onBlocked("当前时段不可游玩")
                        ComplianceMessage.DURATION_LIMIT -> l.onBlocked("今日可玩时长已用尽")
                        ComplianceMessage.INVALID_CLIENT_OR_NETWORK_ERROR -> l.onBlocked("网络或配置异常，请检查网络")
                        ComplianceMessage.REAL_NAME_STOP -> l.onBlocked("请完成实名认证后进入")
                        else -> {
                            if (code == 1100) l.onBlocked("年龄限制，暂不可进入")
                            else Log.w(TAG, "unknown $code")
                        }
                    }
                }
            }
        )
    }

    fun startup(activity: Activity, userId: String) {
        TapTapCompliance.startup(activity, userId)
    }

    fun exit() {
        try { TapTapCompliance.exit() } catch (_: Throwable) {}
    }
}
