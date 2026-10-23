@file:OptIn(ExperimentalMaterial3Api::class)

package com.packfare.app.ui.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packfare.app.data.DocumentEntity
import com.packfare.app.ui.theme.BalanceColors
import com.packfare.app.vault.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DocViewerViewModel(private val vault: Vault, private val docId: Long, private val cacheDir: File) : ViewModel() {
    var doc by mutableStateOf<DocumentEntity?>(null)
        private set
    var pages by mutableStateOf<List<Bitmap>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val d = vault.document(docId)
            if (d == null) {
                error = "Document not found"
                return@launch
            }
            doc = d
            pages = runCatching {
                val bytes = vault.open(d)
                if (d.mimeType == "application/pdf") renderPdf(bytes) else listOfNotNull(decodeImage(bytes))
            }.onFailure { error = "Couldn't open this document" }.getOrDefault(emptyList())
        }
    }

    fun delete(onDone: () -> Unit) {
        val d = doc ?: return
        viewModelScope.launch {
            vault.delete(d)
            onDone()
        }
    }

    private suspend fun decodeImage(bytes: ByteArray): Bitmap? = withContext(Dispatchers.Default) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * PdfRenderer needs a file descriptor, so the decrypted PDF exists briefly in the app's private
     * cache while pages are drawn, then it's deleted. Pages are capped to keep memory sane.
     */
    private suspend fun renderPdf(bytes: ByteArray): List<Bitmap> = withContext(Dispatchers.IO) {
        val tmp = File.createTempFile("vault", ".pdf", cacheDir)
        try {
            tmp.writeBytes(bytes)
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    (0 until minOf(renderer.pageCount, 20)).map { i ->
                        renderer.openPage(i).use { page ->
                            val scale = 1400f / page.width
                            val bmp = Bitmap.createBitmap(1400, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(AndroidColor.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }
        } finally {
            tmp.delete()
        }
    }
}

@Composable
fun DocViewerScreen(vm: DocViewerViewModel, onBack: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    SecureWindow(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.doc?.title ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }, enabled = vm.doc != null) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete document")
                    }
                },
            )
        },
    ) { padding ->
        when {
            vm.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(vm.error.orEmpty())
            }
            vm.pages.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).background(Color(0xFF202020)),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.pages) { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete from vault?") },
            text = { Text("The encrypted copy on this phone is removed for good.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onBack)
                }) { Text("Delete", color = BalanceColors.negative) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Blocks screenshots and hides the content in the recent-apps switcher while a document is open. */
@Composable
private fun SecureWindow(context: Context) {
    DisposableEffect(Unit) {
        val window = (context as? ComponentActivity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
