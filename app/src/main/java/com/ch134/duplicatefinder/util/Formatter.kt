package com.ch134.duplicatefinder.util

import java.util.Locale

object Formatter {

    private val UNITS = arrayOf("KB", "MB", "GB", "TB")

    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble() / 1024.0
        var index = 0
        while (value >= 1024.0 && index < UNITS.size - 1) {
            value /= 1024.0
            index++
        }
        return String.format(Locale.US, "%.1f %s", value, UNITS[index])
    }
}
