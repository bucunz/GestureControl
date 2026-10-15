package com.gesture.control

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * 前台服务：开摄像头 → 逐帧送 MediaPipe → 翻译手势 → 调无障碍服务执行动作。
 * Android 9+ 后台使用摄像头必须走前台服务，并在 Manifest 声明 foregroundServiceType="camera"。
 */
class CameraGestureService : LifecycleService() {

    private lateinit var overlay: OverlayController
    private lateinit var recognizer: GestureRecognizerHelper
    private var mapper: GestureMapper? = null
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.load(this)
        createChannel()
        startForeground(NOTI_ID, buildNotification())

        overlay = OverlayController(this)
        if (Prefs.showCursor && OverlayController.canDrawOverlays(this)) overlay.showCursor()

        val dm = resources.displayMetrics
        mapper = GestureMapper(dm.widthPixels, dm.heightPixels, ::handleAction, ::handleCursor)

        recognizer = GestureRecognizerHelper(this) { result ->
            mapper?.process(result)
        }

        // MediaPipe 初始化较重，放到 IO 线程
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = recognizer.setup()
            if (!ok) report(getString(R.string.toast_model_missing))
        }

        bindCamera()
        isRunning = true
        onStatusChanged?.invoke(true, "服务已启动")
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(analyzerExecutor, ::analyze)

                val selector = if (Prefs.useFrontCamera)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else
                    CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()
                provider.bindToLifecycle(this, selector, analysis)
                Log.i(TAG, "CameraX 已绑定")
            } catch (e: Exception) {
                Log.e(TAG, "绑定摄像头失败", e)
                report("摄像头启动失败：${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val rotation = proxy.imageInfo.rotationDegrees
            val src = rgbaToBitmap(proxy)
            val corrected = transform(src, rotation, Prefs.useFrontCamera)
            if (src !== corrected) src.recycle()
            recognizer.recognize(corrected, SystemClock.uptimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "分析帧失败: ${e.message}")
        } finally {
            proxy.close()
        }
    }

    /** ImageProxy(RGBA_8888) → Bitmap，不依赖 CameraX 的 Kotlin 扩展 */
    private fun rgbaToBitmap(proxy: ImageProxy): Bitmap {
        val plane = proxy.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * proxy.width
        val paddedWidth = proxy.width + rowPadding / pixelStride
        val tmp = Bitmap.createBitmap(paddedWidth, proxy.height, Bitmap.Config.ARGB_8888)
        tmp.copyPixelsFromBuffer(buffer)
        return if (paddedWidth == proxy.width) {
            tmp
        } else {
            val out = Bitmap.createBitmap(tmp, 0, 0, proxy.width, proxy.height)
            tmp.recycle()
            out
        }
    }

    /** 旋转到屏幕方向；前置摄像头再水平镜像，让左右移动符合直觉 */
    private fun transform(src: Bitmap, degrees: Int, mirror: Boolean): Bitmap {
        val m = Matrix()
        if (mirror) m.postScale(-1f, 1f)
        if (degrees != 0) m.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun handleCursor(x: Float, y: Float, pressed: Boolean) {
        if (Prefs.showCursor) overlay.updateCursor(x, y, pressed)
    }

    private fun handleAction(action: GestureMapper.Action) {
        mainHandler.post {
            val a11y = GestureAccessibilityService.instance
            if (a11y == null) {
                report(getString(R.string.toast_need_accessibility))
                return@post
            }
            when (action) {
                GestureMapper.Action.BACK -> a11y.back()
                GestureMapper.Action.HOME -> a11y.home()
                GestureMapper.Action.RECENTS -> a11y.recents()
                GestureMapper.Action.SCROLL_UP -> a11y.scrollUp()
                GestureMapper.Action.SCROLL_DOWN -> a11y.scrollDown()
                GestureMapper.Action.CLICK -> {
                    val (x, y) = mapper?.currentCursor() ?: return@post
                    a11y.click(x, y)
                }
                GestureMapper.Action.LONG_CLICK -> {
                    val (x, y) = mapper?.currentCursor() ?: return@post
                    a11y.longClick(x, y)
                }
            }
            vibrate()
            report("执行：${action.name}")
        }
    }

    private fun report(text: String) {
        onStatusChanged?.invoke(true, text)
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else {
                getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            v?.vibrate(
                android.os.VibrationEffect.createOneShot(
                    25,
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } catch (e: Exception) {
            // 部分设备无马达，忽略
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.channel_desc) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, StopServiceReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(this, 0, stopIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "停止", pi)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "解绑摄像头失败: ${e.message}")
        }
        recognizer.close()
        if (::overlay.isInitialized) overlay.hideCursor()
        analyzerExecutor.shutdown()
        onStatusChanged?.invoke(false, "已停止")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    companion object {
        private const val TAG = "CameraGestureService"
        private const val CHANNEL_ID = "gesture_control"
        private const val NOTI_ID = 1001

        @Volatile
        var isRunning: Boolean = false

        /** 供 UI 订阅运行状态与日志 */
        var onStatusChanged: ((Boolean, String) -> Unit)? = null

        fun start(context: Context) {
            val i = Intent(context, CameraGestureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CameraGestureService::class.java))
        }
    }
}
