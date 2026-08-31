package uk.televo.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import uk.televo.player.databinding.ItemPosterBinding

data class PosterItem(val title: String, val image: String?, val sub: String)

class PosterAdapter(
    private val items: List<PosterItem>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    class VH(val b: ItemPosterBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPosterBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.b.pTitle.text = it.title
        holder.b.pSub.text = it.sub
        ImageLoader.load(it.image, holder.b.poster)
        holder.b.root.setOnClickListener { onClick(position) }
    }

    override fun getItemCount(): Int = items.size
}
