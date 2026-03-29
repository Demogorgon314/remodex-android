package com.emanueledipietro.remodex.data.preferences

import com.emanueledipietro.remodex.model.RemodexQueuedDraft
import com.emanueledipietro.remodex.model.RemodexAccessMode
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexRuntimeOverrides
import com.emanueledipietro.remodex.model.RemodexServiceTier
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    val preferences: Flow<AppPreferences>

    fun setActiveBridgeProfileId(profileId: String?)

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setSelectedThreadId(threadId: String?)

    suspend fun setProjectGroupCollapsed(
        groupId: String,
        collapsed: Boolean,
    )

    suspend fun setThreadDeleted(
        threadId: String,
        deleted: Boolean,
    )

    suspend fun setQueuedDrafts(
        threadId: String,
        drafts: List<RemodexQueuedDraft>,
    )

    suspend fun setRuntimeOverrides(
        threadId: String,
        overrides: RemodexRuntimeOverrides?,
    )

    suspend fun setRuntimeDefaults(defaults: RemodexRuntimeDefaults)

    suspend fun setAppearanceMode(mode: RemodexAppearanceMode)

    suspend fun setAppFontStyle(style: RemodexAppFontStyle)

    suspend fun setMacNickname(
        deviceId: String,
        nickname: String?,
    )
}
