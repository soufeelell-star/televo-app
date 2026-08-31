package uk.televo.player

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import uk.televo.player.databinding.ItemEpgBinding

class EpgAdapter(private val items: List<Xtream.Epg>) : RecyclerView.Adapter<EpgAdapter.VH>() {

    class VH(val b: ItemEpgBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemEpgBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.b.epgTime.text = e.time
        holder.b.epgDesc.text = e.desc
        if (e.now) {
            holder.b.epgTitle.text = "${e.title}  • ON NOW"
            holder.b.epgTitle.setTextColor(Color.parseColor("#D42CB0"))
        } else {
            holder.b.epgTitle.text = e.title
            holder.b.epgTitle.setTextColor(Color.parseColor("#F4F6FB"))
        }
    }

    override fun getItemCount(): Int = items.size
}
