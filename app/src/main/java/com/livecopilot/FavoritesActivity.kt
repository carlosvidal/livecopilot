package com.livecopilot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.livecopilot.data.Favorite
import com.livecopilot.data.FavoriteType
import com.livecopilot.data.FavoritesManager

class FavoritesActivity : AppCompatActivity() {

    private lateinit var favoritesManager: FavoritesManager
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: FavoritesAdapter
    private var contentInputRef: EditText? = null
    private val pickDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            // Persistir permiso de lectura
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        contentInputRef?.setText(uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Favoritos"

        favoritesManager = FavoritesManager(this)

        recycler = findViewById(R.id.favorites_recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = FavoritesAdapter(mutableListOf(), this::onFavoriteClick, this::onFavoriteLongClick)
        recycler.adapter = adapter

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_add_favorite)
            .setOnClickListener { showAddFavoriteDialog() }

        loadFavorites()
    }

    private fun loadFavorites() {
        adapter.setData(favoritesManager.getAll())
    }

    private fun showAddFavoriteDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_favorite, null)
        val typeSpinner = view.findViewById<Spinner>(R.id.input_type)
        val nameInput = view.findViewById<EditText>(R.id.input_name)
        val contentInput = view.findViewById<EditText>(R.id.input_content)
        val pickButton = view.findViewById<Button>(R.id.btn_pick_file)
        contentInputRef = contentInput

        fun updatePickerVisibility(position: Int) {
            // 1 = PDF, 2 = Imagen según arrays.xml mapping en spinnerSelectionToType
            pickButton.visibility = if (position == 1 || position == 2) Button.VISIBLE else Button.GONE
        }

        updatePickerVisibility(typeSpinner.selectedItemPosition)
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updatePickerVisibility(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        pickButton.setOnClickListener {
            val idx = typeSpinner.selectedItemPosition
            val mime = when (idx) {
                1 -> "application/pdf" // PDF
                2 -> "image/*"        // Imagen
                else -> "*/*"
            }
            try {
                pickDocument.launch(arrayOf(mime))
            } catch (_: Exception) {
                Toast.makeText(this, "No se pudo abrir el selector", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Nuevo favorito")
            .setView(view)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                val type = spinnerSelectionToType(typeSpinner.selectedItemPosition)
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val content = contentInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty() || content.isEmpty()) {
                    Toast.makeText(this, "Completa nombre y contenido", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                favoritesManager.add(type, name, content)
                loadFavorites()
            }
            .show()
        // Evitar fugas de referencia
        (view.parent as? AlertDialog)?.setOnDismissListener { contentInputRef = null }
    }

    private fun spinnerSelectionToType(index: Int): FavoriteType = when (index) {
        0 -> FavoriteType.LINK
        1 -> FavoriteType.PDF
        2 -> FavoriteType.IMAGE
        else -> FavoriteType.TEXT
    }

    private fun onFavoriteClick(fav: Favorite) {
        when (fav.type) {
            FavoriteType.LINK -> openLink(fav.content)
            FavoriteType.PDF -> openOrShare("application/pdf", fav.content)
            FavoriteType.IMAGE -> openOrShare("image/*", fav.content)
            FavoriteType.TEXT -> shareText(fav.content)
        }
    }

    private fun onFavoriteLongClick(fav: Favorite) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar favorito")
            .setMessage("¿Eliminar '${fav.name}'?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                favoritesManager.delete(fav.id)
                loadFavorites()
            }
            .show()
    }

    private fun openLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOrShare(mime: String, content: String) {
        try {
            val uri = Uri.parse(content)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(viewIntent, "Abrir con"))
        } catch (_: Exception) {
            // Fallback: compartir
            try {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(content))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, "Compartir"))
            } catch (_: Exception) {
                Toast.makeText(this, "No se pudo abrir/compartir", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareText(text: String) {
        // Copiar al portapapeles y ofrecer compartir
        val clip = ClipData.newPlainText("favorito", text)
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(clip)
        Toast.makeText(this, "Texto copiado", Toast.LENGTH_SHORT).show()

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Compartir texto"))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}