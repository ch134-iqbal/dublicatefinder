package com.ch134.duplicatefinder.data

import android.net.Uri

data class FileEntry(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val displayPath: String
) {
    val isMedia: Boolean
        get() {
            val type = mimeType ?: return false
            return type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/")
        }
}
