package com.livecopilot.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import androidx.exifinterface.media.ExifInterface

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
            val resolver = context.contentResolver
            val mime = resolver.getType(sourceUri) ?: "image/jpeg"
            
            // Directorio destino
            val imagesDir = File(context.filesDir, IMAGES_DIR)
            if (!imagesDir.exists()) imagesDir.mkdirs()
            
            // Decodificación con sampling para limitar tamaño y memoria
            val sampled = decodeSampledBitmap(context, sourceUri, MAX_IMAGE_SIZE)
                ?: return null
            
            // Corregir orientación EXIF si aplica
            val rotated = rotateBitmapIfRequired(context, sourceUri, sampled)
            
            // Selección de formato: preserva PNG si tiene alpha o si el MIME original es PNG
            val hasAlpha = rotated.hasAlpha()
            val usePng = hasAlpha || mime.equals("image/png", ignoreCase = true)
            val format = if (usePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val ext = if (usePng) "png" else "jpg"
            val fileName = "img_${UUID.randomUUID()}.$ext"
            val destinationFile = File(imagesDir, fileName)
            
            FileOutputStream(destinationFile).use { outputStream ->
                val quality = if (format == Bitmap.CompressFormat.JPEG) 85 else 100
                rotated.compress(format, quality, outputStream)
            }
            
            if (rotated != sampled) sampled.recycle()
            rotated.recycle()
            
            Log.d(TAG, "Imagen copiada exitosamente: ${destinationFile.absolutePath}")
            destinationFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Error copiando imagen: ${e.message}")
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado copiando imagen: ${e.message}")
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
     * Decodifica con inSampleSize para evitar OOM y limitar al tamaño máximo.
     */
    private fun decodeSampledBitmap(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        return try {
            val resolver = context.contentResolver
            // 1) Solo bounds
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            val (w, h) = bounds.outWidth to bounds.outHeight
            if (w <= 0 || h <= 0) return null

            // 2) Calcular inSampleSize como potencia de 2
            val inSample = calculateInSampleSize(w, h, maxSize, maxSize)

            val opts = BitmapFactory.Options().apply {
                inSampleSize = inSample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            Log.e(TAG, "decodeSampledBitmap error: ${e.message}")
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Rota el bitmap según EXIF si es necesario.
     */
    private fun rotateBitmapIfRequired(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val resolver = context.contentResolver
            resolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, horizontal = true)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, horizontal = false)
                    else -> bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer EXIF: ${e.message}")
            bitmap
        }
    }

    private fun rotateBitmap(src: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun flipBitmap(src: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply { preScale(if (horizontal) -1f else 1f, if (horizontal) 1f else -1f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
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