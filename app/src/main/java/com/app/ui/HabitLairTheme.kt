package com.app.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessAlarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ai.AiSettingsState
import com.app.ai.AiThemePreset
import com.app.ai.parseHexColorOrNull
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object LairPalette {
    val Canvas = Color(0xFF121212)
    val SurfaceCard = Color(0xFF1A1A1A)
    val SurfaceElevated = Color(0xFF242424)
    val BorderSubtle = Color(0xFF2E2E2E)
    val AccentRose = Color(0xFFC83254)
    val AccentMint = Color(0xFF52B788)
    val AccentAmber = Color(0xFFE09F3E)
    val AccentCyan = Color(0xFF4EA8DE)
    val AccentViolet = Color(0xFF9B5DE5)
    val AccentCopper = Color(0xFFE76F51)
    val TextPrimary = Color(0xFFF3F4F6)
    val TextSecondary = Color(0xFF9CA3AF)
    val DangerRed = Color(0xFFEF4444)
}

private fun baseSchemeWithAccents(primary: Color, secondary: Color) = darkColorScheme(
    primary = primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2D141B),
    onPrimaryContainer = primary,
    secondary = secondary,
    onSecondary = Color(0xFF1A1407),
    tertiary = LairPalette.AccentCyan,
    onTertiary = Color(0xFF061A26),
    background = LairPalette.Canvas,
    onBackground = LairPalette.TextPrimary,
    surface = LairPalette.SurfaceCard,
    onSurface = LairPalette.TextPrimary,
    surfaceVariant = LairPalette.SurfaceElevated,
    onSurfaceVariant = LairPalette.TextSecondary,
    outline = LairPalette.BorderSubtle,
    error = LairPalette.DangerRed,
    onError = Color(0xFFFFFFFF)
)

fun categoryBadgeIcon(category: String): Pair<ImageVector, Color> {
    val key = category.trim().lowercase(Locale.ROOT)
    return when {
        key.contains("study") || key.contains("class") || key.contains("math") || key.contains("chem") || key.contains("phys") || key.contains("bio") ->
            Icons.Outlined.School to LairPalette.AccentViolet
        key.contains("rest") || key.contains("sleep") || key.contains("break") || key.contains("calm") ->
            Icons.Outlined.SelfImprovement to LairPalette.AccentMint
        key.contains("workout") || key.contains("calisthenics") || key.contains("gym") || key.contains("train") ->
            Icons.Outlined.FitnessCenter to LairPalette.AccentCopper
        key.contains("remind") || key.contains("alarm") ->
            Icons.Outlined.AccessAlarm to LairPalette.AccentAmber
        key.contains("task") || key.contains("check") ->
            Icons.Outlined.CheckCircle to LairPalette.AccentCyan
        key.contains("ai") ->
            Icons.Outlined.AutoAwesome to LairPalette.AccentRose
        else ->
            Icons.Outlined.Repeat to LairPalette.AccentMint
    }
}

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
    pressedScale: Float = 0.96f,
    haptic: Boolean = false,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "habitLairPress"
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
            onClick = {
                if (haptic) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }
        )
}

@Composable
fun HabitLairTopBar(
    sectionTitle: String,
    sectionSubtitle: String,
    badgeText: String? = null,
    onOpenDrawer: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pressScale(haptic = true) { onOpenDrawer() }
                    .background(LairPalette.SurfaceCard, RoundedCornerShape(10.dp))
                    .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "Open menu",
                    tint = LairPalette.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = sectionTitle,
                    color = LairPalette.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = sectionSubtitle,
                    color = LairPalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .background(LairPalette.SurfaceCard, RoundedCornerShape(8.dp))
                        .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(8.dp))
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

            if (onOpenSettings != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .pressScale(haptic = true) { onOpenSettings() }
                        .background(LairPalette.SurfaceCard, RoundedCornerShape(10.dp))
                        .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selectedDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${selectedDate.year} • ${selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                color = LairPalette.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (selectedDate != today) {
                Box(
                    modifier = Modifier
                        .pressScale { onSelectDate(today) }
                        .background(LairPalette.SurfaceCard, RoundedCornerShape(8.dp))
                        .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(8.dp))
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
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(dates, key = { it.toEpochDay() }) { date ->
                val isSelected = date == selectedDate
                val isToday = date == today
                Column(
                    modifier = Modifier
                        .pressScale(haptic = true) { onSelectDate(date) }
                        .background(
                            if (isSelected) primary else LairPalette.SurfaceCard,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = if (isToday && !isSelected) 1.dp else 0.dp,
                            color = if (isToday && !isSelected) primary.copy(alpha = 0.65f) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 11.dp, vertical = 7.dp),
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
