import ai.skainet.eksplorer.plugin.PluginDescriptor
import ai.skainet.eksplorer.plugin.PluginInput
import ai.skainet.eksplorer.plugin.WeightStats
import ai.skainet.eksplorer.plugin.engine.PluginEngine
import ai.skainet.eksplorer.plugin.engine.PluginException
import ai.skainet.eksplorer.plugin.engine.PluginLifecycleListener
import java.io.File

fun main(args: Array<String>) {
    val wasmPath = args.firstOrNull() ?: "plugins/minimal-plugin.wasm"
    val wasmFile = File(wasmPath)

    if (!wasmFile.exists()) {
        println("ERROR: WASM file not found: ${wasmFile.absolutePath}")
        return
    }

    val engine = PluginEngine()

    engine.addLifecycleListener(object : PluginLifecycleListener {
        override fun onPluginLoaded(descriptor: PluginDescriptor) {
            println("[lifecycle] Plugin loaded: ${descriptor.id} (${descriptor.name} v${descriptor.version})")
        }

        override fun onPluginUnloaded(descriptor: PluginDescriptor) {
            println("[lifecycle] Plugin unloaded: ${descriptor.id}")
        }

        override fun onPluginLoadFailed(sourceName: String, error: PluginException) {
            println("[lifecycle] Plugin load failed ($sourceName): ${error.message}")
        }
    })

    println("Loading WASM module: ${wasmFile.absolutePath}")
    val plugin = engine.loadPlugin(wasmFile.readBytes(), wasmFile.name)

    println("\n=== Plugin Info ===")
    println("  ID:          ${plugin.descriptor.id}")
    println("  Name:        ${plugin.descriptor.name}")
    println("  Version:     ${plugin.descriptor.version}")
    println("  Description: ${plugin.descriptor.description}")
    println("  Formats:     ${plugin.descriptor.supportedFormats}")

    println("\n=== Calling recognize ===")
    val input = PluginInput(
        totalParams = 3_200_000,
        layerCount = 225,
        layerTypes = mapOf("Conv" to 53, "BatchNorm" to 52, "SiLU" to 51),
        detectedBlocks = listOf("C2f", "SPPF", "Detect"),
        inputShape = listOf(1, 3, 640, 640),
        outputShapes = listOf(listOf(1, 84, 8400)),
        weightStats = WeightStats(mean = 0.0, std = 0.1, min = -1.0, max = 1.0, sparsity = 0.0),
        format = "onnx",
        fileSizeBytes = 6_500_000,
    )

    val output = plugin.recognize(input)
    println("  Recognized: ${output.recognized}")
    println("  Family:     ${output.family}")
    println("  Variant:    ${output.variant}")
    println("  Task:       ${output.task}")
    println("  Confidence: ${output.confidence}")

    println("\n=== Cleanup ===")
    engine.unloadAll()
    println("\n=== Done ===")
}
