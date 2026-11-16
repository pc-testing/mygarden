package com.pctesting.mygarden

import android.app.Application
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
            val result = withContext(Dispatchers.IO) { ClaudeApi.identify(key, model, photo) }
            if (scan?.photo != photo) return@launch // the user moved on
            scan = when (result) {
                is IdentifyResult.Found ->
                    ScanState(photo, false, result.plant.copy(photo = photo.absolutePath), null)
                is IdentifyResult.Unknown ->
                    ScanState(photo, false, null, "We couldn't identify this plant")
                is IdentifyResult.Failed ->
                    ScanState(photo, false, null, result.message)
            }
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

    fun setHindi(on: Boolean) {
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
            setHindi(false)
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
                    setHindi(false)
                }
            }
        }
    }

    fun update(plant: Plant) {
        persist(plants.map { if (it.id == plant.id) plant else it })
    }

    fun delete(id: String) {
        plants.find { it.id == id }?.let { File(it.photo).delete() }
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
        if (plants.none { it.photo == s.photo.absolutePath }) s.photo.delete()
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
