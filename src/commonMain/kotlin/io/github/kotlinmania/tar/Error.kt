// port-lint: source src/error.rs
package io.github.kotlinmania.tar

/**
 * The internal error type used by the tar crate to attach a descriptive
 * message to an underlying [IoError]. The upstream type wraps a
 * `Cow<'static, str>` description alongside a `std::io::Error` source;
 * Kotlin has no borrow-vs-owned distinction so [desc] is plain [String].
 */
class TarError(
    val desc: String,
    val io: IoError,
) : RuntimeException(desc, io) {
    /**
     * Mirrors `impl error::Error::description` on the upstream type. Kotlin's
     * standard library exposes the message through [message], but the
     * upstream-faithful accessor is kept for callers that read [desc]
     * directly.
     */
    fun description(): String = desc

    /**
     * Mirrors `impl error::Error::source`. Returns the wrapped [IoError].
     */
    fun source(): Throwable? = io

    /**
     * Mirrors `impl fmt::Display::fmt`. The upstream `Display` impl forwards
     * to the description, so [toString] does the same.
     */
    override fun toString(): String = desc

    companion object {
        /**
         * Mirrors `TarError::new`. Builds a [TarError] carrying [desc] as the
         * descriptive message and [err] as the wrapped I/O cause.
         */
        fun new(desc: String, err: IoError): TarError = TarError(desc, err)
    }
}

/**
 * Mirrors `impl From<TarError> for io::Error`. Returns a new [IoError] whose
 * kind matches the wrapped [IoError]'s kind and whose cause is the
 * [TarError] itself.
 */
fun TarError.toIoError(): IoError = IoError(io.kind, desc, this)
