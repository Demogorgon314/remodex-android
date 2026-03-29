package com.emanueledipietro.remodex.data.app

import com.emanueledipietro.remodex.data.connection.SecureConnectionSnapshot
import com.emanueledipietro.remodex.model.RemodexConnectionStatus
import com.emanueledipietro.remodex.model.RemodexAppearanceMode
import com.emanueledipietro.remodex.model.RemodexAppFontStyle
import com.emanueledipietro.remodex.model.RemodexBridgeUpdatePrompt
import com.emanueledipietro.remodex.model.RemodexBridgeProfilePresentation
import com.emanueledipietro.remodex.model.RemodexModelOption
import com.emanueledipietro.remodex.model.RemodexNotificationRegistrationState
import com.emanueledipietro.remodex.model.RemodexRuntimeDefaults
import com.emanueledipietro.remodex.model.RemodexThreadSummary
import com.emanueledipietro.remodex.model.RemodexTrustedMacPresentation

data class RemodexSessionSnapshot(
    val onboardingCompleted: Boolean = false,
    val connectionStatus: RemodexConnectionStatus = RemodexConnectionStatus(),
    val secureConnection: SecureConnectionSnapshot = SecureConnectionSnapshot(),
    val notificationRegistration: RemodexNotificationRegistrationState = RemodexNotificationRegistrationState(),
    val collapsedProjectGroupIds: Set<String> = emptySet(),
    val runtimeDefaults: RemodexRuntimeDefaults = RemodexRuntimeDefaults(),
    val availableModels: List<RemodexModelOption> = emptyList(),
    val appearanceMode: RemodexAppearanceMode = RemodexAppearanceMode.SYSTEM,
    val appFontStyle: RemodexAppFontStyle = RemodexAppFontStyle.SYSTEM,
    val trustedMac: RemodexTrustedMacPresentation? = null,
    val bridgeProfiles: List<RemodexBridgeProfilePresentation> = emptyList(),
    val bridgeUpdatePrompt: RemodexBridgeUpdatePrompt? = null,
    val supportsThreadFork: Boolean = true,
    val threads: List<RemodexThreadSummary> = emptyList(),
    val selectedThreadId: String? = null,
    val selectedThreadSnapshot: RemodexThreadSummary? = null,
) {
    val selectedThread: RemodexThreadSummary?
        get() {
            val snapshot = selectedThreadSnapshot
            if (snapshot != null && (selectedThreadId == null || snapshot.id == selectedThreadId)) {
                return snapshot
            }
            return threads.firstOrNull { it.id == selectedThreadId } ?: threads.firstOrNull()
        }
}
