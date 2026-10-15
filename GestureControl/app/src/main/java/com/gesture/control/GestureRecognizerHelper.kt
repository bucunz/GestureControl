package com.gesture.control

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResultListener
import java.io.File

/**
 * MediaPipe Gesture Recognizer 封装。
 * 输入：旋转/镜像校正后的 Bitmap
 * 输出：手势类别 + 21 个手部关键点
 */
class GestureRecognizerHelper(
    private val context: Context,
    private val onResult: (GestureRecognizerResult) -> Unit
) {

    private var recognizer: GestureRecognizer? = null

    /** 模型优先读内部存储（可被脚本下载），其次读 assets */
    private fun resolveModelPath(): String {
        val f = File(context.filesDir, MODEL_NAME)
        return if (f.exists()) f.absolutePath else MODEL_NAME
    }

    fun setup(): Boolean {
        return try {
            val delegate =
                if (Prefs.delegate == 1) BaseOptions.DELEGATE_GPU else BaseOptions.DELEGATE_CPU

            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(resolveModelPath())
                .setDelegate(delegate)
                .build()

            val options = GestureRecognizer.GestureRecognizerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setResultListener(object : GestureRecognizerResultListener {
                    override fun onGestureRecognizerResult(
                        result: GestureRecognizerResult,
                        input: MPImage
                    ) {
                        onResult(result)
                    }
                })
                .setErrorListener { e ->
                    Log.e(TAG, "GestureRecognizer 错误", e)
                }
                .build()

            recognizer = GestureRecognizer.createFromOptions(context, options)
            Log.i(TAG, "手势识别器初始化完成，delegate=$delegate")
            true
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败，确认 assets 中是否存在 $MODEL_NAME", e)
            false
        }
    }

    /**
     * LIVE_STREAM 模式要求 timestampMs 单调递增，否则会直接报错。
     */
    fun recognize(bitmap: android.graphics.Bitmap, timestampMs: Long) {
        val r = recognizer ?: return
        try {
            val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
            r.recognizeAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.w(TAG, "recognizeAsync 失败: ${e.message}")
        }
    }

    fun close() {
        try {
            recognizer?.close()
        } catch (e: Exception) {
            Log.w(TAG, "close 失败: ${e.message}")
        }
        recognizer = null
    }

    companion object {
        private const val TAG = "GRHelper"
        const val MODEL_NAME = "hand_landmarker.task"
        const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
    }
}
