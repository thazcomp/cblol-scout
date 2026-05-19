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
import com.cblol.scout.domain.usecase.SponsorService
import com.cblol.scout.game.GameRepository
import com.cblol.scout.util.TeamColors
import com.google.android.material.tabs.TabLayout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Tela de gerenciamento de patrocínios.
 *
 * Tem duas abas:
 *  - **ATIVOS**: contratos vigentes do time. Para cada um, mostra semanas
 *    restantes, total recebido, e botão CANCELAR (com confirmação de multa).
 *  - **MERCADO**: ofertas disponíveis. Para cada uma, mostra o valor proposto,
 *    duração e botões ACEITAR/RECUSAR. As ofertas têm validade limitada.
 *
 * Header fixo mostra a receita semanal total dos patrocínios ativos e a
 * contagem (ex: "2/4 ativos"), respeitando o limite [SponsorService.MAX_ACTIVE_SPONSORS].
 *
 * **SOLID:**
 *  - **SRP**: Activity orquestra; o [SponsorAdapter] cuida do bind de items
 *    e [SponsorService] cuida da lógica de aceitar/cancelar.
 *  - **OCP**: novos tipos de ação (ex: renegociar valor) viram novos métodos
 *    no adapter sem mexer no resto.
 *  - **DIP**: depende só de [GameRepository] e [SponsorService] (puros).
 */
class SponsorsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySponsorsBinding
    private var currentTab = TAB_ACTIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySponsorsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        binding.recycler.layoutManager = LinearLayoutManager(this)

        // Pode ser que o player abra a tela MUITO cedo (split começando, antes do
        // primeiro ciclo de geração). Tenta gerar agora se já está em data válida.
        SponsorService.generateOffersIfDue(GameRepository.current())
        GameRepository.save(applicationContext)

        renderState()
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    // ── Setup ────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        val gs = GameRepository.current()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(gs.managerTeamId))
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.sponsors_tab_active))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.sponsors_tab_market))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                renderState()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    // ── Render ───────────────────────────────────────────────────────────

    private fun renderState() {
        val gs = GameRepository.current()
        val totalWeekly = SponsorService.totalWeeklyIncomeFromSponsors(gs)
        binding.tvTotalWeekly.text  = getString(R.string.sponsors_weekly_format, "%,d".format(totalWeekly))
        binding.tvActiveCount.text  = getString(R.string.sponsors_active_count_format,
            gs.activeSponsors?.size ?: 0, SponsorService.MAX_ACTIVE_SPONSORS)

        when (currentTab) {
            TAB_ACTIVE -> renderActive(gs.activeSponsors ?: emptyList())
            TAB_MARKET -> renderMarket(gs.availableSponsorOffers ?: emptyList())
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

    // ── Actions ──────────────────────────────────────────────────────────

    private fun handlePrimary(item: SponsorItem) {
        val gs = GameRepository.current()
        when (item) {
            is SponsorItem.OfferItem -> {
                val result = SponsorService.acceptOffer(gs, item.offer.sponsor.id)
                when (result) {
                    SponsorService.AcceptResult.OK -> {
                        GameRepository.save(applicationContext)
                        stylizedDialog(this)
                            .setTitle(R.string.sponsors_accepted_title)
                            .setMessage(getString(R.string.sponsors_accepted_msg,
                                item.offer.sponsor.name,
                                "%,d".format(item.offer.sponsor.weeklyAmount),
                                item.offer.sponsor.durationWeeks))
                            .setPositiveButton(R.string.btn_ok, null)
                            .show()
                        renderState()
                    }
                    SponsorService.AcceptResult.LIMIT_REACHED -> {
                        stylizedDialog(this)
                            .setTitle(R.string.sponsors_limit_reached_title)
                            .setMessage(getString(R.string.sponsors_limit_reached_msg,
                                SponsorService.MAX_ACTIVE_SPONSORS))
                            .setPositiveButton(R.string.btn_ok, null)
                            .show()
                    }
                    else -> { /* ignorar — race condition */ }
                }
            }
            is SponsorItem.Active -> showDetailsDialog(item.contract.sponsor)
        }
    }

    private fun handleSecondary(item: SponsorItem) {
        val gs = GameRepository.current()
        when (item) {
            is SponsorItem.OfferItem -> {
                SponsorService.rejectOffer(gs, item.offer.sponsor.id)
                GameRepository.save(applicationContext)
                renderState()
            }
            is SponsorItem.Active -> {
                val penalty = item.contract.sponsor.weeklyAmount * SponsorService.CANCELLATION_FEE_WEEKS
                stylizedDialog(this)
                    .setTitle(R.string.sponsors_cancel_confirm_title)
                    .setMessage(getString(R.string.sponsors_cancel_confirm_msg,
                        item.contract.sponsor.name, "%,d".format(penalty)))
                    .setPositiveButton(R.string.btn_yes) { _, _ ->
                        val actualPenalty = SponsorService.cancelContract(gs, item.contract.sponsor.id)
                        GameRepository.save(applicationContext)
                        stylizedDialog(this)
                            .setTitle(R.string.sponsors_cancel_confirm_title)
                            .setMessage(getString(R.string.sponsors_cancelled_msg,
                                "%,d".format(actualPenalty)))
                            .setPositiveButton(R.string.btn_ok, null)
                            .show()
                        renderState()
                    }
                    .setNegativeButton(R.string.btn_no, null)
                    .show()
            }
        }
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

    companion object {
        private const val TAB_ACTIVE = 0
        private const val TAB_MARKET = 1
    }

    // ── Modelos de UI ────────────────────────────────────────────────────

    /** Adapta tanto contratos ativos quanto ofertas no mesmo adapter. */
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

            // Cabeçalho comum
            h.tvCategoryIcon.text = sponsor.category.emoji
            h.tvName.text         = sponsor.name
            bindTierBadge(h, sponsor.tier)
            h.tvWeekly.text = ctx.getString(R.string.sponsors_weekly_format,
                "%,d".format(sponsor.weeklyAmount))

            // Bônus / penalidade chips
            bindBonuses(h, sponsor, ctx)

            // Subtítulo + ações específicas por tipo
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

        private fun remainingWeeks(contract: SponsorContract): Int {
            val today = runCatching {
                LocalDate.parse(GameRepository.current().currentDate)
            }.getOrNull() ?: return contract.sponsor.durationWeeks
            val end   = runCatching { LocalDate.parse(contract.endDate) }.getOrNull()
                ?: return contract.sponsor.durationWeeks
            val days = ChronoUnit.DAYS.between(today, end).toInt()
            return ((days + 6) / 7).coerceAtLeast(0)  // arredonda para cima
        }
    }
}
