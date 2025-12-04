package com.hannsapp.fpscounter.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hannsapp.fpscounter.data.AppInfo
import com.hannsapp.fpscounter.databinding.ItemAppBinding

class AppListAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppCheckedChange: (AppInfo, Boolean) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(
        private val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val app = getItem(position)
                    onAppClick(app)
                }
            }

            binding.checkboxMonitor.setOnCheckedChangeListener { _, isChecked ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val app = getItem(position)
                    if (app.isMonitored != isChecked) {
                        onAppCheckedChange(app, isChecked)
                    }
                }
            }
        }

        fun bind(appInfo: AppInfo) {
            binding.apply {
                ivAppIcon.setImageDrawable(appInfo.icon)
                tvAppName.text = appInfo.appName
                tvPackageName.text = appInfo.packageName
                
                checkboxMonitor.setOnCheckedChangeListener(null)
                checkboxMonitor.isChecked = appInfo.isMonitored
                checkboxMonitor.setOnCheckedChangeListener { _, isChecked ->
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val app = getItem(position)
                        if (app.isMonitored != isChecked) {
                            onAppCheckedChange(app, isChecked)
                        }
                    }
                }

                if (appInfo.isSystemApp) {
                    tvSystemBadge.visibility = android.view.View.VISIBLE
                } else {
                    tvSystemBadge.visibility = android.view.View.GONE
                }
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem && oldItem.isMonitored == newItem.isMonitored
        }
    }

    fun updateAppMonitorStatus(packageName: String, isMonitored: Boolean) {
        val currentList = currentList.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            currentList[index] = currentList[index].copy(isMonitored = isMonitored)
            submitList(currentList)
        }
    }
}
