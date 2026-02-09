package ai.skainet.eksplorer.plugin.engine

sealed class PluginException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class ModuleDecodeError(details: String, cause: Throwable? = null) :
        PluginException("Failed to decode WASM module: $details", cause)

    class InstantiationError(details: String, cause: Throwable? = null) :
        PluginException("Failed to instantiate WASM module: $details", cause)

    class MissingExport(exportName: String) :
        PluginException("Required export not found: $exportName")

    class InvocationError(functionName: String, details: String, cause: Throwable? = null) :
        PluginException("Error invoking '$functionName': $details", cause)

    class MemoryError(details: String, cause: Throwable? = null) :
        PluginException("Memory operation failed: $details", cause)

    class DescriptorParseError(details: String, cause: Throwable? = null) :
        PluginException("Failed to parse plugin descriptor: $details", cause)

    class DuplicatePluginError(pluginId: String) :
        PluginException("Plugin with id '$pluginId' is already loaded")
}
