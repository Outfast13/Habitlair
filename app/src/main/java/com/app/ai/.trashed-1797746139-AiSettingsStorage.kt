package com.app.ai

import android.content.Context
import org.json.JSONObject

class AiSettingsStorage(context: Context) {
    private val prefs = context.getSharedPreferences("habitlair_settings_v4", Context.MODE_PRIVATE)

    fun load(): AiSettingsState {
        val raw = prefs.getString("settings_json", null) ?: return AiSettingsState()
        return runCatching {
            val obj = JSONObject(raw)
            val preset = runCatching {
                AiThemePreset.valueOf(obj.optString("themePreset", AiThemePreset.LAIR_MINT.name))
            }.getOrDefault(AiThemePreset.LAIR_MINT)
            val density = runCatching {
                UiDensityMode.valueOf(obj.optString("densityMode", UiDensityMode.COMFORTABLE.name))
            }.getOrDefault(UiDensityMode.COMFORTABLE)

            AiSettingsState(
                providerName = obj.optString("providerName", "OpenAI-Compatible"),
                endpointUrl = obj.optString("endpointUrl", "https://api.openai.com/v1/chat/completions"),
                apiKey = obj.optString("apiKey", ""),
                modelId = obj.optString("modelId", "gpt-4o-mini"),
                enableFailover = obj.optBoolean("enableFailover", true),
                fallbackModelId = obj.optString("fallbackModelId", "gpt-4.1-mini"),
                themePreset = preset,
                useDynamicMaterialYou = obj.optBoolean("useDynamicMaterialYou", true),
                customPrimaryHex = obj.optString("customPrimaryHex", "#6EE7B7"),
                customSecondaryHex = obj.optString("customSecondaryHex", "#FBBF24"),
                useCustomColors = obj.optBoolean("useCustomColors", false),
                densityMode = density,
                showDateWheel = obj.optBoolean("showDateWheel", true),
                notificationsEnabled = obj.optBoolean("notificationsEnabled", true),
                alarmSoundEnabled = obj.optBoolean("alarmSoundEnabled", true),
                vibrateEnabled = obj.optBoolean("vibrateEnabled", true),
                autoBackupEnabled = obj.optBoolean("autoBackupEnabled", true)
            )
        }.getOrDefault(AiSettingsState())
    }

    fun save(state: AiSettingsState) {
        val obj = JSONObject().apply {
            put("providerName", state.providerName)
            put("endpointUrl", state.endpointUrl)
            put("apiKey", state.apiKey)
            put("modelId", state.modelId)
            put("enableFailover", state.enableFailover)
            put("fallbackModelId", state.fallbackModelId)
            put("themePreset", state.themePreset.name)
            put("useDynamicMaterialYou", state.useDynamicMaterialYou)
            put("customPrimaryHex", state.customPrimaryHex)
            put("customSecondaryHex", state.customSecondaryHex)
            put("useCustomColors", state.useCustomColors)
            put("densityMode", state.densityMode.name)
            put("showDateWheel", state.showDateWheel)
            put("notificationsEnabled", state.notificationsEnabled)
            put("alarmSoundEnabled", state.alarmSoundEnabled)
            put("vibrateEnabled", state.vibrateEnabled)
            put("autoBackupEnabled", state.autoBackupEnabled)
        }
        prefs.edit().putString("settings_json", obj.toString()).apply()
    }
}
