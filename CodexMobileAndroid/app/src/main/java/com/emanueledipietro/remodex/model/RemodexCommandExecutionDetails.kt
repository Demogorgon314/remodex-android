package com.emanueledipietro.remodex.model

enum class RemodexCommandExecutionLiveStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    STOPPED,
}

enum class RemodexCommandExecutionSource {
    AGENT,
    USER_SHELL,
    UNIFIED_EXEC_STARTUP,
    UNIFIED_EXEC_INTERACTION,
}

fun RemodexCommandExecutionDetails.isRunningBackgroundTerminal(): Boolean {
    return liveStatus == RemodexCommandExecutionLiveStatus.RUNNING &&
        source == RemodexCommandExecutionSource.UNIFIED_EXEC_STARTUP
}

data class RemodexCommandExecutionDetails(
    val fullCommand: String,
    val cwd: String? = null,
    val exitCode: Int? = null,
    val durationMs: Int? = null,
    val outputTail: String = "",
    val liveStatus: RemodexCommandExecutionLiveStatus? = null,
    val source: RemodexCommandExecutionSource? = null,
) {
    fun appendedOutput(chunk: String): RemodexCommandExecutionDetails {
        if (chunk.isEmpty()) {
            return this
        }
        val combined = outputTail + chunk
        val trimmed = combined
            .split('\n')
            .takeLast(MaxOutputLines)
            .joinToString(separator = "\n")
        return copy(outputTail = trimmed)
    }

    companion object {
        const val MaxOutputLines = 30
    }
}
