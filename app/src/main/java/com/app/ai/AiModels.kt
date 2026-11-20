package com.app.ai

import androidx.compose.ui.graphics.Color
import com.app.habits.TrackerItemType

enum class AiThemePreset(
    val label: String,
    val primaryHex: Long,
    val secondaryHex: Long
) {
    CRIMSON_VAULT("Crimson Vault & Gold", 0xFFC83254, 0xFFE09F3E),
    LAIR_MINT("Lair Mint & Amber", 0xFF52B788, 0xFFE09F3E),
    DYNAMIC_MONET("Material You Dynamic", 0xFFC83254, 0xFFE09F3E),
    SOLAR_EMBER("Solar Amber & Copper", 0xFFE09F3E, 0xFFE76F51),
    ARCTIC_SLATE("Arctic Cyan & Mint", 0xFF4EA8DE, 0xFF52B788),
    AMETHYST_DUSK("Amethyst Dusk & Rose", 0xFF9B5DE5, 0xFFC83254);

    val primaryColor: Color get() = Color(primaryHex)
    val secondaryColor: Color get() = Color(secondaryHex)
}

enum class UiDensityMode(val label: String) {
    COMPACT("Compact"),
    COMFORTABLE("Comfortable")
}

enum class TimePickerMode(val label: String, val description: String) {
    CLOCK("Clock", "Interactive dial clock for hours and minutes"),
    SLIDER("Slider", "Fast hour and minute sliders with live preview"),
    MANUAL("Manual", "Direct numeric time entry")
}

enum class AiProviderPreset(
    val id: String,
    val displayName: String,
    val defaultEndpoint: String,
    val defaultModel: String,
    val fallbackModel: String,
    val isPopular: Boolean,
    val isGeminiNative: Boolean = false
) {
    GEMINI(
        id = "gemini",
        displayName = "Gemini",
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        defaultModel = "gemini-2.5-flash",
        fallbackModel = "gemini-2.5-flash-lite",
        isPopular = true,
        isGeminiNative = true
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultEndpoint = "https://openrouter.ai/api/v1/chat/completions",
        defaultModel = "openrouter/free",
        fallbackModel = "meta-llama/llama-3.3-70b-instruct:free",
        isPopular = true
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini",
        fallbackModel = "gpt-4.1-mini",
        isPopular = true
    ),
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic (Claude)",
        defaultEndpoint = "https://api.anthropic.com/v1/messages",
        defaultModel = "claude-3-5-haiku-latest",
        fallbackModel = "claude-3-7-sonnet-latest",
        isPopular = true
    ),
    GROQ(
        id = "groq",
        displayName = "Groq",
        defaultEndpoint = "https://api.groq.com/openai/v1/chat/completions",
        defaultModel = "llama-3.3-70b-versatile",
        fallbackModel = "llama-3.1-8b-instant",
        isPopular = true
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        defaultEndpoint = "https://api.deepseek.com/chat/completions",
        defaultModel = "deepseek-chat",
        fallbackModel = "deepseek-reasoner",
        isPopular = true
    ),
    MISTRAL(
        id = "mistral",
        displayName = "Mistral AI",
        defaultEndpoint = "https://api.mistral.ai/v1/chat/completions",
        defaultModel = "mistral-small-latest",
        fallbackModel = "open-mistral-nemo",
        isPopular = true
    ),
    XAI(
        id = "xai",
        displayName = "xAI (Grok)",
        defaultEndpoint = "https://api.x.ai/v1/chat/completions",
        defaultModel = "grok-3-mini-fast",
        fallbackModel = "grok-2-1212",
        isPopular = true
    ),
    OLLAMA(
        id = "ollama",
        displayName = "Ollama (Local)",
        defaultEndpoint = "http://127.0.0.1:11434/v1/chat/completions",
        defaultModel = "llama3.2",
        fallbackModel = "qwen2.5:7b",
        isPopular = true
    ),
    OPENAI_COMPATIBLE(
        id = "openai_compatible",
        displayName = "OpenAI-compatible",
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini",
        fallbackModel = "gpt-4.1-mini",
        isPopular = true
    ),
    TOGETHER(
        id = "together",
        displayName = "Together AI",
        defaultEndpoint = "https://api.together.xyz/v1/chat/completions",
        defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
        fallbackModel = "Qwen/Qwen2.5-72B-Instruct-Turbo",
        isPopular = false
    ),
    FIREWORKS(
        id = "fireworks",
        displayName = "Fireworks AI",
        defaultEndpoint = "https://api.fireworks.ai/inference/v1/chat/completions",
        defaultModel = "accounts/fireworks/models/llama-v3p3-70b-instruct",
        fallbackModel = "accounts/fireworks/models/qwen2p5-72b-instruct",
        isPopular = false
    ),
    PERPLEXITY(
        id = "perplexity",
        displayName = "Perplexity",
        defaultEndpoint = "https://api.perplexity.ai/chat/completions",
        defaultModel = "sonar",
        fallbackModel = "sonar-pro",
        isPopular = false
    ),
    CEREBRAS(
        id = "cerebras",
        displayName = "Cerebras",
        defaultEndpoint = "https://api.cerebras.ai/v1/chat/completions",
        defaultModel = "llama-3.3-70b",
        fallbackModel = "llama3.1-8b",
        isPopular = false
    ),
    COHERE(
        id = "cohere",
        displayName = "Cohere",
        defaultEndpoint = "https://api.cohere.com/compatibility/v1/chat/completions",
        defaultModel = "command-r-plus-08-2024",
        fallbackModel = "command-r-08-2024",
        isPopular = false
    ),
    NOVITA(
        id = "novita",
        displayName = "Novita AI",
        defaultEndpoint = "https://api.novita.ai/v3/openai/chat/completions",
        defaultModel = "meta-llama/llama-3.3-70b-instruct",
        fallbackModel = "deepseek/deepseek-v3",
        isPopular = false
    ),
    HYPERBOLIC(
        id = "hyperbolic",
        displayName = "Hyperbolic",
        defaultEndpoint = "https://api.hyperbolic.xyz/v1/chat/completions",
        defaultModel = "meta-llama/Llama-3.3-70B-Instruct",
        fallbackModel = "Qwen/Qwen2.5-72B-Instruct",
        isPopular = false
    ),
    LM_STUDIO(
        id = "lm_studio",
        displayName = "LM Studio (Local)",
        defaultEndpoint = "http://127.0.0.1:1234/v1/chat/completions",
        defaultModel = "local-model",
        fallbackModel = "local-model",
        isPopular = false
    );

    companion object {
        fun fromProviderNameOrId(raw: String): AiProviderPreset {
            val clean = raw.trim().lowercase()
            return entries.firstOrNull {
                it.id.equals(clean, ignoreCase = true) ||
                    it.displayName.equals(raw.trim(), ignoreCase = true) ||
                    (clean.isNotEmpty() && it.displayName.lowercase().contains(clean))
            } ?: OPENAI_COMPATIBLE
        }
    }
}

data class AiSettingsState(
    val providerId: String = AiProviderPreset.GEMINI.id,
    val providerName: String = AiProviderPreset.GEMINI.displayName,
    val endpointUrl: String = AiProviderPreset.GEMINI.defaultEndpoint,
    val apiKey: String = "",
    val apiKeys: List<String> = emptyList(),
    val modelId: String = AiProviderPreset.GEMINI.defaultModel,
    val enableFailover: Boolean = true,
    val fallbackModelId: String = AiProviderPreset.GEMINI.fallbackModel,
    val timePickerMode: TimePickerMode = TimePickerMode.SLIDER,
    val screenTimeGuardEnabled: Boolean = true,
    val dailyScreenLimitMinutes: Int = 180,
    val themePreset: AiThemePreset = AiThemePreset.CRIMSON_VAULT,
    val useDynamicMaterialYou: Boolean = false,
    val customPrimaryHex: String = "#C83254",
    val customSecondaryHex: String = "#E09F3E",
    val useCustomColors: Boolean = false,
    val densityMode: UiDensityMode = UiDensityMode.COMFORTABLE,
    val showDateWheel: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val alarmSoundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val autoBackupEnabled: Boolean = true
) {
    fun allActiveKeys(): List<String> {
        return buildList {
            if (apiKey.isNotBlank()) add(apiKey.trim())
            apiKeys.forEach { k ->
                val t = k.trim()
                if (t.isNotEmpty() && !contains(t)) add(t)
            }
        }
    }
}

data class ProposedTrackerItem(
    val id: String,
    val title: String,
    val type: TrackerItemType,
    val category: String,
    val reason: String,
    val scheduledTime: String = "09:00",
    val evaluationMode: String = "YES_NO"
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
