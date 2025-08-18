package com.livecopilot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar


class MainActivity : AppCompatActivity() {
    
    private var shouldShowBubbleOnPause = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configurar toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        setupButtons()
    }

    private fun setupButtons() {
        // Botón principal
        val startBtn = findViewById<Button>(R.id.btn_start)
        startBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 1234)
            } else {
                startOverlayService()
            }
        }
        
        // Actualizar estado inicial del botón
        updateButtonState()

        // Botón Catálogo
        findViewById<FrameLayout>(R.id.btn_catalog).setOnClickListener {
            shouldShowBubbleOnPause = false // No mostrar burbuja al navegar internamente
            val intent = Intent(this, CatalogActivity::class.java)
            startActivity(intent)
        }

        // Botón Galería
        findViewById<FrameLayout>(R.id.btn_gallery).setOnClickListener {
            val intent = Intent(this, GalleryActivity::class.java)
            startActivity(intent)
        }

        // Botón Favoritos
        findViewById<FrameLayout>(R.id.btn_favorites).setOnClickListener {
            Toast.makeText(this, "Favoritos - Próximamente", Toast.LENGTH_SHORT).show()
        }

        // Botón Settings
        findViewById<FrameLayout>(R.id.btn_settings).setOnClickListener {
            shouldShowBubbleOnPause = false // No mostrar burbuja al navegar internamente
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1234) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                Toast.makeText(this, "Permiso de superposición requerido. Por favor, habilita 'Mostrar sobre otras apps' en la configuración.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Verificar permisos cuando la actividad vuelve al primer plano
        updateButtonState()
        // Restaurar flag para permitir mostrar burbuja
        shouldShowBubbleOnPause = true
    }
    
    override fun onPause() {
        super.onPause()
        // Solo mostrar burbuja si se debe (no al navegar a otras pantallas internas)
        if (shouldShowBubbleOnPause && isOverlayServiceRunning()) {
            val intent = Intent(this, OverlayService::class.java)
            intent.action = OverlayService.ACTION_SHOW_BUBBLE
            startService(intent)
        }
    }
    
    private fun isOverlayServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (OverlayService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    private fun updateButtonState() {
        val startBtn = findViewById<Button>(R.id.btn_start)
        if (Settings.canDrawOverlays(this)) {
            startBtn.text = "Iniciar LiveCopilot"
            startBtn.isEnabled = true
        } else {
            startBtn.text = "Otorgar permisos"
            startBtn.isEnabled = true
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        startForegroundService(intent)
        Toast.makeText(this, "LiveCopilot iniciado", Toast.LENGTH_SHORT).show()
        
        // Mostrar explícitamente la burbuja al iniciar el servicio
        val showIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_BUBBLE
        }
        startService(showIntent)

        // Minimizar la ventana principal
        moveTaskToBack(true)
    }
}
