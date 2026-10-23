@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.packfare.app.ui.expense

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.packfare.app.domain.Category
import com.packfare.app.domain.Money
import com.packfare.app.domain.PaymentMethod
import com.packfare.app.ui.components.emoji
import com.packfare.app.ui.theme.BalanceColors

@Composable
fun AddExpenseScreen(vm: AddExpenseViewModel, startWithScan: Boolean, onDone: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    var showFullReceipt by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var autoScanDone by rememberSaveable { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        vm.onPhotoTaken(ok)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        vm.onGalleryPicked(uri)
    }
    val openGallery: () -> Unit = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val openCamera: () -> Unit = {
        try {
            takePicture.launch(vm.preparePhoto())
        } catch (e: ActivityNotFoundException) {
            vm.onPhotoTaken(false)
            vm.scanMessage = "No camera app found. Pick a photo from the gallery instead."
        }
    }

    LaunchedEffect(Unit) {
        if (startWithScan && !autoScanDone) {
            autoScanDone = true
            openCamera()
        }
    }
    LaunchedEffect(vm.scanMessage) {
        vm.scanMessage?.let {
            snackbar.showSnackbar(it)
            vm.scanMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Edit expense" else "Add expense") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (vm.isEdit) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete expense")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ReceiptSection(
                vm = vm,
                onCamera = openCamera,
                onGallery = openGallery,
                onOpenFull = { showFullReceipt = true },
            )

            OutlinedTextField(
                value = vm.amountText,
                onValueChange = { vm.amountText = it },
                label = { Text("Amount") },
                prefix = { Text("₹ ") },
                singleLine = true,
                isError = vm.amountText.isNotBlank() && vm.amountPaise == null,
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )

            OutlinedTextField(
                value = vm.title,
                onValueChange = { vm.title = it },
                label = { Text("What was it for? (optional)") },
                placeholder = { Text("Dinner at the beach shack") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            Section("Category") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { cat ->
                        FilterChip(
                            selected = vm.category == cat,
                            onClick = { vm.category = cat },
                            label = { Text("${cat.emoji} ${cat.label}") },
                        )
                    }
                }
            }

            Section("Paid by") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.members.forEach { m ->
                        FilterChip(
                            selected = vm.paidById == m.id,
                            onClick = { vm.paidById = m.id },
                            label = { Text(m.name) },
                        )
                    }
                }
            }

            Section("Paid with") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethod.entries.forEach { m ->
                        FilterChip(
                            selected = vm.paymentMethod == m,
                            onClick = { vm.paymentMethod = m },
                            label = { Text("${m.emoji} ${m.label}") },
                        )
                    }
                }
            }

            if (vm.legs.isNotEmpty()) {
                Section("Route leg (optional)") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.legs.forEach { leg ->
                            FilterChip(
                                selected = vm.legId == leg.id,
                                onClick = { vm.legId = if (vm.legId == leg.id) null else leg.id },
                                label = { Text("${leg.fromName} → ${leg.toName}") },
                            )
                        }
                    }
                }
            }

            SplitSection(vm)

            Button(
                onClick = { vm.save(onDone) },
                enabled = vm.canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (vm.isEdit) "Save changes" else "Save expense") }
        }
    }

    if (showFullReceipt) {
        vm.receiptPreview?.let { bmp ->
            Dialog(onDismissRequest = { showFullReceipt = false }) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Receipt photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showFullReceipt = false },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this expense?") },
            text = { Text("Balances will be recalculated.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onDone)
                }) { Text("Delete", color = BalanceColors.negative) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReceiptSection(
    vm: AddExpenseViewModel,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onOpenFull: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val preview = vm.receiptPreview
            when {
                vm.isScanning -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Reading your receipt…")
                }
                preview != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "Receipt photo, tap to enlarge",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenFull),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Receipt attached", fontWeight = FontWeight.Medium)
                        Text(
                            "Stored only on this phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = vm::removeReceipt) { Text("Remove") }
                }
                else -> {
                    Text("🧾 Scan a bill to fill this in automatically", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Reads the total, shop name and category on your phone. Works offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!vm.isScanning) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onCamera) {
                        Text(if (preview == null) "📷  Take photo" else "📷  Rescan")
                    }
                    OutlinedButton(onClick = onGallery) { Text("🖼️  Gallery") }
                }
            }
        }
    }
}

@Composable
private fun SplitSection(vm: AddExpenseViewModel) {
    Section(
        title = "Split",
        trailing = {
            if (vm.splitMode == SplitMode.EQUAL && vm.splitAmong.size < vm.members.size) {
                TextButton(onClick = vm::selectAll) { Text("Everyone") }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SplitMode.entries.forEach { mode ->
                FilterChip(
                    selected = vm.splitMode == mode,
                    onClick = { vm.splitMode = mode },
                    label = { Text(mode.label) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        when (vm.splitMode) {
            SplitMode.EQUAL -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.members.forEach { m ->
                    FilterChip(
                        selected = m.id in vm.splitAmong,
                        onClick = { vm.toggleSplit(m.id) },
                        label = { Text(m.name) },
                    )
                }
            }

            SplitMode.EXACT -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.members.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, Modifier.weight(1f))
                        OutlinedTextField(
                            value = vm.exactAmounts[m.id].orEmpty(),
                            onValueChange = { vm.setExact(m.id, it) },
                            prefix = { Text("₹ ") },
                            placeholder = { Text("0") },
                            singleLine = true,
                            modifier = Modifier.width(140.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
            }

            SplitMode.SHARES -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val shares = vm.computedShares
                vm.members.forEach { m ->
                    val w = vm.weights[m.id] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name)
                            shares?.get(m.id)?.let {
                                Text(
                                    Money.format(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        FilledTonalIconButton(onClick = { vm.changeWeight(m.id, -1) }, enabled = w > 0) {
                            Text("−", fontSize = 20.sp)
                        }
                        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text("$w", style = MaterialTheme.typography.titleMedium)
                        }
                        FilledTonalIconButton(onClick = { vm.changeWeight(m.id, +1) }) {
                            Text("+", fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        vm.splitHint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (vm.splitIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            trailing()
        }
        content()
    }
}
