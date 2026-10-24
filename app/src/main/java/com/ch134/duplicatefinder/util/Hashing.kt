package com.ch134.duplicatefinder.util

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

object Hashing {

    private const val QUICK_BYTES = 64L * 1024L

    fun sha256(context: Context, uri: Uri): String? = digest(context, uri, Long.MAX_VALUE)

    fun quick(context: Context, uri: Uri): String? = digest(context, uri, QUICK_BYTES)

    private fun digest(context: Context, uri: Uri, limit: Long): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val md = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    md.update(buffer, 0, read)
                    if (total >= limit) break
                }
                md.update(total.toString().toByteArray())
                md.digest().joinToString("") { byte -> "%02x".format(byte) }
            }
        } catch (t: Throwable) {
            null
        }
    }
}
