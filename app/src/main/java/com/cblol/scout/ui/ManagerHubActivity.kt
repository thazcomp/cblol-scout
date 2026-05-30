package com.cblol.scout.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.cblol.scout.R
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.databinding.ActivityManagerHubBinding
import com.cblol.scout.domain.usecase.BankService
import com.cblol.scout.domain.usecase.HubState
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository
import com.cblol.scout.ui.hub.HubCardSummaryRenderer
import com.cblol.scout.ui.hub.HubLogAdapter
import com.cblol.scout.ui.viewmodel.ManagerHubViewModel
import com.cblol.scout.util.TeamColors
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Hub central do técnico após o login.
 *
 * **SOLID:**
 * - **SRP**: Activity coordena lifecycle + observers + ações. A renderização
 *   dos cards (orçamento, próxima partida, sponsors, scouting, payroll,
 *   ofertas, base, banco, notícias) vive em [HubCardSummaryRenderer]. O
 *   adapter de log vive em [HubLogAdapter].
 * - **OCP**: novos cards entram no [HubCardSummaryRenderer] sem mexer aqui.
 * - **DIP**: depende de [ManagerHubViewModel] via Koin. Strings/cores via
 *   R.string/R.color.
 */
class ManagerHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManagerHubBinding
    private val vm: ManagerHubViewModel by viewModel()
    private val dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN)
    private val cardRenderer by lazy { HubCardSummaryRenderer(this) }

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
     * `onResume` pode rodar ANTES de `vm.init()` ter carregado o GameState
     * (ex: primeira abertura do Hub vindo do TeamSelect). Por isso usamos
     * `runCatching` — se não há GameState ainda, o próximo onResume cuidará.
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
        cardRenderer.renderAll(state.managerName)

        binding.recyclerLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerLog.adapter = HubLogAdapter(state.log)
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
        binding.btnAdvanceDay.setOnClickListener    { advanceOneDay() }
        binding.cardSquad.setOnClickListener        { startActivity(Intent(this, SquadActivity::class.java)) }
        binding.cardMarket.setOnClickListener       { startActivity(Intent(this, TransferMarketActivity::class.java)) }
        binding.cardSchedule.setOnClickListener     { startActivity(Intent(this, ScheduleActivity::class.java)) }
        binding.cardStandings.setOnClickListener    { startActivity(Intent(this, StandingsActivity::class.java)) }
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
        findViewById<View>(R.id.card_history).setOnClickListener {
            RecentHistoryDialog.show(this)
        }
        findViewById<View>(R.id.btn_history_see_all).setOnClickListener {
            RecentHistoryDialog.show(this)
        }
        binding.btnQuit.setOnClickListener { confirmQuit() }
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
     * Avança o calendário em 1 dia, processando ticks E simulando as partidas
     * dos OUTROS times do dia. Se há partida do gerente amanhã, avisa para
     * jogá-la em vez de pular por cima.
     */
    private fun advanceOneDay() {
        val gs = GameRepository.current()
        val today = LocalDate.parse(gs.currentDate)
        val target = today.plusDays(1)

        // Se há partida do MEU time exatamente no próximo dia, o jogador deve
        // jogá-la (não queremos auto-simular a partida dele).
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

        val report = GameEngine.advanceCalendarTo(applicationContext, target.toString())
        vm.refresh()

        val msg = buildAdvanceReportMessage(target, report)
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
    }

    /** Constrói a mensagem do Toast com os destaques do relatório do avanço. */
    private fun buildAdvanceReportMessage(
        target: LocalDate,
        report: com.cblol.scout.game.AdvanceReport
    ): String = buildString {
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
        report.financialHealthWarning?.let { health ->
            append("\n\n")
            append("${health.emoji} ")
            append(getString(R.string.hub_advance_financial_warning,
                health.label,
                BankService.healthAdvice(GameRepository.current())))
        }
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

    companion object {
        private const val DATE_FORMAT_PATTERN = "dd/MM/yyyy"

        private const val STATE_PENDING_MATCH_ID    = "pendingMatchId"
        private const val STATE_PENDING_MAP_NUMBER  = "pendingMapNumber"
        private const val STATE_PENDING_PLAYER_ID   = "pendingPlayerTeamId"
        private const val STATE_PENDING_OPPONENT_ID = "pendingOpponentTeamId"
    }
}
