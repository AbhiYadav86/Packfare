package com.packfare.app.ui.expense

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packfare.app.data.LegEntity
import com.packfare.app.data.MemberEntity
import com.packfare.app.data.TripRepository
import com.packfare.app.domain.Category
import com.packfare.app.domain.PaymentMethod
import com.packfare.app.domain.Money
import com.packfare.app.domain.SplitCalculator
import com.packfare.app.receipt.ReceiptScanner
import com.packfare.app.receipt.ReceiptStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

enum class SplitMode(val label: String) {
    EQUAL("Equally"),
    EXACT("Exact ₹"),
    SHARES("By shares"),
}

/**
 * Add or edit an expense. Form fields live here (not in the composable) so they survive rotation.
 * [expenseId] null = new expense.
 */
class AddExpenseViewModel(
    private val repository: TripRepository,
    private val scanner: ReceiptScanner,
    private val storage: ReceiptStorage,
    private val tripId: Long,
    private val expenseId: Long?,
) : ViewModel() {

    val isEdit: Boolean = expenseId != null

    var loaded by mutableStateOf(false)
        private set
    var members by mutableStateOf<List<MemberEntity>>(emptyList())
        private set

    // ---- Form ----
    var title by mutableStateOf("")
    var amountText by mutableStateOf("")
    var category by mutableStateOf(Category.FOOD)
    var paidById by mutableStateOf<Long?>(null)
    var paymentMethod by mutableStateOf(PaymentMethod.UPI)
    /** Optional route leg this was spent on. */
    var legId by mutableStateOf<Long?>(null)
    var legs by mutableStateOf<List<LegEntity>>(emptyList())
        private set
    var splitMode by mutableStateOf(SplitMode.EQUAL)

    /** EQUAL mode: who's in. */
    var splitAmong by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** EXACT mode: typed ₹ per person. */
    var exactAmounts by mutableStateOf<Map<Long, String>>(emptyMap())
        private set

    /** SHARES mode: weight per person, 0 = not included. */
    var weights by mutableStateOf<Map<Long, Int>>(emptyMap())
        private set

    // ---- Receipt ----
    var receiptPath by mutableStateOf<String?>(null)
        private set
    var receiptPreview by mutableStateOf<Bitmap?>(null)
        private set
    var isScanning by mutableStateOf(false)
        private set
    /** One-off message after a scan, shown in a snackbar. */
    var scanMessage by mutableStateOf<String?>(null)

    private var originalReceiptPath: String? = null
    private val newReceiptFiles = mutableListOf<String>()
    private var pendingPhoto: File? = null
    private var saved = false
    private var saving = false

    init {
        viewModelScope.launch {
            val list = repository.members(tripId).first()
            legs = repository.legs(tripId).first()
            // Default payer: "me" on this phone, if set.
            val me = repository.tripOnce(tripId)?.ownerMemberId
            members = list
            paidById = list.firstOrNull { it.id == me }?.id ?: list.firstOrNull()?.id
            splitAmong = list.map { it.id }.toSet()
            weights = list.associate { it.id to 1 }

            if (expenseId != null) loadExisting(expenseId, list)
            loaded = true
        }
    }

    private suspend fun loadExisting(id: Long, list: List<MemberEntity>) {
        val (expense, shares) = repository.expenseWithShares(id) ?: return
        title = expense.title
        amountText = Money.toUpiAmount(expense.amountPaise).removeSuffix(".00")
        category = Category.fromName(expense.category)
        paidById = expense.paidById
        paymentMethod = PaymentMethod.fromName(expense.paymentMethod)
        legId = expense.legId
        receiptPath = expense.receiptPath
        originalReceiptPath = expense.receiptPath
        refreshPreview()

        // Was it an equal split? If so, reopen in EQUAL mode; otherwise show the exact amounts.
        val ordered = list.map { it.id }.filter { it in shares.keys }
        val looksEqual = ordered.isNotEmpty() &&
            SplitCalculator.equalShares(expense.amountPaise, ordered) == shares
        if (looksEqual) {
            splitMode = SplitMode.EQUAL
            splitAmong = shares.keys
        } else {
            splitMode = SplitMode.EXACT
            exactAmounts = shares.mapValues { (_, paise) -> Money.toUpiAmount(paise).removeSuffix(".00") }
        }
    }

    // ---- Split editing ----

    fun toggleSplit(memberId: Long) {
        splitAmong = if (memberId in splitAmong) splitAmong - memberId else splitAmong + memberId
    }

    fun selectAll() {
        splitAmong = members.map { it.id }.toSet()
    }

    fun setExact(memberId: Long, text: String) {
        exactAmounts = exactAmounts + (memberId to text)
    }

    fun changeWeight(memberId: Long, delta: Int) {
        val next = ((weights[memberId] ?: 0) + delta).coerceIn(0, 20)
        weights = weights + (memberId to next)
    }

    val amountPaise: Long? get() = Money.parse(amountText)?.takeIf { it > 0 }

    private val orderedIds: List<Long> get() = members.map { it.id }

    /** Per-person paise for the current mode, or null if the split isn't valid yet. */
    val computedShares: Map<Long, Long>?
        get() {
            val amount = amountPaise ?: return null
            return when (splitMode) {
                SplitMode.EQUAL -> {
                    val ids = orderedIds.filter { it in splitAmong }
                    if (ids.isEmpty()) null else SplitCalculator.equalShares(amount, ids)
                }
                SplitMode.EXACT -> {
                    val parsed = orderedIds.associateWith { id ->
                        val t = exactAmounts[id].orEmpty()
                        if (t.isBlank()) 0L else Money.parse(t) ?: return null
                    }.filterValues { it > 0 }
                    if (parsed.isEmpty() || parsed.values.sum() != amount) null else parsed
                }
                SplitMode.SHARES -> {
                    val w = orderedIds.associateWith { weights[it] ?: 0 }
                    if (w.values.none { it > 0 }) null else SplitCalculator.weightedShares(amount, w)
                }
            }
        }

    /** Helper line under the split section, e.g. "₹350 each" or "₹120 left to assign". */
    val splitHint: String?
        get() {
            val amount = amountPaise ?: return null
            return when (splitMode) {
                SplitMode.EQUAL -> computedShares?.values?.maxOrNull()?.let { "${Money.format(it)} each" }
                    ?: "Pick at least one person"
                SplitMode.EXACT -> {
                    val typed = exactAmounts.values.sumOf { if (it.isBlank()) 0L else Money.parse(it) ?: 0L }
                    val diff = amount - typed
                    when {
                        exactAmounts.values.any { it.isNotBlank() && Money.parse(it) == null } -> "Check the amounts"
                        diff > 0 -> "${Money.format(diff)} left to assign"
                        diff < 0 -> "${Money.format(-diff)} more than the total"
                        else -> "Adds up ✓"
                    }
                }
                SplitMode.SHARES -> computedShares?.let { shares ->
                    val parts = members.mapNotNull { m -> shares[m.id]?.let { "${m.name} ${Money.format(it)}" } }
                    parts.joinToString(" · ")
                } ?: "Give at least one person a share"
            }
        }

    val splitIsError: Boolean
        get() = amountPaise != null && computedShares == null

    val canSave: Boolean
        get() = loaded && amountPaise != null && paidById != null && computedShares != null && !isScanning

    // ---- Receipt scanning ----

    /** Called just before launching the camera. Returns the Uri the camera should write to. */
    fun preparePhoto(): Uri {
        val (file, uri) = storage.newPhoto()
        pendingPhoto = file
        return uri
    }

    fun onPhotoTaken(success: Boolean) {
        val file = pendingPhoto ?: return
        pendingPhoto = null
        if (success && file.exists() && file.length() > 0) {
            scan(file)
        } else {
            file.delete()
        }
    }

    fun onGalleryPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val file = runCatching { storage.importFromGallery(uri) }.getOrNull()
            if (file == null) {
                scanMessage = "Couldn't open that image"
            } else {
                scan(file)
            }
        }
    }

    private fun scan(file: File) {
        newReceiptFiles += file.absolutePath
        receiptPath = file.absolutePath
        isScanning = true
        viewModelScope.launch {
            refreshPreview()
            val result = runCatching { scanner.scan(file) }.getOrNull()
            isScanning = false
            if (result == null) {
                scanMessage = "Couldn't read this receipt. Photo saved; please fill in the details."
                return@launch
            }
            result.totalPaise?.let { amountText = Money.toUpiAmount(it).removeSuffix(".00") }
            if (title.isBlank()) result.merchant?.let { title = it }
            result.category?.let { category = it }
            scanMessage = when {
                result.totalPaise != null && result.merchant != null ->
                    "Found ${Money.format(result.totalPaise)} at ${result.merchant}. Please check."
                result.totalPaise != null -> "Found ${Money.format(result.totalPaise)}. Please check."
                else -> "Couldn't find the total. Please type it in."
            }
        }
    }

    fun removeReceipt() {
        receiptPath = null
        receiptPreview = null
    }

    private suspend fun refreshPreview() {
        receiptPreview = receiptPath?.let { storage.loadPreview(it) }
    }

    // ---- Save / delete ----

    fun save(onSaved: () -> Unit) {
        val amount = amountPaise ?: return
        val payer = paidById ?: return
        val shares = computedShares ?: return
        if (!canSave || saving) return
        saving = true
        viewModelScope.launch {
            repository.saveExpense(
                existingId = expenseId,
                tripId = tripId,
                title = title.ifBlank { category.label },
                amountPaise = amount,
                paidById = payer,
                category = category,
                sharesByMember = shares,
                receiptPath = receiptPath,
                paymentMethod = paymentMethod,
                legId = legId,
            )
            saved = true
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = expenseId ?: return
        viewModelScope.launch {
            repository.deleteExpense(id)
            onDeleted()
        }
    }

    override fun onCleared() {
        // Photos taken on this screen but not kept (screen abandoned, or replaced by another scan).
        val keep = if (saved) receiptPath else originalReceiptPath
        newReceiptFiles.filter { it != keep }.forEach { storage.delete(it) }
        pendingPhoto?.delete()
    }
}
