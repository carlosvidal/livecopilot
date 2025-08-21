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
import android.net.Uri
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import android.util.Log
import com.livecopilot.data.Product
import com.livecopilot.data.ProductManager
import com.livecopilot.data.CartItem
import com.livecopilot.data.repository.CartRepository
import com.livecopilot.data.GalleryImage
import com.livecopilot.data.repository.GalleryImageRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import com.livecopilot.utils.ImageUtils
import com.livecopilot.data.repository.FavoriteGenericRepository
import com.livecopilot.data.Favorite
import com.livecopilot.data.FavoriteType
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import java.io.File
import com.livecopilot.util.CurrencyUtils

class OverlayService : Service() {
    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_SHOW_BUBBLE = "SHOW_BUBBLE"
    }
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var fanView: View
    private lateinit var catalogView: View
    private lateinit var productsCatalogView: View
    private lateinit var shareDialogView: View
    private lateinit var galleryView: View
    private lateinit var cartView: View
    private lateinit var favoritesView: View
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var fanParams: WindowManager.LayoutParams
    private lateinit var catalogParams: WindowManager.LayoutParams
    private lateinit var productsCatalogParams: WindowManager.LayoutParams
    private lateinit var shareDialogParams: WindowManager.LayoutParams
    private lateinit var galleryParams: WindowManager.LayoutParams
    private lateinit var cartParams: WindowManager.LayoutParams
    private lateinit var favoritesParams: WindowManager.LayoutParams

    private var currentState = BubbleState.COLLAPSED
    
    enum class BubbleState {
        COLLAPSED, FAN, CATALOG, PRODUCTS_CATALOG, SHARE_DIALOG, GALLERY, CART, FAVORITES
    }

    private fun migrateLegacyCartIfNeeded() {
        val prefs = getSharedPreferences("livecopilot_cart", Context.MODE_PRIVATE)
        val migratedKey = "migrated_to_room"
        if (prefs.getBoolean(migratedKey, false)) return

        try {
            val json = prefs.getString("cart_items", "[]")
            val type = object : TypeToken<List<CartItem>>() {}.type
            val items: List<CartItem> = try { Gson().fromJson(json, type) ?: emptyList() } catch (e: Exception) { emptyList() }
            Log.d(TAG, "migrateLegacyCartIfNeeded: found ${items.size} items")
            if (items.isNotEmpty()) {
                serviceScope.launch {
                    items.forEach { item ->
                        cartRepository.upsert(item.product, item.quantity)
                    }
                    // Clear legacy data and mark migrated
                    prefs.edit().remove("cart_items").putBoolean(migratedKey, true).apply()
                    Log.d(TAG, "migrateLegacyCartIfNeeded: migrated ${items.size} items")
                }
            } else {
                prefs.edit().putBoolean(migratedKey, true).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "migrateLegacyCartIfNeeded failed", e)
            // If migration fails, do not mark as migrated to retry next startup
        }
    }
    
    private var selectedProduct: Product? = null

    // Posición persistente de la burbuja
    private var lastBubbleX = 0   // Pegado al borde izquierdo
    private var lastBubbleY = 300 // Más abajo para dar espacio al abanico

    private val shortcuts = mutableListOf<String>()
    private lateinit var productManager: ProductManager
    private lateinit var cartRepository: CartRepository
    private lateinit var cartAdapter: CartAdapter
    private lateinit var galleryModalAdapter: GalleryOverlayAdapter
    private lateinit var galleryRepository: GalleryImageRepository
    private val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var galleryJob: Job? = null
    private var latestGalleryUi: List<GalleryImage> = emptyList()
    private var cartJob: Job? = null
    private var latestCartUi: List<CartItem> = emptyList()
    private lateinit var favoriteRepository: FavoriteGenericRepository
    private var favoritesJob: Job? = null
    private var latestFavoritesUi: List<Favorite> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        initCriticalViews() // Solo burbuja y abanico inicialmente
        
        // Inicializar ProductManager, CartManager y otras vistas en segundo plano
        Thread {
            productManager = ProductManager(this)
            cartRepository = CartRepository(this)
            galleryRepository = GalleryImageRepository(this)
            favoriteRepository = FavoriteGenericRepository(this)
            // One-time migration from legacy SharedPreferences cart to Room
            migrateLegacyCartIfNeeded()
            initSecondaryViews() // Modales y diálogos después
        }.start()
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
        if (::shareDialogView.isInitialized && shareDialogView.isAttachedToWindow) {
            windowManager.removeView(shareDialogView)
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
        galleryJob?.cancel()
        cartJob?.cancel()
        favoritesJob?.cancel()
        serviceScope.cancel()
    }

    private fun startInForeground() {
        val channelId = "livecopilot_overlay"
        val channelName = getString(R.string.notif_channel_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title_active))
            .setContentText(getString(R.string.notif_text_running))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    private fun initCriticalViews() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        // --- Inicializar y configurar Bubble View (CRÍTICO) ---
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

        // --- Inicializar y configurar Fan View (CRÍTICO) ---
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
    }
    
    private fun initSecondaryViews() {
        runOnUiThread {
            try {
                val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

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
                productsCatalogParams = createCenteredModalParams().apply {
                    // 80% del ancho de la pantalla
                    width = (resources.displayMetrics.widthPixels * 0.8f).toInt()
                    // Oscurecer fondo detrás del modal
                    flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    dimAmount = 0.4f
                }
                setupProductsCatalogViewListeners()

                // --- Inicializar Share Dialog ---
                shareDialogView = inflater.inflate(R.layout.product_share_dialog, null)
                shareDialogParams = createCenteredModalParams()
                setupShareDialogListeners()

                // --- Inicializar Gallery Modal ---
                galleryView = inflater.inflate(R.layout.gallery_modal, null)
                galleryParams = createCenteredModalParams().apply {
                    // 80% del ancho de la pantalla + oscurecer fondo
                    width = (resources.displayMetrics.widthPixels * 0.8f).toInt()
                    flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    dimAmount = 0.4f
                }
                setupGalleryViewListeners()
                // Comenzar a observar la galería desde Room
                startObservingGallery()

                // --- Inicializar Cart Modal ---
                cartView = inflater.inflate(R.layout.cart_modal, null)
                cartParams = createCenteredModalParams().apply {
                    // 80% del ancho de la pantalla + oscurecer fondo
                    width = (resources.displayMetrics.widthPixels * 0.8f).toInt()
                    flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    dimAmount = 0.4f
                }
                setupCartViewListeners()
                // Observar carrito desde Room
                startObservingCart()

                // --- Inicializar Favorites Modal ---
                favoritesView = inflater.inflate(R.layout.favorites_modal, null)
                favoritesParams = createCenteredModalParams().apply {
                    // 80% del ancho de la pantalla + oscurecer fondo
                    width = (resources.displayMetrics.widthPixels * 0.8f).toInt()
                    flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    dimAmount = 0.4f
                }
                setupFavoritesViewListeners()
                // Comenzar a observar favoritos desde Room
                startObservingFavorites()

                loadShortcutsFromPrefs()
                populateCatalogShortcuts()
            } catch (e: Exception) {
                Toast.makeText(this, "Error inicializando modales: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun runOnUiThread(action: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
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
        // Asegurar estado
        currentState = BubbleState.COLLAPSED
        try {
            if (bubbleView.isAttachedToWindow) {
                // Ya agregado: solo actualizar posición/params
                windowManager.updateViewLayout(bubbleView, bubbleParams)
                return
            }
            windowManager.addView(bubbleView, bubbleParams)
        } catch (e: IllegalStateException) {
            // Si ya fue agregado por carrera de eventos, actualiza en vez de agregar
            try {
                windowManager.updateViewLayout(bubbleView, bubbleParams)
            } catch (_: Exception) { /* no-op */ }
        }
    }

    private fun showFan() {
        hideAllViews()
        currentState = BubbleState.FAN
        // El fan ahora es fullscreen (agregar de forma segura)
        try {
            if (fanView.isAttachedToWindow) {
                windowManager.updateViewLayout(fanView, fanParams)
            } else {
                windowManager.addView(fanView, fanParams)
            }
        } catch (e: IllegalStateException) {
            try {
                windowManager.updateViewLayout(fanView, fanParams)
            } catch (_: Exception) { /* no-op */ }
        }

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
        try {
            if (catalogView.isAttachedToWindow) {
                windowManager.updateViewLayout(catalogView, catalogParams)
            } else {
                windowManager.addView(catalogView, catalogParams)
            }
        } catch (e: IllegalStateException) {
            try {
                windowManager.updateViewLayout(catalogView, catalogParams)
            } catch (_: Exception) { /* no-op */ }
        }
    }

    private fun showProductsCatalog() {
        // Verificar que las vistas estén inicializadas
        if (!::productsCatalogView.isInitialized) {
            Toast.makeText(this, "Cargando catálogo...", Toast.LENGTH_SHORT).show()
            return
        }
        
        hideAllViews()
        currentState = BubbleState.PRODUCTS_CATALOG
        populateProductsCatalog()
        try {
            if (productsCatalogView.isAttachedToWindow) {
                windowManager.updateViewLayout(productsCatalogView, productsCatalogParams)
            } else {
                windowManager.addView(productsCatalogView, productsCatalogParams)
            }
        } catch (e: IllegalStateException) {
            try {
                windowManager.updateViewLayout(productsCatalogView, productsCatalogParams)
            } catch (_: Exception) { /* no-op */ }
        }
    }

    private fun collapseToBubble() {
        hideAllViews()
        currentState = BubbleState.COLLAPSED
        bubbleParams.x = lastBubbleX
        bubbleParams.y = lastBubbleY
        try {
            if (::bubbleView.isInitialized) {
                if (bubbleView.isAttachedToWindow) {
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                } else {
                    windowManager.addView(bubbleView, bubbleParams)
                }
            }
        } catch (e: IllegalStateException) {
            try {
                windowManager.updateViewLayout(bubbleView, bubbleParams)
            } catch (_: Exception) { /* no-op */ }
        }
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
        if (::shareDialogView.isInitialized && shareDialogView.isAttachedToWindow) {
            windowManager.removeView(shareDialogView)
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
            showGallery()
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
            showFavorites()
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

    private fun setupShareDialogListeners() {
        shareDialogView.findViewById<Button>(R.id.btn_share_intent).setOnClickListener {
            selectedProduct?.let { product ->
                shareProductWithIntent(product)
                collapseToBubble()
            }
        }
        
        shareDialogView.findViewById<Button>(R.id.btn_copy_text).setOnClickListener {
            selectedProduct?.let { product ->
                copyProductTextOnly(product)
                collapseToBubble()
            }
        }
        
        shareDialogView.findViewById<Button>(R.id.btn_copy_image).setOnClickListener {
            selectedProduct?.let { product ->
                copyProductImageOnly(product)
                collapseToBubble()
            }
        }
        
        shareDialogView.findViewById<Button>(R.id.btn_add_to_cart).setOnClickListener {
            selectedProduct?.let { product ->
                addProductToCart(product)
            }
        }
        
        shareDialogView.findViewById<Button>(R.id.btn_cancel_dialog).setOnClickListener {
            hideShareDialog()
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
            collapseToBubble()
        }
        
        // Configurar RecyclerView del modal de galería (masonry 3 columnas)
        val recyclerView = galleryView.findViewById<RecyclerView>(R.id.gallery_modal_recycler)
        recyclerView.layoutManager = StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)
        
        galleryModalAdapter = GalleryOverlayAdapter(mutableListOf()) { image ->
            shareImageFromGallery(image)
            collapseToBubble()
        }
        recyclerView.adapter = galleryModalAdapter
        
        // Actualizar galería cuando se muestre
        updateGalleryModalDisplay()
    }

    private fun setupCartViewListeners() {
        cartView.findViewById<ImageView>(R.id.btn_close_cart).setOnClickListener { 
            collapseToBubble()
        }
        
        cartView.findViewById<Button>(R.id.btn_clear_cart).setOnClickListener {
            clearCart()
        }
        
        cartView.findViewById<Button>(R.id.btn_share_cart).setOnClickListener {
            shareCart()
            collapseToBubble()
        }
        
        // Configurar RecyclerView del carrito
        val recyclerView = cartView.findViewById<RecyclerView>(R.id.cart_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        cartAdapter = CartAdapter(mutableListOf()) { cartItem, newQuantity ->
            updateCartItemQuantity(cartItem, newQuantity)
        }
        recyclerView.adapter = cartAdapter
        
        // Actualizar carrito cuando se muestre
        updateCartDisplay()

        // Comenzar a observar cambios del carrito
        startObservingCart()
    }

    private fun setupFavoritesViewListeners() {
        favoritesView.findViewById<ImageView>(R.id.btn_close_favorites).setOnClickListener { 
            collapseToBubble()
        }
        // RecyclerView de favoritos
        val recyclerView = favoritesView.findViewById<RecyclerView>(R.id.favorites_recycler_overlay)
        recyclerView?.layoutManager = LinearLayoutManager(this)
        val adapter = FavoritesAdapter(mutableListOf(), { fav -> onOverlayFavoriteClick(fav) }, { _ ->
            // En overlay, para editar/eliminar abre la actividad completa
            val intent = Intent(this, FavoritesActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            collapseToBubble()
        })
        recyclerView?.adapter = adapter
        updateFavoritesOverlayDisplay()
    }

    private fun updateFavoritesOverlayDisplay() {
        val recyclerView = favoritesView.findViewById<RecyclerView>(R.id.favorites_recycler_overlay) ?: return
        val adapter = recyclerView.adapter as? FavoritesAdapter ?: return
        val list = latestFavoritesUi
        adapter.setData(list)
        val empty = favoritesView.findViewById<TextView>(R.id.empty_favorites_overlay)
        empty?.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun startObservingFavorites() {
        favoritesJob?.cancel()
        favoritesJob = serviceScope.launch {
            favoriteRepository.observeAll().collectLatest { entities ->
                latestFavoritesUi = entities.mapNotNull { e ->
                    val type = runCatching { FavoriteType.valueOf(e.type) }.getOrNull() ?: return@mapNotNull null
                    Favorite(
                        id = e.id,
                        type = type,
                        name = e.name,
                        content = e.content
                    )
                }
                updateFavoritesOverlayDisplay()
            }
        }
    }

    private fun onOverlayFavoriteClick(fav: Favorite) {
        when (fav.type) {
            FavoriteType.LINK -> {
                // Copiar y notificar, y permitir abrir
                copyToClipboard(fav.content)
                Toast.makeText(this, getString(R.string.toast_link_copied), Toast.LENGTH_SHORT).show()
            }
            FavoriteType.TEXT -> {
                copyToClipboard(fav.content)
                Toast.makeText(this, getString(R.string.toast_text_copied), Toast.LENGTH_SHORT).show()
            }
            FavoriteType.PDF -> openOrShareOverlay("application/pdf", fav.content)
            FavoriteType.IMAGE -> openOrShareOverlay("image/*", fav.content)
        }
        collapseToBubble()
    }

    private fun openOrShareOverlay(mime: String, content: String) {
        try {
            val uri = Uri.parse(content)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(viewIntent)
        } catch (_: Exception) {
            try {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(content))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(Intent.createChooser(send, getString(R.string.chooser_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.toast_open_share_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showGallery() {
        hideAllViews()
        currentState = BubbleState.GALLERY
        updateGalleryModalDisplay() // Actualizar galería al mostrarlo
        try {
            if (galleryView.isAttachedToWindow) {
                windowManager.updateViewLayout(galleryView, galleryParams)
            } else {
                windowManager.addView(galleryView, galleryParams)
            }
        } catch (e: IllegalStateException) {
            try {
                windowManager.updateViewLayout(galleryView, galleryParams)
            } catch (_: Exception) { /* no-op */ }
        }
    }

    private fun showCart() {
        hideAllViews()
        currentState = BubbleState.CART
        updateCartDisplay() // Actualizar carrito al mostrarlo
        try {
            if (cartView.isAttachedToWindow) {
                windowManager.updateViewLayout(cartView, cartParams)
            } else {
                windowManager.addView(cartView, cartParams)
            }
        } catch (e: IllegalStateException) {
            try {
                windowManager.updateViewLayout(cartView, cartParams)
            } catch (_: Exception) { /* no-op */ }
        }
    }

    private fun showFavorites() {
        hideAllViews()
        currentState = BubbleState.FAVORITES
        updateFavoritesOverlayDisplay()
        try {
            if (favoritesView.isAttachedToWindow) {
                windowManager.updateViewLayout(favoritesView, favoritesParams)
            } else {
                windowManager.addView(favoritesView, favoritesParams)
            }
        } catch (e: IllegalStateException) {
            try {
                windowManager.updateViewLayout(favoritesView, favoritesParams)
            } catch (_: Exception) { /* no-op */ }
        }
    }

    private fun updateCartDisplay() {
        if (!::cartAdapter.isInitialized) return
        runOnUiThread {
            val items = latestCartUi
            cartAdapter.updateItems(items)

            // Actualizar total
            val total = items.sumOf { it.product.price * it.quantity }
            val totalText = cartView.findViewById<TextView>(R.id.cart_total)
            totalText.text = CurrencyUtils.formatAmount(this, total)

            // Mostrar/ocultar mensaje de carrito vacío
            val emptyMessage = cartView.findViewById<TextView>(R.id.empty_cart_message)
            val recyclerView = cartView.findViewById<RecyclerView>(R.id.cart_recycler_view)
            if (items.isEmpty()) {
                emptyMessage.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyMessage.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun startObservingCart() {
        cartJob?.cancel()
        cartJob = serviceScope.launch {
            cartRepository.observeAll().collectLatest { entities ->
                Log.d(TAG, "Cart observeAll emitted ${entities.size} items")
                latestCartUi = entities.map { e ->
                    val product = Product(
                        id = e.productId,
                        name = e.name,
                        price = e.price,
                        link = e.link,
                        imageUri = e.imageUri
                    )
                    CartItem(product = product, quantity = e.quantity)
                }
                updateCartDisplay()
            }
        }
    }

    private fun addProductToCart(product: Product) {
        serviceScope.launch {
            // Calcular cantidad nueva basado en estado actual
            val current = try { cartRepository.getAllOnce().firstOrNull { it.productId == product.id } } catch (_: Exception) { null }
            val newQty = (current?.quantity ?: 0) + 1
            Log.d(TAG, "addProductToCart: ${product.id} newQty=$newQty")
            cartRepository.upsert(product, newQty)
            runOnUiThread {
                Toast.makeText(this@OverlayService, getString(R.string.toast_product_added), Toast.LENGTH_SHORT).show()
            }
        }
        hideShareDialog()
    }

    private fun updateCartItemQuantity(cartItem: CartItem, newQuantity: Int) {
        val pid = cartItem.product.id
        serviceScope.launch {
            Log.d(TAG, "updateCartItemQuantity: $pid -> $newQuantity")
            cartRepository.updateQuantity(pid, newQuantity)
        }
    }

    private fun clearCart() {
        serviceScope.launch {
            Log.d(TAG, "clearCart invoked")
            cartRepository.clear()
            runOnUiThread {
                Toast.makeText(this@OverlayService, getString(R.string.toast_cart_emptied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareCart() {
        val items = latestCartUi
        if (items.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_cart_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.append(getString(R.string.cart_text_title)).append("\n\n")
        Log.d(TAG, "shareCart items=${items.size}")
        items.forEach { item ->
            sb.append("• ").append(item.product.name)
                .append(" x").append(item.quantity)
                .append(" = ")
                .append(CurrencyUtils.formatAmount(this, item.totalPrice))
                .append("\n")
        }
        val total = items.sumOf { it.totalPrice }
        sb.append("\n").append("Total: ")
            .append(CurrencyUtils.formatAmount(this, total))

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            val chooser = Intent.createChooser(shareIntent, getString(R.string.chooser_share_cart))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(chooser)
        } catch (_: Exception) {
            copyTextToClipboard(sb.toString())
            Toast.makeText(this, getString(R.string.toast_product_copied), Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAClick(startX: Float, endX: Float, startY: Float, endY: Float): Boolean {
        val differenceX = Math.abs(startX - endX)
        val differenceY = Math.abs(startY - endY)
        return differenceX <= 5 && differenceY <= 5
    }

    private fun populateProductsCatalog() {
        if (!::productManager.isInitialized) {
            return
        }
        
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
        
        // Crear grid de productos (2 columnas) con nombre y precio
        val columnsCount = 2
        val marginSize = (8 * resources.displayMetrics.density).toInt() // 8dp
        // Calcular ancho disponible del modal (80% de pantalla ya aplicado en params)
        val modalWidth = productsCatalogParams.width
        // Restar padding del ScrollView que contiene al grid
        val productsScroll = productsCatalogView.findViewById<android.widget.ScrollView>(R.id.products_scroll)
        val horizontalPadding = (productsScroll?.paddingLeft ?: 0) + (productsScroll?.paddingRight ?: 0)
        val availableWidth = modalWidth - horizontalPadding
        // Ancho aproximado de cada columna considerando márgenes laterales del item
        val itemWidth = ((availableWidth - (marginSize * 4)) / columnsCount)
        
        var currentRow: LinearLayout? = null
        products.forEachIndexed { index, product ->
            // Crear nueva fila cada 2 productos
            if (index % columnsCount == 0) {
                currentRow = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, marginSize)
                    }
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.START
                }
                gridContainer.addView(currentRow)
            }

            // Contenedor del item (imagen + textos)
            val itemContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(itemWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(marginSize, 0, marginSize, 0)
                }
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(0, 0, 0, marginSize)
            }

            // Wrapper con borde 1px y esquinas redondeadas
            val radiusPx = (8 * resources.displayMetrics.density)
            val imageWrapper = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    itemWidth
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = radiusPx
                    setColor(Color.WHITE)
                    setStroke(2, Color.parseColor("#DDDDDD"))
                }
                // Inset interno igual al grosor del borde para que no se tape
                setPadding(2, 2, 2, 2)
                clipToOutline = true
            }

            // Imagen del producto
            val productImageView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                // Sin background ni padding; el borde vive en el wrapper
                setBackgroundColor(Color.TRANSPARENT)
            }

            // Cargar imagen
            if (product.imageUri.isNotEmpty()) {
                try {
                    val uri = ImageUtils.getImageUri(this@OverlayService, product.imageUri)
                    if (uri != null) {
                        productImageView.setImageURI(uri)
                    } else {
                        productImageView.setImageResource(R.drawable.ic_box)
                    }
                } catch (e: Exception) {
                    productImageView.setImageResource(R.drawable.ic_box)
                }
            } else {
                productImageView.setImageResource(R.drawable.ic_box)
            }

            // Nombre del producto
            val nameView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, marginSize / 2, 0, 0)
                }
                text = product.name
                setTextColor(resources.getColor(android.R.color.black, null))
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.START
            }

            // Precio del producto
            val priceText = if (product.price > 0) {
                CurrencyUtils.formatAmount(this, product.price)
            } else {
                getString(R.string.price_not_specified)
            }
            val priceView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, marginSize / 4, 0, 0)
                }
                text = priceText
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
                textSize = 11f
                gravity = android.view.Gravity.START
            }

            // Click en todo el item
            itemContainer.setOnClickListener { showShareDialog(product) }

            // Armar vista
            imageWrapper.addView(productImageView)
            itemContainer.addView(imageWrapper)
            itemContainer.addView(nameView)
            itemContainer.addView(priceView)
            currentRow?.addView(itemContainer)
        }

        // Ajustar altura del modal dinámicamente
        val rows = ((products.size + (columnsCount - 1)) / columnsCount)
        val maxRowsNoScroll = 4
        val density = resources.displayMetrics.density
        val textExtraPx = (34 * density).toInt() // espacio estimado para nombre y precio + márgenes
        val rowHeight = itemWidth + textExtraPx + marginSize // alto de una fila

        // Medir header si es posible
        val headerView = productsCatalogView.findViewById<LinearLayout>(R.id.products_header)
        if (headerView.measuredHeight == 0) {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(productsCatalogParams.width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            headerView.measure(widthSpec, heightSpec)
        }
        val headerHeight = if (headerView.measuredHeight > 0) headerView.measuredHeight else (48 * density).toInt()
        val verticalPadding = productsCatalogView.paddingTop + productsCatalogView.paddingBottom

        val desiredRows = rows.coerceAtMost(maxRowsNoScroll)
        val contentHeightNoScroll = desiredRows * rowHeight
        val totalDesiredHeight = headerHeight + contentHeightNoScroll + verticalPadding + (16 * density).toInt() // margen inferior del header

        val screenHeight = resources.displayMetrics.heightPixels
        val maxModalHeight = (screenHeight * 0.9f).toInt()

        // Configurar scroll y alturas
        val scrollView = productsCatalogView.findViewById<android.widget.ScrollView>(R.id.products_scroll)
        if (rows <= maxRowsNoScroll) {
            // Sin scroll para 8 o menos productos
            scrollView.isVerticalScrollBarEnabled = false
            scrollView.overScrollMode = View.OVER_SCROLL_NEVER
            scrollView.layoutParams = scrollView.layoutParams.apply {
                height = LinearLayout.LayoutParams.WRAP_CONTENT
            }
            productsCatalogParams.height = totalDesiredHeight.coerceAtMost(maxModalHeight)
        } else {
            // Con scroll para 9 o más
            scrollView.isVerticalScrollBarEnabled = true
            productsCatalogParams.height = maxModalHeight
        }
    }
    
    private fun showShareDialog(product: Product) {
        selectedProduct = product
        hideAllViews()
        currentState = BubbleState.SHARE_DIALOG
        
        // Llenar información del producto en el diálogo
        populateShareDialog(product)
        
        windowManager.addView(shareDialogView, shareDialogParams)
    }
    
    private fun hideShareDialog() {
        if (::shareDialogView.isInitialized && shareDialogView.isAttachedToWindow) {
            windowManager.removeView(shareDialogView)
        }
        selectedProduct = null
        showProductsCatalog()
    }
    
    private fun populateShareDialog(product: Product) {
        // Llenar imagen
        val dialogImage = shareDialogView.findViewById<ImageView>(R.id.dialog_product_image)
        if (product.imageUri.isNotEmpty()) {
            try {
                val uri = ImageUtils.getImageUri(this, product.imageUri)
                if (uri != null) {
                    dialogImage.setImageURI(uri)
                    dialogImage.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    dialogImage.setImageResource(R.drawable.ic_box)
                    dialogImage.scaleType = ImageView.ScaleType.CENTER
                }
            } catch (e: Exception) {
                dialogImage.setImageResource(R.drawable.ic_box)
                dialogImage.scaleType = ImageView.ScaleType.CENTER
            }
        } else {
            dialogImage.setImageResource(R.drawable.ic_box)
            dialogImage.scaleType = ImageView.ScaleType.CENTER
        }
        
        // Llenar textos
        shareDialogView.findViewById<TextView>(R.id.dialog_product_name).text = product.name
        shareDialogView.findViewById<TextView>(R.id.dialog_product_price).text = 
            if (product.price > 0) CurrencyUtils.formatAmount(this, product.price) else getString(R.string.price_not_specified)
    }
    
    private fun shareProductWithIntent(product: Product) {
        // Crear texto completo del producto
        val productText = buildString {
            append("🛍️ ${product.name}")
            if (product.description.isNotEmpty()) {
                append("\n📝 ${product.description}")
            }
            if (product.price > 0) {
                append("\n💰 ${CurrencyUtils.formatAmount(this@OverlayService, product.price)}")
            }
            if (product.link.isNotEmpty()) {
                append("\n🔗 ${product.link}")
            }
        }
        
        try {
            val shareIntent = Intent(Intent.ACTION_SEND)
            
            // Si tiene imagen, compartir imagen con texto
            if (product.imageUri.isNotEmpty()) {
                val imageFile = File(product.imageUri)
                if (imageFile.exists()) {
                    val imageUri = FileProvider.getUriForFile(
                        this,
                        "com.livecopilot.fileprovider",
                        imageFile
                    )
                    shareIntent.apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        putExtra(Intent.EXTRA_TEXT, productText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    // Si no existe la imagen, solo texto
                    shareIntent.apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, productText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            } else {
                // Solo texto
                shareIntent.apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, productText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            
            val chooser = Intent.createChooser(shareIntent, getString(R.string.chooser_share))
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(chooser)
            
        } catch (e: Exception) {
            // Fallback: copiar al portapapeles
            copyTextToClipboard(productText)
            Toast.makeText(this, getString(R.string.toast_product_copied), Toast.LENGTH_SHORT).show()
        }
        
        collapseToBubble()
    }
    
    private fun copyProductToClipboard(product: Product) {
        // Crear texto completo del producto
        val productText = buildString {
            append("🛍️ ${product.name}")
            if (product.description.isNotEmpty()) {
                append("\n📝 ${product.description}")
            }
            if (product.price > 0) {
                append("\n💰 ${CurrencyUtils.formatAmount(this@OverlayService, product.price)}")
            }
            if (product.link.isNotEmpty()) {
                append("\n🔗 ${product.link}")
            }
        }
        
        // Copiar texto al portapapeles
        if (product.imageUri.isNotEmpty()) {
            // Copiar texto e imagen al portapapeles
            copyTextAndImageToClipboard(productText, product.imageUri)
        } else {
            // Solo texto
            copyTextToClipboard(productText)
        }
        
        // Usar servicio de accesibilidad si está disponible para pegar automáticamente
        if (isAccessibilityServiceEnabled()) {
            val intent = Intent(this, AutoPasteAccessibilityService::class.java)
            intent.action = AutoPasteAccessibilityService.ACTION_PASTE_TEXT
            intent.putExtra(AutoPasteAccessibilityService.EXTRA_TEXT, productText)
            startService(intent)
        }
        
        // Mostrar instrucciones sobre la imagen
        val message = if (product.imageUri.isNotEmpty()) {
            "Texto copiado. Para la imagen: mantén presionado en el campo → Pegar imagen"
        } else {
            "Producto copiado al portapapeles"
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        collapseToBubble()
    }
    
    private fun copyProductTextOnly(product: Product) {
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
        
        // Solo copiar texto al portapapeles
        copyTextToClipboard(productText)
        
        // Usar servicio de accesibilidad si está disponible para pegar automáticamente
        if (isAccessibilityServiceEnabled()) {
            val intent = Intent(this, AutoPasteAccessibilityService::class.java)
            intent.action = AutoPasteAccessibilityService.ACTION_PASTE_TEXT
            intent.putExtra(AutoPasteAccessibilityService.EXTRA_TEXT, productText)
            startService(intent)
            Toast.makeText(this, "Texto pegado automáticamente", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Texto copiado al portapapeles", Toast.LENGTH_SHORT).show()
        }
        
        collapseToBubble()
    }
    
    private fun copyProductImageOnly(product: Product) {
        if (product.imageUri.isEmpty()) {
            Toast.makeText(this, "Este producto no tiene imagen", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val imageFile = File(product.imageUri)
            if (imageFile.exists()) {
                val imageUri = FileProvider.getUriForFile(
                    this,
                    "com.livecopilot.fileprovider",
                    imageFile
                )
                
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newUri(contentResolver, "Imagen del producto", imageUri)
                clipboard.setPrimaryClip(clip)
                
                Toast.makeText(this, "Imagen copiada - mantén presionado en el campo → Pegar", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Imagen no encontrada", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al copiar imagen", Toast.LENGTH_SHORT).show()
        }
        
        collapseToBubble()
    }
    
    private fun copyTextToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("LiveCopilot Product", text)
        clipboard.setPrimaryClip(clip)
    }
    
    private fun copyTextAndImageToClipboard(text: String, imagePath: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        try {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                val imageUri = FileProvider.getUriForFile(
                    this,
                    "com.livecopilot.fileprovider",
                    imageFile
                )
                
                // Crear ClipData con texto e imagen
                val clip = ClipData(
                    "LiveCopilot Producto",
                    arrayOf("text/plain", "image/*"),
                    ClipData.Item(text)
                )
                clip.addItem(ClipData.Item(imageUri))
                
                clipboard.setPrimaryClip(clip)
            } else {
                // Si no existe la imagen, solo copiar texto
                copyTextToClipboard(text)
            }
        } catch (e: Exception) {
            // Si falla, al menos copiar el texto
            copyTextToClipboard(text)
        }
    }

    // Gallery modal functionality methods
    private fun updateGalleryModalDisplay() {
        if (::galleryModalAdapter.isInitialized && ::galleryView.isInitialized) {
            runOnUiThread {
                val images = latestGalleryUi
                galleryModalAdapter.updateImages(images)

                // Mostrar/ocultar mensaje de galería vacía
                val emptyMessage = galleryView.findViewById<TextView>(R.id.empty_gallery_modal_message)
                val recyclerView = galleryView.findViewById<RecyclerView>(R.id.gallery_modal_recycler)

                if (images.isEmpty()) {
                    emptyMessage.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyMessage.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun startObservingGallery() {
        galleryJob?.cancel()
        galleryJob = serviceScope.launch {
            galleryRepository.observeAll().collectLatest { entities ->
                latestGalleryUi = entities.map { e ->
                    com.livecopilot.data.GalleryImage(
                        id = e.id,
                        name = e.name,
                        imagePath = e.imagePath,
                        description = e.description,
                        dateAdded = e.dateAdded
                    )
                }
                updateGalleryModalDisplay()
            }
        }
    }
    
    private fun shareImageFromGallery(image: GalleryImage) {
        try {
            val imageFile = File(image.imagePath)
            if (imageFile.exists()) {
                val imageUri = FileProvider.getUriForFile(
                    this,
                    "com.livecopilot.fileprovider",
                    imageFile
                )
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    if (image.description.isNotEmpty()) {
                        putExtra(Intent.EXTRA_TEXT, image.description)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = Intent.createChooser(shareIntent, getString(R.string.chooser_share_image))
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(chooser)
                
            } else {
                Toast.makeText(this, getString(R.string.toast_image_not_found), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_error_share_image), Toast.LENGTH_SHORT).show()
        }
        
        collapseToBubble()
    }
}
