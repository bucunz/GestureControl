package com.gesture.control

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager

/**
 * 悬浮光标：一个不拦截触摸、覆盖全屏的圆形指示点。
 * 需要在 Manifest 中声明 SYSTEM_ALERT_WINDOW，并由用户手动授权。
 */
class OverlayController(context: Context) {

    private val app = context.applicationContext
    private val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cursorView: View? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        private const val TAG = "Overlay"
        private const val SIZE_DP = 34

        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                    Settings.canDrawOverlays(context)
    }

    private fun buildParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = 0
            y = 0
        }
    }

    fun showCursor() {
        if (!canDrawOverlays(app)) {
            Log.w(TAG, "无悬浮窗权限，跳过光标")
            return
        }
        mainHandler.post {
            if (cursorView != null) return@post
            try {
                val v = LayoutInflater.from(app).inflate(R.layout.overlay_cursor, null)
                val p = buildParams()
                wm.addView(v, p)
                cursorView = v
                params = p
                v.visibility = View.INVISIBLE
            } catch (e: Exception) {
                Log.e(TAG, "添加光标失败", e)
            }
        }
    }

    fun hideCursor() {
        mainHandler.post {
            try {
                cursorView?.let { wm.removeView(it) }
            } catch (e: Exception) {
                Log.w(TAG, "移除光标失败: ${e.message}")
            }
            cursorView = null
            params = null
        }
    }

    /** x, y 为屏幕绝对坐标（左上角为原点） */
    fun updateCursor(x: Float, y: Float, pressed: Boolean) {
        mainHandler.post {
            val v = cursorView ?: return@post
            val p = params ?: return@post
            v.visibility = View.VISIBLE
            val offset = (SIZE_DP * app.resources.displayMetrics.density / 2f).toInt()
            p.x = (x - offset).toInt().coerceIn(0, app.resources.displayMetrics.widthPixels - offset * 2)
            p.y = (y - offset).toInt().coerceIn(0, app.resources.displayMetrics.heightPixels - offset * 2)
            try {
                wm.updateViewLayout(v, p)
            } catch (e: Exception) {
                Log.w(TAG, "更新光标失败: ${e.message}")
            }
            val scale = if (pressed) 1.35f else 1.0f
            v.animate().cancel()
            v.animate().scaleX(scale).scaleY(scale).setDuration(90).start()
            v.alpha = if (pressed) 1f else 0.85f
        }
    }

    fun setCursorVisible(visible: Boolean) {
        mainHandler.post {
            cursorView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        }
    }
}
