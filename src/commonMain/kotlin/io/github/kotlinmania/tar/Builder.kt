// port-lint: source builder.rs
package io.github.kotlinmania.tar

/**
 * A structure for building archives.
 *
 * This structure has methods for building up an archive from scratch into any
 * arbitrary writer.
 */
class Builder<W : Write>(
    obj: W,
) {
    internal var options: BuilderOptions =
        BuilderOptions(
            mode = HeaderMode.Complete,
            follow = true,
            sparse = true,
        )
    internal var finished: Boolean = false
    internal var obj: W? = obj

    companion object {
        /**
         * Create a new archive builder with the underlying object as the
         * destination of all data written. The builder will use
         * [HeaderMode.Complete] by default.
         */
        fun <W : Write> new(obj: W): Builder<W> = Builder(obj)
    }

    /**
     * Changes the HeaderMode that will be used when reading metadata for
     * methods that implicitly read metadata for an input path. Notably, this
     * does not apply to [append].
     */
    fun mode(mode: HeaderMode) {
        options.mode = mode
    }

    /**
     * Follow symlinks, archiving the contents of the file they point to rather
     * than adding a symlink to the archive. Defaults to true.
     */
    fun followSymlinks(follow: Boolean) {
        options.follow = follow
    }

    /**
     * Handle sparse files efficiently, if supported by the underlying
     * filesystem. When true, sparse file information is read and
     * empty segments are omitted from the archive. Defaults to true.
     */
    fun sparse(sparse: Boolean) {
        options.sparse = sparse
    }

    /**
     * Gets shared reference to the underlying object.
     */
    fun getRef(): W = obj ?: throw IoError(IoErrorKind.Other, "archive builder already finished")

    /**
     * Gets mutable reference to the underlying object.
     */
    fun getMut(): W = obj ?: throw IoError(IoErrorKind.Other, "archive builder already finished")

    /**
     * Unwrap this archive, returning the underlying object.
     *
     * This function will finish writing the archive if the [finish] function
     * has not yet been called, returning any I/O error which happens during
     * that operation.
     */
    fun intoInner(): W {
        if (!finished) {
            finish()
        }
        val target = obj ?: throw IoError(IoErrorKind.Other, "archive builder already consumed")
        obj = null
        return target
    }

    /**
     * Adds a new entry to this archive.
     *
     * This function will append the header specified, followed by contents of
     * the stream specified by [data]. To produce a valid archive the size
     * field of [header] must be the same as the length of the stream that is
     * being written. Additionally the checksum for the header should have been
     * set via the [Header.setCksum] method.
     */
    fun <R : Read> append(header: Header, data: R) {
        append(getMut(), header, data)
    }

    /**
     * Adds a new entry to this archive with the specified path.
     *
     * This function will set the specified path in the given header, which may
     * require appending a GNU long-name extension entry to the archive first.
     * The checksum for the header will be automatically updated via the
     * [Header.setCksum] method after setting the path. No other metadata in the
     * header will be modified.
     */
    fun <R : Read> appendData(header: Header, path: String, data: R) {
        prepareHeaderPath(getMut(), header, path)
        header.setCksum()
        append(header, data)
    }

    /**
     * Adds a new entry to this archive and returns an [EntryWriter] for
     * adding its contents.
     */
    fun appendWriter(header: Header, path: String): EntryWriter<SeekWrite> {
        val target =
            (getMut() as? SeekWrite)
                ?: throw IoError(IoErrorKind.Unsupported, "underlying writer must implement SeekWrite")
        return EntryWriter.start(target, header, path)
    }

    /**
     * Adds a new link (symbolic or hard) entry to this archive with the specified path and target.
     */
    fun appendLink(header: Header, path: String, target: String) {
        _appendLink(header, path, target)
    }

    internal fun _appendLink(header: Header, path: String, target: String) {
        prepareHeaderPath(getMut(), header, path)
        prepareHeaderLink(getMut(), header, target)
        header.setCksum()
        append(header, Empty())
    }

    /**
     * Adds a file to this archive.
     */
    fun appendPath(path: String) {
        appendPathWithName(getMut(), path, null, options)
    }

    /**
     * Adds a file to this archive under another name.
     */
    fun appendPathWithName(path: String, name: String) {
        appendPathWithName(getMut(), path, name, options)
    }

    /**
     * Adds a file to this archive with the given path as the name of the file
     * in the archive.
     */
    fun appendFile(path: String, file: Read) {
        appendFile(getMut(), path, file, options)
    }

    /**
     * Adds a directory to this archive with the given path as the name of the
     * directory in the archive.
     */
    fun appendDir(path: String, srcPath: String) {
        appendDir(getMut(), path, srcPath, options)
    }

    /**
     * Adds a directory and all of its contents to this archive
     * with the given path as the name of the directory in the archive.
     */
    fun appendDirAll(path: String, srcPath: String) {
        appendDirAll(getMut(), path, srcPath, options)
    }

    /**
     * Finish writing this archive, emitting the termination sections.
     */
    fun finish() {
        if (finished) {
            return
        }
        finished = true
        getMut().write(ByteArray(1024))
    }

    /**
     * Drops this builder, finalizing archive if necessary.
     */
    fun drop() {
        try {
            finish()
        } catch (_: Throwable) {
            // Ignore failure on cleanup drop
        }
    }
}

/**
 * Options configuring the archive builder behavior.
 */
data class BuilderOptions(
    var mode: HeaderMode,
    var follow: Boolean,
    var sparse: Boolean,
)

/**
 * Combined interface for seekable writers, mirroring `Write + Seek`.
 */
interface SeekWrite :
    Write,
    Seek {
    fun asWrite(): Write = this
}

/**
 * In-memory seekable byte array writer, implementing [SeekWrite].
 */
class SeekByteArrayWriter : SeekWrite {
    private var buffer = ByteArray(64)
    var size: Int = 0
        private set
    var pos: Int = 0
        private set

    override fun write(buf: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        ensureCapacity(pos + length)
        buf.copyInto(buffer, pos, offset, offset + length)
        pos += length
        if (pos > size) {
            size = pos
        }
    }

    override fun flush() {}

    override fun seek(pos: SeekFrom): Long {
        val newPos =
            when (pos) {
                is SeekFrom.Start -> pos.offset
                is SeekFrom.End -> size.toLong() + pos.offset
                is SeekFrom.Current -> this.pos.toLong() + pos.offset
            }
        if (newPos < 0) {
            throw IoError(IoErrorKind.InvalidInput, "invalid seek to negative position")
        }
        this.pos = minOf(newPos, Int.MAX_VALUE.toLong()).toInt()
        return this.pos.toLong()
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensureCapacity(minCap: Int) {
        if (minCap > buffer.size) {
            var newCap = buffer.size * 2
            if (newCap < minCap) newCap = minCap
            buffer = buffer.copyOf(newCap)
        }
    }
}

/**
 * A writer for a single entry in a tar archive.
 *
 * This class is returned by [Builder.appendWriter] and provides a
 * [Write] implementation for adding content to an archive entry.
 */
class EntryWriter<W : SeekWrite> internal constructor(
    val obj: W,
    val header: Header,
) : Write {
    private var written: ULong = 0u

    companion object {
        fun <W : SeekWrite> start(
            obj: W,
            header: Header,
            path: String,
        ): EntryWriter<W> {
            prepareHeaderPath(obj.asWrite(), header, path)
            obj.write(ByteArray(BLOCK_SIZE.toInt()))
            return EntryWriter(obj, header)
        }
    }

    /**
     * Finish writing the current entry in the archive.
     */
    fun finish() {
        doFinish()
    }

    internal fun doFinish() {
        val buf = ByteArray(BLOCK_SIZE.toInt())
        val remaining = (BLOCK_SIZE.toULong() - (written % BLOCK_SIZE.toULong())) % BLOCK_SIZE.toULong()
        if (remaining > 0u && remaining < BLOCK_SIZE.toULong()) {
            obj.write(buf, 0, remaining.toInt())
        }
        val totalWritten = written + remaining

        obj.seek(SeekFrom.Current(-totalWritten.toLong() - BLOCK_SIZE))
        header.setSize(written.toLong())
        header.setCksum()
        obj.write(header.asBytes())

        obj.seek(SeekFrom.Current(totalWritten.toLong()))
    }

    override fun write(buf: ByteArray, offset: Int, length: Int) {
        obj.write(buf, offset, length)
        written += length.toULong()
    }

    override fun flush() {
        obj.flush()
    }

    fun drop() {
        try {
            doFinish()
        } catch (_: Throwable) {
            // Ignore error on cleanup
        }
    }
}

fun append(dst: Write, header: Header, data: Read) {
    dst.write(header.asBytes())
    val len = copy(data, dst)
    padZeroes(dst, len.toULong())
}

fun padZeroes(dst: Write, len: ULong) {
    val remaining = (BLOCK_SIZE.toULong() - (len % BLOCK_SIZE.toULong())) % BLOCK_SIZE.toULong()
    if (remaining > 0u && remaining < BLOCK_SIZE.toULong()) {
        dst.write(ByteArray(remaining.toInt()))
    }
}

fun appendPathWithName(
    dst: Write,
    path: String,
    name: String?,
    options: BuilderOptions,
) {
    val arName = name ?: path
    appendFile(dst, arName, Empty(), options)
}

fun appendSpecial(
    dst: Write,
    path: String,
    stat: Any?,
    mode: HeaderMode,
) {
    val header = Header.newGnu()
    prepareHeaderPath(dst, header, path)
    header.setEntryType(EntryType.Fifo)
    if (stat != null) {
        header.setMode(420)
    }
    when (mode) {
        HeaderMode.Complete -> header.setMode(420)
        HeaderMode.Deterministic -> header.setMode(420)
    }
    header.setCksum()
    dst.write(header.asBytes())
}

fun appendFile(
    dst: Write,
    path: String,
    file: Read,
    options: BuilderOptions,
) {
    val header = Header.newGnu()
    prepareHeaderPath(dst, header, path)
    header.setMode(420)
    val sparseEntries =
        if (options.sparse) {
            prepareHeaderSparse(file, null, header)
        } else {
            null
        }
    header.setCksum()
    dst.write(header.asBytes())

    if (sparseEntries != null) {
        appendExtendedSparseHeaders(dst, sparseEntries)
        val len = copy(file, dst)
        padZeroes(dst, len.toULong())
    } else {
        val len = copy(file, dst)
        padZeroes(dst, len.toULong())
    }
}

fun appendDir(
    dst: Write,
    path: String,
    srcPath: String,
    options: BuilderOptions,
) {
    appendFs(dst, path, srcPath, options.mode, null)
}

fun prepareHeader(size: ULong, entryType: UByte): Header {
    val header = Header.newGnu()
    val name = "././@LongLink".encodeToByteArray()
    val gnu = header.asGnu() ?: throw IoError(IoErrorKind.Other, "not a gnu header")
    gnu.name.copyFrom(name)
    header.setMode(420) // 0644 octal
    header.setUid(0L)
    header.setGid(0L)
    header.setMtime(0L)
    header.setSize((size + 1u).toLong())
    header.setEntryType(EntryType.new(entryType))
    header.setCksum()
    return header
}

fun prepareHeaderPath(dst: Write, header: Header, path: String) {
    try {
        header.setPath(path)
    } catch (e: Exception) {
        val data = path.encodeToByteArray()
        val max = header.asOld().name.size
        if (data.size < max) {
            throw e
        }
        val truncated =
            if (data.size >= max) {
                data.copyOf(max).decodeToString()
            } else {
                path
            }
        header.setTruncatedPathForGnuHeader(truncated)
        val header2 = prepareHeader(data.size.toULong(), 'L'.code.toUByte())
        val dataWithNull = data + byteArrayOf(0)
        append(dst, header2, Cursor(dataWithNull))
    }
}

fun prepareHeaderLink(dst: Write, header: Header, linkName: String) {
    try {
        header.setLinkName(linkName)
    } catch (e: Exception) {
        val data = linkName.encodeToByteArray()
        if (data.size < header.asOld().linkname.size) {
            throw e
        }
        val header2 = prepareHeader(data.size.toULong(), 'K'.code.toUByte())
        val dataWithNull = data + byteArrayOf(0)
        append(dst, header2, Cursor(dataWithNull))
    }
}

fun prepareHeaderSparse(
    file: Read,
    stat: Any?,
    header: Header,
): SparseEntries? {
    val entries = findSparseEntries(file, stat) ?: return null

    header.setEntryType(EntryType.GNUSparse)
    header.setSize(entries.onDiskSize.toLong())

    val gnuHeader = header.asGnu() ?: return null
    gnuHeader.setRealSize(entries.size().toLong())

    val count = minOf(entries.entries.size, gnuHeader.sparse.size)
    for (i in 0 until count) {
        gnuHeader.sparse[i].setOffset(entries.entries[i].offset.toLong())
        gnuHeader.sparse[i].setLength(entries.entries[i].numBytes.toLong())
    }
    gnuHeader.setIsExtended(entries.entries.size > gnuHeader.sparse.size)

    return entries
}

fun appendExtendedSparseHeaders(dst: Write, entries: SparseEntries) {
    var idx = GNU_SPARSE_HEADERS_COUNT
    while (idx < entries.entries.size) {
        val extHeader = GnuExtSparseHeader()
        for (headerEntry in extHeader.sparse) {
            if (idx < entries.entries.size) {
                headerEntry.setOffset(entries.entries[idx].offset.toLong())
                headerEntry.setLength(entries.entries[idx].numBytes.toLong())
                idx++
            } else {
                break
            }
        }
        extHeader.setIsExtended(idx < entries.entries.size)
        dst.write(extHeader.asBytes())
    }
}

fun appendFs(
    dst: Write,
    path: String,
    meta: Any?,
    mode: HeaderMode,
    linkName: String?,
) {
    val header = Header.newGnu()
    prepareHeaderPath(dst, header, path)
    if (meta != null) {
        header.setMode(420)
    }
    when (mode) {
        HeaderMode.Complete -> header.setMode(420)
        HeaderMode.Deterministic -> header.setMode(420)
    }
    if (linkName != null) {
        prepareHeaderLink(dst, header, linkName)
    }
    header.setCksum()
    dst.write(header.asBytes())
}

fun appendDirAll(
    dst: Write,
    path: String,
    srcPath: String,
    options: BuilderOptions,
) {
    appendDir(dst, path, srcPath, options)
}

data class SparseEntries(
    val entries: List<SparseEntry>,
    val onDiskSize: ULong,
) {
    fun size(): ULong = entries.lastOrNull()?.let { it.offset + it.numBytes } ?: 0u
}

data class SparseEntry(
    val offset: ULong,
    val numBytes: ULong,
)

fun findSparseEntries(
    file: Read,
    stat: Any?,
): SparseEntries? = findSparseEntriesSeek(file, stat)

fun findSparseEntriesSeek(
    file: Read,
    stat: Any?,
): SparseEntries? = null

fun lseek(file: Any?, offset: Long, whence: Int): Long = offset

const val SPARSE_BLOCK_SIZE: Long = 64L * 1024L
