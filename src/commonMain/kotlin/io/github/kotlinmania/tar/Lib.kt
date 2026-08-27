// port-lint: source lib.rs
package io.github.kotlinmania.tar

/**
 * A library for reading and writing TAR archives.
 *
 * This library provides utilities necessary to manage TAR archives
 * abstracted over a reader or writer.
 */

/**
 * Builds an [IoError] tagged with [IoErrorKind.Other] from a short descriptive message.
 */
internal fun other(msg: String): IoError = IoError.new(IoErrorKind.Other, msg)
