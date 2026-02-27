package com.notificationlogger.ui.logs

import android.os.Bundle
import android.view.*
import android.widget.SearchView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notificationlogger.R
import com.notificationlogger.data.model.NotificationLog
import com.notificationlogger.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

class LogsFragment : Fragment() {

    private lateinit var vm: MainViewModel
    private lateinit var adapter: LogAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_logs, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        adapter = LogAdapter()
        val rv = view.findViewById<RecyclerView>(R.id.rvLogs)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Infinite scroll
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!recyclerView.canScrollVertically(1)) {
                    vm.loadLogs()
                }
            }
        })

        val searchView = view.findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) vm.searchLogs(query)
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) vm.loadLogs(reset = true)
                return true
            }
        })

        vm.logs.observe(viewLifecycleOwner) { logs ->
            adapter.submitList(logs)
        }

        vm.loadLogs(reset = true)
    }
}

// ─── Adapter ───────────────────────────────────────────────────────────────────

class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

    private val dateFormat = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
    private var items: List<NotificationLog> = emptyList()

    fun submitList(list: List<NotificationLog>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notification_log, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], dateFormat)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvApp   = view.findViewById<TextView>(R.id.tvAppName)
        private val tvEvent = view.findViewById<TextView>(R.id.tvEvent)
        private val tvTime  = view.findViewById<TextView>(R.id.tvTime)
        private val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        private val tvText  = view.findViewById<TextView>(R.id.tvText)
        private val tvTopic = view.findViewById<TextView>(R.id.tvTopic)

        fun bind(log: NotificationLog, fmt: SimpleDateFormat) {
            tvApp.text   = log.appName
            tvEvent.text = log.event
            tvTime.text  = fmt.format(Date(log.postTime))
            tvTitle.text = log.title ?: "(no title)"
            tvText.text  = log.bigText ?: log.text ?: ""
            tvTopic.text = log.topicGroup ?: "Other"

            // Color-code by event
            val color = when (log.event) {
                NotificationLog.EVENT_CLICKED   -> 0xFF4CAF50.toInt()
                NotificationLog.EVENT_DISMISSED -> 0xFFFF9800.toInt()
                NotificationLog.EVENT_APP_CANCEL-> 0xFF9E9E9E.toInt()
                else                            -> 0xFF2196F3.toInt()
            }
            tvEvent.setTextColor(color)
        }
    }
}
