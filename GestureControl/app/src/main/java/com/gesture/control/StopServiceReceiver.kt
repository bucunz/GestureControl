package com.gesture.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 通知栏「停止」按钮 */
class StopServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        CameraGestureService.stop(context)
    }
}
