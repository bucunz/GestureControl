package com.gesture.control

import android.content.Context

/** 极简配置存储：只保存少量开关与灵敏度 */
object Prefs {

    private const val FILE = "gesture_prefs"
    private const val KEY_DELEGATE = "delegate"          // 0 = CPU, 1 = GPU
    private const val KEY_COOLDOWN = "cooldown_ms"       // 手势触发冷却时间
    private const val KEY_SHOW_CURSOR = "show_cursor"    // 是否显示悬浮光标
    private const val KEY_USE_FRONT = "use_front"        // 是否使用前置摄像头
    private const val KEY_SENSITIVITY = "sensitivity"    // 光标灵敏度 0.5~2.0

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var delegate: Int
        get() = _delegate
        set(value) { _delegate = value }

    private var _delegate: Int = 0

    fun load(context: Context) {
        val s = sp(context)
        _delegate = s.getInt(KEY_DELEGATE, 0)
        cooldownMs = s.getInt(KEY_COOLDOWN, 900)
        showCursor = s.getBoolean(KEY_SHOW_CURSOR, true)
        useFrontCamera = s.getBoolean(KEY_USE_FRONT, true)
        sensitivity = s.getFloat(KEY_SENSITIVITY, 1.2f)
    }

    fun save(context: Context) {
        sp(context).edit()
            .putInt(KEY_DELEGATE, _delegate)
            .putInt(KEY_COOLDOWN, cooldownMs)
            .putBoolean(KEY_SHOW_CURSOR, showCursor)
            .putBoolean(KEY_USE_FRONT, useFrontCamera)
            .putFloat(KEY_SENSITIVITY, sensitivity)
            .apply()
    }

    @JvmField var cooldownMs: Int = 900
    @JvmField var showCursor: Boolean = true
    @JvmField var useFrontCamera: Boolean = true
    @JvmField var sensitivity: Float = 1.2f
}
