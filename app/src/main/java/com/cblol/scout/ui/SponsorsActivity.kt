package com.cblol.scout.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import com.cblol.scout.data.Sponsor
import com.cblol.scout.data.SponsorContract
import com.cblol.scout.data.SponsorOffer
import com.cblol.scout.data.SponsorTier
import com.cblol.scout.databinding.ActivitySponsorsBinding
import com.cblol.scout.domain.usecase.AcceptSponsorOfferUseCase
import com.cblol.scout.domain.usecase.SponsorService
import com.cblol.scout.domain.usecase.SponsorsUiState
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.viewmodel.SponsorsEvent
import com.cblol.scout.ui.viewmodel.SponsorsViewModel
import com.cblol.scout.util.TeamColors
import com.google.android.material.tabs.TabLayout
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Tela de gerenciamento de patrocínios.
 *
 * **MVVM**: a Activity observa [SponsorsViewModel.state] (contratos ativos +
 * ofertas + total semanal) e responde a [SponsorsEvent] para diálogos de
 * resultado. Toda a regra (aceitar/recusar/cancelar) vive no SponsorService
 * via UseCases — a Activity só dispara as confirmações.
 */
class SponsorsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySponsorsBinding
    private val vm: SponsorsViewModel by viewModel()
    private var currentTab = TAB_ACTIVE
    private var lastState: SponsorsUiState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySponsorsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        binding.recycler.layoutManager = LinearLayoutManager(this)

        vm.state.observe(this) { state ->
            lastState = state
            render(state)
        }
        vm.events.observe(this) { ev -> ev.consume()?.let(::handleEvent) }
        vm.refresh()
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun setupToolbar() {
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(GameRepository.current().managerTeamId))
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.sponsors_tab_active))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.sponsors_tab_market))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                lastState?.let(::render)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // ── Render ───────────────────────────────────────────────────────────

    private fun render(state: SponsorsUiState) {
        binding.tvTotalWeekly.text = getString(R.string.sponsors_weekly_format,
            "%,d".format(state.totalWeeklyIncome))
        binding.tvActiveCount.text = getString(R.string.sponsors_active_count_format,
            state.activeCount, state.maxActive)

        when (currentTab) {
            TAB_ACTIVE -> renderActive(state.activeContracts)
            TAB_MARKET -> renderMarket(state.availableOffers)
        }
    }

    private fun renderActive(contracts: List<SponsorContract>) {
        if (contracts.isEmpty()) {
            binding.recycler.visibility = View.GONE
            binding.tvEmpty.visibility  = View.VISIBLE
            binding.tvEmpty.setText(R.string.sponsors_empty_active)
        } else {
            binding.recycler.visibility = View.VISIBLE
            binding.tvEmpty.visibility  = View.GONE
            binding.recycler.adapter = SponsorAdapter(
                items = contracts.map { SponsorItem.Active(it) },
                onPrimary = ::handlePrimary,
                onSecondary = ::handleSecondary
            )
        }
    }

    private fun renderMarket(offers: List<SponsorOffer>) {
        if (offers.isEmpty()) {
            binding.recycler.visibility = View.GONE
            binding.tvEmpty.visibility  = View.VISIBLE
            binding.tvEmpty.setText(R.string.sponsors_empty_market)
        } else {
            binding.recycler.visibility = View.VISIBLE
            binding.tvEmpty.visibility  = View.GONE
            binding.recycler.adapter = SponsorAdapter(
                items = offers.map { SponsorItem.OfferItem(it) },
                onPrimary = ::handlePrimary,
                onSecondary = ::handleSecondary
            )
        }
    }

    // ── Ações ────────────────────────────────────────────────────────────

    private fun handlePrimary(item: SponsorItem) {
        when (item) {
            is SponsorItem.OfferItem -> vm.accept(item.offer)
            is SponsorItem.Active    -> showDetailsDialog(item.contract.sponsor)
        }
    }

    private fun handleSecondary(item: SponsorItem) {
        when (item) {
            is SponsorItem.OfferItem -> vm.reject(item.offer)
            is SponsorItem.Active    -> confirmCancel(item.contract)
        }
    }

    private fun confirmCancel(contract: SponsorContract) {
        val penalty = contract.sponsor.weeklyAmount * SponsorService.CANCELLATION_FEE_WEEKS
        stylizedDialog(this)
            .setTitle(R.string.sponsors_cancel_confirm_title)
            .setMessage(getString(R.string.sponsors_cancel_confirm_msg,
                contract.sponsor.name, "%,d".format(penalty)))
            .setPositiveButton(R.string.btn_yes) { _, _ -> vm.cancel(contract) }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun showDetailsDialog(sponsor: Sponsor) {
        val body = getString(R.string.sponsors_details_body,
            "${sponsor.category.emoji} ${sponsor.category.label}",
            "${sponsor.tier.emoji} ${sponsor.tier.label}",
            sponsor.durationWeeks,
            "%,d".format(sponsor.weeklyAmount),
            sponsor.description)
        stylizedDialog(this)
            .setTitle(getString(R.string.sponsors_details_dialog_title, sponsor.name))
            .setMessage(body)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    private fun handleEvent(event: SponsorsEvent) {
        when (event) {
            is SponsorsEvent.AcceptDone -> handleAcceptResult(event.offer, event.result)
            is SponsorsEvent.CancelDone -> {
                stylizedDialog(this)
                    .setTitle(R.string.sponsors_cancel_confirm_title)
                    .setMessage(getString(R.string.sponsors_cancelled_msg,
                        "%,d".format(event.result.penaltyPaid)))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
        }
    }

    private fun handleAcceptResult(offer: SponsorOffer, result: AcceptSponsorOfferUseCase.Result) {
        when (result) {
            is AcceptSponsorOfferUseCase.Result.Ok -> {
                stylizedDialog(this)
                    .setTitle(R.string.sponsors_accepted_title)
                    .setMessage(getString(R.string.sponsors_accepted_msg,
                        result.sponsor.name,
                        "%,d".format(result.sponsor.weeklyAmount),
                        result.sponsor.durationWeeks))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            AcceptSponsorOfferUseCase.Result.LimitReached -> {
                stylizedDialog(this)
                    .setTitle(R.string.sponsors_limit_reached_title)
                    .setMessage(getString(R.string.sponsors_limit_reached_msg,
                        SponsorService.MAX_ACTIVE_SPONSORS))
                    .setPositiveButton(R.string.btn_ok, null)
                    .show()
            }
            AcceptSponsorOfferUseCase.Result.NotAvailable -> Unit
        }
    }

    companion object {
        private const val TAB_ACTIVE = 0
        private const val TAB_MARKET = 1
    }

    // ── Modelos de UI ────────────────────────────────────────────────────

    private sealed class SponsorItem {
        data class Active(val contract: SponsorContract) : SponsorItem()
        data class OfferItem(val offer: SponsorOffer) : SponsorItem()
    }

    // ── Adapter ──────────────────────────────────────────────────────────

    private class SponsorAdapter(
        private val items: List<SponsorItem>,
        private val onPrimary: (SponsorItem) -> Unit,
        private val onSecondary: (SponsorItem) -> Unit
    ) : RecyclerView.Adapter<SponsorAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tierBar: View          = v.findViewById(R.id.view_tier_bar)
            val tvCategoryIcon: TextView = v.findViewById(R.id.tv_category_icon)
            val tvName: TextView       = v.findViewById(R.id.tv_sponsor_name)
            val tvTierBadge: TextView  = v.findViewById(R.id.tv_tier_badge)
            val tvSubtitle: TextView   = v.findViewById(R.id.tv_subtitle)
            val tvWeekly: TextView     = v.findViewById(R.id.tv_weekly_value)
            val tvDescription: TextView = v.findViewById(R.id.tv_description)
            val llBonuses: View        = v.findViewById(R.id.ll_bonuses)
            val tvBonusChip: TextView  = v.findViewById(R.id.tv_bonus_chip)
            val tvPenaltyChip: TextView = v.findViewById(R.id.tv_penalty_chip)
            val btnPrimary: com.google.android.material.button.MaterialButton =
                v.findViewById(R.id.btn_primary)
            val btnSecondary: com.google.android.material.button.MaterialButton =
                v.findViewById(R.id.btn_secondary)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_sponsor, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val item = items[position]
            val ctx  = h.itemView.context
            val sponsor = when (item) {
                is SponsorItem.Active    -> item.contract.sponsor
                is SponsorItem.OfferItem -> item.offer.sponsor
            }

            h.tvCategoryIcon.text = sponsor.category.emoji
            h.tvName.text         = sponsor.name
            bindTierBadge(h, sponsor.tier)
            h.tvWeekly.text = ctx.getString(R.string.sponsors_weekly_format,
                "%,d".format(sponsor.weeklyAmount))

            bindBonuses(h, sponsor, ctx)

            when (item) {
                is SponsorItem.Active -> {
                    val remaining = remainingWeeks(item.contract)
                    h.tvSubtitle.text = ctx.getString(R.string.sponsors_subtitle_active,
                        sponsor.category.label, remaining)
                    h.tvDescription.visibility = View.GONE
                    h.btnPrimary.text = ctx.getString(R.string.sponsors_action_details)
                    h.btnSecondary.visibility = View.VISIBLE
                    h.btnSecondary.text = ctx.getString(R.string.sponsors_action_cancel)
                }
                is SponsorItem.OfferItem -> {
                    val expiresDisplay = runCatching {
                        LocalDate.parse(item.offer.expiresOn)
                            .format(DateTimeFormatter.ofPattern("dd/MM"))
                    }.getOrDefault(item.offer.expiresOn)
                    h.tvSubtitle.text = ctx.getString(R.string.sponsors_subtitle_offer,
                        sponsor.category.label, expiresDisplay)
                    h.tvDescription.visibility = if (sponsor.description.isNotBlank())
                        View.VISIBLE else View.GONE
                    h.tvDescription.text = sponsor.description
                    h.btnPrimary.text = ctx.getString(R.string.sponsors_action_accept)
                    h.btnSecondary.visibility = View.VISIBLE
                    h.btnSecondary.text = ctx.getString(R.string.sponsors_action_reject)
                }
            }

            h.btnPrimary.setOnClickListener   { onPrimary(item) }
            h.btnSecondary.setOnClickListener { onSecondary(item) }
        }

        private fun bindTierBadge(h: VH, tier: SponsorTier) {
            val ctx = h.itemView.context
            h.tvTierBadge.text = "${tier.emoji} ${tier.label}"
            val color = tierColor(ctx, tier)
            h.tierBar.setBackgroundColor(color)
            h.tvTierBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f
                setColor(color)
            }
        }

        private fun bindBonuses(h: VH, sponsor: Sponsor, ctx: android.content.Context) {
            val hasBonus   = sponsor.bonusPerWin   > 0
            val hasPenalty = sponsor.penaltyPerLoss > 0
            if (!hasBonus && !hasPenalty) {
                h.llBonuses.visibility = View.GONE
                return
            }
            h.llBonuses.visibility = View.VISIBLE
            if (hasBonus) {
                h.tvBonusChip.visibility = View.VISIBLE
                h.tvBonusChip.text = ctx.getString(R.string.sponsors_bonus_chip,
                    "%,d".format(sponsor.bonusPerWin))
            } else {
                h.tvBonusChip.visibility = View.GONE
            }
            if (hasPenalty) {
                h.tvPenaltyChip.visibility = View.VISIBLE
                h.tvPenaltyChip.text = ctx.getString(R.string.sponsors_penalty_chip,
                    "%,d".format(sponsor.penaltyPerLoss))
            } else {
                h.tvPenaltyChip.visibility = View.GONE
            }
        }

        private fun tierColor(ctx: android.content.Context, tier: SponsorTier): Int = when (tier) {
            SponsorTier.BRONZE  -> Color.parseColor("#CD7F32")
            SponsorTier.SILVER  -> Color.parseColor("#A0A0A0")
            SponsorTier.GOLD    -> ContextCompat.getColor(ctx, R.color.champion_gold)
            SponsorTier.DIAMOND -> Color.parseColor("#5BD0E8")
        }

        /**
         * Cálculo de semanas restantes feito aqui no adapter por simplicidade
         * — depende da data ATUAL do jogo, que pode mudar com avanços fora desta
         * tela. Manter aqui evita ter que reemitir o state só pra refletir esse
         * número (que muda de 7 em 7 dias).
         */
        private fun remainingWeeks(contract: SponsorContract): Int {
            val today = runCatching {
                LocalDate.parse(GameRepository.current().currentDate)
            }.getOrNull() ?: return contract.sponsor.durationWeeks
            val end = runCatching { LocalDate.parse(contract.endDate) }.getOrNull()
                ?: return contract.sponsor.durationWeeks
            val days = ChronoUnit.DAYS.between(today, end).toInt()
            return ((days + 6) / 7).coerceAtLeast(0)
        }
    }
}
