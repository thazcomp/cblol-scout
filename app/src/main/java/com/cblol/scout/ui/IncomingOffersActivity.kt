package com.cblol.scout.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.IncomingTransferOffer
import com.cblol.scout.domain.usecase.IncomingOfferService
import com.cblol.scout.domain.usecase.TransferWindowService
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.SellResult
import com.cblol.scout.game.TransferMarket
import com.cblol.scout.util.TeamColors
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tela de **propostas recebidas**: outros times oferecem dinheiro por jogadores
 * do elenco do gerente durante as janelas de transferência.
 *
 * Para cada oferta, o gerente pode:
 *  - **ACEITAR**: o jogador é vendido para o time proponente pelo valor da
 *    proposta (via [TransferMarket.acceptIncomingOffer]).
 *  - **RECUSAR**: a proposta é descartada (via [TransferMarket.rejectIncomingOffer]);
 *    se o jogador queria sair, isso afeta a moral dele.
 *
 * As ofertas são geradas pelo motor ([IncomingOfferService]) nos ticks diários
 * enquanto o mercado está aberto. Esta Activity apenas as exibe e aplica a
 * resposta do gerente — sem regra de geração (SRP).
 *
 * **SOLID:**
 *  - **SRP**: orquestra a UI; a lógica de venda/recusa vive no TransferMarket +
 *    IncomingOfferService.
 *  - **OCP**: novos tipos de resposta entram como handlers sem mexer no adapter.
 *  - **DIP**: depende de serviços/repos puros e de R.string/R.color.
 */
class IncomingOffersActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvMarketStatus: TextView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_offers)

        recycler       = findViewById(R.id.recycler)
        tvEmpty        = findViewById(R.id.tv_empty)
        tvMarketStatus = findViewById(R.id.tv_market_status)
        toolbar        = findViewById(R.id.toolbar)

        setupToolbar()
        recycler.layoutManager = LinearLayoutManager(this)
        renderState()
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    private fun setupToolbar() {
        val gs = GameRepository.current()
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setBackgroundColor(TeamColors.forTeam(gs.managerTeamId))
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun renderState() {
        val gs = GameRepository.current()

        // Banner de status do mercado (verde aberto / escuro fechado).
        val open = TransferWindowService.isMarketOpen(gs)
        tvMarketStatus.text = TransferWindowService.statusMessage(gs)
        val bgRes = if (open) R.color.state_success else R.color.color_surface_elevated
        val fgRes = if (open) R.color.pick_ban_bg else R.color.color_on_surface_variant
        tvMarketStatus.setBackgroundColor(ContextCompat.getColor(this, bgRes))
        tvMarketStatus.setTextColor(ContextCompat.getColor(this, fgRes))

        val offers = IncomingOfferService.activeOffers(gs)
        if (offers.isEmpty()) {
            recycler.visibility = View.GONE
            tvEmpty.visibility  = View.VISIBLE
            // Mensagem contextual: explica que ofertas só chegam com mercado aberto.
            tvEmpty.text = if (open) {
                getString(R.string.incoming_offers_empty)
            } else {
                getString(R.string.incoming_offers_empty_closed)
            }
        } else {
            recycler.visibility = View.VISIBLE
            tvEmpty.visibility  = View.GONE
            recycler.adapter = OfferAdapter(offers, ::onAccept, ::onReject)
        }
    }

    // ── Ações ────────────────────────────────────────────────────────────

    private fun onAccept(offer: IncomingTransferOffer) {
        stylizedDialog(this)
            .setTitle(R.string.incoming_offers_accept_confirm_title)
            .setMessage(getString(R.string.incoming_offers_accept_confirm_msg,
                offer.playerName, offer.fromTeamName, "%,d".format(offer.amountBrl)))
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                when (val result = TransferMarket.acceptIncomingOffer(applicationContext, offer.id)) {
                    is SellResult.Ok -> {
                        val extra = if (result.terminationFee > 0)
                            getString(R.string.incoming_offers_termination_note,
                                "%,d".format(result.terminationFee))
                        else ""
                        stylizedDialog(this)
                            .setTitle(R.string.incoming_offers_sold_title)
                            .setMessage(getString(R.string.incoming_offers_sold_msg,
                                offer.playerName, result.toTeam, "%,d".format(result.price)) + extra)
                            .setPositiveButton(R.string.btn_ok, null)
                            .show()
                        renderState()
                    }
                    is SellResult.Error -> {
                        stylizedDialog(this)
                            .setMessage(result.msg)
                            .setPositiveButton(R.string.btn_ok, null)
                            .show()
                        renderState()
                    }
                    is SellResult.WarningRequired -> {
                        // acceptIncomingOffer não retorna WarningRequired, mas o
                        // when precisa ser exaustivo. Tratamos como no-op seguro.
                        renderState()
                    }
                }
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun onReject(offer: IncomingTransferOffer) {
        // Recusar jogador que pediu pra sair tem peso emocional — avisamos.
        val warnMoral = if (offer.motivatedByRequest)
            getString(R.string.incoming_offers_reject_warn_morale)
        else ""
        stylizedDialog(this)
            .setTitle(R.string.incoming_offers_reject_confirm_title)
            .setMessage(getString(R.string.incoming_offers_reject_confirm_msg,
                offer.fromTeamName, offer.playerName) + warnMoral)
            .setPositiveButton(R.string.btn_yes) { _, _ ->
                TransferMarket.rejectIncomingOffer(applicationContext, offer.id)
                renderState()
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    companion object {
        fun intent(context: Context) = Intent(context, IncomingOffersActivity::class.java)
    }

    // ── Adapter ──────────────────────────────────────────────────────────

    private class OfferAdapter(
        private val items: List<IncomingTransferOffer>,
        private val onAccept: (IncomingTransferOffer) -> Unit,
        private val onReject: (IncomingTransferOffer) -> Unit
    ) : RecyclerView.Adapter<OfferAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val accentBar: View        = v.findViewById(R.id.view_accent_bar)
            val tvName: TextView       = v.findViewById(R.id.tv_player_name)
            val tvRole: TextView       = v.findViewById(R.id.tv_player_role)
            val tvRequested: TextView  = v.findViewById(R.id.tv_requested_badge)
            val tvSubtitle: TextView   = v.findViewById(R.id.tv_offer_subtitle)
            val tvAmount: TextView     = v.findViewById(R.id.tv_offer_amount)
            val tvVsMarket: TextView   = v.findViewById(R.id.tv_offer_vs_market)
            val btnAccept: MaterialButton = v.findViewById(R.id.btn_accept)
            val btnReject: MaterialButton = v.findViewById(R.id.btn_reject)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_incoming_offer, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val offer = items[position]
            val ctx = h.itemView.context

            h.tvName.text = offer.playerName
            h.tvRole.text = offer.playerRole
            h.tvSubtitle.text = ctx.getString(
                R.string.incoming_offers_subtitle,
                offer.fromTeamName,
                formatDate(offer.expiresOn)
            )
            h.tvAmount.text = ctx.getString(R.string.incoming_offers_amount, "%,d".format(offer.amountBrl))

            // Destaque para jogador que pediu pra sair.
            if (offer.motivatedByRequest) {
                h.tvRequested.visibility = View.VISIBLE
                h.accentBar.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.champion_gold))
            } else {
                h.tvRequested.visibility = View.GONE
                h.accentBar.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.color_outline_variant))
            }

            // Comparação com valor de mercado (se o jogador estiver no elenco).
            val gs = GameRepository.current()
            val player = GameRepository.rosterOf(ctx, gs.managerTeamId)
                .find { it.id == offer.playerId }
            if (player != null) {
                val market = TransferMarket.marketPriceOf(player)
                val diffPct = if (market > 0)
                    ((offer.amountBrl - market) * 100 / market).toInt() else 0
                h.tvVsMarket.text = when {
                    diffPct > 0  -> ctx.getString(R.string.incoming_offers_above_market, diffPct)
                    diffPct < 0  -> ctx.getString(R.string.incoming_offers_below_market, -diffPct)
                    else         -> ctx.getString(R.string.incoming_offers_at_market)
                }
                h.tvVsMarket.visibility = View.VISIBLE
            } else {
                h.tvVsMarket.visibility = View.GONE
            }

            h.btnAccept.setOnClickListener { onAccept(offer) }
            h.btnReject.setOnClickListener { onReject(offer) }
        }

        private fun formatDate(iso: String): String = runCatching {
            LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM"))
        }.getOrDefault(iso)
    }
}
