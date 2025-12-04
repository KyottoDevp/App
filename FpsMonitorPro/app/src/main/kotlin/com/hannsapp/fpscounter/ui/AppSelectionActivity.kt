package com.hannsapp.fpscounter.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hannsapp.fpscounter.HannsApplication
import com.hannsapp.fpscounter.R
import com.hannsapp.fpscounter.adapters.AppListAdapter
import com.hannsapp.fpscounter.data.AppInfo
import com.hannsapp.fpscounter.data.PreferencesManager
import com.hannsapp.fpscounter.databinding.ActivityAppSelectionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSelectionBinding
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var appListAdapter: AppListAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var showSystemApps = false
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = HannsApplication.getInstance().preferencesManager

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupFilters()
        loadApps()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        appListAdapter = AppListAdapter(
            onAppClick = { app ->
                toggleAppMonitoring(app)
            },
            onAppCheckedChange = { app, isChecked ->
                updateAppMonitoring(app, isChecked)
            }
        )

        binding.recyclerViewApps.apply {
            layoutManager = LinearLayoutManager(this@AppSelectionActivity)
            adapter = appListAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                filterApps()
            }
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    private fun setupFilters() {
        binding.chipUserApps.isChecked = true
        binding.chipSystemApps.isChecked = false

        binding.chipUserApps.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showSystemApps = false
                binding.chipSystemApps.isChecked = false
                filterApps()
            }
        }

        binding.chipSystemApps.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showSystemApps = true
                binding.chipUserApps.isChecked = false
                filterApps()
            }
        }

        binding.switchShowAll.setOnCheckedChangeListener { _, isChecked ->
            binding.chipUserApps.isEnabled = !isChecked
            binding.chipSystemApps.isEnabled = !isChecked
            filterApps()
        }
    }

    private fun loadApps() {
        showLoading(true)

        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) {
                getInstalledApps()
            }
            filterApps()
            showLoading(false)
        }
    }

    private fun getInstalledApps(): List<AppInfo> {
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val monitoredApps = preferencesManager.monitoredApps

        return installedApps.mapNotNull { appInfo ->
            try {
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val packageName = appInfo.packageName
                
                if (packageName == this.packageName) return@mapNotNull null

                AppInfo(
                    packageName = packageName,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = packageManager.getApplicationIcon(appInfo),
                    isSystemApp = isSystemApp,
                    isMonitored = monitoredApps.contains(packageName),
                    versionName = try {
                        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
                    } catch (e: Exception) { "" },
                    versionCode = try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            packageManager.getPackageInfo(packageName, 0).longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
                        }
                    } catch (e: Exception) { 0L },
                    installedDate = try {
                        packageManager.getPackageInfo(packageName, 0).firstInstallTime
                    } catch (e: Exception) { 0L },
                    lastUpdated = try {
                        packageManager.getPackageInfo(packageName, 0).lastUpdateTime
                    } catch (e: Exception) { 0L }
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName.lowercase() }
    }

    private fun filterApps() {
        val showAll = binding.switchShowAll.isChecked

        val filteredApps = allApps.filter { app ->
            val matchesSearch = if (searchQuery.isEmpty()) {
                true
            } else {
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            }

            val matchesType = if (showAll) {
                true
            } else {
                if (showSystemApps) app.isSystemApp else !app.isSystemApp
            }

            matchesSearch && matchesType
        }

        appListAdapter.submitList(filteredApps)
        updateEmptyState(filteredApps.isEmpty())
        updateStats(filteredApps)
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmpty.isVisible = isEmpty
        binding.recyclerViewApps.isVisible = !isEmpty
    }

    private fun updateStats(apps: List<AppInfo>) {
        val monitoredCount = apps.count { it.isMonitored }
        binding.tvAppsCount.text = getString(R.string.monitored_apps) + ": $monitoredCount / ${apps.size}"
    }

    private fun toggleAppMonitoring(app: AppInfo) {
        updateAppMonitoring(app, !app.isMonitored)
    }

    private fun updateAppMonitoring(app: AppInfo, isMonitored: Boolean) {
        if (isMonitored) {
            preferencesManager.addMonitoredApp(app.packageName)
        } else {
            preferencesManager.removeMonitoredApp(app.packageName)
        }

        val updatedApps = allApps.map {
            if (it.packageName == app.packageName) {
                it.copy(isMonitored = isMonitored)
            } else {
                it
            }
        }
        allApps = updatedApps
        filterApps()
    }

    private fun showLoading(show: Boolean) {
        binding.progressLoading.isVisible = show
        binding.recyclerViewApps.isVisible = !show
    }
}
