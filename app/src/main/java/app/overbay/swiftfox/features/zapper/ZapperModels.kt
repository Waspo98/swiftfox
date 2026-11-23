package app.overbay.swiftfox.features.zapper

import kotlinx.serialization.Serializable

data class ZapperUiState(
    val isActive: Boolean = false,
    val selectedSelector: String? = null,
    val selectedSummary: String = "",
    val scopeDepth: Int = 0,
    val maxScopeDepth: Int = 0,
    val matchCount: Int = 0,
    val temporaryZapCount: Int = 0,
    val lastTemporaryZapSummary: String? = null
)

@Serializable
data class ZapperSelectionEvent(
    val selector: String = "",
    val summary: String = "",
    val scopeDepth: Int = 0,
    val maxScopeDepth: Int = 0,
    val matchCount: Int = 0
)

@Serializable
data class ZapperTemporaryZapEvent(
    val summary: String = "",
    val temporaryZapCount: Int = 0
)

@Serializable
data class ZapperPermanentRuleEvent(
    val selector: String = "",
    val label: String = "",
    val matchCount: Int = 0
)

@Serializable
data class ZapperUndoEvent(
    val temporaryZapCount: Int = 0
)
