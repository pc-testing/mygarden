package com.pctesting.mygarden

import android.app.Application
import android.content.ContentValues
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Garden : Screen
    data object Scan : Screen
    data class Details(val id: String) : Screen
    data class Edit(val id: String) : Screen
    data object Settings : Screen
}

/** A photo being identified. [plant] is set once identified; [problem] once it failed. */
data class ScanState(
    val photo: File,
    val loading: Boolean = true,
    val plant: Plant? = null,
    val problem: String? = null,
    /** A stronger model is having a second look. */
    val closerLook: Boolean = false,
    /** The model wasn't sure; the name is a best guess. */
    val unsure: Boolean = false,
)

class AppVm(app: Application) : AndroidViewModel(app) {
    private val store = PlantStore(app)
    val settings = SettingsStore(app)

    var plants by mutableStateOf(store.load())
        private set
    var screen by mutableStateOf<Screen>(Screen.Garden)
    var scan by mutableStateOf<ScanState?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
    var openPicker by mutableStateOf(false)
    /** Photo shown full screen, or null. */
    var viewPhoto by mutableStateOf<String?>(null)
    var cameraFile: File? = null

    fun hasKey() = settings.apiKey.isNotBlank()

    fun beginScan(uri: Uri) {
        viewModelScope.launch {
            val photo = withContext(Dispatchers.IO) { Images.importPhoto(getApplication<Application>(), uri) }
            if (photo == null) {
                notice = "Couldn't read that photo. Please try again."
                return@launch
            }
            discardScanPhoto()
            scan = ScanState(photo)
            screen = Screen.Scan
            val key = settings.apiKey
            val model = settings.model
            val strong = settings.strongModel
            val first = withContext(Dispatchers.IO) { ClaudeApi.identify(key, model, photo, careful = false) }
            if (scan?.photo != photo) return@launch // the user moved on

            // Not identified, or identified with low confidence: ask the stronger model once.
            val doubtful = first is IdentifyResult.Unknown ||
                (first is IdentifyResult.Found && first.confidence == "low")
            var result = first
            if (doubtful && strong.isNotBlank() && strong != model) {
                scan = ScanState(photo, closerLook = true)
                val second = withContext(Dispatchers.IO) { ClaudeApi.identify(key, strong, photo, careful = true) }
                if (scan?.photo != photo) return@launch
                result = when {
                    second is IdentifyResult.Found -> second
                    first is IdentifyResult.Found -> first // keep the first guess if the second look fails
                    else -> IdentifyResult.Unknown
                }
            }
            scan = when (result) {
                is IdentifyResult.Found -> ScanState(
                    photo, false, result.plant.copy(photo = photo.absolutePath), null,
                    unsure = result.confidence == "low",
                )
                is IdentifyResult.Unknown ->
                    ScanState(photo, false, null, "We couldn't identify this plant")
                is IdentifyResult.Failed ->
                    ScanState(photo, false, null, result.message)
            }
        }
    }

    /** Saves the garden (plants and photos) to a zip file the user picked. */
    fun exportBackup(uri: Uri) {
        val list = plants
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        Backup.write(list, it)
                    } != null
                } catch (e: Exception) {
                    false
                }
            }
            notice = if (ok) {
                "Backup saved (${list.size} ${if (list.size == 1) "plant" else "plants"}). " +
                    "Keep the file somewhere safe, like Google Drive."
            } else "Couldn't save the backup. Please try again."
        }
    }

    /**
     * Saves the backup straight to Downloads/MyGarden on the phone (Android 10 and newer).
     * Files there stay when the app is uninstalled.
     */
    fun saveBackupToPhone(fileName: String) {
        val list = plants
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val cr = getApplication<Application>().contentResolver
                var uri: Uri? = null
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyGarden")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext false
                    cr.openOutputStream(uri)?.use { Backup.write(list, it) } ?: error("no stream")
                    cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    true
                } catch (e: Exception) {
                    uri?.let { runCatching { cr.delete(it, null, null) } }
                    false
                }
            }
            notice = if (ok) {
                "Backup saved on this phone in Downloads › MyGarden (${list.size} " +
                    "${if (list.size == 1) "plant" else "plants"}). After reinstalling, tap Restore and pick that file."
            } else "Couldn't save the backup. Please try again."
        }
    }

    /** Adds the plants from a backup file; plants already in the garden are kept. */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val restored = withContext(Dispatchers.IO) {
                try {
                    app.contentResolver.openInputStream(uri)?.use { Backup.read(app, it) }
                } catch (e: Exception) {
                    null
                }
            }
            if (restored == null) {
                notice = "That file isn't a My Garden backup."
                return@launch
            }
            val byId = restored.associateBy { it.id }
            val merged = plants.map { byId[it.id] ?: it } + restored.filter { r -> plants.none { it.id == r.id } }
            persist(merged)
            notice = "Restored ${restored.size} ${if (restored.size == 1) "plant" else "plants"}."
        }
    }

    /** Looks in the garden first, then at the plant currently being scanned. */
    fun find(id: String): Plant? = plants.find { it.id == id } ?: scan?.plant?.takeIf { it.id == id }

    fun isSaved(id: String) = plants.any { it.id == id }

    fun addToGarden(plant: Plant) {
        persist(listOf(plant) + plants.filter { it.id != plant.id })
        scan = null
        screen = Screen.Garden
    }

    /** Plant info shown in Hindi; remembered between launches. */
    var hindi by mutableStateOf(settings.hindi)
        private set
    var translatingId by mutableStateOf<String?>(null)
        private set

    fun chooseHindi(on: Boolean) {
        hindi = on
        settings.hindi = on
    }

    /** Fetches the Hindi text for a plant once, then keeps it with the plant. */
    fun translate(id: String) {
        val plant = find(id) ?: return
        if (plant.hindi.isNotEmpty() || translatingId != null) return
        val key = settings.apiKey
        if (key.isBlank()) {
            notice = "Add your Anthropic API key in Settings to translate."
            chooseHindi(false)
            return
        }
        val model = settings.model
        translatingId = id
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { ClaudeApi.translateToHindi(key, model, plant) }
            translatingId = null
            when (result) {
                is TranslateResult.Done -> {
                    if (isSaved(id)) {
                        persist(plants.map { if (it.id == id) it.copy(hindi = result.hindi) else it })
                    }
                    val s = scan
                    val p = s?.plant
                    if (s != null && p != null && p.id == id) scan = s.copy(plant = p.copy(hindi = result.hindi))
                }
                is TranslateResult.Failed -> {
                    notice = "Couldn't translate: ${result.message}"
                    chooseHindi(false)
                }
            }
        }
    }

    fun update(plant: Plant) {
        persist(plants.map { if (it.id == plant.id) plant else it })
    }

    fun delete(id: String) {
        plants.find { it.id == id }?.let { Images.delete(it.photo) }
        persist(plants.filter { it.id != id })
        screen = Screen.Garden
    }

    fun discardScan() {
        discardScanPhoto()
        scan = null
        screen = Screen.Garden
    }

    private fun discardScanPhoto() {
        val s = scan ?: return
        if (plants.none { it.photo == s.photo.absolutePath }) Images.delete(s.photo.absolutePath)
    }

    private fun persist(list: List<Plant>) {
        plants = list
        viewModelScope.launch(Dispatchers.IO) { store.save(list) }
    }
}

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        if (Build.VERSION.SDK_INT >= 29) window.decorView.isForceDarkAllowed = false
        setContent { App(vm) }
    }
}
