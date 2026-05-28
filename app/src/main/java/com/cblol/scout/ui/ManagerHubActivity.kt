package com.cblol.scout.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.LogEntry
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.databinding.ActivityManagerHubBinding
import com.cblol.scout.domain.usecase.HubState
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.viewmodel.ManagerHubViewModel
import com.cblol.scout.util.TeamColors
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Hub central do técnico após o login: mostra time, orçamento, próximo jogo, log de eventos
 * e dá acesso a Elenco, Mercado, Calendário, Classificação.
 *
 * SOLID:
 * - **SRP**: handlers de UI delegam para `ManagerHubViewModel`; renderização separada
 *   em [renderHubState], [renderNextMatch], [setupCardActions].
 * - **OCP**: log icons são mapeados em [LOG_TYPE_ICON_RES] (declarativo).
 * - **DIP**: depende de [ManagerHubViewModel] via Koin. Strings/cores via R.string/R.color.
 */
class ManagerHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManagerHubBinding
    private val vm: ManagerHubViewModel by viewModel()
    private val dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN)

    private var pendingMatchId: String = ""
    private var pendingMapNumber: Int = 1
    private var pendingPlayerTeamId: String = ""
    private var pendingOpponentTeamId: String = ""

    private var autoORmanualPicksDialog: AlertDialog? = null
    private var confirmQuitDialog: AlertDialog? = null

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManagerHubBinding.inflate(layoutInflater)
        restoreState(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        observeViewModel()
        setupCardActions()
        vm.init()
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        showPendingOffMatchEventIfAny()
    }

    /**
     * Se há um evento fora de jogo pendente que ainda não foi visualizado pelo
     * jogador (por exemplo, o app foi fechado entre o fim da série e a abertura
     * do evento), abre a [OffMatchEventActivity] aqui no Hub para mostrar.
     *
     * Essa redundância com o [MatchResultActivity.setupContinueButton] garante
     * que nenhum evento fica esquecido no save.
     *
     * IMPORTANTE: `onResume` pode rodar ANTES do `vm.init()` ter carregado o
     * GameState (ex: primeira abertura do Hub vindo do TeamSelect). Por isso
     * usamos `runCatching` em vez de chamar `GameRepository.current()` direto
     * — se não há GameState ainda, simplesmente não fazemos nada agora; o
     * próximo onResume (depois do load) cuidará disso.
     */
    private fun showPendingOffMatchEventIfAny() {
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        if (gs.pendingOffMatchEvent != null) {
            startActivity(OffMatchEventActivity.intent(this))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING_MATCH_ID,    pendingMatchId)
        outState.putInt(STATE_PENDING_MAP_NUMBER,     pendingMapNumber)
        outState.putString(STATE_PENDING_PLAYER_ID,   pendingPlayerTeamId)
        outState.putString(STATE_PENDING_OPPONENT_ID, pendingOpponentTeamId)
    }

    override fun onDestroy() {
        autoORmanualPicksDialog?.takeIf { it.isShowing }?.dismiss()
        confirmQuitDialog?.takeIf { it.isShowing }?.dismiss()
        super.onDestroy()
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        pendingMatchId        = savedInstanceState?.getString(STATE_PENDING_MATCH_ID) ?: ""
        pendingMapNumber      = savedInstanceState?.getInt(STATE_PENDING_MAP_NUMBER, 1) ?: 1
        pendingPlayerTeamId   = savedInstanceState?.getString(STATE_PENDING_PLAYER_ID) ?: ""
        pendingOpponentTeamId = savedInstanceState?.getString(STATE_PENDING_OPPONENT_ID) ?: ""
    }

    // ── Observers ────────────────────────────────────────────────────────

    private fun observeViewModel() {
        vm.sessionReady.observe(this) { ready ->
            if (!ready) {
                startActivity(Intent(this, TeamSelectActivity::class.java))
                finish()
            }
        }
        vm.hubState.observe(this) { state -> renderHubState(state) }
    }

    private fun renderHubState(state: HubState) {
        binding.toolbar.title    = state.teamName
        binding.toolbar.subtitle = getString(R.string.hub_coach_label, state.managerName)
        binding.toolbar.setBackgroundColor(TeamColors.forTeam(state.teamId))
        binding.toolbar.setTitleTextColor(Color.WHITE)
        binding.toolbar.setSubtitleTextColor(Color.WHITE)

        binding.tvDate.text    = LocalDate.parse(state.currentDate).format(dateFormatter)
        binding.tvBudget.text  = getString(R.string.hub_budget_format, "%,d".format(state.budget))
        binding.tvBudget.setTextColor(color(
            if (state.budget < 0) R.color.hub_budget_negative else R.color.hub_budget_positive
        ))
        binding.tvPayroll.text   = getString(R.string.hub_payroll_format, "%,d".format(state.monthlyPayroll))
        binding.tvSquadInfo.text = getString(R.string.hub_squad_info, state.rosterSize, state.starterCount)

        renderNextMatch(state)
        renderCoachSummary(state.managerName)
        renderSponsorsSummary()
        renderMarketWindow()

        binding.recyclerLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerLog.adapter = LogAdapter(state.log)
    }

    /**
     * Atualiza o banner de janela de transferência no card de próxima partida.
     * Verde quando o mercado está aberto (pré ou inter-temporada), escuro
     * quando fechado. Texto vem do [TransferWindowService].
     */
    private fun renderMarketWindow() {
        val tv = findViewById<android.widget.TextView>(R.id.tv_hub_market_window)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val open   = com.cblol.scout.domain.usecase.TransferWindowService.isMarketOpen(gs)
        val status = com.cblol.scout.domain.usecase.TransferWindowService.statusMessage(gs)
        tv.text = status
        val bgRes = if (open) R.color.state_success else R.color.color_surface_elevated
        val fgRes = if (open) R.color.pick_ban_bg else R.color.color_on_surface_variant
        tv.setBackgroundColor(color(bgRes))
        tv.setTextColor(color(fgRes))
    }

    /**
     * Atualiza o card de Patrocínios no Hub mostrando contagem + receita semanal
     * atual. Caso não haja patrocínios ativos, mostra texto de chamada
     * ("Toque para procurar ofertas").
     */
    private fun renderSponsorsSummary() {
        val tv = findViewById<android.widget.TextView>(R.id.tv_hub_sponsors_subtitle)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val activeCount = gs.activeSponsors?.size ?: 0
        val weekly = com.cblol.scout.domain.usecase.SponsorService
            .totalWeeklyIncomeFromSponsors(gs)
        tv.text = if (activeCount == 0) {
            getString(R.string.hub_sponsors_subtitle_empty)
        } else {
            getString(R.string.hub_sponsors_subtitle_active,
                activeCount,
                com.cblol.scout.domain.usecase.SponsorService.MAX_ACTIVE_SPONSORS,
                "%,d".format(weekly))
        }
        renderScoutingSummary()
    }

    /**
     * Atualiza o card de Olheiros no Hub mostrando X/Y slots ativos + tier.
     * Quando ninguém está sendo escotado, mostra texto-padrão de chamada.
     */
    private fun renderScoutingSummary() {
        val tv = findViewById<android.widget.TextView>(R.id.tv_hub_scouting_subtitle)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val active = com.cblol.scout.domain.usecase.ScoutingService.activeScouts(gs).size
        val tier   = com.cblol.scout.domain.usecase.ScoutingService.tier(gs)
        tv.text = if (active == 0) {
            getString(R.string.hub_scouting_subtitle)
        } else {
            getString(R.string.hub_scouting_subtitle_active,
                active, tier.maxConcurrentScouts, tier.label)
        }
        renderPayrollSummary()
    }

    /**
     * Atualiza o card de Folha Salarial no Hub mostrando o total mensal atual.
     */
    private fun renderPayrollSummary() {
        val tv = findViewById<android.widget.TextView>(R.id.tv_hub_payroll_subtitle)
        val total = runCatching {
            com.cblol.scout.game.GameEngine.totalMonthlyPayroll(applicationContext)
        }.getOrNull() ?: return
        tv.text = getString(R.string.hub_payroll_subtitle_value, "%,d".format(total))
        renderOffersSummary()
    }

    /**
     * Atualiza o badge do card de Propostas Recebidas: mostra a contagem de
     * ofertas ativas quando há alguma (chamativo em dourado), ou esconde quando
     * não há propostas.
     */
    private fun renderOffersSummary() {
        val tv = findViewById<android.widget.TextView>(R.id.tv_hub_offers_badge)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val count = com.cblol.scout.domain.usecase.IncomingOfferService.activeOffers(gs).size
        if (count > 0) {
            tv.visibility = View.VISIBLE
            tv.text = getString(R.string.hub_offers_subtitle_active, count)
        } else {
            tv.visibility = View.GONE
        }
        renderAcademySummary()
    }

    /**
     * Atualiza o badge do card de Categoria de Base: destaca quantos prospects
     * estão prontos para subir ao elenco principal. Se nenhum está pronto,
     * esconde o badge (o card continua acessível).
     */
    private fun renderAcademySummary() {
        val tv = findViewById<android.widget.TextView>(R.id.tv_hub_academy_subtitle)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val ready = com.cblol.scout.domain.usecase.AcademyService.prospects(gs)
            .count { it.isReady() }
        if (ready > 0) {
            tv.visibility = View.VISIBLE
            tv.text = getString(R.string.academy_hub_subtitle_ready, ready)
        } else {
            tv.visibility = View.GONE
        }
        renderBankSummary()
    }

    /**
     * Atualiza o badge do card de Banco: mostra a saúde financeira atual e, se
     * houver dívida ativa, o saldo devedor. Cor do badge espelha a saude
     * financeira para reforçar o aviso visual.
     */
    private fun renderBankSummary() {
        val tv = findViewById<android.widget.TextView>(R.id.tv_hub_bank_subtitle)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val debt = com.cblol.scout.domain.usecase.BankService.totalDebt(gs)
        val health = com.cblol.scout.domain.usecase.BankService.financialHealth(gs)

        // Prioriza mostrar dívida quando há; senão mostra a saude com emoji.
        if (debt > 0) {
            tv.visibility = View.VISIBLE
            tv.text = getString(R.string.hub_bank_subtitle_debt, "%,d".format(debt))
            tv.setTextColor(ContextCompat.getColor(this, R.color.state_danger))
        } else if (health != com.cblol.scout.data.FinancialHealth.HEALTHY) {
            tv.visibility = View.VISIBLE
            tv.text = "${health.emoji} ${health.label}"
            val colorRes = if (health == com.cblol.scout.data.FinancialHealth.CRITICAL)
                R.color.state_danger else R.color.state_warning
            tv.setTextColor(ContextCompat.getColor(this, colorRes))
        } else {
            tv.visibility = View.GONE
        }
        renderNewsSummary()
    }

    /**
     * Atualiza o badge do card de Notícias: mostra a manchete de maior destaque
     * (truncada). Se o feed está vazio, esconde o badge — o card continua
     * acessível para abrir a tela (que mostra o empty state).
     */
    private fun renderNewsSummary() {
        val tv = findViewById<android.widget.TextView>(R.id.tv_hub_news_subtitle)
        val gs = runCatching { GameRepository.current() }.getOrNull() ?: return
        val headline = com.cblol.scout.domain.usecase.NewsService.latestHeadline(gs)
        if (headline != null) {
            tv.visibility = View.VISIBLE
            tv.text = headline.headline
        } else {
            tv.visibility = View.GONE
        }
    }

    /**
     * Atualiza o card resumo do técnico no Hub (nome + título · Lv N).
     * O card abre o [CoachProfileDialog] completo ao ser tocado.
     */
    private fun renderCoachSummary(managerName: String) {
        val tvName     = findViewById<android.widget.TextView>(R.id.tv_hub_coach_name)
        val tvSubtitle = findViewById<android.widget.TextView>(R.id.tv_hub_coach_subtitle)
        val gs         = GameRepository.current()
        val stats      = com.cblol.scout.domain.usecase.CoachProgressionService
                            .compute(gs.coachProfile, managerName)
        tvName.text     = managerName
        tvSubtitle.text = getString(R.string.coach_subtitle, stats.title, stats.level)
    }

    private fun renderNextMatch(state: HubState) {
        val next = state.nextMatch
        if (next != null) {
            val daysUntil = LocalDate.parse(state.currentDate)
                .until(LocalDate.parse(next.date)).days
            binding.tvNextMatch.text     = next.label
            binding.tvNextMatchDate.text = getString(R.string.hub_next_match_date,
                LocalDate.parse(next.date).format(dateFormatter), next.round, daysUntil)
            binding.btnPlayNextMatch.isEnabled = true
        } else {
            binding.tvNextMatch.text     = getString(R.string.hub_no_matches)
            binding.tvNextMatchDate.text = getString(R.string.hub_split_ended)
            binding.btnPlayNextMatch.isEnabled = false
        }
    }

    private fun setupCardActions() {
        binding.btnPlayNextMatch.setOnClickListener { openPickBanOrSim() }
        binding.btnAdvanceDay.setOnClickListener { advanceOneDay() }
        binding.cardSquad.setOnClickListener      { startActivity(Intent(this, SquadActivity::class.java)) }
        binding.cardMarket.setOnClickListener     { startActivity(Intent(this, TransferMarketActivity::class.java)) }
        binding.cardSchedule.setOnClickListener   { startActivity(Intent(this, ScheduleActivity::class.java)) }
        binding.cardStandings.setOnClickListener  { startActivity(Intent(this, StandingsActivity::class.java)) }
        findViewById<View>(R.id.card_coach_profile).setOnClickListener {
            val gs = GameRepository.current()
            CoachProfileDialog.show(this, gs.coachProfile, gs.managerName)
        }
        findViewById<View>(R.id.card_sponsors).setOnClickListener {
            startActivity(Intent(this, SponsorsActivity::class.java))
        }
        findViewById<View>(R.id.card_training).setOnClickListener {
            startActivity(Intent(this, TrainingActivity::class.java))
        }
        findViewById<View>(R.id.card_scouting).setOnClickListener {
            startActivity(Intent(this, ScoutingActivity::class.java))
        }
        findViewById<View>(R.id.card_payroll).setOnClickListener {
            PayrollDialog.show(this) { vm.refresh() }
        }
        findViewById<View>(R.id.card_offers).setOnClickListener {
            startActivity(IncomingOffersActivity.intent(this))
        }
        findViewById<View>(R.id.card_academy).setOnClickListener {
            startActivity(AcademyActivity.intent(this))
        }
        findViewById<View>(R.id.card_bank).setOnClickListener {
            startActivity(BankActivity.intent(this))
        }
        findViewById<View>(R.id.card_news).setOnClickListener {
            startActivity(NewsActivity.intent(this))
        }
        binding.btnQuit.setOnClickListener        { confirmQuit() }
    }

    // ── Pick & ban / simulação ──────────────────────────────────────────

    private fun openPickBanOrSim() {
        val next = vm.hubState.value?.nextMatch ?: return
        val playerTeamId = GameRepository.current().managerTeamId

        autoORmanualPicksDialog = stylizedDialog(this)
            .setTitle(next.label)
            .setMessage(R.string.dialog_pickban_question)
            .setPositiveButton(R.string.btn_do_pickban) { _, _ ->
                clearPendingState()
                // Delega para o PickBanRouterActivity, que faz toda a sequência:
                //   PickBan → RoleAssignment → salvar plano → MatchSimulation.
                // Antes o Hub abria o PickBanActivity direto, mas isso pulava a
                // tela de estratégia de rotas no mapa 1.
                startActivity(
                    Intent(this, PickBanRouterActivity::class.java).apply {
                        putExtra(PickBanRouterActivity.EXTRA_MATCH_ID,       next.matchId)
                        putExtra(PickBanRouterActivity.EXTRA_MAP_NUMBER,     1)
                        putExtra(PickBanRouterActivity.EXTRA_PLAYER_TEAM_ID, playerTeamId)
                        putExtra(PickBanRouterActivity.EXTRA_OPPONENT_ID,    next.opponentId)
                    }
                )
            }
            .setNegativeButton(R.string.btn_skip_simulation) { _, _ ->
                clearPendingState()
                startActivity(Intent(this, MatchSimulationActivity::class.java)
                    .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, next.matchId))
            }
            .show()
    }

    private fun buildPickBanIntent(matchId: String, playerTeamId: String, opponentId: String, mapNum: Int) =
        Intent(this, PickBanActivity::class.java).apply {
            putExtra(PickBanActivity.EXTRA_PLAYER_TEAM_ID,   playerTeamId)
            putExtra(PickBanActivity.EXTRA_OPPONENT_TEAM_ID, opponentId)
            putExtra(PickBanActivity.EXTRA_MATCH_ID,         matchId)
            putExtra(PickBanActivity.EXTRA_MAP_NUMBER,       mapNum)
        }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PickBanActivity.REQUEST_PICK_BAN || resultCode != RESULT_OK || data == null) return
        if (pendingMatchId.isEmpty() || pendingPlayerTeamId.isEmpty()) return

        savePickBanPlan(data)
        startActivity(Intent(this, MatchSimulationActivity::class.java)
            .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, pendingMatchId))
    }

    private fun savePickBanPlan(data: Intent) {
        val bluePicks = data.getStringArrayListExtra(PickBanActivity.RESULT_BLUE_PICKS)?.toList() ?: return
        val redPicks  = data.getStringArrayListExtra(PickBanActivity.RESULT_RED_PICKS)?.toList()  ?: emptyList()
        val blueBans  = data.getStringArrayListExtra(PickBanActivity.RESULT_BLUE_BANS)?.toList()  ?: emptyList()
        val redBans   = data.getStringArrayListExtra(PickBanActivity.RESULT_RED_BANS)?.toList()   ?: emptyList()
        val mapNum    = data.getIntExtra(PickBanActivity.EXTRA_MAP_NUMBER, pendingMapNumber)

        val gs = GameRepository.current()
        gs.matches.find { it.id == pendingMatchId }?.pickBanPlan =
            PickBanPlan(mapNum, bluePicks, redPicks, blueBans, redBans)
        GameRepository.save(applicationContext)
    }

    private fun clearPendingState() {
        pendingMatchId        = ""
        pendingMapNumber      = 1
        pendingPlayerTeamId   = ""
        pendingOpponentTeamId = ""
    }

    // ── Avançar dia ─────────────────────────────────────────────────────

    /**
     * Avança o calendário em 1 dia processando os ticks diários (scouting,
     * moral, economia, janelas de transferência) via
     * [com.cblol.scout.game.GameEngine.advanceCalendarTo].
     *
     * Se houver uma partida do jogador agendada para o próximo dia, avisa para
     * jogá-la em vez de pular por cima (não auto-simula a partida do jogador).
     * Após avançar, atualiza o Hub e mostra um resumo curto.
     */
    private fun advanceOneDay() {
        val gs = GameRepository.current()
        val today = LocalDate.parse(gs.currentDate)
        val target = today.plusDays(1)

        // Se há partida do MEU time exatamente no próximo dia, o jogador deve
        // jogá-la (não queremos auto-simular a partida dele ao avançar o dia).
        val myMatchTomorrow = gs.matches.any {
            !it.played && it.date == target.toString() &&
                (it.homeTeamId == gs.managerTeamId || it.awayTeamId == gs.managerTeamId)
        }
        if (myMatchTomorrow) {
            stylizedDialog(this)
                .setTitle(R.string.hub_advance_blocked_title)
                .setMessage(R.string.hub_advance_blocked_msg)
                .setPositiveButton(R.string.btn_ok, null)
                .show()
            return
        }

        val report = com.cblol.scout.game.GameEngine
            .advanceCalendarTo(applicationContext, target.toString())
        vm.refresh()

        // Scouting/moral/economia aparecem no log; aqui só confirmamos o avanço
        // e sinalizamos pedidos de transferência e propostas recebidas, se houver.
        val msg = buildString {
            append(getString(R.string.hub_advance_done, target.format(dateFormatter)))
            if (report.transferRequests.isNotEmpty()) {
                append("\n\n")
                append(getString(R.string.hub_advance_transfer_requests,
                    report.transferRequests.joinToString(", ")))
            }
            if (report.incomingOffers.isNotEmpty()) {
                append("\n\n")
                append(getString(R.string.hub_advance_incoming_offers,
                    report.incomingOffers.joinToString(", ")))
            }
            if (report.academyReady.isNotEmpty()) {
                append("\n\n")
                append(getString(R.string.hub_advance_academy_ready,
                    report.academyReady.joinToString(", ")))
            }
            // Alerta financeiro: se o avanço cruzou para zona de atenção/crítica,
            // chama o gerente para o Banco com a dica acionável.
            report.financialHealthWarning?.let { health ->
                append("\n\n")
                append("${health.emoji} ")
                append(getString(R.string.hub_advance_financial_warning,
                    health.label,
                    com.cblol.scout.domain.usecase.BankService.healthAdvice(
                        GameRepository.current()
                    )))
            }
        }
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    // ── Encerrar carreira ───────────────────────────────────────────────

    private fun confirmQuit() {
        confirmQuitDialog = stylizedDialog(this)
            .setTitle(R.string.dialog_quit_career_title)
            .setMessage(R.string.dialog_quit_career_message)
            .setPositiveButton(R.string.btn_yes_quit) { _, _ ->
                vm.clearCareer()
                startActivity(Intent(this, TeamSelectActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
            .setNegativeButton(R.string.btn_cancel, null).show()
    }

    private fun color(@ColorRes res: Int) = ContextCompat.getColor(this, res)

    // ── Log adapter ─────────────────────────────────────────────────────

    private class LogAdapter(private val items: List<LogEntry>) :
        RecyclerView.Adapter<LogAdapter.VH>() {

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
    }

    companion object {
        private const val DATE_FORMAT_PATTERN = "dd/MM/yyyy"

        private const val STATE_PENDING_MATCH_ID    = "pendingMatchId"
        private const val STATE_PENDING_MAP_NUMBER  = "pendingMapNumber"
        private const val STATE_PENDING_PLAYER_ID   = "pendingPlayerTeamId"
        private const val STATE_PENDING_OPPONENT_ID = "pendingOpponentTeamId"

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
