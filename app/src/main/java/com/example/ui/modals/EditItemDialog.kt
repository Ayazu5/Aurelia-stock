package com.example.ui.modals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.InventoryItem
import com.example.ui.components.ThumbnailImageBox
import com.example.ui.theme.AureliaTheme
import com.example.ui.theme.Champagne500
import com.example.ui.theme.Obsidian950
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose500

@Composable
fun EditItemDialog(
    item: InventoryItem?,
    onDismiss: () -> Unit,
    onSave: (
        item: InventoryItem,
        name: String,
        extractedText: String,
        designDetails: String,
        category: String,
        quantity: Long,
        threshold: Long
    ) -> Unit,
    onDelete: (InventoryItem) -> Unit
) {
    if (item == null) return

    val colors = AureliaTheme.colors
    var name by remember(item.id) { mutableStateOf(item.name) }
    var extractedText by remember(item.id) { mutableStateOf(item.extractedText) }
    var designDetails by remember(item.id) { mutableStateOf(item.designDetails) }
    var category by remember(item.id) { mutableStateOf(item.category) }
    var quantityText by remember(item.id) { mutableStateOf(item.quantity.toString()) }
    var thresholdText by remember(item.id) { mutableStateOf(item.threshold.toString()) }
    var confirmDelete by remember(item.id) { mutableStateOf(false) }

    BackHandler(enabled = true) {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .imePadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(26.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.hairlineBorder),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThumbnailImageBox(
                            thumbnailBase64 = item.thumbnailBase64,
                            contentDescription = item.name,
                            modifier = Modifier.size(46.dp),
                            cornerRadius = 12.dp
                        )
                        Text(
                            text = stringResource(R.string.edit_modal_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceCard)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Edit Variant dialog",
                            tint = colors.textSecondary
                        )
                    }
                }

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.champagneBright,
                    unfocusedBorderColor = colors.hairlineBorder,
                    focusedLabelColor = colors.champagneBright,
                    unfocusedLabelColor = colors.textMuted,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.champagneBright
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Variant Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_name_input")
                )

                OutlinedTextField(
                    value = extractedText,
                    onValueChange = { extractedText = it },
                    label = { Text("Embossed / OCR Text") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_ocr_input")
                )

                OutlinedTextField(
                    value = designDetails,
                    onValueChange = { designDetails = it },
                    label = { Text("Design & Relief Details") },
                    maxLines = 2,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_design_input")
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_category_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it.filter { c -> c.isDigit() } },
                        label = { Text("Quantity") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_quantity_input")
                    )
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it.filter { c -> c.isDigit() } },
                        label = { Text("Low Alert") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_threshold_input")
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (confirmDelete) {
                        Button(
                            onClick = { onDelete(item) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Rose500,
                                contentColor = PureWhite
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("edit_confirm_delete_btn")
                        ) {
                            Text("Confirm Delete", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.rose.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("edit_delete_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = colors.rose,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = colors.rose,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onSave(
                                item,
                                name,
                                extractedText,
                                designDetails,
                                category,
                                quantityText.toLongOrNull() ?: item.quantity,
                                thresholdText.toLongOrNull() ?: item.threshold
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Champagne500,
                            contentColor = Obsidian950
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(50.dp)
                            .testTag("edit_save_btn")
                    ) {
                        Text(
                            text = stringResource(R.string.action_save_changes),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
