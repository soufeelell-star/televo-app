package uk.televo.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import uk.televo.player.databinding.ItemChannelBinding

class ChannelAdapter(
    private val items: List<Xtream.Channel>,
    private val onClick: (Xtream.Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    class VH(val b: ItemChannelBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.b.chName.text = c.name
        holder.b.chNum.text = c.num
        holder.b.chLogo.text = initials(c.name)
        holder.b.root.setOnClickListener { onClick(c) }
    }

    override fun getItemCount(): Int = items.size

    private fun initials(name: String): String {
        val letters = name.filter { it.isLetterOrDigit() }
        return if (letters.length >= 2) letters.substring(0, 2).uppercase() else "TV"
    }
}
