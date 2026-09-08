package com.arktools.adsdk

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一的激励视频广告调用工具
 * 简化广告调用流程，并确保回调安全（主线程 + 异常捕获）
 */
object AdHelper {

    private const val TAG = "AdHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 广告冷却时间：每看一次广告，需间隔 2 分钟才能再次观看 */
//    private const val AD_COOLDOWN_MS = 2 * 60 * 1000L
    private const val AD_COOLDOWN_MS = 5000L

    /** 每日广告上限次数 */
    private const val DAILY_AD_LIMIT = 50

    /** 上一次成功展示广告的时间戳（用于冷却判定） */
    @Volatile
    private var lastAdShownAt = 0L

    /** 广告加载中（供全屏转圈遮罩订阅） */
    private val _isLoadingAd = MutableStateFlow(false)
    val isLoadingAd: StateFlow<Boolean> = _isLoadingAd.asStateFlow()

    // ========== 每日广告计数（持久化到 SharedPreferences） ==========

    private const val PREFS_NAME = "ad_helper_prefs"
    private const val KEY_AD_DATE = "ad_date"      // 记录广告计数对应的日期 yyyy-MM-dd
    private const val KEY_AD_COUNT = "ad_count"     // 当天已看次数


    /** 当天已看广告次数 */
    @Volatile
    private var todayAdCount: Int = 0

    /** 当天日期 yyyy-MM-dd */
    @Volatile
    private var todayDateStr: String = ""

    /** 格式化器（线程安全：SimpleDateFormat 非线程安全，但只在主线程初始化路径使用） */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * 初始化/恢复每日广告计数
     */
    private fun initDailyCount(prefs: android.content.SharedPreferences) {
        val savedDate = prefs.getString(KEY_AD_DATE, "") ?: ""
        val nowStr = dateFormat.format(Date())
        if (savedDate == nowStr) {
            // 同一天：恢复已看次数
            todayAdCount = prefs.getInt(KEY_AD_COUNT, 0)
            todayDateStr = savedDate
        } else {
            // 跨天：重置
            todayAdCount = 0
            todayDateStr = nowStr
            prefs.edit()
                .putString(KEY_AD_DATE, nowStr)
                .putInt(KEY_AD_COUNT, 0)
                .apply()
        }
    }

    /**
     * 持久化当日广告计数
     */
    private fun persistDailyCount(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_AD_DATE, todayDateStr)
            .putInt(KEY_AD_COUNT, todayAdCount)
            .apply()
    }

    /** 剩余冷却毫秒数（<=0 表示可观看） */
    fun remainingCooldownMs(): Long {
        if (lastAdShownAt == 0L) return 0L
        val elapsed = System.currentTimeMillis() - lastAdShownAt
        return (AD_COOLDOWN_MS - elapsed).coerceAtLeast(0L)
    }

    /** 是否处于冷却中 */
    fun isInCooldown(): Boolean = remainingCooldownMs() > 0L

    /** 今日剩余可看次数 */
    fun remainingDailyCount(): Int = (DAILY_AD_LIMIT - todayAdCount).coerceAtLeast(0)

    /** 每日上限是否已用尽 */
    fun isDailyLimitReached(): Boolean = todayAdCount >= DAILY_AD_LIMIT

    /** 把剩余毫秒格式化为"X分Y秒"/"Y秒" */
    private fun formatRemaining(ms: Long): String {
        val totalSec = ((ms + 999L) / 1000L).toInt()
        return if (totalSec >= 60) {
            val m = totalSec / 60
            val s = totalSec % 60
            if (s == 0) "${m}分钟" else "${m}分${s}秒"
        } else "${totalSec}秒"
    }

    /**
     * 安全执行回调：确保在主线程运行，且捕获异常不崩溃
     */
    private inline fun safeCallback(crossinline block: () -> Unit) {
        val runnable = Runnable {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Ad callback exception", e)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    /**
     * 加载并展示激励视频广告
     * @param activity 当前 Activity
     * @param onRewarded 看完广告后的奖励回调
     * @param onFailed 广告加载/播放失败的回调
     * @param onLoadStart 开始加载广告回调（用于更新 loading 状态）
     * @param onComplete 广告流程结束回调（不管成功失败）
     * @param onCooldown 冷却中/超限提示回调（remainingMs<=0 表示已达每日上限）
     */
    fun showRewardAd(
        activity: Activity,
        onRewarded: () -> Unit,
        onFailed: (() -> Unit)? = null,
        onLoadStart: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        onCooldown: ((remainingMs: Long) -> Unit)? = null
    ) {
        // ===== 每日上限检查：当日已看次数 >= 30 时拦截 =====
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initDailyCount(prefs)

        if (_isLoadingAd.value) {
            return
        }

        if (todayAdCount >= DAILY_AD_LIMIT) {
            Log.i(TAG, "Daily ad limit reached: $todayAdCount/$DAILY_AD_LIMIT")
            safeCallback {
                if (onCooldown != null) {
                    // 传 0 表示已达每日上限，让调用方区分是冷却还是上限
                    onCooldown(0L)
                } else {
                    Toast.makeText(
                        activity,
                        "今日广告次数已用尽（$todayAdCount/$DAILY_AD_LIMIT），明天再来吧",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                onComplete?.invoke()
            }
            return
        }

        // ===== 冷却检查：距上次观看不足 2 分钟时拦截 =====
        val remaining = remainingCooldownMs()
        if (remaining > 0L) {
            Log.i(TAG, "Ad in cooldown, remaining=${remaining}ms")
            safeCallback {
                if (onCooldown != null) {
                    onCooldown(remaining)
                } else {
                    Toast.makeText(
                        activity,
                        "观看太频繁啦，请 ${formatRemaining(remaining)} 后再来观看广告",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                onComplete?.invoke()
            }
            return
        }

        setLoading(true)
        onLoadStart?.invoke()

        // 检查 Activity 是否仍然有效
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Activity is finishing/destroyed, skip ad load")
            setLoading(false)
            onFailed?.invoke()
            onComplete?.invoke()
            return
        }

        AdManager.getInstance().loadRewardVideo(activity, object : AdManager.RewardCallback {
            override fun onRewardVerify() {
                safeCallback {
                    // 广告验证成功 → 计入当日次数
                    todayAdCount++
                    persistDailyCount(activity)
                    onRewarded()
                }
            }

            override fun onVideoComplete() {}

            override fun onAdClose() {
                setLoading(false)
                safeCallback { onComplete?.invoke() }
            }

            override fun onLoadFail(error: String?) {
                Log.w(TAG, "Ad load failed: $error")
                setLoading(false)
                safeCallback {
                    onFailed?.invoke()
                    onComplete?.invoke()
                }
            }

            override fun onLoadSuccess() {
                setLoading(false)
                safeCallback {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        // 成功展示广告时记录时间戳，启动 2 分钟冷却
                        lastAdShownAt = System.currentTimeMillis()
                        AdManager.getInstance().showRewardVideo(activity)
                    } else {
                        Log.w(TAG, "Activity gone before showing ad")
                        onFailed?.invoke()
                        onComplete?.invoke()
                    }
                }
            }
        })
    }

    private fun setLoading(loading: Boolean) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _isLoadingAd.value = loading
        } else {
            mainHandler.post { _isLoadingAd.value = loading }
        }
    }
}
