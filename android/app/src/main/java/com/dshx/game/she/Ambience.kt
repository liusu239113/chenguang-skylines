package com.dshx.game.she

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlin.random.Random

/** 天气环境音：雨循环 + 风循环 + 间歇雷声 */
object Ambience {
    private var ctx: Context? = null
    private var rain: MediaPlayer? = null
    private var wind: MediaPlayer? = null
    private var thunderAcc = 4f
    var lightning = 0f
        private set

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    fun tick(dt: Float, weather: Int, onMap: Boolean) {
        if (!onMap) {
            stop()
            lightning = (lightning - dt * 4f).coerceAtLeast(0f)
            return
        }
        val sfx = Prefs.sfxVolume
        when (weather) {
            1 -> {
                ensure(true, false)
                setVol(rain, 0.42f * sfx)
                setVol(wind, 0.12f * sfx)
                thunderAcc -= dt
                if (thunderAcc <= 0f) {
                    Sfx.play("sfx_thunder", 0.55f + Random.nextFloat() * 0.35f)
                    lightning = 0.55f + Random.nextFloat() * 0.35f
                    thunderAcc = 7f + Random.nextFloat() * 11f
                }
            }
            2 -> {
                ensure(false, true)
                setVol(rain, 0f)
                setVol(wind, 0.28f * sfx)
                thunderAcc = 6f
            }
            else -> {
                ensure(false, true)
                setVol(rain, 0f)
                setVol(wind, 0.08f * sfx)
                thunderAcc = 5f
            }
        }
        if (lightning > 0f) lightning = (lightning - dt * 2.8f).coerceAtLeast(0f)
    }

    private fun ensure(needRain: Boolean, needWind: Boolean) {
        if (needRain && rain == null) rain = loopPlayer("sfx_rain")
        if (needWind && wind == null) wind = loopPlayer("sfx_wind")
    }

    private fun loopPlayer(name: String): MediaPlayer? {
        val c = ctx ?: return null
        val rid = c.resources.getIdentifier(name, "raw", c.packageName)
        if (rid == 0) return null
        return try {
            val mp = MediaPlayer.create(c, rid) ?: return null
            mp.isLooping = true
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setVolume(0f, 0f)
            mp.start()
            mp
        } catch (_: Throwable) {
            null
        }
    }

    private fun setVol(mp: MediaPlayer?, v: Float) {
        try {
            val x = v.coerceIn(0f, 1f)
            mp?.setVolume(x, x)
            if (x <= 0.01f) {
                if (mp?.isPlaying == true) mp.pause()
            } else if (mp?.isPlaying == false) {
                mp.start()
            }
        } catch (_: Throwable) {}
    }

    fun stop() {
        try { rain?.pause() } catch (_: Throwable) {}
        try { wind?.pause() } catch (_: Throwable) {}
    }

    fun release() {
        try { rain?.release() } catch (_: Throwable) {}
        try { wind?.release() } catch (_: Throwable) {}
        rain = null
        wind = null
    }
}
