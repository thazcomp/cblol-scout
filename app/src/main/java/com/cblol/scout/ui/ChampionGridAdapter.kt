package com.cblol.scout.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Champion

/**
 * Adapter para a grade de campeões na tela de pick & ban.
 * Estados visuais por item:
 *  - Normal       : borda sutil, imagem colorida
 *  - Selecionado  : borda accent GROSSA (#C9AA71 gold), glow, escala 1.08
 *  - Usado/Banido : alpha 0.25, grayscale overlay, não clicável
 */
class ChampionGridAdapter(
    private val all: List<Champion>,
    private val onSelected: (Champion) -> Unit
) : RecyclerView.Adapter<ChampionGridAdapter.VH>() {

    private var displayed: List<Champion> = all.toList()
    private var selectedId: String? = null
    private val usedIds: MutableSet<String> = mutableSetOf()

    // ── ViewHolder ──────────────────────────────────────────────────────
    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivChampion: ImageView = itemView.findViewById(R.id.iv_champ_icon)
        val tvName: TextView      = itemView.findViewById(R.id.tv_champ_name)
        val viewBorder: View      = itemView.findViewById(R.id.view_selection_border)
        val viewBanned: View      = itemView.findViewById(R.id.view_banned_overlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_champion_pick, parent, false)
        return VH(v)
    }

    override fun getItemCount() = displayed.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val champ = displayed[position]
        val isUsed     = champ.id in usedIds
        val isSelected = champ.id == selectedId

        holder.tvName.text = champ.shortName

        // Imagem — substitua pelo Glide/Coil real
        // Glide.with(holder.itemView).load(champ.imageUrl).centerCrop().into(holder.ivChampion)
        holder.ivChampion.setBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, R.color.champion_slot_bg)
        )

        // ── Estado: USADO/BANIDO ────────────────────────────────────────
        if (isUsed) {
            holder.itemView.alpha = 0.22f
            holder.viewBanned.visibility = View.VISIBLE
            holder.viewBorder.visibility = View.INVISIBLE
            holder.itemView.isClickable = false
            holder.itemView.scaleX = 1f
            holder.itemView.scaleY = 1f
            return
        }

        holder.itemView.alpha = 1f
        holder.viewBanned.visibility = View.GONE
        holder.itemView.isClickable = true

        // ── Estado: SELECIONADO ────────────────────────────────────────
        if (isSelected) {
            holder.viewBorder.visibility = View.VISIBLE
            holder.viewBorder.setBackgroundResource(R.drawable.border_champion_selected)
            holder.itemView.scaleX = 1.08f
            holder.itemView.scaleY = 1.08f
            holder.itemView.elevation = 12f
        } else {
            holder.viewBorder.visibility = View.INVISIBLE
            holder.itemView.scaleX = 1f
            holder.itemView.scaleY = 1f
            holder.itemView.elevation = 2f
        }

        holder.itemView.setOnClickListener {
            if (!isUsed) onSelected(champ)
        }
    }

    // ── API pública ─────────────────────────────────────────────────────
    fun setSelected(id: String) {
        val oldSelected = selectedId
        selectedId = id
        // Rebind apenas os itens afetados
        oldSelected?.let { notifyItemChanged(displayed.indexOfFirst { c -> c.id == it }) }
        notifyItemChanged(displayed.indexOfFirst { c -> c.id == id })
    }

    fun clearSelection() {
        val old = selectedId
        selectedId = null
        old?.let { notifyItemChanged(displayed.indexOfFirst { c -> c.id == it }) }
    }

    fun markUsed(id: String) {
        usedIds.add(id)
        notifyItemChanged(displayed.indexOfFirst { c -> c.id == id })
    }

    fun filter(query: String) {
        displayed = if (query.isBlank()) all
        else all.filter { it.name.contains(query, ignoreCase = true) }
        notifyDataSetChanged()
    }

    fun filterByRole(role: String?) {
        displayed = if (role == null) all
        else all.filter { it.roles.contains(role) }
        notifyDataSetChanged()
    }
}
