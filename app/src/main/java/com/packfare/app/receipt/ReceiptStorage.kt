package com.packfare.app.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Receipt photos live in the app's private storage (files/receipts). They're never uploaded,
 * and they're deleted along with their expense or trip.
 */
class ReceiptStorage(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "receipts").apply { mkdirs() }

    /** A fresh file plus a content:// Uri the camera app can write into. */
    fun newPhoto(): Pair<File, Uri> {
        val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /** Copies a gallery image into app storage (gallery Uris can become unreadable later). */
    suspend fun importFromGallery(source: Uri): File = withContext(Dispatchers.IO) {
        val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Couldn't open the selected image" }
            file.outputStream().use { input.copyTo(it) }
        }
        file
    }

    fun delete(path: String) {
        val file = File(path)
        // Only ever delete inside our own receipts folder.
        if (file.parentFile?.canonicalPath == dir.canonicalPath) file.delete()
    }

    /** Downscaled bitmap for display. Uses ImageDecoder on Android 9+ so photos come out upright. */
    suspend fun loadPreview(path: String, maxSide: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = maxOf(w, h).toFloat() / maxSide
                    if (scale > 1f) decoder.setTargetSize((w / scale).toInt(), (h / scale).toInt())
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()
    }
}
