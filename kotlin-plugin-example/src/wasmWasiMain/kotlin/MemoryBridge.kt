@file:OptIn(kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class)

import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.withScopedMemoryAllocator

object MemoryBridge {
    // Initialized lazily on first alloc call (after _initialize has run)
    private var heapTop: Int = -1

    private fun ensureInit() {
        if (heapTop >= 0) return
        // Use Kotlin's scoped allocator to discover where free memory starts
        withScopedMemoryAllocator { allocator ->
            val probe = allocator.allocate(16)
            heapTop = probe.address.toInt()
        }
        // Align to 16 bytes and add padding past the scoped allocator region
        heapTop = (heapTop + 1024 + 15) and 15.inv()
    }

    fun alloc(size: Int): Int {
        ensureInit()
        val ptr = heapTop
        heapTop += size
        // Align to 8 bytes
        heapTop = (heapTop + 7) and 7.inv()
        return ptr
    }

    fun dealloc(ptr: Int, size: Int) {
        // No-op for POC bump allocator
    }

    fun writeLengthPrefixed(str: String): Int {
        val bytes = str.encodeToByteArray()
        val totalSize = 4 + bytes.size
        val ptr = alloc(totalSize)
        val pointer = Pointer(ptr.toUInt())

        // Write length as little-endian i32 (Pointer.storeInt is built-in)
        pointer.storeInt(bytes.size)

        // Write string bytes
        for (i in bytes.indices) {
            (pointer + 4 + i).storeByte(bytes[i])
        }

        return ptr
    }

    fun readBytes(ptr: Int, len: Int): ByteArray {
        val pointer = Pointer(ptr.toUInt())
        val result = ByteArray(len)
        for (i in 0 until len) {
            result[i] = (pointer + i).loadByte()
        }
        return result
    }
}
