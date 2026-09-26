package com.automatelinux.evenly

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.automatelinux.evenly.data.PickedFile
import com.automatelinux.evenly.data.Platform
import com.automatelinux.evenly.data.ResponseCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

// Thin Android launcher — all UI lives in the shared commonMain App() composable.
class MainActivity : ComponentActivity() {

    private var pendingPick: ((PickedFile?) -> Unit)? = null
    private var cameraFile: File? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        deliver(uri?.let { readImage(it) })
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        deliver(if (ok && f != null && f.length() > 0) readImage(Uri.fromFile(f)) else null)
    }

    private fun deliver(file: PickedFile?) {
        val cb = pendingPick
        pendingPick = null
        cb?.invoke(file)
    }

    /** Receipts are downscaled to ≤1600px JPEG so uploads stay small on mobile data. */
    private fun readImage(uri: Uri): PickedFile? {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return PickedFile(out.toByteArray(), "receipt-${System.currentTimeMillis()}.jpg", "image/jpeg")
    }

    private val platform = object : Platform {
        override val appVersion: String = BuildConfig.VERSION_NAME

        override fun openUrl(url: String): Boolean = try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }

        override fun pickImage(fromCamera: Boolean, onResult: (PickedFile?) -> Unit) {
            pendingPick = onResult
            if (fromCamera) {
                val dir = File(cacheDir, "camera").apply { mkdirs() }
                val f = File(dir, "receipt.jpg").apply { delete() }
                cameraFile = f
                cameraLauncher.launch(FileProvider.getUriForFile(this@MainActivity, "$packageName.files", f))
            } else {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        override fun saveToDownloads(fileName: String, mime: String, bytes: ByteArray): String {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return "Saving to Downloads needs Android 10 or newer — use Share instead."
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return "Couldn't create the file in Downloads."
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return "Couldn't write to Downloads."
            return "Saved to Downloads/$fileName"
        }

        override fun shareFile(fileName: String, mime: String, bytes: ByteArray) {
            val dir = File(cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, fileName).apply { writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Export $fileName"))
        }
    }

    /** Last good GET responses, one file per API path, in app-private storage. */
    private val cache by lazy {
        val dir = File(filesDir, "api-cache").apply { mkdirs() }
        object : ResponseCache {
            private fun file(key: String): File {
                val hash = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
                return File(dir, "$hash.json")
            }
            override fun get(key: String): String? = file(key).takeIf { it.exists() }?.readText()
            override fun put(key: String, value: String) {
                val f = file(key)
                val tmp = File(dir, f.name + ".tmp")
                tmp.writeText(value)
                tmp.renameTo(f)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App(
                baseUrl = BuildConfig.API_BASE_URL,
                token = BuildConfig.API_TOKEN,
                cache = cache,
                platform = platform,
                backHandler = { enabled, onBack -> BackHandler(enabled, onBack) },
            )
        }
    }
}
