package ai.skainet.eksplorer.plugin.engine

import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.memory.readBytes
import io.github.charlietap.chasm.embedding.memory.readInt
import io.github.charlietap.chasm.embedding.memory.writeBytes
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Instance
import io.github.charlietap.chasm.embedding.shapes.Memory
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.runtime.value.ExecutionValue
import io.github.charlietap.chasm.runtime.value.NumberValue

internal object WasmMemoryOps {

    fun extractI32(value: ExecutionValue): Int =
        (value as NumberValue<*>).value as Int

    fun invokeI32(store: Store, instance: Instance, funcName: String, args: List<ExecutionValue> = emptyList()): Int {
        val result = invoke(store, instance, funcName, args)
        return when (result) {
            is ChasmResult.Success -> {
                if (result.result.isEmpty()) {
                    throw PluginException.InvocationError(funcName, "No return value")
                }
                extractI32(result.result[0])
            }
            is ChasmResult.Error -> {
                throw PluginException.InvocationError(funcName, result.error.toString())
            }
        }
    }

    fun invokeVoid(store: Store, instance: Instance, funcName: String, args: List<ExecutionValue> = emptyList()) {
        val result = invoke(store, instance, funcName, args)
        if (result is ChasmResult.Error) {
            throw PluginException.InvocationError(funcName, result.error.toString())
        }
    }

    fun pluginAlloc(store: Store, instance: Instance, size: Int): Int =
        invokeI32(store, instance, "plugin_alloc", listOf(NumberValue.I32(size)))

    fun pluginDealloc(store: Store, instance: Instance, ptr: Int, size: Int) {
        invokeVoid(store, instance, "plugin_dealloc", listOf(NumberValue.I32(ptr), NumberValue.I32(size)))
    }

    fun readLengthPrefixed(store: Store, memory: Memory, ptr: Int): String {
        val lengthResult = readInt(store, memory, ptr)
        val length = when (lengthResult) {
            is ChasmResult.Success -> lengthResult.result
            is ChasmResult.Error ->
                throw PluginException.MemoryError("Failed to read length at ptr=$ptr: ${lengthResult.error}")
        }

        if (length <= 0 || length > 1_000_000) {
            throw PluginException.MemoryError("Invalid length value: $length at ptr=$ptr")
        }

        val buffer = ByteArray(length)
        val bytesResult = readBytes(store, memory, buffer, ptr + 4, length)
        when (bytesResult) {
            is ChasmResult.Success -> { /* buffer filled */ }
            is ChasmResult.Error ->
                throw PluginException.MemoryError("Failed to read $length bytes at ptr=${ptr + 4}: ${bytesResult.error}")
        }

        return buffer.decodeToString()
    }

    fun writeToMemory(store: Store, memory: Memory, instance: Instance, data: ByteArray): Pair<Int, Int> {
        val ptr = pluginAlloc(store, instance, data.size)
        val writeResult = writeBytes(store, memory, ptr, data)
        when (writeResult) {
            is ChasmResult.Success -> { /* ok */ }
            is ChasmResult.Error ->
                throw PluginException.MemoryError("Failed to write ${data.size} bytes at ptr=$ptr: ${writeResult.error}")
        }
        return ptr to data.size
    }
}
