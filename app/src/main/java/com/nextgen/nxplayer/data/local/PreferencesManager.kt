package com.nextgen.nxplayer.data.local

import android.content.Context
import android.content.SharedPreferences
import com.nextgen.nxplayer.R

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nxplayer_prefs", Context.MODE_PRIVATE)

    var resumePlayback: Boolean
        get() = prefs.getBoolean("resume_playback", true)
        set(value) = prefs.edit().putBoolean("resume_playback", value).apply()

    var defaultSpeed: Float
        get() = prefs.getFloat("default_speed", 1.0f)
        set(value) = prefs.edit().putFloat("default_speed", value).apply()

    var audioBoost: Boolean
        get() = prefs.getBoolean("audio_boost", false)
        set(value) = prefs.edit().putBoolean("audio_boost", value).apply()

    var subtitleFontSize: Float
        get() = prefs.getFloat("subtitle_font_size", 16f)
        set(value) = prefs.edit().putFloat("subtitle_font_size", value).apply()

    var subtitleBackgroundAlpha: Float
        get() = prefs.getFloat("subtitle_bg_alpha", 0.5f)
        set(value) = prefs.edit().putFloat("subtitle_bg_alpha", value).apply()

    var kidsLockDisableVolume: Boolean
        get() = prefs.getBoolean("kids_lock_disable_volume", true)
        set(value) = prefs.edit().putBoolean("kids_lock_disable_volume", value).apply()

    var sleepTimerMinutes: Int
        get() = prefs.getInt("sleep_timer", -1) // -1 = disabled
        set(value) = prefs.edit().putInt("sleep_timer", value).apply()
}