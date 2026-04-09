package com.emanueledipietro.remodex.data.preferences

import com.emanueledipietro.remodex.model.RemodexQueuedDraft
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexRuntimeOverrides

data class AppPreferences(
    val onboardingCompleted: Boolean = false,
    val selectedThreadId: String? = null,
    val collapsedProjectGroupIds: Set<String> = emptySet(),
    val deletedThreadIds: Set<String> = emptySet(),
    val associatedManagedWorktreePathsByThread: Map<String, String> = emptyMap(),
    val queuedDraftsByThread: Map<String, List<RemodexQueuedDraft>> = emptyMap(),
    val runtimeOverridesByThread: Map<String, RemodexRuntimeOverrides> = emptyMap(),
    val runtimeDefaults: RemodexRuntimeDefaults = RemodexRuntimeDefaults(),
    val appearanceMode: RemodexAppearanceMode = RemodexAppearanceMode.SYSTEM,
    val appFontStyle: RemodexAppFontStyle = RemodexAppFontStyle.SYSTEM,
    val macNicknamesByDeviceId: Map<String, String> = emptyMap(),
)
