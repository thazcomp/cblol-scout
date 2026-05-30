package com.cblol.scout.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.domain.usecase.RecentHistoryAggregator
import com.cblol.scout.domain.usecase.RecentHistoryCategory
import com.cblol.scout.domain.usecase.RecentHistoryEvent
import org.koin.core.context.GlobalContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Dialog que mostra o **histórico recente completo** da carreira, agregado
 * de todos os subsistemas (partidas, transferências, patrocínios, banco,
 * academia, scouting, treinos, eventos fora de jogo, moral, química, notícias).
 *
 * **Diferente do [MoodHistoryDialog]** (que é por jogador, só de moral),
 * este é um histórico GLOBAL da carreira. Acessado pelo card "📜 Histórico"
 * no Hub ou pelo botão "Ver tudo →" abaixo do mini-log.
 *
 * **UI:**
 *  - Header com título + botão "Limpar filtros" (visível quando há filtro).
 *  - Linha horizontal rolável de chips de filtro (um por categoria), com
 *    contagem entre parênteses.
 *  - Lista vertical com scroll, sem limite de itens.
 *
 * **Filtros:** vazio = mostra tudo. Cada toque em um chip liga/desliga aquela
 * categoria. Quando >0 categorias estão ligadas, só essas aparecem.
 *
 * **Implementação:** dialog gerencia seu próprio estado de filtros sem
 * ViewModel (mais simples e isolado). Recarrega ao avançar dia/voltar de
 * tela é responsabilidade da Activity que abriu (basta fechar e reabrir).
 *
 * **SOLID:**
 *  - **SRP**: orquestra a exibição. Agregação vive no UseCase.
 *  - **OCP**: nova categoria entra no enum + mapa de cor — UI itera o enum.
 *  - **DIP**: depende do [RecentHistoryAggregator] (Koin).
 */
object RecentHistoryDialog {

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM")

    fun show(activity: Activity) {
        val aggregator: RecentHistoryAggregator =
            GlobalContext.get().get()

        val view = activity.layoutInflater.inflate(R.layout.dialog_recent_history, null)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler_history)
        val emptyView = view.findViewById<TextView>(R.id.tv_empty)
        val chipsContainer = view.findViewById<LinearLayout>(R.id.ll_filter_chips)
        val tvClearFilters = view.findViewById<TextView>(R.id.tv_clear_filters)

        recycler.layoutManager = LinearLayoutManager(activity)
        val adapter = HistoryAdapter()
        recycler.adapter = adapter

        // Estado local de filtros (vazio = mostrar tudo).
        var activeFilters = setOf<RecentHistoryCategory>()

        fun render() {
            val all = aggregator()  // re-roda cada toggle; agregação é leve
            val counts = all.groupingBy { it.category }.eachCount()
            val filtered = if (activeFilters.isEmpty()) all
                           else all.filter { it.category in activeFilters }

            adapter.submit(filtered)
            emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
            tvClearFilters.visibility = if (activeFilters.isEmpty()) View.GONE else View.VISIBLE

            renderChips(activity, chipsContainer, counts, activeFilters) { cat ->
                activeFilters = if (cat in activeFilters) activeFilters - cat
                                else activeFilters + cat
                render()
            }
        }

        tvClearFilters.setOnClickListener {
            activeFilters = emptySet()
            render()
        }

        render()

        stylizedDialog(activity)
            .setView(view)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    // ── Chips de filtro ──────────────────────────────────────────────────

    /**
     * Constrói os chips dinamicamente. Itera todas as categorias do enum e
     * mostra cada uma com a contagem (0 se a carreira ainda não gerou eventos
     * daquela categoria — assim o usuário vê a lista completa de opções).
     */
    private fun renderChips(
        activity: Activity,
        container: LinearLayout,
        counts: Map<RecentHistoryCategory, Int>,
        activeFilters: Set<RecentHistoryCategory>,
        onToggle: (RecentHistoryCategory) -> Unit
    ) {
        container.removeAllViews()
        RecentHistoryCategory.values().forEach { category ->
            val count = counts[category] ?: 0
            // Categorias sem nenhum evento ficam escondidas pra não poluir.
            if (count == 0) return@forEach

            val isActive = category in activeFilters
            val chip = createChip(activity, category, count, isActive)
            chip.setOnClickListener { onToggle(category) }
            container.addView(chip)
        }
    }

    private fun createChip(
        activity: Activity,
        category: RecentHistoryCategory,
        count: Int,
        active: Boolean
    ): TextView {
        val ctx = activity
        val tv = TextView(ctx)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = dp(ctx, 6)
        tv.layoutParams = params

        val padH = dp(ctx, 12)
        val padV = dp(ctx, 6)
        tv.setPadding(padH, padV, padH, padV)
        tv.text = ctx.getString(
            R.string.recent_history_filter_chip_with_count,
            category.emoji, category.label, count
        )
        tv.textSize = 11f

        val accentColor = ContextCompat.getColor(ctx, categoryColor(category))

        if (active) {
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.pick_ban_bg))
            tv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(ctx, 14).toFloat()
                setColor(accentColor)
            }
        } else {
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.color_on_surface))
            tv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(ctx, 14).toFloat()
                setColor(ContextCompat.getColor(ctx, R.color.color_surface_elevated))
                setStroke(dp(ctx, 1), accentColor)
            }
        }

        return tv
    }

    // ── Adapter da lista ─────────────────────────────────────────────────

    private class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.VH>() {

        private var items: List<RecentHistoryEvent> = emptyList()

        fun submit(newItems: List<RecentHistoryEvent>) {
            items = newItems
            notifyDataSetChanged()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val bar: View          = v.findViewById(R.id.view_history_bar)
            val tvEmoji: TextView  = v.findViewById(R.id.tv_history_emoji)
            val tvTitle: TextView  = v.findViewById(R.id.tv_history_title)
            val tvSub: TextView    = v.findViewById(R.id.tv_history_subtitle)
            val tvDate: TextView   = v.findViewById(R.id.tv_history_date)
            val tvDelta: TextView  = v.findViewById(R.id.tv_history_delta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recent_history, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val ev = items[position]
            val ctx = h.itemView.context

            h.tvEmoji.text = ev.category.emoji
            h.tvTitle.text = ev.title
            h.tvSub.text = ev.subtitle.orEmpty()
            h.tvSub.visibility = if (ev.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE

            h.bar.setBackgroundColor(
                ContextCompat.getColor(ctx, categoryColor(ev.category))
            )

            h.tvDate.text = runCatching {
                LocalDate.parse(ev.date).format(DATE_FORMAT)
            }.getOrDefault(ev.date)

            // Chip de delta para eventos com semântica de ganho/perda
            val (deltaText, deltaColorRes) = deltaFor(ev)
            if (deltaText == null) {
                h.tvDelta.visibility = View.GONE
            } else {
                h.tvDelta.visibility = View.VISIBLE
                h.tvDelta.text = deltaText
                h.tvDelta.setTextColor(ContextCompat.getColor(ctx, deltaColorRes))
            }
        }

        /**
         * Calcula o texto + cor do "chip de delta" lateral. Só Mood e Bond
         * têm delta numérico hoje; outras subclasses retornam null para o
         * chip ficar oculto.
         */
        private fun deltaFor(ev: RecentHistoryEvent): Pair<String?, Int> = when (ev) {
            is RecentHistoryEvent.Mood -> labelForDelta(ev.delta)
            is RecentHistoryEvent.Bond -> labelForDelta(ev.delta)
            else -> null to R.color.color_on_surface_variant
        }

        private fun labelForDelta(delta: Int): Pair<String?, Int> = when {
            delta > 0 -> "+$delta" to R.color.state_success
            delta < 0 -> "$delta"  to R.color.state_danger
            else      -> "!"        to R.color.state_warning
        }
    }

    // ── Mapeamento categoria → cor ──────────────────────────────────────

    @ColorRes
    private fun categoryColor(category: RecentHistoryCategory): Int = when (category) {
        RecentHistoryCategory.MATCH     -> R.color.hist_cat_match
        RecentHistoryCategory.TRANSFER  -> R.color.hist_cat_transfer
        RecentHistoryCategory.SPONSOR   -> R.color.hist_cat_sponsor
        RecentHistoryCategory.FINANCE   -> R.color.hist_cat_finance
        RecentHistoryCategory.ACADEMY   -> R.color.hist_cat_academy
        RecentHistoryCategory.SCOUTING  -> R.color.hist_cat_scouting
        RecentHistoryCategory.TRAINING  -> R.color.hist_cat_training
        RecentHistoryCategory.OFF_MATCH -> R.color.hist_cat_off_match
        RecentHistoryCategory.MOOD      -> R.color.hist_cat_mood
        RecentHistoryCategory.BOND      -> R.color.hist_cat_bond
        RecentHistoryCategory.NEWS      -> R.color.hist_cat_news
        RecentHistoryCategory.COACH     -> R.color.hist_cat_coach
        RecentHistoryCategory.SYSTEM    -> R.color.hist_cat_system
    }

    private fun dp(ctx: android.content.Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()
}
