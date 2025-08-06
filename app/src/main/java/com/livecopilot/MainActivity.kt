package com.livecopilot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
