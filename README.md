# Plugin Architecture
Evalute posibility to run KMP apps with WASM based plugins.

## Technology Stack

| Component | Technology | Version | Link |
|-----------|------------|---------|------|
| WASM Runtime | Chasm | 1.2.1 | [GitHub](https://github.com/CharlieTap/chasm) |
| Host Language | Kotlin Multiplatform | 2.3.0 | [kotlinlang.org](https://kotlinlang.org) |
| Plugin Language | Rust | 1.75+ | [rust-lang.org](https://rust-lang.org) |
| WASM Target | wasm32-unknown-unknown | - | - |
| Serialization | kotlinx.serialization | 1.7.3 | [GitHub](https://github.com/Kotlin/kotlinx.serialization) |

## Architecture Overview

```mermaid
graph TB
    subgraph Application["tensor-eksplorer Application"]
        UI[UI Layer]
        PS[PluginService]
        PE[PluginEngine]
    end

    subgraph Runtime["WASM Runtime"]
        WR[WasmRuntime]
        CH[Chasm 1.2.1]
    end

    subgraph Plugins["WASM Plugins"]
        P1[minimal-plugin.wasm]
        P2[yolo-detector.wasm]
        PN[custom-plugin.wasm]
    end

    UI --> PS
    PS --> PE
    PE --> WR
    WR --> CH
    CH --> P1
    CH --> P2
    CH --> PN

    style Application fill:#e1f5fe
    style Runtime fill:#fff3e0
    style Plugins fill:#e8f5e9
```

## Project Structure

```mermaid
graph LR
    subgraph Modules
        API[plugin-api]
        ENGINE[plugin-engine]
        APP[composeApp]
    end

    subgraph Types
        PI[PluginInfo]
        PIN[PluginInput]
        PO[PluginOutput]
    end

    subgraph Runtime
        WR[WasmRuntime]
        WM[WasmModule]
        PE[PluginEngine]
    end

    API --> PI
    API --> PIN
    API --> PO
    ENGINE --> WR
    ENGINE --> WM
    ENGINE --> PE
    APP --> PS[PluginService]

    PS --> PE
    PE --> WR
    WR --> WM
```

### Directory Layout

```
tensors-eKsplorer/
├── plugin-api/                          # Shared types (KMP)
│   └── src/commonMain/kotlin/
│       └── ai/skainet/eksplorer/plugin/
│           ├── PluginInfo.kt            # Metadata type
│           ├── PluginInput.kt           # Recognition input
│           ├── PluginOutput.kt          # Recognition output
│           └── PluginSerializer.kt      # JSON codec
│
├── plugin-engine/                       # WASM runtime (KMP)
│   └── src/commonMain/kotlin/
│       └── ai/skainet/eksplorer/plugin/engine/
│           ├── WasmRuntime.kt           # Chasm wrapper
│           └── PluginEngine.kt          # Plugin manager
│
├── composeApp/                          # Desktop app
│   └── src/jvmMain/kotlin/.../plugins/
│       └── PluginService.kt             # App integration
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

## Data Flow

### Plugin Loading Sequence

```mermaid
sequenceDiagram
    participant App as Application
    participant PS as PluginService
    participant PE as PluginEngine
    participant WR as WasmRuntime
    participant CH as Chasm
    participant WM as WASM Module

    App->>PS: initialize()
    PS->>PS: scan plugins directory

    loop For each .wasm file
        PS->>PE: loadPlugin(name, bytes)
        PE->>WR: loadModule(bytes)
        WR->>CH: module(bytes)
        CH-->>WR: Module
        WR->>CH: instance(store, module)
        CH-->>WR: Instance
        WR->>CH: exports(instance)
        CH-->>WR: [memory, functions...]
        WR-->>PE: WasmModule

        PE->>WM: getPluginInfo()
        WM->>CH: invoke("plugin_info")
        CH-->>WM: ptr (i32)
        WM->>CH: readInt(ptr)
        CH-->>WM: length
        WM->>CH: readBytes(ptr+4, length)
        CH-->>WM: JSON bytes
        WM-->>PE: PluginInfo

        PE-->>PS: PluginInfo
    end

    PS-->>App: plugins loaded
```

### Model Recognition Flow

```mermaid
sequenceDiagram
    participant UI as UI Layer
    participant PS as PluginService
    participant PE as PluginEngine
    participant WM as WasmModule
    participant CH as Chasm
    participant Plugin as WASM Plugin

    UI->>PS: recognize(format, tensors)
    PS->>PS: createPluginInput()
    PS->>PE: recognize(input)

    loop For each loaded plugin
        PE->>WM: recognize(input)

        Note over WM,Plugin: Memory Write
        WM->>WM: serialize to JSON
        WM->>CH: invoke("plugin_alloc", size)
        CH->>Plugin: plugin_alloc(size)
        Plugin-->>CH: ptr
        CH-->>WM: ptr
        WM->>CH: writeInt(ptr, length)
        WM->>CH: writeBytes(ptr+4, json)

        Note over WM,Plugin: Function Call
        WM->>CH: invoke("recognize", ptr, len)
        CH->>Plugin: recognize(ptr, len)
        Plugin->>Plugin: parse JSON input
        Plugin->>Plugin: run recognition logic
        Plugin->>Plugin: serialize output
        Plugin-->>CH: result_ptr
        CH-->>WM: result_ptr

        Note over WM,Plugin: Memory Read
        WM->>CH: readInt(result_ptr)
        CH-->>WM: length
        WM->>CH: readBytes(result_ptr+4, length)
        CH-->>WM: JSON bytes
        WM->>WM: deserialize output

        WM-->>PE: PluginOutput

        alt recognized == true
            PE-->>PS: RecognitionResult
            PS-->>UI: result
        end
    end
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

### Host ↔ Plugin Communication

```mermaid
flowchart LR
    subgraph Host["Host (Kotlin)"]
        H1[Serialize to JSON]
        H2[Call plugin_alloc]
        H3[Write length + data]
        H4[Invoke function]
        H5[Read result pointer]
        H6[Read length + data]
        H7[Deserialize JSON]
    end

    subgraph Plugin["Plugin (WASM)"]
        P1[plugin_alloc]
        P2[Linear Memory]
        P3[recognize/plugin_info]
        P4[Write result]
    end

    H1 --> H2
    H2 --> P1
    P1 --> P2
    H3 --> P2
    H4 --> P3
    P3 --> P4
    P4 --> P2
    H5 --> P2
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

    class Memory {
        <<linear memory>>
        data: bytes
    }

    WASMPlugin --> Memory : exports
```

| Export | Signature | Description |
|--------|-----------|-------------|
| `memory` | `Memory` | Linear memory for data exchange |
| `plugin_alloc` | `(i32) -> i32` | Allocate N bytes, return pointer |
| `plugin_dealloc` | `(i32, i32) -> void` | Free memory at ptr with size |
| `plugin_info` | `() -> i32` | Return pointer to JSON metadata |
| `recognize` | `(i32, i32) -> i32` | Process input, return result pointer |

### Data Types

```mermaid
classDiagram
    class PluginInfo {
        +name: String
        +version: String
        +description: String
        +supportedFormats: List~String~
    }

    class PluginInput {
        +totalParams: Long
        +layerCount: Int
        +layerTypes: Map~String,Int~
        +detectedBlocks: List~String~
        +inputShape: List~Int~
        +outputShapes: List~List~Int~~
        +weightStats: WeightStats
        +format: String
        +fileSizeBytes: Long
        +embeddedMetadata: Map~String,String~
    }

    class PluginOutput {
        +recognized: Boolean
        +family: String?
        +variant: String?
        +task: String?
        +confidence: Float
        +metadata: Map~String,String~?
    }

    class WeightStats {
        +mean: Float
        +std: Float
        +min: Float
        +max: Float
        +sparsity: Float
    }

    PluginInput --> WeightStats
```

### JSON Examples

**PluginInfo:**
```json
{
  "name": "Minimal Rust Plugin",
  "version": "0.1.0",
  "description": "A minimal example plugin written in Rust",
  "supportedFormats": ["onnx", "gguf"]
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

## Usage

### Loading Plugins

```kotlin
// Initialize on app startup
PluginService.initialize()

// Or load from custom directory
PluginService.initialize(File("/path/to/plugins"))

// Check loaded plugins
val plugins = PluginService.getLoadedPlugins()
plugins.forEach { (name, info) ->
    println("${info.name} v${info.version}")
}
```

### Running Recognition

```kotlin
// From model analysis
val result = PluginService.recognize(
    format = ModelFormat.ONNX,
    tensors = analyzedTensors,
    metadata = modelMetadata
)

if (result != null && result.recognized) {
    println("Detected: ${result.family} ${result.variant}")
    println("Task: ${result.task}")
    println("Confidence: ${result.confidence}")
}
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
- `writeInt` + `writeBytes` for writing
- `readInt` + `readBytes` for reading

### 3. Memory API Discovery

**Problem:** Chasm memory APIs not documented.

**Solution:** Found in source code:
```kotlin
import io.github.charlietap.chasm.embedding.memory.readInt
import io.github.charlietap.chasm.embedding.memory.readBytes
import io.github.charlietap.chasm.embedding.memory.writeInt
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

*Last updated: 2025-01-25 | Plugin System v1.0.0*
