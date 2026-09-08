package com.dshx.game.su

import android.content.Context
import android.util.Log
import com.taptap.sdk.compliance.option.TapTapComplianceOptions
import com.taptap.sdk.core.TapTapRegion
import com.taptap.sdk.core.TapTapSdk
import com.taptap.sdk.core.TapTapSdkOptions

object TapSdkInitializer {
    private var initialized = false

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) return
        try {
            val tapSdkOptions = TapTapSdkOptions(
                clientId = "S4ASQXORBAU4U0qtzr",
                clientToken = "eQKI7h1PxpfufYPkK4nNbCE2QNaIkB3pt9fk3jwt",
                region = TapTapRegion.CN,
                enableLog = false
            )
            TapTapSdk.init(
                context.applicationContext,
                tapSdkOptions,
                options = arrayOf(
                    TapTapComplianceOptions(
                        showSwitchAccount = true,
                        useAgeRange = false
                    )
                )
            )
            initialized = true
            Log.i("TapSdk", "initialized")
        } catch (e: Exception) {
            Log.e("TapSdk", "init failed", e)
        }
    }

    fun isInitialized(): Boolean = initialized
}
