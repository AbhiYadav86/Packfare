package com.packfare.app.receipt

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR with ML Kit (bundled model: works offline, no Play Services download, free).
 * Returns the lines with their positions so [ReceiptParser] can pair labels with amounts.
 */
class ReceiptScanner(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun scan(photo: File): ParsedReceipt {
        val image = InputImage.fromFilePath(context, Uri.fromFile(photo)) // applies EXIF rotation
        val text = recognize(image)
        val lines = text.textBlocks.flatMap { block ->
            block.lines.map { line ->
                val box = line.boundingBox
                OcrLine(
                    text = line.text,
                    left = box?.left ?: 0,
                    top = box?.top ?: 0,
                    right = box?.right ?: 0,
                    bottom = box?.bottom ?: 0,
                )
            }
        }
        return ReceiptParser.parse(lines)
    }

    private suspend fun recognize(image: InputImage): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
