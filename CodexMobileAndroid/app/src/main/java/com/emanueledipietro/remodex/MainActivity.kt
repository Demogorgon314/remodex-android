package com.emanueledipietro.remodex

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emanueledipietro.remodex.feature.appshell.AppViewModel
import com.emanueledipietro.remodex.feature.appshell.AppViewModelFactory
import com.emanueledipietro.remodex.feature.appshell.RemodexApp
import com.emanueledipietro.remodex.platform.notifications.parseThreadIdFromRoute
import com.emanueledipietro.remodex.ui.theme.RemodexTheme

private const val PendingThreadDeepLinkStateKey = "pending_thread_deep_link_id"

class MainActivity : ComponentActivity() {
    private val appContainer
        get() = (application as RemodexApplication).container

    private val viewModel: AppViewModel by viewModels {
        AppViewModelFactory(
            repository = appContainer.appRepository,
            voiceRecorder = appContainer.voiceRecorder,
        )
    }
    private var pendingThreadDeepLinkId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        pendingThreadDeepLinkId = resolvePendingThreadDeepLinkId(
            savedPendingThreadDeepLinkId = savedInstanceState?.getString(PendingThreadDeepLinkStateKey),
            didRestoreSavedState = savedInstanceState?.containsKey(PendingThreadDeepLinkStateKey) == true,
            intentThreadDeepLinkId = intent.threadDeepLinkId(),
        )
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            RemodexTheme(
                appearanceMode = uiState.appearanceMode,
                appFontStyle = uiState.appFontStyle,
            ) {
                RemodexApp(
                    viewModel = viewModel,
                    notificationManager = appContainer.notificationManager,
                    pendingThreadDeepLinkId = pendingThreadDeepLinkId,
                    onThreadDeepLinkHandled = {
                        pendingThreadDeepLinkId = null
                    },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(PendingThreadDeepLinkStateKey, pendingThreadDeepLinkId)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingThreadDeepLinkId = intent.threadDeepLinkId()
    }
}

internal fun resolvePendingThreadDeepLinkId(
    savedPendingThreadDeepLinkId: String?,
    didRestoreSavedState: Boolean,
    intentThreadDeepLinkId: String?,
): String? {
    return if (didRestoreSavedState) {
        savedPendingThreadDeepLinkId
    } else {
        intentThreadDeepLinkId
    }
}

private fun Intent.threadDeepLinkId(): String? {
    return parseThreadIdFromRoute(dataString)
        ?: extras?.getString("threadId")
        ?: extras?.getString("thread_id")
}
