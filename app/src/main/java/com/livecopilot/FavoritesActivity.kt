package com.livecopilot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.livecopilot.data.Favorite
import com.livecopilot.data.FavoriteType
import com.livecopilot.data.FavoritesManager
import android.util.Patterns
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FavoritesActivity : AppCompatActivity() {

    private lateinit var favoritesManager: FavoritesManager
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: FavoritesAdapter
    private var selectionMode: Boolean = false
    private lateinit var fab: FloatingActionButton
    private var contentInputRef: EditText? = null
    private val pickDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            // Persistir permiso de lectura
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        contentInputRef?.setText(uri.toString())
    }

    private fun showLimitDialogFavorites() {
        AlertDialog.Builder(this)
            .setTitle("Límite alcanzado")
            .setMessage("Has alcanzado el límite de 24 favoritos en el plan Free.\n\nActiva Pro en Preferencias para agregar ilimitados.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Favoritos"
        // Color de encabezado igual al botón de Favoritos
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.favorites_primary))
        toolbar.setTitleTextColor(Color.WHITE)
        toolbar.navigationIcon?.setTint(Color.WHITE)
        toolbar.overflowIcon?.setTint(Color.WHITE)

        favoritesManager = FavoritesManager(this)

        recycler = findViewById(R.id.favorites_recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = FavoritesAdapter(mutableListOf(), this::onFavoriteClick, this::onFavoriteLongClick)
        recycler.adapter = adapter

        fab = findViewById(R.id.fab_add_favorite)
        fab.setOnClickListener {
            if (!favoritesManager.canAddMore()) {
                showLimitDialogFavorites()
            } else {
                showAddFavoriteDialog()
            }
        }

        loadFavorites()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(
            if (selectionMode) R.menu.menu_favorites_selection else R.menu.menu_favorites,
            menu
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_eliminar -> {
                enterSelectionMode()
                true
            }
            R.id.action_confirm_delete -> {
                confirmBatchDelete()
                true
            }
            android.R.id.home -> {
                if (selectionMode) {
                    exitSelectionMode()
                    true
                } else super.onOptionsItemSelected(item)
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun enterSelectionMode() {
        if (!selectionMode) {
            selectionMode = true
            adapter.setSelectionMode(true)
            fab.hide()
            invalidateOptionsMenu()
        }
    }

    private fun exitSelectionMode() {
        if (selectionMode) {
            selectionMode = false
            adapter.setSelectionMode(false)
            fab.show()
            invalidateOptionsMenu()
        }
    }

    private fun confirmBatchDelete() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) {
            exitSelectionMode()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Eliminar")
            .setMessage("¿Eliminar ${'$'}{ids.size} favorito(s)?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                ids.forEach { id -> favoritesManager.delete(id) }
                loadFavorites()
                exitSelectionMode()
            }
            .show()
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

        val dialog = AlertDialog.Builder(this)
            .setTitle("Nuevo favorito")
            .setView(view)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()

        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                if (!favoritesManager.canAddMore()) {
                    showLimitDialogFavorites()
                    return@setOnClickListener
                }
                nameInput.error = null
                contentInput.error = null

                val typeIndex = typeSpinner.selectedItemPosition
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val content = contentInput.text?.toString()?.trim().orEmpty()

                val errorMsg = validateFavoriteInputs(typeIndex, name, content, excludeId = null)
                if (errorMsg != null) {
                    // Marcar errores específicos
                    if (name.isEmpty()) nameInput.error = "Requerido"
                    if (content.isEmpty()) contentInput.error = "Requerido"
                    else contentInput.error = errorMsg
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val type = spinnerSelectionToType(typeIndex)
                favoritesManager.add(type, name, content)
                loadFavorites()
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener { contentInputRef = null }
        dialog.show()
    }

    private fun spinnerSelectionToType(index: Int): FavoriteType = when (index) {
        0 -> FavoriteType.LINK
        1 -> FavoriteType.PDF
        2 -> FavoriteType.IMAGE
        else -> FavoriteType.TEXT
    }

    private fun onFavoriteClick(fav: Favorite) {
        // En la pantalla de Favoritos (no el modal), un tap debe abrir el formulario de edición
        showEditFavoriteDialog(fav)
    }

    private fun onFavoriteLongClick(fav: Favorite) {
        val options = arrayOf("Editar", "Eliminar")
        AlertDialog.Builder(this)
            .setTitle(fav.name)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showEditFavoriteDialog(fav)
                    1 -> confirmDeleteFavorite(fav)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDeleteFavorite(fav: Favorite) {
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

    private fun showEditFavoriteDialog(fav: Favorite) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_favorite, null)
        val typeSpinner = view.findViewById<Spinner>(R.id.input_type)
        val nameInput = view.findViewById<EditText>(R.id.input_name)
        val contentInput = view.findViewById<EditText>(R.id.input_content)
        val pickButton = view.findViewById<Button>(R.id.btn_pick_file)
        contentInputRef = contentInput

        // Prefill
        nameInput.setText(fav.name)
        contentInput.setText(fav.content)
        typeSpinner.setSelection(
            when (fav.type) {
                FavoriteType.LINK -> 0
                FavoriteType.PDF -> 1
                FavoriteType.IMAGE -> 2
                FavoriteType.TEXT -> 3
            }
        )

        fun updatePickerVisibility(position: Int) {
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
                1 -> "application/pdf"
                2 -> "image/*"
                else -> "*/*"
            }
            try {
                pickDocument.launch(arrayOf(mime))
            } catch (_: Exception) {
                Toast.makeText(this, "No se pudo abrir el selector", Toast.LENGTH_SHORT).show()
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Editar favorito")
            .setView(view)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                nameInput.error = null
                contentInput.error = null

                val typeIndex = typeSpinner.selectedItemPosition
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val content = contentInput.text?.toString()?.trim().orEmpty()

                val errorMsg = validateFavoriteInputs(typeIndex, name, content, excludeId = fav.id)
                if (errorMsg != null) {
                    if (name.isEmpty()) nameInput.error = "Requerido"
                    if (content.isEmpty()) contentInput.error = "Requerido"
                    else contentInput.error = errorMsg
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val type = spinnerSelectionToType(typeIndex)
                favoritesManager.update(fav.copy(type = type, name = name, content = content))
                loadFavorites()
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener { contentInputRef = null }
        dialog.show()
    }

    private fun validateFavoriteInputs(typeIndex: Int, name: String, content: String, excludeId: String? = null): String? {
        if (name.isBlank() || content.isBlank()) return "Completa nombre y contenido"

        // Duplicados por nombre (case-insensitive)
        val duplicated = favoritesManager.getAll().any { it.name.equals(name, ignoreCase = true) && it.id != excludeId }
        if (duplicated) return "Ya existe un favorito con ese nombre"

        return when (typeIndex) {
            0 -> { // LINK
                val isWeb = Patterns.WEB_URL.matcher(content).matches()
                val uri = runCatching { Uri.parse(content) }.getOrNull()
                val schemeOk = uri?.scheme == "http" || uri?.scheme == "https"
                if (!isWeb || !schemeOk) "Ingresa un enlace válido (http/https)" else null
            }
            1, // PDF
            2 -> { // IMAGE
                val uri = runCatching { Uri.parse(content) }.getOrNull()
                val scheme = uri?.scheme
                val allowed = scheme == "content" || scheme == "file" || scheme == "http" || scheme == "https"
                if (!allowed) {
                    "Ingresa una URI válida (content://, file:// o http/https)"
                } else {
                    // Validación MIME para content:// cuando sea posible
                    if (scheme == "content" && uri != null) {
                        val mime = runCatching { contentResolver.getType(uri) }.getOrNull()
                        if (mime != null) {
                            val ok = when (typeIndex) {
                                1 -> mime == "application/pdf"
                                2 -> mime.startsWith("image/")
                                else -> true
                            }
                            if (!ok) return "El contenido no coincide con el tipo seleccionado"
                        }
                    }
                    null
                }
            }
            else -> null // TEXT
        }
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
        return if (selectionMode) {
            exitSelectionMode()
            true
        } else {
            finish()
            true
        }
    }
}