package com.example.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.InventoryItem
import com.example.data.model.ScanMode
import com.example.ui.components.OcrTextChip
import com.example.ui.components.ThumbnailImageBox
import com.example.ui.theme.AureliaTheme
import com.example.ui.theme.Champagne400
import com.example.ui.theme.Champagne500
import com.example.ui.theme.Emerald600
import com.example.ui.theme.EmeraldNeon400
import com.example.ui.theme.EmeraldNeon500
import com.example.ui.theme.Obsidian950
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose500
import com.example.ui.theme.TabularNumberStyle
import com.example.viewmodel.ScanSheetState
import kotlin.math.roundToInt

@Composable
fun ScanVisionSheet(
    state: ScanSheetState,
    inventoryItems: List<InventoryItem>,
    isEffectivelyOffline: Boolean,
    onDismiss: () -> Unit,
    onSetCameraActive: (Boolean) -> Unit,
    onPhotoCaptured: (android.graphics.Bitmap) -> Unit,
    onPhotoPicked: (android.net.Uri) -> Unit,
    onRetake: () -> Unit,
    onCameraError: (String) -> Unit,
    onSelectCandidate: (String) -> Unit,
    onConfirmUseSelectedCandidate: () -> Unit,
    onConfirmCreateNewSku: () -> Unit,
    onSelectManualOutItem: (InventoryItem) -> Unit,
    onUpdateForm: (
        name: String?,
        extractedText: String?,
        designDetails: String?,
        category: String?,
        quantity: Long?,
        threshold: Long?
    ) -> Unit,
    onSubmit: () -> Unit
) {
    if (!state.isOpen) return

    val colors = AureliaTheme.colors
    val context = LocalContext.current
    val isStockIn = state.mode == ScanMode.IN
    val itemMap = remember(inventoryItems) { inventoryItems.associateBy { it.id } }

    BackHandler(enabled = true) {
        if (state.isCameraActive) onSetCameraActive(false)
        else onDismiss()
    }

    // Runtime Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSetCameraActive(true)
        } else {
            onCameraError("Camera permission denied. Use Gallery / Storage or enable camera access in settings.")
        }
    }

    // Zero-permission Android Photo Picker (Google Play compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPhotoPicked(uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .statusBarsPadding()
            .imePadding()
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .padding(top = 16.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.hairlineBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Sheet Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Surface(
                            shape = CircleShape,
                            color = if (isStockIn) colors.emeraldContainer else colors.roseContainer
                        ) {
                            Text(
                                text = if (isStockIn) "STOCK IN · VISION AI" else "STOCK OUT · DEDUCT",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp),
                                color = if (isStockIn) colors.emerald else colors.rose,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isStockIn) "Scan or Log Variant Intake" else "Identify Variant to Deduct",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceCard)
                            .testTag("scan_sheet_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Vision Scan Sheet",
                            tint = colors.textSecondary
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Camera / Photo Preview Zone
                    when {
                        state.isCameraActive -> {
                            CameraPreviewView(
                                onImageCaptured = onPhotoCaptured,
                                onCameraError = { msg ->
                                    onSetCameraActive(false)
                                    onCameraError(msg)
                                },
                                onCancel = { onSetCameraActive(false) }
                            )
                        }

                        state.capturedBitmap != null -> {
                            ScannedPhotoPreview(
                                bitmap = state.capturedBitmap,
                                isAnalyzing = state.isAnalyzing,
                                onRetake = onRetake
                            )
                        }

                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) onSetCameraActive(true)
                                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    color = colors.surfaceCard,
                                    border = BorderStroke(1.dp, colors.champagne.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(84.dp)
                                        .testTag("scan_live_camera_button")
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PhotoCamera,
                                            contentDescription = null,
                                            tint = colors.champagneBright,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.scan_live_camera),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.textPrimary
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    color = colors.surfaceCard,
                                    border = BorderStroke(1.dp, colors.hairlineBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(84.dp)
                                        .testTag("scan_gallery_button")
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PhotoLibrary,
                                            contentDescription = null,
                                            tint = colors.emerald,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.scan_gallery_storage),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. AI Scanning Progress Card
                    AnimatedVisibility(visible = state.isAnalyzing) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.emeraldContainer,
                            border = BorderStroke(1.dp, colors.emerald.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = colors.emerald,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.scan_ai_analyzing_title),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = stringResource(R.string.scan_ai_analyzing_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    // 3. AI Error Banner (surfaces real failure transparently)
                    if (!state.aiError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.roseContainer,
                            border = BorderStroke(1.dp, colors.rose.copy(alpha = 0.40f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = colors.rose,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = state.aiError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }

                    // 4. AI Disambiguation Card (Prevents blind merging of similar variants)
                    val analysis = state.analysis
                    if (state.showDisambiguation && analysis != null && analysis.similarCandidates.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = colors.amberContainer,
                            border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = colors.amber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.scan_disambig_title),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.textPrimary
                                    )
                                }

                                if (analysis.confidenceNote.isNotBlank()) {
                                    Text(
                                        text = analysis.confidenceNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }

                                analysis.similarCandidates.forEach { cand ->
                                    val candidateItem = itemMap[cand.id] ?: return@forEach
                                    val selected = state.selectedCandidateId == cand.id
                                    Surface(
                                        onClick = { onSelectCandidate(cand.id) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) colors.surfaceElevated else colors.surfaceCard,
                                        border = BorderStroke(
                                            width = if (selected) 1.5.dp else 1.dp,
                                            color = if (selected) colors.champagneBright else colors.hairlineBorder
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("disambig_candidate_${cand.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            ThumbnailImageBox(
                                                thumbnailBase64 = candidateItem.thumbnailBase64,
                                                contentDescription = candidateItem.name,
                                                modifier = Modifier.size(46.dp),
                                                cornerRadius = 10.dp
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = candidateItem.name,
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = colors.textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = "${(cand.similarity * 100).roundToInt()}% · ${candidateItem.quantity} qty",
                                                        style = TabularNumberStyle.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = colors.champagneBright
                                                    )
                                                }
                                                if (candidateItem.extractedText.isNotBlank()) {
                                                    Spacer(Modifier.height(3.dp))
                                                    OcrTextChip(text = candidateItem.extractedText)
                                                }
                                                if (cand.reason.isNotBlank()) {
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        text = cand.reason,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.textSecondary,
                                                        maxLines = 2
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onConfirmUseSelectedCandidate,
                                        enabled = state.selectedCandidateId != null,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Champagne500,
                                            contentColor = Obsidian950
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .testTag("disambig_use_selected_btn")
                                    ) {
                                        Text(
                                            text = stringResource(
                                                if (isStockIn) R.string.scan_btn_add_selected
                                                else R.string.scan_btn_use_selected_out
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1
                                        )
                                    }
                                    if (isStockIn) {
                                        OutlinedButton(
                                            onClick = onConfirmCreateNewSku,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, colors.hairlineBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                                .testTag("disambig_create_new_btn")
                                        ) {
                                            Text(
                                                text = stringResource(R.string.scan_btn_list_new),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = colors.textPrimary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (analysis != null && !state.isAnalyzing) {
                        // Resolved AI Analysis Banner
                        val matchedItem = state.matchedItemId?.let { itemMap[it] }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.emeraldContainer,
                            border = BorderStroke(1.dp, colors.emerald.copy(alpha = 0.30f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (matchedItem != null) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.emerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (matchedItem != null) {
                                            "Matched Saved SKU (${(analysis.confidence * 100).roundToInt()}%) · ${matchedItem.quantity} in stock"
                                        } else {
                                            "New Variant Identified (${(analysis.confidence * 100).roundToInt()}%)"
                                        },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.textPrimary
                                    )
                                    if (analysis.confidenceNote.isNotBlank()) {
                                        Text(
                                            text = analysis.confidenceNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Stock OUT Quick Visual Selector Strip ("Help · Select Individual Variant")
                    if (!isStockIn && inventoryItems.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "HELP · SELECT INDIVIDUAL VARIANT",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                                color = colors.textMuted
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(inventoryItems, key = { "out_${it.id}" }) { item ->
                                    val selected = state.matchedItemId == item.id
                                    Surface(
                                        onClick = { onSelectManualOutItem(item) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) colors.champagneContainer else colors.surfaceCard,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) colors.champagneBright else colors.hairlineBorder
                                        ),
                                        modifier = Modifier
                                            .width(156.dp)
                                            .testTag("stock_out_pick_${item.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ThumbnailImageBox(
                                                thumbnailBase64 = item.thumbnailBase64,
                                                contentDescription = item.name,
                                                modifier = Modifier.size(38.dp),
                                                cornerRadius = 8.dp
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = colors.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${item.quantity} avail",
                                                    style = TabularNumberStyle.copy(fontSize = 10.sp),
                                                    color = if (item.isLowStock()) colors.rose else colors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 6. Always-Editable Variant Metadata Form
                    val textFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.champagneBright,
                        unfocusedBorderColor = colors.hairlineBorder,
                        focusedLabelColor = colors.champagneBright,
                        unfocusedLabelColor = colors.textMuted,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.champagneBright
                    )

                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { onUpdateForm(it, null, null, null, null, null) },
                        label = { Text("Variant / Product Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scan_name_input")
                    )

                    if (isStockIn) {
                        OutlinedTextField(
                            value = state.extractedText,
                            onValueChange = { onUpdateForm(null, it, null, null, null, null) },
                            label = { Text("Embossed / Micro-OCR Text") },
                            placeholder = { Text("Stamped markings, codes, serials…") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_ocr_input")
                        )

                        OutlinedTextField(
                            value = state.designDetails,
                            onValueChange = { onUpdateForm(null, null, it, null, null, null) },
                            label = { Text("Design, Finish & Relief Details") },
                            maxLines = 2,
                            shape = RoundedCornerShape(14.dp),
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_design_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = state.category,
                                onValueChange = { onUpdateForm(null, null, null, it, null, null) },
                                label = { Text("Category") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = textFieldColors,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("scan_category_input")
                            )
                            OutlinedTextField(
                                value = state.threshold.toString(),
                                onValueChange = { raw ->
                                    val parsed = raw.filter { it.isDigit() }.toLongOrNull() ?: 0L
                                    onUpdateForm(null, null, null, null, null, parsed)
                                },
                                label = { Text("Low Alert") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                colors = textFieldColors,
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("scan_threshold_input")
                            )
                        }
                    }

                    // Quantity Stepper
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceCard,
                        border = BorderStroke(1.dp, colors.hairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isStockIn) "Quantity Adding" else "Quantity Deducting",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = if (isEffectivelyOffline) "Will queue locally for auto-sync" else "Updates cloud ledger instantly",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        onUpdateForm(null, null, null, null, (state.quantity - 1L).coerceAtLeast(1L), null)
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceSubtle)
                                        .testTag("scan_qty_minus")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Remove,
                                        contentDescription = "Decrease quantity",
                                        tint = colors.textPrimary
                                    )
                                }

                                Text(
                                    text = state.quantity.toString(),
                                    style = TabularNumberStyle.copy(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = colors.champagneBright,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )

                                IconButton(
                                    onClick = {
                                        onUpdateForm(null, null, null, null, state.quantity + 1L, null)
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceSubtle)
                                        .testTag("scan_qty_plus")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Increase quantity",
                                        tint = colors.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                }

                // Bottom Sticky Confirm Action
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = onSubmit,
                        enabled = !state.isAnalyzing && !state.isSubmitting,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStockIn) EmeraldNeon500 else Rose500,
                            contentColor = if (isStockIn) Obsidian950 else PureWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("scan_submit_button")
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                color = if (isStockIn) Obsidian950 else PureWhite,
                                strokeWidth = 2.4.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Saving…", style = MaterialTheme.typography.titleSmall)
                        } else {
                            Text(
                                text = stringResource(
                                    if (isStockIn) R.string.scan_confirm_in else R.string.scan_confirm_out
                                ),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannedPhotoPreview(
    bitmap: android.graphics.Bitmap,
    isAnalyzing: Boolean,
    onRetake: () -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val infinite = rememberInfiniteTransition(label = "laser_sweep")
    val laserProgress by infinite.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Obsidian950)
            .border(1.dp, EmeraldNeon400.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Captured product preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Animated Emerald & Champagne AI Laser Scanline
        if (isAnalyzing) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height * laserProgress
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            EmeraldNeon400.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        startY = (y - 28.dp.toPx()).coerceAtLeast(0f),
                        endY = (y + 28.dp.toPx()).coerceAtMost(size.height)
                    )
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, EmeraldNeon400, Champagne400, EmeraldNeon400, Color.Transparent)
                    ),
                    start = Offset(16f, y),
                    end = Offset(size.width - 16f, y),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // Retake Pill
        Surface(
            onClick = onRetake,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .testTag("scan_retake_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Retake photo",
                    tint = PureWhite,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.scan_retake),
                    style = MaterialTheme.typography.labelSmall,
                    color = PureWhite
                )
            }
        }
    }
}
