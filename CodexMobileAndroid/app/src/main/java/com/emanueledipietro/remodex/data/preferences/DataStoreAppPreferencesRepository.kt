package com.emanueledipietro.remodex.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexQueuedDraft
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexRuntimeOverrides
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val RemodexPreferencesName = "remodex_preferences"
private val OnboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
private val SelectedThreadIdKey = stringPreferencesKey("selected_thread_id")
private val SelectedThreadIdsJsonKey = stringPreferencesKey("selected_thread_ids_by_profile_json")
private val CollapsedProjectGroupIdsJsonKey = stringPreferencesKey("collapsed_project_group_ids_json")
private val CollapsedProjectGroupIdsByProfileJsonKey = stringPreferencesKey("collapsed_project_group_ids_by_profile_json")
private val DeletedThreadIdsJsonKey = stringPreferencesKey("deleted_thread_ids_json")
private val DeletedThreadIdsByProfileJsonKey = stringPreferencesKey("deleted_thread_ids_by_profile_json")
private val QueuedDraftsJsonKey = stringPreferencesKey("queued_drafts_json")
private val QueuedDraftsByProfileJsonKey = stringPreferencesKey("queued_drafts_by_profile_json")
private val RuntimeOverridesJsonKey = stringPreferencesKey("runtime_overrides_json")
private val RuntimeOverridesByProfileJsonKey = stringPreferencesKey("runtime_overrides_by_profile_json")
private val RuntimeDefaultsJsonKey = stringPreferencesKey("runtime_defaults_json")
private val RuntimeDefaultsByProfileJsonKey = stringPreferencesKey("runtime_defaults_by_profile_json")
private val AppearanceModeKey = stringPreferencesKey("appearance_mode")
private val AppFontStyleKey = stringPreferencesKey("app_font_style")
private val MacNicknamesJsonKey = stringPreferencesKey("mac_nicknames_json")
private val Context.remodexDataStore by preferencesDataStore(name = RemodexPreferencesName)

class DataStoreAppPreferencesRepository(
    private val context: Context,
) : AppPreferencesRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val activeBridgeProfileId = MutableStateFlow<String?>(null)

    override val preferences: Flow<AppPreferences> =
        combine(context.remodexDataStore.data, activeBridgeProfileId) { preferences, profileId ->
            val normalizedProfileId = normalizeProfileId(profileId)
            val selectedThreadId = profileValue(
                rawValue = preferences[SelectedThreadIdsJsonKey],
                profileId = normalizedProfileId,
                fallback = { preferences[SelectedThreadIdKey] },
            ) { raw ->
                json.decodeFromString<ProfileStringEnvelope>(raw).profiles
            }
            val deletedThreadIds = profileMapValue(
                rawValue = preferences[DeletedThreadIdsByProfileJsonKey],
                profileId = normalizedProfileId,
                fallback = {
                    preferences[DeletedThreadIdsJsonKey]
                        ?.let { raw -> json.decodeFromString<DeletedThreadIdsEnvelope>(raw).threadIds }
                        ?.toSet()
                        ?: emptySet()
                },
            ) { raw ->
                json.decodeFromString<ProfileStringListEnvelope>(raw).profiles
            }.toSet()
            val collapsedProjectGroupIds = profileMapValue(
                rawValue = preferences[CollapsedProjectGroupIdsByProfileJsonKey],
                profileId = normalizedProfileId,
                fallback = {
                    preferences[CollapsedProjectGroupIdsJsonKey]
                        ?.let { raw -> json.decodeFromString<CollapsedProjectGroupIdsEnvelope>(raw).groupIds }
                        ?.toSet()
                        ?: emptySet()
                },
            ) { raw ->
                json.decodeFromString<ProfileStringListEnvelope>(raw).profiles
            }.toSet()
            val queuedDrafts = profileNestedMapValue(
                rawValue = preferences[QueuedDraftsByProfileJsonKey],
                profileId = normalizedProfileId,
                fallback = {
                    preferences[QueuedDraftsJsonKey]
                        ?.let { raw -> json.decodeFromString<QueuedDraftsEnvelope>(raw).threads }
                        ?: emptyMap()
                },
            ) { raw ->
                json.decodeFromString<ProfileQueuedDraftsEnvelope>(raw).profiles
            }
            val runtimeOverrides = profileNestedMapValue(
                rawValue = preferences[RuntimeOverridesByProfileJsonKey],
                profileId = normalizedProfileId,
                fallback = {
                    preferences[RuntimeOverridesJsonKey]
                        ?.let { raw -> json.decodeFromString<RuntimeOverridesEnvelope>(raw).threads }
                        ?: emptyMap()
                },
            ) { raw ->
                json.decodeFromString<ProfileRuntimeOverridesEnvelope>(raw).profiles
            }
            val runtimeDefaults = profileValue(
                rawValue = preferences[RuntimeDefaultsByProfileJsonKey],
                profileId = normalizedProfileId,
                fallback = {
                    preferences[RuntimeDefaultsJsonKey]
                        ?.let { raw -> json.decodeFromString<RemodexRuntimeDefaults>(raw) }
                        ?: RemodexRuntimeDefaults()
                },
            ) { raw ->
                json.decodeFromString<ProfileRuntimeDefaultsEnvelope>(raw).profiles
            } ?: RemodexRuntimeDefaults()
            val macNicknames = preferences[MacNicknamesJsonKey]
                ?.let { raw -> json.decodeFromString<MacNicknamesEnvelope>(raw).nicknames }
                ?: emptyMap()
            AppPreferences(
                onboardingCompleted = preferences[OnboardingCompletedKey] ?: false,
                selectedThreadId = selectedThreadId,
                collapsedProjectGroupIds = collapsedProjectGroupIds,
                deletedThreadIds = deletedThreadIds,
                queuedDraftsByThread = queuedDrafts,
                runtimeOverridesByThread = runtimeOverrides,
                runtimeDefaults = runtimeDefaults,
                appearanceMode = preferences[AppearanceModeKey]
                    ?.let(RemodexAppearanceMode::valueOf)
                    ?: RemodexAppearanceMode.SYSTEM,
                appFontStyle = preferences[AppFontStyleKey]
                    ?.let(RemodexAppFontStyle::valueOf)
                    ?: RemodexAppFontStyle.SYSTEM,
                macNicknamesByDeviceId = macNicknames,
            )
        }

    override fun setActiveBridgeProfileId(profileId: String?) {
        activeBridgeProfileId.value = normalizeProfileId(profileId)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.remodexDataStore.edit { preferences: MutablePreferences ->
            preferences[OnboardingCompletedKey] = completed
        }
    }

    override suspend fun setSelectedThreadId(threadId: String?) {
        context.remodexDataStore.edit { preferences: MutablePreferences ->
            mutateProfileStringEnvelope(
                preferences = preferences,
                key = SelectedThreadIdsJsonKey,
                profileId = activeBridgeProfileId.value,
                nextValue = threadId,
            )
        }
    }

    override suspend fun setProjectGroupCollapsed(
        groupId: String,
        collapsed: Boolean,
    ) {
        context.remodexDataStore.edit { preferences: MutablePreferences ->
            val normalizedGroupId = groupId.trim()
            if (normalizedGroupId.isEmpty()) {
                return@edit
            }
            val current = mutableProfileStringSet(
                preferences = preferences,
                key = CollapsedProjectGroupIdsByProfileJsonKey,
                profileId = activeBridgeProfileId.value,
            ) ?: return@edit
            if (collapsed) {
                current.add(normalizedGroupId)
            } else {
                current.remove(normalizedGroupId)
            }
            writeProfileStringSet(
                preferences = preferences,
                key = CollapsedProjectGroupIdsByProfileJsonKey,
                profileId = activeBridgeProfileId.value,
                values = current,
            )
        }
    }

    override suspend fun setThreadDeleted(
        threadId: String,
        deleted: Boolean,
    ) {
        context.remodexDataStore.edit { preferences: MutablePreferences ->
            val normalizedThreadId = threadId.trim()
            if (normalizedThreadId.isEmpty()) {
                return@edit
            }
            val current = mutableProfileStringSet(
                preferences = preferences,
                key = DeletedThreadIdsByProfileJsonKey,
                profileId = activeBridgeProfileId.value,
            ) ?: return@edit
            if (deleted) {
                current.add(normalizedThreadId)
            } else {
                current.remove(normalizedThreadId)
            }
            writeProfileStringSet(
                preferences = preferences,
                key = DeletedThreadIdsByProfileJsonKey,
                profileId = activeBridgeProfileId.value,
                values = current,
            )
        }
    }

    override suspend fun setQueuedDrafts(
        threadId: String,
        drafts: List<RemodexQueuedDraft>,
    ) {
        context.remodexDataStore.edit { preferences: MutablePreferences ->
            val profileId = normalizeProfileId(activeBridgeProfileId.value) ?: return@edit
            val current = preferences[QueuedDraftsByProfileJsonKey]
                ?.let { raw -> json.decodeFromString<ProfileQueuedDraftsEnvelope>(raw).profiles }
                ?.toMutableMap()
                ?: mutableMapOf()
            val perProfile = current[profileId]?.toMutableMap() ?: mutableMapOf()
            if (drafts.isEmpty()) {
                perProfile.remove(threadId)
            } else {
                perProfile[threadId] = drafts
            }
            if (perProfile.isEmpty()) {
                current.remove(profileId)
            } else {
                current[profileId] = perProfile
            }
            preferences[QueuedDraftsByProfileJsonKey] = json.encodeToString(ProfileQueuedDraftsEnvelope(current))
        }
    }

    override suspend fun setRuntimeOverrides(
        threadId: String,
        overrides: RemodexRuntimeOverrides?,
    ) {
        context.remodexDataStore.edit { preferences: MutablePreferences ->
            val profileId = normalizeProfileId(activeBridgeProfileId.value) ?: return@edit
            val current = preferences[RuntimeOverridesByProfileJsonKey]
                ?.let { raw -> json.decodeFromString<ProfileRuntimeOverridesEnvelope>(raw).profiles }
                ?.toMutableMap()
                ?: mutableMapOf()
            val perProfile = current[profileId]?.toMutableMap() ?: mutableMapOf()
            if (overrides == null) {
                perProfile.remove(threadId)
            } else {
                perProfile[threadId] = overrides
            }
            if (perProfile.isEmpty()) {
                current.remove(profileId)
            } else {
                current[profileId] = perProfile
            }
            preferences[RuntimeOverridesByProfileJsonKey] = json.encodeToString(ProfileRuntimeOverridesEnvelope(current))
        }
    }

    override suspend fun setRuntimeDefaults(defaults: RemodexRuntimeDefaults) {
        context.remodexDataStore.edit { preferences: MutablePreferences ->
            val profileId = normalizeProfileId(activeBridgeProfileId.value) ?: return@edit
            val current = preferences[RuntimeDefaultsByProfileJsonKey]
                ?.let { raw -> json.decodeFromString<ProfileRuntimeDefaultsEnvelope>(raw).profiles }
                ?.toMutableMap()
                ?: mutableMapOf()
            current[profileId] = defaults
            preferences[RuntimeDefaultsByProfileJsonKey] = json.encodeToString(ProfileRuntimeDefaultsEnvelope(current))
        }
    }

    override suspend fun setAppearanceMode(mode: RemodexAppearanceMode) {
        context.remodexDataStore.edit { preferences: MutablePreferences ->
            preferences[AppearanceModeKey] = mode.name
        }
    }

    override suspend fun setAppFontStyle(style: RemodexAppFontStyle) {
        context.remodexDataStore.edit { preferences: MutablePreferences ->
            preferences[AppFontStyleKey] = style.name
        }
    }

    override suspend fun setMacNickname(
        deviceId: String,
        nickname: String?,
    ) {
        context.remodexDataStore.edit { preferences: MutablePreferences ->
            val current = preferences[MacNicknamesJsonKey]
                ?.let { raw -> json.decodeFromString<MacNicknamesEnvelope>(raw).nicknames }
                ?.toMutableMap()
                ?: mutableMapOf()
            val normalizedDeviceId = deviceId.trim()
            if (normalizedDeviceId.isEmpty()) {
                return@edit
            }
            val normalizedNickname = nickname?.trim().orEmpty()
            if (normalizedNickname.isEmpty()) {
                current.remove(normalizedDeviceId)
            } else {
                current[normalizedDeviceId] = normalizedNickname
            }
            preferences[MacNicknamesJsonKey] = json.encodeToString(MacNicknamesEnvelope(current))
        }
    }

    private fun normalizeProfileId(profileId: String?): String? {
        return profileId?.trim()?.takeIf(String::isNotBlank)
    }

    private fun mutateProfileStringEnvelope(
        preferences: MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        profileId: String?,
        nextValue: String?,
    ) {
        val normalizedProfileId = normalizeProfileId(profileId) ?: return
        val current = preferences[key]
            ?.let { raw -> json.decodeFromString<ProfileStringEnvelope>(raw).profiles }
            ?.toMutableMap()
            ?: mutableMapOf()
        val normalizedValue = nextValue?.trim().orEmpty()
        if (normalizedValue.isEmpty()) {
            current.remove(normalizedProfileId)
        } else {
            current[normalizedProfileId] = normalizedValue
        }
        preferences[key] = json.encodeToString(ProfileStringEnvelope(current))
    }

    private fun mutableProfileStringSet(
        preferences: MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        profileId: String?,
    ): MutableSet<String>? {
        val normalizedProfileId = normalizeProfileId(profileId) ?: return null
        return preferences[key]
            ?.let { raw -> json.decodeFromString<ProfileStringListEnvelope>(raw).profiles[normalizedProfileId] }
            ?.toMutableSet()
            ?: mutableSetOf()
    }

    private fun writeProfileStringSet(
        preferences: MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        profileId: String?,
        values: Set<String>,
    ) {
        val normalizedProfileId = normalizeProfileId(profileId) ?: return
        val current = preferences[key]
            ?.let { raw -> json.decodeFromString<ProfileStringListEnvelope>(raw).profiles }
            ?.toMutableMap()
            ?: mutableMapOf()
        if (values.isEmpty()) {
            current.remove(normalizedProfileId)
        } else {
            current[normalizedProfileId] = values.toList().sorted()
        }
        preferences[key] = json.encodeToString(ProfileStringListEnvelope(current))
    }

    private fun <T> profileValue(
        rawValue: String?,
        profileId: String?,
        fallback: () -> T?,
        decode: (String) -> Map<String, T>,
    ): T? {
        val normalizedProfileId = normalizeProfileId(profileId) ?: return null
        if (rawValue == null) {
            return fallback()
        }
        return decode(rawValue)[normalizedProfileId]
    }

    private fun profileMapValue(
        rawValue: String?,
        profileId: String?,
        fallback: () -> Set<String>,
        decode: (String) -> Map<String, List<String>>,
    ): Set<String> {
        val normalizedProfileId = normalizeProfileId(profileId) ?: return emptySet()
        if (rawValue == null) {
            return fallback()
        }
        return decode(rawValue)[normalizedProfileId]?.toSet() ?: emptySet()
    }

    private fun <T> profileNestedMapValue(
        rawValue: String?,
        profileId: String?,
        fallback: () -> Map<String, T>,
        decode: (String) -> Map<String, Map<String, T>>,
    ): Map<String, T> {
        val normalizedProfileId = normalizeProfileId(profileId) ?: return emptyMap()
        if (rawValue == null) {
            return fallback()
        }
        return decode(rawValue)[normalizedProfileId].orEmpty()
    }
}

@Serializable
private data class QueuedDraftsEnvelope(
    val threads: Map<String, List<RemodexQueuedDraft>> = emptyMap(),
)

@Serializable
private data class DeletedThreadIdsEnvelope(
    val threadIds: List<String> = emptyList(),
)

@Serializable
private data class CollapsedProjectGroupIdsEnvelope(
    val groupIds: List<String> = emptyList(),
)

@Serializable
private data class RuntimeOverridesEnvelope(
    val threads: Map<String, RemodexRuntimeOverrides> = emptyMap(),
)

@Serializable
private data class ProfileStringEnvelope(
    val profiles: Map<String, String> = emptyMap(),
)

@Serializable
private data class ProfileStringListEnvelope(
    val profiles: Map<String, List<String>> = emptyMap(),
)

@Serializable
private data class ProfileQueuedDraftsEnvelope(
    val profiles: Map<String, Map<String, List<RemodexQueuedDraft>>> = emptyMap(),
)

@Serializable
private data class ProfileRuntimeOverridesEnvelope(
    val profiles: Map<String, Map<String, RemodexRuntimeOverrides>> = emptyMap(),
)

@Serializable
private data class ProfileRuntimeDefaultsEnvelope(
    val profiles: Map<String, RemodexRuntimeDefaults> = emptyMap(),
)

@Serializable
private data class MacNicknamesEnvelope(
    val nicknames: Map<String, String> = emptyMap(),
)
