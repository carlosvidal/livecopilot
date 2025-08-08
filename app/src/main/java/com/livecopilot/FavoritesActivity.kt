package com.livecopilot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class FavoritesActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)
        
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Favoritos"
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}