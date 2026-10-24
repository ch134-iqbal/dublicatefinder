package com.ch134.duplicatefinder.data

data class DuplicateGroup(
    val hash: String,
    val size: Long,
    val files: List<FileEntry>
) {
    val wastedBytes: Long
        get() = size * (files.size - 1).coerceAtLeast(0)
}
