// port-lint: tests all.rs
package io.github.kotlinmania.tar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveTest {
    private fun createTarBytes(files: List<Pair<String, ByteArray>>): ByteArray {
        val out = mutableListOf<Byte>()
        for ((name, content) in files) {
            val h = Header.newGnu()
            h.setPath(name)
            h.setSize(content.size.toLong())
            h.setMode(420) // 0644 octal
            h.setCksum()
            out.addAll(h.asBytes().toList())
            out.addAll(content.toList())
            val pad = (512 - (content.size % 512)) % 512
            repeat(pad) { out.add(0) }
        }
        // Two zero blocks at the end
        repeat(1024) { out.add(0) }
        return out.toByteArray()
    }

    @Test
    fun testArchiveEntriesBasic() {
        val file1Content = "Hello from file 1!\n".encodeToByteArray()
        val file2Content = "Short file 2".encodeToByteArray()
        val tarBytes =
            createTarBytes(
                listOf(
                    "file1.txt" to file1Content,
                    "dir/file2.txt" to file2Content,
                ),
            )

        val cursor = Cursor(tarBytes)
        val ar = Archive.new(cursor)
        val entries = ar.entries()

        assertTrue(entries.hasNext())
        val e1 = entries.next()
        assertEquals("file1.txt", e1.path())
        assertEquals(file1Content.size.toULong(), e1.size())
        val buf1 = ByteArray(file1Content.size)
        e1.readExact(buf1)
        assertEquals("Hello from file 1!\n", buf1.decodeToString())

        assertTrue(entries.hasNext())
        val e2 = entries.next()
        assertEquals("dir/file2.txt", e2.path())
        assertEquals(file2Content.size.toULong(), e2.size())
        val buf2 = ByteArray(file2Content.size)
        e2.readExact(buf2)
        assertEquals("Short file 2", buf2.decodeToString())

        assertFalse(entries.hasNext())
    }

    @Test
    fun testArchiveEntriesWithSeek() {
        val file1Content = "A".repeat(1000).encodeToByteArray()
        val file2Content = "B".repeat(500).encodeToByteArray()
        val tarBytes =
            createTarBytes(
                listOf(
                    "large1.txt" to file1Content,
                    "large2.txt" to file2Content,
                ),
            )

        val cursor = Cursor(tarBytes)
        val ar = Archive.new(cursor)
        val entries = ar.entriesWithSeek()

        assertTrue(entries.hasNext())
        val e1 = entries.next()
        assertEquals("large1.txt", e1.path())
        // Skip reading e1 body entirely to exercise seek-skipping
        assertTrue(entries.hasNext())
        val e2 = entries.next()
        assertEquals("large2.txt", e2.path())
        val buf2 = ByteArray(file2Content.size)
        e2.readExact(buf2)
        assertEquals("B".repeat(500), buf2.decodeToString())

        assertFalse(entries.hasNext())
    }

    @Test
    fun testArchiveSimpleConcat() {
        val fileContent = "Content for concat test".encodeToByteArray()
        val singleArchive = createTarBytes(listOf("file.txt" to fileContent))

        // Concatenate two archives together (with null blocks in-between)
        val doubleArchive = singleArchive + singleArchive

        // Without ignore_zeros: should only yield the entry from the first archive
        val ar1 = Archive.new(Cursor(doubleArchive))
        val names1 = mutableListOf<String>()
        for (entry in ar1.entries()) {
            names1.add(entry.path())
        }
        assertEquals(listOf("file.txt"), names1)

        // With ignore_zeros: should yield entries from both archives
        val ar2 = Archive.new(Cursor(doubleArchive))
        ar2.setIgnoreZeros(true)
        val names2 = mutableListOf<String>()
        for (entry in ar2.entries()) {
            names2.add(entry.path())
        }
        assertEquals(listOf("file.txt", "file.txt"), names2)
    }

    @Test
    fun testArchiveIntoInner() {
        val data = createTarBytes(listOf("test.txt" to "abc".encodeToByteArray()))
        val cursor = Cursor(data)
        val ar = Archive.new(cursor)
        assertEquals(cursor, ar.intoInner())
    }

    @Test
    fun testArchiveChecksumMismatch() {
        val data = createTarBytes(listOf("file.txt" to "test".encodeToByteArray()))
        // Corrupt byte in header outside cksum field
        data[0] = (data[0] + 1).toByte()

        val ar = Archive.new(Cursor(data))
        val entries = ar.entries()
        assertFailsWith<IoError> {
            entries.hasNext()
        }
    }

    @Test
    fun testArchiveGnuLongname() {
        val longName = "a/very/long/path/name/that/exceeds/the/standard/one/hundred/character/limit/for/tar/headers/file.txt"
        val longNameBytes = "$longName\u0000".encodeToByteArray()
        val content = "long name file content".encodeToByteArray()

        val out = mutableListOf<Byte>()

        // 1. Long name header and block
        val longHeader = Header.newGnu()
        longHeader.setPath("././@LongLink")
        longHeader.setEntryType(EntryType.GNULongName)
        longHeader.setSize(longNameBytes.size.toLong())
        longHeader.setCksum()
        out.addAll(longHeader.asBytes().toList())
        out.addAll(longNameBytes.toList())
        val pad1 = (512 - (longNameBytes.size % 512)) % 512
        repeat(pad1) { out.add(0) }

        // 2. Real file header and content
        val fileHeader = Header.newGnu()
        fileHeader.setPath("truncated_name.txt")
        fileHeader.setSize(content.size.toLong())
        fileHeader.setCksum()
        out.addAll(fileHeader.asBytes().toList())
        out.addAll(content.toList())
        val pad2 = (512 - (content.size % 512)) % 512
        repeat(pad2) { out.add(0) }

        // 3. Two zero end blocks
        repeat(1024) { out.add(0) }

        val ar = Archive.new(Cursor(out.toByteArray()))
        val entries = ar.entries()
        assertTrue(entries.hasNext())
        val e = entries.next()
        assertEquals(longName, e.path())
        val buf = ByteArray(content.size)
        e.readExact(buf)
        assertEquals("long name file content", buf.decodeToString())
        assertFalse(entries.hasNext())
    }

    @Test
    fun testArchiveRawEntries() {
        val longName = "a/very/long/path/file.txt"
        val longNameBytes = "$longName\u0000".encodeToByteArray()
        val content = "content".encodeToByteArray()

        val out = mutableListOf<Byte>()

        val longHeader = Header.newGnu()
        longHeader.setPath("././@LongLink")
        longHeader.setEntryType(EntryType.GNULongName)
        longHeader.setSize(longNameBytes.size.toLong())
        longHeader.setCksum()
        out.addAll(longHeader.asBytes().toList())
        out.addAll(longNameBytes.toList())
        val pad1 = (512 - (longNameBytes.size % 512)) % 512
        repeat(pad1) { out.add(0) }

        val fileHeader = Header.newGnu()
        fileHeader.setPath("file.txt")
        fileHeader.setSize(content.size.toLong())
        fileHeader.setCksum()
        out.addAll(fileHeader.asBytes().toList())
        out.addAll(content.toList())
        val pad2 = (512 - (content.size % 512)) % 512
        repeat(pad2) { out.add(0) }

        repeat(1024) { out.add(0) }

        val ar = Archive.new(Cursor(out.toByteArray()))
        val entries = ar.entries().raw(true)

        // Raw mode should yield the LongLink header as its own entry
        assertTrue(entries.hasNext())
        val raw1 = entries.next()
        assertEquals(EntryType.GNULongName, raw1.header().entryType())

        assertTrue(entries.hasNext())
        val raw2 = entries.next()
        assertEquals("file.txt", raw2.path())

        assertFalse(entries.hasNext())
    }

    @Test
    fun testArchiveEmpty() {
        val emptyBytes = ByteArray(1024)
        val ar = Archive.new(Cursor(emptyBytes))
        val entries = ar.entries()
        assertFalse(entries.hasNext())
    }
}
