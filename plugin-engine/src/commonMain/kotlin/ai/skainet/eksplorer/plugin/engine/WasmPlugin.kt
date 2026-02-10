package ai.skainet.eksplorer.plugin.engine

import ai.skainet.eksplorer.plugin.PluginDescriptor
import ai.skainet.eksplorer.plugin.PluginInput
import ai.skainet.eksplorer.plugin.PluginOutput
import ai.skainet.eksplorer.plugin.PluginSerializer
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Memory
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.runtime.value.NumberValue

class WasmPlugin internal constructor(
    val descriptor: PluginDescriptor,
    val sourceName: String,
    private val store: Store,
    private val instance: Instance,
    private val memory: Memory,
    private val hasOnUnload: Boolean,
) {
    val id: String get() = descriptor.id

    private var disposed = false

    fun recognize(input: PluginInput): PluginOutput {
        check(!disposed) { "Plugin '$id' has been disposed" }

        val inputJson = PluginSerializer.encodeInput(input)
        val inputBytes = inputJson.encodeToByteArray()

        val (ptr, len) = WasmMemoryOps.writeToMemory(store, memory, instance, inputBytes)

        val resultPtr = WasmMemoryOps.invokeI32(
            store, instance, "recognize",
            listOf(NumberValue.I32(ptr), NumberValue.I32(len))
        )

        val resultJson = WasmMemoryOps.readLengthPrefixed(store, memory, resultPtr)
        return PluginSerializer.decodeOutput(resultJson)
    }

    internal fun dispose() {
        if (disposed) return
        disposed = true

        if (hasOnUnload) {
            try {
                WasmMemoryOps.invokeVoid(store, instance, "on_unload")
            } catch (_: PluginException) {
                // best-effort
            }
        }
    }
}
