package com.gesture.control

import android.util.Log
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 手势 → 动作的翻译层。
 * 关键设计：
 *  1. 连续 N 帧识别到同一手势才认定有效（防抖动误触）
 *  2. 每个动作有冷却时间，避免一次手势连发
 *  3. 光标模式下用食指指尖定位，拇指-食指捏合触发点击
 */
class GestureMapper(
    private val screenW: Int,
    private val screenH: Int,
    private val onAction: (Action) -> Unit,
    private val onCursor: (Float, Float, Boolean) -> Unit
) {

    enum class Action { BACK, HOME, RECENTS, SCROLL_UP, SCROLL_DOWN, CLICK, LONG_CLICK }

    // ---- 防抖状态 ----
    private var pending: String? = null
    private var pendingCount = 0
    private var lastFiredAt = 0L
    private var lastFiredGesture: String? = null

    // ---- 光标状态 ----
    private var cursorMode = false
    private var cursorX = screenW / 2f
    private var cursorY = screenH / 2f
    private var pinching = false
    private var pinchStartAt = 0L

    companion object {
        private const val TAG = "GestureMapper"
        private const val CONFIRM_FRAMES = 3     // 连续多少帧一致才触发
        private const val SCORE_THRESHOLD = 0.60f
        private const val PINCH_RATIO = 0.45f    // 相对手掌尺寸，越小越严格
        private const val LONG_PRESS_MS = 600L
    }

    fun process(result: GestureRecognizerResult) {
        val name = topGesture(result)
        val landmarks = result.landmarks()

        if (landmarks.isNullOrEmpty()) {
            // 手离开画面：退出光标模式，重置防抖
            cursorMode = false
            pinching = false
            pending = null
            pendingCount = 0
            onCursor(cursorX, cursorY, false)
            return
        }

        val hand = landmarks[0]

        // ---------- 光标模式 ----------
        if (cursorMode) {
            val tip = hand[8]     // 食指指尖
            val thumb = hand[4]   // 拇指指尖
            updateCursor(tip.x(), tip.y())

            val palmSpan = distance(hand[0], hand[9]).coerceAtLeast(1e-4f)
            val pinchDist = distance(thumb, tip)
            val nowPinching = pinchDist / palmSpan < PINCH_RATIO
            onCursor(cursorX, cursorY, nowPinching)

            if (nowPinching && !pinching) {
                pinchStartAt = System.currentTimeMillis()
                pinching = true
            } else if (!nowPinching && pinching) {
                val held = System.currentTimeMillis() - pinchStartAt
                pinching = false
                fire(
                    if (held >= LONG_PRESS_MS) Action.LONG_CLICK else Action.CLICK,
                    "pinch"
                )
            }
            // 光标模式下，明确的导航手势仍然可以打断
            if (name == "Closed_Fist" || name == "Open_Palm" || name == "Victory") {
                cursorMode = false
            } else {
                return
            }
        }

        // ---------- 普通手势模式 ----------
        if (name == null || name == "None") {
            pending = null
            pendingCount = 0
            return
        }

        if (name == pending) pendingCount++ else {
            pending = name
            pendingCount = 1
        }

        if (pendingCount >= CONFIRM_FRAMES) {
            when (name) {
                "Closed_Fist" -> fire(Action.BACK, name)
                "Open_Palm" -> fire(Action.HOME, name)
                "Victory" -> fire(Action.RECENTS, name)
                "Thumb_Up" -> fire(Action.SCROLL_UP, name)
                "Thumb_Down" -> fire(Action.SCROLL_DOWN, name)
                "Pointing_Up" -> {
                    if (!cursorMode) {
                        cursorMode = true
                        Log.d(TAG, "进入光标模式")
                    }
                }
            }
            pendingCount = 0
        }
    }

    private fun topGesture(result: GestureRecognizerResult): String? {
        val list = result.gestures()
        if (list.isNullOrEmpty()) return null
        val cats = list[0]
        if (cats.isNullOrEmpty()) return null
        val best = cats.maxByOrNull { it.score() } ?: return null
        return if (best.score() >= SCORE_THRESHOLD) best.categoryName() else null
    }

    private fun updateCursor(nx: Float, ny: Float) {
        // 以画面中心为原点做灵敏度放大，让小幅移动也能覆盖全屏
        val sx = ((nx - 0.5f) * Prefs.sensitivity + 0.5f).coerceIn(0f, 1f)
        val sy = ((ny - 0.5f) * Prefs.sensitivity + 0.5f).coerceIn(0f, 1f)
        cursorX = sx * screenW
        cursorY = sy * screenH
    }

    private fun fire(action: Action, key: String) {
        val now = System.currentTimeMillis()
        if (key == lastFiredGesture && now - lastFiredAt < Prefs.cooldownMs) return
        lastFiredGesture = key
        lastFiredAt = now
        onAction(action)
    }

    private fun distance(a: com.google.mediapipe.tasks.components.containers.Landmark,
                         b: com.google.mediapipe.tasks.components.containers.Landmark): Float =
        sqrt((a.x() - b.x()) * (a.x() - b.x()) + (a.y() - b.y()) * (a.y() - b.y()))

    fun currentCursor(): Pair<Float, Float> = cursorX to cursorY

    /** 供外部（如悬浮窗）强制复位 */
    fun reset() {
        cursorMode = false
        pinching = false
        pending = null
        pendingCount = 0
        lastFiredGesture = null
    }

    fun isCursorMode(): Boolean = cursorMode
}
