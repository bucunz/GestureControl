package com.gesture.control

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var btnCamera: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnAccessibility: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var dotStatus: android.view.View

    private var modelDownloadId: Long? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
            if (id != modelDownloadId) return
            val tmp = File(getExternalFilesDir(null), "hand_landmarker.task")
            if (tmp.exists() && tmp.length() > 1_000_000) {
                val dest = File(filesDir, GestureRecognizerHelper.MODEL_NAME)
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
                log("模型已就绪（${dest.length() / 1024} KB）")
            } else {
                log("模型下载失败，请手动放入 assets/ 目录")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        btnCamera = findViewById(R.id.btnCamera)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        dotStatus = findViewById(R.id.dotStatus)

        btnToggle.setOnClickListener { toggle() }
        btnCamera.setOnClickListener { requestCamera() }
        btnOverlay.setOnClickListener { requestOverlay() }
        btnAccessibility.setOnClickListener { openAccessibilitySettings() }

        CameraGestureService.onStatusChanged = { running, msg ->
            runOnUiThread {
                tvStatus.text = if (running) getString(R.string.status_running) else getString(R.string.status_idle)
                dotStatus.setBackgroundResource(
                    if (running) R.drawable.circle_green else R.drawable.circle_gray
                )
                btnToggle.text = if (running) getString(R.string.btn_stop) else getString(R.string.btn_start)
                if (msg.isNotBlank()) log(msg)
            }
        }

        registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
        ensureModel()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    override fun onDestroy() {
        CameraGestureService.onStatusChanged = null
        try { unregisterReceiver(downloadReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    // ---------- 模型 ----------

    private fun hasModel(): Boolean {
        if (File(filesDir, GestureRecognizerHelper.MODEL_NAME).exists()) return true
        return try {
            assets.open(GestureRecognizerHelper.MODEL_NAME).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureModel() {
        if (hasModel()) {
            log("模型已就绪")
            return
        }
        log("未检测到模型，正在后台下载 hand_landmarker.task（约 7.8MB）…")
        val req = DownloadManager.Request(Uri.parse(GestureRecognizerHelper.MODEL_URL))
            .setTitle("下载手势识别模型")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(this, null, GestureRecognizerHelper.MODEL_NAME)
            .setAllowedOverMetered(true)
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        modelDownloadId = dm.enqueue(req)
    }

    // ---------- 开关 ----------

    private fun toggle() {
        if (CameraGestureService.isRunning) {
            CameraGestureService.stop(this)
            return
        }
        if (!hasCamera()) {
            toast(getString(R.string.toast_need_camera)); requestCamera(); return
        }
        if (!OverlayController.canDrawOverlays(this)) {
            toast(getString(R.string.toast_need_overlay)); requestOverlay(); return
        }
        if (!isAccessibilityEnabled()) {
            toast(getString(R.string.toast_need_accessibility)); openAccessibilitySettings(); return
        }
        if (!hasModel()) {
            toast("模型尚未下载完成，请稍候"); return
        }
        CameraGestureService.start(this)
    }

    // ---------- 权限 ----------

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestCamera() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002
            )
        }
    }

    private fun requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val i = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(i)
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${GestureAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
    }

    private fun refreshPermissionState() {
        btnCamera.text = if (hasCamera()) getString(R.string.granted) else getString(R.string.not_granted)
        btnOverlay.text =
            if (OverlayController.canDrawOverlays(this)) getString(R.string.granted) else getString(R.string.not_granted)
        btnAccessibility.text =
            if (isAccessibilityEnabled()) getString(R.string.granted) else getString(R.string.not_granted)

        val running = CameraGestureService.isRunning
        tvStatus.text = if (running) getString(R.string.status_running) else getString(R.string.status_idle)
        btnToggle.text = if (running) getString(R.string.btn_stop) else getString(R.string.btn_start)
    }

    private fun log(msg: String) {
        runOnUiThread {
            tvLog.text = "• $msg\n${tvLog.text}".take(600)
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
