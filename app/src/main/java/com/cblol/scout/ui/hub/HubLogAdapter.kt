package com.cblol.scout.ui.hub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.LogEntry

/**
 * Adapter da lista de eventos do log do jogo, exibida no Hub.
 *
 * Extraído da [com.cblol.scout.ui.ManagerHubActivity] porque era um adapter
 * autocontido de ~30 linhas + um mapa estático de tipos → ícones. Mover para
 * fora deixa a Activity focada em coordenação.
 *
 * **OCP**: novos tipos de log entram no mapa [LOG_TYPE_ICON_RES] sem
 * mudar a lógica de render.
 */
internal class HubLogAdapter(
    private val items: List<LogEntry>
) : RecyclerView.Adapter<HubLogAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvIcon: TextView = v.findViewById(R.id.tv_log_icon)
        val tvDate: TextView = v.findViewById(R.id.tv_log_date)
        val tvMsg: TextView  = v.findViewById(R.id.tv_log_msg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val e = items[i]
        val ctx = h.itemView.context
        h.tvIcon.text = ctx.getString(LOG_TYPE_ICON_RES[e.type] ?: R.string.icon_bullet)
        h.tvDate.text = e.date
        h.tvMsg.text  = e.message
    }

    companion object {
        /** Mapeamento declarativo log-type → ícone (OCP: novo tipo é só adicionar par). */
        @StringRes
        private val LOG_TYPE_ICON_RES: Map<String, Int> = mapOf(
            "MATCH"    to R.string.icon_match,
            "TRANSFER" to R.string.icon_transfer,
            "ECONOMY"  to R.string.icon_economy,
            "CONTRACT" to R.string.icon_contract,
            "SQUAD"    to R.string.icon_squad,
            "CAREER"   to R.string.icon_career,
            "ACADEMY"  to R.string.icon_career,
            "BANK"     to R.string.icon_economy,
            "MOOD"     to R.string.icon_squad,
            "SCOUT"    to R.string.icon_transfer
        )
    }
}
