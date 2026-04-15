package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Filesystem type classification — originates from the Rust core.
 *
 * WHY an enum here: Provides exhaustive `when` matching on the Kotlin side.
 * The Rust core serializes the type as a string; this enum maps it.
 *
 * PRD Non-Negotiable Rule #1 applied to filesystems: the Kotlin UI never
 * decides filesystem type — that classification comes from the Rust core.
 */
@Serializable
enum class FilesystemType {
    /** ext4 — supported for read-only browsing in v1. */
    EXT4,

    /** EROFS — detected but not supported until v1.3. */
    EROFS,

    /** Unknown format — raw export only. */
    UNKNOWN,
    ;

    companion object {
        /**
         * Map a Rust core filesystem type string to the enum.
         *
         * WHY ignoreCase: The Rust side may emit "ext4", "Ext4", or "EXT4"
         * depending on the Display vs Serialize path.
         */
        fun fromString(value: String): FilesystemType = when {
            value.equals("ext4", ignoreCase = true) -> EXT4
            value.equals("erofs", ignoreCase = true) -> EROFS
            else -> UNKNOWN
        }
    }
}

/**
 * File entry type — mirrors the Rust FsFileType.
 */
@Serializable
enum class FsEntryType {
    FILE,
    DIRECTORY,
    SYMLINK,
    CHAR_DEVICE,
    BLOCK_DEVICE,
    FIFO,
    SOCKET,
    UNKNOWN,
    ;

    companion object {
        fun fromString(value: String): FsEntryType = when {
            value.equals("file", ignoreCase = true) ||
                value.equals("regular", ignoreCase = true) ||
                value.equals("RegularFile", ignoreCase = true) -> FILE
            value.equals("directory", ignoreCase = true) -> DIRECTORY
            value.equals("symlink", ignoreCase = true) -> SYMLINK
            value.equals("char device", ignoreCase = true) -> CHAR_DEVICE
            value.equals("block device", ignoreCase = true) -> BLOCK_DEVICE
            value.equals("fifo", ignoreCase = true) -> FIFO
            value.equals("socket", ignoreCase = true) -> SOCKET
            else -> UNKNOWN
        }
    }
}
