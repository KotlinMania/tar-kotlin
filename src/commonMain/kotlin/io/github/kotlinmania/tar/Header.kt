// port-lint: source header.rs
package io.github.kotlinmania.tar

/**
 * A deterministic, arbitrary, non-zero timestamp that is used as `mtime`
 * of headers when [HeaderMode.Deterministic] is used.
 *
 * Corresponds to Jul 23, 2006.
 */
const val DETERMINISTIC_TIMESTAMP: Long = 1153704088L

const val BLOCK_SIZE: Long = 512L

const val GNU_SPARSE_HEADERS_COUNT: Int = 4

const val GNU_EXT_SPARSE_HEADERS_COUNT: Int = 21

private val USTAR_MAGIC = byteArrayOf('u'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(), 0)
private val USTAR_VERSION = byteArrayOf('0'.code.toByte(), '0'.code.toByte())
private val GNU_MAGIC = byteArrayOf('u'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(), ' '.code.toByte())
private val GNU_VERSION = byteArrayOf(' '.code.toByte(), 0)

/**
 * Declares the information that should be included when filling a Header
 * from filesystem metadata.
 */
enum class HeaderMode {
    /**
     * All supported metadata, including mod/access times and ownership will
     * be included.
     */
    Complete,

    /**
     * Only metadata that is directly relevant to the identity of a file will
     * be included. In particular, ownership and mod/access times are excluded.
     */
    Deterministic,
}

/**
 * A mutable slice view over a range of a ByteArray.
 */
class ByteSlice(
    val array: ByteArray,
    val offset: Int,
    val size: Int,
) : Iterable<Byte> {
    operator fun get(index: Int): Byte {
        require(index in 0 until size) { "Index $index out of bounds for slice size $size" }
        return array[offset + index]
    }

    operator fun set(index: Int, value: Byte) {
        require(index in 0 until size) { "Index $index out of bounds for slice size $size" }
        array[offset + index] = value
    }

    operator fun set(index: Int, value: Int) {
        set(index, value.toByte())
    }

    fun toByteArray(): ByteArray = array.copyOfRange(offset, offset + size)

    fun copyFrom(bytes: ByteArray) {
        val copyLen = minOf(size, bytes.size)
        bytes.copyInto(array, offset, 0, copyLen)
        if (copyLen < size) {
            array.fill(0, offset + copyLen, offset + size)
        }
    }

    override fun iterator(): ByteIterator =
        object : ByteIterator() {
            private var idx = 0

            override fun hasNext(): Boolean = idx < size

            override fun nextByte(): Byte = get(idx++)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is ByteSlice) {
            if (size != other.size) return false
            for (i in 0 until size) {
                if (this[i] != other[i]) return false
            }
            return true
        }
        if (other is ByteArray) {
            if (size != other.size) return false
            for (i in 0 until size) {
                if (this[i] != other[i]) return false
            }
            return true
        }
        return false
    }

    override fun hashCode(): Int {
        var result = 1
        for (i in 0 until size) {
            result = 31 * result + array[offset + i]
        }
        return result
    }

    override fun toString(): String = toByteArray().contentToString()

    companion object {
        fun wrap(bytes: ByteArray): ByteSlice = ByteSlice(bytes, 0, bytes.size)
    }
}

/**
 * Representation of the header of an entry in an archive.
 */
class Header(
    val bytes: ByteArray = ByteArray(BLOCK_SIZE.toInt()),
) {
    init {
        require(bytes.size == BLOCK_SIZE.toInt()) {
            "Header byte array must be exactly ${BLOCK_SIZE} bytes (was ${bytes.size})"
        }
    }

    /**
     * Creates a new blank GNU header.
     */
    companion object {
        fun newGnu(): Header {
            val header = Header()
            GNU_MAGIC.copyInto(header.bytes, 257)
            GNU_VERSION.copyInto(header.bytes, 263)
            header.setMtime(0)
            return header
        }

        /**
         * Creates a new blank UStar header.
         */
        fun newUstar(): Header {
            val header = Header()
            USTAR_MAGIC.copyInto(header.bytes, 257)
            USTAR_VERSION.copyInto(header.bytes, 263)
            header.setMtime(0)
            return header
        }

        /**
         * Creates a new blank old header.
         */
        fun newOld(): Header {
            val header = Header()
            header.setMtime(0)
            return header
        }

        /**
         * Treats the given byte slice as a header.
         */
        fun fromByteSlice(bytes: ByteArray): Header {
            require(bytes.size == BLOCK_SIZE.toInt()) {
                "Byte slice length must be ${BLOCK_SIZE} (was ${bytes.size})"
            }
            return Header(bytes.copyOf())
        }
    }

    fun isUstar(): Boolean {
        for (i in 0 until 6) {
            if (bytes[257 + i] != USTAR_MAGIC[i]) return false
        }
        for (i in 0 until 2) {
            if (bytes[263 + i] != USTAR_VERSION[i]) return false
        }
        return true
    }

    fun isGnu(): Boolean {
        for (i in 0 until 6) {
            if (bytes[257 + i] != GNU_MAGIC[i]) return false
        }
        for (i in 0 until 2) {
            if (bytes[263 + i] != GNU_VERSION[i]) return false
        }
        return true
    }

    /**
     * View this archive header as a raw "old" archive header.
     */
    fun asOld(): OldHeader = OldHeader(this)

    /** Same as `asOld`, but mutable view. */
    fun asOldMut(): OldHeader = OldHeader(this)

    /**
     * View this archive header as a raw UStar archive header.
     */
    fun asUstar(): UstarHeader? = if (isUstar()) UstarHeader(this) else null

    /** Same as `asUstar`, but mutable view. */
    fun asUstarMut(): UstarHeader? = if (isUstar()) UstarHeader(this) else null

    /**
     * View this archive header as a raw GNU archive header.
     */
    fun asGnu(): GnuHeader? = if (isGnu()) GnuHeader(this) else null

    /** Same as `asGnu`, but mutable view. */
    fun asGnuMut(): GnuHeader? = if (isGnu()) GnuHeader(this) else null

    /** Returns a view into this header as a byte array. */
    fun asBytes(): ByteArray = bytes

    /** Returns a view into this header as a byte array. */
    fun asMutBytes(): ByteArray = bytes

    /**
     * Returns the size of entry's data this header represents.
     */
    fun entrySize(): Long = numFieldWrapperFrom(bytes.copyOfRange(124, 136))

    /**
     * Returns the file size this header represents.
     */
    fun size(): Long =
        if (entryType().isGnuSparse()) {
            val gnu = asGnu() ?: throw IoError(IoErrorKind.Other, "sparse header was not a gnu header")
            gnu.realSize()
        } else {
            entrySize()
        }

    /** Encodes the `size` argument into the size field of this header. */
    fun setSize(size: Long) {
        val b = ByteArray(12)
        numFieldWrapperInto(b, size)
        b.copyInto(bytes, 124)
    }

    /** Returns the raw path name stored in this header. */
    fun path(): String = bytes2path(pathBytes())

    /** Returns the pathname stored in this header as a byte array. */
    fun pathBytes(): ByteArray {
        val ustar = asUstar()
        return if (ustar != null) {
            ustar.pathBytes()
        } else {
            truncate(bytes.copyOfRange(0, 100))
        }
    }

    fun pathLossy(): String = pathBytes().decodeToString()

    /** Sets the path name for this header. */
    fun setPath(path: String) {
        setPathInner(path, false)
    }

    fun setTruncatedPathForGnuHeader(path: String) {
        setPathInner(path, true)
    }

    private fun setPathInner(path: String, isTruncatedGnuLongPath: Boolean) {
        val ustar = asUstarMut()
        if (ustar != null) {
            ustar.setPath(path)
            return
        }
        val targetSlot = ByteSlice(bytes, 0, 100)
        if (isTruncatedGnuLongPath) {
            copyPathIntoGnuLong(targetSlot, path, false)
        } else {
            copyPathInto(targetSlot, path, false)
        }
    }

    /** Returns the link name stored in this header, if any is found. */
    fun linkName(): String? {
        val b = linkNameBytes() ?: return null
        return bytes2path(b)
    }

    /** Returns the link name stored in this header as a byte array, if any. */
    fun linkNameBytes(): ByteArray? =
        if (bytes[157] != 0.toByte()) {
            truncate(bytes.copyOfRange(157, 257))
        } else {
            null
        }

    /** Sets the link name for this header. */
    fun setLinkName(path: String) {
        copyPathInto(ByteSlice(bytes, 157, 100), path, true)
    }

    /** Sets the link name for this header without any transformation. */
    fun setLinkNameLiteral(bytesInput: ByteArray) {
        copyInto(ByteSlice(bytes, 157, 100), bytesInput)
    }

    /** Returns the mode bits for this file. */
    fun mode(): Int = octalFrom(bytes.copyOfRange(100, 108)).toInt()

    /** Encodes the `mode` provided into this header. */
    fun setMode(mode: Int) {
        val b = ByteArray(8)
        octalInto(b, mode.toLong())
        b.copyInto(bytes, 100)
    }

    /** Returns the value of the owner's user ID field. */
    fun uid(): Long = numFieldWrapperFrom(bytes.copyOfRange(108, 116))

    /** Encodes the `uid` provided into this header. */
    fun setUid(uid: Long) {
        val b = ByteArray(8)
        numFieldWrapperInto(b, uid)
        b.copyInto(bytes, 108)
    }

    /** Returns the value of the group's user ID field. */
    fun gid(): Long = numFieldWrapperFrom(bytes.copyOfRange(116, 124))

    /** Encodes the `gid` provided into this header. */
    fun setGid(gid: Long) {
        val b = ByteArray(8)
        numFieldWrapperInto(b, gid)
        b.copyInto(bytes, 116)
    }

    /** Returns the last modification time in Unix time format. */
    fun mtime(): Long = numFieldWrapperFrom(bytes.copyOfRange(136, 148))

    /** Encodes the `mtime` provided into this header. */
    fun setMtime(mtime: Long) {
        val b = ByteArray(12)
        numFieldWrapperInto(b, mtime)
        b.copyInto(bytes, 136)
    }

    /** Return the user name of the owner of this file. */
    fun username(): String? {
        val b = usernameBytes() ?: return null
        return b.decodeToString()
    }

    /** Returns the user name of the owner of this file, if present. */
    fun usernameBytes(): ByteArray? {
        val ustar = asUstar()
        if (ustar != null) return ustar.usernameBytes()
        val gnu = asGnu()
        if (gnu != null) return gnu.usernameBytes()
        return null
    }

    /** Sets the username inside this header. */
    fun setUsername(name: String) {
        val ustar = asUstarMut()
        if (ustar != null) {
            ustar.setUsername(name)
            return
        }
        val gnu = asGnuMut()
        if (gnu != null) {
            gnu.setUsername(name)
            return
        }
        throw IoError(IoErrorKind.Other, "not a ustar or gnu archive, cannot set username")
    }

    /** Return the group name of the owner of this file. */
    fun groupname(): String? {
        val b = groupnameBytes() ?: return null
        return b.decodeToString()
    }

    /** Returns the group name of the owner of this file, if present. */
    fun groupnameBytes(): ByteArray? {
        val ustar = asUstar()
        if (ustar != null) return ustar.groupnameBytes()
        val gnu = asGnu()
        if (gnu != null) return gnu.groupnameBytes()
        return null
    }

    /** Sets the group name inside this header. */
    fun setGroupname(name: String) {
        val ustar = asUstarMut()
        if (ustar != null) {
            ustar.setGroupname(name)
            return
        }
        val gnu = asGnuMut()
        if (gnu != null) {
            gnu.setGroupname(name)
            return
        }
        throw IoError(IoErrorKind.Other, "not a ustar or gnu archive, cannot set groupname")
    }

    /** Returns the device major number, if present. */
    fun deviceMajor(): Int? {
        val ustar = asUstar()
        if (ustar != null) return ustar.deviceMajor()
        val gnu = asGnu()
        if (gnu != null) return gnu.deviceMajor()
        return null
    }

    /** Encodes the value `major` into the device major field of this header. */
    fun setDeviceMajor(major: Int) {
        val ustar = asUstarMut()
        if (ustar != null) {
            ustar.setDeviceMajor(major)
            return
        }
        val gnu = asGnuMut()
        if (gnu != null) {
            gnu.setDeviceMajor(major)
            return
        }
        throw IoError(IoErrorKind.Other, "not a ustar or gnu archive, cannot set device major")
    }

    /** Returns the device minor number, if present. */
    fun deviceMinor(): Int? {
        val ustar = asUstar()
        if (ustar != null) return ustar.deviceMinor()
        val gnu = asGnu()
        if (gnu != null) return gnu.deviceMinor()
        return null
    }

    /** Encodes the value `minor` into the device minor field of this header. */
    fun setDeviceMinor(minor: Int) {
        val ustar = asUstarMut()
        if (ustar != null) {
            ustar.setDeviceMinor(minor)
            return
        }
        val gnu = asGnuMut()
        if (gnu != null) {
            gnu.setDeviceMinor(minor)
            return
        }
        throw IoError(IoErrorKind.Other, "not a ustar or gnu archive, cannot set device minor")
    }

    /** Returns the type of file described by this header. */
    fun entryType(): EntryType = EntryType.new(bytes[156].toUByte())

    /** Sets the type of file that will be described by this header. */
    fun setEntryType(ty: EntryType) {
        bytes[156] = ty.asByte().toByte()
    }

    /** Returns the checksum field of this header. */
    fun cksum(): Int = octalFrom(bytes.copyOfRange(148, 156)).toInt()

    /** Sets the checksum field of this header based on the current fields. */
    fun setCksum() {
        val calculated = calculateCksum()
        val b = ByteArray(8)
        octalInto(b, calculated)
        b.copyInto(bytes, 148)
    }

    fun calculateCksum(): Long {
        var sum = 0L
        for (i in 0 until 148) {
            sum += (bytes[i].toInt() and 0xFF)
        }
        // 8 space characters for the checksum field
        sum += 8 * (' '.code.toLong())
        for (i in 156 until BLOCK_SIZE.toInt()) {
            sum += (bytes[i].toInt() and 0xFF)
        }
        return sum
    }

    fun clone(): Header = Header(bytes.copyOf())
}

/**
 * Representation of the header of an entry in an archive (Old format).
 */
class OldHeader(
    val header: Header,
) {
    val bytes: ByteArray get() = header.bytes

    val name: ByteSlice get() = ByteSlice(bytes, 0, 100)
    val mode: ByteSlice get() = ByteSlice(bytes, 100, 8)
    val uid: ByteSlice get() = ByteSlice(bytes, 108, 8)
    val gid: ByteSlice get() = ByteSlice(bytes, 116, 8)
    val size: ByteSlice get() = ByteSlice(bytes, 124, 12)
    val mtime: ByteSlice get() = ByteSlice(bytes, 136, 12)
    val cksum: ByteSlice get() = ByteSlice(bytes, 148, 8)
    val linkflag: ByteSlice get() = ByteSlice(bytes, 156, 1)
    val linkname: ByteSlice get() = ByteSlice(bytes, 157, 100)
    val pad: ByteSlice get() = ByteSlice(bytes, 257, 255)

    fun asHeader(): Header = header

    fun asHeaderMut(): Header = header
}

/**
 * Representation of the header of an entry in an archive (UStar format).
 */
class UstarHeader(
    val header: Header,
) {
    val bytes: ByteArray get() = header.bytes

    val name: ByteSlice get() = ByteSlice(bytes, 0, 100)
    val mode: ByteSlice get() = ByteSlice(bytes, 100, 8)
    val uid: ByteSlice get() = ByteSlice(bytes, 108, 8)
    val gid: ByteSlice get() = ByteSlice(bytes, 116, 8)
    val size: ByteSlice get() = ByteSlice(bytes, 124, 12)
    val mtime: ByteSlice get() = ByteSlice(bytes, 136, 12)
    val cksum: ByteSlice get() = ByteSlice(bytes, 148, 8)
    val typeflag: ByteSlice get() = ByteSlice(bytes, 156, 1)
    val linkname: ByteSlice get() = ByteSlice(bytes, 157, 100)

    val magic: ByteSlice get() = ByteSlice(bytes, 257, 6)
    val version: ByteSlice get() = ByteSlice(bytes, 263, 2)
    val uname: ByteSlice get() = ByteSlice(bytes, 265, 32)
    val gname: ByteSlice get() = ByteSlice(bytes, 297, 32)
    val devMajor: ByteSlice get() = ByteSlice(bytes, 329, 8)
    val devMinor: ByteSlice get() = ByteSlice(bytes, 337, 8)
    val prefix: ByteSlice get() = ByteSlice(bytes, 345, 155)
    val pad: ByteSlice get() = ByteSlice(bytes, 500, 12)

    fun asHeader(): Header = header

    fun asHeaderMut(): Header = header

    fun pathBytes(): ByteArray {
        val prefixTrunc = truncate(prefix.toByteArray())
        val nameTrunc = truncate(name.toByteArray())
        return if (prefixTrunc.isEmpty()) {
            nameTrunc
        } else {
            val out = ByteArray(prefixTrunc.size + 1 + nameTrunc.size)
            prefixTrunc.copyInto(out, 0)
            out[prefixTrunc.size] = '/'.code.toByte()
            nameTrunc.copyInto(out, prefixTrunc.size + 1)
            out
        }
    }

    fun setPath(path: String) {
        val normalized = normalizePath(path, false)
        val pathBytes = normalized.encodeToByteArray()
        if (pathBytes.size <= 100) {
            copyPathInto(name, normalized, false)
            prefix.copyFrom(ByteArray(155))
        } else {
            val hasTrailingSlash = normalized.endsWith('/')
            val trimmed = if (hasTrailingSlash) normalized.dropLast(1) else normalized
            val components = trimmed.split('/')
            if (components.size < 2) {
                if (pathBytes.size > 255) {
                    throw IoError(IoErrorKind.InvalidInput, "provided value is too long")
                }
                throw IoError(IoErrorKind.Other, "path cannot be split to be inserted into archive: $path")
            }

            var chosenSplit = -1
            for (splitIdx in components.size - 1 downTo 1) {
                val prefixCandidate = components.subList(0, splitIdx).joinToString("/")
                val prefixLen = prefixCandidate.encodeToByteArray().size
                if (prefixLen <= 155) {
                    var nameCandidate = components.subList(splitIdx, components.size).joinToString("/")
                    if (hasTrailingSlash) {
                        nameCandidate += "/"
                    }
                    val nameLen = nameCandidate.encodeToByteArray().size
                    if (nameLen <= 100) {
                        chosenSplit = splitIdx
                        break
                    }
                }
            }

            if (chosenSplit < 0) {
                if (pathBytes.size > 255) {
                    throw IoError(IoErrorKind.InvalidInput, "provided value is too long")
                }
                throw IoError(IoErrorKind.Other, "path cannot be split to be inserted into archive: $path")
            }

            val prefixPart = components.subList(0, chosenSplit).joinToString("/")
            var namePart = components.subList(chosenSplit, components.size).joinToString("/")
            if (hasTrailingSlash) {
                namePart += "/"
            }
            copyPathInto(prefix, prefixPart, false)
            copyPathInto(name, namePart, false)
        }
    }

    fun usernameBytes(): ByteArray = truncate(uname.toByteArray())

    fun setUsername(nameStr: String) {
        copyInto(uname, nameStr.encodeToByteArray())
    }

    fun groupnameBytes(): ByteArray = truncate(gname.toByteArray())

    fun setGroupname(nameStr: String) {
        copyInto(gname, nameStr.encodeToByteArray())
    }

    fun deviceMajor(): Int = octalFrom(devMajor.toByteArray()).toInt()

    fun setDeviceMajor(major: Int) {
        val b = ByteArray(8)
        octalInto(b, major.toLong())
        devMajor.copyFrom(b)
    }

    fun deviceMinor(): Int = octalFrom(devMinor.toByteArray()).toInt()

    fun setDeviceMinor(minor: Int) {
        val b = ByteArray(8)
        octalInto(b, minor.toLong())
        devMinor.copyFrom(b)
    }
}

/**
 * Representation of the header of an entry in an archive (GNU format).
 */
class GnuHeader(
    val header: Header,
) {
    val bytes: ByteArray get() = header.bytes

    val name: ByteSlice get() = ByteSlice(bytes, 0, 100)
    val mode: ByteSlice get() = ByteSlice(bytes, 100, 8)
    var uid: ByteSlice
        get() = ByteSlice(bytes, 108, 8)
        set(v) {
            ByteSlice(bytes, 108, 8).copyFrom(v.toByteArray())
        }
    var gid: ByteSlice
        get() = ByteSlice(bytes, 116, 8)
        set(v) {
            ByteSlice(bytes, 116, 8).copyFrom(v.toByteArray())
        }
    var size: ByteSlice
        get() = ByteSlice(bytes, 124, 12)
        set(v) {
            ByteSlice(bytes, 124, 12).copyFrom(v.toByteArray())
        }
    var mtime: ByteSlice
        get() = ByteSlice(bytes, 136, 12)
        set(v) {
            ByteSlice(bytes, 136, 12).copyFrom(v.toByteArray())
        }
    val cksum: ByteSlice get() = ByteSlice(bytes, 148, 8)
    val typeflag: ByteSlice get() = ByteSlice(bytes, 156, 1)
    val linkname: ByteSlice get() = ByteSlice(bytes, 157, 100)

    val magic: ByteSlice get() = ByteSlice(bytes, 257, 6)
    val version: ByteSlice get() = ByteSlice(bytes, 263, 2)
    val uname: ByteSlice get() = ByteSlice(bytes, 265, 32)
    val gname: ByteSlice get() = ByteSlice(bytes, 297, 32)
    val devMajor: ByteSlice get() = ByteSlice(bytes, 329, 8)
    val devMinor: ByteSlice get() = ByteSlice(bytes, 337, 8)

    val atimeBytes: ByteSlice get() = ByteSlice(bytes, 345, 12)
    val ctimeBytes: ByteSlice get() = ByteSlice(bytes, 357, 12)
    val offsetBytes: ByteSlice get() = ByteSlice(bytes, 369, 12)
    val longnames: ByteSlice get() = ByteSlice(bytes, 381, 4)
    val unused: ByteSlice get() = ByteSlice(bytes, 385, 1)

    val sparse: List<GnuSparseHeader> =
        List(GNU_SPARSE_HEADERS_COUNT) { i ->
            GnuSparseHeader(bytes, 386 + i * 24)
        }

    val isextendedBytes: ByteSlice get() = ByteSlice(bytes, 482, 1)
    var realsizeBytes: ByteSlice
        get() = ByteSlice(bytes, 483, 12)
        set(v) {
            ByteSlice(bytes, 483, 12).copyFrom(v.toByteArray())
        }
    val pad: ByteSlice get() = ByteSlice(bytes, 495, 17)

    fun asHeader(): Header = header

    fun asHeaderMut(): Header = header

    fun usernameBytes(): ByteArray = truncate(uname.toByteArray())

    fun setUsername(nameStr: String) {
        copyInto(uname, nameStr.encodeToByteArray())
    }

    fun groupnameBytes(): ByteArray = truncate(gname.toByteArray())

    fun setGroupname(nameStr: String) {
        copyInto(gname, nameStr.encodeToByteArray())
    }

    fun deviceMajor(): Int = octalFrom(devMajor.toByteArray()).toInt()

    fun setDeviceMajor(major: Int) {
        val b = ByteArray(8)
        octalInto(b, major.toLong())
        devMajor.copyFrom(b)
    }

    fun deviceMinor(): Int = octalFrom(devMinor.toByteArray()).toInt()

    fun setDeviceMinor(minor: Int) {
        val b = ByteArray(8)
        octalInto(b, minor.toLong())
        devMinor.copyFrom(b)
    }

    fun atime(): Long = numFieldWrapperFrom(atimeBytes.toByteArray())

    fun setAtime(atimeVal: Long) {
        val b = ByteArray(12)
        numFieldWrapperInto(b, atimeVal)
        atimeBytes.copyFrom(b)
    }

    fun ctime(): Long = numFieldWrapperFrom(ctimeBytes.toByteArray())

    fun setCtime(ctimeVal: Long) {
        val b = ByteArray(12)
        numFieldWrapperInto(b, ctimeVal)
        ctimeBytes.copyFrom(b)
    }

    fun realSize(): Long = numFieldWrapperFrom(realsizeBytes.toByteArray())

    fun setRealSize(realSizeVal: Long) {
        val b = ByteArray(12)
        numFieldWrapperInto(b, realSizeVal)
        realsizeBytes.copyFrom(b)
    }

    fun isExtended(): Boolean = bytes[482] == 1.toByte()

    fun setIsExtended(isExtendedVal: Boolean) {
        bytes[482] = if (isExtendedVal) 1 else 0
    }
}

/**
 * Description of the header of a sparse entry.
 */
class GnuSparseHeader(
    val array: ByteArray,
    val offsetIndex: Int,
) {
    var offsetBytes: ByteSlice
        get() = ByteSlice(array, offsetIndex, 12)
        set(v) {
            ByteSlice(array, offsetIndex, 12).copyFrom(v.toByteArray())
        }

    var numbytes: ByteSlice
        get() = ByteSlice(array, offsetIndex + 12, 12)
        set(v) {
            ByteSlice(array, offsetIndex + 12, 12).copyFrom(v.toByteArray())
        }

    fun isEmpty(): Boolean = offsetBytes[0] == 0.toByte() || numbytes[0] == 0.toByte()

    fun offset(): Long = numFieldWrapperFrom(offsetBytes.toByteArray())

    fun setOffset(offsetVal: Long) {
        val b = ByteArray(12)
        numFieldWrapperInto(b, offsetVal)
        offsetBytes.copyFrom(b)
    }

    fun length(): Long = numFieldWrapperFrom(numbytes.toByteArray())

    fun setLength(lengthVal: Long) {
        val b = ByteArray(12)
        numFieldWrapperInto(b, lengthVal)
        numbytes.copyFrom(b)
    }
}

/**
 * Representation of the entry found to represent extended GNU sparse files.
 */
class GnuExtSparseHeader(
    val bytes: ByteArray = ByteArray(BLOCK_SIZE.toInt()),
) {
    init {
        require(bytes.size == BLOCK_SIZE.toInt()) {
            "GnuExtSparseHeader must be exactly ${BLOCK_SIZE} bytes"
        }
    }

    val sparse: List<GnuSparseHeader> =
        List(GNU_EXT_SPARSE_HEADERS_COUNT) { i ->
            GnuSparseHeader(bytes, i * 24)
        }

    fun asBytes(): ByteArray = bytes

    fun asMutBytes(): ByteArray = bytes

    fun isExtended(): Boolean = bytes[504] == 1.toByte()

    fun setIsExtended(isExtendedVal: Boolean) {
        bytes[504] = if (isExtendedVal) 1 else 0
    }

    companion object {
        fun new(): GnuExtSparseHeader = GnuExtSparseHeader()
    }
}

fun octalFrom(slice: ByteArray): Long {
    val trun = truncate(slice)
    if (trun.isEmpty()) return 0L
    val numStr =
        try {
            trun.decodeToString()
        } catch (_: Exception) {
            throw IoError(IoErrorKind.InvalidData, "numeric field did not have utf-8 text")
        }
    val trimmed = numStr.trim()
    if (trimmed.isEmpty()) return 0L
    return try {
        trimmed.toLong(8)
    } catch (_: Exception) {
        throw IoError(IoErrorKind.InvalidData, "numeric field was not a number: $numStr")
    }
}

fun octalInto(dst: ByteArray, valArg: Long) {
    val o = valArg.toString(8)
    val len = dst.size
    dst[len - 1] = 0
    var oIdx = o.length - 1
    for (i in len - 2 downTo 0) {
        if (oIdx >= 0) {
            dst[i] = o[oIdx].code.toByte()
            oIdx--
        } else {
            dst[i] = '0'.code.toByte()
        }
    }
}

fun numFieldWrapperInto(dst: ByteArray, src: Long) {
    if (src >= 8589934592L || (src >= 2097152L && dst.size == 8)) {
        numericExtendedInto(dst, src)
    } else {
        octalInto(dst, src)
    }
}

fun numFieldWrapperFrom(src: ByteArray): Long =
    if ((src[0].toInt() and 0x80) != 0) {
        numericExtendedFrom(src)
    } else {
        octalFrom(src)
    }

fun numericExtendedInto(dst: ByteArray, src: Long) {
    val len = dst.size
    val extra = len - 8
    for (i in 0 until extra) {
        dst[i] = 0
    }
    for (i in 0 until 8) {
        val shift = (7 - i) * 8
        dst[extra + i] = ((src ushr shift) and 0xFFL).toByte()
    }
    dst[0] = (dst[0].toInt() or 0x80).toByte()
}

fun numericExtendedFrom(src: ByteArray): Long {
    var dst: Long = 0L
    val bToSkip =
        if (src.size == 8) {
            dst = ((src[0].toInt() and 0xFF) xor 0x80).toLong()
            1
        } else {
            src.size - 8
        }
    for (i in bToSkip until src.size) {
        dst = (dst shl 8) or ((src[i].toInt() and 0xFF).toLong())
    }
    return dst
}

fun truncate(slice: ByteArray): ByteArray {
    var nulIdx = -1
    for (i in slice.indices) {
        if (slice[i] == 0.toByte()) {
            nulIdx = i
            break
        }
    }
    return if (nulIdx >= 0) slice.copyOfRange(0, nulIdx) else slice
}

fun copyInto(slot: ByteSlice, bytes: ByteArray) {
    if (bytes.size > slot.size) {
        throw IoError(IoErrorKind.InvalidInput, "provided value is too long")
    }
    if (bytes.contains(0.toByte())) {
        throw IoError(IoErrorKind.InvalidInput, "provided value contains a nul byte")
    }
    slot.copyFrom(bytes)
}

fun copyInto(slot: ByteArray, bytes: ByteArray) {
    copyInto(ByteSlice.wrap(slot), bytes)
}

fun normalizePath(path: String, isLinkName: Boolean): String {
    if (path.contains('\u0000')) {
        throw IoError(IoErrorKind.InvalidInput, "provided value contains a nul byte")
    }
    val hasTrailingSlash = path.endsWith('/') || path.endsWith('\\')
    val parts = path.split('/', '\\')
    val normalizedParts = mutableListOf<String>()

    for (part in parts) {
        if (part.isEmpty() || part == ".") {
            continue
        }
        if (part == "..") {
            if (!isLinkName) {
                throw IoError(IoErrorKind.InvalidInput, "paths in archives must not have `..`")
            }
        }
        normalizedParts.add(part)
    }

    if (normalizedParts.isEmpty()) {
        if (path == "./" || path == ".") {
            return if (hasTrailingSlash) "./" else "."
        }
        if (!isLinkName) {
            throw IoError(IoErrorKind.InvalidInput, "paths in archives must have at least one component")
        }
    }

    if (!isLinkName) {
        if (path.startsWith('/') || path.startsWith('\\') || (path.length >= 2 && path[1] == ':')) {
            throw IoError(IoErrorKind.InvalidInput, "paths in archives must be relative")
        }
    }

    var result = normalizedParts.joinToString("/")
    if (isLinkName && (path.startsWith('/') || path.startsWith('\\'))) {
        result = "/$result"
    }
    if (hasTrailingSlash && !result.endsWith('/')) {
        result += "/"
    }
    return result
}

fun copyPathInto(slot: ByteSlice, path: String, isLinkName: Boolean) {
    val normalized = normalizePath(path, isLinkName)
    val bytes = normalized.encodeToByteArray()
    if (bytes.size > slot.size) {
        throw IoError(IoErrorKind.InvalidInput, "provided value is too long")
    }
    slot.copyFrom(bytes)
}

fun copyPathIntoGnuLong(slot: ByteSlice, path: String, isLinkName: Boolean) {
    // Truncated version for GNU long paths
    val normalized = normalizePath(path, isLinkName)
    val bytes = normalized.encodeToByteArray()
    val copyLen = minOf(slot.size, bytes.size)
    val slice = bytes.copyOfRange(0, copyLen)
    slot.copyFrom(slice)
}

fun bytes2path(bytes: ByteArray): String = bytes.decodeToString()
