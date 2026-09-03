// port-lint: tests pax.rs
package io.github.kotlinmania.tar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PaxTest {
    @Test
    fun roundtripPaxExtensions() {
        val extensions = listOf(
            PAX_PATH to "hello/world".encodeToByteArray(),
            PAX_SIZE to "123456789".encodeToByteArray(),
            PAX_UNAME to "ferris".encodeToByteArray(),
        )

        val formatted = formatPaxExtensions(extensions)
        val parsed = PaxExtensions.new(formatted).toList()

        assertEquals(3, parsed.size)
        assertEquals(PAX_PATH, parsed[0].key())
        assertEquals("hello/world", parsed[0].value())

        assertEquals(PAX_SIZE, parsed[1].key())
        assertEquals("123456789", parsed[1].value())

        assertEquals(PAX_UNAME, parsed[2].key())
        assertEquals("ferris", parsed[2].value())
    }

    @Test
    fun paxExtensionsValueParsing() {
        val extensions = listOf(
            PAX_SIZE to "9876543210".encodeToByteArray(),
            PAX_UID to "1000".encodeToByteArray(),
        )
        val data = formatPaxExtensions(extensions)

        assertEquals(9876543210uL, paxExtensionsValue(data, PAX_SIZE))
        assertEquals(1000uL, paxExtensionsValue(data, PAX_UID))
        assertNull(paxExtensionsValue(data, PAX_GID))
    }

    @Test
    fun malformedPaxExtensionFails() {
        // Missing space
        val badData1 = "10path=foo\n".encodeToByteArray()
        assertFailsWith<IoError> {
            PaxExtensions.new(badData1).toList()
        }

        // Invalid length
        val badData2 = "abc path=foo\n".encodeToByteArray()
        assertFailsWith<IoError> {
            PaxExtensions.new(badData2).toList()
        }

        // Length mismatch
        val badData3 = "20 path=foo\n".encodeToByteArray()
        assertFailsWith<IoError> {
            PaxExtensions.new(badData3).toList()
        }

        // Missing equals
        val badData4 = "12 pathfoo\n".encodeToByteArray()
        assertFailsWith<IoError> {
            PaxExtensions.new(badData4).toList()
        }
    }

    @Test
    fun gnuSparsePaxKeywords() {
        val extensions = listOf(
            PAX_GNUSPARSENUMBLOCKS to "2".encodeToByteArray(),
            PAX_GNUSPARSEREALSIZE to "4096".encodeToByteArray(),
        )
        val data = formatPaxExtensions(extensions)

        assertEquals(2uL, paxExtensionsValue(data, PAX_GNUSPARSENUMBLOCKS))
        assertEquals(4096uL, paxExtensionsValue(data, PAX_GNUSPARSEREALSIZE))
    }
}
