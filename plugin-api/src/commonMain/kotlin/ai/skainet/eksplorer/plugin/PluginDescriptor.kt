package ai.skainet.eksplorer.plugin

import kotlinx.serialization.Serializable

@Serializable
data class PluginDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val supportedFormats: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)
