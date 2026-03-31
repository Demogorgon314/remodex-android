package com.emanueledipietro.remodex.model

enum class RemodexGptVoiceStatus {
    READY,
    DISCONNECTED,
    LOGIN_REQUIRED,
    LOGIN_PENDING,
    REAUTH_REQUIRED,
    VOICE_SYNC_IN_PROGRESS,
}

fun remodexGptVoiceStatus(
    snapshot: RemodexGptAccountSnapshot,
    isConnected: Boolean,
): RemodexGptVoiceStatus {
    return when {
        !isConnected -> RemodexGptVoiceStatus.DISCONNECTED
        snapshot.needsReauth || snapshot.status == RemodexGptAccountStatus.EXPIRED ->
            RemodexGptVoiceStatus.REAUTH_REQUIRED
        snapshot.isAuthenticated && snapshot.isVoiceTokenReady ->
            RemodexGptVoiceStatus.READY
        snapshot.isAuthenticated ->
            RemodexGptVoiceStatus.VOICE_SYNC_IN_PROGRESS
        snapshot.hasActiveLogin ->
            RemodexGptVoiceStatus.LOGIN_PENDING
        else ->
            RemodexGptVoiceStatus.LOGIN_REQUIRED
    }
}

fun remodexGptHintText(
    snapshot: RemodexGptAccountSnapshot,
    isConnected: Boolean,
): String? {
    return when (remodexGptVoiceStatus(snapshot = snapshot, isConnected = isConnected)) {
        RemodexGptVoiceStatus.READY -> null
        RemodexGptVoiceStatus.REAUTH_REQUIRED ->
            "Voice on this bridge needs a fresh ChatGPT sign-in on your Mac."
        RemodexGptVoiceStatus.VOICE_SYNC_IN_PROGRESS ->
            "Waiting for voice sync..."
        RemodexGptVoiceStatus.LOGIN_PENDING ->
            "Finish the ChatGPT sign-in flow in the browser on your Mac."
        RemodexGptVoiceStatus.DISCONNECTED ->
            "Connect to your bridge first."
        RemodexGptVoiceStatus.LOGIN_REQUIRED ->
            "ChatGPT voice uses the account already signed in on your Mac."
    }
}

fun remodexGptSummaryText(
    snapshot: RemodexGptAccountSnapshot,
    isConnected: Boolean,
): String {
    return when (remodexGptVoiceStatus(snapshot = snapshot, isConnected = isConnected)) {
        RemodexGptVoiceStatus.READY ->
            "Using the ChatGPT session from your paired Mac bridge."
        RemodexGptVoiceStatus.REAUTH_REQUIRED ->
            "Refresh the ChatGPT sign-in on your paired Mac."
        RemodexGptVoiceStatus.VOICE_SYNC_IN_PROGRESS ->
            "Signed in. Waiting for voice sync from your Mac."
        RemodexGptVoiceStatus.LOGIN_PENDING ->
            "Finish the browser sign-in flow on your paired Mac."
        RemodexGptVoiceStatus.DISCONNECTED ->
            "Connect to your paired Mac before checking ChatGPT voice."
        RemodexGptVoiceStatus.LOGIN_REQUIRED ->
            "Sign in to ChatGPT on the paired Mac, not on this phone."
    }
}
