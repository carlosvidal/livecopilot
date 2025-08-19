package com.livecopilot

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.livecopilot.utils.ImageUtils

class AutoPasteAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_PASTE_TEXT = "com.livecopilot.PASTE_TEXT"
        const val ACTION_PASTE_PRODUCT = "com.livecopilot.PASTE_PRODUCT"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        var instance: AutoPasteAccessibilityService? = null
    }
    
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No necesitamos manejar eventos específicos para este caso
    }

    override fun onInterrupt() {
        // Manejar interrupciones del servicio
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PASTE_TEXT -> {
                val textToPaste = intent.getStringExtra(EXTRA_TEXT)
                if (!textToPaste.isNullOrEmpty()) {
                    pasteText(textToPaste)
                }
            }
            ACTION_PASTE_PRODUCT -> {
                val textToPaste = intent.getStringExtra(EXTRA_TEXT)
                val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
                if (!textToPaste.isNullOrEmpty()) {
                    pasteProductWithImage(textToPaste, imagePath)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun pasteText(text: String) {
        try {
            // Primero copiamos el texto al portapapeles (necesario para el pegado automático)
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("LiveCopilot", text)
            clipboardManager.setPrimaryClip(clip)

            // Buscamos el campo de entrada de texto activo
            val rootNode = rootInActiveWindow ?: return
            val editTextNode = findEditTextNode(rootNode)

            if (editTextNode != null) {
                // Focalizamos el campo de texto
                editTextNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                
                // Insertamos el texto directamente
                val arguments = Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                editTextNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun findEditTextNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Buscamos un campo de entrada de texto que esté habilitado y sea editable
        if (node.isEditable && node.isEnabled && node.isFocusable) {
            return node
        }

        // Buscar en los nodos hijos
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            if (childNode != null) {
                val result = findEditTextNode(childNode)
                if (result != null) {
                    return result
                }
                childNode.recycle()
            }
        }
        return null
    }
    
    private fun pasteProductWithImage(text: String, imagePath: String?) {
        try {
            // Paso 1: Pegar el texto
            pasteText(text)
            
            // Paso 2: Si hay imagen, copiarla al portapapeles y mostrar instrucciones
            if (!imagePath.isNullOrEmpty()) {
                handler.postDelayed({
                    copyImageToClipboard(imagePath)
                }, 1500) // Esperar 1.5 segundos para que se pegue el texto primero
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun copyImageToClipboard(imagePath: String) {
        try {
            val imageUri = ImageUtils.getImageUri(this, imagePath)
            if (imageUri != null) {
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newUri(contentResolver, "Imagen del producto", imageUri)
                clipboardManager.setPrimaryClip(clip)
                
                // Mostrar notificación o toast indicando que la imagen está lista para pegar
                // (esto puede requerir permisos de notificaciones)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}