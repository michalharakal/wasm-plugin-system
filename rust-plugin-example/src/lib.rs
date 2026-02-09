use std::alloc::{alloc, dealloc, Layout};
use std::slice;

// ─── Memory management ───

#[no_mangle]
pub extern "C" fn plugin_alloc(size: i32) -> i32 {
    let layout = Layout::from_size_align(size as usize, 1).unwrap();
    unsafe { alloc(layout) as i32 }
}

#[no_mangle]
pub extern "C" fn plugin_dealloc(ptr: i32, size: i32) {
    let layout = Layout::from_size_align(size as usize, 1).unwrap();
    unsafe { dealloc(ptr as *mut u8, layout) }
}

// ─── Length-prefixed helpers ───

/// Write a string into linear memory as [4-byte LE length][UTF-8 bytes]
/// and return the pointer to the start.
fn write_length_prefixed(s: &str) -> i32 {
    let bytes = s.as_bytes();
    let total = 4 + bytes.len();
    let ptr = plugin_alloc(total as i32);
    unsafe {
        let base = ptr as *mut u8;
        // write length as little-endian i32
        let len_bytes = (bytes.len() as u32).to_le_bytes();
        std::ptr::copy_nonoverlapping(len_bytes.as_ptr(), base, 4);
        // write payload
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), base.add(4), bytes.len());
    }
    ptr
}

/// Read a length-prefixed string from (ptr, len) where ptr points to raw JSON bytes.
fn read_input(ptr: i32, len: i32) -> String {
    unsafe {
        let slice = slice::from_raw_parts(ptr as *const u8, len as usize);
        String::from_utf8_lossy(slice).into_owned()
    }
}

// ─── Plugin exports ───

#[no_mangle]
pub extern "C" fn plugin_info() -> i32 {
    let json = r#"{"id":"minimal-rust-plugin","name":"Minimal Rust Plugin","version":"0.1.0","description":"A minimal example plugin written in Rust","supportedFormats":["onnx","gguf"],"metadata":{}}"#;
    write_length_prefixed(json)
}

#[no_mangle]
pub extern "C" fn on_load() {}

#[no_mangle]
pub extern "C" fn on_unload() {}

#[no_mangle]
pub extern "C" fn recognize(ptr: i32, len: i32) -> i32 {
    let input = read_input(ptr, len);

    // Simple heuristic: check if input contains YOLO-like patterns
    let is_yolo = input.contains("\"C2f\"")
        || input.contains("\"SPPF\"")
        || input.contains("\"Detect\"");

    let json = if is_yolo {
        r#"{"recognized":true,"family":"YOLO","variant":"v8n","task":"detect","confidence":0.95,"metadata":{"detected_by":"minimal-rust-plugin"}}"#
    } else {
        r#"{"recognized":false,"family":null,"variant":null,"task":null,"confidence":0.0,"metadata":{"detected_by":"minimal-rust-plugin"}}"#
    };

    write_length_prefixed(json)
}
