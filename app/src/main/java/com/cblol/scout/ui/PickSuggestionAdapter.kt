package com.cblol.scout.ui

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cblol.scout.R
import com.cblol.scout.data.Champion
import com.cblol.scout.util.PickSuggestionEngine

/**
 * Adapter horizontal de sugestões de pick exibidas no turno do jogador.
 *
 * Cada item é um card com imagem, nome e um badge colorido indicando o
 * motivo principal da sugestão (MAIN/COMP/COUNTER/SYNERGY/META).
 *
 * SOLID:
 * - **SRP**: cada ViewHolder só binda dados — decisões de cor/label moram em
 *   [badgeColorRes] e [badgeLabelRes] (mapeamentos declarativos).
 * - **OCP**: adicionar um novo tipo de motivo é só somar um par nos mapas.
 */
class PickSuggestionAdapter(
    private val onClick: (Champion) -> Unit
) : RecyclerView.Adapter<PickSuggestionAdapter.VH>() {

    private val items = mutableListOf<PickSuggestionEngine.Suggestion>()

    fun submit(newItems: List<PickSuggestionEngine.Suggestion>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pick_suggestion, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s   = items[position]
        val ctx = holder.itemView.context
        val champ = s.champion

        Glide.with(holder.image).load(champ.imageUrl).into(holder.image)
        holder.name.text = champ.name

        val primary = s.primaryReason
        holder.badge.setText(badgeLabelRes(primary))
        holder.badge.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = BADGE_CORNER_PX
            setColor(ContextCompat.getColor(ctx, badgeColorRes(primary)))
        }

        // Lista até 2 motivos abaixo do badge (concatenados com " · ")
        holder.reasons.text = s.reasons
            .sortedByDescending { it.weight }
            .take(MAX_REASON_LABELS)
            .joinToString(separator = " · ") { it.label }

        holder.itemView.setOnClickListener { onClick(champ) }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView  = view.findViewById(R.id.iv_suggestion_image)
        val name: TextView    = view.findViewById(R.id.tv_suggestion_name)
        val badge: TextView   = view.findViewById(R.id.tv_suggestion_badge)
        val reasons: TextView = view.findViewById(R.id.tv_suggestion_reasons)
    }

    // ── Mapeamento de motivo → recursos visuais (OCP) ───────────────────

    @StringRes
    private fun badgeLabelRes(r: PickSuggestionEngine.Reason): Int = when (r) {
        PickSuggestionEngine.Reason.MAIN        -> R.string.badge_main
        PickSuggestionEngine.Reason.COMPOSITION -> R.string.badge_comp
        PickSuggestionEngine.Reason.COUNTER     -> R.string.badge_counter
        PickSuggestionEngine.Reason.SYNERGY     -> R.string.badge_synergy
        PickSuggestionEngine.Reason.META        -> R.string.badge_meta
    }

    @ColorRes
    private fun badgeColorRes(r: PickSuggestionEngine.Reason): Int = when (r) {
        PickSuggestionEngine.Reason.MAIN        -> R.color.suggestion_badge_main
        PickSuggestionEngine.Reason.COMPOSITION -> R.color.suggestion_badge_comp
        PickSuggestionEngine.Reason.COUNTER     -> R.color.suggestion_badge_counter
        PickSuggestionEngine.Reason.SYNERGY     -> R.color.suggestion_badge_synergy
        PickSuggestionEngine.Reason.META        -> R.color.suggestion_badge_meta
    }

    companion object {
        private const val BADGE_CORNER_PX  = 8f
        private const val MAX_REASON_LABELS = 2
    }
}
