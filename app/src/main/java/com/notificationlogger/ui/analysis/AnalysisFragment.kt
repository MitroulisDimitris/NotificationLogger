package com.notificationlogger.ui.analysis

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.notificationlogger.R
import com.notificationlogger.data.model.DayOfWeekCount
import com.notificationlogger.data.model.HourDayCount
import com.notificationlogger.ui.MainViewModel
import com.notificationlogger.util.CsvExporter
import com.notificationlogger.widget.BarChartView
import java.util.*

class AnalysisFragment : Fragment() {

    private lateinit var vm: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_analysis, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val tvTopApps    = view.findViewById<TextView>(R.id.tvTopAppsAnalysis)
        val tvTopics     = view.findViewById<TextView>(R.id.tvTopics)
        val tvDoom       = view.findViewById<TextView>(R.id.tvDoomScrollDetail)
        val btnExport    = view.findViewById<Button>(R.id.btnExportCsv)
        val spinnerDays  = view.findViewById<Spinner>(R.id.spinnerDays)

        val chartHourly  = view.findViewById<BarChartView>(R.id.chartHourly)
        val chartDayOfWeek = view.findViewById<BarChartView>(R.id.chartDayOfWeek)
        val tvHeatmapHeader = view.findViewById<TextView>(R.id.tvHeatmapHeader)

        val dp = resources.displayMetrics.density

        // Configure hourly bar chart
        chartHourly.orientation    = BarChartView.Orientation.VERTICAL
        chartHourly.barColor       = 0xFF00D4FF.toInt()
        chartHourly.highlightColor = 0xFF00FFB3.toInt()
        chartHourly.showValues     = true
        chartHourly.labelTextSize  = dp * 8

        // Configure day-of-week bar chart
        chartDayOfWeek.orientation    = BarChartView.Orientation.VERTICAL
        chartDayOfWeek.barColor       = 0xFF7B8FFF.toInt()
        chartDayOfWeek.highlightColor = 0xFFFFD740.toInt()
        chartDayOfWeek.showValues     = true
        chartDayOfWeek.labelTextSize  = dp * 9

        // Days selector
        val options   = arrayOf("Last 7 days","Last 14 days","Last 30 days","Last 90 days")
        val dayValues = intArrayOf(7, 14, 30, 90)
        spinnerDays.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, options)
        spinnerDays.setSelection(2)
        spinnerDays.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                vm.loadAnalysis(dayValues[pos])
            }
        }

        vm.topApps.observe(viewLifecycleOwner) { apps ->
            tvTopApps.text = if (apps.isEmpty()) "No data" else
                apps.take(10).mapIndexed { i, a -> "${i+1}. ${a.appName}  ${a.count}" }
                    .joinToString("\n")
        }

        vm.hourlyDist.observe(viewLifecycleOwner) { hours ->
            if (hours.isEmpty()) return@observe
            val peakIdx = hours.indexOfFirst { it.count == hours.maxOf { h -> h.count } }
            val data = (0..23).map { h ->
                val entry = hours.find { it.hour == h }
                "%02d".format(h) to (entry?.count?.toFloat() ?: 0f)
            }
            chartHourly.setData(data, peakIdx)

            val peakHour = hours.maxByOrNull { it.count }
            tvHeatmapHeader.text = if (peakHour != null)
                "🌡️ Hourly Activity  — peak at %02d:00 (%d notifications)".format(
                    peakHour.hour, peakHour.count)
            else "🌡️ Hourly Activity"
        }

        vm.dayOfWeekDist.observe(viewLifecycleOwner) { days ->
            if (days.isEmpty()) return@observe
            val dayNames = arrayOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
            val map = days.associateBy { it.dayOfWeek }
            val peakDay = days.maxByOrNull { it.count }?.dayOfWeek ?: -1
            val data = (0..6).map { d -> dayNames[d] to (map[d]?.count?.toFloat() ?: 0f) }
            val peakIdx = (0..6).indexOfFirst { it == peakDay }
            chartDayOfWeek.setData(data, peakIdx)
        }

        vm.topicBreakdown.observe(viewLifecycleOwner) { topics ->
            tvTopics.text = if (topics.isEmpty()) "No topics" else
                topics.joinToString("\n") { "• ${it.topicGroup}: ${it.count}" }
        }

        vm.doomScrollEvents.observe(viewLifecycleOwner) { events ->
            tvDoom.text = if (events.isEmpty()) "No burst events detected" else
                events.take(10).joinToString("\n") {
                    "⚡ ${it.appName}: ${it.burstCount} notifications in 2 min"
                }
        }

        btnExport.setOnClickListener { vm.loadAllForExport() }

        vm.exportEvent.observe(viewLifecycleOwner) { event ->
            val logs = event.getIfNotHandled() ?: return@observe
            if (logs.isEmpty()) {
                Toast.makeText(requireContext(), "No logs to export", Toast.LENGTH_SHORT).show()
                return@observe
            }
            val intent = CsvExporter.buildShareIntent(requireContext(), logs)
            if (intent != null) startActivity(Intent.createChooser(intent, "Export CSV"))
            else Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show()
        }

        vm.loadAnalysis()
    }
}
