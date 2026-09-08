package com.dshx.game.su

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

object Bgm {
    private var player: MediaPlayer? = null
    private var ctx: Context? = null
    private var current = ""
    private var paused = false

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    fun playMenu() = play("bgm_menu")
    fun playCity() = play("bgm_city")

    private fun play(name: String) {
        val c = ctx ?: return
        if (current == name && player?.isPlaying == true) {
            applyVolume()
            return
        }
        stop()
        val rid = c.resources.getIdentifier(name, "raw", c.packageName)
        if (rid == 0) return
        try {
            val mp = MediaPlayer.create(c, rid) ?: return
            mp.isLooping = true
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            val v = Prefs.bgmVolume
            mp.setVolume(v, v)
            mp.start()
            player = mp
            current = name
            paused = false
        } catch (_: Throwable) {
            current = ""
        }
    }

    fun applyVolume() {
        val v = Prefs.bgmVolume
        try { player?.setVolume(v, v) } catch (_: Throwable) {}
    }

    fun pause() {
        try {
            if (player?.isPlaying == true) {
                player?.pause()
                paused = true
            }
        } catch (_: Throwable) {}
    }

    fun resume() {
        try {
            if (paused) {
                player?.start()
                paused = false
            }
        } catch (_: Throwable) {}
    }

    fun stop() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Throwable) {}
        player = null
        current = ""
        paused = false
    }
}
