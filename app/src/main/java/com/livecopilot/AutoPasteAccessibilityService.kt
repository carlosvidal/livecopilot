package com.livecopilot

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle

class AutoPasteAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_PASTE_TEXT = "com.livecopilot.PASTE_TEXT"
        const val EXTRA_TEXT = "extra_text"
        var instance: AutoPasteAccessibilityService? = null
    }

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
        if (intent?.action == ACTION_PASTE_TEXT) {
            val textToPaste = intent.getStringExtra(EXTRA_TEXT)
            if (!textToPaste.isNullOrEmpty()) {
                pasteText(textToPaste)
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
}