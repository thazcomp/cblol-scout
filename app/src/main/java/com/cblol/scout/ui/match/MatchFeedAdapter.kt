package com.cblol.scout.ui.match

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.domain.GameConstants

/**
 * Adapter do feed de eventos da simulação ao vivo.
 *
 * Mantém uma fila LIFO de [FeedEntry] (eventos mais recentes no topo) com
 * limite [GameConstants.Simulation.FEED_MAX_ITEMS]. Cada entrada tem ícone +
 * texto + barra de acento colorida.
 *
 * Extraído da [com.cblol.scout.ui.MatchSimulationActivity] para ficar
 * standalone (antes era um `inner class` aninhado). Recebe o RecyclerView
 * para fazer o `scrollToPosition(0)` após cada add — interno ao adapter,
 * Activity não precisa se preocupar.
 *
 * **SOLID:**
 *  - **SRP**: adapter de uma lista de eventos visuais.
 *  - **DIP**: depende só do RecyclerView e dos recursos de layout.
 */
internal class MatchFeedAdapter(
    private val recycler: RecyclerView
) : RecyclerView.Adapter<MatchFeedAdapter.VH>() {

    private val items = mutableListOf<FeedEntry>()

    /** Entrada de uma linha do feed: ícone + texto + cor de acento. */
    data class FeedEntry(val icon: String, val text: String, val accentColor: Int)

    fun add(e: FeedEntry) {
        items.add(0, e)
        if (items.size > GameConstants.Simulation.FEED_MAX_ITEMS) {
            items.removeAt(items.size - 1)
        }
        notifyItemInserted(0)
        recycler.scrollToPosition(0)
    }

    fun clear() {
        val c = items.size
        items.clear()
        notifyItemRangeRemoved(0, c)
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvIcon: TextView = v.findViewById(R.id.tv_feed_icon)
        val tvText: TextView = v.findViewById(R.id.tv_feed_text)
        val viewBar: View = v.findViewById(R.id.view_feed_accent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_match_feed, parent, false)
    )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val e = items[i]
        h.tvIcon.text = e.icon
        h.tvText.text = e.text
        h.viewBar.setBackgroundColor(e.accentColor)
    }
}
