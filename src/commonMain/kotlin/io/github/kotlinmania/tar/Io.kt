package io.github.kotlinmania.tar

/**
 * Platform-neutral representation of I/O error and error kinds.
 */

/**
 * Categories of I/O failure mirrored from `std::io::ErrorKind`. Additional
 * variants are added as the port surfaces them; the upstream type is marked
 * `non_exhaustive` so this enum should not be matched exhaustively.
 */
enum class IoErrorKind {
    NotFound,
    PermissionDenied,
    InvalidData,
    InvalidInput,
    UnexpectedEof,
    Unsupported,
    Other,
}

/**
 * A platform-neutral I/O error carrying an [IoErrorKind] tag. Stands in for
 * `std::io::Error` so callers can branch on [kind] the way upstream code does.
 */
open class IoError(
    val kind: IoErrorKind,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    companion object {
        /** Mirrors `std::io::Error::new(kind, payload)` for string payloads. */
        fun new(kind: IoErrorKind, message: String): IoError = IoError(kind, message)

        /** Mirrors `std::io::Error::new(kind, payload)` for [Throwable] payloads. */
        fun new(kind: IoErrorKind, cause: Throwable): IoError =
            IoError(kind, cause.message ?: cause::class.simpleName ?: "io error", cause)
    }
}

/**
 * Trait for reading bytes from a source, mirroring `std::io::Read`.
 */
interface Read {
    fun read(into: ByteArray, offset: Int = 0, length: Int = into.size - offset): Int
}

/**
 * Reads the exact number of bytes required to fill [into] from [offset] to [offset] + [length].
 */
fun Read.readExact(into: ByteArray, offset: Int = 0, length: Int = into.size - offset) {
    var total = 0
    while (total < length) {
        val n = read(into, offset + total, length - total)
        if (n <= 0) {
            throw IoError(IoErrorKind.UnexpectedEof, "failed to fill whole buffer")
        }
        total += n
    }
}

/**
 * Reads all bytes from this reader until EOF.
 */
fun Read.readToEnd(): ByteArray {
    val temp = ByteArray(8192)
    val chunks = mutableListOf<ByteArray>()
    var totalSize = 0
    while (true) {
        val n = read(temp, 0, temp.size)
        if (n <= 0) break
        chunks.add(temp.copyOf(n))
        totalSize += n
    }
    val out = ByteArray(totalSize)
    var pos = 0
    for (chunk in chunks) {
        chunk.copyInto(out, pos)
        pos += chunk.size
    }
    return out
}

/**
 * Trait for writing bytes to a sink, mirroring `std::io::Write`.
 */
interface Write {
    fun write(buf: ByteArray, offset: Int = 0, length: Int = buf.size - offset)

    fun flush() {}
}

/**
 * In-memory byte array writer, mirroring `Vec<u8>` as `std::io::Write`.
 */
class ByteArrayWriter : Write {
    private var buffer = ByteArray(64)
    var size: Int = 0
        private set

    override fun write(buf: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        ensureCapacity(size + length)
        buf.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    override fun flush() {}

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
 * Copies all data from [reader] to [writer].
 */
fun copy(reader: Read, writer: Write, buffer: ByteArray = ByteArray(8192)): Long {
    var total = 0L
    while (true) {
        val n = reader.read(buffer, 0, buffer.size)
        if (n <= 0) break
        writer.write(buffer, 0, n)
        total += n
    }
    return total
}

/**
 * Seek positioning enumeration, mirroring `std::io::SeekFrom`.
 */
sealed class SeekFrom {
    data class Start(
        val offset: Long,
    ) : SeekFrom()

    data class End(
        val offset: Long,
    ) : SeekFrom()

    data class Current(
        val offset: Long,
    ) : SeekFrom()
}

/**
 * Trait for seeking in byte streams, mirroring `std::io::Seek`.
 */
interface Seek {
    fun seek(pos: SeekFrom): Long
}

/**
 * Reader adapter that limits the number of bytes read, mirroring `std::io::Take`.
 */
class Take<R : Read>(
    val reader: R,
    var limit: Long,
) : Read {
    override fun read(into: ByteArray, offset: Int, length: Int): Int {
        if (limit <= 0L) return 0
        val maxToRead = minOf(length.toLong(), limit).toInt()
        val n = reader.read(into, offset, maxToRead)
        if (n > 0) {
            limit -= n
        }
        return n
    }
}

fun <R : Read> R.take(limit: Long): Take<R> = Take(this, limit)

/**
 * Infinite reader that returns copies of a single byte, mirroring `std::io::Repeat`.
 */
class Repeat(
    val byte: Byte = 0,
) : Read {
    override fun read(into: ByteArray, offset: Int, length: Int): Int {
        for (i in offset until (offset + length)) {
            into[i] = byte
        }
        return length
    }
}

fun repeat(byte: Byte = 0): Repeat = Repeat(byte)

/**
 * Reader that is immediately at EOF, mirroring `std::io::Empty`.
 */
class Empty : Read {
    override fun read(into: ByteArray, offset: Int, length: Int): Int = 0
}

fun empty(): Empty = Empty()

/**
 * In-memory seekable reader over a byte array, mirroring `std::io::Cursor<&[u8]>`.
 */
class Cursor(
    val bytes: ByteArray,
    var pos: Int = 0,
) : SeekRead {
    override fun read(into: ByteArray, offset: Int, length: Int): Int {
        if (pos >= bytes.size) return 0
        val available = minOf(length, bytes.size - pos)
        bytes.copyInto(into, offset, pos, pos + available)
        pos += available
        return available
    }

    override fun seek(pos: SeekFrom): Long {
        val newPos =
            when (pos) {
                is SeekFrom.Start -> pos.offset
                is SeekFrom.End -> bytes.size.toLong() + pos.offset
                is SeekFrom.Current -> this.pos.toLong() + pos.offset
            }
        if (newPos < 0) {
            throw IoError(IoErrorKind.InvalidInput, "invalid seek to negative position")
        }
        this.pos = minOf(newPos, Int.MAX_VALUE.toLong()).toInt()
        return this.pos.toLong()
    }

    fun position(): Long = pos.toLong()
}
