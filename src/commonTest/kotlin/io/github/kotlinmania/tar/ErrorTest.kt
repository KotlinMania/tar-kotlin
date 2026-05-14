// port-lint: source src/error.rs
package io.github.kotlinmania.tar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ErrorTest {
    @Test
    fun tarErrorCarriesDescriptionAndSource() {
        val cause = IoError.new(IoErrorKind.Other, "underlying")
        val err = TarError.new("wrapped", cause)

        assertEquals("wrapped", err.description())
        assertEquals("wrapped", err.desc)
        assertSame(cause, err.source())
        assertEquals(cause, err.io)
    }

    @Test
    fun tarErrorDisplayMatchesDescription() {
        val err = TarError.new("printable", IoError.new(IoErrorKind.Other, "io"))
        assertEquals("printable", err.toString())
    }

    @Test
    fun toIoErrorPreservesKindAndChainsCause() {
        val cause = IoError.new(IoErrorKind.InvalidData, "bad bytes")
        val tar = TarError.new("could not read header", cause)

        val converted = tar.toIoError()

        assertEquals(IoErrorKind.InvalidData, converted.kind)
        assertEquals("could not read header", converted.message)
        assertSame(tar, converted.cause)
    }

    @Test
    fun otherHelperBuildsOtherKindError() {
        val err = other("oh no")
        assertEquals(IoErrorKind.Other, err.kind)
        assertEquals("oh no", err.message)
    }

    @Test
    fun ioErrorFactoryFromThrowableUsesPayloadMessage() {
        val payload = RuntimeException("payload-msg")
        val err = IoError.new(IoErrorKind.UnexpectedEof, payload)

        assertEquals(IoErrorKind.UnexpectedEof, err.kind)
        assertEquals("payload-msg", err.message)
        assertTrue(err.cause === payload)
    }
}
