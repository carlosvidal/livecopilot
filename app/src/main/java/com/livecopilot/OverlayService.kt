package com.livecopilot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var expandedView: View
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var expandedParams: WindowManager.LayoutParams

    private var isExpanded = false

    // Posición persistente de la burbuja
    private var lastBubbleX = 50
    private var lastBubbleY = 200

    private val shortcuts = mutableListOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        initViews()
        showBubble()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar vistas al destruir el servicio
        if (::bubbleView.isInitialized && bubbleView.isAttachedToWindow) {
            windowManager.removeView(bubbleView)
        }
        if (::expandedView.isInitialized && expandedView.isAttachedToWindow) {
            windowManager.removeView(expandedView)
        }
    }

    private fun startInForeground() {
        val channelId = "livecopilot_overlay"
        val channelName = "LiveCopilot Overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LiveCopilot activo")
            .setContentText("Burbuja flotante en ejecución")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    private fun initViews() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        // --- Inicializar y configurar Bubble View ---
        bubbleView = inflater.inflate(R.layout.bubble_layout, null)
        val sizePx = (48 * resources.displayMetrics.density).toInt()
        bubbleParams = WindowManager.LayoutParams(
            sizePx, sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastBubbleX
            y = lastBubbleY
        }
        setupBubbleTouchListener()

        // --- Inicializar y configurar Expanded View ---
        expandedView = inflater.inflate(R.layout.expanded_layout, null)
        expandedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        setupExpandedViewListeners()
        loadShortcutsFromPrefs()
        populateShortcuts()
    }

    private fun showBubble() {
        if (bubbleView.isAttachedToWindow) return
        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun toggleExpansion() {
        if (isExpanded) {
            collapse()
        } else {
            expand()
        }
    }

    private fun expand() {
        if (isExpanded) return
        isExpanded = true

        // Guardar última posición de la burbuja y ocultarla
        lastBubbleX = bubbleParams.x
        lastBubbleY = bubbleParams.y
        bubbleView.visibility = View.GONE

        // Recargar atajos por si han cambiado
        loadShortcutsFromPrefs()
        populateShortcuts()

        // Posicionar y mostrar el menú expandido
        expandedParams.x = lastBubbleX
        expandedParams.y = lastBubbleY
        windowManager.addView(expandedView, expandedParams)
    }

    private fun collapse() {
        if (!isExpanded) return
        isExpanded = false

        // Guardar última posición del menú y ocultarlo
        lastBubbleX = expandedParams.x
        lastBubbleY = expandedParams.y
        windowManager.removeView(expandedView)

        // Restaurar y mostrar la burbuja en la nueva posición
        bubbleParams.x = lastBubbleX
        bubbleParams.y = lastBubbleY
        bubbleView.visibility = View.VISIBLE
        windowManager.updateViewLayout(bubbleView, bubbleParams)
    }

    private fun setupBubbleTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val endX = event.rawX
                    val endY = event.rawY
                    if (isAClick(initialTouchX, endX, initialTouchY, endY)) {
                        toggleExpansion()
                    } else {
                        // Snap to edge
                        val screenWidth = resources.displayMetrics.widthPixels
                        if (bubbleParams.x > screenWidth / 2) {
                            bubbleParams.x = screenWidth - bubbleView.width
                        } else {
                            bubbleParams.x = 0
                        }
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    bubbleParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    bubbleParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupExpandedViewListeners() {
        expandedView.findViewById<ImageView>(R.id.collapse_icon).setOnClickListener { collapse() }
        expandedView.findViewById<ImageView>(R.id.settings_icon).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            collapse()
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        expandedView.findViewById<ImageView>(R.id.grip_icon).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = expandedParams.x
                    initialY = expandedParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    expandedParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    expandedParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(expandedView, expandedParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun populateShortcuts() {
        val shortcutsLayout = expandedView.findViewById<LinearLayout>(R.id.shortcuts_container)
        shortcutsLayout.removeAllViews() // Limpiar por si acaso

        val rainbowColors = listOf(
            0xFFFF5252.toInt(), 0xFFFF9800.toInt(), 0xFFFFEB3B.toInt(),
            0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFF9C27B0.toInt()
        )

        shortcuts.forEachIndexed { index, text ->
            val btn = LayoutInflater.from(this).inflate(R.layout.shortcut_button, shortcutsLayout, false) as FrameLayout
            val number = btn.findViewById<TextView>(R.id.shortcut_number)
            number.text = (index + 1).toString()
            number.setBackgroundResource(R.drawable.shortcut_circle_bg)
            number.background.setTint(rainbowColors[index % rainbowColors.size])
            btn.setOnClickListener {
                copyToClipboard(text)
                Toast.makeText(this, "Mensaje copiado", Toast.LENGTH_SHORT).show()
                collapse()
            }
            shortcutsLayout.addView(btn)
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("LiveCopilot Shortcut", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun loadShortcutsFromPrefs() {
        shortcuts.clear()
        val prefs = getSharedPreferences("livecopilot_prefs", Context.MODE_PRIVATE)
        val defaultShortcuts = listOf(
            "¡Gracias por tu compra! Aquí está tu link de pago: https://pago.link/123",
            "Si tienes dudas, escríbenos por WhatsApp: https://wa.me/123456789",
            "Envío gratis por compras hoy 🚚",
            "¡Oferta especial solo en vivo!",
            "¿Quieres otro producto? Comenta abajo",
            "Finaliza tu compra aquí: https://pago.link/xyz"
        )
        for (i in 0 until 6) {
            val shortcut = prefs.getString("shortcut_$i", defaultShortcuts.getOrElse(i) { "" }) ?: ""
            shortcuts.add(shortcut)
        }
    }

    private fun isAClick(startX: Float, endX: Float, startY: Float, endY: Float): Boolean {
        val differenceX = Math.abs(startX - endX)
        val differenceY = Math.abs(startY - endY)
        return differenceX <= 5 && differenceY <= 5
    }
}
