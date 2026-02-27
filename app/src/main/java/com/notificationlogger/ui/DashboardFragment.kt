package com.notificationlogger.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.notificationlogger.MainActivity
import com.notificationlogger.R
import com.notificationlogger.ui.MainViewModel

class DashboardFragment : Fragment() {

    private lateinit var vm: MainViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        val tvTotal = view.findViewById<TextView>(R.id.tvTotalCount)
        val tvTopApps = view.findViewById<TextView>(R.id.tvTopApps)
        val tvBusyHour = view.findViewById<TextView>(R.id.tvBusyHour)
        val tvTopTopic = view.findViewById<TextView>(R.id.tvTopTopic)
        val tvDoomScroll = view.findViewById<TextView>(R.id.tvDoomScroll)

        vm.totalCount.observe(viewLifecycleOwner) { count ->
            tvTotal.text = "$count total notifications logged"
        }

        vm.topApps.observe(viewLifecycleOwner) { apps ->
            if (apps.isEmpty()) {
                tvTopApps.text = "No data yet"
                return@observe
            }
            val top5 = apps.take(5)
            tvTopApps.text = top5.joinToString("\n") { "• ${it.appName}: ${it.count}" }
        }

        vm.hourlyDist.observe(viewLifecycleOwner) { hours ->
            val busiest = hours.maxByOrNull { it.count }
            tvBusyHour.text = if (busiest != null)
                "Busiest hour: ${busiest.hour}:00 (${busiest.count} notifications)"
            else "No hourly data yet"
        }

        vm.topicBreakdown.observe(viewLifecycleOwner) { topics ->
            val top = topics.firstOrNull()
            tvTopTopic.text = if (top != null) "Top topic: ${top.topicGroup} (${top.count})" else "No topic data"
        }

        vm.doomScrollEvents.observe(viewLifecycleOwner) { events ->
            tvDoomScroll.text = if (events.isEmpty())
                "No doomscroll bursts detected (7 days)"
            else {
                val top = events.first()
                "⚠ ${top.burstCount} rapid notifications from ${top.appName} in a 2-min window"
            }
        }

        // Refresh
        vm.loadAnalysis()
        vm.loadLogs(reset = true)
    }

    override fun onResume() {
        super.onResume()
        vm.loadAnalysis()
    }
}
