package io.github.kotlinmania.tar

/**
 * Platform-neutral representation of I/O error and error kinds.
 */

/**
 * Categories of I/O failure mirrored from `std::io::ErrorKind`. Additional
 * variants are added as the port surfaces them; the upstream type is marked
 * `non_exhaustive` so this enum should not be matched exhaustively.
 */
enum class IoErrorKind {
    NotFound,
    PermissionDenied,
    InvalidData,
    InvalidInput,
    UnexpectedEof,
    Unsupported,
    Other,
}

/**
 * A platform-neutral I/O error carrying an [IoErrorKind] tag. Stands in for
 * `std::io::Error` so callers can branch on [kind] the way upstream code does.
 */
open class IoError(
    val kind: IoErrorKind,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    companion object {
        /** Mirrors `std::io::Error::new(kind, payload)` for string payloads. */
        fun new(kind: IoErrorKind, message: String): IoError = IoError(kind, message)

        /** Mirrors `std::io::Error::new(kind, payload)` for [Throwable] payloads. */
        fun new(kind: IoErrorKind, cause: Throwable): IoError =
            IoError(kind, cause.message ?: cause::class.simpleName ?: "io error", cause)
    }
}
