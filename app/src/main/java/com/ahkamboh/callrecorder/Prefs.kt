package com.ahkamboh.callrecorder

import android.content.Context
import android.media.MediaRecorder

object Prefs {
    private const val FILE = "prefs"
    private const val KEY_SOURCE = "source"
    private const val KEY_ENABLED = "enabled"
    const val KEY_RULE_MODE = "rule_mode"
    const val KEY_RULE_NUMBERS = "rule_numbers"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun source(ctx: Context): Int = p(ctx).getInt(KEY_SOURCE, MediaRecorder.AudioSource.VOICE_RECOGNITION)
    fun setSource(ctx: Context, source: Int) = p(ctx).edit().putInt(KEY_SOURCE, source).apply()

    fun enabled(ctx: Context): Boolean = p(ctx).getBoolean(KEY_ENABLED, true)
    fun setEnabled(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean(KEY_ENABLED, on).apply()

    fun getInt(ctx: Context, key: String, def: Int) = p(ctx).getInt(key, def)
    fun getString(ctx: Context, key: String): String? = p(ctx).getString(key, null)
    fun put(ctx: Context, key: String, value: Int) = p(ctx).edit().putInt(key, value).apply()
    fun put(ctx: Context, key: String, value: String) = p(ctx).edit().putString(key, value).apply()
}
