package com.livecopilot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.livecopilot.data.GalleryImage
import com.livecopilot.data.ImageManager
import com.livecopilot.utils.ImageUtils
import java.io.File

class GalleryActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GalleryAdapter
    private lateinit var emptyView: TextView
    private lateinit var fabAddImage: FloatingActionButton
    private lateinit var imageManager: ImageManager
    private val images = mutableListOf<GalleryImage>()
    private var selectedImageUri: Uri? = null
    
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
        
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Galería"
        
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
        
        // Usar StaggeredGridLayoutManager para layout masonry
        recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter
        
        fabAddImage.setOnClickListener {
            showImageSourceDialog()
        }
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
        try {
            // Copiar imagen al almacenamiento interno
            val copiedPath = ImageUtils.copyImageToInternalStorage(this, uri)
            
            if (copiedPath != null) {
                // Generar nombre automático basado en timestamp
                val timestamp = System.currentTimeMillis()
                val name = "Imagen_${java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()).format(timestamp)}"
                
                val galleryImage = GalleryImage(
                    name = name,
                    imagePath = copiedPath,
                    description = ""
                )
                
                if (imageManager.addImage(galleryImage)) {
                    Toast.makeText(this, "Imagen agregada a la galería", Toast.LENGTH_SHORT).show()
                    loadImages() // Recargar la lista
                } else {
                    Toast.makeText(this, "Error al agregar imagen", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Error al procesar imagen", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al agregar imagen", Toast.LENGTH_SHORT).show()
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
        finish()
        return true
    }
}