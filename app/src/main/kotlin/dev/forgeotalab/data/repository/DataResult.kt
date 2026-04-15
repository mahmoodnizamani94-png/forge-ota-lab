package dev.forgeotalab.data.repository

/**
 * Sealed result type for data layer operations.
 *
 * WHY not kotlin.Result: kotlin.Result cannot be used as a return type in
 * Kotlin functions. A custom sealed class provides typed error information
 * and enables exhaustive `when` handling — new result types cause compile
 * errors at every consumption site.
 *
 * WHY not shared-contracts JniResult: JniResult is for the JNI bridge boundary.
 * DataResult is for the persistence boundary. Different layers, different error
 * semantics. DataResult carries Throwable cause for debugging; JniResult carries
 * string-based context for cross-process serialization.
 */
sealed class DataResult<out T> {

    data class Success<T>(val data: T) : DataResult<T>()

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : DataResult<Nothing>()
}

/**
 * Maps the data in a successful result, preserving errors unchanged.
 */
inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(data))
    is DataResult.Error -> this
}

/**
 * Flat-maps a successful result into another DataResult operation.
 */
inline fun <T, R> DataResult<T>.flatMap(transform: (T) -> DataResult<R>): DataResult<R> = when (this) {
    is DataResult.Success -> transform(data)
    is DataResult.Error -> this
}

/**
 * Returns the success data or null if this is an error.
 */
fun <T> DataResult<T>.getOrNull(): T? = when (this) {
    is DataResult.Success -> data
    is DataResult.Error -> null
}

/**
 * Returns the success data or throws the error's cause (or a RuntimeException).
 */
fun <T> DataResult<T>.getOrThrow(): T = when (this) {
    is DataResult.Success -> data
    is DataResult.Error -> throw cause ?: RuntimeException(message)
}
