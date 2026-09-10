package com.dshx.game.she

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
                clientId = "wwdsb0dgvedziffljp",
                clientToken = "qVoxlzXGicMKBQn9L4J8oA6d1ZVSKpf7Ml8sA6jN",
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
