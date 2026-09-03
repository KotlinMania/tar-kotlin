// port-lint: tests all.rs
package io.github.kotlinmania.tar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuilderTest {
    @Test
    fun testAppendBasic() {
        val writer = ByteArrayWriter()
        val builder = Builder.new(writer)

        val header = Header.newGnu()
        header.setPath("hello.txt")
        val data = "Hello, world!".encodeToByteArray()
        header.setSize(data.size.toLong())
        header.setMode(420)
        header.setCksum()

        builder.append(header, Cursor(data))
        val finishedWriter = builder.intoInner()
        val tarBytes = finishedWriter.toByteArray()

        // Read back with Archive
        val archive = Archive.new(Cursor(tarBytes))
        val entries = archive.entries()
        assertTrue(entries.hasNext())
        val entry = entries.next()
        assertEquals("hello.txt", entry.path())
        assertEquals(data.size.toULong(), entry.size())

        val content = ByteArray(data.size)
        entry.readExact(content)
        assertEquals("Hello, world!", content.decodeToString())
    }

    @Test
    fun testAppendData() {
        val writer = ByteArrayWriter()
        val builder = Builder.new(writer)

        val header = Header.newGnu()
        val data = "File contents here".encodeToByteArray()
        header.setSize(data.size.toLong())
        header.setMode(420)

        builder.appendData(header, "dir/sub/test.txt", Cursor(data))
        builder.finish()

        val archive = Archive.new(Cursor(writer.toByteArray()))
        val entries = archive.entries()
        assertTrue(entries.hasNext())
        val entry = entries.next()
        assertEquals("dir/sub/test.txt", entry.path())
        assertEquals(data.size.toULong(), entry.size())

        val readBuf = ByteArray(data.size)
        entry.readExact(readBuf)
        assertEquals("File contents here", readBuf.decodeToString())
    }

    @Test
    fun testAppendDataLongName() {
        val writer = ByteArrayWriter()
        val builder = Builder.new(writer)

        val longPath = "a".repeat(120) + "/file.txt"
        val header = Header.newGnu()
        val data = "Data in long path file".encodeToByteArray()
        header.setSize(data.size.toLong())
        header.setMode(420)

        builder.appendData(header, longPath, Cursor(data))
        builder.finish()

        val archive = Archive.new(Cursor(writer.toByteArray()))
        val entries = archive.entries()
        assertTrue(entries.hasNext())
        val entry = entries.next()
        assertEquals(longPath, entry.path())

        val readBuf = ByteArray(data.size)
        entry.readExact(readBuf)
        assertEquals("Data in long path file", readBuf.decodeToString())
    }

    @Test
    fun testLongNameTrailingNul() {
        val writer = ByteArrayWriter()
        val builder = Builder.new(writer)

        val h1 = Header.newGnu()
        h1.setPath("././@LongLink")
        h1.setSize(4)
        h1.setEntryType(EntryType.new('L'.code.toUByte()))
        h1.setCksum()
        builder.append(h1, Cursor("foo\u0000".encodeToByteArray()))

        val h2 = Header.newGnu()
        h2.setPath("bar")
        h2.setSize(6)
        h2.setEntryType(EntryType.file())
        h2.setCksum()
        builder.append(h2, Cursor("foobar".encodeToByteArray()))

        val archive = Archive.new(Cursor(builder.intoInner().toByteArray()))
        val entries = archive.entries()
        assertTrue(entries.hasNext())
        val entry = entries.next()
        assertEquals("foo", entry.path())
    }

    @Test
    fun testLongLinknameTrailingNul() {
        val writer = ByteArrayWriter()
        val builder = Builder.new(writer)

        val h1 = Header.newGnu()
        h1.setPath("././@LongLink")
        h1.setSize(4)
        h1.setEntryType(EntryType.new('K'.code.toUByte()))
        h1.setCksum()
        builder.append(h1, Cursor("foo\u0000".encodeToByteArray()))

        val h2 = Header.newGnu()
        h2.setPath("bar")
        h2.setSize(6)
        h2.setEntryType(EntryType.file())
        h2.setCksum()
        builder.append(h2, Cursor("foobar".encodeToByteArray()))

        val archive = Archive.new(Cursor(builder.intoInner().toByteArray()))
        val entries = archive.entries()
        assertTrue(entries.hasNext())
        val entry = entries.next()
        assertEquals("foo", entry.linkName())
    }

    @Test
    fun testLongLinknameGnu() {
        for (t in listOf(EntryType.Symlink, EntryType.Link)) {
            val writer = ByteArrayWriter()
            val builder = Builder.new(writer)

            val header = Header.newGnu()
            header.setEntryType(t)
            header.setSize(0)

            val path = "usr/lib/.build-id/05/159ed904e45ff5100f7acd3d3b99fa7e27e34f"
            val target =
                "../../../../usr/lib64/qt5/plugins/wayland-graphics-integration-server" +
                    "/libqt-wayland-compositor-xcomposite-egl.so"

            builder.appendLink(header, path, target)
            val archive = Archive.new(Cursor(builder.intoInner().toByteArray()))
            val entries = archive.entries()
            assertTrue(entries.hasNext())
            val entry = entries.next()
            assertEquals(t, entry.header().entryType())
            assertEquals(path, entry.path())
            assertEquals(target, entry.linkName())
        }
    }

    @Test
    fun testLinknameLiteral() {
        for (t in listOf(EntryType.Symlink, EntryType.Link)) {
            val writer = ByteArrayWriter()
            val builder = Builder.new(writer)

            val header = Header.newGnu()
            header.setEntryType(t)
            header.setSize(0)

            val path = "usr/lib/systemd/systemd-sysv-install"
            val target = "../../..//sbin/chkconfig"
            header.setLinkNameLiteral(target.encodeToByteArray())

            builder.appendData(header, path, Empty())
            val archive = Archive.new(Cursor(builder.intoInner().toByteArray()))
            val entries = archive.entries()
            assertTrue(entries.hasNext())
            val entry = entries.next()
            assertEquals(t, entry.header().entryType())
            assertEquals(path, entry.path())
            assertEquals(target, entry.linkName())
        }
    }

    @Test
    fun testAppendWriter() {
        val seekWriter = SeekByteArrayWriter()
        val builder = Builder.new(seekWriter)

        val header = Header.newGnu()
        val entryWriter = builder.appendWriter(header, "streamed.txt")
        val chunk1 = "Hello, ".encodeToByteArray()
        val chunk2 = "streamed tar world!\n".encodeToByteArray()

        entryWriter.write(chunk1)
        entryWriter.write(chunk2)
        entryWriter.finish()
        builder.finish()

        val archive = Archive.new(Cursor(seekWriter.toByteArray()))
        val entries = archive.entries()
        assertTrue(entries.hasNext())
        val entry = entries.next()
        assertEquals("streamed.txt", entry.path())
        val expectedSize = (chunk1.size + chunk2.size).toULong()
        assertEquals(expectedSize, entry.size())

        val readBuf = ByteArray(expectedSize.toInt())
        entry.readExact(readBuf)
        assertEquals("Hello, streamed tar world!\n", readBuf.decodeToString())
    }

    @Test
    fun testFindSparseEntries() {
        val sparseBlockSize = SPARSE_BLOCK_SIZE.toULong()
        val fullySparseMap =
            listOf(
                SparseEntry(offset = 4uL * sparseBlockSize, numBytes = 0uL),
            )
        val denseMap =
            listOf(
                SparseEntry(offset = 0uL, numBytes = 4uL * sparseBlockSize),
                SparseEntry(offset = 4uL * sparseBlockSize, numBytes = 0uL),
            )

        val expectedSparse =
            SparseEntries(
                entries = fullySparseMap,
                onDiskSize = 0uL,
            )
        assertEquals(4uL * sparseBlockSize, expectedSparse.size())

        val expectedDense =
            SparseEntries(
                entries = denseMap,
                onDiskSize = 4uL * sparseBlockSize,
            )
        assertEquals(4uL * sparseBlockSize, expectedDense.size())

        val checkResult = looseCheckSparseEntries(expectedSparse, expectedSparse)
        assertEquals(null, checkResult)

        val checkDense = looseCheckSparseEntries(expectedDense, expectedDense)
        assertEquals(null, checkDense)
    }

    private fun looseCheckSparseEntries(
        reported: SparseEntries?,
        expected: SparseEntries?,
    ): String? {
        val rep = reported ?: return null
        val exp = expected ?: return "Expected dense file, but reported as sparse"

        for (e in exp.entries) {
            val covered =
                rep.entries.any { r ->
                    e.offset >= r.offset && (e.offset + e.numBytes) <= (r.offset + r.numBytes)
                }
            if (!covered) {
                return "Reported is not a superset of expected"
            }
        }

        if (rep.entries.lastOrNull() != exp.entries.lastOrNull()) {
            return "Last zero-length entry is not as expected"
        }

        var prevEnd: ULong? = null
        for (e in rep.entries) {
            val end = prevEnd
            if (end != null && e.offset < end) {
                return "Overlapping or unsorted entries"
            }
            prevEnd = e.offset + e.numBytes
        }

        val sumBytes = rep.entries.fold(0uL) { acc, e -> acc + e.numBytes }
        if (rep.onDiskSize != sumBytes) {
            return "Incorrect on-disk size"
        }

        return null
    }
}
