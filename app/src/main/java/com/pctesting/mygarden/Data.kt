package com.pctesting.mygarden

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

val SUNLIGHT_OPTIONS = listOf("Full sun", "Partial shade", "Indirect light", "Low light")

data class Plant(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val scientific: String = "",
    val localName: String = "",
    val localNameHi: String = "",
    val photo: String = "",
    val sunlight: String = "",
    val sunlightNote: String = "",
    val waterDays: Int = 0,
    val waterNote: String = "",
    val fertiliser: String = "",
    val fertiliserNote: String = "",
    val flowering: Boolean = true,
    val blooming: String = "",
    val bloomingNote: String = "",
    val propagation: String = "",
    val propagationNote: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    /** Hindi version of the text fields, keyed by field name; empty until translated. */
    val hindi: Map<String, String> = emptyMap(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("scientific", scientific).put("photo", photo)
        .put("sunlight", sunlight).put("sunlightNote", sunlightNote)
        .put("waterDays", waterDays).put("waterNote", waterNote)
        .put("fertiliser", fertiliser).put("fertiliserNote", fertiliserNote)
        .put("flowering", flowering).put("blooming", blooming).put("bloomingNote", bloomingNote)
        .put("propagation", propagation).put("propagationNote", propagationNote)
        .put("addedAt", addedAt)
        .put("localName", localName).put("localNameHi", localNameHi)
        .put("hindi", JSONObject(hindi as Map<*, *>))

    companion object {
        fun fromJson(o: JSONObject) = Plant(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            scientific = o.optString("scientific"),
            photo = o.optString("photo"),
            sunlight = o.optString("sunlight"),
            sunlightNote = o.optString("sunlightNote"),
            waterDays = o.optInt("waterDays", 0),
            waterNote = o.optString("waterNote"),
            fertiliser = o.optString("fertiliser"),
            fertiliserNote = o.optString("fertiliserNote"),
            flowering = o.optBoolean("flowering", true),
            blooming = o.optString("blooming"),
            bloomingNote = o.optString("bloomingNote"),
            propagation = o.optString("propagation"),
            propagationNote = o.optString("propagationNote"),
            addedAt = o.optLong("addedAt", System.currentTimeMillis()),
            localName = o.optString("localName"),
            localNameHi = o.optString("localNameHi"),
            hindi = o.optJSONObject("hindi")?.let { h ->
                h.keys().asSequence().associateWith { h.optString(it) }
            } ?: emptyMap(),
        )
    }
}

fun waterLabel(days: Int): String = when {
    days <= 0 -> "Watering not set"
    days == 1 -> "Water daily"
    else -> "Water every $days days"
}

fun waterShort(days: Int, hindi: Boolean = false): String = when {
    days <= 0 -> if (hindi) "तय नहीं" else "Not set"
    days == 1 -> if (hindi) "रोज़" else "Daily"
    else -> if (hindi) "हर $days दिन" else "Every $days days"
}

/** The garden, kept as one JSON file in the app's private storage. */
class PlantStore(context: Context) {
    private val file = File(context.filesDir, "plants.json")

    fun load(): List<Plant> = try {
        if (!file.exists()) emptyList()
        else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { Plant.fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(plants: List<Plant>) {
        val arr = JSONArray()
        plants.forEach { arr.put(it.toJson()) }
        val tmp = File(file.parentFile, "plants.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}

/** API key (encrypted with a key held in the Android Keystore) and model name. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    var apiKey: String
        get() = try {
            val iv = prefs.getString("key_iv", null)
            val ct = prefs.getString("key_ct", null)
            if (iv == null || ct == null) ""
            else {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE, secretKey(),
                    GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
                )
                String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }
        set(value) {
            if (value.isBlank()) {
                prefs.edit().remove("key_iv").remove("key_ct").apply()
                return
            }
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey())
                val ct = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
                prefs.edit()
                    .putString("key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .putString("key_ct", Base64.encodeToString(ct, Base64.NO_WRAP))
                    .apply()
            } catch (e: Exception) {
                // Leave the previous value in place.
            }
        }

    var model: String
        get() = prefs.getString("model", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        set(value) {
            prefs.edit().putString("model", value.trim()).apply()
        }

    /** Used for a second look when the main model can't identify a plant or isn't sure. */
    var strongModel: String
        get() = prefs.getString("strong_model", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_STRONG_MODEL
        set(value) {
            prefs.edit().putString("strong_model", value.trim()).apply()
        }

    /** Show plant info in Hindi. */
    var hindi: Boolean
        get() = prefs.getBoolean("hindi", false)
        set(value) {
            prefs.edit().putBoolean("hindi", value).apply()
        }

    companion object {
        const val DEFAULT_MODEL = "claude-haiku-5-5"
        const val DEFAULT_STRONG_MODEL = "claude-sonnet-5-5"
        private const val ALIAS = "mygarden_api_key"
    }
}

object Images {
    /** Full photo: sharp enough to identify the plant and view full screen, ~100–150 KB. */
    private const val MAX_SIDE = 1024
    private const val QUALITY = 75

    /** List thumbnail: ~10–20 KB, so the garden list loads quickly. */
    private const val THUMB_SIDE = 320
    private const val THUMB_QUALITY = 70

    /** Decoded pictures kept in memory, so scrolling back doesn't decode them again. */
    private val cache = object : android.util.LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Copies a picked or captured photo into private storage, upright and scaled down. */
    fun importPhoto(context: Context, uri: Uri): File? = try {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

        if (bmp == null) null
        else {
            val degrees = try {
                cr.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) {
                0f
            }
            val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
            if (degrees != 0f || scale < 1f) {
                val m = Matrix()
                if (scale < 1f) m.postScale(scale, scale)
                if (degrees != 0f) m.postRotate(degrees)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val out = File(dir, UUID.randomUUID().toString() + ".jpg")
            val finalBmp: Bitmap = bmp
            FileOutputStream(out).use { finalBmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            saveThumb(finalBmp, thumbFile(out.absolutePath))
            out
        }
    } catch (e: Exception) {
        null
    }

    private fun thumbFile(path: String) = File(path.removeSuffix(".jpg") + "_t.jpg")

    private fun saveThumb(full: Bitmap, file: File) {
        try {
            val scale = THUMB_SIDE.toFloat() / maxOf(full.width, full.height)
            val small = if (scale < 1f) Bitmap.createScaledBitmap(
                full, (full.width * scale).toInt().coerceAtLeast(1),
                (full.height * scale).toInt().coerceAtLeast(1), true,
            ) else full
            FileOutputStream(file).use { small.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, it) }
        } catch (e: Exception) {
            file.delete()
        }
    }

    /**
     * Loads a photo for showing. Small sizes come from the thumbnail file, which is made
     * on first use for photos saved before thumbnails existed (or restored from a backup).
     */
    fun load(path: String, maxSide: Int): Bitmap? {
        if (path.isBlank()) return null
        val key = "$path@$maxSide"
        cache.get(key)?.let { return it }
        val bmp = try {
            if (maxSide <= THUMB_SIDE) {
                val thumb = thumbFile(path)
                if (!thumb.isFile) decodeSampled(path, MAX_SIDE)?.let { saveThumb(it, thumb) }
                decodeSampled(if (thumb.isFile) thumb.absolutePath else path, maxSide)
            } else {
                decodeSampled(path, maxSide)
            }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    private fun decodeSampled(path: String, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Deletes a photo and its thumbnail. */
    fun delete(path: String) {
        if (path.isBlank()) return
        File(path).delete()
        thumbFile(path).delete()
        cache.snapshot().keys.filter { it.startsWith("$path@") }.forEach { cache.remove(it) }
    }

    fun base64(file: File): String = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
}

/**
 * Backup file: a zip with plants.json and a photos/ folder.
 * The API key is not included.
 */
object Backup {
    private const val INDEX = "plants.json"
    private const val PHOTOS = "photos/"

    fun write(plants: List<Plant>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            val arr = JSONArray()
            plants.forEach { p ->
                val photo = File(p.photo)
                val rel = if (p.photo.isNotBlank() && photo.isFile) PHOTOS + photo.name else ""
                arr.put(p.copy(photo = rel).toJson())
                if (rel.isNotEmpty()) {
                    zip.putNextEntry(ZipEntry(rel))
                    photo.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            zip.putNextEntry(ZipEntry(INDEX))
            zip.write(JSONObject().put("app", "mygarden").put("version", 1).put("plants", arr)
                .toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    /** Returns the plants in the backup with their photos copied in, or null if it isn't a backup. */
    fun read(context: Context, input: InputStream): List<Plant>? {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        var index: String? = null
        val photos = mutableMapOf<String, File>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.isDirectory -> Unit
                    entry.name == INDEX -> index = zip.readBytes().toString(Charsets.UTF_8)
                    entry.name.startsWith(PHOTOS) -> {
                        // Use only the file name, so a crafted entry can't write outside the folder.
                        val name = File(entry.name).name
                        if (name.isNotBlank()) {
                            val f = File(dir, name)
                            FileOutputStream(f).use { zip.copyTo(it) }
                            photos[PHOTOS + name] = f
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val o = try {
            JSONObject(index ?: return null)
        } catch (e: Exception) {
            return null
        }
        if (o.optString("app") != "mygarden") return null
        val arr = o.optJSONArray("plants") ?: return null
        return (0 until arr.length()).map { i ->
            val p = Plant.fromJson(arr.getJSONObject(i))
            p.copy(photo = photos[p.photo]?.absolutePath ?: "")
        }
    }
}
