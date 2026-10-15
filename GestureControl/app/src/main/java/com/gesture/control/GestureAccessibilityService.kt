package com.gesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 真正"动手"的地方。
 * 普通 App 无法跨应用操作屏幕，只有无障碍服务可以：
 *  - performGlobalAction(...)  执行系统级导航
 *  - dispatchGesture(...)      模拟点击 / 滑动（Android 7.0+, API 24）
 */
class GestureAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "A11yService"

        /** 与 CameraGestureService 同进程，直接静态引用即可 */
        @Volatile
        var instance: GestureAccessibilityService? = null

        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "无障碍服务已连接")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    // ---------- 对外暴露的动作 ----------

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun notifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun lockScreen(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        else false

    /** 单击：一次极短的按下-抬起 */
    fun click(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val desc = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(desc, null, null)
    }

    /** 长按 */
    fun longClick(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val desc = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 700))
            .build()
        return dispatchGesture(desc, null, null)
    }

    /** 滑动 / 滚动，duration 建议 200~600ms */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 400): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val desc = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(desc, null, null)
    }

    /** 上滑（向下看内容） */
    fun scrollDown(): Boolean {
        val m = screenMetrics()
        return swipe(m.width * 0.5f, m.height * 0.72f, m.width * 0.5f, m.height * 0.28f, 350)
    }

    /** 下滑（向上看内容） */
    fun scrollUp(): Boolean {
        val m = screenMetrics()
        return swipe(m.width * 0.5f, m.height * 0.28f, m.width * 0.5f, m.height * 0.72f, 350)
    }

    private data class Metrics(val width: Float, val height: Float)

    private fun screenMetrics(): Metrics {
        val dm = resources.displayMetrics
        return Metrics(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
    }
}
