package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.auth.attemptAutoSignIn
import com.example.data.auth.onGoogleSignInClicked
import com.example.ui.components.AureliaBrandMark
import com.example.ui.components.PulsingNeonDot
import com.example.ui.components.openWhatsAppConcierge
import com.example.ui.theme.AureliaTheme
import com.example.ui.theme.Champagne400
import com.example.ui.theme.Champagne500
import com.example.ui.theme.EmeraldNeon400
import com.example.ui.theme.Obsidian950

@Composable
fun AuthScreen(
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val colors = AureliaTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    // Attempt silent auto-sign-in once on launch while always keeping interactive button visible
    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* Stay on interactive AuthScreen */ },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Subtle luxury ambient glow accents
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Champagne500.copy(alpha = if (colors.isDark) 0.14f else 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Brand Bar + Theme Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AureliaBrandMark(size = 42.dp)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.brand_title),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.4.sp
                                ),
                                color = colors.textPrimary
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.champagneContainer,
                                border = BorderStroke(1.dp, colors.champagne.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = stringResource(R.string.brand_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.champagneBright,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.brand_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                }

                IconButton(
                    onClick = onToggleDarkMode,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceCard)
                        .border(1.dp, colors.hairlineBorder, CircleShape)
                        .testTag("auth_theme_toggle_button")
                ) {
                    Icon(
                        imageVector = if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = if (darkMode) "Switch to Alabaster Light Mode" else "Switch to Obsidian Dark Mode",
                        tint = colors.champagneBright
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // Hero Section
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    shape = CircleShape,
                    color = colors.emeraldContainer,
                    border = BorderStroke(1.dp, colors.emerald.copy(alpha = 0.30f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PulsingNeonDot(color = EmeraldNeon400, size = 7.dp)
                        Text(
                            text = stringResource(R.string.auth_eyebrow).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = colors.emerald
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.auth_headline),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.textPrimary
                )

                Text(
                    text = stringResource(R.string.auth_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary
                )
            }

            Spacer(Modifier.height(28.dp))

            // Value Pillars Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.hairlineBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AuthFeatureRow(
                        icon = Icons.Rounded.QrCodeScanner,
                        accent = colors.emerald,
                        title = "Gemini Vision Micro-OCR",
                        subtitle = "Reads embossed text & relief details to separate lookalike SKU variants."
                    )
                    AuthFeatureRow(
                        icon = Icons.Rounded.CloudDone,
                        accent = colors.champagneBright,
                        title = "Isolated Private Cloud Ledger",
                        subtitle = "Real-time Firestore sync scoped strictly to your Google account."
                    )
                    AuthFeatureRow(
                        icon = Icons.Rounded.Sync,
                        accent = colors.amber,
                        title = "Offline Queue & Auto-Sync",
                        subtitle = "Keep logging stock without signal; queued entries sync automatically."
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Error display if Google Sign-In failed
            if (!authError.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.roseContainer,
                    border = BorderStroke(1.dp, colors.rose.copy(alpha = 0.40f))
                ) {
                    Text(
                        text = authError!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.rose,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }

            // Interactive Google Sign-In CTA
            Button(
                onClick = {
                    authError = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            authError = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Champagne500,
                    contentColor = Obsidian950,
                    disabledContainerColor = Champagne500.copy(alpha = 0.55f),
                    disabledContentColor = Obsidian950
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Obsidian950,
                        strokeWidth = 2.4.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Authenticating…",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.auth_google_button),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // WhatsApp Concierge Support Link
            Surface(
                onClick = { openWhatsAppConcierge(context) },
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceSubtle,
                border = BorderStroke(1.dp, colors.whatsapp.copy(alpha = 0.28f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("auth_whatsapp_concierge_button")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = "WhatsApp Concierge",
                        tint = colors.whatsapp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.whatsapp_concierge_short),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Champagne400,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.auth_footer_note),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AuthFeatureRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String
) {
    val colors = AureliaTheme.colors
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f))
                .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}
