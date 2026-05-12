// port-lint: source src/entry_type.rs
package io.github.kotlinmania.tar

// See https://en.wikipedia.org/wiki/Tar_%28computing%29#UStar_format
/**
 * Indicate the type of content described by a header.
 *
 * This is returned by [Header.entryType] and should be used to
 * distinguish between types of content.
 */
sealed class EntryType {
    /** Regular file */
    object Regular : EntryType()

    /** Hard link */
    object Link : EntryType()

    /** Symbolic link */
    object Symlink : EntryType()

    /** Character device */
    object Char : EntryType()

    /** Block device */
    object Block : EntryType()

    /** Directory */
    object Directory : EntryType()

    /** Named pipe (fifo) */
    object Fifo : EntryType()

    /** Implementation-defined 'high-performance' type, treated as regular file */
    object Continuous : EntryType()

    /** GNU extension - long file name */
    object GNULongName : EntryType()

    /** GNU extension - long link name (link target) */
    object GNULongLink : EntryType()

    /** GNU extension - sparse file */
    object GNUSparse : EntryType()

    /** Global extended header */
    object XGlobalHeader : EntryType()

    /** Extended Header */
    object XHeader : EntryType()

    /**
     * Hints that destructuring should not be exhaustive.
     *
     * This sealed hierarchy may grow additional variants, so this makes sure
     * clients don't count on exhaustive matching. (Otherwise, adding a new
     * variant could break existing code.)
     */
    data class __Nonexhaustive(val byte: UByte) : EntryType()

    /**
     * Returns the raw underlying byte that this entry type represents.
     */
    fun asByte(): UByte = when (this) {
        Regular -> '0'.code.toUByte()
        Link -> '1'.code.toUByte()
        Symlink -> '2'.code.toUByte()
        Char -> '3'.code.toUByte()
        Block -> '4'.code.toUByte()
        Directory -> '5'.code.toUByte()
        Fifo -> '6'.code.toUByte()
        Continuous -> '7'.code.toUByte()
        XHeader -> 'x'.code.toUByte()
        XGlobalHeader -> 'g'.code.toUByte()
        GNULongName -> 'L'.code.toUByte()
        GNULongLink -> 'K'.code.toUByte()
        GNUSparse -> 'S'.code.toUByte()
        is __Nonexhaustive -> this.byte
    }

    /** Returns whether this type represents a regular file. */
    fun isFile(): Boolean = this == Regular

    /** Returns whether this type represents a hard link. */
    fun isHardLink(): Boolean = this == Link

    /** Returns whether this type represents a symlink. */
    fun isSymlink(): Boolean = this == Symlink

    /** Returns whether this type represents a character special device. */
    fun isCharacterSpecial(): Boolean = this == Char

    /** Returns whether this type represents a block special device. */
    fun isBlockSpecial(): Boolean = this == Block

    /** Returns whether this type represents a directory. */
    fun isDir(): Boolean = this == Directory

    /** Returns whether this type represents a FIFO. */
    fun isFifo(): Boolean = this == Fifo

    /** Returns whether this type represents a contiguous file. */
    fun isContiguous(): Boolean = this == Continuous

    /** Returns whether this type represents a GNU long name header. */
    fun isGnuLongname(): Boolean = this == GNULongName

    /** Returns whether this type represents a GNU sparse header. */
    fun isGnuSparse(): Boolean = this == GNUSparse

    /** Returns whether this type represents a GNU long link header. */
    fun isGnuLonglink(): Boolean = this == GNULongLink

    /**
     * Returns whether this type represents PAX global extensions, that
     * should affect all following entries.  For more, see [PAX].
     *
     * [PAX]: https://pubs.opengroup.org/onlinepubs/9699919799/utilities/pax.html
     */
    fun isPaxGlobalExtensions(): Boolean = this == XGlobalHeader

    /**
     * Returns whether this type represents PAX local extensions; these
     * only affect the current entry.  For more, see [PAX].
     *
     * [PAX]: https://pubs.opengroup.org/onlinepubs/9699919799/utilities/pax.html
     */
    fun isPaxLocalExtensions(): Boolean = this == XHeader

    companion object {
        /**
         * Creates a new entry type from a raw byte.
         *
         * Note that the other named constructors of entry type may be more
         * appropriate to create a file type from.
         */
        fun new(byte: UByte): EntryType = when (byte.toInt()) {
            0x00, '0'.code -> Regular
            '1'.code -> Link
            '2'.code -> Symlink
            '3'.code -> Char
            '4'.code -> Block
            '5'.code -> Directory
            '6'.code -> Fifo
            '7'.code -> Continuous
            'x'.code -> XHeader
            'g'.code -> XGlobalHeader
            'L'.code -> GNULongName
            'K'.code -> GNULongLink
            'S'.code -> GNUSparse
            else -> __Nonexhaustive(byte)
        }

        /** Creates a new entry type representing a regular file. */
        fun file(): EntryType = Regular

        /** Creates a new entry type representing a hard link. */
        fun hardLink(): EntryType = Link

        /** Creates a new entry type representing a symlink. */
        fun symlink(): EntryType = Symlink

        /** Creates a new entry type representing a character special device. */
        fun characterSpecial(): EntryType = Char

        /** Creates a new entry type representing a block special device. */
        fun blockSpecial(): EntryType = Block

        /** Creates a new entry type representing a directory. */
        fun dir(): EntryType = Directory

        /** Creates a new entry type representing a FIFO. */
        fun fifo(): EntryType = Fifo

        /** Creates a new entry type representing a contiguous file. */
        fun contiguous(): EntryType = Continuous
    }
}
