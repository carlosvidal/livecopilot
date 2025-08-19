package com.livecopilot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputFilter
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.livecopilot.data.Product
import com.livecopilot.data.ProductManager
import com.livecopilot.utils.ImageUtils
import java.io.File
import java.text.DecimalFormat

class AddProductActivity : AppCompatActivity() {
    
    private lateinit var editName: EditText
    private lateinit var editDescription: EditText
    private lateinit var editPrice: EditText
    private lateinit var editLink: EditText
    private lateinit var btnSave: Button
    private lateinit var btnSelectImage: Button
    private lateinit var btnBack: ImageView
    private lateinit var toolbarTitle: TextView
    private lateinit var imagePreview: ImageView
    private lateinit var productManager: ProductManager
    
    private var editingProductId: String? = null
    private var editingProduct: Product? = null
    private var selectedImageUri: Uri? = null
    private var copiedImagePath: String? = null // Ruta de la imagen copiada al almacenamiento interno
    
    // ActivityResultLauncher para selección de imagen de galería
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            copyImageToInternalStorage()
        }
    }
    
    // ActivityResultLauncher para tomar foto con cámara
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && selectedImageUri != null) {
            copyImageToInternalStorage()
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
        setContentView(R.layout.activity_add_product)
        
        productManager = ProductManager(this)
        
        // Verificar si estamos editando un producto existente
        editingProductId = intent.getStringExtra("product_id")
        editingProduct = editingProductId?.let { productManager.getProduct(it) }
        
        setupViews()
        loadProductData()
    }
    
    private fun setupViews() {
        editName = findViewById(R.id.edit_product_name)
        editDescription = findViewById(R.id.edit_product_description)
        editPrice = findViewById(R.id.edit_product_price)
        editLink = findViewById(R.id.edit_product_link)
        btnSave = findViewById(R.id.btn_save_product)
        btnSelectImage = findViewById(R.id.btn_select_image)
        btnBack = findViewById(R.id.btn_back)
        toolbarTitle = findViewById(R.id.toolbar_title)
        imagePreview = findViewById(R.id.product_image_preview)
        
        // Configurar título según modo
        toolbarTitle.text = if (editingProduct != null) "Editar Producto" else "Nuevo Producto"
        
        // Configurar campo de precio para solo números con 2 decimales
        editPrice.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        
        btnSave.setOnClickListener {
            saveProduct()
        }
        
        btnSelectImage.setOnClickListener {
            checkPermissionAndOpenPicker()
        }
        
        btnBack.setOnClickListener {
            finish()
        }
    }
    
    private fun loadProductData() {
        editingProduct?.let { product ->
            editName.setText(product.name)
            editDescription.setText(product.description)
            editPrice.setText(DecimalFormat("#.##").format(product.price))
            editLink.setText(product.link)
            
            // Cargar imagen si existe
            if (product.imageUri.isNotEmpty()) {
                copiedImagePath = product.imageUri
                loadImagePreview()
            }
        }
    }
    
    private fun checkPermissionAndOpenPicker() {
        showImageSourceDialog()
    }
    
    private fun showImageSourceDialog() {
        val options = arrayOf("📸 Tomar foto", "🖼️ Seleccionar de galería")
        
        AlertDialog.Builder(this)
            .setTitle("Seleccionar imagen")
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
    
    private fun copyImageToInternalStorage() {
        selectedImageUri?.let { uri ->
            // Mostrar progreso
            btnSelectImage.isEnabled = false
            btnSelectImage.text = "Procesando..."
            
            // Copiar imagen en hilo de fondo
            Thread {
                val imagePath = ImageUtils.copyImageToInternalStorage(this, uri)
                
                runOnUiThread {
                    btnSelectImage.isEnabled = true
                    btnSelectImage.text = "Seleccionar Imagen"
                    
                    if (imagePath != null) {
                        copiedImagePath = imagePath
                        loadImagePreview()
                        Toast.makeText(this, "Imagen seleccionada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error al procesar la imagen", Toast.LENGTH_SHORT).show()
                        selectedImageUri = null
                        copiedImagePath = null
                    }
                }
            }.start()
        }
    }
    
    private fun loadImagePreview() {
        copiedImagePath?.let { path ->
            try {
                val uri = ImageUtils.getImageUri(this, path)
                if (uri != null) {
                    imagePreview.setImageURI(uri)
                    imagePreview.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    throw Exception("No se pudo cargar la imagen")
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
                copiedImagePath = null
                imagePreview.setImageResource(R.drawable.ic_box)
                imagePreview.scaleType = ImageView.ScaleType.CENTER
            }
        } ?: run {
            // Sin imagen, mostrar placeholder
            imagePreview.setImageResource(R.drawable.ic_box)
            imagePreview.scaleType = ImageView.ScaleType.CENTER
        }
    }
    
    private fun saveProduct() {
        val name = editName.text.toString().trim()
        val description = editDescription.text.toString().trim()
        val priceText = editPrice.text.toString().trim()
        val link = editLink.text.toString().trim()
        
        if (name.isEmpty()) {
            editName.error = "El nombre es requerido"
            editName.requestFocus()
            return
        }
        
        val price = try {
            if (priceText.isEmpty()) 0.0 else priceText.toDouble()
        } catch (e: NumberFormatException) {
            editPrice.error = "Precio inválido"
            editPrice.requestFocus()
            return
        }
        
        if (price < 0) {
            editPrice.error = "El precio no puede ser negativo"
            editPrice.requestFocus()
            return
        }
        
        val product = Product(
            id = editingProduct?.id ?: "",
            name = name,
            description = description,
            price = price,
            link = link,
            imageUri = copiedImagePath ?: editingProduct?.imageUri ?: ""
        )
        
        val success = if (editingProduct != null) {
            // Editar producto existente
            productManager.updateProduct(product)
        } else {
            // Crear nuevo producto
            when (val result = productManager.addProduct(product)) {
                ProductManager.AddProductResult.SUCCESS -> true
                ProductManager.AddProductResult.LIMIT_REACHED -> {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Límite alcanzado")
                        .setMessage("Has alcanzado el límite de 24 productos en el plan gratuito.\n\n¿Te gustaría actualizar a Premium?")
                        .setPositiveButton("Más tarde") { dialog, _ -> dialog.dismiss() }
                        .setNegativeButton("Ver Premium") { dialog, _ -> 
                            dialog.dismiss()
                            // TODO: Abrir pantalla premium
                        }
                        .show()
                    false
                }
            }
        }
        
        if (success) {
            val message = if (editingProduct != null) "Producto actualizado" else "Producto guardado"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
}