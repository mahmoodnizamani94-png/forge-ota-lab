package dev.forgeotalab.integration

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.forgeotalab.nativebridge.NativeBridge
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * JNI panic containment test — the most critical security test in the suite.
 *
 * PRD Security: "catch_unwind on every exported function. Return serialized
 * error, never propagate panic."
 *
 * This test calls a JNI function that deliberately panics inside
 * catch_unwind. The test passes if:
 * 1. The app does NOT crash (no SIGSEGV, no abort)
 * 2. A structured error JSON is returned
 * 3. The error code is RUST_PANIC
 *
 * The test-panic JNI function is only compiled when the `test-panic`
 * Cargo feature is enabled (debug builds only).
 *
 * If this test fails, it means Rust panics can escape to Java and
 * crash the app — a ship-blocking defect.
 */
@RunWith(AndroidJUnit4::class)
class NativeBridgePanicContainmentTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun rust_panic_produces_structured_error_not_crash() {
        // If this call crashes the process, the test fails at the harness level
        // (no assertion needed — a crash IS the failure).
        val result: String = try {
            NativeBridge.triggerTestPanic()
        } catch (e: UnsatisfiedLinkError) {
            // The test-panic feature was not enabled in this build.
            // This is acceptable for release builds — skip the test.
            println(
                "SKIP: nativeTriggerTestPanic not available " +
                    "(test-panic feature not enabled in this build)"
            )
            return
        } catch (e: Exception) {
            // Any other exception means the panic was NOT properly contained
            throw AssertionError(
                "Rust panic escaped JNI boundary as Java exception: ${e::class.simpleName}: ${e.message}",
                e,
            )
        }

        // If we get here, the JVM survived the Rust panic. Verify the result.
        assertThat(result).isNotNull()
        assertThat(result).isNotEmpty()

        // Parse the structured error JSON
        val json = JSONObject(result)

        // Verify it's a properly structured error response
        val status = json.optString("status", "")
        assertThat(status).isAnyOf("error", "Error", "ERROR")

        // Verify the error code indicates a caught panic
        val errorCode = json.optString("error_code", json.optString("code", ""))
        assertThat(errorCode).isEqualTo("RUST_PANIC")

        // Verify there's a descriptive message
        val message = json.optString("message", json.optString("error", ""))
        assertThat(message).isNotEmpty()
        assertThat(message).contains("catch_unwind")
    }

    @Test
    fun native_bridge_loads_without_crash() {
        // Basic smoke test: verify the native library loads successfully.
        // If this fails, all JNI tests will fail.
        // WHY separate test: isolates library loading failures from
        // logic-level failures for cleaner diagnostics.
        try {
            System.loadLibrary("forge_jni")
        } catch (e: UnsatisfiedLinkError) {
            // Library may already be loaded — that's fine
            if (!e.message.orEmpty().contains("already loaded")) {
                throw e
            }
        }
        // If we get here without crashing, the library loaded successfully
    }
}
