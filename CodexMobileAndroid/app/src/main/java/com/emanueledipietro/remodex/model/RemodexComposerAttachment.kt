package com.emanueledipietro.remodex.model

import kotlinx.serialization.Serializable

@Serializable
data class RemodexComposerAttachment(
    val id: String,
    val uriString: String,
    val displayName: String,
    val payloadDataUrl: String? = null,
)
