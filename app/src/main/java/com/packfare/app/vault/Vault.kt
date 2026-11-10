package com.packfare.app.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.packfare.app.data.DocumentEntity
import com.packfare.app.data.ExtrasDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class DocKind(val label: String, val emoji: String) {
    TICKET("Ticket", "🎫"),
    ID("ID / Passport", "🪪"),
    PERMIT("Permit", "📜"),
    BOOKING("Booking", "🏨"),
    INSURANCE("Insurance", "🛟"),
    OTHER("Other", "📄");

    companion object {
        fun fromName(name: String?): DocKind = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * Encrypted, offline document vault.
 *
 * Each file is encrypted with AES-256-GCM. The key lives in the Android Keystore (hardware-backed
 * on most phones) and never leaves it, so copying the app's files off the phone doesn't reveal them.
 * File format: [12-byte IV][ciphertext + GCM tag].
 */
class Vault(private val context: Context, private val dao: ExtrasDao) {

    private val dir: File get() = File(context.filesDir, "vault").apply { mkdirs() }

    fun documents() = dao.documents()

    fun documentsForTrip(tripId: Long) = dao.documentsForTrip(tripId)

    suspend fun add(source: Uri, title: String, kind: DocKind, tripId: Long? = null): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(source) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Couldn't open the file" }
            input.readBytes()
        }
        require(bytes.size <= MAX_BYTES) { "Files up to 20 MB only" }
        val fileName = UUID.randomUUID().toString() + ".bin"
        File(dir, fileName).writeBytes(encrypt(bytes))
        dao.insertDocument(
            DocumentEntity(
                title = title.ifBlank { displayName(source) ?: kind.label },
                kind = kind.name,
                fileName = fileName,
                mimeType = mime,
                sizeBytes = bytes.size.toLong(),
                tripId = tripId,
            ),
        )
    }

    /** Decrypted bytes, kept in memory only. */
    suspend fun open(doc: DocumentEntity): ByteArray = withContext(Dispatchers.IO) {
        decrypt(File(dir, doc.fileName).readBytes())
    }

    suspend fun delete(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        File(dir, doc.fileName).delete()
        dao.deleteDocument(doc.id)
    }

    suspend fun document(id: Long) = dao.document(id)

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
        }

    // ---- crypto ----

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        return iv + cipher.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    companion object {
        private const val KEY_ALIAS = "packfare_vault_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val MAX_BYTES = 20 * 1024 * 1024
    }
}
