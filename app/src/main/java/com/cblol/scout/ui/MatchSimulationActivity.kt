package com.cblol.scout.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Match
import com.cblol.scout.data.MatchEvent
import com.cblol.scout.data.Side
import com.cblol.scout.databinding.ActivityMatchSimulationBinding
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.LiveMatchEngine
import com.cblol.scout.util.TeamColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Player de partida acelerado.
 *
 * Lê o ID da partida via Intent extra, gera a timeline com LiveMatchEngine e
 * toca os eventos com delays definidos em [GameConstants.Simulation].
 *
 * Antes de iniciar a fase de jogo (após pick & ban), exibe o
 * [PreSimulationDialog] com sinergias e vantagens. Ao final aplica o resultado
 * no GameState e lança a [MatchResultActivity].
 *
 * Suporta velocidades 1x / 2x / 4x via botão de acelerador.
 *
 * Princípios SOLID aplicados:
 * - **SRP**: cada método tem uma única responsabilidade
 *   ([renderEvent], [applyResult], [skipToResult], [updateScoreboard]).
 * - **OCP**: novos tipos de evento são adicionados em [renderEvent] sem
 *   tocar no resto do código.
 * - **DIP**: depende de [LiveMatchEngine] e [GameRepository] como abstrações
 *   de domínio. Strings/cores são lidas de recursos, não hardcoded.
 */
class MatchSimulationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchSimulationBinding
    private lateinit var match: Match
    private lateinit var feedAdapter: FeedAdapter

    // Contadores acumulados durante a simulação (resetados por mapa)
    private var homeKills = 0;   private var awayKills = 0
    private var homeTowers = 0;  private var awayTowers = 0
    private var homeDragons = 0; private var awayDragons = 0
    private var homeBarons = 0;  private var awayBarons = 0
    private var homeHeralds = 0; private var awayHeralds = 0

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
        setupFeed()
        binding.btnSpeed.setOnClickListener { cycleSpeed() }
        startSimulation()
    }

    override fun onSupportNavigateUp(): Boolean { confirmExit(); return true }

    // ── Inicialização ────────────────────────────────────────────────────

    /** Resolve a partida a partir do Intent. Retorna false se inválida (e finaliza a Activity). */
    private fun resolveMatch(): Boolean {
        val matchId = intent.getStringExtra(EXTRA_MATCH_ID)
        val target  = GameRepository.current().matches.find { it.id == matchId }
        if (target == null || target.played) { finish(); return false }
        match = target
        return true
    }

    private fun renderTeamHeader() {
        val snap = GameRepository.snapshot(applicationContext)
        val home = snap.times.find { it.id == match.homeTeamId } ?: return
        val away = snap.times.find { it.id == match.awayTeamId } ?: return
        binding.tvHomeName.text = home.nome
        binding.tvAwayName.text = away.nome
        binding.viewHomeBar.setBackgroundColor(TeamColors.forTeam(home.id))
        binding.viewAwayBar.setBackgroundColor(TeamColors.forTeam(away.id))
    }

    private fun setupFeed() {
        feedAdapter = FeedAdapter()
        binding.recyclerFeed.layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true; stackFromEnd = false
        }
        binding.recyclerFeed.adapter = feedAdapter
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
            withContext(Dispatchers.Main) { applyResult(homeMaps, awayMaps) }
        }
    }

    /**
     * Versão silenciosa de [startSimulation] que pula a animação e vai direto ao resultado.
     * Mantém os contadores para que a MatchResultActivity exiba stats reais.
     */
    private suspend fun skipToResult() {
        val partialHome = match.homeScore
        val partialAway = match.awayScore
        val gameNumber  = partialHome + partialAway + 1

        val result = withContext(Dispatchers.Default) {
            LiveMatchEngine.generateSingleMap(applicationContext, match, gameNumber)
        }
        for (e in result.events) accumulateStats(e)
        val finalHome = partialHome + if (result.homeWon) 1 else 0
        val finalAway = partialAway + if (!result.homeWon) 1 else 0
        withContext(Dispatchers.Main) { applyResult(finalHome, finalAway) }
    }

    /** Acumula stats no estado interno sem renderizar nada. */
    private fun accumulateStats(e: MatchEvent) {
        when (e) {
            is MatchEvent.GameStart -> resetMapCounters()
            is MatchEvent.Kill      -> if (e.killerSide == Side.HOME) homeKills++ else awayKills++
            is MatchEvent.TowerDown -> if (e.side == Side.HOME) homeTowers++ else awayTowers++
            is MatchEvent.Dragon    -> if (e.side == Side.HOME) homeDragons++ else awayDragons++
            is MatchEvent.Baron     -> if (e.side == Side.HOME) homeBarons++ else awayBarons++
            is MatchEvent.Herald    -> if (e.side == Side.HOME) homeHeralds++ else awayHeralds++
            else -> Unit
        }
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
            withContext(Dispatchers.Main) { renderEvent(e) }
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

    // ── Renderização de eventos ──────────────────────────────────────────

    private fun renderEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.GameStart        -> onGameStart(event)
            is MatchEvent.PhaseAnnouncement -> binding.tvPhase.text = event.text
            is MatchEvent.Ban              -> onBan(event)
            is MatchEvent.Pick             -> onPick(event)
            is MatchEvent.GameTick         -> onGameTick(event)
            is MatchEvent.Kill             -> onKill(event)
            is MatchEvent.TowerDown        -> onTower(event)
            is MatchEvent.Inhibitor        -> onInhibitor(event)
            is MatchEvent.Dragon           -> onDragon(event)
            is MatchEvent.Baron            -> onBaron(event)
            is MatchEvent.Herald           -> onHerald(event)
            is MatchEvent.Buff             -> onBuff(event)
            is MatchEvent.GameEnd          -> onGameEnd(event)
            is MatchEvent.SeriesEnd        -> binding.tvPhase.text = getString(
                R.string.sim_series_winner, sideName(event.winnerSide),
                event.mapScore.first, event.mapScore.second
            )
        }
    }

    private fun onGameStart(event: MatchEvent.GameStart) {
        resetMapCounters()
        binding.tvGameNumber.text = getString(R.string.sim_map_label, event.gameNumber)
        binding.tvPhase.text      = getString(R.string.sim_phase_pickban)
        binding.tvGameTimer.text  = getString(R.string.sim_timer_zero)
        binding.layoutPickBan.visibility   = View.VISIBLE
        binding.layoutGameStats.visibility = View.GONE
        binding.containerHomeBans.removeAllViews()
        binding.containerAwayBans.removeAllViews()
        binding.containerHomePicks.removeAllViews()
        binding.containerAwayPicks.removeAllViews()
        feedAdapter.clear()
    }

    private fun onBan(event: MatchEvent.Ban) {
        val container = if (event.side == Side.HOME) binding.containerHomeBans else binding.containerAwayBans
        addPickBanChip(event.champion, isBan = true, container = container)
    }

    private fun onPick(event: MatchEvent.Pick) {
        val container = if (event.side == Side.HOME) binding.containerHomePicks else binding.containerAwayPicks
        addPickBanChip(
            getString(R.string.event_role_champion, event.role, event.champion),
            isBan = false, container = container
        )
        feedAdapter.add(FeedEntry(
            icon = getString(R.string.icon_pick),
            text = getString(R.string.event_pick, event.playerName, event.role, event.champion),
            accentColor = sideAccent(event.side)
        ))
    }

    private fun onGameTick(event: MatchEvent.GameTick) {
        if (binding.layoutPickBan.visibility == View.VISIBLE) {
            binding.layoutPickBan.visibility   = View.GONE
            binding.layoutGameStats.visibility = View.VISIBLE
            binding.tvPhase.text = getString(R.string.sim_phase_in_game, currentGameNum())
        }
        binding.tvGameTimer.text = TIMER_FORMAT.format(event.minute, event.second)
    }

    private fun onKill(event: MatchEvent.Kill) {
        if (event.killerSide == Side.HOME) homeKills++ else awayKills++
        updateScoreboard()
        feedAdapter.add(FeedEntry(
            icon = getString(R.string.icon_kill),
            text = getString(R.string.event_kill, event.time,
                event.killerName, event.killerChamp, event.victimName, event.victimChamp),
            accentColor = sideAccent(event.killerSide)
        ))
    }

    private fun onTower(event: MatchEvent.TowerDown) {
        if (event.side == Side.HOME) homeTowers++ else awayTowers++
        updateScoreboard()
        feedAdapter.add(FeedEntry(
            icon = getString(R.string.icon_tower),
            text = getString(R.string.event_tower, event.time, event.location, sideName(event.side)),
            accentColor = color(R.color.sim_tower_accent)
        ))
    }

    private fun onInhibitor(event: MatchEvent.Inhibitor) {
        feedAdapter.add(FeedEntry(
            icon = getString(R.string.icon_inhibitor),
            text = getString(R.string.event_inhibitor, event.time, event.location, sideName(event.side)),
            accentColor = color(R.color.sim_inhibitor_accent)
        ))
    }

    private fun onDragon(event: MatchEvent.Dragon) {
        if (event.side == Side.HOME) homeDragons++ else awayDragons++
        updateScoreboard()
        feedAdapter.add(FeedEntry(
            icon = getString(R.string.icon_dragon),
            text = getString(R.string.event_dragon, event.time, event.type, sideName(event.side)),
            accentColor = color(R.color.sim_dragon_accent)
        ))
    }

    private fun onBaron(event: MatchEvent.Baron) {
        if (event.side == Side.HOME) homeBarons++ else awayBarons++
        updateScoreboard()
        feedAdapter.add(FeedEntry(
            icon = getString(R.string.icon_baron),
            text = getString(R.string.event_baron, event.time, sideName(event.side)),
            accentColor = color(R.color.sim_baron_accent)
        ))
    }

    private fun onHerald(event: MatchEvent.Herald) {
        if (event.side == Side.HOME) homeHeralds++ else awayHeralds++
        updateScoreboard()
        feedAdapter.add(FeedEntry(
            icon = getString(R.string.icon_herald),
            text = getString(R.string.event_herald, event.time, sideName(event.side)),
            accentColor = color(R.color.sim_herald_accent)
        ))
    }

    private fun onBuff(event: MatchEvent.Buff) {
        val isRed = event.type == BUFF_TYPE_RED
        feedAdapter.add(FeedEntry(
            icon = getString(if (isRed) R.string.icon_buff_red else R.string.icon_buff_blue),
            text = getString(R.string.event_buff, event.time, event.playerName, event.type),
            accentColor = color(if (isRed) R.color.sim_buff_red else R.color.sim_buff_blue)
        ))
    }

    private fun onGameEnd(event: MatchEvent.GameEnd) {
        binding.tvSeriesScore.text = getString(R.string.result_score_format, homeMaps, awayMaps)
        binding.tvPhase.text = getString(R.string.sim_map_ended,
            event.gameNumber, sideName(event.winnerSide), event.durationMinutes)
        feedAdapter.add(FeedEntry(
            icon = getString(R.string.icon_match_end),
            text = getString(R.string.sim_map_summary, event.gameNumber,
                event.finalKills.first, event.finalKills.second, sideName(event.winnerSide)),
            accentColor = color(R.color.sim_match_end_accent)
        ))
    }

    // ── Helpers de UI ────────────────────────────────────────────────────

    private fun resetMapCounters() {
        homeKills = 0; awayKills = 0
        homeTowers = 0; awayTowers = 0
        homeDragons = 0; awayDragons = 0
        homeBarons = 0; awayBarons = 0
        homeHeralds = 0; awayHeralds = 0
        updateScoreboard()
    }

    private fun updateScoreboard() {
        binding.tvHomeKills.text = homeKills.toString()
        binding.tvAwayKills.text = awayKills.toString()
        binding.tvObjTowers.text  = getString(R.string.obj_towers_format,  homeTowers,  awayTowers)
        binding.tvObjDragons.text = getString(R.string.obj_dragons_format, homeDragons, awayDragons)
        binding.tvObjBarons.text  = getString(R.string.obj_barons_format,  homeBarons,  awayBarons)
        binding.tvObjHeralds.text = getString(R.string.obj_heralds_format, homeHeralds, awayHeralds)
    }

    private fun currentGameNum(): Int = homeMaps + awayMaps + 1

    /** Cria um chip visual no painel de pick/ban (banido = riscado, pick = dourado bold). */
    private fun addPickBanChip(label: String, isBan: Boolean, container: ViewGroup) {
        val tv = TextView(this).apply {
            text = if (isBan) getString(R.string.event_ban_prefix, label) else label
            textSize = resources.getDimension(R.dimen.sim_chip_text_size) /
                       resources.displayMetrics.scaledDensity
            val padH = resources.getDimensionPixelSize(R.dimen.sim_chip_padding_horizontal)
            val padV = resources.getDimensionPixelSize(R.dimen.sim_chip_padding_vertical)
            setPadding(padH, padV, padH, padV)
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = resources.getDimensionPixelSize(R.dimen.sim_chip_margin)
                setMargins(m, m, m, m)
            }
            if (isBan) {
                setTextColor(color(R.color.sim_pickban_chip_ban))
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                setTextColor(color(R.color.sim_pickban_chip_pick))
                setTypeface(null, Typeface.BOLD)
            }
            setBackgroundColor(color(R.color.sim_pickban_chip_bg))
        }
        container.addView(tv)
    }

    private fun sideName(side: Side): String =
        if (side == Side.HOME) binding.tvHomeName.text.toString()
        else binding.tvAwayName.text.toString()

    private fun sideAccent(side: Side): Int =
        if (side == Side.HOME) color(R.color.sim_home_accent) else color(R.color.sim_away_accent)

    private fun color(@ColorRes res: Int): Int = ContextCompat.getColor(this, res)

    // ── Aplicação do resultado ───────────────────────────────────────────

    private fun applyResult(homeMapsFinal: Int, awayMapsFinal: Int) {
        if (resultApplied) return
        resultApplied = true

        val seriesFinished = homeMapsFinal >= GameConstants.Series.MAPS_TO_WIN ||
                             awayMapsFinal >= GameConstants.Series.MAPS_TO_WIN

        match.homeScore = homeMapsFinal
        match.awayScore = awayMapsFinal
        if (seriesFinished) {
            match.played      = true
            match.pickBanPlan = null
        }

        val gs     = GameRepository.current()
        val winner = if (homeMapsFinal > awayMapsFinal) match.homeTeamId else match.awayTeamId
        val isMine = match.homeTeamId == gs.managerTeamId || match.awayTeamId == gs.managerTeamId

        val prize = if (seriesFinished && isMine) calculatePrize(winner, homeMapsFinal, awayMapsFinal, gs.managerTeamId) else 0L
        if (prize > 0L) gs.budget += prize

        val today    = LocalDate.parse(gs.currentDate)
        val matchDay = LocalDate.parse(match.date)
        if (seriesFinished && today.isBefore(matchDay)) gs.currentDate = match.date

        if (seriesFinished) {
            GameRepository.log("MATCH",
                "Rodada ${match.round}: ${binding.tvHomeName.text} $homeMapsFinal-$awayMapsFinal ${binding.tvAwayName.text}")
        }
        GameRepository.save(applicationContext)

        launchResultActivity(homeMapsFinal, awayMapsFinal, winner, prize, seriesFinished, gs.managerTeamId)
        finish()
    }

    private fun calculatePrize(winner: String, homeMaps: Int, awayMaps: Int, managerId: String): Long {
        return if (winner == managerId) {
            val mapsWon = maxOf(homeMaps, awayMaps)
            GameConstants.Economy.PRIZE_PER_SERIES_WIN + GameConstants.Economy.PRIZE_PER_MAP_WIN * mapsWon
        } else {
            val myMaps = if (match.homeTeamId == managerId) homeMaps else awayMaps
            GameConstants.Economy.PRIZE_PER_MAP_WIN * myMaps
        }
    }

    private fun launchResultActivity(
        homeMapsFinal: Int, awayMapsFinal: Int, winner: String,
        prize: Long, seriesFinished: Boolean, managerId: String
    ) {
        val isPlayerHome   = match.homeTeamId == managerId
        val opponentTeamId = if (isPlayerHome) match.awayTeamId else match.homeTeamId

        startActivity(Intent(this, MatchResultActivity::class.java).apply {
            putExtra(MatchResultActivity.EXTRA_HOME_NAME,        binding.tvHomeName.text.toString())
            putExtra(MatchResultActivity.EXTRA_AWAY_NAME,        binding.tvAwayName.text.toString())
            putExtra(MatchResultActivity.EXTRA_HOME_ID,          match.homeTeamId)
            putExtra(MatchResultActivity.EXTRA_AWAY_ID,          match.awayTeamId)
            putExtra(MatchResultActivity.EXTRA_HOME_SCORE,       homeMapsFinal)
            putExtra(MatchResultActivity.EXTRA_AWAY_SCORE,       awayMapsFinal)
            putExtra(MatchResultActivity.EXTRA_WINNER_ID,        winner)
            putExtra(MatchResultActivity.EXTRA_MANAGER_ID,       managerId)
            putExtra(MatchResultActivity.EXTRA_HOME_KILLS,       homeKills)
            putExtra(MatchResultActivity.EXTRA_AWAY_KILLS,       awayKills)
            putExtra(MatchResultActivity.EXTRA_HOME_TOWERS,      homeTowers)
            putExtra(MatchResultActivity.EXTRA_AWAY_TOWERS,      awayTowers)
            putExtra(MatchResultActivity.EXTRA_HOME_DRAGONS,     homeDragons)
            putExtra(MatchResultActivity.EXTRA_AWAY_DRAGONS,     awayDragons)
            putExtra(MatchResultActivity.EXTRA_HOME_BARONS,      homeBarons)
            putExtra(MatchResultActivity.EXTRA_AWAY_BARONS,      awayBarons)
            putExtra(MatchResultActivity.EXTRA_PRIZE,            prize)
            putExtra(MatchResultActivity.EXTRA_MATCH_ID,         match.id)
            putExtra(MatchResultActivity.EXTRA_MAP_NUMBER,       homeMaps + awayMaps)
            putExtra(MatchResultActivity.EXTRA_PLAYER_TEAM_ID,   managerId)
            putExtra(MatchResultActivity.EXTRA_OPPONENT_TEAM_ID, opponentTeamId)
            putExtra(MatchResultActivity.EXTRA_SERIES_FINISHED,  seriesFinished)
        })
    }

    // ── Feed adapter ─────────────────────────────────────────────────────

    private data class FeedEntry(val icon: String, val text: String, val accentColor: Int)

    private inner class FeedAdapter : RecyclerView.Adapter<FeedAdapter.VH>() {
        private val items = mutableListOf<FeedEntry>()

        fun add(e: FeedEntry) {
            items.add(0, e)
            if (items.size > GameConstants.Simulation.FEED_MAX_ITEMS) {
                items.removeAt(items.size - 1)
            }
            notifyItemInserted(0)
            binding.recyclerFeed.scrollToPosition(0)
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

    companion object {
        const val EXTRA_MATCH_ID = "extra_match_id"

        private const val SPEED_1X = 1f
        private const val SPEED_2X = 2f
        private const val SPEED_4X = 4f

        private const val BUFF_TYPE_RED = "Red"
        private const val TIMER_FORMAT  = "%02d:%02d"
    }
}
