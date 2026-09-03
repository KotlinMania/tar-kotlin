// port-lint: tests entry.rs
package io.github.kotlinmania.tar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntryTest {
    @Test
    fun testEntryBasicMetadata() {
        val header = Header.newGnu()
        header.setSize(100L)
        header.setPath("hello/world.txt")
        header.setCksum()

        val data = "Hello, world!".encodeToByteArray()
        val entryIo = EntryIo.Data(Cursor(data).take(data.size.toLong()))
        val fields =
            EntryFields(
                header = header,
                size = data.size.toULong(),
                headerPos = 0u,
                filePos = 512u,
                data = mutableListOf(entryIo),
            )
        val entry = Entry<Read>(fields)

        assertEquals("hello/world.txt", entry.path())
        assertEquals("hello/world.txt", entry.pathBytes().decodeToString())
        assertEquals(data.size.toULong(), entry.size())
        assertEquals(0u.toULong(), entry.rawHeaderPosition())
        assertEquals(512u.toULong(), entry.rawFilePosition())
        assertNull(entry.linkName())
        assertNull(entry.linkNameBytes())
        assertEquals(header, entry.header())

        val readBuf = ByteArray(32)
        val n = entry.read(readBuf, 0, readBuf.size)
        assertEquals(data.size, n)
        assertEquals("Hello, world!", readBuf.copyOf(n).decodeToString())
    }

    @Test
    fun testEntryGnuLongName() {
        val header = Header.newGnu()
        header.setPath("short.txt")
        header.setCksum()

        val longPathBytes = "this/is/a/very/long/path/name/that/exceeds/normal/tar/limits.txt\u0000".encodeToByteArray()
        val fields =
            EntryFields(
                header = header,
                longPathname = longPathBytes,
                size = 0u,
            )
        val entry = Entry<Read>(fields)

        assertEquals("this/is/a/very/long/path/name/that/exceeds/normal/tar/limits.txt", entry.path())
        assertEquals(
            "this/is/a/very/long/path/name/that/exceeds/normal/tar/limits.txt",
            entry.pathBytes().decodeToString(),
        )
    }

    @Test
    fun testEntryGnuLongLink() {
        val header = Header.newGnu()
        header.setPath("symlink.txt")
        header.setLinkName("short_target")
        header.setCksum()

        val longLinkBytes = "target/of/a/very/long/symlink/path.txt\u0000".encodeToByteArray()
        val fields =
            EntryFields(
                header = header,
                longLinkname = longLinkBytes,
                size = 0u,
            )
        val entry = Entry<Read>(fields)

        assertEquals("target/of/a/very/long/symlink/path.txt", entry.linkName())
        assertEquals(
            "target/of/a/very/long/symlink/path.txt",
            entry.linkNameBytes()?.decodeToString(),
        )
    }

    @Test
    fun testEntryPaxExtensions() {
        val header = Header.newUstar()
        header.setPath("orig.txt")
        header.setCksum()

        val paxData =
            formatPaxExtensions(
                listOf(
                    PAX_PATH to "pax/override.txt".encodeToByteArray(),
                    PAX_LINKPATH to "pax/target.txt".encodeToByteArray(),
                ),
            )
        val fields =
            EntryFields(
                header = header,
                paxExtensionsData = paxData,
                size = 0u,
            )
        val entry = Entry<Read>(fields)

        assertEquals("pax/override.txt", entry.path())
        assertEquals("pax/override.txt", entry.pathBytes().decodeToString())
        assertEquals("pax/target.txt", entry.linkName())
        assertEquals("pax/target.txt", entry.linkNameBytes()?.decodeToString())

        val extensions = entry.paxExtensions()
        assertNotNull(extensions)
        val list = extensions.toList()
        assertEquals(2, list.size)
        assertEquals(PAX_PATH, list[0].key())
        assertEquals("pax/override.txt", list[0].value())
        assertEquals(PAX_LINKPATH, list[1].key())
        assertEquals("pax/target.txt", list[1].value())
    }

    @Test
    fun testEntryReadAll() {
        val data = "Chunk of data to read completely".encodeToByteArray()
        val entryIo = EntryIo.Data(Cursor(data).take(data.size.toLong()))
        val fields =
            EntryFields(
                size = data.size.toULong(),
                data = mutableListOf(entryIo),
            )

        val result = fields.readAll()
        assertEquals(data.size, result.size)
        assertTrue(data.contentEquals(result))
    }

    @Test
    fun testEntryConfigurationSetters() {
        val fields = EntryFields()
        val entry = Entry<Read>(fields)

        entry.setMask(18u)
        assertEquals(18u, fields.mask)

        entry.setUnpackXattrs(true)
        assertTrue(fields.unpackXattrs)

        entry.setPreservePermissions(true)
        assertTrue(fields.preservePermissions)

        entry.setPreserveMtime(false)
        assertFalse(fields.preserveMtime)
    }

    @Test
    fun testEntryUnpackInPathTraversalValidation() {
        val fields =
            EntryFields(
                longPathname = "foo/../../bar".encodeToByteArray(),
            )
        val entry = Entry<Read>(fields)

        // Path containing '..' components is skipped to prevent directory traversal
        assertFalse(entry.unpackIn("target_dir"))
    }

    @Test
    fun testEntryUnpackInEmptyPath() {
        val fields =
            EntryFields(
                longPathname = "///././.".encodeToByteArray(),
            )
        val entry = Entry<Read>(fields)

        // Path with only root/slashes/dots is effectively empty and accepted as no-op
        assertTrue(entry.unpackIn("target_dir"))
    }

    @Test
    fun testEntryFieldsFromAndIntoEntry() {
        val header = Header.newGnu()
        header.setPath("test.txt")
        val fields = EntryFields(header = header)
        val entry = Entry<Read>(fields)

        val retrieved = EntryFields.from(entry)
        assertEquals(fields, retrieved)

        val newEntry = retrieved.intoEntry()
        assertEquals("test.txt", newEntry.path())
    }
}
