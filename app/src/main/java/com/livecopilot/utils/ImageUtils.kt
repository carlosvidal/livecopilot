package com.livecopilot.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

object ImageUtils {
    
    private const val TAG = "ImageUtils"
    private const val IMAGES_DIR = "product_images"
    private const val MAX_IMAGE_SIZE = 1024 // Píxeles máximos para optimización
    
    /**
     * Copia una imagen desde una URI temporal al almacenamiento interno de la app
     * @param context Context de la aplicación
     * @param sourceUri URI temporal de la imagen
     * @return String con la ruta de la imagen copiada, o null si hay error
     */
    fun copyImageToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            inputStream?.use { stream ->
                // Generar nombre único para la imagen
                val fileName = "img_${UUID.randomUUID()}.jpg"
                val imagesDir = File(context.filesDir, IMAGES_DIR)
                
                // Crear directorio si no existe
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                
                val destinationFile = File(imagesDir, fileName)
                
                // Leer y optimizar la imagen
                val originalBitmap = BitmapFactory.decodeStream(stream)
                val optimizedBitmap = optimizeBitmap(originalBitmap)
                
                // Guardar imagen optimizada
                FileOutputStream(destinationFile).use { outputStream ->
                    optimizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                }
                
                // Liberar memoria
                if (originalBitmap != optimizedBitmap) {
                    originalBitmap.recycle()
                }
                optimizedBitmap.recycle()
                
                Log.d(TAG, "Imagen copiada exitosamente: ${destinationFile.absolutePath}")
                destinationFile.absolutePath
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error copiando imagen: ${e.message}")
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos: ${e.message}")
            null
        }
    }
    
    /**
     * Obtiene un File desde una ruta de imagen interna
     */
    fun getImageFile(context: Context, imagePath: String): File? {
        return try {
            val file = File(imagePath)
            if (file.exists()) file else null
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo archivo de imagen: ${e.message}")
            null
        }
    }
    
    /**
     * Obtiene una URI desde una ruta de imagen interna
     */
    fun getImageUri(context: Context, imagePath: String): Uri? {
        return getImageFile(context, imagePath)?.let { Uri.fromFile(it) }
    }
    
    /**
     * Elimina una imagen del almacenamiento interno
     */
    fun deleteImage(imagePath: String): Boolean {
        return try {
            val file = File(imagePath)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Imagen eliminada: $imagePath, éxito: $deleted")
                deleted
            } else {
                true // Si no existe, consideramos que está "eliminada"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando imagen: ${e.message}")
            false
        }
    }
    
    /**
     * Optimiza una imagen para reducir su tamaño
     */
    private fun optimizeBitmap(originalBitmap: Bitmap): Bitmap {
        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height
        
        // Si la imagen ya es pequeña, no hacer nada
        if (originalWidth <= MAX_IMAGE_SIZE && originalHeight <= MAX_IMAGE_SIZE) {
            return originalBitmap
        }
        
        // Calcular nuevo tamaño manteniendo proporción
        val scaleFactor = if (originalWidth > originalHeight) {
            MAX_IMAGE_SIZE.toFloat() / originalWidth
        } else {
            MAX_IMAGE_SIZE.toFloat() / originalHeight
        }
        
        val newWidth = (originalWidth * scaleFactor).toInt()
        val newHeight = (originalHeight * scaleFactor).toInt()
        
        return Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
    }
    
    /**
     * Limpia imágenes huérfanas (que no están siendo usadas por ningún producto)
     */
    fun cleanupUnusedImages(context: Context, usedImagePaths: List<String>) {
        try {
            val imagesDir = File(context.filesDir, IMAGES_DIR)
            if (!imagesDir.exists()) return
            
            val allImageFiles = imagesDir.listFiles() ?: return
            var deletedCount = 0
            
            for (file in allImageFiles) {
                val filePath = file.absolutePath
                if (!usedImagePaths.contains(filePath)) {
                    if (file.delete()) {
                        deletedCount++
                        Log.d(TAG, "Imagen huérfana eliminada: $filePath")
                    }
                }
            }
            
            Log.d(TAG, "Limpieza completada. Imágenes eliminadas: $deletedCount")
        } catch (e: Exception) {
            Log.e(TAG, "Error durante limpieza: ${e.message}")
        }
    }
}