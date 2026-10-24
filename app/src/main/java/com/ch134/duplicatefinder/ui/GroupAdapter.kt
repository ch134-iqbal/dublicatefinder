package com.ch134.duplicatefinder.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ch134.duplicatefinder.R
import com.ch134.duplicatefinder.data.DuplicateGroup
import com.ch134.duplicatefinder.data.FileEntry
import com.ch134.duplicatefinder.databinding.ItemFileBinding
import com.ch134.duplicatefinder.databinding.ItemGroupBinding
import com.ch134.duplicatefinder.util.Formatter

class GroupAdapter(
    private val onToggle: (FileEntry) -> Unit,
    private val isSelected: (FileEntry) -> Boolean,
    private val onOpen: (FileEntry) -> Unit
) : ListAdapter<DuplicateGroup, GroupAdapter.GroupHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupHolder {
        val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GroupHolder(private val b: ItemGroupBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(group: DuplicateGroup) {
            val ctx = b.root.context
            b.groupTitle.text = ctx.getString(R.string.group_title, group.files.size)
            b.groupSubtitle.text = ctx.getString(
                R.string.group_subtitle,
                Formatter.size(group.size),
                Formatter.size(group.wastedBytes)
            )
            b.fileList.removeAllViews()
            group.files.forEachIndexed { index, entry ->
                val row = ItemFileBinding.inflate(LayoutInflater.from(ctx), b.fileList, false)
                row.fileName.text = entry.name
                row.fileMeta.text = ctx.getString(
                    R.string.file_meta,
                    Formatter.size(entry.size),
                    if (index == 0) "keep" else "duplicate"
                )
                row.fileCheck.isChecked = isSelected(entry)
                row.fileCheck.setOnClickListener { onToggle(entry) }
                row.root.setOnClickListener { onOpen(entry) }
                b.fileList.addView(row.root)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DuplicateGroup>() {
            override fun areItemsTheSame(oldItem: DuplicateGroup, newItem: DuplicateGroup) =
                oldItem.hash == newItem.hash

            override fun areContentsTheSame(oldItem: DuplicateGroup, newItem: DuplicateGroup) =
                oldItem == newItem
        }
    }
}
