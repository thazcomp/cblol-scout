package com.cblol.scout.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cblol.scout.R
import com.cblol.scout.data.Match
import com.cblol.scout.data.MatchEvent
import com.cblol.scout.data.Side
import com.cblol.scout.databinding.ActivityMatchSimulationBinding
import com.cblol.scout.game.GameEngine
import com.cblol.scout.game.GameRepository
import com.cblol.scout.game.LiveMatchEngine
import com.cblol.scout.util.TeamColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Player de partida acelerado. Lê o ID da partida via Intent extra, gera a timeline
 * com LiveMatchEngine e toca os eventos com delays escalonados:
 *  - Pick/Ban: ~600ms por evento
 *  - Tick de tempo de jogo: ~700ms por minuto simulado
 *  - Eventos do jogo: aparecem instantaneamente quando o tick chega no mesmo minuto
 *
 * Ao final aplica o resultado no GameState (mesma lógica de prêmio do GameEngine).
 *
 * Suporta velocidades 1x / 2x / 4x via botão de acelerador.
 */
class MatchSimulationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MATCH_ID = "extra_match_id"
    }

    private lateinit var binding: ActivityMatchSimulationBinding
    private lateinit var match: Match
    private lateinit var feedAdapter: FeedAdapter

    private var homeKills = 0
    private var awayKills = 0
    private var homeMaps = 0
    private var awayMaps = 0

    // contadores por mapa
    private var homeTowers = 0; private var awayTowers = 0
    private var homeDragons = 0; private var awayDragons = 0
    private var homeBarons = 0; private var awayBarons = 0
    private var homeHeralds = 0; private var awayHeralds = 0

    private var speed = 1f  // 1x, 2x, 4x
    private var resultApplied = false  // guard contra applyResult duplo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchSimulationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { confirmExit() }

        if (GameRepository.load(applicationContext) == null) { finish(); return }

        val matchId = intent.getStringExtra(EXTRA_MATCH_ID)
        val gs = GameRepository.current()
        val target = gs.matches.find { it.id == matchId }
        // Aceita partidas ainda não jogadas OU que tenham um PickBanPlan pendente
        // (pick & ban manual recém-feito, partida ainda não foi simulada)
        if (target == null) { finish(); return }
        // Série encerrada → nada mais a simular
        if (target.played) { finish(); return }
        match = target

        val snap = GameRepository.snapshot(applicationContext)
        val home = snap.times.find { it.id == match.homeTeamId }!!
        val away = snap.times.find { it.id == match.awayTeamId }!!

        binding.tvHomeName.text = home.nome
        binding.tvAwayName.text = away.nome
        binding.viewHomeBar.setBackgroundColor(TeamColors.forTeam(home.id))
        binding.viewAwayBar.setBackgroundColor(TeamColors.forTeam(away.id))

        feedAdapter = FeedAdapter()
        binding.recyclerFeed.layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true; stackFromEnd = false
        }
        binding.recyclerFeed.adapter = feedAdapter

        binding.btnSpeed.setOnClickListener { cycleSpeed() }

        startSimulation()
    }

    private fun cycleSpeed() {
        speed = when (speed) {
            1f -> 2f
            2f -> 4f
            else -> 1f
        }
        binding.btnSpeed.text = "${speed.toInt()}x"
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("Sair?")
            .setMessage("Deseja pular direto para o resultado?")
            .setPositiveButton("Pular pro fim") { _, _ ->
                lifecycleScope.launch {
                    skipToResult()
                }
            }
            .setNegativeButton("Continuar assistindo", null)
            .show()
    }

    /**
     * Gera a série completa em background sem exibir os eventos,
     * acumula os contadores de stats e chama applyResult normalmente
     * para que a MatchResultActivity seja exibida.
     */
    private suspend fun skipToResult() {
        val partialHome = match.homeScore
        val partialAway = match.awayScore
        val gameNumber  = partialHome + partialAway + 1

        val result = withContext(Dispatchers.Default) {
            LiveMatchEngine.generateSingleMap(applicationContext, match, gameNumber)
        }
        for (e in result.events) {
            when (e) {
                is MatchEvent.GameStart  -> resetMapCounters()
                is MatchEvent.Kill       -> if (e.killerSide == Side.HOME) homeKills++ else awayKills++
                is MatchEvent.TowerDown  -> if (e.side == Side.HOME) homeTowers++ else awayTowers++
                is MatchEvent.Dragon     -> if (e.side == Side.HOME) homeDragons++ else awayDragons++
                is MatchEvent.Baron      -> if (e.side == Side.HOME) homeBarons++ else awayBarons++
                is MatchEvent.Herald     -> if (e.side == Side.HOME) homeHeralds++ else awayHeralds++
                else -> Unit
            }
        }
        val finalHome = partialHome + if (result.homeWon) 1 else 0
        val finalAway = partialAway + if (!result.homeWon) 1 else 0
        withContext(Dispatchers.Main) {
            applyResult(finalHome, finalAway)
        }
    }

    private fun startSimulation() {
        binding.tvPhase.text = getString(R.string.generating_timeline)
        // Placar parcial da série (mapa anterior já jogado)
        homeMaps = match.homeScore
        awayMaps = match.awayScore
        binding.tvSeriesScore.text = "$homeMaps - $awayMaps"
        val gameNumber = homeMaps + awayMaps + 1

        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                LiveMatchEngine.generateSingleMap(applicationContext, match, gameNumber)
            }
            if (result.homeWon) homeMaps++ else awayMaps++
            playEvents(result.events)
            withContext(Dispatchers.Main) {
                applyResult(homeMaps, awayMaps)
            }
        }
    }



    private suspend fun playEvents(events: List<MatchEvent>) {
        for (e in events) {
            withContext(Dispatchers.Main) { renderEvent(e) }
            delay(delayFor(e))
        }
    }

    private fun delayFor(event: MatchEvent): Long {
        val base: Long = when (event) {
            is MatchEvent.GameStart, is MatchEvent.PhaseAnnouncement -> 1200
            is MatchEvent.Ban -> 450
            is MatchEvent.Pick -> 600
            is MatchEvent.GameTick -> 700
            is MatchEvent.Kill, is MatchEvent.TowerDown, is MatchEvent.Dragon,
            is MatchEvent.Baron, is MatchEvent.Herald, is MatchEvent.Inhibitor,
            is MatchEvent.Buff -> 350
            is MatchEvent.GameEnd -> 2200
            is MatchEvent.SeriesEnd -> 0
        }
        return (base / speed).toLong()
    }

    private fun renderEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.GameStart -> {
                resetMapCounters()
                binding.tvGameNumber.text = "Mapa ${event.gameNumber}"
                binding.tvPhase.text = "Pick & Ban"
                binding.tvGameTimer.text = "00:00"
                binding.layoutPickBan.visibility = View.VISIBLE
                binding.layoutGameStats.visibility = View.GONE
                binding.containerHomeBans.removeAllViews()
                binding.containerAwayBans.removeAllViews()
                binding.containerHomePicks.removeAllViews()
                binding.containerAwayPicks.removeAllViews()
                feedAdapter.clear()
            }
            is MatchEvent.PhaseAnnouncement -> {
                binding.tvPhase.text = event.text
            }
            is MatchEvent.Ban -> {
                addPickBanChip(event.champion, isBan = true,
                    container = if (event.side == Side.HOME) binding.containerHomeBans else binding.containerAwayBans)
            }
            is MatchEvent.Pick -> {
                addPickBanChip("${event.role} · ${event.champion}", isBan = false,
                    container = if (event.side == Side.HOME) binding.containerHomePicks else binding.containerAwayPicks)
                feedAdapter.add(FeedEntry(
                    icon = "🎯", text = "${event.playerName} (${event.role}) escolhe ${event.champion}",
                    accentColor = if (event.side == Side.HOME) Color.parseColor("#0AC8B9") else Color.parseColor("#E84057")
                ))
            }
            is MatchEvent.GameTick -> {
                if (binding.layoutPickBan.visibility == View.VISIBLE) {
                    binding.layoutPickBan.visibility = View.GONE
                    binding.layoutGameStats.visibility = View.VISIBLE
                    binding.tvPhase.text = "Em jogo · Mapa ${currentGameNum()}"
                }
                binding.tvGameTimer.text = "%02d:%02d".format(event.minute, event.second)
            }
            is MatchEvent.Kill -> {
                if (event.killerSide == Side.HOME) homeKills++ else awayKills++
                updateScoreboard()
                feedAdapter.add(FeedEntry(
                    icon = "⚔️",
                    text = "${event.time}  ${event.killerName} (${event.killerChamp}) abateu ${event.victimName} (${event.victimChamp})",
                    accentColor = if (event.killerSide == Side.HOME) Color.parseColor("#0AC8B9") else Color.parseColor("#E84057")
                ))
            }
            is MatchEvent.TowerDown -> {
                if (event.side == Side.HOME) homeTowers++ else awayTowers++
                updateScoreboard()
                feedAdapter.add(FeedEntry(
                    icon = "🏰",
                    text = "${event.time}  Torre ${event.location} destruída por ${sideName(event.side)}",
                    accentColor = Color.parseColor("#C8AA6E")
                ))
            }
            is MatchEvent.Inhibitor -> {
                feedAdapter.add(FeedEntry(
                    icon = "💎",
                    text = "${event.time}  Inibidor ${event.location} destruído por ${sideName(event.side)}",
                    accentColor = Color.parseColor("#FFB800")
                ))
            }
            is MatchEvent.Dragon -> {
                if (event.side == Side.HOME) homeDragons++ else awayDragons++
                updateScoreboard()
                feedAdapter.add(FeedEntry(
                    icon = "🐉",
                    text = "${event.time}  Drake ${event.type} para ${sideName(event.side)}",
                    accentColor = Color.parseColor("#0AC8B9")
                ))
            }
            is MatchEvent.Baron -> {
                if (event.side == Side.HOME) homeBarons++ else awayBarons++
                updateScoreboard()
                feedAdapter.add(FeedEntry(
                    icon = "🦣",
                    text = "${event.time}  BARON NASHOR para ${sideName(event.side)}",
                    accentColor = Color.parseColor("#B19CD9")
                ))
            }
            is MatchEvent.Herald -> {
                if (event.side == Side.HOME) homeHeralds++ else awayHeralds++
                updateScoreboard()
                feedAdapter.add(FeedEntry(
                    icon = "🦅",
                    text = "${event.time}  Arauto do Vale para ${sideName(event.side)}",
                    accentColor = Color.parseColor("#C89B3C")
                ))
            }
            is MatchEvent.Buff -> {
                val color = if (event.type == "Red") "#E84057" else "#0AC8B9"
                feedAdapter.add(FeedEntry(
                    icon = if (event.type == "Red") "🔴" else "🔵",
                    text = "${event.time}  ${event.playerName} pegou ${event.type} buff",
                    accentColor = Color.parseColor(color)
                ))
            }
            is MatchEvent.GameEnd -> {
                // homeMaps/awayMaps já foram incrementados em startSimulation antes de playEvents
                binding.tvSeriesScore.text = "$homeMaps - $awayMaps"
                binding.tvPhase.text =
                    "Mapa ${event.gameNumber} encerrado · ${sideName(event.winnerSide)} venceu (${event.durationMinutes}min)"
                feedAdapter.add(FeedEntry(
                    icon = "🏁",
                    text = "Mapa ${event.gameNumber} terminou: ${event.finalKills.first}-${event.finalKills.second} · vitória de ${sideName(event.winnerSide)}",
                    accentColor = Color.parseColor("#C89B3C")
                ))
            }
            is MatchEvent.SeriesEnd -> {
                binding.tvPhase.text = "🏆 ${sideName(event.winnerSide)} vence a série ${event.mapScore.first}-${event.mapScore.second}"
            }
        }
    }

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
        binding.tvObjTowers.text = "🏰 $homeTowers-$awayTowers"
        binding.tvObjDragons.text = "🐉 $homeDragons-$awayDragons"
        binding.tvObjBarons.text = "🦣 $homeBarons-$awayBarons"
        binding.tvObjHeralds.text = "🦅 $homeHeralds-$awayHeralds"
    }

    private fun currentGameNum(): Int = homeMaps + awayMaps + 1

    /** Adiciona um chip visual ao painel de pick/ban (banido = riscado vermelho, pick = dourado). */
    private fun addPickBanChip(label: String, isBan: Boolean, container: ViewGroup) {
        val tv = TextView(this).apply {
            text = if (isBan) "❌ $label" else label
            textSize = 11f
            setPadding(10, 4, 10, 4)
            val params = android.view.ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(2, 2, 2, 2)
            layoutParams = params
            if (isBan) {
                setTextColor(Color.parseColor("#A09B8C"))
                paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                setTextColor(Color.parseColor("#C89B3C"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            setBackgroundColor(Color.parseColor("#1E2D40"))
        }
        container.addView(tv)
    }

    private fun sideName(side: Side): String =
        if (side == Side.HOME) binding.tvHomeName.text.toString()
        else binding.tvAwayName.text.toString()

    private fun applyResult(homeMapsFinal: Int, awayMapsFinal: Int) {
        if (resultApplied) return
        resultApplied = true

        val seriesFinished = homeMapsFinal >= 2 || awayMapsFinal >= 2

        // Só marca como jogada quando a série terminar (2-0 ou 2-1)
        // Para mapas intermediários (1-0), persiste apenas o placar parcial
        match.homeScore = homeMapsFinal
        match.awayScore = awayMapsFinal
        if (seriesFinished) {
            match.played     = true
            match.pickBanPlan = null  // limpa só quando a série termina
        }

        val gs     = GameRepository.current()
        val snap   = GameRepository.snapshot(applicationContext)
        val winner = if (homeMapsFinal > awayMapsFinal) match.homeTeamId else match.awayTeamId
        val isMine = match.homeTeamId == gs.managerTeamId || match.awayTeamId == gs.managerTeamId

        var prize = 0L
        if (seriesFinished && isMine) {
            if (winner == gs.managerTeamId) {
                val mapsWon = maxOf(homeMapsFinal, awayMapsFinal)
                prize = GameEngine.PRIZE_PER_SERIES_WIN + GameEngine.PRIZE_PER_MAP_WIN * mapsWon
            } else {
                val myMaps = if (match.homeTeamId == gs.managerTeamId) homeMapsFinal else awayMapsFinal
                prize = GameEngine.PRIZE_PER_MAP_WIN * myMaps
            }
            gs.budget += prize
        }

        val today    = LocalDate.parse(gs.currentDate)
        val matchDay = LocalDate.parse(match.date)
        if (seriesFinished && today.isBefore(matchDay)) gs.currentDate = match.date

        if (seriesFinished) {
            GameRepository.log(
                "MATCH",
                "Rodada ${match.round}: ${binding.tvHomeName.text} $homeMapsFinal-$awayMapsFinal ${binding.tvAwayName.text}"
            )
        }
        GameRepository.save(applicationContext)

        val isPlayerHome   = match.homeTeamId == gs.managerTeamId
        val playerTeamId   = gs.managerTeamId
        val opponentTeamId = if (isPlayerHome) match.awayTeamId else match.homeTeamId

        val homeName = binding.tvHomeName.text.toString()
        val awayName = binding.tvAwayName.text.toString()
        startActivity(
            Intent(this, MatchResultActivity::class.java).apply {
                putExtra(MatchResultActivity.EXTRA_HOME_NAME,     homeName)
                putExtra(MatchResultActivity.EXTRA_AWAY_NAME,     awayName)
                putExtra(MatchResultActivity.EXTRA_HOME_ID,       match.homeTeamId)
                putExtra(MatchResultActivity.EXTRA_AWAY_ID,       match.awayTeamId)
                putExtra(MatchResultActivity.EXTRA_HOME_SCORE,    homeMapsFinal)
                putExtra(MatchResultActivity.EXTRA_AWAY_SCORE,    awayMapsFinal)
                putExtra(MatchResultActivity.EXTRA_WINNER_ID,     winner)
                putExtra(MatchResultActivity.EXTRA_MANAGER_ID,    gs.managerTeamId)
                putExtra(MatchResultActivity.EXTRA_HOME_KILLS,    homeKills)
                putExtra(MatchResultActivity.EXTRA_AWAY_KILLS,    awayKills)
                putExtra(MatchResultActivity.EXTRA_HOME_TOWERS,   homeTowers)
                putExtra(MatchResultActivity.EXTRA_AWAY_TOWERS,   awayTowers)
                putExtra(MatchResultActivity.EXTRA_HOME_DRAGONS,  homeDragons)
                putExtra(MatchResultActivity.EXTRA_AWAY_DRAGONS,  awayDragons)
                putExtra(MatchResultActivity.EXTRA_HOME_BARONS,   homeBarons)
                putExtra(MatchResultActivity.EXTRA_AWAY_BARONS,   awayBarons)
                putExtra(MatchResultActivity.EXTRA_PRIZE,         prize)
                putExtra(MatchResultActivity.EXTRA_MATCH_ID,         match.id)
                putExtra(MatchResultActivity.EXTRA_MAP_NUMBER,        homeMaps + awayMaps)
                putExtra(MatchResultActivity.EXTRA_PLAYER_TEAM_ID,    playerTeamId)
                putExtra(MatchResultActivity.EXTRA_OPPONENT_TEAM_ID,  opponentTeamId)
                putExtra(MatchResultActivity.EXTRA_SERIES_FINISHED,   seriesFinished)
            }
        )
        finish()
    }

    override fun onSupportNavigateUp(): Boolean { confirmExit(); return true }

    // ───── Feed adapter ─────

    private data class FeedEntry(val icon: String, val text: String, val accentColor: Int)

    private inner class FeedAdapter : RecyclerView.Adapter<FeedAdapter.VH>() {
        private val items = mutableListOf<FeedEntry>()

        fun add(e: FeedEntry) {
            items.add(0, e)
            if (items.size > 60) items.removeAt(items.size - 1)
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH = VH(
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Pick & ban agora é tratado pela ScheduleActivity via PickBanActivity.
        // MatchSimulationActivity apenas simula a partida sem pick & ban próprio.
    }
}