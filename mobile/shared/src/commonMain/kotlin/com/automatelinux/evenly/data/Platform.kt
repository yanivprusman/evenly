package com.automatelinux.evenly.data

import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeImage(bytes: ByteArray): ImageBitmap?

class PickedFile(val bytes: ByteArray, val fileName: String, val mime: String)

/** Things only the Android shell can do; implemented in :app's MainActivity. */
interface Platform {
    val appVersion: String
    /** Opens a URL (e.g. a wa.me reminder). Returns false when nothing can handle it. */
    fun openUrl(url: String): Boolean
    fun pickImage(fromCamera: Boolean, onResult: (PickedFile?) -> Unit)
    /** Saves to the public Downloads folder; returns a user-facing result line. */
    fun saveToDownloads(fileName: String, mime: String, bytes: ByteArray): String
    fun shareFile(fileName: String, mime: String, bytes: ByteArray)
}
