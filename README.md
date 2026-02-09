# Plugin Architecture
Evalute posibility to run KMP apps with WASM based plugins.

## Technology Stack

| Component | Technology | Version | Link |
|-----------|------------|---------|------|
| WASM Runtime | Chasm | 1.3.1 | [GitHub](https://github.com/CharlieTap/chasm) |
| Host Language | Kotlin Multiplatform | 2.3.0 | [kotlinlang.org](https://kotlinlang.org) |
| Build System | Gradle | 8.12 | [gradle.org](https://gradle.org) |
| JVM Toolchain | Java | 21 | - |
| Serialization | kotlinx.serialization | 1.7.3 | [GitHub](https://github.com/Kotlin/kotlinx.serialization) |
| Plugin Language | Rust | 2021 edition | [rust-lang.org](https://rust-lang.org) |
| WASM Target | wasm32-unknown-unknown | - | - |

## Architecture Overview

```mermaid
graph TB
    subgraph Application["Host Application"]
        UI[UI Layer]
        PE[PluginEngine]
        WP[WasmPlugin]
        MO[WasmMemoryOps]
    end

    subgraph API["Plugin API"]
        PD[PluginDescriptor]
        PI[PluginInput]
        PO[PluginOutput]
        PS[PluginSerializer]
    end

    subgraph Runtime["WASM Runtime"]
        CH[Chasm 1.3.1]
        ST[Store]
        IN[Instance]
        MEM[Linear Memory]
    end

    subgraph Plugins["WASM Plugins"]
        P1[minimal-plugin.wasm]
        P2[yolo-detector.wasm]
        PN[custom-plugin.wasm]
    end

    UI --> PE
    PE --> WP
    WP --> MO
    WP --> PS
    MO --> CH
    PE --> PD
    WP --> PI
    WP --> PO

    CH --> ST
    ST --> IN
    IN --> MEM

    CH --> P1
    CH --> P2
    CH --> PN

    style Application fill:#e1f5fe
    style API fill:#f3e5f5
    style Runtime fill:#fff3e0
    style Plugins fill:#e8f5e9
```

## Project Structure

```mermaid
graph LR
    subgraph plugin-api["plugin-api module"]
        PD[PluginDescriptor]
        PI[PluginInput]
        PO[PluginOutput]
        WS[WeightStats]
        SER[PluginSerializer]
    end

    subgraph plugin-engine["plugin-engine module"]
        PE[PluginEngine]
        WP[WasmPlugin]
        MO[WasmMemoryOps]
        LL[PluginLifecycleListener]
        EX[PluginException]
    end

    subgraph chasm-test-cli["chasm-test-cli"]
        CT[ChasmTest]
    end

    PE --> PD
    PE --> PI
    PE --> PO
    WP --> SER
    PI --> WS
    CT --> PE

    style plugin-api fill:#f3e5f5
    style plugin-engine fill:#e1f5fe
    style chasm-test-cli fill:#fff3e0
```

### Directory Layout

```
wasm-plugin-system/
├── plugin-api/                          # Shared types (KMP)
│   └── src/commonMain/kotlin/
│       └── ai/skainet/eksplorer/plugin/
│           ├── PluginDescriptor.kt      # Plugin metadata
│           ├── PluginInput.kt           # Recognition input
│           ├── PluginOutput.kt          # Recognition output
│           ├── WeightStats.kt           # Weight statistics
│           └── PluginSerializer.kt      # JSON codec
│
├── plugin-engine/                       # WASM runtime (KMP)
│   └── src/commonMain/kotlin/
│       └── ai/skainet/eksplorer/plugin/engine/
│           ├── PluginEngine.kt          # Plugin lifecycle manager
│           ├── WasmPlugin.kt            # Single plugin instance
│           ├── WasmMemoryOps.kt         # Low-level memory I/O
│           ├── PluginLifecycleListener.kt # Load/unload callbacks
│           └── PluginException.kt       # Typed error hierarchy
│
├── rust-plugin-example/                 # Reference plugin
│   ├── src/lib.rs
│   ├── Cargo.toml
│   ├── Dockerfile
│   └── build.sh
│
├── chasm-test-cli/                      # Debug tool
│   └── src/main/kotlin/ChasmTest.kt
│
└── plugins/                             # Compiled .wasm files
    └── minimal-plugin.wasm
```

## Plugin API

### Data Types

```mermaid
classDiagram
    class PluginDescriptor {
        +id: String
        +name: String
        +version: String
        +description: String
        +supportedFormats: List~String~
        +metadata: Map~String,String~
    }

    class PluginInput {
        +totalParams: Long
        +layerCount: Int
        +layerTypes: Map~String,Int~
        +detectedBlocks: List~String~
        +inputShape: List~Int~
        +outputShapes: List~List~Int~~
        +weightStats: WeightStats?
        +format: String
        +fileSizeBytes: Long
        +embeddedMetadata: Map~String,String~
    }

    class PluginOutput {
        +recognized: Boolean
        +family: String?
        +variant: String?
        +task: String?
        +confidence: Double
        +metadata: Map~String,String~?
    }

    class WeightStats {
        +mean: Double
        +std: Double
        +min: Double
        +max: Double
        +sparsity: Double
    }

    class PluginSerializer {
        -json: Json
        +encodeInput(PluginInput) String
        +decodeOutput(String) PluginOutput
        +decodeDescriptor(String) PluginDescriptor
    }

    PluginInput --> WeightStats
    PluginSerializer ..> PluginInput : encodes
    PluginSerializer ..> PluginOutput : decodes
    PluginSerializer ..> PluginDescriptor : decodes
```

### Plugin Engine Classes

```mermaid
classDiagram
    class PluginEngine {
        -plugins: Map~String,WasmPlugin~
        -listeners: List~PluginLifecycleListener~
        +loadPlugin(bytes: ByteArray, sourceName: String) WasmPlugin
        +unloadPlugin(id: String) Boolean
        +unloadAll()
        +getPlugin(id: String) WasmPlugin?
        +getDescriptors() List~PluginDescriptor~
        +recognizeFirst(input: PluginInput, format: String?) PluginOutput?
        +recognizeAll(input: PluginInput) Map~String,PluginOutput~
        +addLifecycleListener(listener: PluginLifecycleListener)
        +removeLifecycleListener(listener: PluginLifecycleListener)
    }

    class WasmPlugin {
        +descriptor: PluginDescriptor
        +sourceName: String
        +id: String
        -store: Store
        -instance: Instance
        -memory: Memory
        -hasOnUnload: Boolean
        -disposed: Boolean
        +recognize(input: PluginInput) PluginOutput
        ~dispose()
    }

    class WasmMemoryOps {
        <<internal object>>
        +extractI32(ExecutionValue) Int
        +invokeI32(Store, Instance, String, args) Int
        +invokeVoid(Store, Instance, String, args)
        +pluginAlloc(Store, Instance, size: Int) Int
        +pluginDealloc(Store, Instance, ptr: Int, size: Int)
        +readLengthPrefixed(Store, Memory, ptr: Int) String
        +writeToMemory(Store, Memory, Instance, ByteArray) Pair~Int,Int~
    }

    class PluginLifecycleListener {
        <<interface>>
        +onPluginLoaded(PluginDescriptor)
        +onPluginUnloaded(PluginDescriptor)
        +onPluginLoadFailed(sourceName: String, PluginException)
    }

    class PluginException {
        <<sealed>>
    }
    class ModuleDecodeError
    class InstantiationError
    class MissingExport
    class InvocationError
    class MemoryError
    class DescriptorParseError
    class DuplicatePluginError

    PluginEngine --> WasmPlugin : manages
    PluginEngine --> PluginLifecycleListener : notifies
    WasmPlugin --> WasmMemoryOps : uses
    PluginEngine ..> PluginException : throws

    PluginException <|-- ModuleDecodeError
    PluginException <|-- InstantiationError
    PluginException <|-- MissingExport
    PluginException <|-- InvocationError
    PluginException <|-- MemoryError
    PluginException <|-- DescriptorParseError
    PluginException <|-- DuplicatePluginError
```

### Module Dependencies

```mermaid
graph BT
    subgraph External
        CHASM["chasm 1.3.1"]
        KSJ["kotlinx-serialization-json 1.7.3"]
    end

    API["plugin-api"] --> KSJ
    ENGINE["plugin-engine"] --> API
    ENGINE --> CHASM
    ENGINE --> KSJ
    CLI["chasm-test-cli"] --> ENGINE

    style External fill:#fff3e0
    style API fill:#f3e5f5
    style ENGINE fill:#e1f5fe
    style CLI fill:#fff3e0
```

## Data Flow

### Plugin Loading Sequence

```mermaid
sequenceDiagram
    participant App as Application
    participant PE as PluginEngine
    participant MO as WasmMemoryOps
    participant CH as Chasm
    participant WP as WasmPlugin
    participant WM as WASM Module

    App->>PE: loadPlugin(bytes, sourceName)
    PE->>CH: store()
    CH-->>PE: Store
    PE->>CH: module(bytes)
    CH-->>PE: Module
    PE->>CH: instance(store, module, [])
    CH-->>PE: Instance
    PE->>CH: exports(instance)
    CH-->>PE: [memory, functions...]

    PE->>PE: validate required exports

    opt on_load present
        PE->>MO: invokeVoid(store, instance, "on_load")
        MO->>CH: invoke("on_load")
        CH->>WM: on_load()
    end

    PE->>MO: invokeI32(store, instance, "plugin_info")
    MO->>CH: invoke("plugin_info")
    CH->>WM: plugin_info()
    WM-->>CH: ptr (i32)
    CH-->>MO: ptr
    PE->>MO: readLengthPrefixed(store, memory, ptr)
    MO->>CH: readInt(ptr)
    CH-->>MO: length
    MO->>CH: readBytes(ptr+4, length)
    CH-->>MO: JSON bytes
    MO-->>PE: JSON string

    PE->>PE: PluginSerializer.decodeDescriptor(json)
    PE->>PE: check duplicate ID

    PE->>WP: create WasmPlugin(descriptor, store, instance, memory)
    PE->>PE: notify listeners: onPluginLoaded()
    PE-->>App: WasmPlugin
```

### Model Recognition Flow

```mermaid
sequenceDiagram
    participant Caller
    participant PE as PluginEngine
    participant WP as WasmPlugin
    participant SER as PluginSerializer
    participant MO as WasmMemoryOps
    participant CH as Chasm
    participant Plugin as WASM Plugin

    Caller->>PE: recognizeFirst(input, format)
    PE->>PE: filter plugins by format

    loop For each candidate plugin
        PE->>WP: recognize(input)

        WP->>SER: encodeInput(input)
        SER-->>WP: JSON string

        Note over WP,Plugin: Memory Write
        WP->>MO: writeToMemory(store, memory, instance, jsonBytes)
        MO->>MO: pluginAlloc(store, instance, size)
        MO->>CH: invoke("plugin_alloc", size)
        CH->>Plugin: plugin_alloc(size)
        Plugin-->>CH: ptr
        CH-->>MO: ptr
        MO->>CH: writeBytes(memory, ptr, data)
        MO-->>WP: (ptr, len)

        Note over WP,Plugin: Function Call
        WP->>MO: invokeI32(store, instance, "recognize", [ptr, len])
        MO->>CH: invoke("recognize", ptr, len)
        CH->>Plugin: recognize(ptr, len)
        Plugin->>Plugin: parse JSON input
        Plugin->>Plugin: run recognition logic
        Plugin->>Plugin: serialize output
        Plugin-->>CH: result_ptr
        CH-->>MO: result_ptr
        MO-->>WP: result_ptr

        Note over WP,Plugin: Memory Read
        WP->>MO: readLengthPrefixed(store, memory, result_ptr)
        MO->>CH: readInt(result_ptr)
        CH-->>MO: length
        MO->>CH: readBytes(result_ptr+4, length)
        CH-->>MO: JSON bytes
        MO-->>WP: JSON string

        WP->>SER: decodeOutput(json)
        SER-->>WP: PluginOutput
        WP-->>PE: PluginOutput

        alt recognized == true
            PE-->>Caller: PluginOutput
        end
    end
```

### Plugin Unload Flow

```mermaid
sequenceDiagram
    participant App as Application
    participant PE as PluginEngine
    participant WP as WasmPlugin
    participant MO as WasmMemoryOps
    participant CH as Chasm
    participant WM as WASM Module

    App->>PE: unloadPlugin(id)
    PE->>PE: remove from plugins map
    PE->>WP: dispose()

    opt on_unload present
        WP->>MO: invokeVoid(store, instance, "on_unload")
        MO->>CH: invoke("on_unload")
        CH->>WM: on_unload()
        Note over WP: best-effort, errors swallowed
    end

    WP->>WP: disposed = true
    PE->>PE: notify listeners: onPluginUnloaded()
    PE-->>App: true
```

## Memory Exchange Protocol

### Data Format

```mermaid
graph LR
    subgraph "Length-Prefixed String"
        L[Length<br/>4 bytes<br/>little-endian]
        C[Content<br/>N bytes<br/>UTF-8 JSON]
    end

    L --> C

    style L fill:#ffecb3
    style C fill:#c8e6c9
```

### Memory Layout Example

```
Offset    0    1    2    3    4    5    6    7   ...   N+3
        ┌────┬────┬────┬────┬────┬────┬────┬────┬─────┬────┐
        │ 8C │ 00 │ 00 │ 00 │ 7B │ 22 │ 6E │ 61 │ ... │ 7D │
        └────┴────┴────┴────┴────┴────┴────┴────┴─────┴────┘
         └──────────────┘    └────────────────────────────┘
          Length = 140        JSON: {"name":"..."...}
          (little-endian)
```

Max payload size: 1,000,000 bytes (enforced by `WasmMemoryOps.readLengthPrefixed`).

### Host ↔ Plugin Communication

```mermaid
flowchart LR
    subgraph Host["Host (Kotlin)"]
        H1[PluginSerializer.encodeInput]
        H2[WasmMemoryOps.writeToMemory]
        H3[pluginAlloc via Chasm]
        H4[writeBytes via Chasm]
        H5[invokeI32 'recognize']
        H6[readLengthPrefixed]
        H7[PluginSerializer.decodeOutput]
    end

    subgraph Plugin["Plugin (WASM)"]
        P1[plugin_alloc]
        P2[Linear Memory]
        P3[recognize]
        P4[Write result to memory]
    end

    H1 --> H2
    H2 --> H3
    H3 --> P1
    P1 --> P2
    H4 --> P2
    H5 --> P3
    P3 --> P4
    P4 --> P2
    H6 --> P2
    H6 --> H7
```

## Plugin Interface

### Required Exports

```mermaid
classDiagram
    class WASMPlugin {
        +memory: Memory
        +plugin_alloc(size: i32) i32
        +plugin_dealloc(ptr: i32, size: i32)
        +plugin_info() i32
        +recognize(ptr: i32, len: i32) i32
    }

    class OptionalExports {
        <<optional>>
        +on_load()
        +on_unload()
    }

    class Memory {
        <<linear memory>>
        data: bytes
    }

    WASMPlugin --> Memory : exports
    WASMPlugin .. OptionalExports : may export
```

| Export | Signature | Required | Description |
|--------|-----------|----------|-------------|
| `memory` | `Memory` | Yes | Linear memory for data exchange |
| `plugin_alloc` | `(i32) -> i32` | Yes | Allocate N bytes, return pointer |
| `plugin_dealloc` | `(i32, i32) -> void` | Yes | Free memory at ptr with size |
| `plugin_info` | `() -> i32` | Yes | Return pointer to JSON metadata |
| `recognize` | `(i32, i32) -> i32` | Yes | Process input, return result pointer |
| `on_load` | `() -> void` | No | Called when plugin is loaded |
| `on_unload` | `() -> void` | No | Called when plugin is unloaded (best-effort) |

### JSON Examples

**PluginDescriptor:**
```json
{
  "id": "minimal-rust-plugin",
  "name": "Minimal Rust Plugin",
  "version": "0.1.0",
  "description": "A minimal example plugin written in Rust",
  "supportedFormats": ["onnx", "gguf"],
  "metadata": {}
}
```

**PluginInput:**
```json
{
  "totalParams": 3200000,
  "layerCount": 225,
  "layerTypes": {"Conv": 53, "BatchNorm": 52, "SiLU": 51},
  "detectedBlocks": ["C2f", "SPPF", "Detect"],
  "inputShape": [1, 3, 640, 640],
  "outputShapes": [[1, 84, 8400]],
  "weightStats": {"mean": 0.0, "std": 0.1, "min": -1.0, "max": 1.0, "sparsity": 0.0},
  "format": "onnx",
  "fileSizeBytes": 6500000,
  "embeddedMetadata": {}
}
```

**PluginOutput:**
```json
{
  "recognized": true,
  "family": "YOLO",
  "variant": "v8n",
  "task": "detect",
  "confidence": 0.95,
  "metadata": {"detected_by": "minimal-rust-plugin"}
}
```

### Error Handling

```mermaid
classDiagram
    class PluginException {
        <<sealed>>
        +message: String
        +cause: Throwable?
    }

    class ModuleDecodeError {
        Failed to decode WASM bytes
    }
    class InstantiationError {
        Failed to create instance
    }
    class MissingExport {
        Required export not found
    }
    class InvocationError {
        Error calling WASM function
    }
    class MemoryError {
        Memory read/write failed
    }
    class DescriptorParseError {
        Invalid plugin_info JSON
    }
    class DuplicatePluginError {
        Plugin ID already loaded
    }

    PluginException <|-- ModuleDecodeError
    PluginException <|-- InstantiationError
    PluginException <|-- MissingExport
    PluginException <|-- InvocationError
    PluginException <|-- MemoryError
    PluginException <|-- DescriptorParseError
    PluginException <|-- DuplicatePluginError
```

## Usage

### Loading Plugins

```kotlin
val engine = PluginEngine()

// Register lifecycle listener
engine.addLifecycleListener(object : PluginLifecycleListener {
    override fun onPluginLoaded(descriptor: PluginDescriptor) {
        println("Loaded: ${descriptor.name} v${descriptor.version}")
    }
    override fun onPluginUnloaded(descriptor: PluginDescriptor) {
        println("Unloaded: ${descriptor.id}")
    }
    override fun onPluginLoadFailed(sourceName: String, error: PluginException) {
        println("Failed to load $sourceName: ${error.message}")
    }
})

// Load a plugin from bytes
val wasmBytes = File("plugins/minimal-plugin.wasm").readBytes()
val plugin = engine.loadPlugin(wasmBytes, "minimal-plugin.wasm")

// Check loaded plugins
engine.getDescriptors().forEach { desc ->
    println("${desc.id}: ${desc.name} v${desc.version}")
    println("  Formats: ${desc.supportedFormats}")
}
```

### Running Recognition

```kotlin
val input = PluginInput(
    totalParams = 3_200_000,
    layerCount = 225,
    layerTypes = mapOf("Conv" to 53, "BatchNorm" to 52, "SiLU" to 51),
    detectedBlocks = listOf("C2f", "SPPF", "Detect"),
    inputShape = listOf(1, 3, 640, 640),
    outputShapes = listOf(listOf(1, 84, 8400)),
    format = "onnx",
    fileSizeBytes = 6_500_000,
)

// First match (filtered by format)
val result = engine.recognizeFirst(input, format = "onnx")
if (result != null && result.recognized) {
    println("Detected: ${result.family} ${result.variant}")
    println("Task: ${result.task}")
    println("Confidence: ${result.confidence}")
}

// Or get results from all plugins
val allResults = engine.recognizeAll(input)
allResults.forEach { (pluginId, output) ->
    println("$pluginId: recognized=${output.recognized}")
}
```

### Unloading Plugins

```kotlin
// Unload a single plugin (calls on_unload if exported)
engine.unloadPlugin("minimal-rust-plugin")

// Unload all plugins
engine.unloadAll()
```

### Building a Plugin

```bash
# Clone the example
cp -r rust-plugin-example my-plugin
cd my-plugin

# Edit src/lib.rs with your logic

# Build with Docker
./build.sh

# Install
cp my-plugin.wasm ~/.tensor-eksplorer/plugins/
```

## Lessons Learned

### 1. Kotlin/WASM Incompatibility

```mermaid
graph LR
    K[Kotlin/WASM] -->|uses| GC[WasmGC Instructions]
    GC -->|byte 0x06| X[DecodeError]
    R[Rust wasm32] -->|standard| W[WASM 1.0]
    W -->|compatible| CH[Chasm]

    style X fill:#ffcdd2
    style CH fill:#c8e6c9
```

**Problem:** Plugins compiled with Kotlin/WASM fail with `DecodeError(UnknownInstruction(byte=6))`.

**Cause:** Kotlin/WASM uses WasmGC proposal instructions that Chasm cannot decode.

**Solution:** Use Rust with `wasm32-unknown-unknown` target.

### 2. No Built-in String Passing

**Problem:** Chasm has no high-level API for passing strings between host and guest.

**Solution:** Implemented length-prefixed protocol with manual memory management:
- `plugin_alloc` / `plugin_dealloc` for memory
- `writeBytes` / `readBytes` for data transfer
- `readInt` for length prefix

### 3. Memory API Discovery

**Problem:** Chasm memory APIs not documented.

**Solution:** Found in source code:
```kotlin
import io.github.charlietap.chasm.embedding.memory.readInt
import io.github.charlietap.chasm.embedding.memory.readBytes
import io.github.charlietap.chasm.embedding.memory.writeBytes
```

### 4. Java Version Mismatch

**Problem:** `UnsupportedClassVersionError` across modules.

**Solution:** Consistent toolchain in all `build.gradle.kts`:
```kotlin
kotlin {
    jvmToolchain(21)
}
```

## Debugging

### chasm-test-cli

Test plugins directly:

```bash
cd chasm-test-cli
./gradlew run --args="../plugins/minimal-plugin.wasm"
```

Output:
```
=== Module Exports ===
  memory: Memory
  plugin_alloc: Function
  plugin_dealloc: Function
  plugin_info: Function
  recognize: Function

=== Calling plugin_info ===
Pointer value: 1114536 (0x1101a8)
Length prefix: 140 bytes

Plugin Info JSON:
{"name":"Minimal Rust Plugin","version":"0.1.0",...}
```

### Inspecting WASM Files

```bash
# List exports
wasm-objdump -x plugin.wasm | grep -A20 "Export"

# Check for Kotlin/WASM (will contain "kotlin" in imports)
wasm-objdump -x plugin.wasm | grep -i kotlin
```

## References

| Resource | Link |
|----------|------|
| Chasm Repository | https://github.com/CharlieTap/chasm |
| Chasm Memory API | [source](https://github.com/CharlieTap/chasm/tree/main/chasm/src/commonMain/kotlin/io/github/charlietap/chasm/embedding/memory) |
| WebAssembly Spec | https://webassembly.github.io/spec/core/ |
| Rust WASM Book | https://rustwasm.github.io/docs/book/ |

---

*Last updated: 2025-01-27 | Plugin System v1.1.0*
