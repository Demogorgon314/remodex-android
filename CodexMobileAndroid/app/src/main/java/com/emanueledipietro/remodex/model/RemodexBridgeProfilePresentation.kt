package com.emanueledipietro.remodex.model

data class RemodexBridgeProfilePresentation(
    val profileId: String,
    val title: String,
    val name: String,
    val systemName: String? = null,
    val detail: String? = null,
    val isActive: Boolean = false,
    val isConnected: Boolean = false,
)
