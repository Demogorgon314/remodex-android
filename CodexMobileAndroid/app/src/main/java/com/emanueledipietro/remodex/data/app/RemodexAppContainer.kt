package com.emanueledipietro.remodex.data.app

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Room
import com.emanueledipietro.remodex.data.connection.EncryptedPrefsSecureStore
import com.emanueledipietro.remodex.data.connection.OkHttpRelayWebSocketFactory
import com.emanueledipietro.remodex.data.connection.OkHttpTrustedSessionResolver
import com.emanueledipietro.remodex.data.connection.SecureConnectionCoordinator
import com.emanueledipietro.remodex.data.preferences.DataStoreAppPreferencesRepository
import com.emanueledipietro.remodex.data.threads.BridgeThreadSyncService
import com.emanueledipietro.remodex.data.threads.ProfileAwareThreadCacheStore
import com.emanueledipietro.remodex.data.voice.AndroidWebViewCookieStore
import com.emanueledipietro.remodex.data.voice.DefaultRemodexVoiceTranscriptionService
import com.emanueledipietro.remodex.data.voice.OkHttpVoiceTranscriptionClient
import com.emanueledipietro.remodex.data.voice.VoiceTranscriptionCookieJar
import com.emanueledipietro.remodex.platform.notifications.AndroidRemodexNotificationManager
import com.emanueledipietro.remodex.platform.notifications.AndroidManagedPushRegistrationCoordinator
import com.emanueledipietro.remodex.platform.notifications.FirebaseManagedPushTokenProvider
import com.emanueledipietro.remodex.platform.media.DefaultAndroidVoiceRecorder
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class RemodexAppContainer(
    context: Context,
) : AutoCloseable {
    // App-level sync and notification flows can process large thread snapshots.
    // Keep that work off the UI thread so turn completion does not ANR MainActivity.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appPreferencesRepository = DataStoreAppPreferencesRepository(context.applicationContext)
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val voiceTranscriptionClient = okHttpClient.newBuilder()
        .cookieJar(
            VoiceTranscriptionCookieJar(
                cookieStore = AndroidWebViewCookieStore(),
            ),
        )
        .build()
    val notificationManager = AndroidRemodexNotificationManager(context.applicationContext)
    private val secureStore = EncryptedPrefsSecureStore(context.applicationContext)
    private val secureConnectionCoordinator = SecureConnectionCoordinator(
        store = secureStore,
        trustedSessionResolver = OkHttpTrustedSessionResolver(okHttpClient),
        relayWebSocketFactory = OkHttpRelayWebSocketFactory(okHttpClient),
        scope = appScope,
    )
    private val threadCacheStore = ProfileAwareThreadCacheStore(
        context = context.applicationContext,
        secureStore = secureStore,
    )
    private val threadSyncService = BridgeThreadSyncService(
        secureConnectionCoordinator = secureConnectionCoordinator,
        scope = appScope,
    )
    val voiceRecorder = DefaultAndroidVoiceRecorder(context.applicationContext)
    private val voiceTranscriptionService = DefaultRemodexVoiceTranscriptionService(
        secureConnectionCoordinator = secureConnectionCoordinator,
        transcriptionClient = OkHttpVoiceTranscriptionClient(voiceTranscriptionClient),
    )
    val managedPushRegistrationCoordinator = AndroidManagedPushRegistrationCoordinator(
        secureConnectionCoordinator = secureConnectionCoordinator,
        secureStore = secureStore,
        statusProvider = notificationManager,
        tokenProvider = FirebaseManagedPushTokenProvider(context.applicationContext),
        scope = appScope,
        appEnvironment = if (
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        ) {
            "development"
        } else {
            "production"
        },
    )

    val appRepository: RemodexAppRepository =
        DefaultRemodexAppRepository(
            appPreferencesRepository = appPreferencesRepository,
            secureConnectionCoordinator = secureConnectionCoordinator,
            threadCacheStore = threadCacheStore,
            threadSyncService = threadSyncService,
            threadCommandService = threadSyncService,
            threadHydrationService = threadSyncService,
            voiceTranscriptionService = voiceTranscriptionService,
            managedPushRegistrationState = managedPushRegistrationCoordinator.state,
            scope = appScope,
        )

    init {
        notificationManager.coordinator.start(
            scope = appScope,
            sessionSnapshots = appRepository.session,
        )
        managedPushRegistrationCoordinator.refresh(force = false)
    }

    override fun close() {
        appScope.cancel()
        threadCacheStore.close()
        okHttpClient.dispatcher.cancelAll()
        okHttpClient.connectionPool.evictAll()
        okHttpClient.cache?.close()
    }
}
