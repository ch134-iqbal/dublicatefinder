package com.ch134.duplicatefinder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ch134.duplicatefinder.data.DuplicateGroup
import com.ch134.duplicatefinder.data.DuplicateScanner
import com.ch134.duplicatefinder.data.ScanEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val scanning: Boolean = false,
    val progress: Int = 0,
    val indeterminate: Boolean = false,
    val status: String = "",
    val groups: List<DuplicateGroup> = emptyList(),
    val scanned: Int = 0,
    val reclaimable: Long = 0L,
    val selectedCount: Int = 0,
    val selectedBytes: Long = 0L,
    val error: String? = null
)

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = DuplicateScanner(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null
    private val selections = LinkedHashSet<Uri>()

    fun scan(uri: Uri, minSize: Long) {
        if (job?.isActive == true) return
        selections.clear()
        _state.value = UiState(scanning = true, indeterminate = true, status = "Starting scan")
        job = viewModelScope.launch {
            scanner.scan(uri, minSize) { ev ->
                when (ev) {
                    is ScanEvent.Progress -> _state.value = _state.value.copy(
                        indeterminate = ev.total <= 0,
                        progress = if (ev.total > 0) (ev.done * 100 / ev.total) else 0,
                        status = ev.label,
                        scanned = ev.done
                    )
                    is ScanEvent.Finished -> _state.value = _state.value.copy(
                        scanning = false,
                        indeterminate = false,
                        progress = 100,
                        groups = ev.groups,
                        reclaimable = ev.reclaimable,
                        status = if (ev.groups.isEmpty())
                            "No duplicates found"
                        else
                            "${ev.groups.size} duplicate groups found"
                    )
                    is ScanEvent.Failed -> _state.value = _state.value.copy(
                        scanning = false,
                        indeterminate = false,
                        error = ev.message
                    )
                }
            }
        }
    }

    fun toggle(uri: Uri) {
        if (!selections.remove(uri)) selections.add(uri)
        recompute()
    }

    fun selectAllExtras() {
        state.value.groups.forEach { group ->
            group.files.drop(1).forEach { selections.add(it.uri) }
        }
        recompute()
    }

    fun clearSelection() {
        selections.clear()
        recompute()
    }

    fun consumeError() {
        if (_state.value.error != null) {
            _state.value = _state.value.copy(error = null)
        }
    }

    fun isSelected(uri: Uri): Boolean = selections.contains(uri)

    fun deletionTargets(): List<Uri> = selections.toList()

    fun afterDelete(deleted: Int, failed: Int) {
        val remaining = state.value.groups.mapNotNull { group ->
            val kept = group.files.filter { !selections.contains(it.uri) }
            if (kept.size <= 1) null else group.copy(files = kept)
        }
        selections.clear()
        _state.value = _state.value.copy(
            groups = remaining,
            status = "Deleted $deleted files" + if (failed > 0) ", $failed failed" else "",
            selectedCount = 0,
            selectedBytes = 0,
            reclaimable = remaining.sumOf { it.wastedBytes }
        )
    }

    private fun recompute() {
        val selected = state.value.groups
            .flatMap { it.files }
            .filter { selections.contains(it.uri) }
        _state.value = _state.value.copy(
            selectedCount = selected.size,
            selectedBytes = selected.sumOf { it.size }
        )
    }
}
