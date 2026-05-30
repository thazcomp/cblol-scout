package com.cblol.scout.ui.hub

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.cblol.scout.R
import com.cblol.scout.data.FinancialHealth
import com.cblol.scout.domain.usecase.AcademyService
import com.cblol.scout.domain.usecase.BankService
import com.cblol.scout.domain.usecase.CoachProgressionService
import com.cblol.scout.domain.usecase.IncomingOfferService
import com.cblol.scout.domain.usecase.NewsService
import com.cblol.scout.domain.usecase.ScoutingService
import com.cblol.scout.domain.usecase.SponsorService
import com.cblol.scout.domain.usecase.TransferWindowService
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository

/**
 * Renderiza os badges/subtítulos dos cards do Hub a partir do [GameState] atual.
 *
 * Extraído da [com.cblol.scout.ui.ManagerHubActivity] porque a Activity
 * tinha 6 métodos `render*Summary` encadeados linearmente — cada um lendo um
 * serviço diferente e atualizando uma TextView específica. Centralizar aqui
 * mantém SRP (a Activity orquestra ciclo de vida + observadores; o renderer
 * cuida do "como mostrar"), e a chamada vira uma linha só.
 *
 * O renderer é **stateless** — recebe a Activity (para `findViewById` +
 * `getString`) em cada chamada e lê o GameRepository diretamente. Como cada
 * método é defensivo contra `GameRepository.current()` lançar (via
 * `runCatching`), pode ser chamado em `onResume` antes do load completar.
 *
 * **SOLID:**
 *  - **SRP**: cada `update*` cuida de UM card.
 *  - **OCP**: adicionar um novo card no Hub vira um novo `update*` +
 *    uma linha em [renderAll], sem mexer no resto.
 *  - **DIP**: Activity recebe um objeto colaborador em vez de inline.
 */
internal class HubCardSummaryRenderer(private val activity: Activity) {

    /** Atualiza todos os cards do Hub em sequência. Chamado após o render principal. */
    fun renderAll(managerName: String) {
        updateMarketWindow()
        updateCoachSummary(managerName)
        updateSponsorsSummary()
        updateScoutingSummary()
        updatePayrollSummary()
        updateOffersSummary()
        updateAcademySummary()
        updateBankSummary()
        updateNewsSummary()
    }

    // ── Cards individuais ────────────────────────────────────────────────

    /**
     * Banner de janela de transferência no card de próxima partida.
     * Verde quando o mercado está aberto, escuro quando fechado.
     */
    private fun updateMarketWindow() {
        val tv = activity.findViewById<TextView>(R.id.tv_hub_market_window)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val open   = TransferWindowService.isMarketOpen(gs)
        val status = TransferWindowService.statusMessage(gs)
        tv.text = status
        val bgRes = if (open) R.color.state_success else R.color.color_surface_elevated
        val fgRes = if (open) R.color.pick_ban_bg else R.color.color_on_surface_variant
        tv.setBackgroundColor(color(bgRes))
        tv.setTextColor(color(fgRes))
    }

    /** Card resumo do técnico (nome + título · Lv N). */
    private fun updateCoachSummary(managerName: String) {
        val tvName     = activity.findViewById<TextView>(R.id.tv_hub_coach_name)
        val tvSubtitle = activity.findViewById<TextView>(R.id.tv_hub_coach_subtitle)
        val gs         = GameRepository.current()
        val stats      = CoachProgressionService.compute(gs.coachProfile, managerName)
        tvName.text     = managerName
        tvSubtitle.text = activity.getString(R.string.coach_subtitle, stats.title, stats.level)
    }

    /** Patrocínios: contagem + receita semanal atual. */
    private fun updateSponsorsSummary() {
        val tv = activity.findViewById<TextView>(R.id.tv_hub_sponsors_subtitle)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val activeCount = gs.activeSponsors?.size ?: 0
        val weekly = SponsorService.totalWeeklyIncomeFromSponsors(gs)
        tv.text = if (activeCount == 0) {
            activity.getString(R.string.hub_sponsors_subtitle_empty)
        } else {
            activity.getString(
                R.string.hub_sponsors_subtitle_active,
                activeCount,
                SponsorService.MAX_ACTIVE_SPONSORS,
                "%,d".format(weekly)
            )
        }
    }

    /** Olheiros: X/Y slots ativos + tier do departamento. */
    private fun updateScoutingSummary() {
        val tv = activity.findViewById<TextView>(R.id.tv_hub_scouting_subtitle)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val active = ScoutingService.activeScouts(gs).size
        val tier   = ScoutingService.tier(gs)
        tv.text = if (active == 0) {
            activity.getString(R.string.hub_scouting_subtitle)
        } else {
            activity.getString(
                R.string.hub_scouting_subtitle_active,
                active, tier.maxConcurrentScouts, tier.label
            )
        }
    }

    /** Folha salarial: total mensal atual. */
    private fun updatePayrollSummary() {
        val tv = activity.findViewById<TextView>(R.id.tv_hub_payroll_subtitle)
        val total = runCatching {
            GameEngine.totalMonthlyPayroll(activity.applicationContext)
        }.getOrNull() ?: return
        tv.text = activity.getString(R.string.hub_payroll_subtitle_value, "%,d".format(total))
    }

    /**
     * Propostas recebidas: contagem em destaque quando há ofertas, escondido
     * quando não há.
     */
    private fun updateOffersSummary() {
        val tv = activity.findViewById<TextView>(R.id.tv_hub_offers_badge)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val count = IncomingOfferService.activeOffers(gs).size
        if (count > 0) {
            tv.visibility = View.VISIBLE
            tv.text = activity.getString(R.string.hub_offers_subtitle_active, count)
        } else {
            tv.visibility = View.GONE
        }
    }

    /**
     * Categoria de base: destaca quantos prospects estão prontos para subir
     * ao elenco principal.
     */
    private fun updateAcademySummary() {
        val tv = activity.findViewById<TextView>(R.id.tv_hub_academy_subtitle)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val ready = AcademyService.prospects(gs).count { it.isReady() }
        if (ready > 0) {
            tv.visibility = View.VISIBLE
            tv.text = activity.getString(R.string.academy_hub_subtitle_ready, ready)
        } else {
            tv.visibility = View.GONE
        }
    }

    /**
     * Banco: mostra dívida ativa (prioridade) ou saúde financeira em zona de
     * atenção/crítica. Esconde se está tudo saudável e sem dívidas.
     */
    private fun updateBankSummary() {
        val tv = activity.findViewById<TextView>(R.id.tv_hub_bank_subtitle)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val debt = BankService.totalDebt(gs)
        val health = BankService.financialHealth(gs)

        if (debt > 0) {
            tv.visibility = View.VISIBLE
            tv.text = activity.getString(R.string.hub_bank_subtitle_debt, "%,d".format(debt))
            tv.setTextColor(color(R.color.state_danger))
        } else if (health != FinancialHealth.HEALTHY) {
            tv.visibility = View.VISIBLE
            tv.text = "${health.emoji} ${health.label}"
            val colorRes = if (health == FinancialHealth.CRITICAL) R.color.state_danger
                           else R.color.state_warning
            tv.setTextColor(color(colorRes))
        } else {
            tv.visibility = View.GONE
        }
    }

    /** Notícias: manchete de maior destaque truncada, ou escondida se feed vazio. */
    private fun updateNewsSummary() {
        val tv = activity.findViewById<TextView>(R.id.tv_hub_news_subtitle)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val headline = NewsService.latestHeadline(gs)
        if (headline != null) {
            tv.visibility = View.VISIBLE
            tv.text = headline.headline
        } else {
            tv.visibility = View.GONE
        }
    }

    private fun color(@ColorRes res: Int) = ContextCompat.getColor(activity, res)
}
