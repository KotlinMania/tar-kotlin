// port-lint: tests tests/header/mod.rs
package io.github.kotlinmania.tar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeaderTest {
    @Test
    fun defaultGnu() {
        val h = Header.newGnu()
        assertNotNull(h.asGnu())
        assertNotNull(h.asGnuMut())
        assertNull(h.asUstar())
        assertNull(h.asUstarMut())
    }

    @Test
    fun gotoOld() {
        val h = Header.newOld()
        assertNull(h.asGnu())
        assertNull(h.asGnuMut())
        assertNull(h.asUstar())
        assertNull(h.asUstarMut())
    }

    @Test
    fun gotoUstar() {
        val h = Header.newUstar()
        assertNull(h.asGnu())
        assertNull(h.asGnuMut())
        assertNotNull(h.asUstar())
        assertNotNull(h.asUstarMut())
    }

    @Test
    fun linkName() {
        val h = Header.newGnu()
        h.setLinkName("foo")
        assertEquals("foo", h.linkName())
        h.setLinkName("../foo")
        assertEquals("../foo", h.linkName())
        h.setLinkName("foo/bar")
        assertEquals("foo/bar", h.linkName())
        h.setLinkName("foo\\ba")
        assertEquals("foo/ba", h.linkName())

        val name = "foo\\bar\u0000".encodeToByteArray()
        name.copyInto(h.asOldMut().linkname.array, h.asOldMut().linkname.offset)
        assertEquals("foo\\bar", h.linkName())

        assertFailsWith<IoError> {
            h.setLinkName("\u0000")
        }
    }

    @Test
    fun mtime() {
        val hGnu = Header.newGnu()
        assertEquals(0L, hGnu.mtime())

        val hUstar = Header.newUstar()
        assertEquals(0L, hUstar.mtime())

        val hOld = Header.newOld()
        assertEquals(0L, hOld.mtime())
    }

    @Test
    fun userAndGroupName() {
        var h = Header.newGnu()
        h.setUsername("foo")
        h.setGroupname("bar")
        assertEquals("foo", h.username())
        assertEquals("bar", h.groupname())

        h = Header.newUstar()
        h.setUsername("foo")
        h.setGroupname("bar")
        assertEquals("foo", h.username())
        assertEquals("bar", h.groupname())

        h = Header.newOld()
        assertEquals(null, h.username())
        assertEquals(null, h.groupname())
        assertFailsWith<IoError> {
            h.setUsername("foo")
        }
        assertFailsWith<IoError> {
            h.setGroupname("foo")
        }
    }

    @Test
    fun devMajorMinor() {
        var h = Header.newGnu()
        h.setDeviceMajor(1)
        h.setDeviceMinor(2)
        assertEquals(1, h.deviceMajor())
        assertEquals(2, h.deviceMinor())

        h = Header.newUstar()
        h.setDeviceMajor(1)
        h.setDeviceMinor(2)
        assertEquals(1, h.deviceMajor())
        assertEquals(2, h.deviceMinor())

        h.asUstarMut()!!.devMinor[0] = 0x7f.toByte()
        h.asUstarMut()!!.devMajor[0] = 0x7f.toByte()
        assertFailsWith<IoError> {
            h.deviceMajor()
        }
        assertFailsWith<IoError> {
            h.deviceMinor()
        }

        h.asUstarMut()!!.devMinor[0] = 'g'.code.toByte()
        h.asUstarMut()!!.devMajor[0] = 'h'.code.toByte()
        assertFailsWith<IoError> {
            h.deviceMajor()
        }
        assertFailsWith<IoError> {
            h.deviceMinor()
        }

        h = Header.newOld()
        assertEquals(null, h.deviceMajor())
        assertEquals(null, h.deviceMinor())
        assertFailsWith<IoError> {
            h.setDeviceMajor(1)
        }
        assertFailsWith<IoError> {
            h.setDeviceMinor(1)
        }
    }

    @Test
    fun setPath() {
        val h = Header.newGnu()
        h.setPath("foo")
        assertEquals("foo", h.path())
        h.setPath("foo/")
        assertEquals("foo/", h.path())
        h.setPath("foo/bar")
        assertEquals("foo/bar", h.path())
        h.setPath("foo\\bar")
        assertEquals("foo/bar", h.path())

        // setPath removes "." signifying current directory
        h.setPath("./control")
        assertEquals("control", h.path())

        val longName = "foo".repeat(100)
        val medium1 = "foo".repeat(52)
        val medium2 = "fo/".repeat(52)

        assertFailsWith<IoError> { h.setPath(longName) }
        assertFailsWith<IoError> { h.setPath(medium1) }
        assertFailsWith<IoError> { h.setPath(medium2) }
        assertFailsWith<IoError> { h.setPath("\u0000") }

        assertFailsWith<IoError> { h.setPath("..") }
        assertFailsWith<IoError> { h.setPath("foo/..") }
        assertFailsWith<IoError> { h.setPath("foo/../bar") }

        val hUstar = Header.newUstar()
        hUstar.setPath("foo")
        assertEquals("foo", hUstar.path())

        assertFailsWith<IoError> { hUstar.setPath(longName) }
        assertFailsWith<IoError> { hUstar.setPath(medium1) }
        hUstar.setPath(medium2)
        assertEquals(medium2, hUstar.path())
    }

    @Test
    fun setUstarPathHard() {
        val h = Header.newUstar()
        val p = "a/" + "a".repeat(100)
        h.setPath(p)
        assertEquals(p, h.path())
    }

    // Note: set_metadata_deterministic from upstream is omitted in commonTest due to std::fs filesystem metadata dependency across KMP targets.

    @Test
    fun extendedNumericFormat() {
        val h = GnuHeader(Header())
        h.asHeaderMut().setSize(42)
        assertEquals(ByteSlice.wrap(byteArrayOf(48, 48, 48, 48, 48, 48, 48, 48, 48, 53, 50, 0)), h.size)
        h.asHeaderMut().setSize(8589934593L)
        assertEquals(
            ByteSlice.wrap(byteArrayOf(0x80.toByte(), 0, 0, 0, 0, 0, 0, 0x02, 0, 0, 0, 1)),
            h.size,
        )
        h.asHeaderMut().setSize(44)
        assertEquals(ByteSlice.wrap(byteArrayOf(48, 48, 48, 48, 48, 48, 48, 48, 48, 53, 52, 0)), h.size)
        h.size.copyFrom(byteArrayOf(0x80.toByte(), 0, 0, 0, 0, 0, 0, 0x02, 0, 0, 0, 0))
        assertEquals(0x0200000000L, h.asHeader().entrySize())
        h.size.copyFrom(byteArrayOf(48, 48, 48, 48, 48, 48, 48, 48, 48, 53, 51, 0))
        assertEquals(43L, h.asHeader().entrySize())

        h.asHeaderMut().setGid(42)
        assertEquals(ByteSlice.wrap(byteArrayOf(48, 48, 48, 48, 48, 53, 50, 0)), h.gid)
        assertEquals(42L, h.asHeader().gid())
        h.asHeaderMut().setGid(0x7fffffffffffffffL)
        assertEquals(ByteSlice.wrap(ByteArray(8) { 0xff.toByte() }), h.gid)
        assertEquals(0x7fffffffffffffffL, h.asHeader().gid())
        h.uid.copyFrom(byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00, 0x12, 0x34, 0x56, 0x78))
        assertEquals(0x12345678L, h.asHeader().uid())

        h.mtime.copyFrom(
            byteArrayOf(
                0x80.toByte(),
                0,
                0,
                0,
                0x01,
                0x23,
                0x45,
                0x67,
                0x89.toByte(),
                0xab.toByte(),
                0xcd.toByte(),
                0xef.toByte(),
            ),
        )
        assertEquals(0x0123456789abcdefL, h.asHeader().mtime())

        h.realsizeBytes.copyFrom(
            byteArrayOf(
                0x80.toByte(),
                0,
                0,
                0,
                0,
                0x12,
                0x34,
                0x56,
                0x78,
                0x9a.toByte(),
                0xbc.toByte(),
                0xde.toByte(),
            ),
        )
        assertEquals(0x00123456789abcdeL, h.realSize())

        h.sparse[0].offsetBytes.copyFrom(
            byteArrayOf(
                0x80.toByte(),
                0,
                0,
                0,
                0,
                0x01,
                0x23,
                0x45,
                0x67,
                0x89.toByte(),
                0xab.toByte(),
                0xcd.toByte(),
            ),
        )
        assertEquals(0x000123456789abcdL, h.sparse[0].offset())

        h.sparse[0].numbytes.copyFrom(
            byteArrayOf(
                0x80.toByte(),
                0,
                0,
                0,
                0,
                0x12,
                0x34,
                0x56,
                0x78,
                0x9a.toByte(),
                0xbc.toByte(),
                0xde.toByte(),
            ),
        )
        assertEquals(0x00123456789abcdeL, h.sparse[0].length())
    }

    @Test
    fun byteSliceConversion() {
        val h = Header.newGnu()
        val b = h.asBytes()
        val bConv = Header.fromByteSlice(h.asBytes()).asBytes()
        assertTrue(b.contentEquals(bConv))
    }
}
