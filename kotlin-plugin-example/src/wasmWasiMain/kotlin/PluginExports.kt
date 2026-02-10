@file:OptIn(kotlin.wasm.ExperimentalWasmInterop::class)

import ai.skainet.eksplorer.plugin.PluginDescriptor
import ai.skainet.eksplorer.plugin.PluginOutput
import ai.skainet.eksplorer.plugin.PluginSerializer

private val descriptor = PluginDescriptor(
    id = "kotlin-wasm-plugin",
    name = "Kotlin WASM Plugin",
    version = "0.1.0",
    description = "Example plugin written in Kotlin/WASM",
    supportedFormats = listOf("onnx", "gguf"),
)

@WasmExport("plugin_alloc")
fun pluginAlloc(size: Int): Int = MemoryBridge.alloc(size)

@WasmExport("plugin_dealloc")
fun pluginDealloc(ptr: Int, size: Int) = MemoryBridge.dealloc(ptr, size)

@WasmExport("plugin_info")
fun pluginInfo(): Int {
    val json = PluginSerializer.encodeDescriptor(descriptor)
    return MemoryBridge.writeLengthPrefixed(json)
}

@WasmExport("recognize")
fun recognize(ptr: Int, len: Int): Int {
    val bytes = MemoryBridge.readBytes(ptr, len)
    val inputJson = bytes.decodeToString()
    val input = PluginSerializer.decodeInput(inputJson)

    val hasC2f = "C2f" in input.detectedBlocks
    val hasSPPF = "SPPF" in input.detectedBlocks
    val hasDetect = "Detect" in input.detectedBlocks
    val isYolo = hasC2f && hasSPPF && hasDetect

    val output = if (isYolo) {
        PluginOutput(
            recognized = true,
            family = "YOLOv8",
            variant = "n",
            task = "detect",
            confidence = 0.92,
            metadata = mapOf("runtime" to "kotlin-wasm"),
        )
    } else {
        PluginOutput(
            recognized = false,
            metadata = mapOf("runtime" to "kotlin-wasm"),
        )
    }

    val outputJson = PluginSerializer.encodeOutput(output)
    return MemoryBridge.writeLengthPrefixed(outputJson)
}

@WasmExport("on_load")
fun onLoad() {
    // No-op
}

@WasmExport("on_unload")
fun onUnload() {
    // No-op
}
