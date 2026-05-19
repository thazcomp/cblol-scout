package com.cblol.scout.ui

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cblol.scout.R
import com.cblol.scout.data.GameState
import com.cblol.scout.data.MoodEvent
import com.cblol.scout.data.Player
import com.cblol.scout.domain.usecase.MoraleService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Dialog que mostra o estado atual da moral de um jogador e o histórico
 * das últimas mudanças (vitórias, derrotas, transferências, decay etc).
 *
 * Aberto ao tocar no emoji de moral no card do jogador (PlayerAdapter).
 *
 * **SOLID:**
 * - **SRP**: a função `show` orquestra; helpers privados ([bindHeader],
 *   [bindHistory], [appendEvent]) cuidam de cada seção.
 * - **OCP**: novas seções (ex: gráfico de tendência) podem ser adicionadas
 *   sem mexer no resto.
 * - **DIP**: depende só do [GameState] e do modelo de domínio; não acessa
 *   GameRepository diretamente.
 */
object MoodHistoryDialog {

    private val DATE_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /**
     * Exibe o dialog para o jogador especificado.
     *
     * @param activity Activity hospedeira (usada para o tema do dialog)
     * @param state    GameState ativo (lido para obter mood + histórico)
     * @param player   jogador clicado
     */
    fun show(activity: Activity, state: GameState, player: Player) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_mood_history, null)

        val moodValue   = MoraleService.moodOf(state, player.id)
        val moodState   = MoraleService.moodStateOf(state, player.id)
        val history     = MoraleService.historyOf(state, player.id)
        val transferReq = MoraleService.hasRequestedTransfer(state, player.id)

        bindHeader(view, player, moodValue, moodState)
        bindTransferFlag(view, transferReq)
        bindHistory(view, history)

        stylizedDialog(activity)
            .setTitle(R.string.mood_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    // ── Binds ────────────────────────────────────────────────────────────

    private fun bindHeader(
        view: View,
        player: Player,
        moodValue: Int,
        moodState: com.cblol.scout.data.Mood
    ) {
        val ctx = view.context
        val moodLabel = when (moodState) {
            com.cblol.scout.data.Mood.HAPPY   -> ctx.getString(R.string.mood_label_happy)
            com.cblol.scout.data.Mood.NEUTRAL -> ctx.getString(R.string.mood_label_neutral)
            com.cblol.scout.data.Mood.SAD     -> ctx.getString(R.string.mood_label_sad)
        }
        view.findViewById<TextView>(R.id.tv_mood_emoji).text = moodState.emoji
        view.findViewById<TextView>(R.id.tv_player_name).text = player.nome_jogo
        view.findViewById<TextView>(R.id.tv_mood_summary).text =
            ctx.getString(R.string.mood_dialog_summary, moodValue, moodLabel)
        view.findViewById<ProgressBar>(R.id.pb_mood).progress = moodValue
    }

    private fun bindTransferFlag(view: View, requested: Boolean) {
        view.findViewById<View>(R.id.tv_transfer_request).visibility =
            if (requested) View.VISIBLE else View.GONE
    }

    private fun bindHistory(view: View, history: List<MoodEvent>) {
        val container = view.findViewById<LinearLayout>(R.id.ll_history)
        val emptyView = view.findViewById<TextView>(R.id.tv_history_empty)

        container.removeAllViews()
        if (history.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE

        val inflater = (view.context as Activity).layoutInflater
        history.forEach { event ->
            val row = inflater.inflate(R.layout.item_mood_event, container, false)
            appendEvent(row, event)
            container.addView(row)
        }
    }

    /** Preenche uma linha do histórico com cor adequada conforme o delta. */
    private fun appendEvent(row: View, event: MoodEvent) {
        val ctx = row.context
        val tvDelta  = row.findViewById<TextView>(R.id.tv_event_delta)
        val tvReason = row.findViewById<TextView>(R.id.tv_event_reason)
        val tvDate   = row.findViewById<TextView>(R.id.tv_event_date)

        when {
            event.delta > 0 -> {
                tvDelta.text = "+${event.delta}"
                tvDelta.setTextColor(ContextCompat.getColor(ctx, R.color.state_success))
            }
            event.delta < 0 -> {
                tvDelta.text = event.delta.toString()
                tvDelta.setTextColor(ContextCompat.getColor(ctx, R.color.state_danger))
            }
            else -> {
                // Evento sem delta (ex: pedido de transferência) — mostra ícone
                tvDelta.text = ctx.getString(R.string.mood_delta_warning)
                tvDelta.setTextColor(ContextCompat.getColor(ctx, R.color.state_warning))
            }
        }

        tvReason.text = event.reason
        tvDate.text   = runCatching {
            LocalDate.parse(event.date).format(DATE_DISPLAY)
        }.getOrDefault(event.date)
    }
}
