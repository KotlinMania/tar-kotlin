// port-lint: source pax.rs
package io.github.kotlinmania.tar

// Keywords for PAX extended header records.
const val PAX_NONE: String = "" // Indicates that no PAX key is suitable
const val PAX_PATH: String = "path"
const val PAX_LINKPATH: String = "linkpath"
const val PAX_SIZE: String = "size"
const val PAX_UID: String = "uid"
const val PAX_GID: String = "gid"
const val PAX_UNAME: String = "uname"
const val PAX_GNAME: String = "gname"
const val PAX_MTIME: String = "mtime"
const val PAX_ATIME: String = "atime"
const val PAX_CTIME: String = "ctime" // Removed from later revision of PAX spec, but was valid
const val PAX_CHARSET: String = "charset" // Currently unused
const val PAX_COMMENT: String = "comment" // Currently unused

const val PAX_SCHILYXATTR: String = "SCHILY.xattr."

// Keywords for GNU sparse files in a PAX extended header.
const val PAX_GNUSPARSE: String = "GNU.sparse."
const val PAX_GNUSPARSENUMBLOCKS: String = "GNU.sparse.numblocks"
const val PAX_GNUSPARSEOFFSET: String = "GNU.sparse.offset"
const val PAX_GNUSPARSENUMBYTES: String = "GNU.sparse.numbytes"
const val PAX_GNUSPARSEMAP: String = "GNU.sparse.map"
const val PAX_GNUSPARSENAME: String = "GNU.sparse.name"
const val PAX_GNUSPARSEMAJOR: String = "GNU.sparse.major"
const val PAX_GNUSPARSEMINOR: String = "GNU.sparse.minor"
const val PAX_GNUSPARSESIZE: String = "GNU.sparse.size"
const val PAX_GNUSPARSEREALSIZE: String = "GNU.sparse.realsize"

/**
 * A key/value pair corresponding to a pax extension.
 */
class PaxExtension(
    private val key: ByteArray,
    private val value: ByteArray,
) {
    /**
     * Returns the key for this key/value pair parsed as a string.
     */
    fun key(): String = key.decodeToString()

    /**
     * Returns the underlying raw bytes for the key of this key/value pair.
     */
    fun keyBytes(): ByteArray = key

    /**
     * Returns the value for this key/value pair parsed as a string.
     */
    fun value(): String = value.decodeToString()

    /**
     * Returns the underlying raw bytes for this value of this key/value pair.
     */
    fun valueBytes(): ByteArray = value
}

/**
 * An iterator over the pax extensions in an archive entry.
 *
 * This iterator yields structures which can themselves be parsed into
 * key/value pairs.
 */
class PaxExtensions(private val data: ByteArray) : Iterable<PaxExtension> {
    override fun iterator(): Iterator<PaxExtension> = PaxExtensionsIterator(data)

    companion object {
        /**
         * Create new pax extensions iterator from the given entry data.
         */
        fun new(data: ByteArray): PaxExtensions = PaxExtensions(data)
    }
}

private class PaxExtensionsIterator(private val data: ByteArray) : Iterator<PaxExtension> {
    private var offset: Int = 0
    private var nextItem: PaxExtension? = null
    private var advanceChecked: Boolean = false

    override fun hasNext(): Boolean {
        if (!advanceChecked) {
            advance()
            advanceChecked = true
        }
        return nextItem != null
    }

    override fun next(): PaxExtension {
        if (!hasNext()) {
            throw NoSuchElementException()
        }
        advanceChecked = false
        val item = nextItem!!
        nextItem = null
        return item
    }

    private fun advance() {
        if (offset >= data.size) {
            nextItem = null
            return
        }

        // Find newline delimiter
        var nextNewline = -1
        for (i in offset until data.size) {
            if (data[i] == '\n'.code.toByte()) {
                nextNewline = i
                break
            }
        }

        val lineEnd = if (nextNewline >= 0) nextNewline else data.size
        if (offset == lineEnd) {
            nextItem = null
            return
        }

        val lineLen = lineEnd - offset
        val hasNewline = nextNewline >= 0

        // Find space separating length from key=value
        var spacePos = -1
        for (i in offset until lineEnd) {
            if (data[i] == ' '.code.toByte()) {
                spacePos = i
                break
            }
        }

        if (spacePos < 0) {
            offset = if (hasNewline) nextNewline + 1 else data.size
            throw IoError(IoErrorKind.InvalidData, "malformed pax extension")
        }

        val lenStr = data.decodeToString(offset, spacePos)
        val reportedLen = lenStr.toIntOrNull()
        if (reportedLen == null) {
            offset = if (hasNewline) nextNewline + 1 else data.size
            throw IoError(IoErrorKind.InvalidData, "malformed pax extension")
        }

        // In pax format, reported length includes the trailing newline
        val actualRecordLen = lineLen + if (hasNewline) 1 else 0
        if (reportedLen != actualRecordLen) {
            offset = if (hasNewline) nextNewline + 1 else data.size
            throw IoError(IoErrorKind.InvalidData, "malformed pax extension")
        }

        val kvStart = spacePos + 1
        var equalsPos = -1
        for (i in kvStart until lineEnd) {
            if (data[i] == '='.code.toByte()) {
                equalsPos = i
                break
            }
        }

        if (equalsPos < 0) {
            offset = if (hasNewline) nextNewline + 1 else data.size
            throw IoError(IoErrorKind.InvalidData, "malformed pax extension")
        }

        val key = data.copyOfRange(kvStart, equalsPos)
        val value = data.copyOfRange(equalsPos + 1, lineEnd)

        offset = if (hasNewline) nextNewline + 1 else data.size
        nextItem = PaxExtension(key, value)
    }
}

/**
 * Searches the pax extensions in [data] for [key] and parses its value as an unsigned 64-bit integer.
 */
fun paxExtensionsValue(data: ByteArray, key: String): ULong? {
    for (extension in PaxExtensions.new(data)) {
        if (extension.key() != key) {
            continue
        }
        val value = extension.value()
        return value.toULongOrNull()
    }
    return null
}

/**
 * Formats key/value headers as a PAX extended header data block.
 */
fun formatPaxExtensions(headers: Iterable<Pair<String, ByteArray>>): ByteArray {
    var totalSize = 0
    val formattedRecords = mutableListOf<ByteArray>()

    for ((key, value) in headers) {
        val keyBytes = key.encodeToByteArray()
        var lenLen = 1
        var maxLen = 10
        val restLen = 3 + keyBytes.size + value.size
        while (restLen + lenLen >= maxLen) {
            lenLen++
            maxLen *= 10
        }
        val recordLen = restLen + lenLen
        val lenPrefix = "$recordLen $key=".encodeToByteArray()
        val record = ByteArray(lenPrefix.size + value.size + 1)
        lenPrefix.copyInto(record, 0)
        value.copyInto(record, lenPrefix.size)
        record[record.size - 1] = '\n'.code.toByte()

        formattedRecords.add(record)
        totalSize += record.size
    }

    val result = ByteArray(totalSize)
    var pos = 0
    for (record in formattedRecords) {
        record.copyInto(result, pos)
        pos += record.size
    }
    return result
}

/** Returns true if the given byte is a newline. */
fun isNewline(b: Byte): Boolean = b == '\n'.code.toByte()

/** Formats and appends PAX extensions into a byte array. */
fun appendPaxExtensions(headers: Iterable<Pair<String, ByteArray>>): ByteArray = formatPaxExtensions(headers)

