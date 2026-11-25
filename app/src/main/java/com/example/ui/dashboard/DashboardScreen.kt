package com.example.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.HandSide
import com.example.data.model.InventoryItem
import com.example.data.model.ScanMode
import com.example.data.model.StockFilter
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.data.model.ViewDensity
import com.example.ui.components.OcrTextChip
import com.example.ui.components.PulsingNeonDot
import com.example.ui.components.ThumbnailImageBox
import com.example.ui.theme.AureliaTheme
import com.example.ui.theme.Champagne400
import com.example.ui.theme.Champagne500
import com.example.ui.theme.Champagne600
import com.example.ui.theme.Emerald600
import com.example.ui.theme.EmeraldNeon400
import com.example.ui.theme.EmeraldNeon500
import com.example.ui.theme.Obsidian950
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Rose500
import com.example.ui.theme.Rose600
import com.example.ui.theme.TabularNumberStyle

@Composable
fun DashboardScreen(
    userDisplayName: String,
    userEmail: String,
    inventoryState: UiState<List<InventoryItem>>,
    preferences: UserPreferences,
    isEffectivelyOffline: Boolean,
    offlineQueueCount: Int,
    isSyncingQueue: Boolean,
    searchQuery: String,
    activeFilter: StockFilter,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelect: (StockFilter) -> Unit,
    onToggleDensity: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenScan: (ScanMode) -> Unit,
    onQuickAdjust: (InventoryItem, Long) -> Unit,
    onEditItem: (InventoryItem) -> Unit
) {
    val colors = AureliaTheme.colors
    val allItems = (inventoryState as? UiState.Success)?.data.orEmpty()

    val totalUnits by remember(allItems) {
        derivedStateOf { allItems.sumOf { it.quantity } }
    }
    val lowStockCount by remember(allItems) {
        derivedStateOf { allItems.count { it.isLowStock() } }
    }
    val categories by remember(allItems) {
        derivedStateOf {
            allItems.map { it.category.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }
    }
    val filteredItems by remember(allItems, searchQuery, activeFilter) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            allItems.filter { item ->
                val matchesFilter = when (activeFilter) {
                    StockFilter.All -> true
                    StockFilter.LowStock -> item.isLowStock()
                    StockFilter.InStock -> !item.isLowStock()
                    is StockFilter.Category -> item.category.equals(activeFilter.name, ignoreCase = true)
                }
                val matchesSearch = q.isEmpty() ||
                    item.name.lowercase().contains(q) ||
                    item.extractedText.lowercase().contains(q) ||
                    item.designDetails.lowercase().contains(q) ||
                    item.category.lowercase().contains(q)
                matchesFilter && matchesSearch
            }
        }
    }

    val isLeftHanded = preferences.handSide == HandSide.LEFT

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
        ) {
            // Mirrored Glassmorphic Top App Bar
            DashboardTopBar(
                userDisplayName = userDisplayName,
                userEmail = userEmail,
                isLeftHanded = isLeftHanded,
                isEffectivelyOffline = isEffectivelyOffline,
                onOpenDrawer = onOpenDrawer
            )

            // Main scrollable dashboard body
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 124.dp // room for ergonomic floating bottom dock
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Offline / Queue Status Banner
                item(key = "offline_banner") {
                    AnimatedVisibility(visible = isEffectivelyOffline || offlineQueueCount > 0 || isSyncingQueue) {
                        OfflineQueueBanner(
                            isOffline = isEffectivelyOffline,
                            queueCount = offlineQueueCount,
                            isSyncing = isSyncingQueue
                        )
                    }
                }

                // 2 KPI Bento Cards
                item(key = "kpi_bento") {
                    KpiBentoRow(
                        totalUnits = totalUnits,
                        skuCount = allItems.size,
                        lowStockCount = lowStockCount,
                        isLowFilterActive = activeFilter == StockFilter.LowStock,
                        onTotalClick = { onFilterSelect(StockFilter.All) },
                        onLowStockClick = {
                            onFilterSelect(
                                if (activeFilter == StockFilter.LowStock) StockFilter.All
                                else StockFilter.LowStock
                            )
                        }
                    )
                }

                // Search Bar + List/Grid Density Toggle
                item(key = "search_bar") {
                    SearchAndDensityBar(
                        query = searchQuery,
                        density = preferences.viewDensity,
                        onQueryChange = onSearchQueryChange,
                        onToggleDensity = onToggleDensity
                    )
                }

                // Segmented Filter Pills
                item(key = "filter_pills") {
                    FilterPillsRow(
                        activeFilter = activeFilter,
                        lowStockCount = lowStockCount,
                        categories = categories,
                        onSelect = onFilterSelect
                    )
                }

                // Section Heading + Audit Log quick trigger
                item(key = "section_heading") {
                    val heading = when (activeFilter) {
                        StockFilter.All -> stringResource(R.string.heading_all_inventory)
                        StockFilter.LowStock -> stringResource(R.string.heading_low_stock)
                        StockFilter.InStock -> stringResource(R.string.heading_in_stock)
                        is StockFilter.Category -> activeFilter.name
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = heading,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary
                            )
                            Surface(
                                shape = CircleShape,
                                color = colors.surfaceSubtle,
                                border = BorderStroke(1.dp, colors.hairlineBorder)
                            ) {
                                Text(
                                    text = "${filteredItems.size}",
                                    style = TabularNumberStyle.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = onOpenHistory,
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surfaceCard,
                            border = BorderStroke(1.dp, colors.hairlineBorder),
                            modifier = Modifier.testTag("dashboard_audit_log_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = "Open Audit Log",
                                    tint = colors.champagneBright,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.action_audit_log),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // Inventory Content States
                when (inventoryState) {
                    UiState.Loading -> {
                        item(key = "loading_state") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = colors.champagneBright,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = "Syncing isolated cloud ledger…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    is UiState.Error -> {
                        item(key = "error_state") {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = colors.roseContainer,
                                border = BorderStroke(1.dp, colors.rose.copy(alpha = 0.40f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Cloud Sync Issue",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.rose
                                    )
                                    Text(
                                        text = inventoryState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    is UiState.Success -> {
                        if (filteredItems.isEmpty()) {
                            item(key = "empty_state") {
                                EmptyInventoryCard(
                                    hasSearchOrFilter = searchQuery.isNotBlank() || activeFilter != StockFilter.All,
                                    onClearFilters = {
                                        onSearchQueryChange("")
                                        onFilterSelect(StockFilter.All)
                                    }
                                )
                            }
                        } else if (preferences.viewDensity == ViewDensity.LIST) {
                            items(
                                items = filteredItems,
                                key = { it.id }
                            ) { item ->
                                InventoryListCard(
                                    item = item,
                                    onAdjust = { delta -> onQuickAdjust(item, delta) },
                                    onEdit = { onEditItem(item) }
                                )
                            }
                        } else {
                            val pairs = filteredItems.chunked(2)
                            items(
                                items = pairs,
                                key = { row -> row.joinToString("_") { it.id } }
                            ) { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    pair.forEach { item ->
                                        InventoryGridCard(
                                            item = item,
                                            onAdjust = { delta -> onQuickAdjust(item, delta) },
                                            onEdit = { onEditItem(item) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (pair.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ergonomic Mirrored Floating Bottom Action Dock (Thumb-Zone)
        FloatingStockActionDock(
            isLeftHanded = isLeftHanded,
            onStockIn = { onOpenScan(ScanMode.IN) },
            onStockOut = { onOpenScan(ScanMode.OUT) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 560.dp)
        )
    }
}

@Composable
private fun DashboardTopBar(
    userDisplayName: String,
    userEmail: String,
    isLeftHanded: Boolean,
    isEffectivelyOffline: Boolean,
    onOpenDrawer: () -> Unit
) {
    val colors = AureliaTheme.colors
    val initials = remember(userDisplayName, userEmail) {
        val src = userDisplayName.ifBlank { userEmail.substringBefore("@") }
        src.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "AS" }
    }

    val profileButton: @Composable () -> Unit = {
        Surface(
            onClick = onOpenDrawer,
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceCard,
            border = BorderStroke(1.dp, colors.hairlineBorder),
            modifier = Modifier.testTag("profile_drawer_trigger")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Champagne400, Champagne600))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Obsidian950
                    )
                }
                Column(modifier = Modifier.widthIn(max = 128.dp)) {
                    Text(
                        text = userDisplayName.ifBlank { "Executive" },
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isLeftHanded) "Left-Hand Mode" else "Right-Hand Mode",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.champagneBright,
                        maxLines = 1
                    )
                }
            }
        }
    }

    val statusPills: @Composable () -> Unit = {
        Column(
            horizontalAlignment = if (isLeftHanded) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // AI Agent Readiness Pill
            Surface(
                shape = CircleShape,
                color = if (isEffectivelyOffline) colors.surfaceSubtle else colors.emeraldContainer,
                border = BorderStroke(
                    1.dp,
                    if (isEffectivelyOffline) colors.hairlineBorder else colors.emerald.copy(alpha = 0.30f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    PulsingNeonDot(
                        color = if (isEffectivelyOffline) colors.textMuted else EmeraldNeon400,
                        animate = !isEffectivelyOffline,
                        size = 6.dp
                    )
                    Text(
                        text = stringResource(
                            if (isEffectivelyOffline) R.string.status_ai_paused else R.string.status_ai_ready
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isEffectivelyOffline) colors.textMuted else colors.emerald
                    )
                }
            }

            // Network Sync Status Pill
            Surface(
                shape = CircleShape,
                color = if (isEffectivelyOffline) colors.amberContainer else colors.surfaceCard,
                border = BorderStroke(
                    1.dp,
                    if (isEffectivelyOffline) colors.amber.copy(alpha = 0.35f) else colors.hairlineBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isEffectivelyOffline) colors.amber else EmeraldNeon500)
                    )
                    Text(
                        text = stringResource(
                            if (isEffectivelyOffline) R.string.status_offline_manual else R.string.status_online
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isEffectivelyOffline) colors.amber else colors.textSecondary
                    )
                }
            }
        }
    }

    Surface(
        color = colors.glassSurface,
        border = BorderStroke(0.5.dp, colors.hairlineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLeftHanded) {
                profileButton()
                statusPills()
            } else {
                statusPills()
                profileButton()
            }
        }
    }
}

@Composable
private fun OfflineQueueBanner(
    isOffline: Boolean,
    queueCount: Int,
    isSyncing: Boolean
) {
    val colors = AureliaTheme.colors
    val accent = if (isSyncing) colors.emerald else colors.amber
    val bg = if (isSyncing) colors.emeraldContainer else colors.amberContainer

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isSyncing) Icons.Rounded.Sync else Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = when {
                    isSyncing -> "Reconnected — syncing $queueCount queued update(s) to Firestore…"
                    isOffline && queueCount > 0 -> "Offline · Manual: AI paused. $queueCount update(s) queued for auto-sync."
                    isOffline -> stringResource(R.string.offline_banner_text)
                    else -> "$queueCount queued offline entry awaiting cloud sync."
                },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiBentoRow(
    totalUnits: Long,
    skuCount: Int,
    lowStockCount: Int,
    isLowFilterActive: Boolean,
    onTotalClick: () -> Unit,
    onLowStockClick: () -> Unit
) {
    val colors = AureliaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Total Stock Bento Card
        Surface(
            onClick = onTotalClick,
            shape = RoundedCornerShape(22.dp),
            color = colors.surfaceCard,
            border = BorderStroke(1.dp, colors.hairlineBorder),
            modifier = Modifier
                .weight(1f)
                .testTag("kpi_total_stock_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.kpi_total_stock),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.champagneContainer
                    ) {
                        Text(
                            text = "$skuCount SKUs",
                            style = TabularNumberStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.champagneBright,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = totalUnits.toString(),
                    style = TabularNumberStyle.copy(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = colors.textPrimary
                )
            }
        }

        // Low Stock Alerts Bento Card
        val hasAlerts = lowStockCount > 0
        val alertAccent = if (hasAlerts) colors.rose else colors.emerald
        Surface(
            onClick = onLowStockClick,
            shape = RoundedCornerShape(22.dp),
            color = if (isLowFilterActive) colors.roseContainer else colors.surfaceCard,
            border = BorderStroke(
                1.dp,
                if (isLowFilterActive || hasAlerts) alertAccent.copy(alpha = 0.35f) else colors.hairlineBorder
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("kpi_low_stock_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.kpi_low_stock_alerts),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = alertAccent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "$lowStockCount Alerts",
                            style = TabularNumberStyle.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = alertAccent,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = lowStockCount.toString(),
                    style = TabularNumberStyle.copy(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = if (hasAlerts) colors.rose else colors.emerald
                )
            }
        }
    }
}

@Composable
private fun SearchAndDensityBar(
    query: String,
    density: ViewDensity,
    onQueryChange: (String) -> Unit,
    onToggleDensity: () -> Unit
) {
    val colors = AureliaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceCard,
            border = BorderStroke(1.dp, colors.hairlineBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.champagneBright),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_search_input")
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Clear search",
                            tint = colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Density Toggle Button (48x48dp touch target)
        Surface(
            onClick = onToggleDensity,
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceCard,
            border = BorderStroke(1.dp, colors.hairlineBorder),
            modifier = Modifier
                .size(48.dp)
                .testTag("density_toggle_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (density == ViewDensity.LIST) Icons.Rounded.GridView
                    else Icons.AutoMirrored.Rounded.FormatListBulleted,
                    contentDescription = if (density == ViewDensity.LIST) "Switch to Grid view" else "Switch to List view",
                    tint = colors.champagneBright,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
private fun FilterPillsRow(
    activeFilter: StockFilter,
    lowStockCount: Int,
    categories: List<String>,
    onSelect: (StockFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "f_all") {
            FilterPill(
                label = "All Items",
                selected = activeFilter == StockFilter.All,
                onClick = { onSelect(StockFilter.All) },
                testTag = "filter_pill_all"
            )
        }
        item(key = "f_low") {
            FilterPill(
                label = "Low Stock ($lowStockCount)",
                selected = activeFilter == StockFilter.LowStock,
                accentRose = lowStockCount > 0,
                onClick = { onSelect(StockFilter.LowStock) },
                testTag = "filter_pill_low"
            )
        }
        item(key = "f_in") {
            FilterPill(
                label = "In Stock",
                selected = activeFilter == StockFilter.InStock,
                onClick = { onSelect(StockFilter.InStock) },
                testTag = "filter_pill_in"
            )
        }
        items(categories, key = { "cat_$it" }) { cat ->
            FilterPill(
                label = cat,
                selected = activeFilter == StockFilter.Category(cat),
                onClick = { onSelect(StockFilter.Category(cat)) },
                testTag = "filter_pill_cat_$cat"
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    accentRose: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = AureliaTheme.colors
    val bg = when {
        selected && accentRose -> colors.rose
        selected -> colors.champagne
        else -> colors.surfaceCard
    }
    val fg = when {
        selected && accentRose -> PureWhite
        selected -> Obsidian950
        else -> colors.textSecondary
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        border = if (selected) null else BorderStroke(1.dp, colors.hairlineBorder),
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = fg,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun InventoryListCard(
    item: InventoryItem,
    onAdjust: (Long) -> Unit,
    onEdit: () -> Unit
) {
    val colors = AureliaTheme.colors
    val isLow = item.isLowStock()

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceCard,
        border = BorderStroke(
            1.dp,
            if (isLow) colors.rose.copy(alpha = 0.32f) else colors.hairlineBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("inventory_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                ThumbnailImageBox(
                    thumbnailBase64 = item.thumbnailBase64,
                    contentDescription = "${item.name} thumbnail",
                    modifier = Modifier.size(64.dp),
                    cornerRadius = 14.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLow) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.roseContainer
                            ) {
                                Text(
                                    text = "LOW",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.rose,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.champagneBright
                        )
                        Text("•", color = colors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = "Alert ≤ ${item.threshold}",
                            style = TabularNumberStyle.copy(fontSize = 11.sp),
                            color = colors.textMuted
                        )
                    }

                    if (item.extractedText.isNotBlank()) {
                        OcrTextChip(text = item.extractedText)
                    }

                    if (item.designDetails.isNotBlank()) {
                        Text(
                            text = item.designDetails,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Footer: Stock count + inline +/- quick adjust + Edit Variant button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceSubtle)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.quantity.toString(),
                        style = TabularNumberStyle.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = if (isLow) colors.rose else colors.textPrimary
                    )
                    Text(
                        text = "in stock",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { onAdjust(-1L) },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceCard)
                            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(12.dp))
                            .testTag("quick_minus_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Decrease ${item.name} by 1",
                            tint = colors.rose,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { onAdjust(1L) },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceCard)
                            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(12.dp))
                            .testTag("quick_plus_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Increase ${item.name} by 1",
                            tint = colors.emerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.champagneContainer)
                            .testTag("edit_item_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.action_edit_variant),
                            tint = colors.champagneBright,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryGridCard(
    item: InventoryItem,
    onAdjust: (Long) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AureliaTheme.colors
    val isLow = item.isLowStock()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceCard,
        border = BorderStroke(
            1.dp,
            if (isLow) colors.rose.copy(alpha = 0.35f) else colors.hairlineBorder
        ),
        modifier = modifier
            .clickable(onClick = onEdit)
            .testTag("inventory_grid_card_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            ) {
                ThumbnailImageBox(
                    thumbnailBase64 = item.thumbnailBase64,
                    contentDescription = "${item.name} thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 14.dp
                )
                if (isLow) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Rose600,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "LOW",
                            style = MaterialTheme.typography.labelSmall,
                            color = PureWhite,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.extractedText.isNotBlank()) {
                OcrTextChip(text = item.extractedText)
            } else {
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.champagneBright,
                    maxLines = 1
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.quantity}",
                    style = TabularNumberStyle.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = if (isLow) colors.rose else colors.textPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { onAdjust(-1L) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceSubtle)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Decrease ${item.name}",
                            tint = colors.rose,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onAdjust(1L) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceSubtle)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Increase ${item.name}",
                            tint = colors.emerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInventoryCard(
    hasSearchOrFilter: Boolean,
    onClearFilters: () -> Unit
) {
    val colors = AureliaTheme.colors
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.hairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.champagneContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasSearchOrFilter) Icons.Rounded.WarningAmber else Icons.Rounded.Inventory2,
                    contentDescription = null,
                    tint = colors.champagneBright,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = stringResource(R.string.empty_inventory_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasSearchOrFilter) {
                    "No SKU variants match the active search or filter."
                } else {
                    stringResource(R.string.empty_inventory_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            if (hasSearchOrFilter) {
                Surface(
                    onClick = onClearFilters,
                    shape = CircleShape,
                    color = colors.champagneContainer,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(
                        text = "Reset Filters",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.champagneBright,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingStockActionDock(
    isLeftHanded: Boolean,
    onStockIn: () -> Unit,
    onStockOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AureliaTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.background.copy(alpha = 0.92f),
                        colors.background
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.hairlineBorder),
            shadowElevation = 14.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val stockInButton: @Composable (Modifier) -> Unit = { mod ->
                    Box(
                        modifier = mod
                            .height(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Emerald600, EmeraldNeon500)
                                )
                            )
                            .clickable(onClick = onStockIn)
                            .testTag("dock_stock_in_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = null,
                                tint = Obsidian950,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.action_stock_in),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                ),
                                color = Obsidian950
                            )
                        }
                    }
                }

                val stockOutButton: @Composable (Modifier) -> Unit = { mod ->
                    Box(
                        modifier = mod
                            .height(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.roseContainer)
                            .border(1.dp, Rose500.copy(alpha = 0.40f), RoundedCornerShape(18.dp))
                            .clickable(onClick = onStockOut)
                            .testTag("dock_stock_out_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = null,
                                tint = colors.rose,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.action_stock_out),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                ),
                                color = colors.rose
                            )
                        }
                    }
                }

                // In Left-Handed mode, primary Stock IN sits right under the left thumb;
                // in Right-Handed mode, primary Stock IN mirrors to the right thumb!
                if (isLeftHanded) {
                    stockInButton(Modifier.weight(1.15f))
                    stockOutButton(Modifier.weight(1f))
                } else {
                    stockOutButton(Modifier.weight(1f))
                    stockInButton(Modifier.weight(1.15f))
                }
            }
        }
    }
}
