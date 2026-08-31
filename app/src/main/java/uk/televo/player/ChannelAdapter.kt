package uk.televo.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import uk.televo.player.databinding.ItemChannelBinding

class ChannelAdapter(
    private val items: List<Xtream.Channel>,
    private val onClick: (Xtream.Channel) -> Unit,
    private val onToggleFav: (Xtream.Channel) -> Unit = {}
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    private var selected = -1

    class VH(val b: ItemChannelBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        val ctx = holder.b.root.context
        holder.b.chName.text = c.name
        holder.b.chNum.text = c.num
        holder.b.chLogo.text = initials(c.name)

        // logo from the tv-logos collection (matched by name), else the provider's; initials show until it loads
        holder.b.chLogoImg.setImageDrawable(null)
        ImageLoader.loadChannel(ctx, c.name, c.icon, holder.b.chLogoImg)

        holder.b.chFav.visibility = if (Prefs.isFavorite(ctx, c.streamId)) View.VISIBLE else View.GONE
        holder.b.root.isSelected = position == selected

        holder.b.root.setOnClickListener {
            val old = selected
            selected = position
            if (old >= 0) notifyItemChanged(old)
            notifyItemChanged(position)
            onClick(c)
        }
        holder.b.root.setOnLongClickListener {
            onToggleFav(c)
            notifyItemChanged(position)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    private fun initials(name: String): String {
        val letters = name.filter { it.isLetterOrDigit() }
        return if (letters.length >= 2) letters.substring(0, 2).uppercase() else "TV"
    }
}
