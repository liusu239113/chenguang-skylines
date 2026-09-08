package com.arktools.adsdk

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.privacyDataStore by preferencesDataStore(name = "privacy_prefs")

/**
 * 隐私政策管理器
 * 管理用户隐私同意状态和个性化广告设置
 */
class PrivacyPolicyManager(private val context: Context) {

    companion object {
        val PRIVACY_ACCEPTED = booleanPreferencesKey("privacy_accepted")
        val PERSONALIZED_AD_ENABLED = booleanPreferencesKey("personalized_ad_enabled")
    }

    /**
     * 用户是否已同意隐私政策
     */
    val isPrivacyAccepted: Flow<Boolean> = context.privacyDataStore.data.map { prefs ->
        prefs[PRIVACY_ACCEPTED] ?: false
    }

    /**
     * 是否开启个性化广告
     */
    val isPersonalizedAdEnabled: Flow<Boolean> = context.privacyDataStore.data.map { prefs ->
        prefs[PERSONALIZED_AD_ENABLED] ?: false
    }

    /**
     * 同意隐私政策
     * @param enablePersonalizedAd 是否开启个性化推荐广告
     */
    suspend fun acceptPrivacyPolicy(enablePersonalizedAd: Boolean) {
        context.privacyDataStore.edit { prefs ->
            prefs[PRIVACY_ACCEPTED] = true
            prefs[PERSONALIZED_AD_ENABLED] = enablePersonalizedAd
        }
    }

    /**
     * 重置隐私同意状态（用于测试或用户主动撤销）
     */
    suspend fun resetPrivacyPolicy() {
        context.privacyDataStore.edit { prefs ->
            prefs.remove(PRIVACY_ACCEPTED)
            prefs.remove(PERSONALIZED_AD_ENABLED)
        }
    }
}
