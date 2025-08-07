package com.livecopilot

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

        // Botón Catálogo
        findViewById<FrameLayout>(R.id.btn_catalog).setOnClickListener {
            Toast.makeText(this, "Catálogo - Funcionalidad disponible en la burbuja flotante", Toast.LENGTH_SHORT).show()
        }

        // Botón Galería
        findViewById<FrameLayout>(R.id.btn_gallery).setOnClickListener {
            Toast.makeText(this, "Galería - Próximamente", Toast.LENGTH_SHORT).show()
        }

        // Botón Favoritos
        findViewById<FrameLayout>(R.id.btn_favorites).setOnClickListener {
            Toast.makeText(this, "Favoritos - Próximamente", Toast.LENGTH_SHORT).show()
        }

        // Botón Settings
        findViewById<FrameLayout>(R.id.btn_settings).setOnClickListener {
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
                Toast.makeText(this, "Permiso de superposición requerido", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        startForegroundService(intent)
        Toast.makeText(this, "LiveCopilot iniciado", Toast.LENGTH_SHORT).show()
    }
}
