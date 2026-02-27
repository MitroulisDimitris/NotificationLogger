package com.notificationlogger.ui.people

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.notificationlogger.R
import com.notificationlogger.data.model.SenderStats
import com.notificationlogger.ui.MainViewModel
import com.notificationlogger.widget.BarChartView
import java.text.SimpleDateFormat
import java.util.*

class PersonDetailBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "PersonDetail"
        private const val ARG_SENDER  = "sender"
        private const val ARG_PKG     = "pkg"
        private const val ARG_APP     = "app"
        private const val ARG_COUNT   = "count"
        private const val ARG_PEAK    = "peak"
        private const val ARG_LAST    = "last"
        private const val ARG_TOPTYPE = "toptype"

        fun show(fm: FragmentManager, stats: SenderStats) {
            val sheet = PersonDetailBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_SENDER, stats.sender)
                    putString(ARG_PKG,    stats.packageName)
                    putString(ARG_APP,    stats.appName)
                    putInt(ARG_COUNT,     stats.messageCount)
                    putInt(ARG_PEAK,      stats.peakHour)
                    putLong(ARG_LAST,     stats.lastSeen)
                    putString(ARG_TOPTYPE, stats.topType)
                }
            }
            sheet.show(fm, TAG)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_person_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vm = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val sender  = arguments?.getString(ARG_SENDER) ?: return
        val pkg     = arguments?.getString(ARG_PKG) ?: return
        val app     = arguments?.getString(ARG_APP) ?: ""
        val count   = arguments?.getInt(ARG_COUNT) ?: 0
        val peak    = arguments?.getInt(ARG_PEAK) ?: 0
        val lastMs  = arguments?.getLong(ARG_LAST) ?: 0L
        val topType = arguments?.getString(ARG_TOPTYPE) ?: "UNKNOWN"

        val fmt = SimpleDateFormat("MMM d yyyy, HH:mm", Locale.getDefault())

        // Header
        view.findViewById<TextView>(R.id.tvDetailName).text   = sender
        view.findViewById<TextView>(R.id.tvDetailApp).text    = app
        view.findViewById<TextView>(R.id.tvDetailCount).text  = "$count notifications"
        view.findViewById<TextView>(R.id.tvDetailPeak).text   = "Most active at %02d:00".format(peak)
        view.findViewById<TextView>(R.id.tvDetailLast).text   = "Last seen: ${fmt.format(Date(lastMs))}"
        view.findViewById<TextView>(R.id.tvDetailTopType).text = "Top type: ${topType.replace("_"," ")}"

        // Avatar initial
        val avatarView = view.findViewById<TextView>(R.id.tvDetailAvatar)
        avatarView.text = sender.take(1).uppercase()
        val colors = listOf(0xFF1565C0L,0xFF2E7D32L,0xFF6A1B9AL,0xFFAD1457L,0xFF00695CL,0xFFE65100L,0xFF37474FL)
        avatarView.setBackgroundColor(colors[Math.abs(sender.hashCode()) % colors.size].toInt())

        // Hourly chart
        val hourlyChart = view.findViewById<BarChartView>(R.id.chartPersonHourly)
        hourlyChart.orientation = BarChartView.Orientation.VERTICAL
        hourlyChart.barColor = 0xFF00D4FF.toInt()
        hourlyChart.highlightColor = 0xFF00FFB3.toInt()
        hourlyChart.showValues = false

        vm.loadSenderDetail(sender, pkg, 90)
        vm.senderHourly.observe(viewLifecycleOwner) { hours ->
            if (hours.isEmpty()) return@observe
            val peakIdx = hours.indexOfFirst { it.count == hours.maxOf { h -> h.count } }
            val data = hours.map { "%02d".format(it.hour) to it.count.toFloat() }
            hourlyChart.labelTextSize = resources.displayMetrics.density * 9
            hourlyChart.setData(data, peakIdx)
        }

        // Type breakdown chart
        val typeChart = view.findViewById<BarChartView>(R.id.chartPersonTypes)
        typeChart.orientation = BarChartView.Orientation.HORIZONTAL
        typeChart.barColor = 0xFF7B8FFF.toInt()
        typeChart.labelTextSize = resources.displayMetrics.density * 10
        typeChart.valueTextSize = resources.displayMetrics.density * 10

        vm.senderTypes.observe(viewLifecycleOwner) { types ->
            if (types.isEmpty()) return@observe
            val data = types.take(6).map { item ->
                item.topicGroup.replace("_", " ") to item.count.toFloat()
            }
            typeChart.setData(data)
        }
    }
}
