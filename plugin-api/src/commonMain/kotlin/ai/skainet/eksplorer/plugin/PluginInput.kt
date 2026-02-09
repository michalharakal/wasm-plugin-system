package ai.skainet.eksplorer.plugin

import kotlinx.serialization.Serializable

@Serializable
data class PluginInput(
    val totalParams: Long,
    val layerCount: Int,
    val layerTypes: Map<String, Int> = emptyMap(),
    val detectedBlocks: List<String> = emptyList(),
    val inputShape: List<Int> = emptyList(),
    val outputShapes: List<List<Int>> = emptyList(),
    val weightStats: WeightStats? = null,
    val format: String = "",
    val fileSizeBytes: Long = 0,
    val embeddedMetadata: Map<String, String> = emptyMap(),
)
