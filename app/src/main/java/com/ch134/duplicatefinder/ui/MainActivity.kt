package com.ch134.duplicatefinder.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ch134.duplicatefinder.R
import com.ch134.duplicatefinder.databinding.ActivityMainBinding
import com.ch134.duplicatefinder.util.Formatter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: GroupAdapter
    private val vm: ScanViewModel by viewModels()
    private var folderUri: Uri? = null

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (t: Throwable) {
                // some providers refuse write grants; scanning still works read-only
            }
            folderUri = uri
            binding.folderLabel.text = uri.lastPathSegment ?: uri.toString()
            binding.scanButton.isEnabled = true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = GroupAdapter(
            onToggle = { entry -> vm.toggle(entry.uri) },
            isSelected = { entry -> vm.isSelected(entry.uri) },
            onOpen = { entry -> openFile(entry.uri, entry.mimeType) }
        )
        binding.groups.layoutManager = LinearLayoutManager(this)
        binding.groups.adapter = adapter

        binding.pickButton.setOnClickListener { pickFolder.launch(folderUri) }
        binding.scanButton.isEnabled = false
        binding.scanButton.setOnClickListener {
            folderUri?.let { vm.scan(it, minSize()) }
        }
        binding.selectDupes.setOnClickListener { vm.selectAllExtras() }
        binding.clearSel.setOnClickListener { vm.clearSelection() }
        binding.deleteSelected.setOnClickListener { confirmDelete() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    private fun minSize(): Long = when (binding.sizeFilter.selectedItemPosition) {
        0 -> 0L
        1 -> 1024L
        2 -> 1024L * 1024L
        else -> 10L * 1024L * 1024L
    }

    private fun render(state: UiState) {
        binding.progress.visibility = if (state.scanning) View.VISIBLE else View.GONE
        binding.progress.isIndeterminate = state.indeterminate
        binding.progress.progress = state.progress
        binding.status.text = state.status
        binding.summary.text =
            if (state.groups.isEmpty()) ""
            else getString(R.string.summary, state.groups.size, Formatter.size(state.reclaimable))
        binding.deleteSelected.isEnabled = state.selectedCount > 0
        binding.deleteSelected.text =
            getString(R.string.delete_n, state.selectedCount, Formatter.size(state.selectedBytes))
        binding.scanButton.isEnabled = folderUri != null && !state.scanning
        adapter.submitList(state.groups)
        state.error?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            vm.consumeError()
        }
    }

    private fun confirmDelete() {
        val targets = vm.deletionTargets()
        if (targets.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, targets.size))
            .setPositiveButton(R.string.delete_confirm) { _, _ -> deleteFiles(targets) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteFiles(targets: List<Uri>) {
        var ok = 0
        var failed = 0
        targets.forEach { uri ->
            try {
                if (android.provider.DocumentsContract.deleteDocument(contentResolver, uri)) ok++ else failed++
            } catch (t: Throwable) {
                failed++
            }
        }
        vm.afterDelete(ok, failed)
        Toast.makeText(this, getString(R.string.delete_done, ok, failed), Toast.LENGTH_LONG).show()
    }

    private fun openFile(uri: Uri, mime: String?) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        } catch (t: Throwable) {
            Toast.makeText(this, R.string.no_viewer, Toast.LENGTH_SHORT).show()
        }
    }
}
