// port-lint: source archive.rs
package io.github.kotlinmania.tar

/**
 * A top-level representation of an archive file.
 *
 * This archive can have an entry added to it and it can be iterated over.
 */
class Archive<R : Read>(
    obj: R,
) {
    internal val inner: ArchiveInner<R> = ArchiveInner(obj)

    companion object {
        /**
         * Create a new archive with the underlying object as the reader.
         */
        fun <R : Read> new(obj: R): Archive<R> = Archive(obj)
    }

    /**
     * Unwrap this archive, returning the underlying object.
     */
    fun intoInner(): R = inner.obj

    /**
     * Construct an iterator over the entries in this archive.
     *
     * Note that care must be taken to consider each entry within an archive in
     * sequence. If entries are processed out of sequence (from what the
     * iterator returns), then the contents read for each entry may be
     * corrupted.
     */
    fun entries(): Entries<R> {
        val fields = _entries(null)
        return Entries(fields)
    }

    /**
     * Construct an iterator over the entries in this archive for a seekable
     * reader. Seek will be used to efficiently skip over file contents.
     *
     * Note that care must be taken to consider each entry within an archive in
     * sequence. If entries are processed out of sequence (from what the
     * iterator returns), then the contents read for each entry may be
     * corrupted.
     */
    fun entriesWithSeek(): Entries<R> {
        if (inner.obj !is Seek) {
            throw other("archive reader does not implement Seek")
        }
        val fields = _entries(inner)
        return Entries(fields)
    }

    /**
     * Unpacks the contents of this tarball into the specified [dst] directory path.
     *
     * This function will iterate over the entire contents of this tarball,
     * extracting each file in turn to the location specified by the entry's
     * path name.
     */
    fun unpack(dst: String) {
        _unpack(dst)
    }

    /**
     * Set the mask of the permission bits when unpacking this entry.
     *
     * The mask will be inverted when applying against a mode, similar to how
     * `umask` works on Unix. In logical notation it looks like:
     *
     * ```text
     * newMode = oldMode and (mask.inv())
     * ```
     *
     * The mask is 0 by default and is currently only implemented on Unix.
     */
    fun setMask(mask: UInt) {
        inner.mask = mask
    }

    /**
     * Indicate whether extended file attributes (xattrs on Unix) are preserved
     * when unpacking this archive.
     *
     * This flag is disabled by default and is currently only implemented on
     * Unix using xattr support.
     */
    fun setUnpackXattrs(unpackXattrs: Boolean) {
        inner.unpackXattrs = unpackXattrs
    }

    /**
     * Indicate whether extended permissions (like suid on Unix) are preserved
     * when unpacking this entry.
     *
     * This flag is disabled by default and is currently only implemented on
     * Unix.
     */
    fun setPreservePermissions(preserve: Boolean) {
        inner.preservePermissions = preserve
    }

    /**
     * Indicate whether numeric ownership ids (like uid and gid on Unix)
     * are preserved when unpacking this entry.
     *
     * This flag is disabled by default and is currently only implemented on
     * Unix.
     */
    fun setPreserveOwnerships(preserve: Boolean) {
        inner.preserveOwnerships = preserve
    }

    /**
     * Indicate whether files and symlinks should be overwritten on extraction.
     */
    fun setOverwrite(overwrite: Boolean) {
        inner.overwrite = overwrite
    }

    /**
     * Indicate whether access time information is preserved when unpacking
     * this entry.
     *
     * This flag is enabled by default.
     */
    fun setPreserveMtime(preserve: Boolean) {
        inner.preserveMtime = preserve
    }

    /**
     * Ignore zeroed headers, which would otherwise indicate to the archive that it has no more
     * entries.
     *
     * This can be used in case multiple tar archives have been concatenated together.
     */
    fun setIgnoreZeros(ignoreZeros: Boolean) {
        inner.ignoreZeros = ignoreZeros
    }

    internal fun _entries(seekable: SeekRead?): EntriesFields {
        if (inner.pos != 0uL) {
            throw other("cannot call entries unless archive is at position 0")
        }
        return EntriesFields(
            archive = inner,
            seekableArchive = seekable,
            nextOffset = 0uL,
            done = false,
            raw = false,
        )
    }

    internal fun _unpack(dst: String) {
        val entriesIter = _entries(null)
        val directories = mutableListOf<Entry<R>>()

        while (true) {
            val nextEntry = entriesIter.nextEntry() ?: break
            val entry = Entry<R>(nextEntry.fields)
            if (entry.header().entryType() == EntryType.Directory) {
                directories.add(entry)
            } else {
                entry.unpackIn(dst)
            }
        }

        // Apply the directories in reverse order
        directories.sortWith { a, b -> compareByteArrays(b.pathBytes(), a.pathBytes()) }
        for (dir in directories) {
            dir.unpackIn(dst)
        }
    }
}

/**
 * Combined interface for seekable readers, mirroring `std::io::Seek + Read`.
 */
interface SeekRead :
    Read,
    Seek

/**
 * Inner storage and configuration for [Archive].
 */
class ArchiveInner<R : Read>(
    val obj: R,
) : SeekRead {
    var pos: ULong = 0uL
    var mask: UInt = 0u
    var unpackXattrs: Boolean = false
    var preservePermissions: Boolean = false
    var preserveOwnerships: Boolean = false
    var preserveMtime: Boolean = true
    var overwrite: Boolean = true
    var ignoreZeros: Boolean = false

    override fun read(into: ByteArray, offset: Int, length: Int): Int {
        val count = obj.read(into, offset, length)
        if (count > 0) {
            pos += count.toULong()
        }
        return count
    }

    override fun seek(pos: SeekFrom): Long {
        val seekable =
            (obj as? Seek)
                ?: throw other("Underlying reader is not seekable")
        val newPos = seekable.seek(pos)
        this.pos = newPos.toULong()
        return newPos
    }
}

/**
 * An iterator over the entries of an archive.
 */
class Entries<R : Read> internal constructor(
    internal val fields: EntriesFields,
) : Iterator<Entry<R>>,
    Iterable<Entry<R>> {
    private var nextItem: Entry<R>? = null
    private var done: Boolean = false

    /**
     * Indicates whether this iterator will return raw entries or not.
     *
     * If the raw list of entries is returned, then no preprocessing happens
     * on account of this library, for example taking into account GNU long name
     * or long link archive members. Raw iteration is disabled by default.
     */
    fun raw(raw: Boolean): Entries<R> {
        fields.raw = raw
        return this
    }

    override fun iterator(): Iterator<Entry<R>> = this

    override fun hasNext(): Boolean {
        if (done) return false
        if (nextItem != null) return true
        val entry = fields.advance()
        if (entry == null) {
            done = true
            return false
        }
        nextItem = Entry<R>(entry.fields)
        return true
    }

    override fun next(): Entry<R> {
        if (!hasNext()) {
            throw NoSuchElementException("No more entries in archive")
        }
        val item = nextItem ?: throw NoSuchElementException("No more entries in archive")
        nextItem = null
        return item
    }
}

internal class EntriesFields(
    val archive: ArchiveInner<*>,
    val seekableArchive: SeekRead?,
    var nextOffset: ULong,
    var done: Boolean,
    var raw: Boolean,
) {
    fun nextEntryRaw(paxExtensions: ByteArray?): Entry<Read>? {
        val header = Header.newOld()
        val headerBytes = header.asMutBytes()
        var headerPos = nextOffset
        val blockSize = BLOCK_SIZE.toULong()

        while (true) {
            val delta = nextOffset - archive.pos
            skip(delta)

            if (!tryReadAll(archive, headerBytes)) {
                return null
            }

            var allZeros = true
            for (b in headerBytes) {
                if (b != 0.toByte()) {
                    allZeros = false
                    break
                }
            }

            if (!allZeros) {
                nextOffset += blockSize
                break
            }

            if (!archive.ignoreZeros) {
                return null
            }
            nextOffset += blockSize
            headerPos = nextOffset
        }

        var sum = 0L
        for (i in 0 until 148) {
            sum += (headerBytes[i].toInt() and 0xFF).toLong()
        }
        for (i in 156 until BLOCK_SIZE.toInt()) {
            sum += (headerBytes[i].toInt() and 0xFF).toLong()
        }
        sum += 8L * 32L

        val cksum = header.cksum().toLong()
        if (sum != cksum) {
            throw other("archive header checksum mismatch")
        }

        var paxSize: ULong? = null
        if (paxExtensions != null) {
            paxSize = paxExtensionsValue(paxExtensions, PAX_SIZE)
            val paxUid = paxExtensionsValue(paxExtensions, PAX_UID)
            if (paxUid != null) {
                header.setUid(paxUid.toLong())
            }
            val paxGid = paxExtensionsValue(paxExtensions, PAX_GID)
            if (paxGid != null) {
                header.setGid(paxGid.toLong())
            }
        }

        val filePos = nextOffset
        var size = header.entrySize().toULong()
        if (paxSize != null) {
            size = paxSize
        }

        val ret =
            EntryFields(
                size = size,
                headerPos = headerPos,
                filePos = filePos,
                data = mutableListOf(EntryIo.Data(archive.take(size.toLong()))),
                header = header,
                longPathname = null,
                longLinkname = null,
                paxExtensionsData = null,
                mask = archive.mask,
                unpackXattrs = archive.unpackXattrs,
                preservePermissions = archive.preservePermissions,
                preserveMtime = archive.preserveMtime,
                overwrite = archive.overwrite,
                preserveOwnerships = archive.preserveOwnerships,
            )

        val sizeWithPadding = size + (blockSize - 1uL)
        val alignedSize = sizeWithPadding and (blockSize - 1uL).inv()
        nextOffset += alignedSize

        return ret.intoEntry()
    }

    fun nextEntry(): Entry<Read>? {
        if (raw) {
            return nextEntryRaw(null)
        }

        var gnuLongname: ByteArray? = null
        var gnuLonglink: ByteArray? = null
        var paxExtensions: ByteArray? = null
        var processed = 0

        while (true) {
            processed += 1
            val entry =
                nextEntryRaw(paxExtensions) ?: run {
                    if (processed > 1) {
                        throw other("members found describing a future member but no future member found")
                    }
                    return null
                }

            val isRecognizedHeader =
                entry.header().asGnu() != null || entry.header().asUstar() != null

            if (isRecognizedHeader && entry.header().entryType().isGnuLongname()) {
                if (gnuLongname != null) {
                    throw other("two long name entries describing the same member")
                }
                gnuLongname = EntryFields.from(entry).readAll()
                continue
            }

            if (isRecognizedHeader && entry.header().entryType().isGnuLonglink()) {
                if (gnuLonglink != null) {
                    throw other("two long name entries describing the same member")
                }
                gnuLonglink = EntryFields.from(entry).readAll()
                continue
            }

            if (isRecognizedHeader && entry.header().entryType().isPaxLocalExtensions()) {
                if (paxExtensions != null) {
                    throw other("two pax extensions entries describing the same member")
                }
                paxExtensions = EntryFields.from(entry).readAll()
                continue
            }

            val fields = EntryFields.from(entry)
            fields.longPathname = gnuLongname
            fields.longLinkname = gnuLonglink
            fields.paxExtensionsData = paxExtensions
            parseSparseHeader(fields)
            return fields.intoEntry()
        }
    }

    fun parseSparseHeader(entry: EntryFields) {
        if (!entry.header.entryType().isGnuSparse()) {
            return
        }
        val gnu =
            entry.header.asGnu()
                ?: throw other("sparse entry type listed but not GNU header")

        entry.data.clear()

        var cur = 0uL
        var remaining = entry.size

        val data = entry.data
        val reader = archive
        val totalSize = entry.size
        val blockSize = BLOCK_SIZE.toULong()

        val addBlock: (GnuSparseHeader) -> Unit = { block ->
            if (!block.isEmpty()) {
                val off = block.offset().toULong()
                val len = block.length().toULong()
                if (len != 0uL && (totalSize - remaining) % blockSize != 0uL) {
                    throw other("previous block in sparse file was not aligned to 512-byte boundary")
                } else if (off < cur) {
                    throw other("out of order or overlapping sparse blocks")
                } else if (cur < off) {
                    val padBlock = Repeat(0.toByte()).take((off - cur).toLong())
                    data.add(EntryIo.Pad(padBlock))
                }
                cur = off + len
                if (remaining < len) {
                    throw other("sparse file consumed more data than the header listed")
                }
                remaining -= len
                data.add(EntryIo.Data(reader.take(len.toLong())))
            }
        }

        for (block in gnu.sparse) {
            addBlock(block)
        }

        if (gnu.isExtended()) {
            val ext = GnuExtSparseHeader.new()
            ext.setIsExtended(true)
            while (ext.isExtended()) {
                if (!tryReadAll(archive, ext.asMutBytes())) {
                    throw other("failed to read extension")
                }
                nextOffset += blockSize
                for (block in ext.sparse) {
                    addBlock(block)
                }
            }
        }

        if (cur != gnu.realSize().toULong()) {
            throw other("mismatch in sparse file chunks and size in header")
        }
        entry.size = cur
        if (remaining > 0uL) {
            throw other("mismatch in sparse file chunks and entry size in header")
        }
    }

    fun skip(amtParam: ULong) {
        var amt = amtParam
        val seekable = seekableArchive
        if (seekable != null) {
            val offset = amt.toLong()
            if (offset < 0L) {
                throw other("seek position out of bounds")
            }
            seekable.seek(SeekFrom.Current(offset))
        } else {
            val buf = ByteArray(4096 * 8)
            while (amt > 0uL) {
                val nToRead = minOf(amt, buf.size.toULong()).toInt()
                val n = archive.read(buf, 0, nToRead)
                if (n <= 0) {
                    throw other("unexpected EOF during skip")
                }
                amt -= n.toULong()
            }
        }
    }

    internal fun advance(): Entry<Read>? = next()

    fun next(): Entry<Read>? {
        if (done) {
            return null
        }
        val e =
            try {
                nextEntry()
            } catch (e: Exception) {
                done = true
                throw e
            }
        if (e == null) {
            done = true
            return null
        }
        return e
    }
}

/**
 * Try to fill the buffer from the reader.
 *
 * If the reader reaches its end before filling the buffer at all, returns `false`.
 * Otherwise returns `true`.
 */
fun tryReadAll(r: Read, buf: ByteArray): Boolean {
    var read = 0
    while (read < buf.size) {
        val n = r.read(buf, read, buf.size - read)
        if (n <= 0) {
            if (read == 0) {
                return false
            }
            throw other("failed to read entire block")
        }
        read += n
    }
    return true
}

private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
    val minLen = minOf(a.size, b.size)
    for (i in 0 until minLen) {
        val cmp = (a[i].toInt() and 0xFF).compareTo(b[i].toInt() and 0xFF)
        if (cmp != 0) return cmp
    }
    return a.size.compareTo(b.size)
}
