package com.notificationlogger.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notificationlogger.R
import com.notificationlogger.data.database.AppFilter
import com.notificationlogger.data.model.PersonAlias
import com.notificationlogger.ui.MainViewModel
import com.notificationlogger.util.BiometricHelper
import com.notificationlogger.util.PrefsHelper

class SettingsFragment : Fragment() {

    private lateinit var vm: MainViewModel
    private lateinit var prefs: PrefsHelper

    // File picker for CSV import
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        // Confirm before wiping
        AlertDialog.Builder(requireContext())
            .setTitle("Replace all data?")
            .setMessage("This will permanently delete all current logs and replace them with the imported file. This cannot be undone.")
            .setPositiveButton("Import & Replace") { _, _ ->
                vm.importFromCsv(uri)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm    = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        prefs = PrefsHelper(requireContext())

        // ── Privacy toggles ────────────────────────────────────────────────
        val swContent = view.findViewById<Switch>(R.id.swContentLogging)
        swContent.isChecked = prefs.isContentLoggingEnabled()
        swContent.setOnCheckedChangeListener { _, checked -> prefs.setContentLogging(checked) }

        val swBiometric = view.findViewById<Switch>(R.id.swBiometric)
        swBiometric.isEnabled = BiometricHelper.isAvailable(requireActivity())
        swBiometric.isChecked = prefs.isBiometricEnabled()
        swBiometric.setOnCheckedChangeListener { _, checked -> prefs.setBiometricEnabled(checked) }

        // ── Import CSV ────────────────────────────────────────────────────
        view.findViewById<Button>(R.id.btnImportCsv).setOnClickListener {
            importLauncher.launch("text/*")
        }

        vm.importResult.observe(viewLifecycleOwner) { event ->
            val result = event.getIfNotHandled() ?: return@observe
            val msg = buildString {
                append("Import complete\n")
                append("✅ ${result.imported} rows imported\n")
                if (result.skipped > 0) append("⏭️ ${result.skipped} rows skipped\n")
                if (result.errors.isNotEmpty()) {
                    append("⚠️ ${result.errors.size} errors:")
                    result.errors.take(3).forEach { append("\n  $it") }
                }
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }

        // ── Delete all ────────────────────────────────────────────────────
        view.findViewById<Button>(R.id.btnDeleteAll).setOnClickListener {
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

        // ── Aliases ───────────────────────────────────────────────────────
        val rvAliases = view.findViewById<RecyclerView>(R.id.rvAliases)
        val aliasAdapter = AliasAdapter(
            onRemove = { alias -> vm.removeAlias(alias) },
            onRemoveGroup = { canonical ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Remove alias group?")
                    .setMessage("Remove all aliases for '$canonical'?")
                    .setPositiveButton("Remove") { _, _ -> vm.removeAliasGroup(canonical) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        rvAliases.layoutManager = LinearLayoutManager(requireContext())
        rvAliases.adapter = aliasAdapter

        vm.allAliases.observe(viewLifecycleOwner) { aliases ->
            aliasAdapter.submitList(aliases)
        }

        view.findViewById<Button>(R.id.btnAddAlias).setOnClickListener {
            showAddAliasDialog()
        }

        // ── App Filters ────────────────────────────────────────────────────
        val rvFilters = view.findViewById<RecyclerView>(R.id.rvFilters)
        val filterAdapter = FilterAdapter { filter -> vm.removeFilter(filter) }
        rvFilters.layoutManager = LinearLayoutManager(requireContext())
        rvFilters.adapter = filterAdapter
        vm.allFilters.observe(viewLifecycleOwner) { filterAdapter.submitList(it) }

        view.findViewById<Button>(R.id.btnAddBlacklist).setOnClickListener {
            showAddFilterDialog("BLACKLIST")
        }
        view.findViewById<Button>(R.id.btnAddWhitelist).setOnClickListener {
            showAddFilterDialog("WHITELIST")
        }
    }

    private fun showAddAliasDialog() {
        val inflater = layoutInflater
        val dialogView = inflater.inflate(android.R.layout.simple_list_item_2, null)

        // Simple two-field dialog using EditTexts
        val rawInput = EditText(requireContext()).apply {
            hint = "Raw name (as it appears in notifications)"
        }
        val canonInput = EditText(requireContext()).apply {
            hint = "Canonical name (display name to group under)"
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(rawInput)
            addView(canonInput)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Add Name Alias")
            .setMessage("Map a raw sender name to a canonical display name.")
            .setView(container)
            .setPositiveButton("Add") { _, _ ->
                val raw    = rawInput.text.toString().trim()
                val canon  = canonInput.text.toString().trim()
                if (raw.isNotEmpty() && canon.isNotEmpty()) {
                    vm.addAlias(raw, canon)
                    Toast.makeText(requireContext(),
                        "\"$raw\" → \"$canon\" added", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Both fields required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddFilterDialog(type: String) {
        val input = EditText(requireContext()).apply { hint = "com.example.app" }
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

// ── Alias adapter ─────────────────────────────────────────────────────────────

class AliasAdapter(
    private val onRemove: (PersonAlias) -> Unit,
    private val onRemoveGroup: (String) -> Unit
) : RecyclerView.Adapter<AliasAdapter.VH>() {

    private var items: List<PersonAlias> = emptyList()

    fun submitList(list: List<PersonAlias>) { items = list; notifyDataSetChanged() }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_alias, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(items[position], onRemove, onRemoveGroup)

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvRaw    = view.findViewById<TextView>(R.id.tvAliasRaw)
        private val tvCanon  = view.findViewById<TextView>(R.id.tvAliasCanonical)
        private val btnRemove = view.findViewById<TextView>(R.id.btnRemoveAlias)

        fun bind(alias: PersonAlias, onRemove: (PersonAlias) -> Unit, onRemoveGroup: (String) -> Unit) {
            tvRaw.text   = "\"${alias.rawName}\""
            tvCanon.text = "→ ${alias.canonicalName}"
            btnRemove.setOnClickListener { onRemove(alias) }
            btnRemove.setOnLongClickListener { onRemoveGroup(alias.canonicalName); true }
        }
    }
}

// ── Filter adapter (unchanged) ────────────────────────────────────────────────

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
        private val tvName    = view.findViewById<TextView>(R.id.tvFilterName)
        private val tvType    = view.findViewById<TextView>(R.id.tvFilterType)
        private val btnRemove = view.findViewById<Button>(R.id.btnRemoveFilter)

        fun bind(f: AppFilter, onRemove: (AppFilter) -> Unit) {
            tvName.text = f.appName
            tvType.text = f.filterType
            tvType.setTextColor(if (f.filterType == "BLACKLIST") 0xFFE53935.toInt() else 0xFF43A047.toInt())
            btnRemove.setOnClickListener { onRemove(f) }
        }
    }
}
