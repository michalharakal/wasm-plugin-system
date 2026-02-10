import ai.skainet.eksplorer.plugin.PluginDescriptor
import ai.skainet.eksplorer.plugin.PluginInput
import ai.skainet.eksplorer.plugin.WeightStats
import ai.skainet.eksplorer.plugin.engine.PluginEngine
import ai.skainet.eksplorer.plugin.engine.PluginException
import ai.skainet.eksplorer.plugin.engine.PluginLifecycleListener
import java.io.File
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    val paths = if (args.isEmpty()) listOf("plugins/") else args.toList()

    // Collect all .wasm files from args (files or directories)
    val wasmFiles = paths.flatMap { path ->
        val f = File(path)
        when {
            f.isDirectory -> f.listFiles()?.filter { it.extension == "wasm" }?.sortedBy { it.name } ?: emptyList()
            f.isFile && f.extension == "wasm" -> listOf(f)
            else -> {
                println("WARNING: Skipping '$path' (not a .wasm file or directory)")
                emptyList()
            }
        }
    }

    if (wasmFiles.isEmpty()) {
        println("ERROR: No .wasm files found in: ${paths.joinToString()}")
        return
    }

    println("Found ${wasmFiles.size} WASM file(s): ${wasmFiles.joinToString { it.name }}")

    val engine = PluginEngine()

    engine.addLifecycleListener(object : PluginLifecycleListener {
        override fun onPluginLoaded(descriptor: PluginDescriptor) {
            println("  [lifecycle] Loaded: ${descriptor.id} (${descriptor.name} v${descriptor.version})")
        }

        override fun onPluginUnloaded(descriptor: PluginDescriptor) {
            println("  [lifecycle] Unloaded: ${descriptor.id}")
        }

        override fun onPluginLoadFailed(sourceName: String, error: PluginException) {
            println("  [lifecycle] Load failed ($sourceName): ${error.message}")
        }
    })

    // Load all plugins
    println("\n=== Loading Plugins ===")
    for (wasmFile in wasmFiles) {
        print("Loading ${wasmFile.name}...")
        try {
            val ms = measureTimeMillis {
                engine.loadPlugin(wasmFile.readBytes(), wasmFile.name)
            }
            println(" OK (${ms}ms)")
        } catch (e: PluginException) {
            println(" FAILED: ${e.message}")
        }
    }

    // Summary table
    val descriptors = engine.getDescriptors()
    if (descriptors.isEmpty()) {
        println("\nNo plugins loaded successfully.")
        return
    }

    println("\n=== Plugin Summary ===")
    println("%-25s %-25s %-10s %s".format("ID", "Name", "Version", "Formats"))
    println("-".repeat(80))
    for (d in descriptors) {
        println("%-25s %-25s %-10s %s".format(d.id, d.name, d.version, d.supportedFormats.joinToString()))
    }

    // Test 1: YOLO-positive input
    println("\n=== Test 1: YOLO-positive input ===")
    val yoloInput = PluginInput(
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

    var recognizeMs = measureTimeMillis {
        val results = engine.recognizeAll(yoloInput)
        for ((id, output) in results) {
            println("  [$id] recognized=${output.recognized}, family=${output.family}, confidence=${output.confidence}")
        }
    }
    println("  (${recognizeMs}ms)")

    // Test 2: Negative input (generic model, no YOLO blocks)
    println("\n=== Test 2: Non-YOLO input (should not recognize) ===")
    val genericInput = PluginInput(
        totalParams = 10_000_000,
        layerCount = 50,
        layerTypes = mapOf("Linear" to 24, "LayerNorm" to 25),
        detectedBlocks = emptyList(),
        inputShape = listOf(1, 512),
        outputShapes = listOf(listOf(1, 1000)),
        weightStats = WeightStats(mean = 0.0, std = 0.02, min = -0.5, max = 0.5, sparsity = 0.1),
        format = "onnx",
        fileSizeBytes = 40_000_000,
    )

    recognizeMs = measureTimeMillis {
        val results = engine.recognizeAll(genericInput)
        for ((id, output) in results) {
            println("  [$id] recognized=${output.recognized}, family=${output.family}, confidence=${output.confidence}")
        }
    }
    println("  (${recognizeMs}ms)")

    // Cleanup
    println("\n=== Cleanup ===")
    engine.unloadAll()
    println("\n=== Done ===")
}
