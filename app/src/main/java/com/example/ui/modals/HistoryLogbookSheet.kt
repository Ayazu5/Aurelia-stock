package com.example.ui.modals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.StockHistoryEntry
import com.example.data.model.UiState
import com.example.ui.components.ThumbnailImageBox
import com.example.ui.theme.AureliaTheme
import com.example.ui.theme.TabularNumberStyle
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HistoryLogbookSheet(
    isOpen: Boolean,
    historyState: UiState<List<StockHistoryEntry>>,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val colors = AureliaTheme.colors
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    BackHandler(enabled = true) {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .statusBarsPadding()
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .widthIn(max = 620.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.hairlineBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.champagneContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = colors.champagneBright,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.drawer_history_logbook),
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Immutable IN / OUT cloud audit trail",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceCard)
                            .testTag("history_sheet_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Stock History Logbook",
                            tint = colors.textSecondary
                        )
                    }
                }

                when (historyState) {
                    UiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = colors.champagneBright)
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = historyState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.rose
                            )
                        }
                    }

                    is UiState.Success -> {
                        val entries = historyState.data
                        if (entries.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.history_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(entries, key = { it.id }) { entry ->
                                    val isIn = entry.type == "IN"
                                    val accent = if (isIn) colors.emerald else colors.rose
                                    val timeLabel = entry.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Just now"

                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = colors.surfaceCard,
                                        border = BorderStroke(1.dp, colors.hairlineBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            ThumbnailImageBox(
                                                thumbnailBase64 = entry.thumbnailBase64,
                                                contentDescription = entry.itemName,
                                                modifier = Modifier.size(46.dp),
                                                cornerRadius = 12.dp
                                            )

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.itemName,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = colors.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(3.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = timeLabel,
                                                        style = TabularNumberStyle.copy(fontSize = 11.sp),
                                                        color = colors.textMuted
                                                    )
                                                    Text("•", color = colors.textMuted, style = MaterialTheme.typography.labelSmall)
                                                    Text(
                                                        text = "Bal: ${entry.resultingStock}",
                                                        style = TabularNumberStyle.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        ),
                                                        color = colors.textSecondary
                                                    )
                                                    if (entry.offlineQueued) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = colors.amberContainer
                                                        ) {
                                                            Text(
                                                                text = "SYNCED",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = colors.amber,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = accent.copy(alpha = 0.14f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isIn) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                                                        contentDescription = null,
                                                        tint = accent,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "${if (isIn) "+" else "-"}${entry.quantity}",
                                                        style = TabularNumberStyle.copy(
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        ),
                                                        color = accent
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
