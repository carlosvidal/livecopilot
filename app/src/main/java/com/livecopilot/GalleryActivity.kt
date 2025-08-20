package com.livecopilot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Color
import android.graphics.Rect
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.livecopilot.data.GalleryImage
import com.livecopilot.data.ImageManager
import com.livecopilot.utils.ImageUtils
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GalleryActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GalleryAdapter
    private lateinit var emptyView: TextView
    private lateinit var fabAddImage: FloatingActionButton
    private lateinit var imageManager: ImageManager
    private val images = mutableListOf<GalleryImage>()
    private var selectedImageUri: Uri? = null
    private var selectionMode: Boolean = false
    
    // ActivityResultLauncher para selección de imagen de galería
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            addImageToGallery(it)
        }
    }
    
    // ActivityResultLauncher para tomar foto con cámara
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && selectedImageUri != null) {
            selectedImageUri?.let { uri ->
                addImageToGallery(uri)
            }
        }
    }
    
    // ActivityResultLauncher para permisos de galería
    private val galleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        } else {
            Toast.makeText(this, "Permiso necesario para acceder a la galería", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ActivityResultLauncher para permisos de cámara
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Permiso de cámara necesario para tomar fotos", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Galería"
        // Color de encabezado igual al botón de Galería
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.gallery_primary))
        toolbar.setTitleTextColor(Color.WHITE)
        toolbar.navigationIcon?.setTint(Color.WHITE)
        toolbar.overflowIcon?.setTint(Color.WHITE)
        
        imageManager = ImageManager(this)
        setupViews()
        loadImages()
    }
    
    private fun setupViews() {
        recyclerView = findViewById(R.id.recycler_gallery)
        emptyView = findViewById(R.id.empty_gallery_view)
        fabAddImage = findViewById(R.id.fab_add_image)
        
        adapter = GalleryAdapter(images) { image ->
            shareImageFromActivity(image)
        }
        
        // Masonry con 2 columnas
        val spanCount = 2
        recyclerView.layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        val spacing = 8.dp()
        recyclerView.addItemDecoration(GridSpacingItemDecoration(spanCount, spacing, includeEdge = true))

        // Calcular ancho de columna una vez que el RecyclerView tenga tamaño
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (recyclerView.width > 0) {
                    recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val totalSpace = recyclerView.width - spacing * (spanCount + 1)
                    val columnWidth = totalSpace / spanCount
                    adapter.setColumnWidth(columnWidth)
                }
            }
        })
        
        fabAddImage.setOnClickListener {
            showImageSourceDialog()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(
            if (selectionMode) R.menu.menu_favorites_selection else R.menu.menu_favorites,
            menu
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_eliminar -> { enterSelectionMode(); true }
            R.id.action_confirm_delete -> { confirmBatchDelete(); true }
            android.R.id.home -> {
                if (selectionMode) { exitSelectionMode(); true } else super.onOptionsItemSelected(item)
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun enterSelectionMode() {
        if (!selectionMode) {
            selectionMode = true
            adapter.setSelectionMode(true)
            fabAddImage.hide()
            invalidateOptionsMenu()
        }
    }

    private fun exitSelectionMode() {
        if (selectionMode) {
            selectionMode = false
            adapter.setSelectionMode(false)
            fabAddImage.show()
            invalidateOptionsMenu()
        }
    }

    private fun confirmBatchDelete() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) { exitSelectionMode(); return }
        AlertDialog.Builder(this)
            .setTitle("Eliminar")
            .setMessage("¿Eliminar ${'$'}{ids.size} imagen(es)?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { dialog, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    ids.forEach { id -> imageManager.deleteImage(id) }
                    withContext(Dispatchers.Main) {
                        loadImages()
                        exitSelectionMode()
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }
    
    private fun loadImages() {
        val allImages = imageManager.getAllImages()
        
        if (allImages.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            adapter.updateImages(allImages)
        }
    }
    
    private fun showImageSourceDialog() {
        val options = arrayOf("📸 Tomar foto", "🖼️ Seleccionar de galería")
        
        AlertDialog.Builder(this)
            .setTitle("Agregar imagen")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun openCamera() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun launchCamera() {
        try {
            // Crear archivo temporal para la foto
            val photoFile = File(cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
            selectedImageUri = FileProvider.getUriForFile(
                this,
                "com.livecopilot.fileprovider",
                photoFile
            )
            
            selectedImageUri?.let { uri ->
                cameraLauncher.launch(uri)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al inicializar la cámara", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                imagePickerLauncher.launch("image/*")
            }
            else -> {
                galleryPermissionLauncher.launch(permission)
            }
        }
    }
    
    private fun addImageToGallery(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Copiar imagen al almacenamiento interno (IO)
                val copiedPath = ImageUtils.copyImageToInternalStorage(this@GalleryActivity, uri)

                if (copiedPath != null) {
                    val timestamp = System.currentTimeMillis()
                    val name = "Imagen_${java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()).format(timestamp)}"

                    val galleryImage = GalleryImage(
                        name = name,
                        imagePath = copiedPath,
                        description = ""
                    )

                    val added = imageManager.addImage(galleryImage)
                    withContext(Dispatchers.Main) {
                        if (added) {
                            Toast.makeText(this@GalleryActivity, "Imagen agregada a la galería", Toast.LENGTH_SHORT).show()
                            loadImages()
                        } else {
                            Toast.makeText(this@GalleryActivity, "Error al agregar imagen", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@GalleryActivity, "Error al procesar imagen", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GalleryActivity, "Error al agregar imagen", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun shareImageFromActivity(image: GalleryImage) {
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
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = Intent.createChooser(shareIntent, "Compartir imagen:")
                startActivity(chooser)
                
            } else {
                Toast.makeText(this, "Imagen no encontrada", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir imagen", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadImages() // Recargar imágenes cuando se vuelve a la actividad
    }
    
    override fun onSupportNavigateUp(): Boolean {
        return if (selectionMode) {
            exitSelectionMode(); true
        } else { finish(); true }
    }
}

// ItemDecoration y helper de dp
private class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacing: Int,
    private val includeEdge: Boolean
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        val column = position % spanCount
        if (includeEdge) {
            outRect.left = spacing - column * spacing / spanCount
            outRect.right = (column + 1) * spacing / spanCount
            if (position < spanCount) outRect.top = spacing
            outRect.bottom = spacing
        } else {
            outRect.left = column * spacing / spanCount
            outRect.right = spacing - (column + 1) * spacing / spanCount
            if (position >= spanCount) outRect.top = spacing
        }
    }
}

private fun Int.dp(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()