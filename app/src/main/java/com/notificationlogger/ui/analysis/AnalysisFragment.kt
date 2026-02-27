package com.notificationlogger.ui.analysis

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.notificationlogger.R
import com.notificationlogger.data.model.HourlyCount
import com.notificationlogger.ui.MainViewModel
import com.notificationlogger.util.CsvExporter

class AnalysisFragment : Fragment() {

    private lateinit var vm: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_analysis, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val tvTopApps       = view.findViewById<TextView>(R.id.tvTopAppsAnalysis)
        val tvHeatmap       = view.findViewById<TextView>(R.id.tvHeatmap)
        val tvTopics        = view.findViewById<TextView>(R.id.tvTopics)
        val tvDoom          = view.findViewById<TextView>(R.id.tvDoomScrollDetail)
        val btnExport       = view.findViewById<Button>(R.id.btnExportCsv)
        val spinnerDays     = view.findViewById<Spinner>(R.id.spinnerDays)

        // Days selector
        val options = arrayOf("Last 7 days", "Last 14 days", "Last 30 days", "Last 90 days")
        val dayValues = intArrayOf(7, 14, 30, 90)
        spinnerDays.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        spinnerDays.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                vm.loadAnalysis(dayValues[pos])
            }
        }

        vm.topApps.observe(viewLifecycleOwner) { apps ->
            tvTopApps.text = if (apps.isEmpty()) "No data" else
                apps.take(10).mapIndexed { i, a -> "${i+1}. ${a.appName} — ${a.count}" }.joinToString("\n")
        }

        vm.hourlyDist.observe(viewLifecycleOwner) { hours ->
            tvHeatmap.text = buildHeatmap(hours)
        }

        vm.topicBreakdown.observe(viewLifecycleOwner) { topics ->
            tvTopics.text = if (topics.isEmpty()) "No topics" else
                topics.joinToString("\n") { "• ${it.topicGroup}: ${it.count}" }
        }

        vm.doomScrollEvents.observe(viewLifecycleOwner) { events ->
            tvDoom.text = if (events.isEmpty()) "No burst events detected" else
                events.take(10).joinToString("\n") {
                    "⚡ ${it.appName}: ${it.burstCount} notifs in 2 min"
                }
        }

        btnExport.setOnClickListener {
            vm.loadAllForExport()
        }

        vm.exportEvent.observe(viewLifecycleOwner) { event ->
            // getIfNotHandled() returns the list only the FIRST time — null on any
            // subsequent delivery (e.g. screen rotation, fragment resume).
            val logs = event.getIfNotHandled() ?: return@observe
            if (logs.isEmpty()) {
                Toast.makeText(requireContext(), "No logs to export", Toast.LENGTH_SHORT).show()
                return@observe
            }
            val intent = CsvExporter.buildShareIntent(requireContext(), logs)
            if (intent != null) {
                startActivity(Intent.createChooser(intent, "Export CSV"))
            } else {
                Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show()
            }
        }

        vm.loadAnalysis()
    }

    private fun buildHeatmap(hours: List<HourlyCount>): String {
        if (hours.isEmpty()) return "No data"
        val map = hours.associateBy { it.hour }
        val maxCount = hours.maxOf { it.count }.coerceAtLeast(1)
        return buildString {
            appendLine("Hourly notification heatmap:")
            appendLine("(each █ = ~${maxCount / 5 + 1} notifications)\n")
            for (h in 0..23) {
                val count = map[h]?.count ?: 0
                val bars = (count * 20 / maxCount).coerceAtLeast(if (count > 0) 1 else 0)
                val label = "%02d:00".format(h)
                val bar = "█".repeat(bars)
                append("$label  $bar $count\n")
            }
        }
    }
}
