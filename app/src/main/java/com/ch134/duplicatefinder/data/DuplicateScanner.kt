package com.ch134.duplicatefinder.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ch134.duplicatefinder.util.Hashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

sealed class ScanEvent {
    data class Progress(val done: Int, val total: Int, val label: String) : ScanEvent()
    data class Finished(val groups: List<DuplicateGroup>, val reclaimable: Long) : ScanEvent()
    data class Failed(val message: String) : ScanEvent()
}

class DuplicateScanner(private val context: Context) {

    suspend fun scan(treeUri: Uri, minSize: Long, onEvent: (ScanEvent) -> Unit) =
        withContext(Dispatchers.IO) {
            try {
                val root = DocumentFile.fromTreeUri(context, treeUri)
                if (root == null) {
                    onEvent(ScanEvent.Failed("Cannot open the selected folder"))
                    return@withContext
                }

                onEvent(ScanEvent.Progress(0, 0, "Indexing files"))
                val files = ArrayList<FileEntry>(1024)
                collect(root, files, minSize) { count ->
                    if (count % 250 == 0) {
                        onEvent(ScanEvent.Progress(count, 0, "Indexed $count files"))
                    }
                }
                coroutineContext.ensureActive()

                val buckets = files.groupBy { it.size }.filter { it.value.size > 1 }
                val quickTotal = buckets.values.sumOf { it.size }
                onEvent(
                    ScanEvent.Progress(
                        0,
                        quickTotal,
                        "Comparing ${files.size} files across ${buckets.size} size buckets"
                    )
                )

                val quickMatches = ArrayList<List<FileEntry>>()
                var done = 0
                for (bucket in buckets.values) {
                    coroutineContext.ensureActive()
                    val byHash = LinkedHashMap<String, MutableList<FileEntry>>()
                    for (entry in bucket) {
                        val hash = Hashing.quick(context, entry.uri) ?: continue
                        byHash.getOrPut(hash) { mutableListOf() }.add(entry)
                        done++
                        if (done % 20 == 0) {
                            onEvent(ScanEvent.Progress(done, quickTotal, "Quick hashing"))
                        }
                    }
                    byHash.values.filter { it.size > 1 }.forEach { quickMatches.add(it) }
                }

                val fullTotal = quickMatches.sumOf { it.size }
                var verified = 0
                val groups = ArrayList<DuplicateGroup>()
                for (bucket in quickMatches) {
                    coroutineContext.ensureActive()
                    val byHash = LinkedHashMap<String, MutableList<FileEntry>>()
                    for (entry in bucket) {
                        val hash = Hashing.sha256(context, entry.uri) ?: continue
                        byHash.getOrPut(hash) { mutableListOf() }.add(entry)
                        verified++
                        if (verified % 10 == 0) {
                            onEvent(ScanEvent.Progress(verified, fullTotal, "Verifying matches"))
                        }
                    }
                    byHash.forEach { (hash, list) ->
                        if (list.size > 1) {
                            groups.add(
                                DuplicateGroup(
                                    hash = hash,
                                    size = list.first().size,
                                    files = list.sortedBy { it.name.lowercase() }
                                )
                            )
                        }
                    }
                }

                groups.sortByDescending { it.wastedBytes }
                onEvent(ScanEvent.Finished(groups, groups.sumOf { it.wastedBytes }))
            } catch (t: Throwable) {
                onEvent(ScanEvent.Failed(t.message ?: t.javaClass.simpleName))
            }
        }

    private fun collect(
        dir: DocumentFile,
        out: MutableList<FileEntry>,
        minSize: Long,
        onCount: (Int) -> Unit
    ) {
        val children = try {
            dir.listFiles()
        } catch (t: Throwable) {
            emptyArray<DocumentFile>()
        }
        for (child in children) {
            if (child.isDirectory) {
                collect(child, out, minSize, onCount)
            } else if (child.isFile && child.length() >= minSize) {
                out.add(
                    FileEntry(
                        uri = child.uri,
                        name = child.name ?: "unknown",
                        size = child.length(),
                        mimeType = child.type,
                        displayPath = child.uri.lastPathSegment ?: child.name.orEmpty()
                    )
                )
                onCount(out.size)
            }
        }
    }
}
