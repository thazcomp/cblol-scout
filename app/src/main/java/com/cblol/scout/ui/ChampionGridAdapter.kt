package com.cblol.scout.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Champion
import com.cblol.scout.data.ChampionTag
import com.cblol.scout.domain.GameConstants

/**
 * Adapter para a grade de campeões na tela de pick & ban.
 *
 * Filtros combinados (AND):
 *  - role   : "TOP" / "JNG" / "MID" / "ADC" / "SUP" / null (ALL)
 *  - tag    : ChampionTag ou null (nenhuma)
 *  - search : string de busca no nome
 *
 * Estados visuais:
 *  - Normal       : imagem colorida, sem borda
 *  - Selecionado  : borda gold 3dp, scaleX/Y 1.08, elevation 12
 *  - Usado/Banido : alpha 0.22, overlay escuro, não clicável
 */
class ChampionGridAdapter(
    private val all: List<Champion>,
    private val onSelected: (Champion) -> Unit
) : RecyclerView.Adapter<ChampionGridAdapter.VH>() {

    private var displayed: List<Champion> = all.toList()
    private var selectedId: String? = null
    private val usedIds: MutableSet<String> = mutableSetOf()

    // Filtros ativos
    private var activeRole: String? = null
    private var activeTag: ChampionTag? = null
    private var activeSearch: String = ""

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
        val champ      = displayed[position]
        val isUsed     = champ.id in usedIds
        val isSelected = champ.id == selectedId

        holder.tvName.text = champ.shortName

        Glide.with(holder.itemView.context)
            .load(champ.imageUrl)
            .transition(DrawableTransitionOptions.withCrossFade(100))
            .apply(RequestOptions().centerCrop()
                .placeholder(R.color.champion_slot_bg)
                .error(R.color.champion_slot_bg))
            .into(holder.ivChampion)

        if (isUsed) {
            holder.itemView.alpha    = GameConstants.Draft.DISABLED_ALPHA
            holder.viewBanned.visibility = View.VISIBLE
            holder.viewBorder.visibility = View.INVISIBLE
            holder.itemView.isClickable  = false
            holder.itemView.scaleX = 1f
            holder.itemView.scaleY = 1f
            return
        }

        holder.itemView.alpha    = 1f
        holder.viewBanned.visibility = View.GONE
        holder.itemView.isClickable  = true

        if (isSelected) {
            holder.viewBorder.visibility = View.VISIBLE
            holder.viewBorder.setBackgroundResource(R.drawable.border_champion_selected)
            holder.itemView.scaleX    = GameConstants.Draft.SELECTED_SCALE
            holder.itemView.scaleY    = GameConstants.Draft.SELECTED_SCALE
            holder.itemView.elevation = SELECTED_ELEVATION
        } else {
            holder.viewBorder.visibility = View.INVISIBLE
            holder.itemView.scaleX    = 1f
            holder.itemView.scaleY    = 1f
            holder.itemView.elevation = DEFAULT_ELEVATION
        }

        holder.itemView.setOnClickListener { onSelected(champ) }
    }

    // ── API de filtros ──────────────────────────────────────────────────

    fun filterByRole(role: String?) {
        activeRole = role
        applyFilters()
    }

    fun filterByTag(tag: ChampionTag?) {
        activeTag = tag
        applyFilters()
    }

    fun filter(query: String) {
        activeSearch = query
        applyFilters()
    }

    private fun applyFilters() {
        displayed = all.filter { champ ->
            val roleOk   = activeRole == null || activeRole in champ.roles
            val tagOk    = activeTag  == null || champ.hasTag(activeTag!!)
            val searchOk = activeSearch.isBlank() ||
                champ.name.contains(activeSearch, ignoreCase = true)
            roleOk && tagOk && searchOk
        }
        notifyDataSetChanged()
    }

    // ── API de seleção/uso ──────────────────────────────────────────────

    fun setSelected(id: String) {
        val old = selectedId
        selectedId = id
        old?.let { notifyItemChanged(displayed.indexOfFirst { c -> c.id == it }) }
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

    companion object {
        private const val SELECTED_ELEVATION = 12f
        private const val DEFAULT_ELEVATION  = 2f
    }
}
