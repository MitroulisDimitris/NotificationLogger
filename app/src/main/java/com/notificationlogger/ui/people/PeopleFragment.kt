package com.notificationlogger.ui.people

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.*
import com.notificationlogger.R
import com.notificationlogger.data.model.SenderStats
import com.notificationlogger.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

class PeopleFragment : Fragment() {

    private lateinit var vm: MainViewModel
    private lateinit var adapter: PeopleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_people, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        adapter = PeopleAdapter { stats ->
            PersonDetailBottomSheet.show(parentFragmentManager, stats)
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvPeople)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        val spinnerDays = view.findViewById<Spinner>(R.id.spinnerPeopleDays)
        val options = arrayOf("Last 7 days", "Last 30 days", "Last 90 days", "All time")
        val dayValues = intArrayOf(7, 30, 90, 3650)
        spinnerDays.adapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, options)
        spinnerDays.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                vm.loadSenderStats(dayValues[pos])
            }
        }

        val searchView = view.findViewById<SearchView>(R.id.searchPeople)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = true
            override fun onQueryTextChange(q: String?): Boolean {
                adapter.filter(q.orEmpty())
                return true
            }
        })

        vm.senderStats.observe(viewLifecycleOwner) { stats ->
            adapter.setData(stats)
            view.findViewById<TextView>(R.id.tvPeopleCount).text =
                "${stats.size} contacts found"
        }

        vm.loadSenderStats(30)
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────

class PeopleAdapter(private val onClick: (SenderStats) -> Unit) :
    RecyclerView.Adapter<PeopleAdapter.VH>() {

    private var allItems: List<SenderStats> = emptyList()
    private var displayItems: List<SenderStats> = emptyList()
    private val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    fun setData(data: List<SenderStats>) {
        allItems = data
        displayItems = data
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        displayItems = if (query.isBlank()) allItems
        else allItems.filter {
            it.sender.contains(query, ignoreCase = true) ||
            it.appName.contains(query, ignoreCase = true)
        }
        notifyDataSetChanged()
    }

    override fun getItemCount() = displayItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_person, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(displayItems[position], fmt, onClick)

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName  = view.findViewById<TextView>(R.id.tvPersonName)
        private val tvApp   = view.findViewById<TextView>(R.id.tvPersonApp)
        private val tvCount = view.findViewById<TextView>(R.id.tvPersonCount)
        private val tvPeak  = view.findViewById<TextView>(R.id.tvPersonPeak)
        private val tvLast  = view.findViewById<TextView>(R.id.tvPersonLast)
        private val tvType  = view.findViewById<TextView>(R.id.tvPersonTopType)
        private val avatar  = view.findViewById<TextView>(R.id.tvAvatar)

        fun bind(s: SenderStats, fmt: SimpleDateFormat, onClick: (SenderStats) -> Unit) {
            tvName.text  = s.sender
            tvApp.text   = s.appName
            tvCount.text = "${s.messageCount}"
            tvPeak.text  = "Peak %02d:00".format(s.peakHour)
            tvLast.text  = fmt.format(Date(s.lastSeen))
            tvType.text  = s.topType.replace("_", " ")
            avatar.text  = s.sender.take(1).uppercase()
            val colors = listOf(
                0xFF1565C0L, 0xFF2E7D32L, 0xFF6A1B9AL,
                0xFFAD1457L, 0xFF00695CL, 0xFFE65100L, 0xFF37474FL
            )
            avatar.setBackgroundColor(
                colors[Math.abs(s.sender.hashCode()) % colors.size].toInt()
            )
            itemView.setOnClickListener { onClick(s) }
        }
    }
}
