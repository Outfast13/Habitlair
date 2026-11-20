package com.app.ai

import androidx.compose.ui.graphics.Color
import com.app.habits.TrackerItemType

enum class AiThemePreset(
    val label: String,
    val primaryHex: Long,
    val secondaryHex: Long
) {
    LAIR_MINT("Lair Mint & Amber", 0xFF6EE7B7, 0xFFFBBF24),
    DYNAMIC_MONET("Material You Dynamic", 0xFF6EE7B7, 0xFFFBBF24),
    SOLAR_EMBER("Solar Amber & Copper", 0xFFFBBF24, 0xFFFB923C),
    ARCTIC_SLATE("Arctic Cyan & Slate", 0xFF38BDF8, 0xFF6EE7B7),
    CRIMSON_VAULT("Crimson Vault & Gold", 0xFFF43F5E, 0xFFFBBF24),
    AMETHYST_DUSK("Amethyst Dusk & Rose", 0xFFC084FC, 0xFFF472B6);

    val primaryColor: Color get() = Color(primaryHex)
    val secondaryColor: Color get() = Color(secondaryHex)
}

enum class UiDensityMode(val label: String) {
    COMPACT("Compact"),
    COMFORTABLE("Comfortable")
}

data class AiSettingsState(
    val providerName: String = "OpenAI-Compatible",
    val endpointUrl: String = "https://api.openai.com/v1/chat/completions",
    val apiKey: String = "",
    val modelId: String = "gpt-4o-mini",
    val enableFailover: Boolean = true,
    val fallbackModelId: String = "gpt-4.1-mini",
    val themePreset: AiThemePreset = AiThemePreset.LAIR_MINT,
    val useDynamicMaterialYou: Boolean = true,
    val customPrimaryHex: String = "#6EE7B7",
    val customSecondaryHex: String = "#FBBF24",
    val useCustomColors: Boolean = false,
    val densityMode: UiDensityMode = UiDensityMode.COMFORTABLE,
    val showDateWheel: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val alarmSoundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val autoBackupEnabled: Boolean = true
)

data class ProposedTrackerItem(
    val id: String,
    val title: String,
    val type: TrackerItemType,
    val category: String,
    val reason: String
)

fun parseHexColorOrNull(raw: String): Color? {
    val cleaned = raw.trim().removePrefix("#")
    if (cleaned.length == 6) {
        return runCatching { Color(0xFF000000 or cleaned.toLong(16)) }.getOrNull()
    }
    if (cleaned.length == 8) {
        return runCatching { Color(cleaned.toLong(16)) }.getOrNull()
    }
    return null
}
