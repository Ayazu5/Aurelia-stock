package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ToastMessage
import com.example.data.model.ToastTone
import com.example.data.vision.ImageProcessor
import com.example.ui.theme.AureliaTheme
import com.example.ui.theme.Champagne400
import com.example.ui.theme.Champagne600
import com.example.ui.theme.Obsidian950

fun openWhatsAppConcierge(context: Context): Boolean {
    val message = Uri.encode("Hello Aurelia Stock Concierge, I need assistance with my inventory workspace.")
    val uri = Uri.parse("https://wa.me/917011162050?text=$message")
    return try {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

@Composable
fun AureliaBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Champagne400, Champagne600)
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.28f),
                shape = RoundedCornerShape(14.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = "Aurelia Stock emblem",
            tint = Obsidian950,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

@Composable
fun PulsingNeonDot(
    color: Color,
    animate: Boolean = true,
    size: Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "neon_dot")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (animate) 1.65f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = if (animate) 0.08f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier.size(size * 1.9f),
        contentAlignment = Alignment.Center
    ) {
        if (animate) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = pulseAlpha))
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun ThumbnailImageBox(
    thumbnailBase64: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp
) {
    val colors = AureliaTheme.colors
    val imageBitmap = remember(thumbnailBase64) {
        ImageProcessor.decodeThumbnailToImageBitmap(thumbnailBase64)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.surfaceSubtle)
            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Inventory2,
                contentDescription = contentDescription,
                tint = colors.textMuted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun OcrTextChip(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = AureliaTheme.colors
    if (text.isBlank()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = colors.emeraldContainer,
        border = BorderStroke(1.dp, colors.emerald.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.QrCodeScanner,
                contentDescription = null,
                tint = colors.emerald,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                ),
                color = colors.emerald,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ExecutiveToastHost(
    toast: ToastMessage?,
    modifier: Modifier = Modifier
) {
    val colors = AureliaTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(tween(180)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(180)) + slideOutVertically { -it / 2 }
        ) {
            toast?.let { msg ->
                val accent = when (msg.tone) {
                    ToastTone.SUCCESS -> colors.emerald
                    ToastTone.WARNING -> colors.amber
                    ToastTone.ERROR -> colors.rose
                    ToastTone.INFO -> colors.champagne
                }
                val icon = when (msg.tone) {
                    ToastTone.SUCCESS -> Icons.Rounded.CheckCircle
                    ToastTone.WARNING -> Icons.Rounded.WarningAmber
                    ToastTone.ERROR -> Icons.Rounded.ErrorOutline
                    ToastTone.INFO -> Icons.Rounded.Info
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceElevated,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
