import io.github.charlietap.chasm.embedding.exports
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.embedding.memory.readBytes
import io.github.charlietap.chasm.embedding.memory.readInt
import io.github.charlietap.chasm.embedding.memory.writeBytes
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Memory
import io.github.charlietap.chasm.runtime.value.ExecutionValue
import io.github.charlietap.chasm.runtime.value.NumberValue
import java.io.File

fun main(args: Array<String>) {
    val wasmPath = args.firstOrNull() ?: "plugins/minimal-plugin.wasm"
    val wasmFile = File(wasmPath)

    if (!wasmFile.exists()) {
        println("ERROR: WASM file not found: ${wasmFile.absolutePath}")
        return
    }

    println("Loading WASM module: ${wasmFile.absolutePath}")
    val wasmBytes = wasmFile.readBytes()
    println("Module size: ${wasmBytes.size} bytes")

    // 1. Create store
    val store = store()

    // 2. Decode module
    val moduleResult = module(wasmBytes)
    val mod = when (moduleResult) {
        is ChasmResult.Success -> moduleResult.result
        is ChasmResult.Error -> {
            println("ERROR decoding module: ${moduleResult.error}")
            return
        }
    }
    println("Module decoded successfully")

    // 3. Instantiate (no imports needed for this plugin)
    val instanceResult = instance(store, mod, emptyList())
    val inst = when (instanceResult) {
        is ChasmResult.Success -> instanceResult.result
        is ChasmResult.Error -> {
            println("ERROR instantiating module: ${instanceResult.error}")
            return
        }
    }
    println("Module instantiated successfully")

    // 4. List exports
    val exportList = exports(inst)
    println("\n=== Module Exports ===")
    for (export in exportList) {
        val kind = when (export.value) {
            is Memory -> "Memory"
            is io.github.charlietap.chasm.embedding.shapes.Function -> "Function"
            else -> export.value::class.simpleName ?: "Unknown"
        }
        println("  ${export.name}: $kind")
    }

    // 5. Get memory export
    val memory = exportList
        .firstOrNull { it.value is Memory }
        ?.value as? Memory
    if (memory == null) {
        println("ERROR: No memory export found")
        return
    }

    // 6. Call plugin_info
    println("\n=== Calling plugin_info ===")
    val infoResult = invoke(store, inst, "plugin_info", emptyList())
    when (infoResult) {
        is ChasmResult.Success -> {
            val values = infoResult.result
            if (values.isNotEmpty()) {
                val ptr = extractI32(values[0])
                println("Pointer value: $ptr (0x${ptr.toString(16)})")
                val json = readLengthPrefixed(store, memory, ptr)
                println("Plugin Info JSON:")
                println(json)
            }
        }
        is ChasmResult.Error -> println("ERROR calling plugin_info: ${infoResult.error}")
    }

    // 7. Call recognize with test input
    println("\n=== Calling recognize ===")
    val testInput = """{"totalParams":3200000,"layerCount":225,"layerTypes":{"Conv":53,"BatchNorm":52,"SiLU":51},"detectedBlocks":["C2f","SPPF","Detect"],"inputShape":[1,3,640,640],"outputShapes":[[1,84,8400]],"weightStats":{"mean":0.0,"std":0.1,"min":-1.0,"max":1.0,"sparsity":0.0},"format":"onnx","fileSizeBytes":6500000,"embeddedMetadata":{}}"""
    println("Input (${testInput.length} chars):")
    println(testInput.take(80) + "...")

    val inputBytes = testInput.toByteArray(Charsets.UTF_8)

    // Allocate memory in the plugin for the input
    val allocResult = invoke(store, inst, "plugin_alloc", listOf(NumberValue.I32(inputBytes.size)))
    val inputPtr = when (allocResult) {
        is ChasmResult.Success -> extractI32(allocResult.result[0])
        is ChasmResult.Error -> {
            println("ERROR calling plugin_alloc: ${allocResult.error}")
            return
        }
    }
    println("Allocated ${inputBytes.size} bytes at ptr=$inputPtr")

    // Write input bytes to plugin memory
    val writeResult = writeBytes(store, memory, inputPtr, inputBytes)
    when (writeResult) {
        is ChasmResult.Success -> println("Wrote input bytes to plugin memory")
        is ChasmResult.Error -> {
            println("ERROR writing bytes: ${writeResult.error}")
            return
        }
    }

    // Call recognize(ptr, len)
    val recognizeResult = invoke(
        store, inst, "recognize",
        listOf(NumberValue.I32(inputPtr), NumberValue.I32(inputBytes.size))
    )
    when (recognizeResult) {
        is ChasmResult.Success -> {
            val values = recognizeResult.result
            if (values.isNotEmpty()) {
                val resultPtr = extractI32(values[0])
                println("Result pointer: $resultPtr (0x${resultPtr.toString(16)})")
                val json = readLengthPrefixed(store, memory, resultPtr)
                println("\nRecognition Result JSON:")
                println(json)
            }
        }
        is ChasmResult.Error -> println("ERROR calling recognize: ${recognizeResult.error}")
    }

    println("\n=== Done ===")
}

fun extractI32(value: ExecutionValue): Int {
    return (value as NumberValue<*>).value as Int
}

/**
 * Read a length-prefixed string from WASM memory.
 * Format: [4 bytes LE length][N bytes UTF-8 payload]
 */
fun readLengthPrefixed(store: io.github.charlietap.chasm.embedding.shapes.Store, memory: Memory, ptr: Int): String {
    // Read the 4-byte length prefix
    val lengthResult = readInt(store, memory, ptr)
    val length = when (lengthResult) {
        is ChasmResult.Success -> lengthResult.result
        is ChasmResult.Error -> {
            println("ERROR reading length at ptr=$ptr: ${lengthResult.error}")
            return "<error>"
        }
    }
    println("Length prefix: $length bytes")

    if (length <= 0 || length > 1_000_000) {
        println("WARNING: Suspicious length value: $length")
        return "<invalid length>"
    }

    // Read the JSON payload bytes
    val buffer = ByteArray(length)
    val bytesResult = readBytes(store, memory, buffer, ptr + 4, length)
    when (bytesResult) {
        is ChasmResult.Success -> { /* buffer is filled */ }
        is ChasmResult.Error -> {
            println("ERROR reading bytes: ${bytesResult.error}")
            return "<error>"
        }
    }

    return String(buffer, Charsets.UTF_8)
}
