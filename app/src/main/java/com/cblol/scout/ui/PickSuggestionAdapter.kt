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
 * **Card de ajuda final**: após os N cards de sugestão, é exibido um card
 * extra com "?" que abre o mesmo dialog de composições do botão "?" do topo
 * ([onHelpClick]). Isso dá ao treinador acesso rápido às composições
 * recomendadas sem precisar ir até o topo da tela. O card de ajuda só aparece
 * quando há ao menos uma sugestão (lista não vazia).
 *
 * **Comportamento de toque (sugestão)**: tocar num card APENAS seleciona o
 * campeão na grade — não confirma automaticamente. Isso permite que o treinador
 * veja a seleção destacada e tenha a opção de:
 *  - Clicar em CONFIRMAR para concluir o pick sugerido
 *  - Escolher outro campeão da lista padrão (a seleção muda)
 *  - Usar o botão LIMPAR para voltar à escolha livre
 *
 * SOLID:
 * - **SRP**: cada ViewHolder só binda dados — decisões de cor/label moram em
 *   [badgeColorRes] e [badgeLabelRes] (mapeamentos declarativos).
 * - **OCP**: adicionar um novo tipo de motivo é só somar um par nos mapas;
 *   o card de ajuda é um viewType separado que não interfere nos demais.
 *
 * @param onClick callback de toque num card de sugestão (seleciona o campeão)
 * @param onHelpClick callback de toque no card de ajuda "?" (abre o dialog de comps)
 */
class PickSuggestionAdapter(
    private val onClick: (Champion) -> Unit,
    private val onHelpClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<PickSuggestionEngine.Suggestion>()

    fun submit(newItems: List<PickSuggestionEngine.Suggestion>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /**
     * Conta os cards de sugestão + 1 card de ajuda no fim (somente quando há
     * ao menos uma sugestão — sem sugestões, a faixa inteira fica escondida
     * pela Activity, então o card de ajuda também não deve aparecer).
     */
    override fun getItemCount(): Int = if (items.isEmpty()) 0 else items.size + 1

    override fun getItemViewType(position: Int): Int =
        if (position < items.size) VIEW_TYPE_SUGGESTION else VIEW_TYPE_HELP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HELP) {
            HelpVH(inflater.inflate(R.layout.item_pick_suggestion_help, parent, false))
        } else {
            SuggestionVH(inflater.inflate(R.layout.item_pick_suggestion, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HelpVH -> holder.itemView.setOnClickListener { onHelpClick() }
            is SuggestionVH -> bindSuggestion(holder, items[position])
        }
    }

    private fun bindSuggestion(holder: SuggestionVH, s: PickSuggestionEngine.Suggestion) {
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

    class SuggestionVH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView  = view.findViewById(R.id.iv_suggestion_image)
        val name: TextView    = view.findViewById(R.id.tv_suggestion_name)
        val badge: TextView   = view.findViewById(R.id.tv_suggestion_badge)
        val reasons: TextView = view.findViewById(R.id.tv_suggestion_reasons)
    }

    /** ViewHolder do card de ajuda — sem dados a bindar, só o clique. */
    class HelpVH(view: View) : RecyclerView.ViewHolder(view)

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
        private const val BADGE_CORNER_PX   = 8f
        private const val MAX_REASON_LABELS = 2

        private const val VIEW_TYPE_SUGGESTION = 0
        private const val VIEW_TYPE_HELP       = 1
    }
}
