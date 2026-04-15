package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy for results crossing the JNI boundary.
 *
 * WHY: Results cross JNI as serialized JSON (not raw JNI types) for debuggability
 * and safety. The Rust side serializes into this shape; the Kotlin side deserializes.
 * Using a sealed class ensures exhaustive handling — new result variants cause
 * compile errors at every `when` site.
 */
@Serializable
sealed class JniResult<out T> {

    @Serializable
    data class Success<T>(val data: T) : JniResult<T>()

    @Serializable
    data class Error(
        val code: String,
        val message: String,
        val context: Map<String, String> = emptyMap(),
    ) : JniResult<Nothing>()
}
