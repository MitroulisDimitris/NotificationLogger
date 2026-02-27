package com.notificationlogger.ui.settings

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notificationlogger.R
import com.notificationlogger.data.database.AppFilter
import com.notificationlogger.ui.MainViewModel
import com.notificationlogger.util.BiometricHelper
import com.notificationlogger.util.PrefsHelper  // still needed for biometric

class SettingsFragment : Fragment() {

    private lateinit var vm: MainViewModel
    private lateinit var prefs: PrefsHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        prefs = PrefsHelper(requireContext())

        // Content Logging Toggle
        val swContent = view.findViewById<Switch>(R.id.swContentLogging)
        swContent.isChecked = prefs.isContentLoggingEnabled()
        swContent.setOnCheckedChangeListener { _, checked ->
            prefs.setContentLogging(checked)
        }

        // Biometric Toggle
        val swBiometric = view.findViewById<Switch>(R.id.swBiometric)
        swBiometric.isEnabled = BiometricHelper.isAvailable(requireActivity())
        swBiometric.isChecked = prefs.isBiometricEnabled()
        swBiometric.setOnCheckedChangeListener { _, checked ->
            prefs.setBiometricEnabled(checked)
        }

        // Delete all
        val btnDeleteAll = view.findViewById<Button>(R.id.btnDeleteAll)
        btnDeleteAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete ALL Logs?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete All") { _, _ ->
                    vm.deleteAll()
                    Toast.makeText(requireContext(), "All logs deleted", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Filters list
        val rvFilters = view.findViewById<RecyclerView>(R.id.rvFilters)
        val filterAdapter = FilterAdapter { filter -> vm.removeFilter(filter) }
        rvFilters.layoutManager = LinearLayoutManager(requireContext())
        rvFilters.adapter = filterAdapter

        vm.allFilters.observe(viewLifecycleOwner) { filters ->
            filterAdapter.submitList(filters)
        }

        // Add blacklist
        val btnAddBlacklist = view.findViewById<Button>(R.id.btnAddBlacklist)
        btnAddBlacklist.setOnClickListener { showAddFilterDialog("BLACKLIST") }

        // Add whitelist
        val btnAddWhitelist = view.findViewById<Button>(R.id.btnAddWhitelist)
        btnAddWhitelist.setOnClickListener { showAddFilterDialog("WHITELIST") }
    }

    private fun showAddFilterDialog(type: String) {
        val input = EditText(requireContext()).apply {
            hint = "com.example.app"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Add to ${type.lowercase().replaceFirstChar { it.uppercase() }}")
            .setMessage("Enter the app's package name:")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val pkg = input.text.toString().trim()
                if (pkg.isNotEmpty()) {
                    if (type == "BLACKLIST") vm.addToBlacklist(pkg, pkg)
                    else vm.addToWhitelist(pkg, pkg)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class FilterAdapter(private val onRemove: (AppFilter) -> Unit) :
    RecyclerView.Adapter<FilterAdapter.VH>() {

    private var items: List<AppFilter> = emptyList()

    fun submitList(list: List<AppFilter>) { items = list; notifyDataSetChanged() }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_filter, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position], onRemove)

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName  = view.findViewById<TextView>(R.id.tvFilterName)
        private val tvType  = view.findViewById<TextView>(R.id.tvFilterType)
        private val btnRemove = view.findViewById<Button>(R.id.btnRemoveFilter)

        fun bind(f: AppFilter, onRemove: (AppFilter) -> Unit) {
            tvName.text = f.appName
            tvType.text = f.filterType
            tvType.setTextColor(if (f.filterType == "BLACKLIST") 0xFFE53935.toInt() else 0xFF43A047.toInt())
            btnRemove.setOnClickListener { onRemove(f) }
        }
    }
}
