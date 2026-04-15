package dev.forgeotalab.nativebridge

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Typed Kotlin wrapper for the Rust JNI bridge.
 *
 * WHY a singleton object: System.loadLibrary must be called exactly once.
 * Centralizing it here prevents raw System.loadLibrary from being scattered
 * across feature modules. All native calls go through this single entry point.
 *
 * The library name "forge_jni" matches the Cargo.toml [lib] output:
 * cargo produces libforge_jni.so, Android loads it as "forge_jni".
 */
object NativeBridge {

    private const val TAG = "NativeBridge"
    private const val LIB_NAME = "forge_jni"

    /**
     * Track whether the native library loaded successfully.
     * If loading fails, calls return safe fallback values instead of crashing.
     */
    var isLoaded: Boolean = false
        private set

    init {
        try {
            System.loadLibrary(LIB_NAME)
            isLoaded = true
            Log.i(TAG, "Native library '$LIB_NAME' loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            isLoaded = false
            Log.e(TAG, "Failed to load native library '$LIB_NAME': ${e.message}")
        }
    }

    /**
     * Ping → Pong round-trip proving the JNI bridge works.
     *
     * @return "pong" if the native library is loaded and functional,
     *         or an error description if not.
     */
    fun ping(): String {
        if (!isLoaded) {
            return "error: native library not loaded"
        }
        return nativePing()
    }

    /**
     * Returns the version of the Rust extraction core.
     */
    fun coreVersion(): String {
        if (!isLoaded) {
            return "unknown (native library not loaded)"
        }
        return nativeCoreVersion()
    }

    /**
     * Analyze an OTA package at the given file path.
     *
     * Returns a JSON string representing the analysis result envelope:
     * - Success: {"ok": <AnalysisResult>}
     * - Error:   {"error": {"code": "...", "message": "...", "details": "..."}}
     *
     * WHY JSON: Results cross JNI as serialized JSON — less efficient than
     * direct JNI types, but verifiable and debuggable (PRD requirement).
     *
     * @param filePath Absolute path to the OTA package file.
     * @return JSON-serialized JniResult<AnalysisResult>.
     */
    fun analyze(filePath: String): String {
        if (!isLoaded) {
            return """{"error":{"code":"NATIVE_NOT_LOADED","message":"Native library not loaded","details":""}}"""
        }
        return nativeAnalyze(filePath)
    }

    /**
     * Alias for [analyze] — used by integration tests that reference
     * the more descriptive name.
     */
    fun analyzePayload(filePath: String): String = analyze(filePath)

    /**
     * Trigger a deliberate Rust panic inside catch_unwind.
     *
     * This function ONLY works in debug builds where the `test-panic`
     * Cargo feature is enabled. In release builds, the JNI function
     * does not exist and this will throw UnsatisfiedLinkError.
     *
     * Used exclusively by NativeBridgePanicContainmentTest to verify
     * PRD security requirement: "catch_unwind on every exported function.
     * Return serialized error, never propagate panic."
     *
     * @return JSON error envelope if panic was caught, never returns on crash.
     * @throws UnsatisfiedLinkError if test-panic feature is not enabled.
     */
    fun triggerTestPanic(): String {
        if (!isLoaded) {
            return """{ "status": "error", "error_code": "NATIVE_NOT_LOADED", "message": "Native library not loaded" }"""
        }
        return nativeTriggerTestPanic()
    }

    /**
     * Extract a single partition from a payload file.
     *
     * Returns a JSON string representing the extraction result envelope:
     * - Success: {"ok": <ExtractionOutcome>}
     * - Error:   {"error": {"code": "...", "message": "...", "details": "..."}}
     *
     * PRD Non-Negotiables enforced in the native layer:
     * - No extraction starts before storage/permission validation (caller responsibility)
     * - No success state before SHA-256 verification completes
     * - Path traversal: output path canonicalized and validated against destination
     *
     * @param filePath Absolute path to the payload file.
     * @param partitionName Name of the partition to extract (e.g., "boot", "system").
     * @param outputPath Absolute path for the extraction output file.
     * @param callback Optional progress callback for extraction updates.
     * @return JSON-serialized JniResult<ExtractionOutcome>.
     */
    fun extractPartition(
        filePath: String,
        partitionName: String,
        outputPath: String,
        callback: ExtractionCallback? = null,
    ): String {
        if (!isLoaded) {
            return """{"error":{"code":"NATIVE_NOT_LOADED","message":"Native library not loaded","details":""}}"""
        }
        return nativeExtractPartition(filePath, partitionName, outputPath, callback)
    }

    /**
     * Parse the extraction result JSON envelope into a typed result.
     *
     * WHY here: Centralizes JSON parsing so the ExtractionWorker doesn't
     * need to handle raw JSON — it works with typed Kotlin objects.
     *
     * @param rawJson JSON string from nativeExtractPartition.
     * @return ExtractionResult.Success or ExtractionResult.Error.
     */
    fun parseExtractionResult(rawJson: String): ExtractionResult {
        return try {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val envelope = json.parseToJsonElement(rawJson).jsonObject

            if (envelope.containsKey("ok")) {
                val okElement = envelope["ok"] ?: return ExtractionResult.Error(
                    code = "PARSE_ERROR",
                    message = "Missing 'ok' value in result envelope",
                )
                val outcome = json.decodeFromJsonElement(
                    NativeExtractionOutcome.serializer(),
                    okElement,
                )
                ExtractionResult.Success(outcome)
            } else {
                val errorObj = envelope["error"]?.jsonObject
                ExtractionResult.Error(
                    code = errorObj?.get("code")?.jsonPrimitive?.content ?: "UNKNOWN",
                    message = errorObj?.get("message")?.jsonPrimitive?.content ?: "Unknown error",
                    details = errorObj?.get("details")?.jsonPrimitive?.content,
                )
            }
        } catch (e: Exception) {
            ExtractionResult.Error(
                code = "PARSE_ERROR",
                message = "Failed to parse extraction result: ${e.message}",
            )
        }
    }

    // =========================================================================
    // Filesystem browsing — PRD FR-8
    // =========================================================================

    /**
     * Open a filesystem image and return the root directory listing.
     *
     * Returns a JSON string representing the browse result:
     * - Browsable: {"ok": {"Browsable": {"filesystem_type": "...", "root_listing": {...}}}}
     * - Unsupported: {"ok": {"Unsupported": {"format": "..."}}}
     * - Error: {"error": {"code": "...", "message": "...", "details": "..."}}
     *
     * PRD FR-8: "Mount read-only through Rust core."
     */
    fun browseFilesystem(imagePath: String): String {
        if (!isLoaded) {
            return """{"error":{"code":"NATIVE_NOT_LOADED","message":"Native library not loaded","details":""}}"""
        }
        return nativeBrowseFilesystem(imagePath)
    }

    /**
     * List entries in a specific directory within a filesystem image.
     *
     * PRD: "Lazy loading — directories load on expand, not all upfront."
     */
    fun listDirectory(
        imagePath: String,
        directoryPath: String,
        offset: Long = 0,
        limit: Int = 500,
    ): String {
        if (!isLoaded) {
            return """{"error":{"code":"NATIVE_NOT_LOADED","message":"Native library not loaded","details":""}}"""
        }
        return nativeListDirectory(imagePath, directoryPath, offset, limit)
    }

    /**
     * Read a file from inside a filesystem image and write it to an output path.
     *
     * PRD Security: Output path canonicalized and validated in Rust core.
     * PRD: "Source artifact is never mutated."
     */
    fun readFile(
        imagePath: String,
        filePath: String,
        outputPath: String,
    ): String {
        if (!isLoaded) {
            return """{"error":{"code":"NATIVE_NOT_LOADED","message":"Native library not loaded","details":""}}"""
        }
        return nativeReadFile(imagePath, filePath, outputPath)
    }

    /**
     * Parse the browse filesystem result JSON into a typed result.
     */
    fun parseBrowseResult(rawJson: String): FsBrowseNativeResult {
        return try {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val envelope = json.parseToJsonElement(rawJson).jsonObject

            if (envelope.containsKey("ok")) {
                val okElement = envelope["ok"]?.jsonObject
                    ?: return FsBrowseNativeResult.Error(
                        code = "PARSE_ERROR",
                        message = "Missing 'ok' value in browse result",
                    )

                when {
                    okElement.containsKey("Browsable") -> {
                        val browsable = okElement["Browsable"]?.jsonObject
                            ?: return FsBrowseNativeResult.Error(
                                code = "PARSE_ERROR",
                                message = "Missing 'Browsable' object",
                            )
                        val fsType = browsable["filesystem_type"]?.jsonPrimitive?.content ?: "unknown"
                        val listingElement = browsable["root_listing"]
                            ?: return FsBrowseNativeResult.Error(
                                code = "PARSE_ERROR",
                                message = "Missing 'root_listing'",
                            )
                        val listing = json.decodeFromJsonElement(
                            NativeDirectoryListing.serializer(),
                            listingElement,
                        )
                        FsBrowseNativeResult.Browsable(fsType, listing)
                    }
                    okElement.containsKey("Unsupported") -> {
                        val unsupported = okElement["Unsupported"]?.jsonObject
                        val format = unsupported?.get("format")?.jsonPrimitive?.content
                            ?: "Unknown format"
                        FsBrowseNativeResult.Unsupported(format)
                    }
                    else -> FsBrowseNativeResult.Error(
                        code = "PARSE_ERROR",
                        message = "Unknown browse result variant",
                    )
                }
            } else {
                val errorObj = envelope["error"]?.jsonObject
                FsBrowseNativeResult.Error(
                    code = errorObj?.get("code")?.jsonPrimitive?.content ?: "UNKNOWN",
                    message = errorObj?.get("message")?.jsonPrimitive?.content ?: "Unknown error",
                    details = errorObj?.get("details")?.jsonPrimitive?.content,
                )
            }
        } catch (e: Exception) {
            FsBrowseNativeResult.Error(
                code = "PARSE_ERROR",
                message = "Failed to parse browse result: ${e.message}",
            )
        }
    }

    /**
     * Parse a directory listing result JSON into a typed result.
     */
    fun parseListDirectoryResult(rawJson: String): FsListDirectoryNativeResult {
        return try {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val envelope = json.parseToJsonElement(rawJson).jsonObject

            if (envelope.containsKey("ok")) {
                val okElement = envelope["ok"]
                    ?: return FsListDirectoryNativeResult.Error(
                        code = "PARSE_ERROR",
                        message = "Missing 'ok' value",
                    )
                val listing = json.decodeFromJsonElement(
                    NativeDirectoryListing.serializer(),
                    okElement,
                )
                FsListDirectoryNativeResult.Success(listing)
            } else {
                val errorObj = envelope["error"]?.jsonObject
                FsListDirectoryNativeResult.Error(
                    code = errorObj?.get("code")?.jsonPrimitive?.content ?: "UNKNOWN",
                    message = errorObj?.get("message")?.jsonPrimitive?.content ?: "Unknown error",
                    details = errorObj?.get("details")?.jsonPrimitive?.content,
                )
            }
        } catch (e: Exception) {
            FsListDirectoryNativeResult.Error(
                code = "PARSE_ERROR",
                message = "Failed to parse listing result: ${e.message}",
            )
        }
    }

    /**
     * Parse a file export result JSON into a typed result.
     */
    fun parseFileExportResult(rawJson: String): FsFileExportNativeResult {
        return try {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val envelope = json.parseToJsonElement(rawJson).jsonObject

            if (envelope.containsKey("ok")) {
                val okElement = envelope["ok"]
                    ?: return FsFileExportNativeResult.Error(
                        code = "PARSE_ERROR",
                        message = "Missing 'ok' value",
                    )
                val result = json.decodeFromJsonElement(
                    NativeFileExportResult.serializer(),
                    okElement,
                )
                FsFileExportNativeResult.Success(result)
            } else {
                val errorObj = envelope["error"]?.jsonObject
                FsFileExportNativeResult.Error(
                    code = errorObj?.get("code")?.jsonPrimitive?.content ?: "UNKNOWN",
                    message = errorObj?.get("message")?.jsonPrimitive?.content ?: "Unknown error",
                    details = errorObj?.get("details")?.jsonPrimitive?.content,
                )
            }
        } catch (e: Exception) {
            FsFileExportNativeResult.Error(
                code = "PARSE_ERROR",
                message = "Failed to parse export result: ${e.message}",
            )
        }
    }

    // -- JNI declarations --
    // These map 1:1 to #[no_mangle] exports in forge-jni/src/lib.rs
    // Method names use the JNI naming convention:
    //   Java_{package}_{class}_{method}
    //   where package separators (.) become _ in the Rust function name

    @JvmStatic
    private external fun nativePing(): String

    @JvmStatic
    private external fun nativeCoreVersion(): String

    @JvmStatic
    private external fun nativeAnalyze(filePath: String): String

    @JvmStatic
    private external fun nativeExtractPartition(
        filePath: String,
        partitionName: String,
        outputPath: String,
        callback: Any?,
    ): String

    // Filesystem browsing — PRD FR-8
    @JvmStatic
    private external fun nativeBrowseFilesystem(imagePath: String): String

    @JvmStatic
    private external fun nativeListDirectory(
        imagePath: String,
        directoryPath: String,
        offset: Long,
        limit: Int,
    ): String

    @JvmStatic
    private external fun nativeReadFile(
        imagePath: String,
        filePath: String,
        outputPath: String,
    ): String

    // Test-only JNI — compiled only with `test-panic` Cargo feature (debug builds).
    // Deliberately panics inside catch_unwind to verify JNI panic isolation.
    @JvmStatic
    private external fun nativeTriggerTestPanic(): String
}
