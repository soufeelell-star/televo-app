package uk.televo.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import uk.televo.player.databinding.ItemEpisodeBinding

class EpisodeAdapter(
    private val items: List<Xtream.Episode>,
    private val onClick: (Xtream.Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    class VH(val b: ItemEpisodeBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.b.epNum.text = e.num.ifBlank { (position + 1).toString() }
        holder.b.epTitle.text = e.title
        holder.b.root.setOnClickListener { onClick(e) }
    }

    override fun getItemCount(): Int = items.size
}
