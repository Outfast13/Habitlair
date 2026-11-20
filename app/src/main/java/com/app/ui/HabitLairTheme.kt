package com.app.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ai.AiSettingsState
import com.app.ai.parseHexColorOrNull
import com.app.habits.HabitState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object LairPalette {
    val Canvas = Color(0xFF0E1015)
    val SurfaceCard = Color(0xFF171A21)
    val SurfaceElevated = Color(0xFF212631)
    val BorderSubtle = Color(0xFF2B3140)
    val TextPrimary = Color(0xFFF3F5F8)
    val TextSecondary = Color(0xFF9BA3B4)
    val MintSuccess = Color(0xFF52B788)
    val AccentAmber = Color(0xFFE09F3E)
    val DangerRed = Color(0xFFE63946)
}

fun Modifier.pressScale(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = if (pressed) 0.97f else 1f
    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

fun format24hToAmPm(raw: String): String {
    val parts = raw.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return raw
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return raw
    val suffix = if (hour >= 12) "PM" else "AM"
    val h12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format(Locale.US, "%d:%02d %s", h12, minute, suffix)
}

@Composable
fun HabitLairTheme(
    settings: AiSettingsState,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = if (settings.useDynamicMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        val primary = if (settings.useCustomColors) {
            parseHexColorOrNull(settings.customPrimaryHex) ?: settings.themePreset.primaryColor
        } else {
            settings.themePreset.primaryColor
        }
        val secondary = if (settings.useCustomColors) {
            parseHexColorOrNull(settings.customSecondaryHex) ?: settings.themePreset.secondaryColor
        } else {
            settings.themePreset.secondaryColor
        }
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = secondary,
            onSecondary = Color(0xFF141414),
            background = LairPalette.Canvas,
            onBackground = LairPalette.TextPrimary,
            surface = LairPalette.SurfaceCard,
            onSurface = LairPalette.TextPrimary,
            outline = LairPalette.BorderSubtle
        )
    }

    MaterialTheme(
        colorScheme = baseScheme,
        content = content
    )
}

@Composable
fun HabitLairTopBar(
    sectionTitle: String,
    sectionSubtitle: String,
    badgeText: String,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LairPalette.SurfaceCard, RoundedCornerShape(14.dp))
            .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .pressScale(onOpenDrawer)
                .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Menu, contentDescription = "Menu", tint = LairPalette.TextPrimary, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sectionTitle,
                color = LairPalette.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = sectionSubtitle,
                color = LairPalette.TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Box(
            modifier = Modifier
                .background(primary.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(badgeText, color = primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .pressScale(onOpenSettings)
                .background(LairPalette.SurfaceElevated, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = LairPalette.TextPrimary, modifier = Modifier.size(20.dp))
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
    val days = remember(selectedDate) {
        (-7..7).map { offset -> selectedDate.plusDays(offset.toLong()) }
    }
    val monthLabel = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LairPalette.SurfaceCard, RoundedCornerShape(12.dp))
            .border(1.dp, LairPalette.BorderSubtle, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = monthLabel,
                color = LairPalette.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .pressScale { onSelectDate(selectedDate.minusDays(1)) }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("<", color = LairPalette.TextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .pressScale { onSelectDate(today) }
                        .background(
                            if (selectedDate == today) primary.copy(alpha = 0.2f) else LairPalette.SurfaceElevated,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (selectedDate == today) "Today" else "Jump Today",
                        color = if (selectedDate == today) primary else LairPalette.TextPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .pressScale { onSelectDate(selectedDate.plusDays(1)) }
                        .background(LairPalette.SurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(">", color = LairPalette.TextPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(days, key = { it.toEpochDay() }) { d ->
                val isSelected = d == selectedDate
                val isToday = d == today
                Column(
                    modifier = Modifier
                        .pressScale { onSelectDate(d) }
                        .background(if (isSelected) primary else LairPalette.SurfaceElevated, RoundedCornerShape(10.dp))
                        .border(
                            1.dp,
                            when {
                                isSelected -> primary
                                isToday -> MaterialTheme.colorScheme.secondary
                                else -> LairPalette.BorderSubtle
                            },
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = d.dayOfWeek.name.take(3),
                        color = if (isSelected) onPrimary else LairPalette.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = d.dayOfMonth.toString(),
                        color = if (isSelected) onPrimary else LairPalette.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
