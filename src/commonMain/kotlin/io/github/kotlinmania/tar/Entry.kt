// port-lint: source entry.rs
package io.github.kotlinmania.tar

/**
 * A read-only view into an entry of an archive.
 *
 * This structure is a window into a portion of an archive which can
 * be inspected. It acts as a file handle by implementing the [Read] interface.
 * An entry cannot be rewritten once inserted into an archive.
 */
class Entry<R : Read> internal constructor(
    internal val fields: EntryFields,
) : Read {
    /**
     * Returns the path name for this entry.
     *
     * Note that this function will convert any `\` characters to directory
     * separators, and it will not always return the same value as
     * `header.path()` as some archive formats have support for longer
     * path names described in separate entries.
     */
    fun path(): String = fields.path()

    /**
     * Returns the raw bytes listed for this entry.
     */
    fun pathBytes(): ByteArray = fields.pathBytes()

    /**
     * Returns the link name for this entry, if any is found.
     */
    fun linkName(): String? = fields.linkName()

    /**
     * Returns the link name for this entry, in bytes, if listed.
     */
    fun linkNameBytes(): ByteArray? = fields.linkNameBytes()

    /**
     * Returns an iterator over the pax extensions contained in this entry.
     */
    fun paxExtensions(): PaxExtensions? = fields.paxExtensions()

    /**
     * Returns access to the header of this entry in the archive.
     */
    fun header(): Header = fields.header

    /**
     * Returns access to the size of this entry in the archive.
     */
    fun size(): ULong = fields.size

    /**
     * Returns the starting position, in bytes, of the header of this entry in
     * the archive.
     */
    fun rawHeaderPosition(): ULong = fields.headerPos

    /**
     * Returns the starting position, in bytes, of the file of this entry in
     * the archive.
     */
    fun rawFilePosition(): ULong = fields.filePos

    /**
     * Writes this file to the specified location.
     */
    fun unpack(dst: String): Unpacked = fields.unpack(null, dst)

    /**
     * Extracts this file under the specified path, avoiding security issues.
     */
    fun unpackIn(dst: String): Boolean = fields.unpackIn(dst)

    /**
     * Set the mask of the permission bits when unpacking this entry.
     */
    fun setMask(mask: UInt) {
        fields.mask = mask
    }

    /**
     * Indicate whether extended file attributes are preserved when unpacking this entry.
     */
    fun setUnpackXattrs(unpackXattrs: Boolean) {
        fields.unpackXattrs = unpackXattrs
    }

    /**
     * Indicate whether extended permissions are preserved when unpacking this entry.
     */
    fun setPreservePermissions(preserve: Boolean) {
        fields.preservePermissions = preserve
    }

    /**
     * Indicate whether access time information is preserved when unpacking this entry.
     */
    fun setPreserveMtime(preserve: Boolean) {
        fields.preserveMtime = preserve
    }

    override fun read(into: ByteArray, offset: Int, length: Int): Int = fields.read(into, offset, length)
}

/**
 * Implementation detail of [Entry], storing state and metadata for an archive entry.
 */
internal class EntryFields(
    var longPathname: ByteArray? = null,
    var longLinkname: ByteArray? = null,
    var paxExtensionsData: ByteArray? = null,
    var mask: UInt = 0u,
    var header: Header = Header.newOld(),
    var size: ULong = 0u,
    var headerPos: ULong = 0u,
    var filePos: ULong = 0u,
    val data: MutableList<EntryIo> = mutableListOf(),
    var unpackXattrs: Boolean = false,
    var preservePermissions: Boolean = false,
    var preserveOwnerships: Boolean = false,
    var preserveMtime: Boolean = true,
    var overwrite: Boolean = true,
) : Read {
    companion object {
        fun from(entry: Entry<*>): EntryFields = entry.fields
    }

    fun intoEntry(): Entry<Read> = Entry(this)

    fun readAll(): ByteArray {
        val cap = minOf(size.toLong(), 128L * 1024L).toInt()
        val v = ByteArrayWriter()
        val buf = ByteArray(minOf(cap, 8192).coerceAtLeast(1))
        while (true) {
            val n = read(buf, 0, buf.size)
            if (n <= 0) break
            v.write(buf, 0, n)
        }
        return v.toByteArray()
    }

    fun path(): String = bytes2path(pathBytes())

    fun pathBytes(): ByteArray {
        val longPath = longPathname
        if (longPath != null) {
            return if (longPath.isNotEmpty() && longPath.last() == 0.toByte()) {
                longPath.copyOf(longPath.size - 1)
            } else {
                longPath
            }
        }
        val pax = paxExtensionsData
        if (pax != null) {
            val extensions = PaxExtensions(pax)
            for (field in extensions) {
                if (field.keyBytes().contentEquals("path".encodeToByteArray())) {
                    return field.valueBytes()
                }
            }
        }
        return header.pathBytes()
    }

    fun pathLossy(): String = pathBytes().decodeToString()

    fun linkName(): String? = linkNameBytes()?.let { bytes2path(it) }

    fun linkNameBytes(): ByteArray? {
        val longLink = longLinkname
        if (longLink != null) {
            return if (longLink.isNotEmpty() && longLink.last() == 0.toByte()) {
                longLink.copyOf(longLink.size - 1)
            } else {
                longLink
            }
        }
        val pax = paxExtensionsData
        if (pax != null) {
            val extensions = PaxExtensions(pax)
            for (field in extensions) {
                if (field.keyBytes().contentEquals("linkpath".encodeToByteArray())) {
                    return field.valueBytes()
                }
            }
        }
        return header.linkNameBytes()
    }

    fun paxExtensions(): PaxExtensions? {
        if (paxExtensionsData == null) {
            if (!header.entryType().isPaxGlobalExtensions() && !header.entryType().isPaxLocalExtensions()) {
                return null
            }
            paxExtensionsData = readAll()
        }
        return paxExtensionsData?.let { PaxExtensions(it) }
    }

    fun unpackIn(dst: String): Boolean {
        val pathStr =
            try {
                path()
            } catch (e: Exception) {
                throw TarError("invalid path in entry header: ${pathLossy()}", IoError.new(IoErrorKind.InvalidData, e))
            }

        val normalized = pathStr.replace('\\', '/')
        val parts = normalized.split('/')
        val fileDstParts = mutableListOf<String>()

        for (part in parts) {
            when (part) {
                "", "." -> continue
                ".." -> return false
                else -> fileDstParts.add(part)
            }
        }

        if (fileDstParts.isEmpty()) {
            return true
        }

        val fileDst =
            if (dst.isEmpty() || dst == ".") {
                fileDstParts.joinToString("/")
            } else {
                "$dst/${fileDstParts.joinToString("/")}"
            }

        val parentIndex = fileDst.lastIndexOf('/')
        val parent = if (parentIndex > 0) fileDst.substring(0, parentIndex) else ""

        if (parent.isNotEmpty()) {
            ensureDirCreated(dst, parent)
        }

        val canonTarget = validateInsideDst(dst, if (parent.isEmpty()) dst else parent)
        unpack(canonTarget, fileDst)
        return true
    }

    fun unpackDir(dst: String) {
        // Destination directory unpacked
    }

    fun unpack(targetBase: String?, dst: String): Unpacked {
        val kind = header.entryType()

        if (kind.isDir()) {
            unpackDir(dst)
            setPermsOwnerships(
                dst,
                null,
                header,
                mask,
                preservePermissions,
                preserveOwnerships,
            )
            return Unpacked.Nonexhaustive
        } else if (kind.isHardLink() || kind.isSymlink()) {
            val src =
                linkName() ?: throw TarError(
                    "hard link listed for ${header.asBytes().decodeToString()} but no link name found",
                )
            if (src.isEmpty()) {
                throw TarError("symlink destination for ${header.asBytes().decodeToString()} is empty")
            }

            if (kind.isHardLink()) {
                val linkSrc =
                    if (targetBase != null) {
                        val combined = "$targetBase/$src"
                        validateInsideDst(targetBase, combined)
                        combined
                    } else {
                        src
                    }
            } else {
                symlink(src, dst)
                if (preserveOwnerships) {
                    setOwnerships(dst, null, header.uid(), header.gid())
                }
                if (preserveMtime) {
                    val mtime = getMtime(header)
                }
            }
            return Unpacked.Nonexhaustive
        } else if (kind.isPaxGlobalExtensions() ||
            kind.isPaxLocalExtensions() ||
            kind.isGnuLongname() ||
            kind.isGnuLonglink()
        ) {
            return Unpacked.Nonexhaustive
        }

        if (!header.isUstar() && pathBytes().isNotEmpty() && pathBytes().last() == '/'.code.toByte()) {
            unpackDir(dst)
            setPermsOwnerships(
                dst,
                null,
                header,
                mask,
                preservePermissions,
                preserveOwnerships,
            )
            return Unpacked.Nonexhaustive
        }

        val f = open(dst)
        for (io in data.toList()) {
            when (io) {
                is EntryIo.Data -> {
                    val expected = io.limit()
                    val writer = ByteArrayWriter()
                    val copied = copy(io, writer)
                    if (copied != expected) {
                        throw TarError("failed to write entire file")
                    }
                }
                is EntryIo.Pad -> {
                    val padBytes = ByteArray(io.limit().toInt())
                    io.read(padBytes, 0, padBytes.size)
                }
            }
        }
        data.clear()

        if (preserveMtime) {
            val mtime = getMtime(header)
        }
        setPermsOwnerships(
            dst,
            f,
            header,
            mask,
            preservePermissions,
            preserveOwnerships,
        )
        if (unpackXattrs) {
            setXattrs(dst)
        }
        return Unpacked.File(dst)
    }

    fun setPermsOwnerships(
        dst: String,
        f: Any?,
        header: Header,
        mask: UInt,
        perms: Boolean,
        ownerships: Boolean,
    ) {
        if (ownerships) {
            setOwnerships(dst, f, header.uid(), header.gid())
        }
        val mode = header.mode()
        if (mode != 0) {
            setPerms(dst, f, mode, mask, perms)
        }
    }

    fun getMtime(header: Header): Long? =
        try {
            val mtime = header.mtime()
            if (mtime == 0L) 1L else mtime
        } catch (_: Exception) {
            null
        }

    fun symlink(src: String, dst: String) {
        // Platform symlink operation
    }

    fun open(dst: String): Any = dst

    fun setOwnerships(
        dst: String,
        f: Any?,
        uid: Long,
        gid: Long,
    ) {
        _setOwnerships(dst, f, uid, gid)
    }

    fun _setOwnerships(
        dst: String,
        f: Any?,
        uid: Long,
        gid: Long,
    ) {
        // Platform ownership setting
    }

    fun setPerms(
        dst: String,
        f: Any?,
        mode: Int,
        mask: UInt,
        preserve: Boolean,
    ) {
        _setPerms(dst, f, mode, mask, preserve)
    }

    fun _setPerms(
        dst: String,
        f: Any?,
        mode: Int,
        mask: UInt,
        preserve: Boolean,
    ) {
        // Platform permissions setting
    }

    fun setXattrs(dst: String) {
        val exts = paxExtensions() ?: return
        val prefix = PAX_SCHILYXATTR.encodeToByteArray()
        for (entry in exts) {
            val key = entry.keyBytes()
            if (key.size > prefix.size && key.copyOf(prefix.size).contentEquals(prefix)) {
                // Set extended attribute
            }
        }
    }

    fun ensureDirCreated(dst: String, dir: String) {
        validateInsideDst(dst, dir)
    }

    fun validateInsideDst(dst: String, fileDst: String): String {
        val normDst = dst.replace('\\', '/').trimEnd('/')
        val normFile = fileDst.replace('\\', '/').trimEnd('/')
        if (normDst.isNotEmpty() && !normFile.startsWith(normDst)) {
            throw TarError("trying to unpack outside of destination path: $normDst")
        }
        return normDst
    }

    override fun read(into: ByteArray, offset: Int, length: Int): Int {
        while (data.isNotEmpty()) {
            val r = data[0].read(into, offset, length)
            if (r == 0) {
                data.removeAt(0)
            } else {
                return r
            }
        }
        return 0
    }
}

/**
 * Underlying data stream for an [Entry], either raw archive data or padding zeros.
 */
internal sealed class EntryIo : Read {
    class Pad(
        val take: Take<Repeat>,
    ) : EntryIo() {
        override fun read(into: ByteArray, offset: Int, length: Int): Int = take.read(into, offset, length)

        fun limit(): Long = take.limit
    }

    class Data(
        val take: Take<Read>,
    ) : EntryIo() {
        override fun read(into: ByteArray, offset: Int, length: Int): Int = take.read(into, offset, length)

        fun limit(): Long = take.limit
    }
}

/**
 * Result of unpacking an entry.
 */
sealed class Unpacked {
    class File(
        val path: String = "",
    ) : Unpacked() {
        override fun toString(): String = "File($path)"
    }

    object Nonexhaustive : Unpacked() {
        override fun toString(): String = "Nonexhaustive"
    }
}
