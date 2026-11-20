package com.app.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AiSettingsStorage(context: Context) {
    private val prefs = context.getSharedPreferences("habitlair_settings_v4", Context.MODE_PRIVATE)

    fun load(): AiSettingsState {
        val raw = prefs.getString("settings_json", null) ?: return AiSettingsState()
        return runCatching {
            val obj = JSONObject(raw)
            val savedProviderName = obj.optString("providerName", AiProviderPreset.GEMINI.displayName)
            val preset = AiProviderPreset.fromProviderNameOrId(
                obj.optString("providerId", savedProviderName)
            )
            val theme = runCatching {
                AiThemePreset.valueOf(obj.optString("themePreset", AiThemePreset.CRIMSON_VAULT.name))
            }.getOrDefault(AiThemePreset.CRIMSON_VAULT)
            val density = runCatching {
                UiDensityMode.valueOf(obj.optString("densityMode", UiDensityMode.COMFORTABLE.name))
            }.getOrDefault(UiDensityMode.COMFORTABLE)
            val pickerMode = runCatching {
                TimePickerMode.valueOf(obj.optString("timePickerMode", TimePickerMode.SLIDER.name))
            }.getOrDefault(TimePickerMode.SLIDER)

            val singleKey = obj.optString("apiKey", "")
            val keysArr = obj.optJSONArray("apiKeys") ?: JSONArray()
            val parsedKeys = buildList {
                if (singleKey.isNotBlank()) add(singleKey.trim())
                for (i in 0 until keysArr.length()) {
                    val k = keysArr.optString(i, "").trim()
                    if (k.isNotEmpty() && !contains(k)) add(k)
                }
            }

            AiSettingsState(
                providerId = preset.id,
                providerName = savedProviderName.ifBlank { preset.displayName },
                endpointUrl = obj.optString("endpointUrl", preset.defaultEndpoint),
                apiKey = parsedKeys.firstOrNull() ?: singleKey,
                apiKeys = parsedKeys,
                modelId = obj.optString("modelId", preset.defaultModel),
                enableFailover = obj.optBoolean("enableFailover", true),
                fallbackModelId = obj.optString("fallbackModelId", preset.fallbackModel),
                timePickerMode = pickerMode,
                screenTimeGuardEnabled = obj.optBoolean("screenTimeGuardEnabled", true),
                dailyScreenLimitMinutes = obj.optInt("dailyScreenLimitMinutes", 180).coerceIn(15, 960),
                themePreset = theme,
                useDynamicMaterialYou = obj.optBoolean("useDynamicMaterialYou", false),
                customPrimaryHex = obj.optString("customPrimaryHex", "#C83254"),
                customSecondaryHex = obj.optString("customSecondaryHex", "#E09F3E"),
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
        val keysList = state.allActiveKeys()
        val keysArr = JSONArray().apply { keysList.forEach { put(it) } }
        val obj = JSONObject().apply {
            put("providerId", state.providerId)
            put("providerName", state.providerName)
            put("endpointUrl", state.endpointUrl)
            put("apiKey", keysList.firstOrNull() ?: state.apiKey)
            put("apiKeys", keysArr)
            put("modelId", state.modelId)
            put("enableFailover", state.enableFailover)
            put("fallbackModelId", state.fallbackModelId)
            put("timePickerMode", state.timePickerMode.name)
            put("screenTimeGuardEnabled", state.screenTimeGuardEnabled)
            put("dailyScreenLimitMinutes", state.dailyScreenLimitMinutes)
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
