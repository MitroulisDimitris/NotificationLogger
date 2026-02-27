package com.notificationlogger.ui.explorer

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.*
import com.notificationlogger.R
import com.notificationlogger.data.model.ExplorerFilter
import com.notificationlogger.data.model.ExplorerRow
import com.notificationlogger.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

class ExplorerFragment : Fragment() {

    private lateinit var vm: MainViewModel
    private lateinit var adapter: ExplorerAdapter

    // Current filter state
    private var selectedApp: String? = null
    private var selectedType: String? = null
    private var textQuery: String? = null
    private var senderQuery: String? = null
    private var sinceDays: Int = 30

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_explorer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        // Results list
        adapter = ExplorerAdapter()
        val rv = view.findViewById<RecyclerView>(R.id.rvExplorer)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Infinite scroll
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(1)) applyFilter(loadMore = true)
            }
        })

        // ── Filter bar ────────────────────────────────────────────────────────

        // Text search
        val searchView = view.findViewById<SearchView>(R.id.searchExplorer)
        searchView.queryHint = "Search message text..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean { textQuery = q; applyFilter(); return true }
            override fun onQueryTextChange(q: String?): Boolean {
                if (q.isNullOrBlank()) { textQuery = null; applyFilter() }
                return true
            }
        })

        // Sender search
        val etSender = view.findViewById<EditText>(R.id.etSenderFilter)
        val btnSenderApply = view.findViewById<View>(R.id.btnSenderApply)
        btnSenderApply.setOnClickListener {
            senderQuery = etSender.text.toString().trim().ifBlank { null }
            applyFilter()
        }

        // App spinner — populated from DB
        val spinnerApp = view.findViewById<Spinner>(R.id.spinnerAppFilter)
        vm.availableApps.observe(viewLifecycleOwner) { apps ->
            val labels = listOf("All apps") + apps.map { it.appName }
            val pkgs   = listOf<String?>(null) + apps.map { it.packageName }
            spinnerApp.adapter = ArrayAdapter(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, labels)
            spinnerApp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p: AdapterView<*>?) {}
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    selectedApp = pkgs[pos]
                    applyFilter()
                }
            }
        }

        // Type spinner — populated from DB
        val spinnerType = view.findViewById<Spinner>(R.id.spinnerTypeFilter)
        vm.availableTypes.observe(viewLifecycleOwner) { types ->
            val labels = listOf("All types") + types.map { it.notificationType.replace("_"," ") }
            val values = listOf<String?>(null) + types.map { it.notificationType }
            spinnerType.adapter = ArrayAdapter(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, labels)
            spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p: AdapterView<*>?) {}
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    selectedType = values[pos]
                    applyFilter()
                }
            }
        }

        // Days spinner
        val spinnerDays = view.findViewById<Spinner>(R.id.spinnerExplorerDays)
        val dayLabels = arrayOf("7 days","30 days","90 days","All time")
        val dayValues = intArrayOf(7, 30, 90, 3650)
        spinnerDays.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, dayLabels)
        spinnerDays.setSelection(1)
        spinnerDays.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                sinceDays = dayValues[pos]
                applyFilter()
            }
        }

        // Clear filters button
        view.findViewById<View>(R.id.btnClearFilters).setOnClickListener {
            selectedApp = null; selectedType = null; textQuery = null; senderQuery = null
            etSender.setText("")
            searchView.setQuery("", false)
            spinnerApp.setSelection(0)
            spinnerType.setSelection(0)
            applyFilter()
        }

        // Observe results
        vm.explorerRows.observe(viewLifecycleOwner) { rows ->
            adapter.submitList(rows)
            view.findViewById<TextView>(R.id.tvExplorerCount).text =
                "${rows.size} results"
        }

        // Load filter options from DB
        vm.loadFilterOptions()
        applyFilter()
    }

    private fun applyFilter(loadMore: Boolean = false) {
        val filter = ExplorerFilter(
            appPackage = selectedApp,
            notificationType = selectedType,
            sender = senderQuery,
            textQuery = textQuery,
            sinceDays = sinceDays
        )
        vm.loadExplorer(filter, loadMore)
    }
}

// ── Adapter (table rows) ──────────────────────────────────────────────────────

class ExplorerAdapter : RecyclerView.Adapter<ExplorerAdapter.VH>() {

    private var items: List<ExplorerRow> = emptyList()
    private val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    fun submitList(list: List<ExplorerRow>) { items = list; notifyDataSetChanged() }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_explorer_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(items[position], fmt)

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTime   = view.findViewById<TextView>(R.id.colTime)
        private val tvApp    = view.findViewById<TextView>(R.id.colApp)
        private val tvSender = view.findViewById<TextView>(R.id.colSender)
        private val tvMsg    = view.findViewById<TextView>(R.id.colMessage)
        private val tvType   = view.findViewById<TextView>(R.id.colType)

        fun bind(row: ExplorerRow, fmt: SimpleDateFormat) {
            tvTime.text   = fmt.format(Date(row.postTime))
            tvApp.text    = row.appName
            tvSender.text = row.sender ?: "—"
            tvMsg.text    = row.text?.take(80) ?: "—"
            tvType.text   = row.notificationType.replace("_", "\n")

            // Color-code type pill
            val typeColor = when {
                row.notificationType.contains("MESSAGE") -> 0xFF1565C0.toInt()
                row.notificationType.contains("REACT")   -> 0xFFE65100.toInt()
                row.notificationType.contains("CALL")    -> 0xFF2E7D32.toInt()
                row.notificationType.contains("PHOTO") ||
                row.notificationType.contains("VIDEO") ||
                row.notificationType.contains("REEL")   -> 0xFF6A1B9A.toInt()
                row.notificationType.contains("LIKE") ||
                row.notificationType.contains("POST")   -> 0xFFAD1457.toInt()
                else                                     -> 0xFF37474F.toInt()
            }
            tvType.setBackgroundColor(typeColor)
        }
    }
}
