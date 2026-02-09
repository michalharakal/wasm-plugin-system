package ai.skainet.eksplorer.plugin

import kotlinx.serialization.Serializable

@Serializable
data class PluginOutput(
    val recognized: Boolean,
    val family: String? = null,
    val variant: String? = null,
    val task: String? = null,
    val confidence: Double = 0.0,
    val metadata: Map<String, String>? = null,
)
