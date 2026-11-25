package com.example.ui.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.HandSide
import com.example.data.model.PhotoQuality
import com.example.data.model.UserPreferences
import com.example.ui.components.openWhatsAppConcierge
import com.example.ui.theme.AureliaTheme
import com.example.ui.theme.Champagne400
import com.example.ui.theme.Champagne600
import com.example.ui.theme.Obsidian950
import com.example.ui.theme.TabularNumberStyle

@Composable
fun ProfileSettingsDrawer(
    isOpen: Boolean,
    uid: String,
    displayName: String,
    email: String,
    preferences: UserPreferences,
    historyCount: Int,
    onDismiss: () -> Unit,
    onHandSideChange: (HandSide) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onUiScaleChange: (Int) -> Unit,
    onPhotoQualityChange: (PhotoQuality) -> Unit,
    onSimulatedOfflineChange: (Boolean) -> Unit,
    onOpenHistory: () -> Unit,
    onSignOut: () -> Unit
) {
    val colors = AureliaTheme.colors
    val context = LocalContext.current
    val slideFromLeft = preferences.handSide == HandSide.LEFT

    BackHandler(enabled = isOpen) {
        onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(initialOffsetX = { if (slideFromLeft) -it else it }),
            exit = slideOutHorizontally(targetOffsetX = { if (slideFromLeft) -it else it }),
            modifier = Modifier.align(if (slideFromLeft) Alignment.CenterStart else Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(326.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.hairlineBorder),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Header: Profile Card & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        val initials = remember(displayName, email) {
                            displayName.ifBlank { email.substringBefore("@") }
                                .split(" ")
                                .filter { it.isNotBlank() }
                                .take(2)
                                .map { it.first().uppercaseChar() }
                                .joinToString("")
                                .ifBlank { "AS" }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Champagne400, Champagne600))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Obsidian950
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName.ifBlank { "Executive Member" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = email.ifBlank { "Google Account" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("drawer_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close Settings Drawer",
                                tint = colors.textSecondary
                            )
                        }
                    }

                    // Isolated Cloud UID Badge
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.emeraldContainer,
                        border = BorderStroke(1.dp, colors.emerald.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = colors.emerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "ISOLATED CLOUD WORKSPACE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.emerald
                                )
                                Text(
                                    text = "UID · ${uid.take(14)}…",
                                    style = TabularNumberStyle.copy(fontSize = 11.sp),
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = colors.hairlineBorder)

                    // Hand Orientation Ergonomics
                    DrawerSection(
                        title = stringResource(R.string.drawer_hand_orientation),
                        icon = Icons.Rounded.PanTool
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SegmentedChoiceChip(
                                label = stringResource(R.string.drawer_hand_left),
                                selected = preferences.handSide == HandSide.LEFT,
                                onClick = { onHandSideChange(HandSide.LEFT) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("drawer_hand_left_btn")
                            )
                            SegmentedChoiceChip(
                                label = stringResource(R.string.drawer_hand_right),
                                selected = preferences.handSide == HandSide.RIGHT,
                                onClick = { onHandSideChange(HandSide.RIGHT) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("drawer_hand_right_btn")
                            )
                        }
                    }

                    // Appearance Theme
                    DrawerSection(
                        title = stringResource(R.string.drawer_appearance),
                        icon = if (preferences.darkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SegmentedChoiceChip(
                                label = "Obsidian",
                                selected = preferences.darkMode,
                                onClick = { onDarkModeChange(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("drawer_theme_dark_btn")
                            )
                            SegmentedChoiceChip(
                                label = "Alabaster",
                                selected = !preferences.darkMode,
                                onClick = { onDarkModeChange(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("drawer_theme_light_btn")
                            )
                        }
                    }

                    // UI Resolution Scale
                    DrawerSection(title = stringResource(R.string.drawer_ui_scale)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(90, 100, 110, 120).forEach { scale ->
                                SegmentedChoiceChip(
                                    label = "$scale%",
                                    selected = preferences.uiScalePercent == scale,
                                    onClick = { onUiScaleChange(scale) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("drawer_scale_$scale")
                                )
                            }
                        }
                    }

                    // Photo Upload Quality
                    DrawerSection(
                        title = stringResource(R.string.drawer_photo_quality),
                        icon = Icons.Rounded.PhotoCamera
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PhotoQuality.entries.forEach { quality ->
                                SegmentedChoiceChip(
                                    label = quality.label,
                                    selected = preferences.photoQuality == quality,
                                    onClick = { onPhotoQualityChange(quality) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("drawer_quality_${quality.key}")
                                )
                            }
                        }
                    }

                    // Simulate Offline Mode Toggle
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceCard,
                        border = BorderStroke(
                            1.dp,
                            if (preferences.simulatedOffline) colors.amber.copy(alpha = 0.40f) else colors.hairlineBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudOff,
                                    contentDescription = null,
                                    tint = if (preferences.simulatedOffline) colors.amber else colors.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.drawer_simulate_offline),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = stringResource(R.string.drawer_simulate_offline_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted
                                    )
                                }
                            }
                            Switch(
                                checked = preferences.simulatedOffline,
                                onCheckedChange = onSimulatedOfflineChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Obsidian950,
                                    checkedTrackColor = colors.amber
                                ),
                                modifier = Modifier.testTag("drawer_simulate_offline_switch")
                            )
                        }
                    }

                    HorizontalDivider(color = colors.hairlineBorder)

                    // Stock History Logbook Action
                    Surface(
                        onClick = {
                            onDismiss()
                            onOpenHistory()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceCard,
                        border = BorderStroke(1.dp, colors.hairlineBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drawer_history_logbook_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = colors.champagneBright,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.drawer_history_logbook),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = colors.champagneContainer
                            ) {
                                Text(
                                    text = "$historyCount",
                                    style = TabularNumberStyle.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.champagneBright,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // WhatsApp Concierge Support
                    Surface(
                        onClick = { openWhatsAppConcierge(context) },
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceCard,
                        border = BorderStroke(1.dp, colors.whatsapp.copy(alpha = 0.30f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drawer_whatsapp_concierge_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.whatsapp.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Chat,
                                    contentDescription = "WhatsApp Concierge",
                                    tint = colors.whatsapp,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.whatsapp_concierge_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.whatsapp_concierge_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Secure Logout
                    Surface(
                        onClick = {
                            onDismiss()
                            onSignOut()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = colors.roseContainer,
                        border = BorderStroke(1.dp, colors.rose.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("drawer_logout_button")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                                tint = colors.rose,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.drawer_secure_logout),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.rose
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable () -> Unit
) {
    val colors = AureliaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.champagneBright,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                color = colors.textMuted
            )
        }
        content()
    }
}

@Composable
private fun SegmentedChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AureliaTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.champagne else colors.surfaceCard,
        border = if (selected) null else BorderStroke(1.dp, colors.hairlineBorder),
        modifier = modifier.height(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                ),
                color = if (selected) Obsidian950 else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
