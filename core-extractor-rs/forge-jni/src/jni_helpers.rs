//! JNI helper functions for common patterns.
//!
//! Centralizes string conversion, JSON result envelope construction,
//! and error serialization so the JNI exports stay focused on
//! orchestration logic.

use jni::JNIEnv;
use jni::objects::JString;
use serde::Serialize;

// ---------------------------------------------------------------------------
// JNI result envelope
// ---------------------------------------------------------------------------

/// Serialize a success result as the JNI JSON envelope: `{"ok": <value>}`.
pub fn jni_result_ok<T: Serialize>(value: &T) -> String {
    // WHY manual construction: we want `{"ok": <value>}` not `{"Ok": <value>}`.
    // Using a struct with serde would produce `Ok` or require rename attributes.
    match serde_json::to_string(value) {
        Ok(json) => format!(r#"{{"ok":{json}}}"#),
        Err(e) => jni_result_error(
            "SERIALIZATION_ERROR",
            &format!("Failed to serialize result: {e}"),
            "",
        ),
    }
}

/// Serialize an error result as the JNI JSON envelope:
/// `{"error": {"code": "...", "message": "...", "details": "..."}}`.
pub fn jni_result_error(code: &str, message: &str, details: &str) -> String {
    // WHY manual JSON: avoids allocating a struct just for error serialization.
    // Code, message, and details are escaped for JSON safety.
    let code_escaped = escape_json_string(code);
    let msg_escaped = escape_json_string(message);
    let details_escaped = escape_json_string(details);

    format!(
        r#"{{"error":{{"code":"{code_escaped}","message":"{msg_escaped}","details":"{details_escaped}"}}}}"#
    )
}

// ---------------------------------------------------------------------------
// String conversion
// ---------------------------------------------------------------------------

/// Extract a Rust String from a JNI JString.
///
/// Returns a descriptive error message if the conversion fails,
/// rather than panicking.
pub fn string_from_jstring(env: &mut JNIEnv<'_>, jstr: &JString<'_>) -> Result<String, String> {
    env.get_string(jstr)
        .map(|s| s.into())
        .map_err(|e| format!("Failed to convert JNI string: {e}"))
}

// ---------------------------------------------------------------------------
// JSON string escaping
// ---------------------------------------------------------------------------

/// Escape a string for safe embedding in a JSON string value.
///
/// Handles: backslash, double-quote, newline, carriage return, tab.
fn escape_json_string(s: &str) -> String {
    let mut escaped = String::with_capacity(s.len());
    for ch in s.chars() {
        match ch {
            '\\' => escaped.push_str("\\\\"),
            '"' => escaped.push_str("\\\""),
            '\n' => escaped.push_str("\\n"),
            '\r' => escaped.push_str("\\r"),
            '\t' => escaped.push_str("\\t"),
            c => escaped.push(c),
        }
    }
    escaped
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_jni_result_ok_wraps_in_ok_envelope() {
        let value = serde_json::json!({"name": "test"});
        let result = jni_result_ok(&value);
        assert!(result.starts_with(r#"{"ok":"#));
        assert!(result.contains("test"));
    }

    #[test]
    fn test_jni_result_error_wraps_in_error_envelope() {
        let result = jni_result_error("TEST_CODE", "test message", "test details");
        assert!(result.contains("TEST_CODE"));
        assert!(result.contains("test message"));
        assert!(result.contains("test details"));
    }

    #[test]
    fn test_escape_json_string_handles_special_chars() {
        assert_eq!(escape_json_string(r#"he said "hi""#), r#"he said \"hi\""#);
        assert_eq!(escape_json_string("line\nnext"), "line\\nnext");
        assert_eq!(escape_json_string("path\\to\\file"), "path\\\\to\\\\file");
    }

    #[test]
    fn test_escape_json_string_passes_through_normal_text() {
        assert_eq!(escape_json_string("hello world"), "hello world");
    }

    #[test]
    fn test_jni_result_error_escapes_message_content() {
        let result = jni_result_error("ERR", "path \"C:\\bad\"", "");
        // Should not break JSON parsing
        assert!(result.contains("\\\"C:\\\\bad\\\""));
    }
}
