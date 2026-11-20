package com.app.ui

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ai.AiSettingsState
import com.app.ai.AiThemePreset
import com.app.ai.parseHexColorOrNull
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object LairPalette {
    val Canvas = Color(0xFF0A0E0C)
    val SurfaceCard = Color(0xFF131A16)
    val SurfaceElevated = Color(0xFF1B241F)
    val BorderSubtle = Color(0xFF2B3A31)
    val AccentMint = Color(0xFF6EE7B7)
    val AccentAmber = Color(0xFFFBBF24)
    val AccentCyan = Color(0xFF38BDF8)
    val AccentCopper = Color(0xFFFB923C)
    val TextPrimary = Color(0xFFF4F7F5)
    val TextSecondary = Color(0xFFB7C6BD)
    val DangerRed = Color(0xFFF87171)
}

private fun baseSchemeWithAccents(primary: Color, secondary: Color) = darkColorScheme(
    primary = primary,
    onPrimary = Color(0xFF071510),
    primaryContainer = Color(0xFF17382B),
    onPrimaryContainer = primary,
    secondary = secondary,
    onSecondary = Color(0xFF1F1604),
    tertiary = LairPalette.AccentCyan,
    onTertiary = Color(0xFF051C2B),
    background = LairPalette.Canvas,
    onBackground = LairPalette.TextPrimary,
    surface = LairPalette.SurfaceCard,
    onSurface = LairPalette.TextPrimary,
    surfaceVariant = LairPalette.SurfaceElevated,
    onSurfaceVariant = LairPalette.TextSecondary,
    outline = LairPalette.BorderSubtle,
    error = LairPalette.DangerRed,
    onError = Color(0xFF2B0808)
)

@Composable
fun HabitLairTheme(
    settings: AiSettingsState,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val resolvedScheme = remember(settings, context) {
        val customP = parseHexColorOrNull(settings.customPrimaryHex)
        val customS = parseHexColorOrNull(settings.customSecondaryHex)
        if (settings.useCustomColors && customP != null) {
            baseSchemeWithAccents(customP, customS ?: LairPalette.AccentAmber)
        } else {
            val wantDynamic = settings.useDynamicMaterialYou || settings.themePreset == AiThemePreset.DYNAMIC_MONET
            if (wantDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context).copy(
                    background = LairPalette.Canvas,
                    surface = LairPalette.SurfaceCard,
                    surfaceVariant = LairPalette.SurfaceElevated,
                    onBackground = LairPalette.TextPrimary,
                    onSurface = LairPalette.TextPrimary,
                    onSurfaceVariant = LairPalette.TextSecondary,
                    outline = LairPalette.BorderSubtle
                )
            } else {
                baseSchemeWithAccents(
                    primary = settings.themePreset.primaryColor,
                    secondary = settings.themePreset.secondaryColor
                )
            }
        }
    }

    MaterialTheme(
        colorScheme = resolvedScheme,
        content = content
    )
}

@Composable
fun Modifier.pressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "habitLairSpringPress"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

@Composable
fun HabitLairBrandMark(
    modifier: Modifier = Modifier,
    sizeDp: Int = 36
) {
    val accentPrimary = MaterialTheme.colorScheme.primary
    val accentSecondary = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val strokeMain = size.minDimension * 0.11f
        val strokeBar = size.minDimension * 0.085f
        val pad = strokeMain * 0.7f
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val top = pad + h * 0.12f
        val bottom = pad + h

        drawArc(
            color = accentPrimary,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(pad, top),
            size = Size(w, w),
            style = Stroke(width = strokeMain, cap = StrokeCap.Round)
        )
        val pillarTop = top + w * 0.5f
        drawLine(
            color = accentPrimary,
            start = Offset(pad, pillarTop),
            end = Offset(pad, bottom),
            strokeWidth = strokeMain,
            cap = StrokeCap.Round
        )
        drawLine(
            color = accentPrimary,
            start = Offset(pad + w, pillarTop),
            end = Offset(pad + w, bottom),
            strokeWidth = strokeMain,
            cap = StrokeCap.Round
        )
        drawLine(
            color = accentSecondary,
            start = Offset(pad + w * 0.25f, pillarTop + h * 0.14f),
            end = Offset(pad + w * 0.75f, pillarTop + h * 0.14f),
            strokeWidth = strokeBar,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = accentPrimary,
            radius = size.minDimension * 0.09f,
            center = Offset(pad + w * 0.5f, pillarTop + h * 0.36f)
        )
    }
}

@Composable
fun HabitLairTopBar(
    sectionTitle: String,
    sectionSubtitle: String,
    badgeText: String? = null,
    onOpenDrawer: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LairPalette.SurfaceCard, RoundedCornerShape(16.dp))
            .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .pressScale { onOpenDrawer() }
                    .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "☰",
                    color = primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            HabitLairBrandMark(sizeDp = 24)

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "HABITLAIR • $sectionTitle",
                    color = primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = sectionSubtitle,
                    color = LairPalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }

        if (!badgeText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = badgeText,
                    color = secondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TinyDateWheelBar(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val today = remember { LocalDate.now() }

    val dates = remember(today) {
        (-14L..30L).map { offset -> today.plusDays(offset) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LairPalette.SurfaceCard, RoundedCornerShape(14.dp))
            .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(14.dp))
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selectedDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${selectedDate.year} • ${selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                color = LairPalette.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (selectedDate != today) {
                Box(
                    modifier = Modifier
                        .pressScale { onSelectDate(today) }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Today",
                        color = primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(dates, key = { it.toEpochDay() }) { date ->
                val isSelected = date == selectedDate
                val isToday = date == today
                Column(
                    modifier = Modifier
                        .pressScale { onSelectDate(date) }
                        .background(
                            if (isSelected) primary else LairPalette.SurfaceElevated,
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = if (isToday && !isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) primary else if (isToday) MaterialTheme.colorScheme.secondary else LairPalette.BorderSubtle,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        color = if (isSelected) onPrimary else LairPalette.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        color = if (isSelected) onPrimary else LairPalette.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
