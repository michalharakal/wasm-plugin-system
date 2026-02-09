package ai.skainet.eksplorer.plugin.engine

import ai.skainet.eksplorer.plugin.PluginDescriptor
import ai.skainet.eksplorer.plugin.PluginInput
import ai.skainet.eksplorer.plugin.PluginOutput
import ai.skainet.eksplorer.plugin.PluginSerializer
import io.github.charlietap.chasm.embedding.exports
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Memory
import io.github.charlietap.chasm.embedding.store

class PluginEngine {

    private val plugins = mutableMapOf<String, WasmPlugin>()
    private val listeners = mutableListOf<PluginLifecycleListener>()

    fun loadPlugin(bytes: ByteArray, sourceName: String): WasmPlugin {
        // 1. Create store
        val pluginStore = store()

        // 2. Decode module
        val mod = when (val result = module(bytes)) {
            is ChasmResult.Success -> result.result
            is ChasmResult.Error -> {
                val ex = PluginException.ModuleDecodeError(result.error.toString())
                notifyLoadFailed(sourceName, ex)
                throw ex
            }
        }

        // 3. Instantiate
        val inst = when (val result = instance(pluginStore, mod, emptyList())) {
            is ChasmResult.Success -> result.result
            is ChasmResult.Error -> {
                val ex = PluginException.InstantiationError(result.error.toString())
                notifyLoadFailed(sourceName, ex)
                throw ex
            }
        }

        // 4. Validate required exports
        val exportList = exports(inst)
        val exportNames = exportList.map { it.name }.toSet()

        val requiredExports = listOf("memory", "plugin_alloc", "plugin_dealloc", "plugin_info", "recognize")
        for (name in requiredExports) {
            if (name !in exportNames) {
                val ex = PluginException.MissingExport(name)
                notifyLoadFailed(sourceName, ex)
                throw ex
            }
        }

        val memory = exportList.first { it.value is Memory }.value as Memory
        val hasOnLoad = "on_load" in exportNames
        val hasOnUnload = "on_unload" in exportNames

        // 5. Call on_load() if present
        if (hasOnLoad) {
            try {
                WasmMemoryOps.invokeVoid(pluginStore, inst, "on_load")
            } catch (e: PluginException) {
                val ex = PluginException.InvocationError("on_load", e.message ?: "unknown error")
                notifyLoadFailed(sourceName, ex)
                throw ex
            }
        }

        // 6. Call plugin_info() and parse descriptor
        val descriptor: PluginDescriptor
        try {
            val infoPtr = WasmMemoryOps.invokeI32(pluginStore, inst, "plugin_info")
            val infoJson = WasmMemoryOps.readLengthPrefixed(pluginStore, memory, infoPtr)
            descriptor = PluginSerializer.decodeDescriptor(infoJson)
        } catch (e: PluginException) {
            notifyLoadFailed(sourceName, e)
            throw e
        } catch (e: Exception) {
            val ex = PluginException.DescriptorParseError(e.message ?: "unknown error", e)
            notifyLoadFailed(sourceName, ex)
            throw ex
        }

        // 7. Check for duplicate ID
        if (descriptor.id in plugins) {
            val ex = PluginException.DuplicatePluginError(descriptor.id)
            notifyLoadFailed(sourceName, ex)
            throw ex
        }

        // 8. Register plugin, notify listeners
        val plugin = WasmPlugin(
            descriptor = descriptor,
            sourceName = sourceName,
            store = pluginStore,
            instance = inst,
            memory = memory,
            hasOnUnload = hasOnUnload,
        )
        plugins[descriptor.id] = plugin

        for (listener in listeners) {
            listener.onPluginLoaded(descriptor)
        }

        return plugin
    }

    fun unloadPlugin(id: String): Boolean {
        val plugin = plugins.remove(id) ?: return false
        plugin.dispose()

        for (listener in listeners) {
            listener.onPluginUnloaded(plugin.descriptor)
        }
        return true
    }

    fun unloadAll() {
        val ids = plugins.keys.toList()
        for (id in ids) {
            unloadPlugin(id)
        }
    }

    fun getPlugin(id: String): WasmPlugin? = plugins[id]

    fun getDescriptors(): List<PluginDescriptor> = plugins.values.map { it.descriptor }

    fun recognizeFirst(input: PluginInput, format: String? = null): PluginOutput? {
        val candidates = if (format != null) {
            plugins.values.filter { format in it.descriptor.supportedFormats }
        } else {
            plugins.values.toList()
        }
        for (plugin in candidates) {
            val result = plugin.recognize(input)
            if (result.recognized) return result
        }
        return null
    }

    fun recognizeAll(input: PluginInput): Map<String, PluginOutput> {
        return plugins.mapValues { (_, plugin) -> plugin.recognize(input) }
    }

    fun addLifecycleListener(listener: PluginLifecycleListener) {
        listeners.add(listener)
    }

    fun removeLifecycleListener(listener: PluginLifecycleListener) {
        listeners.remove(listener)
    }

    private fun notifyLoadFailed(sourceName: String, error: PluginException) {
        for (listener in listeners) {
            listener.onPluginLoadFailed(sourceName, error)
        }
    }
}
