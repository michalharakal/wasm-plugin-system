package ai.skainet.eksplorer.plugin

import kotlinx.serialization.json.Json

object PluginSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun encodeInput(input: PluginInput): String =
        json.encodeToString(PluginInput.serializer(), input)

    fun decodeOutput(raw: String): PluginOutput =
        json.decodeFromString(PluginOutput.serializer(), raw)

    fun decodeDescriptor(raw: String): PluginDescriptor =
        json.decodeFromString(PluginDescriptor.serializer(), raw)

    fun encodeDescriptor(descriptor: PluginDescriptor): String =
        json.encodeToString(PluginDescriptor.serializer(), descriptor)

    fun encodeOutput(output: PluginOutput): String =
        json.encodeToString(PluginOutput.serializer(), output)

    fun decodeInput(raw: String): PluginInput =
        json.decodeFromString(PluginInput.serializer(), raw)
}
