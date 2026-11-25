@file:OptIn(ExperimentalMaterial3Api::class)

package com.pctesting.mygarden

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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

// Navy & Saffron theme
private val Navy = Color(0xFF1F2F5C)      // filled buttons
private val Saffron = Color(0xFFA85400)   // highlights, links, outlined buttons
private val Bg = Color(0xFFF5F6F8)
private val Ink = Color(0xFF141B2D)
private val Muted = Color(0xFF525A6B)
private val Line = Color(0xFFE0E3EA)
private val Tonal = Color(0xFFE9ECF2)
private val PhotoBg = Color(0xFFDDE3EE)

private val AppColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondaryContainer = Tonal,
    onSecondaryContainer = Ink,
    background = Bg,
    onBackground = Ink,
    surface = Bg,
    onSurface = Ink,
    surfaceVariant = Tonal,
    onSurfaceVariant = Muted,
    outline = Color(0xFFC3C8D3),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Tonal,
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
        vm.viewPhoto?.let { path -> PhotoViewer(path) { vm.viewPhoto = null } }
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
private fun StatCard(label: String, title: String, note: String, hi: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White,
        contentColor = Ink,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            Text(title.ifBlank { L(hi, "Not set", "तय नहीं") }, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (note.isNotBlank()) Text(note, fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
private fun InfoCard(label: String, title: String, note: String, hi: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        contentColor = Ink,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
            Text(title.ifBlank { L(hi, "Not set", "तय नहीं") }, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
            it.scientific.contains(query, ignoreCase = true) ||
            it.localName.contains(query, ignoreCase = true) ||
            it.localNameHi.contains(query)
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
                        PlantRow(
                            plant,
                            onPhoto = { vm.viewPhoto = plant.photo },
                            onClick = { vm.screen = Screen.Details(plant.id) },
                        )
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
            containerColor = Navy,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

@Composable
private fun PlantRow(plant: Plant, onPhoto: () -> Unit, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        contentColor = Ink,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Line),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlantPhoto(
                plant.photo, 256,
                Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = plant.photo.isNotBlank(), onClickLabel = "View photo") { onPhoto() },
            )
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(plant.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (plant.localLine().isNotBlank()) {
                    Text(plant.localLine(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Muted)
                }
                if (plant.scientific.isNotBlank()) {
                    Text(plant.scientific, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Muted)
                }
                Text(careLine(plant), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Saffron)
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
                Text(
                    if (s.closerLook) "Taking a closer look…" else "Identifying your plant…",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
                if (s.closerLook) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This one is tricky, so a stronger model is checking it.",
                        fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.discardScan() }) { Text("Cancel") }
            }
        }

        plant != null -> ResultView(
            vm = vm,
            plant = plant,
            unsure = s.unsure,
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
    vm: AppVm,
    plant: Plant,
    unsure: Boolean,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onAdd: () -> Unit,
    onSkip: () -> Unit,
) {
    val hi = vm.hindi
    AutoTranslate(vm, plant)
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
            PlantPhoto(plant.photo, 1024, Modifier.fillMaxSize())
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.White,
                contentColor = Ink,
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
            Text(
                if (unsure) L(hi, "BEST GUESS", "अनुमान") else L(hi, "IDENTIFIED", "पहचाना गया"),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Saffron,
            )
            NameBlock(plant, hi, 30)
            if (unsure) {
                Text(
                    L(
                        hi, "Not fully sure. Compare with your plant before adding it.",
                        "पूरी तरह पक्का नहीं है। जोड़ने से पहले अपने पौधे से मिलान कर लें।",
                    ),
                    fontSize = 14.sp, color = Muted,
                )
            }
            LangToggle(hi, vm.translatingId == plant.id) { vm.chooseHindi(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    L(hi, "Sunlight", "धूप"), plant.show("sunlight", hi), plant.show("sunlightNote", hi),
                    hi, Modifier.weight(1f),
                )
                StatCard(
                    L(hi, "Watering", "पानी"), waterShort(plant.waterDays, hi), plant.show("waterNote", hi),
                    hi, Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = onMore,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Saffron),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Saffron),
            ) {
                Text(L(hi, "More info", "और जानकारी"), fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            Text(
                L(hi, "Fertiliser, blooming season and propagation", "खाद, फूल आने का मौसम और नए पौधे लगाने का तरीका"),
                fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Surface(
            color = Color.White,
            contentColor = Ink,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            border = BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(
                        L(hi, "Add this plant to your garden?", "क्या यह पौधा अपने बगीचे में जोड़ें?"),
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        L(hi, "Skip it if this plant isn't yours.", "अगर यह पौधा आपका नहीं है, तो छोड़ दें।"),
                        fontSize = 14.sp, color = Muted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(L(hi, "Not mine", "मेरा नहीं"), fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(L(hi, "Add to garden", "बगीचे में जोड़ें"), fontWeight = FontWeight.Bold) }
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
    val hi = vm.hindi
    AutoTranslate(vm, plant)
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
        TopBar(L(hi, "Plant details", "पौधे की जानकारी"), onBack = { goBack(vm) }) {
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
                PlantPhoto(
                    plant.photo, 256,
                    Modifier.size(84.dp).clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = plant.photo.isNotBlank(), onClickLabel = "View photo") {
                            vm.viewPhoto = plant.photo
                        },
                )
                Spacer(Modifier.width(14.dp))
                NameBlock(plant, hi, 26)
            }
            LangToggle(hi, vm.translatingId == plant.id) { vm.chooseHindi(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    L(hi, "Sunlight", "धूप"), plant.show("sunlight", hi), plant.show("sunlightNote", hi),
                    hi, Modifier.weight(1f),
                )
                StatCard(
                    L(hi, "Watering", "पानी"), waterShort(plant.waterDays, hi), plant.show("waterNote", hi),
                    hi, Modifier.weight(1f),
                )
            }
            InfoCard(L(hi, "Fertiliser", "खाद"), plant.show("fertiliser", hi), plant.show("fertiliserNote", hi), hi)
            if (plant.flowering) {
                InfoCard(L(hi, "Blooming", "फूल आने का मौसम"), plant.show("blooming", hi), plant.show("bloomingNote", hi), hi)
            } else {
                InfoCard(
                    L(hi, "Blooming", "फूल आने का मौसम"),
                    L(hi, "Not a flowering plant", "इस पौधे पर फूल नहीं आते"), "", hi,
                )
            }
            InfoCard(
                L(hi, "Propagation", "नए पौधे कैसे लगाएँ"),
                plant.show("propagation", hi), plant.show("propagationNote", hi), hi,
            )
            Text(
                when {
                    hi -> "यह जानकारी AI से बनी है और गलत हो सकती है।"
                    saved -> "Care info may be AI-generated. Tap the pencil to correct anything."
                    else -> "Care info is AI-generated. You can correct it after adding the plant."
                },
                fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        if (!saved) {
            Button(
                onClick = { vm.addToGarden(plant) },
                modifier = Modifier.fillMaxWidth().padding(20.dp).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(L(hi, "Add to garden", "बगीचे में जोड़ें"), fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------- Language and names ----------

private fun L(hi: Boolean, en: String, hindi: String) = if (hi) hindi else en

/** A plant field in the chosen language, falling back to English until translated. */
private fun Plant.show(key: String, hi: Boolean): String {
    val en = field(key)
    return if (hi) hindi[key]?.takeIf { it.isNotBlank() } ?: en else en
}

private fun Plant.localLine(): String = when {
    localName.isNotBlank() && localNameHi.isNotBlank() -> "$localName ($localNameHi)"
    else -> localName.ifBlank { localNameHi }
}

@Composable
private fun AutoTranslate(vm: AppVm, plant: Plant) {
    LaunchedEffect(vm.hindi, plant.id, plant.hindi.isEmpty()) {
        if (vm.hindi && plant.hindi.isEmpty()) vm.translate(plant.id)
    }
}

@Composable
private fun NameBlock(plant: Plant, hi: Boolean, titleSize: Int) {
    val hiTitle = plant.localNameHi.ifBlank { plant.hindi["name"].orEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (hi && hiTitle.isNotBlank()) {
            Text(hiTitle, fontSize = titleSize.sp, fontWeight = FontWeight.ExtraBold, lineHeight = (titleSize + 8).sp)
            Text(plant.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Muted)
        } else {
            Text(plant.name, fontSize = titleSize.sp, fontWeight = FontWeight.ExtraBold, lineHeight = (titleSize + 6).sp)
            if (plant.localLine().isNotBlank()) {
                Text(
                    plant.localLine(),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Saffron,
                )
            }
        }
        if (plant.scientific.isNotBlank()) {
            Text(plant.scientific, fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Muted)
        }
    }
}

@Composable
private fun LangChip(selected: Boolean, text: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Tonal,
            selectedLabelColor = Ink,
            selectedLeadingIconColor = Ink,
            labelColor = Muted,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Line,
            selectedBorderColor = Saffron,
            selectedBorderWidth = 1.dp,
        ),
    )
}

@Composable
private fun LangToggle(hi: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LangChip(!hi, "English") { onChange(false) }
        LangChip(hi, "हिंदी") { onChange(true) }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("अनुवाद हो रहा है…", fontSize = 13.sp, color = Muted)
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
    var localName by rememberSaveable(initial.id) { mutableStateOf(initial.localName) }
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
            OutlinedTextField(
                value = localName, onValueChange = { localName = it },
                label = { Text("Local name (optional)") },
                placeholder = { Text("e.g. Gudhal") },
                singleLine = true,
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
                border = BorderStroke(1.dp, Saffron),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Saffron),
            ) { Text(secondaryLabel, fontWeight = FontWeight.Bold) }
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val days = water.toIntOrNull() ?: 0
                    // A note written for the old value is dropped when that value is changed.
                    val edited =
                        initial.copy(
                            name = name.trim(),
                            localName = localName.trim(),
                            localNameHi = if (localName.trim() == initial.localName) initial.localNameHi else "",
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
                    // Any change makes the saved Hindi text out of date; it is fetched again when needed.
                    onPrimary(if (edited == initial) initial else edited.copy(hindi = emptyMap()))
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
    var strong by remember { mutableStateOf(vm.settings.strongModel) }

    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) vm.exportBackup(uri) }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("Settings", onBack = { vm.screen = Screen.Garden })
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Anthropic API")
            OutlinedTextField(
                value = key, onValueChange = { key = it },
                label = { Text("API key") },
                placeholder = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Stored encrypted on this phone and only sent to Anthropic when you scan a plant.",
                fontSize = 13.sp, color = Muted,
            )
            OutlinedTextField(
                value = model, onValueChange = { model = it },
                label = { Text("Main model") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = strong, onValueChange = { strong = it },
                label = { Text("Model for tricky plants") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "The main model checks every photo at low effort. If it isn't sure, the photo is " +
                    "checked again by the model for tricky plants.",
                fontSize = 13.sp, color = Muted,
            )
            Button(
                onClick = {
                    vm.settings.apiKey = key
                    vm.settings.model = model.ifBlank { SettingsStore.DEFAULT_MODEL }
                    vm.settings.strongModel = strong.ifBlank { SettingsStore.DEFAULT_STRONG_MODEL }
                    vm.screen = Screen.Garden
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Save settings", fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Backup")
            Text(
                "Save your garden (plants and photos) to a file on this phone, in Downloads › MyGarden. " +
                    "After reinstalling the app, tap Restore and pick that file. Your API key is not included.",
                fontSize = 13.sp, color = Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        try {
                            restoreBackup.launch(arrayOf("application/zip", "application/octet-stream"))
                        } catch (e: Exception) {
                            vm.notice = "Couldn't open the file picker on this phone."
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Saffron),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Saffron),
                ) { Text("Restore", fontWeight = FontWeight.Bold) }
                Button(
                    enabled = vm.plants.isNotEmpty(),
                    onClick = {
                        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date())
                        val name = "mygarden-backup-$date.zip"
                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                            vm.saveBackupToPhone(name)
                        } else {
                            // Older Android: let the user pick where on the phone to save it.
                            try {
                                saveBackup.launch(name)
                            } catch (e: Exception) {
                                vm.notice = "Couldn't open the file picker on this phone."
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Save backup", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted,
        modifier = Modifier.padding(top = 4.dp),
    )
}

// ---------- Full-screen photo ----------

@Composable
private fun PhotoViewer(path: String, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
            value = withContext(Dispatchers.IO) { Images.load(path, 1024)?.asImageBitmap() }
        }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else scale = 2.5f
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            val b = bitmap
            if (b == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Image(
                    bitmap = b,
                    contentDescription = "Plant photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer(
                        scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y,
                    ),
                )
            }
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp).size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = "Close photo")
                }
            }
        }
    }
}
