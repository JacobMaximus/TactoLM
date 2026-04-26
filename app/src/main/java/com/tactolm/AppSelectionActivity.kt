package com.tactolm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    var isSelected: Boolean
)

class AppSelectionActivity : AppCompatActivity() {

    private lateinit var rvApps: RecyclerView
    private lateinit var progressLoading: ProgressBar
    private lateinit var btnBack: LinearLayout
    private lateinit var adapter: AppSelectionAdapter

    private val prefs by lazy { getSharedPreferences("TactoLM_Prefs", Context.MODE_PRIVATE) }
    private val selectedApps: MutableSet<String> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-edge dark theme
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_app_selection)

        rvApps = findViewById(R.id.rv_apps)
        progressLoading = findViewById(R.id.progress_loading)
        btnBack = findViewById(R.id.btn_back)

        btnBack.setOnClickListener { finish() }

        // Load saved preferences
        val saved = prefs.getStringSet("selected_apps", emptySet())
        if (saved != null) {
            selectedApps.addAll(saved)
        }

        rvApps.layoutManager = LinearLayoutManager(this)

        loadApps()
    }

    private fun loadApps() {
        progressLoading.visibility = View.VISIBLE
        rvApps.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            
            val resolveInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                pm.queryIntentActivities(mainIntent, 0)
            }

            val appList = resolveInfos.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                // Filter out TactoLM itself just to be clean
                if (packageName == applicationContext.packageName) return@mapNotNull null
                
                val appName = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                val isSelected = selectedApps.contains(packageName)
                
                AppItem(packageName, appName, icon, isSelected)
            }.sortedBy { it.appName.lowercase() }
            
            // Deduplicate in case multiple launcher activities exist for one app
            val distinctList = appList.distinctBy { it.packageName }

            withContext(Dispatchers.Main) {
                adapter = AppSelectionAdapter(distinctList) { item, isChecked ->
                    if (isChecked) {
                        selectedApps.add(item.packageName)
                    } else {
                        selectedApps.remove(item.packageName)
                    }
                    saveSelectedApps()
                }
                rvApps.adapter = adapter
                progressLoading.visibility = View.GONE
                rvApps.visibility = View.VISIBLE
            }
        }
    }

    private fun saveSelectedApps() {
        prefs.edit().putStringSet("selected_apps", selectedApps).apply()
    }

    inner class AppSelectionAdapter(
        private val items: List<AppItem>,
        private val onSelectionChanged: (AppItem, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppSelectionAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val name: TextView = view.findViewById(R.id.app_name)
            val checkbox: CheckBox = view.findViewById(R.id.app_checkbox)
            val root: LinearLayout = view as LinearLayout
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_selection, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.icon.setImageDrawable(item.icon)
            holder.name.text = item.appName
            
            // Unregister listener so it doesn't trigger during recycling
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = item.isSelected
            
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
                onSelectionChanged(item, isChecked)
            }
            
            holder.root.setOnClickListener {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
            }
        }

        override fun getItemCount() = items.size
    }
}
