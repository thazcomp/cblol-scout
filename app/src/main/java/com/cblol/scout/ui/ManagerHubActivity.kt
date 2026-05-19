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

        binding.recyclerLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerLog.adapter = LogAdapter(state.log)
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
        binding.cardSquad.setOnClickListener      { startActivity(Intent(this, SquadActivity::class.java)) }
        binding.cardMarket.setOnClickListener     { startActivity(Intent(this, TransferMarketActivity::class.java)) }
        binding.cardSchedule.setOnClickListener   { startActivity(Intent(this, ScheduleActivity::class.java)) }
        binding.cardStandings.setOnClickListener  { startActivity(Intent(this, StandingsActivity::class.java)) }
        findViewById<View>(R.id.card_coach_profile).setOnClickListener {
            val gs = GameRepository.current()
            CoachProfileDialog.show(this, gs.coachProfile, gs.managerName)
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
            "CAREER"   to R.string.icon_career
        )
    }
}
