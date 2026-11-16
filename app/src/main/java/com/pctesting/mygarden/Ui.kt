@file:OptIn(ExperimentalMaterial3Api::class)

package com.pctesting.mygarden

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val Green = Color(0xFF1F6B3A)
private val Bg = Color(0xFFF3F5F0)
private val Ink = Color(0xFF17201A)
private val Muted = Color(0xFF4D5B51)
private val Line = Color(0xFFE1E6DE)
private val PhotoBg = Color(0xFFCFE3D2)

private val AppColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFDCEBDD),
    onSecondaryContainer = Ink,
    background = Bg,
    onBackground = Ink,
    surface = Bg,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF1EB),
    onSurfaceVariant = Muted,
    outline = Color(0xFFC5CCC2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEEF1EB),
)

@Composable
fun App(vm: AppVm) {
    MaterialTheme(colorScheme = AppColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                when (val s = vm.screen) {
                    is Screen.Garden -> GardenScreen(vm)
                    is Screen.Scan -> ScanScreen(vm)
                    is Screen.Details -> DetailsScreen(vm, s.id)
                    is Screen.Edit -> EditScreen(vm, s.id)
                    is Screen.Settings -> SettingsScreen(vm)
                }
            }
        }
        BackHandler(enabled = vm.screen != Screen.Garden) { goBack(vm) }
        vm.notice?.let { message ->
            AlertDialog(
                onDismissRequest = { vm.notice = null },
                confirmButton = { TextButton(onClick = { vm.notice = null }) { Text("OK") } },
                text = { Text(message) },
            )
        }
    }
}

private fun goBack(vm: AppVm) {
    when (val s = vm.screen) {
        is Screen.Garden -> Unit
        is Screen.Scan -> vm.discardScan()
        is Screen.Details -> vm.screen = if (vm.isSaved(s.id)) Screen.Garden else Screen.Scan
        is Screen.Edit -> vm.screen = Screen.Details(s.id)
        is Screen.Settings -> vm.screen = Screen.Garden
    }
}

// ---------- Shared pieces ----------

@Composable
private fun PlantPhoto(path: String, maxSide: Int, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, maxSide) {
        value = withContext(Dispatchers.IO) {
            if (path.isBlank()) null else Images.load(path, maxSide)?.asImageBitmap()
        }
    }
    Box(modifier = modifier.background(PhotoBg)) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun StatCard(label: String, title: String, note: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            Text(title.ifBlank { "Not set" }, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (note.isNotBlank()) Text(note, fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
private fun InfoCard(label: String, title: String, note: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
            Text(title.ifBlank { "Not set" }, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (note.isNotBlank()) Text(note, fontSize = 14.sp, lineHeight = 20.sp, color = Muted)
        }
    }
}

// ---------- Garden ----------

@Composable
private fun GardenScreen(vm: AppVm) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = vm.cameraFile
        if (ok && f != null) vm.beginScan(Uri.fromFile(f))
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.beginScan(uri)
    }

    if (vm.openPicker) {
        AlertDialog(
            onDismissRequest = { vm.openPicker = false },
            title = { Text("Scan a plant") },
            text = { Text("Use a clear photo of the whole plant or a leaf.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.openPicker = false
                    try {
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val f = File(dir, "capture.jpg")
                        vm.cameraFile = f
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
                        takePicture.launch(uri)
                    } catch (e: Exception) {
                        vm.notice = "Couldn't open a camera app on this phone."
                    }
                }) { Text("Take photo") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.openPicker = false
                    try {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } catch (e: Exception) {
                        vm.notice = "Couldn't open the gallery on this phone."
                    }
                }) { Text("Choose from gallery") }
            },
        )
    }

    val shown = vm.plants.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) ||
            it.scientific.contains(query, ignoreCase = true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val n = vm.plants.size
                    Text(
                        if (n == 1) "1 plant" else "$n plants",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Muted,
                    )
                    Text("My garden", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                }
                IconButton(onClick = { vm.screen = Screen.Settings }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
            if (vm.plants.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search your plants") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (vm.plants.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No plants yet", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scan a plant to find out what it is and how to care for it.",
                        color = Muted, textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { it.id }) { plant ->
                        PlantRow(plant) { vm.screen = Screen.Details(plant.id) }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                if (vm.hasKey()) vm.openPicker = true
                else {
                    vm.notice = "Add your Anthropic API key first, then scan a plant."
                    vm.screen = Screen.Settings
                }
            },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Scan a plant", fontWeight = FontWeight.Bold) },
            containerColor = Green,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

@Composable
private fun PlantRow(plant: Plant, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Line),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlantPhoto(plant.photo, 256, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(plant.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (plant.scientific.isNotBlank()) {
                    Text(plant.scientific, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Muted)
                }
                Text(careLine(plant), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Green)
            }
        }
    }
}

private fun careLine(plant: Plant): String =
    listOf(waterLabel(plant.waterDays), plant.sunlight).filter { it.isNotBlank() }.joinToString(" · ")

// ---------- Scan: identifying, result, or manual entry ----------

@Composable
private fun ScanScreen(vm: AppVm) {
    val s = vm.scan
    if (s == null) {
        LaunchedEffect(Unit) { vm.screen = Screen.Garden }
        return
    }
    val plant = s.plant
    when {
        s.loading -> Column(modifier = Modifier.fillMaxSize()) {
            PlantPhoto(s.photo.absolutePath, 1024, Modifier.fillMaxWidth().height(280.dp))
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(20.dp))
                Text("Identifying your plant…", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.discardScan() }) { Text("Cancel") }
            }
        }

        plant != null -> ResultView(
            plant = plant,
            onBack = { vm.discardScan() },
            onMore = { vm.screen = Screen.Details(plant.id) },
            onAdd = { vm.addToGarden(plant) },
            onSkip = { vm.discardScan() },
        )

        else -> {
            val draft = remember(s.photo) { Plant(photo = s.photo.absolutePath) }
            PlantForm(
                title = "Add plant details",
                initial = draft,
                banner = s.problem ?: "We couldn't identify this plant",
                primaryLabel = "Save to garden",
                secondaryLabel = "Retake photo",
                onBack = { vm.discardScan() },
                onPrimary = { vm.addToGarden(it) },
                onSecondary = {
                    vm.discardScan()
                    vm.openPicker = true
                },
            )
        }
    }
}

@Composable
private fun ResultView(
    plant: Plant,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onAdd: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
            PlantPhoto(plant.photo, 1024, Modifier.fillMaxSize())
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.padding(12.dp).size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text("IDENTIFIED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Green)
                Text(plant.name, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                if (plant.scientific.isNotBlank()) {
                    Text(plant.scientific, fontSize = 15.sp, fontStyle = FontStyle.Italic, color = Muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Sunlight", plant.sunlight, plant.sunlightNote, Modifier.weight(1f))
                StatCard("Watering", waterShort(plant.waterDays), plant.waterNote, Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = onMore,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Green),
            ) {
                Text("More info", fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            Text(
                "Fertiliser, blooming season and propagation",
                fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            border = BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("Add this plant to your garden?", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Skip it if this plant isn't yours.", fontSize = 14.sp, color = Muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Not mine", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Add to garden", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ---------- Details (More info) ----------

@Composable
private fun DetailsScreen(vm: AppVm, id: String) {
    val plant = vm.find(id)
    if (plant == null) {
        LaunchedEffect(Unit) { vm.screen = Screen.Garden }
        return
    }
    val saved = vm.isSaved(id)
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${plant.name}?") },
            text = { Text("This removes the plant and its photo from your garden.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(id)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("Plant details", onBack = { goBack(vm) }) {
            if (saved) {
                IconButton(onClick = { vm.screen = Screen.Edit(id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit details")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove plant")
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlantPhoto(plant.photo, 512, Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(plant.name, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    if (plant.scientific.isNotBlank()) {
                        Text(plant.scientific, fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Muted)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Sunlight", plant.sunlight, plant.sunlightNote, Modifier.weight(1f))
                StatCard("Watering", waterShort(plant.waterDays), plant.waterNote, Modifier.weight(1f))
            }
            InfoCard("Fertiliser", plant.fertiliser, plant.fertiliserNote)
            if (plant.flowering) InfoCard("Blooming", plant.blooming, plant.bloomingNote)
            else InfoCard("Blooming", "Not a flowering plant", "")
            InfoCard("Propagation", plant.propagation, plant.propagationNote)
            Text(
                if (saved) "Care info may be AI-generated. Tap the pencil to correct anything."
                else "Care info is AI-generated. You can correct it after adding the plant.",
                fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        if (!saved) {
            Button(
                onClick = { vm.addToGarden(plant) },
                modifier = Modifier.fillMaxWidth().padding(20.dp).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Add to garden", fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------- Edit / manual entry ----------

@Composable
private fun EditScreen(vm: AppVm, id: String) {
    val plant = vm.plants.find { it.id == id }
    if (plant == null) {
        LaunchedEffect(Unit) { vm.screen = Screen.Garden }
        return
    }
    PlantForm(
        title = "Edit plant",
        initial = plant,
        banner = null,
        primaryLabel = "Save",
        secondaryLabel = "Cancel",
        onBack = { vm.screen = Screen.Details(id) },
        onPrimary = {
            vm.update(it)
            vm.screen = Screen.Details(id)
        },
        onSecondary = { vm.screen = Screen.Details(id) },
    )
}

@Composable
private fun PlantForm(
    title: String,
    initial: Plant,
    banner: String?,
    primaryLabel: String,
    secondaryLabel: String,
    onBack: () -> Unit,
    onPrimary: (Plant) -> Unit,
    onSecondary: () -> Unit,
) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var sunlight by rememberSaveable(initial.id) { mutableStateOf(initial.sunlight) }
    var water by rememberSaveable(initial.id) {
        mutableStateOf(if (initial.waterDays > 0) initial.waterDays.toString() else "")
    }
    var fertiliser by rememberSaveable(initial.id) { mutableStateOf(initial.fertiliser) }
    var blooming by rememberSaveable(initial.id) { mutableStateOf(initial.blooming) }
    var propagation by rememberSaveable(initial.id) { mutableStateOf(initial.propagation) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title, onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (banner != null) {
                Surface(
                    color = Color(0xFFFFF1D6),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE5C98A)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlantPhoto(initial.photo, 256, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(banner, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4A3200))
                            Text(
                                "Retake the photo, or fill in what you know.",
                                fontSize = 13.sp, color = Color(0xFF4A3200),
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Plant name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Sunlight", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
            SUNLIGHT_OPTIONS.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { option ->
                        FilterChip(
                            selected = sunlight == option,
                            onClick = { sunlight = if (sunlight == option) "" else option },
                            label = { Text(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = water,
                onValueChange = { v -> water = v.filter { it.isDigit() }.take(3) },
                label = { Text("Water every (days)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = fertiliser, onValueChange = { fertiliser = it },
                label = { Text("Fertiliser (optional)") },
                placeholder = { Text("e.g. compost once a month") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = blooming, onValueChange = { blooming = it },
                label = { Text("Blooming season (optional)") },
                placeholder = { Text("e.g. summer, or leave blank") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = propagation, onValueChange = { propagation = it },
                label = { Text("Propagation (optional)") },
                placeholder = { Text("e.g. seeds, cuttings") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Green),
            ) { Text(secondaryLabel, fontWeight = FontWeight.Bold) }
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val days = water.toIntOrNull() ?: 0
                    // A note written for the old value is dropped when that value is changed.
                    onPrimary(
                        initial.copy(
                            name = name.trim(),
                            sunlight = sunlight,
                            sunlightNote = if (sunlight == initial.sunlight) initial.sunlightNote else "",
                            waterDays = days,
                            waterNote = if (days == initial.waterDays) initial.waterNote else "",
                            fertiliser = fertiliser.trim(),
                            fertiliserNote = if (fertiliser.trim() == initial.fertiliser) initial.fertiliserNote else "",
                            flowering = if (blooming.isNotBlank()) true else initial.flowering,
                            blooming = blooming.trim(),
                            bloomingNote = if (blooming.trim() == initial.blooming) initial.bloomingNote else "",
                            propagation = propagation.trim(),
                            propagationNote = if (propagation.trim() == initial.propagation) initial.propagationNote else "",
                        )
                    )
                },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(primaryLabel, fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------- Settings ----------

@Composable
private fun SettingsScreen(vm: AppVm) {
    var key by remember { mutableStateOf(vm.settings.apiKey) }
    var model by remember { mutableStateOf(vm.settings.model) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("Settings", onBack = { vm.screen = Screen.Garden })
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = key, onValueChange = { key = it },
                label = { Text("Anthropic API key") },
                placeholder = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "The key is stored encrypted on this phone and is only sent to Anthropic when you scan a plant.",
                fontSize = 13.sp, color = Muted,
            )
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                label = { Text("Model") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Default is ${SettingsStore.DEFAULT_MODEL}, run at low effort to keep token use down.",
                fontSize = 13.sp, color = Muted,
            )
        }
        Button(
            onClick = {
                vm.settings.apiKey = key
                vm.settings.model = model.ifBlank { SettingsStore.DEFAULT_MODEL }
                vm.screen = Screen.Garden
            },
            modifier = Modifier.fillMaxWidth().padding(20.dp).height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Save", fontWeight = FontWeight.Bold) }
    }
}
