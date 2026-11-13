package com.yukisan.asisten

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class YukiService : Service(), SensorEventListener {

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var overlay: LinearLayout? = null
    private var bubble: ImageView? = null
    private var toggleBtn: TextView? = null
    private var pulse: ObjectAnimator? = null
    private var sensorManager: SensorManager? = null

    // State untuk deteksi lambaian
    private var isNear = false
    private var nearSince = 0L
    private var lastWave = 0L

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildOverlay()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun startAsForeground() {
        val channelId = "yuki_run"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "Yukisan Asisten", NotificationManager.IMPORTANCE_LOW)
        )
        val notif = Notification.Builder(this, channelId)
            .setContentTitle("Yukisan Asisten aktif")
            .setContentText("Bubble ada di layar. Tap bubble untuk membuka menu.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notif)
        }
    }

    private fun roundBg(hex: String, radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(hex))
        setCornerRadius(dp(radiusDp).toFloat())
        setStroke(dp(1), Color.parseColor("#668FE8FF"))
    }

    private fun menuButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundBg("#4B2FA8", 12)
            layoutParams = LinearLayout.LayoutParams(dp(132), dp(44)).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            setOnClickListener { onClick() }
        }

    private fun buildOverlay() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Panel menu (awalnya tersembunyi)
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundBg("#EE150C2A", 18)
        }
        p.addView(menuButton("↑  Naik") { swipe(Dir.UP) })
        p.addView(menuButton("↓  Turun") { swipe(Dir.DOWN) })
        p.addView(menuButton("←  Kiri") { swipe(Dir.LEFT) })
        p.addView(menuButton("→  Kanan") { swipe(Dir.RIGHT) })
        toggleBtn = menuButton("ON") { toggle() }
        p.addView(toggleBtn)
        p.addView(menuButton("✕  Tutup") { stopSelf() })

        // Bubble logo dengan aura
        val bubbleBg = GradientDrawable().apply {
            setShape(GradientDrawable.OVAL)
            setGradientType(GradientDrawable.RADIAL_GRADIENT)
            setGradientRadius(dp(40).toFloat())
            setColors(
                intArrayOf(
                    Color.parseColor("#9F7BFF"),
                    Color.parseColor("#2A1B5E"),
                    Color.parseColor("#0B0720")
                )
            )
            setStroke(dp(2), Color.parseColor("#88FFB6E1"))
        }
        val b = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_fg)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = bubbleBg
            elevation = dp(10).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(68))
        }
        bubble = b

        // Animasi napas pelan di bubble saja. Panel menu tidak ikut animasi.
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            b,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f)
        ).apply {
            duration = 1400L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }

        container.addView(b)
        container.addView(p)

        // Bubble bisa digeser. Tap tanpa geser = buka atau tutup panel.
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        b.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        overlay?.let { wm.updateViewLayout(it, params) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        p.visibility = if (p.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                }
            }
            true
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(180)
        }

        wm.addView(container, params)
        overlay = container
        renderToggle()
    }

    private fun renderToggle() {
        toggleBtn?.text = if (YukiState.enabled) "ON ●" else "OFF"
        bubble?.alpha = if (YukiState.enabled) 1f else 0.45f
    }

    private fun toggle() {
        YukiState.enabled = !YukiState.enabled
        renderToggle()
    }

    private fun swipe(dir: Dir) {
        val svc = YukiAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(this, "Aktifkan Accessibility Yukisan dulu", Toast.LENGTH_SHORT).show()
            return
        }
        svc.swipe(dir)
    }

    // Sensor proximity: lambaian cepat di atas sensor = video berikutnya
    override fun onSensorChanged(event: SensorEvent) {
        val near = event.values[0] < event.sensor.maximumRange
        val now = SystemClock.elapsedRealtime()

        if (near && !isNear) {
            isNear = true
            nearSince = now
        } else if (!near && isNear) {
            isNear = false
            val duration = now - nearSince
            // 40 sampai 700 ms = lambaian. Lebih lama dianggap tangan menutup sensor.
            if (duration in 40L..700L && now - lastWave > 900L) {
                val svc = YukiAccessibilityService.instance
                if (YukiState.enabled && svc != null && svc.isTargetApp()) {
                    lastWave = now
                    svc.swipe(Dir.UP)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        pulse?.cancel()
        overlay?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
                // view sudah tidak ada, abaikan
            }
        }
        overlay = null
        super.onDestroy()
    }
}
