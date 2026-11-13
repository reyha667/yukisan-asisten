package com.yukisan.asisten

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(56), dp(24), dp(24))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#2A1B5E"), Color.parseColor("#05030D"))
            )
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_fg)
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(120))
        }
        val title = TextView(this).apply {
            text = "Yukisan Asisten"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val sub = TextView(this).apply {
            text = "Scroll video pakai tombol volume, gesture, dan lambaian"
            textSize = 14f
            setTextColor(Color.parseColor("#B9A8FF"))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(20))
        }
        status = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }

        root.addView(logo)
        root.addView(title)
        root.addView(sub)
        root.addView(status)
        root.addView(button("1. Izinkan tampil di atas app") { requestOverlay() })
        root.addView(button("2. Aktifkan Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("3. Izinkan notifikasi") { requestNotif() })
        root.addView(button("▶  Jalankan Yukisan") { start() })
        root.addView(button("■  Hentikan") {
            stopService(Intent(this, YukiService::class.java))
            toast("Yukisan dihentikan")
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5B3FD6"))
                setCornerRadius(dp(14).toFloat())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(6)) }
            setOnClickListener { onClick() }
        }

    private fun requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            toast("Izin overlay sudah aktif")
        } else {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun requestNotif() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun start() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Izinkan tampil di atas app dulu")
            return
        }
        if (!isAccessibilityOn()) {
            toast("Aktifkan Accessibility Yukisan dulu")
            return
        }
        startForegroundService(Intent(this, YukiService::class.java))
        toast("Yukisan aktif. Bubble muncul di layar")
    }

    private fun isAccessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, YukiAccessibilityService::class.java).flattenToString()
        return enabled.split(":").any { it.equals(me, ignoreCase = true) }
    }

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val access = isAccessibilityOn()
        val notif = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        status.text = "Overlay: ${mark(overlay)}\nAccessibility: ${mark(access)}\nNotifikasi: ${mark(notif)}"
    }

    private fun mark(ok: Boolean) = if (ok) "✅" else "❌"

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
