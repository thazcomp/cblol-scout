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
import com.cblol.scout.domain.usecase.IncomingOfferRow
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.SellResult
import com.cblol.scout.ui.viewmodel.IncomingOffersEvent
import com.cblol.scout.ui.viewmodel.IncomingOffersViewModel
import com.cblol.scout.util.TeamColors
import com.google.android.material.button.MaterialButton
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tela de **propostas recebidas** — outros times oferecem dinheiro pelos
 * jogadores do elenco do gerente.
 *
 * **MVVM**: Activity observa [IncomingOffersViewModel.state] e exibe diálogos
 * de resultado quando recebe [IncomingOffersEvent]. Toda a regra de venda /
 * recusa vive nos UseCases que delegam para [com.cblol.scout.game.TransferMarket].
 */
class IncomingOffersActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvMarketStatus: TextView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    private val vm: IncomingOffersViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_offers)

        recycler       = findViewById(R.id.recycler)
        tvEmpty        = findViewById(R.id.tv_empty)
        tvMarketStatus = findViewById(R.id.tv_market_status)
        toolbar        = findViewById(R.id.toolbar)

        setupToolbar()
        recycler.layoutManager = LinearLayoutManager(this)

        vm.state.observe(this) { render(it) }
        vm.events.observe(this) { ev -> ev.consume()?.let(::handleEvent) }
        vm.refresh()
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun setupToolbar() {
        // Cor do header só precisa do teamId — uma leitura única, não invalida
        // o MVVM. A regra "qual time" não muda durante a tela.
        toolbar.setBackgroundColor(TeamColors.forTeam(GameRepository.current().managerTeamId))
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun render(state: com.cblol.scout.domain.usecase.IncomingOffersUiState) {
        // Banner de status do mercado
        tvMarketStatus.text = state.marketStatus
        val bgRes = if (state.marketOpen) R.color.state_success else R.color.color_surface_elevated
        val fgRes = if (state.marketOpen) R.color.pick_ban_bg else R.color.color_on_surface_variant
        tvMarketStatus.setBackgroundColor(ContextCompat.getColor(this, bgRes))
        tvMarketStatus.setTextColor(ContextCompat.getColor(this, fgRes))

        if (state.offers.isEmpty()) {
            recycler.visibility = View.GONE
            tvEmpty.visibility  = View.VISIBLE
            tvEmpty.text = if (state.marketOpen)
                getString(R.string.incoming_offers_empty)
            else
                getString(R.string.incoming_offers_empty_closed)
        } else {
            recycler.visibility = View.VISIBLE
            tvEmpty.visibility  = View.GONE
            recycler.adapter = OfferAdapter(state.offers, ::confirmAccept, ::confirmReject)
        }
    }

    private fun handleEvent(event: IncomingOffersEvent) {
        when (event) {
            is IncomingOffersEvent.AcceptDone -> handleAcceptResult(event.offer, event.result)
        }
    }

    // ── Diálogos de confirmação (UI pura) ────────────────────────────────

    private fun confirmAccept(offer: IncomingTransferOffer) {
        stylizedDialog(this)
            .setTitle(R.string.incoming_offers_accept_confirm_title)
            .setMessage(getString(R.string.incoming_offers_accept_confirm_msg,
                offer.playerName, offer.fromTeamName, "%,d".format(offer.amountBrl)))
            .setPositiveButton(R.string.btn_yes) { _, _ -> vm.acceptOffer(offer) }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun confirmReject(offer: IncomingTransferOffer) {
        val warnMoral = if (offer.motivatedByRequest)
            getString(R.string.incoming_offers_reject_warn_morale)
        else ""
        stylizedDialog(this)
            .setTitle(R.string.incoming_offers_reject_confirm_title)
            .setMessage(getString(R.string.incoming_offers_reject_confirm_msg,
                offer.fromTeamName, offer.playerName) + warnMoral)
            .setPositiveButton(R.string.btn_yes) { _, _ -> vm.rejectOffer(offer) }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun handleAcceptResult(offer: IncomingTransferOffer, result: SellResult) {
        when (result) {
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
            }
            is SellResult.Error -> {
                stylizedDialog(this)
                    .setMessage(result.msg)
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            is SellResult.WarningRequired -> Unit  // acceptIncomingOffer não emite
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, IncomingOffersActivity::class.java)
    }

    // ── Adapter ──────────────────────────────────────────────────────────

    private class OfferAdapter(
        private val items: List<IncomingOfferRow>,
        private val onAccept: (IncomingTransferOffer) -> Unit,
        private val onReject: (IncomingTransferOffer) -> Unit
    ) : RecyclerView.Adapter<OfferAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val accentBar: View       = v.findViewById(R.id.view_accent_bar)
            val tvName: TextView      = v.findViewById(R.id.tv_player_name)
            val tvRole: TextView      = v.findViewById(R.id.tv_player_role)
            val tvRequested: TextView = v.findViewById(R.id.tv_requested_badge)
            val tvSubtitle: TextView  = v.findViewById(R.id.tv_offer_subtitle)
            val tvAmount: TextView    = v.findViewById(R.id.tv_offer_amount)
            val tvVsMarket: TextView  = v.findViewById(R.id.tv_offer_vs_market)
            val btnAccept: MaterialButton = v.findViewById(R.id.btn_accept)
            val btnReject: MaterialButton = v.findViewById(R.id.btn_reject)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_incoming_offer, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val row = items[position]
            val offer = row.offer
            val ctx = h.itemView.context

            h.tvName.text = offer.playerName
            h.tvRole.text = offer.playerRole
            h.tvSubtitle.text = ctx.getString(R.string.incoming_offers_subtitle,
                offer.fromTeamName, formatDate(offer.expiresOn))
            h.tvAmount.text = ctx.getString(R.string.incoming_offers_amount,
                "%,d".format(offer.amountBrl))

            if (offer.motivatedByRequest) {
                h.tvRequested.visibility = View.VISIBLE
                h.accentBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.champion_gold))
            } else {
                h.tvRequested.visibility = View.GONE
                h.accentBar.setBackgroundColor(ContextCompat.getColor(ctx, R.color.color_outline_variant))
            }

            // Comparativo vs preço de mercado — já calculado pelo UseCase
            val pct = row.vsMarketPercent
            if (pct == null) {
                h.tvVsMarket.visibility = View.GONE
            } else {
                h.tvVsMarket.visibility = View.VISIBLE
                h.tvVsMarket.text = when {
                    pct > 0 -> ctx.getString(R.string.incoming_offers_above_market, pct)
                    pct < 0 -> ctx.getString(R.string.incoming_offers_below_market, -pct)
                    else    -> ctx.getString(R.string.incoming_offers_at_market)
                }
            }

            h.btnAccept.setOnClickListener { onAccept(offer) }
            h.btnReject.setOnClickListener { onReject(offer) }
        }

        private fun formatDate(iso: String): String = runCatching {
            LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM"))
        }.getOrDefault(iso)
    }
}
