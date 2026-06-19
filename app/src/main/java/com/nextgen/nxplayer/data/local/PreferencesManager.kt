package com.nextgen.nxplayer.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var privacyAccepted: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIVACY_ACCEPTED, value).apply()

    var resumePlayback: Boolean
        get() = prefs.getBoolean(KEY_RESUME_PLAYBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_RESUME_PLAYBACK, value).apply()

    var defaultSpeed: Float
        get() = prefs.getFloat(KEY_DEFAULT_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_DEFAULT_SPEED, value.coerceIn(0.25f, 4.0f)).apply()

    var aspectMode: String
        get() = prefs.getString(KEY_ASPECT_MODE, "FIT") ?: "FIT"
        set(value) = prefs.edit().putString(KEY_ASPECT_MODE, value).apply()

    var pipOnBack: Boolean
        get() = prefs.getBoolean(KEY_PIP_ON_BACK, false)
        set(value) = prefs.edit().putBoolean(KEY_PIP_ON_BACK, value).apply()

    var subtitleEnabledByDefault: Boolean
        get() = prefs.getBoolean(KEY_SUBTITLE_ENABLED_BY_DEFAULT, true)
        set(value) = prefs.edit().putBoolean(KEY_SUBTITLE_ENABLED_BY_DEFAULT, value).apply()

    var subtitleFontSize: Float
        get() = prefs.getFloat(KEY_SUBTITLE_FONT_SIZE, 16f)
        set(value) = prefs.edit().putFloat(KEY_SUBTITLE_FONT_SIZE, value.coerceIn(12f, 32f)).apply()

    var subtitleBackgroundAlpha: Float
        get() = prefs.getFloat(KEY_SUBTITLE_BACKGROUND_ALPHA, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_SUBTITLE_BACKGROUND_ALPHA, value.coerceIn(0f, 1f)).apply()

    var subtitleTextColor: String
        get() = prefs.getString(KEY_SUBTITLE_TEXT_COLOR, "WHITE") ?: "WHITE"
        set(value) = prefs.edit().putString(KEY_SUBTITLE_TEXT_COLOR, value).apply()

    var subtitleEncoding: String
        get() = prefs.getString(KEY_SUBTITLE_ENCODING, "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString(KEY_SUBTITLE_ENCODING, value).apply()

    var audioBoost: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_BOOST, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_BOOST, value).apply()

    var rememberAudioTrackPerVideo: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_AUDIO_TRACK_PER_VIDEO, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER_AUDIO_TRACK_PER_VIDEO, value).apply()

    var preferredAudioLanguage: String
        get() = prefs.getString(KEY_PREFERRED_AUDIO_LANGUAGE, "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString(KEY_PREFERRED_AUDIO_LANGUAGE, value).apply()

    var gesturesEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURES_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_GESTURES_ENABLED, value).apply()

    var doubleTapSeekSeconds: Int
        get() = prefs.getInt(KEY_DOUBLE_TAP_SEEK_SECONDS, 10)
        set(value) = prefs.edit().putInt(KEY_DOUBLE_TAP_SEEK_SECONDS, value.coerceIn(5, 30)).apply()

    var swipeVolumeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_VOLUME_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_VOLUME_ENABLED, value).apply()

    var swipeBrightnessEnabled: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_BRIGHTNESS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_BRIGHTNESS_ENABLED, value).apply()

    var longPressKidsLock: Boolean
        get() = prefs.getBoolean(KEY_LONG_PRESS_KIDS_LOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_LONG_PRESS_KIDS_LOCK, value).apply()

    var kidsLockDisableVolume: Boolean
        get() = prefs.getBoolean(KEY_KIDS_LOCK_DISABLE_VOLUME, true)
        set(value) = prefs.edit().putBoolean(KEY_KIDS_LOCK_DISABLE_VOLUME, value).apply()

    var kidsLockHideControls: Boolean
        get() = prefs.getBoolean(KEY_KIDS_LOCK_HIDE_CONTROLS, true)
        set(value) = prefs.edit().putBoolean(KEY_KIDS_LOCK_HIDE_CONTROLS, value).apply()

    fun resetToDefaults() {
        prefs.edit()
            .putBoolean(KEY_RESUME_PLAYBACK, true)
            .putFloat(KEY_DEFAULT_SPEED, 1.0f)
            .putString(KEY_ASPECT_MODE, "FIT")
            .putBoolean(KEY_PIP_ON_BACK, false)
            .putBoolean(KEY_SUBTITLE_ENABLED_BY_DEFAULT, true)
            .putFloat(KEY_SUBTITLE_FONT_SIZE, 16f)
            .putFloat(KEY_SUBTITLE_BACKGROUND_ALPHA, 0.5f)
            .putString(KEY_SUBTITLE_TEXT_COLOR, "WHITE")
            .putString(KEY_SUBTITLE_ENCODING, "AUTO")
            .putBoolean(KEY_AUDIO_BOOST, false)
            .putBoolean(KEY_REMEMBER_AUDIO_TRACK_PER_VIDEO, true)
            .putString(KEY_PREFERRED_AUDIO_LANGUAGE, "AUTO")
            .putBoolean(KEY_GESTURES_ENABLED, true)
            .putInt(KEY_DOUBLE_TAP_SEEK_SECONDS, 10)
            .putBoolean(KEY_SWIPE_VOLUME_ENABLED, true)
            .putBoolean(KEY_SWIPE_BRIGHTNESS_ENABLED, true)
            .putBoolean(KEY_LONG_PRESS_KIDS_LOCK, true)
            .putBoolean(KEY_KIDS_LOCK_DISABLE_VOLUME, true)
            .putBoolean(KEY_KIDS_LOCK_HIDE_CONTROLS, true)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "nxplayer_prefs"

        private const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
        private const val KEY_RESUME_PLAYBACK = "resume_playback"
        private const val KEY_DEFAULT_SPEED = "default_speed"
        private const val KEY_ASPECT_MODE = "player_aspect_mode"
        private const val KEY_PIP_ON_BACK = "pip_on_back"

        private const val KEY_SUBTITLE_ENABLED_BY_DEFAULT = "subtitle_enabled_by_default"
        private const val KEY_SUBTITLE_FONT_SIZE = "subtitle_font_size"
        private const val KEY_SUBTITLE_BACKGROUND_ALPHA = "subtitle_bg_alpha"
        private const val KEY_SUBTITLE_TEXT_COLOR = "subtitle_text_color"
        private const val KEY_SUBTITLE_ENCODING = "subtitle_encoding"

        private const val KEY_AUDIO_BOOST = "audio_boost"
        private const val KEY_REMEMBER_AUDIO_TRACK_PER_VIDEO = "remember_audio_track_per_video"
        private const val KEY_PREFERRED_AUDIO_LANGUAGE = "preferred_audio_language"

        private const val KEY_GESTURES_ENABLED = "gestures_enabled"
        private const val KEY_DOUBLE_TAP_SEEK_SECONDS = "double_tap_seek_seconds"
        private const val KEY_SWIPE_VOLUME_ENABLED = "swipe_volume_enabled"
        private const val KEY_SWIPE_BRIGHTNESS_ENABLED = "swipe_brightness_enabled"

        private const val KEY_LONG_PRESS_KIDS_LOCK = "long_press_kids_lock"
        private const val KEY_KIDS_LOCK_DISABLE_VOLUME = "kids_lock_disable_volume"
        private const val KEY_KIDS_LOCK_HIDE_CONTROLS = "kids_lock_hide_controls"
    }
}