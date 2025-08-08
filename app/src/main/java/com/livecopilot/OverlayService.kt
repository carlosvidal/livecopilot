package com.livecopilot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import kotlin.math.*
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
import androidx.core.content.FileProvider
import com.livecopilot.data.Product
import com.livecopilot.data.ProductManager
import com.livecopilot.utils.ImageUtils
import java.io.File

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var fanView: View
    private lateinit var catalogView: View
    private lateinit var productsCatalogView: View
    private lateinit var galleryView: View
    private lateinit var cartView: View
    private lateinit var favoritesView: View
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var fanParams: WindowManager.LayoutParams
    private lateinit var catalogParams: WindowManager.LayoutParams
    private lateinit var productsCatalogParams: WindowManager.LayoutParams
    private lateinit var galleryParams: WindowManager.LayoutParams
    private lateinit var cartParams: WindowManager.LayoutParams
    private lateinit var favoritesParams: WindowManager.LayoutParams

    private var currentState = BubbleState.COLLAPSED
    
    enum class BubbleState {
        COLLAPSED, FAN, CATALOG, PRODUCTS_CATALOG, GALLERY, CART, FAVORITES
    }

    // Posición persistente de la burbuja
    private var lastBubbleX = 0   // Pegado al borde izquierdo
    private var lastBubbleY = 300 // Más abajo para dar espacio al abanico

    private val shortcuts = mutableListOf<String>()
    private lateinit var productManager: ProductManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        productManager = ProductManager(this)
        startInForeground()
        initViews()
        showBubble()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_SHOW_BUBBLE -> {
                    if (currentState == BubbleState.COLLAPSED && !bubbleView.isAttachedToWindow) {
                        showBubble()
                    }
                }
            }
        }
        return START_STICKY
    }
    
    companion object {
        const val ACTION_SHOW_BUBBLE = "SHOW_BUBBLE"
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar vistas al destruir el servicio
        if (::bubbleView.isInitialized && bubbleView.isAttachedToWindow) {
            windowManager.removeView(bubbleView)
        }
        if (::fanView.isInitialized && fanView.isAttachedToWindow) {
            windowManager.removeView(fanView)
        }
        if (::catalogView.isInitialized && catalogView.isAttachedToWindow) {
            windowManager.removeView(catalogView)
        }
        if (::productsCatalogView.isInitialized && productsCatalogView.isAttachedToWindow) {
            windowManager.removeView(productsCatalogView)
        }
        if (::galleryView.isInitialized && galleryView.isAttachedToWindow) {
            windowManager.removeView(galleryView)
        }
        if (::cartView.isInitialized && cartView.isAttachedToWindow) {
            windowManager.removeView(cartView)
        }
        if (::favoritesView.isInitialized && favoritesView.isAttachedToWindow) {
            windowManager.removeView(favoritesView)
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

        // --- Inicializar y configurar Fan View ---
        fanView = inflater.inflate(R.layout.fan_layout, null)
        fanParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        setupFanViewListeners()

        // --- Inicializar y configurar Catalog View ---
        catalogView = inflater.inflate(R.layout.catalog_layout, null)
        catalogParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        setupCatalogViewListeners()
        
        // --- Inicializar Products Catalog Modal ---
        productsCatalogView = inflater.inflate(R.layout.products_catalog_modal, null)
        productsCatalogParams = createCenteredModalParams()
        setupProductsCatalogViewListeners()
        
        // --- Inicializar Gallery Modal ---
        galleryView = inflater.inflate(R.layout.gallery_modal, null)
        galleryParams = createCenteredModalParams()
        setupGalleryViewListeners()

        // --- Inicializar Cart Modal ---
        cartView = inflater.inflate(R.layout.cart_modal, null)
        cartParams = createCenteredModalParams()
        setupCartViewListeners()

        // --- Inicializar Favorites Modal ---
        favoritesView = inflater.inflate(R.layout.favorites_modal, null)
        favoritesParams = createCenteredModalParams()
        setupFavoritesViewListeners()
        
        loadShortcutsFromPrefs()
        populateCatalogShortcuts()
    }

    private fun createCenteredModalParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun showBubble() {
        if (bubbleView.isAttachedToWindow) return
        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun showFan() {
        hideAllViews()
        currentState = BubbleState.FAN
        
        // El fan ahora es fullscreen
        windowManager.addView(fanView, fanParams)
        
        // Posicionar los botones del abanico dinámicamente
        positionFanButtons()
    }

    private fun positionFanButtons() {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val isOnLeftSide = lastBubbleX < screenWidth / 2
        
        // Radio del abanico reducido 10 píxeles más
        val fanRadius = (98 * resources.displayMetrics.density).toInt()
        
        // Centro del abanico es la posición actual de la burbuja
        val centerX = lastBubbleX + 30  // 30 = 60dp/2 (mitad del bubble)
        val centerY = lastBubbleY + 30  // 30 = 60dp/2 (mitad del bubble)
        
        // Posicionar el botón cerrar exactamente donde estaba la burbuja
        val centerCloseBtn = fanView.findViewById<ImageView>(R.id.btn_close)
        val centerCloseParams = centerCloseBtn.layoutParams as FrameLayout.LayoutParams
        centerCloseParams.leftMargin = lastBubbleX
        centerCloseParams.topMargin = lastBubbleY
        centerCloseBtn.layoutParams = centerCloseParams
        
        // Botones del abanico
        val buttons = listOf(
            fanView.findViewById<ImageView>(R.id.btn_catalog),
            fanView.findViewById<ImageView>(R.id.btn_gallery),
            fanView.findViewById<ImageView>(R.id.btn_favorites),
            fanView.findViewById<ImageView>(R.id.btn_cart),
            fanView.findViewById<ImageView>(R.id.btn_settings)
        )
        
        // Ángulos para el abanico (en radianes) - arco más amplio
        val startAngle = if (isOnLeftSide) -PI/2.5 else PI - PI/2.5  // Más amplio
        val endAngle = if (isOnLeftSide) PI/2.5 else PI + PI/2.5     
        val angleStep = (endAngle - startAngle) / (buttons.size - 1)
        
        buttons.forEachIndexed { index, button ->
            val angle = startAngle + (index * angleStep)
            val x = centerX + (fanRadius * cos(angle)).toInt() - 24  // 24 = 48dp/2
            val y = centerY + (fanRadius * sin(angle)).toInt() - 24
            
            val params = button.layoutParams as FrameLayout.LayoutParams
            params.leftMargin = maxOf(0, minOf(x, screenWidth - 48))  // Limitar a pantalla
            params.topMargin = maxOf(0, minOf(y, screenHeight - 48))  // Limitar a pantalla
            button.layoutParams = params
            button.visibility = View.VISIBLE
        }
    }

    private fun showCatalog() {
        hideAllViews()
        currentState = BubbleState.CATALOG
        // El catálogo se centra automáticamente con gravity = CENTER
        windowManager.addView(catalogView, catalogParams)
    }

    private fun showProductsCatalog() {
        hideAllViews()
        currentState = BubbleState.PRODUCTS_CATALOG
        populateProductsCatalog()
        windowManager.addView(productsCatalogView, productsCatalogParams)
    }

    private fun collapseToBubble() {
        hideAllViews()
        currentState = BubbleState.COLLAPSED
        bubbleParams.x = lastBubbleX
        bubbleParams.y = lastBubbleY
        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun hideAllViews() {
        if (::bubbleView.isInitialized && bubbleView.isAttachedToWindow) {
            windowManager.removeView(bubbleView)
        }
        if (::fanView.isInitialized && fanView.isAttachedToWindow) {
            windowManager.removeView(fanView)
        }
        if (::catalogView.isInitialized && catalogView.isAttachedToWindow) {
            windowManager.removeView(catalogView)
        }
        if (::productsCatalogView.isInitialized && productsCatalogView.isAttachedToWindow) {
            windowManager.removeView(productsCatalogView)
        }
        if (::galleryView.isInitialized && galleryView.isAttachedToWindow) {
            windowManager.removeView(galleryView)
        }
        if (::cartView.isInitialized && cartView.isAttachedToWindow) {
            windowManager.removeView(cartView)
        }
        if (::favoritesView.isInitialized && favoritesView.isAttachedToWindow) {
            windowManager.removeView(favoritesView)
        }
    }

    private fun setupFanViewListeners() {
        fanView.findViewById<ImageView>(R.id.btn_close).setOnClickListener { 
            collapseToBubble()
        }
        fanView.findViewById<ImageView>(R.id.btn_catalog).setOnClickListener { 
            showProductsCatalog()
        }
        fanView.findViewById<ImageView>(R.id.btn_gallery).setOnClickListener { 
            val intent = Intent(this, GalleryActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            collapseToBubble()
        }
        fanView.findViewById<ImageView>(R.id.btn_cart).setOnClickListener { 
            showCart()
        }
        fanView.findViewById<ImageView>(R.id.btn_settings).setOnClickListener { 
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            
            // Ocultar completamente la burbuja
            hideAllViews()
            currentState = BubbleState.COLLAPSED
        }
        fanView.findViewById<ImageView>(R.id.btn_favorites).setOnClickListener { 
            val intent = Intent(this, FavoritesActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            collapseToBubble()
        }
    }

    private fun setupCatalogViewListeners() {
        catalogView.findViewById<ImageView>(R.id.btn_back).setOnClickListener { 
            showFan()
        }
        catalogView.findViewById<ImageView>(R.id.btn_close_catalog).setOnClickListener { 
            collapseToBubble()
        }
    }

    private fun setupProductsCatalogViewListeners() {
        productsCatalogView.findViewById<ImageView>(R.id.btn_close_products_catalog).setOnClickListener { 
            collapseToBubble()
        }
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
                        showFan()
                    } else {
                        // Actualizar posición y snap to edge
                        lastBubbleX = bubbleParams.x
                        lastBubbleY = bubbleParams.y
                        val screenWidth = resources.displayMetrics.widthPixels
                        if (bubbleParams.x > screenWidth / 2) {
                            bubbleParams.x = screenWidth - bubbleView.width
                            lastBubbleX = bubbleParams.x
                        } else {
                            bubbleParams.x = 0
                            lastBubbleX = bubbleParams.x
                        }
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val screenWidth = resources.displayMetrics.widthPixels
                    val screenHeight = resources.displayMetrics.heightPixels
                    val bubbleSize = bubbleView.width
                    
                    // Calcular nueva posición con límites
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val newY = initialY + (event.rawY - initialTouchY).toInt()
                    
                    // Aplicar límites horizontales y verticales
                    bubbleParams.x = maxOf(0, minOf(newX, screenWidth - bubbleSize))
                    bubbleParams.y = maxOf(50, minOf(newY, screenHeight - bubbleSize - 100))  // 50px arriba, 100px abajo para navegación
                    
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun populateCatalogShortcuts() {
        val rainbowColors = listOf(
            0xFFFF5252.toInt(), 0xFFFF9800.toInt(), 0xFFFFEB3B.toInt(),
            0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFF9C27B0.toInt()
        )

        // Configurar los 12 botones del catálogo
        for (i in 0 until 12) {
            val shortcutId = resources.getIdentifier("shortcut_$i", "id", packageName)
            val numberId = resources.getIdentifier("shortcut_number_$i", "id", packageName)
            
            val shortcutButton = catalogView.findViewById<FrameLayout>(shortcutId)
            val numberView = catalogView.findViewById<TextView>(numberId)
            
            if (i < shortcuts.size && shortcuts[i].isNotEmpty()) {
                val text = shortcuts[i]
                numberView.background.setTint(rainbowColors[i % rainbowColors.size])
                shortcutButton.setOnClickListener {
                    copyToClipboard(text)
                    val message = if (isAccessibilityServiceEnabled()) {
                        "Texto pegado automáticamente"
                    } else {
                        "Copiado al portapapeles - mantén presionado en WhatsApp para pegar"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    collapseToBubble()
                }
                shortcutButton.alpha = 1.0f
            } else {
                shortcutButton.alpha = 0.3f
                shortcutButton.setOnClickListener { 
                    Toast.makeText(this, "Atajo vacío", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun copyToClipboard(text: String) {
        if (isAccessibilityServiceEnabled()) {
            // Usar servicio de accesibilidad para pegado automático
            val intent = Intent(this, AutoPasteAccessibilityService::class.java)
            intent.action = AutoPasteAccessibilityService.ACTION_PASTE_TEXT
            intent.putExtra(AutoPasteAccessibilityService.EXTRA_TEXT, text)
            startService(intent)
        } else {
            // Fallback: copiar al portapapeles
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("LiveCopilot", text)
            clipboard.setPrimaryClip(clip)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) {
            0
        }

        if (accessibilityEnabled == 1) {
            val services = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return services?.contains("${packageName}/${AutoPasteAccessibilityService::class.java.name}") == true
        }
        return false
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
            "Finaliza tu compra aquí: https://pago.link/xyz",
            "¡Aprovecha esta promoción única!",
            "Producto disponible por tiempo limitado",
            "Envío inmediato a todo el país",
            "Garantía de 30 días",
            "¡Solo quedan pocas unidades!",
            "Contacta para más información"
        )
        for (i in 0 until 12) {
            val shortcut = prefs.getString("shortcut_$i", defaultShortcuts.getOrElse(i) { "" }) ?: ""
            shortcuts.add(shortcut)
        }
    }

    private fun setupGalleryViewListeners() {
        galleryView.findViewById<ImageView>(R.id.btn_close_gallery).setOnClickListener { 
            showFan()
        }
    }

    private fun setupCartViewListeners() {
        cartView.findViewById<ImageView>(R.id.btn_close_cart).setOnClickListener { 
            showFan()
        }
    }

    private fun setupFavoritesViewListeners() {
        favoritesView.findViewById<ImageView>(R.id.btn_close_favorites).setOnClickListener { 
            showFan()
        }
    }

    private fun showGallery() {
        hideAllViews()
        currentState = BubbleState.GALLERY
        windowManager.addView(galleryView, galleryParams)
    }

    private fun showCart() {
        hideAllViews()
        currentState = BubbleState.CART
        windowManager.addView(cartView, cartParams)
    }

    private fun showFavorites() {
        hideAllViews()
        currentState = BubbleState.FAVORITES
        windowManager.addView(favoritesView, favoritesParams)
    }

    private fun isAClick(startX: Float, endX: Float, startY: Float, endY: Float): Boolean {
        val differenceX = Math.abs(startX - endX)
        val differenceY = Math.abs(startY - endY)
        return differenceX <= 5 && differenceY <= 5
    }

    private fun populateProductsCatalog() {
        val products = productManager.getAllProducts()
        val gridContainer = productsCatalogView.findViewById<LinearLayout>(R.id.products_grid_container)
        val emptyMessage = productsCatalogView.findViewById<TextView>(R.id.empty_products_message)
        
        // Limpiar contenido anterior
        gridContainer.removeAllViews()
        
        if (products.isEmpty()) {
            emptyMessage.visibility = View.VISIBLE
            return
        }
        
        emptyMessage.visibility = View.GONE
        
        // Crear grid de productos (3 columnas)
        val columnsCount = 3
        val imageSize = (80 * resources.displayMetrics.density).toInt() // 80dp
        val marginSize = (8 * resources.displayMetrics.density).toInt() // 8dp
        
        var currentRow: LinearLayout? = null
        products.forEachIndexed { index, product ->
            
            // Crear nueva fila cada 3 productos
            if (index % columnsCount == 0) {
                currentRow = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, marginSize)
                    }
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                }
                gridContainer.addView(currentRow)
            }
            
            // Crear vista de producto (imagen clickeable)
            val productImageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(imageSize, imageSize).apply {
                    setMargins(marginSize, 0, marginSize, 0)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = resources.getDrawable(R.drawable.shortcut_bg, null)
                isClickable = true
                isFocusable = true
                
                // Cargar imagen del producto
                if (product.imageUri.isNotEmpty()) {
                    try {
                        val uri = ImageUtils.getImageUri(this@OverlayService, product.imageUri)
                        if (uri != null) {
                            setImageURI(uri)
                        } else {
                            setImageResource(R.drawable.ic_box)
                        }
                    } catch (e: Exception) {
                        setImageResource(R.drawable.ic_box)
                    }
                } else {
                    setImageResource(R.drawable.ic_box)
                }
                
                // Click listener para copiar y pegar producto
                setOnClickListener {
                    copyProductToClipboard(product)
                }
            }
            
            currentRow?.addView(productImageView)
        }
    }
    
    private fun copyProductToClipboard(product: Product) {
        // Crear texto completo del producto
        val productText = buildString {
            append("🛍️ ${product.name}")
            if (product.description.isNotEmpty()) {
                append("\n📝 ${product.description}")
            }
            if (product.price > 0) {
                append("\n💰 \$${String.format("%.2f", product.price)}")
            }
            if (product.link.isNotEmpty()) {
                append("\n🔗 ${product.link}")
            }
        }
        
        // Estrategia simplificada: siempre copiar texto al portapapeles y abrir WhatsApp con imagen
        copyTextToClipboard(productText)
        
        if (product.imageUri.isNotEmpty()) {
            // Abrir WhatsApp con la imagen
            openWhatsAppWithImage(product.imageUri, productText)
        } else {
            // Solo texto, usar servicio de accesibilidad si está disponible
            if (isAccessibilityServiceEnabled()) {
                val intent = Intent(this, AutoPasteAccessibilityService::class.java)
                intent.action = AutoPasteAccessibilityService.ACTION_PASTE_TEXT
                intent.putExtra(AutoPasteAccessibilityService.EXTRA_TEXT, productText)
                startService(intent)
            }
        }
        
        collapseToBubble()
    }
    
    private fun copyTextToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("LiveCopilot Product", text)
        clipboard.setPrimaryClip(clip)
    }
    
    private fun openWhatsAppWithImage(imagePath: String, text: String) {
        try {
            // Crear URI segura usando FileProvider
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                // Si no existe el archivo, solo pegar texto
                return
            }
            
            val imageUri = FileProvider.getUriForFile(
                this,
                "com.livecopilot.fileprovider",
                imageFile
            )
            
            // Intent para compartir imagen con texto
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Intentar abrir WhatsApp directamente
            val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
            var opened = false
            
            for (packageName in whatsappPackages) {
                try {
                    shareIntent.setPackage(packageName)
                    startActivity(shareIntent)
                    opened = true
                    break
                } catch (e: Exception) {
                    // Continuar con el siguiente package
                }
            }
            
            if (!opened) {
                // Si no se pudo abrir WhatsApp, usar selector
                shareIntent.setPackage(null)
                val chooser = Intent.createChooser(shareIntent, "Enviar producto por:")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(chooser)
            }
            
        } catch (e: Exception) {
            // Si falla todo, el texto ya está en portapapeles
            e.printStackTrace()
        }
    }
}
