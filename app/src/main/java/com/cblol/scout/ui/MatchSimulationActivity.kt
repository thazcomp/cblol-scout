package com.cblol.scout.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cblol.scout.R
import com.cblol.scout.data.Match
import com.cblol.scout.data.MatchEvent
import com.cblol.scout.databinding.ActivityMatchSimulationBinding
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.LiveMatchEngine
import com.cblol.scout.ui.match.MatchEventRenderer
import com.cblol.scout.ui.match.MatchFeedAdapter
import com.cblol.scout.ui.match.MatchResultPublisher
import com.cblol.scout.ui.match.MatchStatsAccumulator
import com.cblol.scout.util.TeamColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Player de partida acelerado: orquestra a simulação ao vivo de UM mapa.
 *
 * Lê o ID da partida via Intent extra, gera a timeline com [LiveMatchEngine]
 * e toca os eventos com delays definidos em [GameConstants.Simulation].
 *
 * **Arquitetura SOLID — colaboradores em `ui/match/`:**
 *
 *  - **[MatchStatsAccumulator]** — guarda os contadores do mapa (kills,
 *    torres, dragões, baron, herald) e atualiza o scoreboard.
 *  - **[MatchFeedAdapter]** — adapter standalone do feed de eventos.
 *  - **[MatchEventRenderer]** — dispatch dos 13 tipos de evento na UI
 *    (chips de pick/ban, scoreboard, linhas do feed, transições de fase).
 *  - **[MatchResultPublisher]** — encerra o mapa/série, calcula prêmio,
 *    avança calendário, publica notícia e lança a [MatchResultActivity].
 *
 * A Activity em si só coordena lifecycle + coroutine de tocagem dos eventos +
 * controles (velocidade, pular).
 *
 * Suporta velocidades 1x / 2x / 4x via botão de acelerador.
 */
class MatchSimulationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchSimulationBinding
    private lateinit var match: Match
    private lateinit var feedAdapter: MatchFeedAdapter
    private lateinit var stats: MatchStatsAccumulator
    private lateinit var renderer: MatchEventRenderer
    private lateinit var resultPublisher: MatchResultPublisher

    // Placar de mapas (acumulado entre mapas da série)
    private var homeMaps = 0
    private var awayMaps = 0

    private var speed = SPEED_1X
    private var resultApplied = false  // guard contra applyResult duplo

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchSimulationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { confirmExit() }

        if (GameRepository.load(applicationContext) == null) { finish(); return }
        if (!resolveMatch()) return

        renderTeamHeader()
        setupCollaborators()
        binding.btnSpeed.setOnClickListener { cycleSpeed() }
        startSimulation()
    }

    override fun onSupportNavigateUp(): Boolean { confirmExit(); return true }

    // ── Inicialização ────────────────────────────────────────────────────

    private fun resolveMatch(): Boolean {
        val matchId = intent.getStringExtra(EXTRA_MATCH_ID)
        val target  = GameRepository.current().matches.find { it.id == matchId }
        if (target == null || target.played) { finish(); return false }
        match = target
        return true
    }

    private fun renderTeamHeader() {
        // Times podem vir do snapshot (1ª div) ou de gs.secondDivisionTeams
        // (modo "começar de baixo"). teamsForCurrentDivision cobre os dois.
        val teams = GameRepository.teamsForCurrentDivision(applicationContext)
        val home = teams.find { it.id == match.homeTeamId } ?: return
        val away = teams.find { it.id == match.awayTeamId } ?: return
        binding.tvHomeName.text = home.nome
        binding.tvAwayName.text = away.nome
        binding.viewHomeBar.setBackgroundColor(TeamColors.forTeam(home.id))
        binding.viewAwayBar.setBackgroundColor(TeamColors.forTeam(away.id))
    }

    /** Cria os 4 colaboradores e conecta-os ao binding/match. */
    private fun setupCollaborators() {
        binding.recyclerFeed.layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true; stackFromEnd = false
        }
        feedAdapter = MatchFeedAdapter(binding.recyclerFeed).also {
            binding.recyclerFeed.adapter = it
        }
        stats = MatchStatsAccumulator(binding, this)
        renderer = MatchEventRenderer(
            activity = this,
            binding = binding,
            stats = stats,
            feed = feedAdapter,
            currentGameNum = { homeMaps + awayMaps + 1 },
            seriesScore = { homeMaps to awayMaps }
        )
        resultPublisher = MatchResultPublisher(
            activity = this,
            binding = binding,
            match = match,
            stats = stats,
            seriesScore = { homeMaps to awayMaps }
        )
    }

    // ── Controles ────────────────────────────────────────────────────────

    private fun cycleSpeed() {
        speed = when (speed) {
            SPEED_1X -> SPEED_2X
            SPEED_2X -> SPEED_4X
            else     -> SPEED_1X
        }
        binding.btnSpeed.text = getString(R.string.sim_speed_button, speed.toInt())
    }

    private fun confirmExit() {
        stylizedDialog(this)
            .setTitle(R.string.sim_dialog_exit_title)
            .setMessage(R.string.sim_dialog_exit_message)
            .setPositiveButton(R.string.btn_skip_finish) { _, _ ->
                lifecycleScope.launch { skipToResult() }
            }
            .setNegativeButton(R.string.btn_continue_watching, null)
            .show()
    }

    // ── Motor de simulação ──────────────────────────────────────────────

    private fun startSimulation() {
        binding.tvPhase.text = getString(R.string.generating_timeline)
        homeMaps = match.homeScore
        awayMaps = match.awayScore
        binding.tvSeriesScore.text = getString(R.string.result_score_format, homeMaps, awayMaps)
        val gameNumber = homeMaps + awayMaps + 1

        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                LiveMatchEngine.generateSingleMap(applicationContext, match, gameNumber)
            }
            if (result.homeWon) homeMaps++ else awayMaps++

            // Separa eventos em duas fases: pick & ban (até o 1º GameTick) e jogo
            val cutIdx = result.events.indexOfFirst { it is MatchEvent.GameTick }
                .takeIf { it >= 0 } ?: result.events.size
            val pickBanEvents = result.events.subList(0, cutIdx)
            val gameEvents    = result.events.subList(cutIdx, result.events.size)

            playEvents(pickBanEvents)

            // Pausa para o dialog de sinergia entre o draft e o jogo
            if (pickBanEvents.any { it is MatchEvent.Pick }) {
                withContext(Dispatchers.Main) {
                    binding.tvPhase.text = getString(R.string.pb_phase_done)
                }
                awaitDialogConfirmation()
            }

            playEvents(gameEvents)
            withContext(Dispatchers.Main) { applyResult(homeMaps, awayMaps, result.homeWon) }
        }
    }

    /**
     * Versão silenciosa de [startSimulation] que pula a animação e vai direto
     * ao resultado. Mantém os contadores para que a MatchResultActivity exiba
     * stats reais.
     */
    private suspend fun skipToResult() {
        val partialHome = match.homeScore
        val partialAway = match.awayScore
        val gameNumber  = partialHome + partialAway + 1

        val result = withContext(Dispatchers.Default) {
            LiveMatchEngine.generateSingleMap(applicationContext, match, gameNumber)
        }
        for (e in result.events) stats.accumulateSilent(e)
        val finalHome = partialHome + if (result.homeWon) 1 else 0
        val finalAway = partialAway + if (!result.homeWon) 1 else 0
        withContext(Dispatchers.Main) { applyResult(finalHome, finalAway, result.homeWon) }
    }

    /** Suspende a coroutine até o usuário confirmar o PreSimulationDialog. */
    private suspend fun awaitDialogConfirmation() = suspendCancellableCoroutine<Unit> { cont ->
        runOnUiThread {
            PreSimulationDialog.show(this, match.id, onConfirm = {
                if (cont.isActive) cont.resumeWith(Result.success(Unit))
            })
            cont.invokeOnCancellation { /* dialog descartado */ }
        }
    }

    private suspend fun playEvents(events: List<MatchEvent>) {
        for (e in events) {
            withContext(Dispatchers.Main) { renderer.render(e) }
            delay(delayFor(e))
        }
    }

    /** Delay configurável por tipo de evento, dividido pela velocidade atual. */
    private fun delayFor(event: MatchEvent): Long {
        val base = when (event) {
            is MatchEvent.GameStart, is MatchEvent.PhaseAnnouncement -> GameConstants.Simulation.DELAY_PHASE_MS
            is MatchEvent.Ban     -> GameConstants.Simulation.DELAY_BAN_MS
            is MatchEvent.Pick    -> GameConstants.Simulation.DELAY_PICK_MS
            is MatchEvent.GameTick -> GameConstants.Simulation.DELAY_TICK_MS
            is MatchEvent.Kill, is MatchEvent.TowerDown, is MatchEvent.Dragon,
            is MatchEvent.Baron, is MatchEvent.Herald, is MatchEvent.Inhibitor,
            is MatchEvent.Buff -> GameConstants.Simulation.DELAY_OBJECTIVE_MS
            is MatchEvent.GameEnd -> GameConstants.Simulation.DELAY_GAME_END_MS
            is MatchEvent.SeriesEnd -> 0L
        }
        return (base / speed).toLong()
    }

    // ── Encerramento ─────────────────────────────────────────────────────

    /**
     * Delega ao [resultPublisher] para persistir o resultado, calcular prêmio,
     * avançar calendário, publicar notícia e abrir a tela de resultado.
     * Idempotente via guard [resultApplied].
     *
     * @param mapWonByHome vencedor DESTE mapa (essencial — placar pode estar
     *   empatado e a comparação `home > away` daria errado). Vem do
     *   `result.homeWon` da última chamada de [LiveMatchEngine].
     */
    private fun applyResult(homeMapsFinal: Int, awayMapsFinal: Int, mapWonByHome: Boolean) {
        if (resultApplied) return
        resultApplied = true
        resultPublisher.publish(homeMapsFinal, awayMapsFinal, mapWonByHome)
        finish()
    }

    companion object {
        const val EXTRA_MATCH_ID = "extra_match_id"

        private const val SPEED_1X = 1f
        private const val SPEED_2X = 2f
        private const val SPEED_4X = 4f
    }
}
