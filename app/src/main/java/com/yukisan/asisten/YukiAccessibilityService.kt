package com.yukisan.asisten

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class YukiAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: YukiAccessibilityService? = null

        @Volatile
        var currentPackage: String = ""
    }

    fun isTargetApp(): Boolean = currentPackage in YukiState.targetPackages

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Wajib agar tombol volume bisa ditangkap
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { currentPackage = it }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        val isVolume = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (!isVolume || !YukiState.enabled || !isTargetApp()) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> swipe(Dir.RIGHT)
                KeyEvent.KEYCODE_VOLUME_UP -> swipe(Dir.LEFT)
            }
        }
        // Konsumsi juga event UP supaya volume tidak berubah
        return true
    }

    fun swipe(dir: Dir) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        val path = Path()
        when (dir) {
            Dir.UP -> {
                path.moveTo(cx, h * 0.75f)
                path.lineTo(cx, h * 0.25f)
            }
            Dir.DOWN -> {
                path.moveTo(cx, h * 0.25f)
                path.lineTo(cx, h * 0.75f)
            }
            Dir.LEFT -> {
                path.moveTo(w * 0.8f, cy)
                path.lineTo(w * 0.2f, cy)
            }
            Dir.RIGHT -> {
                path.moveTo(w * 0.2f, cy)
                path.lineTo(w * 0.8f, cy)
            }
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, 220L)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
