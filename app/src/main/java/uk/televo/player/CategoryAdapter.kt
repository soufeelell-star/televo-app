package uk.televo.player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import uk.televo.player.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val items: List<Xtream.Category>,
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var selected = 0

    class VH(val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.b.catName.text = c.name
        holder.b.catCount.text = c.count.toString()
        holder.b.root.isSelected = position == selected
        holder.b.root.setOnClickListener {
            val old = selected
            selected = position
            notifyItemChanged(old)
            notifyItemChanged(position)
            onSelect(position)
        }
    }

    override fun getItemCount(): Int = items.size
}
