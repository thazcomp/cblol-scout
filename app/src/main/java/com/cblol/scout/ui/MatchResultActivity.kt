package com.cblol.scout.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.animation.doOnEnd
import com.cblol.scout.R
import com.cblol.scout.databinding.ActivityMatchResultBinding
import com.cblol.scout.util.TeamColors

/**
 * MatchResultActivity — tela animada de resultado pós-partida.
 *
 * Extras esperados via Intent:
 *   EXTRA_HOME_NAME    String  nome do time home
 *   EXTRA_AWAY_NAME    String  nome do time away
 *   EXTRA_HOME_ID      String  id do time home (para cor)
 *   EXTRA_AWAY_ID      String  id do time away (para cor)
 *   EXTRA_HOME_SCORE   Int     mapas vencidos pelo home
 *   EXTRA_AWAY_SCORE   Int     mapas vencidos pelo away
 *   EXTRA_WINNER_ID    String  id do time vencedor
 *   EXTRA_MANAGER_ID   String  id do time do jogador
 *   EXTRA_HOME_KILLS   Int     total de kills home na série
 *   EXTRA_AWAY_KILLS   Int     total de kills away na série
 *   EXTRA_HOME_TOWERS  Int
 *   EXTRA_AWAY_TOWERS  Int
 *   EXTRA_HOME_DRAGONS Int
 *   EXTRA_AWAY_DRAGONS Int
 *   EXTRA_HOME_BARONS  Int
 *   EXTRA_AWAY_BARONS  Int
 *   EXTRA_PRIZE        Long    prêmio em R$ (0 se não for partida do jogador)
 */
class MatchResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HOME_NAME     = "res_home_name"
        const val EXTRA_AWAY_NAME     = "res_away_name"
        const val EXTRA_HOME_ID       = "res_home_id"
        const val EXTRA_AWAY_ID       = "res_away_id"
        const val EXTRA_HOME_SCORE    = "res_home_score"
        const val EXTRA_AWAY_SCORE    = "res_away_score"
        const val EXTRA_WINNER_ID     = "res_winner_id"
        const val EXTRA_MANAGER_ID    = "res_manager_id"
        const val EXTRA_HOME_KILLS    = "res_home_kills"
        const val EXTRA_AWAY_KILLS    = "res_away_kills"
        const val EXTRA_HOME_TOWERS   = "res_home_towers"
        const val EXTRA_AWAY_TOWERS   = "res_away_towers"
        const val EXTRA_HOME_DRAGONS  = "res_home_dragons"
        const val EXTRA_AWAY_DRAGONS  = "res_away_dragons"
        const val EXTRA_HOME_BARONS   = "res_home_barons"
        const val EXTRA_AWAY_BARONS   = "res_away_barons"
        const val EXTRA_PRIZE         = "res_prize"
        // Dados da série em andamento (BO3)
        const val EXTRA_MATCH_ID      = "res_match_id"
        const val EXTRA_MAP_NUMBER    = "res_map_number"    // mapa recém jogado (1 ou 2)
        const val EXTRA_PLAYER_TEAM_ID   = "res_player_team_id"
        const val EXTRA_OPPONENT_TEAM_ID = "res_opponent_team_id"
        const val EXTRA_SERIES_FINISHED  = "res_series_finished" // true = série encerrada
    }

    private lateinit var binding: ActivityMatchResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val homeName    = intent.getStringExtra(EXTRA_HOME_NAME)    ?: "Home"
        val awayName    = intent.getStringExtra(EXTRA_AWAY_NAME)    ?: "Away"
        val homeId      = intent.getStringExtra(EXTRA_HOME_ID)      ?: ""
        val awayId      = intent.getStringExtra(EXTRA_AWAY_ID)      ?: ""
        val homeScore   = intent.getIntExtra(EXTRA_HOME_SCORE, 0)
        val awayScore   = intent.getIntExtra(EXTRA_AWAY_SCORE, 0)
        val winnerId    = intent.getStringExtra(EXTRA_WINNER_ID)    ?: ""
        val managerId   = intent.getStringExtra(EXTRA_MANAGER_ID)   ?: ""
        val homeKills   = intent.getIntExtra(EXTRA_HOME_KILLS, 0)
        val awayKills   = intent.getIntExtra(EXTRA_AWAY_KILLS, 0)
        val homeTowers  = intent.getIntExtra(EXTRA_HOME_TOWERS, 0)
        val awayTowers  = intent.getIntExtra(EXTRA_AWAY_TOWERS, 0)
        val homeDragons = intent.getIntExtra(EXTRA_HOME_DRAGONS, 0)
        val awayDragons = intent.getIntExtra(EXTRA_AWAY_DRAGONS, 0)
        val homeBarons  = intent.getIntExtra(EXTRA_HOME_BARONS, 0)
        val awayBarons  = intent.getIntExtra(EXTRA_AWAY_BARONS, 0)
        val prize       = intent.getLongExtra(EXTRA_PRIZE, 0L)

        val isMyMatch  = managerId == homeId || managerId == awayId
        val playerWon  = isMyMatch && winnerId == managerId
        val playerLost = isMyMatch && winnerId != managerId

        // ── Preenche views ────────────────────────────────────────────────
        binding.tvHomeName.text   = homeName
        binding.tvAwayName.text   = awayName
        binding.tvSeriesScore.text = "$homeScore — $awayScore"

        binding.viewHomeColor.setBackgroundColor(TeamColors.forTeam(homeId))
        binding.viewAwayColor.setBackgroundColor(TeamColors.forTeam(awayId))

        val (icon, label, labelColor, bgColor) = when {
            !isMyMatch          -> ResultDisplay("🎮", "PARTIDA ENCERRADA", "#C9AA71",  "#0A0A14")
            playerWon           -> ResultDisplay("🏆", "VITÓRIA",            "#FFD700",  "#0A1F0A")
            else                -> ResultDisplay("💔", "DERROTA",             "#E84057",  "#1F0A0A")
        }

        binding.tvResultIcon.text  = icon
        binding.tvResultLabel.text = label
        binding.tvResultLabel.setTextColor(android.graphics.Color.parseColor(labelColor))

        // Fundo colorido sutil
        val bgGradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(android.graphics.Color.parseColor(bgColor),
                       android.graphics.Color.parseColor("#0A0A14"))
        )
        binding.viewResultBg.background = bgGradient

        // Stats
        binding.tvStatHomeKills.text   = homeKills.toString()
        binding.tvStatAwayKills.text   = awayKills.toString()
        binding.tvStatHomeTowers.text  = homeTowers.toString()
        binding.tvStatAwayTowers.text  = awayTowers.toString()
        binding.tvStatHomeDragons.text = homeDragons.toString()
        binding.tvStatAwayDragons.text = awayDragons.toString()
        binding.tvStatHomeBarons.text  = homeBarons.toString()
        binding.tvStatAwayBarons.text  = awayBarons.toString()

        // Barras proporcionais de stat
        setStatBars(binding.barKillsHome,   binding.barKillsAway,   homeKills,   awayKills)
        setStatBars(binding.barTowersHome,  binding.barTowersAway,  homeTowers,  awayTowers)
        setStatBars(binding.barDragonsHome, binding.barDragonsAway, homeDragons, awayDragons)
        setStatBars(binding.barBaronsHome,  binding.barBaronsAway,  homeBarons,  awayBarons)

        // Prêmio
        if (prize > 0L && isMyMatch) {
            binding.cardPrize.visibility = View.VISIBLE
            binding.tvPrize.text         = "+ R$ ${"%,d".format(prize)}"
        } else {
            binding.cardPrize.visibility = View.GONE
        }

        // Dados de série
        val matchId        = intent.getStringExtra(EXTRA_MATCH_ID)       ?: ""
        val mapNumber      = intent.getIntExtra(EXTRA_MAP_NUMBER, 1)
        val playerTeamId   = intent.getStringExtra(EXTRA_PLAYER_TEAM_ID)   ?: managerId
        val opponentTeamId = intent.getStringExtra(EXTRA_OPPONENT_TEAM_ID) ?: ""
        val seriesFinished = intent.getBooleanExtra(EXTRA_SERIES_FINISHED, true)

        // Botão continuar
        binding.btnContinue.setOnClickListener {
            if (seriesFinished || matchId.isEmpty()) {
                // Série encerrada → volta ao hub
                startActivity(Intent(this, ManagerHubActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
            } else {
                // Série continua → pergunta como o jogador quer fazer o próximo mapa
                val nextMap = mapNumber + 1
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Mapa $nextMap")
                    .setMessage("Como deseja jogar o próximo mapa?")
                    .setPositiveButton("Fazer Pick & Ban") { _, _ ->
                        finish()
                        startActivity(
                            Intent(this, PickBanRouterActivity::class.java).apply {
                                putExtra(PickBanRouterActivity.EXTRA_MATCH_ID,       matchId)
                                putExtra(PickBanRouterActivity.EXTRA_MAP_NUMBER,     nextMap)
                                putExtra(PickBanRouterActivity.EXTRA_PLAYER_TEAM_ID, playerTeamId)
                                putExtra(PickBanRouterActivity.EXTRA_OPPONENT_ID,    opponentTeamId)
                            }
                        )
                    }
                    .setNeutralButton("Simular direto") { _, _ ->
                        finish()
                        startActivity(
                            Intent(this, MatchSimulationActivity::class.java)
                                .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, matchId)
                        )
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }

        // Inicia a sequência de animações
        runEntranceAnimations(playerWon, playerLost)
    }

    // ── Animações de entrada ───────────────────────────────────────────────
    private fun runEntranceAnimations(playerWon: Boolean, playerLost: Boolean) {

        // 1. Fundo: fade in rápido
        ObjectAnimator.ofFloat(binding.viewResultBg, "alpha", 0f, 0.6f).apply {
            duration     = 500
            interpolator = DecelerateInterpolator()
            start()
        }

        // 2. Ícone: escala bounce após 200ms
        binding.tvResultIcon.postDelayed({
            binding.tvResultIcon.scaleX = 0f
            binding.tvResultIcon.scaleY = 0f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(binding.tvResultIcon, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(binding.tvResultIcon, "scaleX", 0f, 1f),
                    ObjectAnimator.ofFloat(binding.tvResultIcon, "scaleY", 0f, 1f)
                )
                duration     = 600
                interpolator = BounceInterpolator()
                start()
            }
        }, 200)

        // 3. Label resultado: slide de baixo + fade após 600ms
        binding.tvResultLabel.postDelayed({
            binding.tvResultLabel.translationY = 40f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(binding.tvResultLabel, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(binding.tvResultLabel, "translationY", 40f, 0f)
                )
                duration     = 500
                interpolator = DecelerateInterpolator()
                start()
            }
        }, 600)

        // 4. Placar: fade + overshoot após 1000ms
        binding.llScoreboard.postDelayed({
            binding.llScoreboard.scaleX = 0.7f
            binding.llScoreboard.scaleY = 0.7f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(binding.llScoreboard, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(binding.llScoreboard, "scaleX", 0.7f, 1f),
                    ObjectAnimator.ofFloat(binding.llScoreboard, "scaleY", 0.7f, 1f)
                )
                duration     = 500
                interpolator = OvershootInterpolator(1.5f)
                start()
            }
        }, 1000)

        // 5. Contadores de score animados (conta de 0 até o valor)
        binding.llScoreboard.postDelayed({
            animateCounter(binding.tvStatHomeKills,   0, intent.getIntExtra(EXTRA_HOME_KILLS,   0), 800)
            animateCounter(binding.tvStatAwayKills,   0, intent.getIntExtra(EXTRA_AWAY_KILLS,   0), 800)
            animateCounter(binding.tvStatHomeTowers,  0, intent.getIntExtra(EXTRA_HOME_TOWERS,  0), 800)
            animateCounter(binding.tvStatAwayTowers,  0, intent.getIntExtra(EXTRA_AWAY_TOWERS,  0), 800)
            animateCounter(binding.tvStatHomeDragons, 0, intent.getIntExtra(EXTRA_HOME_DRAGONS, 0), 800)
            animateCounter(binding.tvStatAwayDragons, 0, intent.getIntExtra(EXTRA_AWAY_DRAGONS, 0), 800)
            animateCounter(binding.tvStatHomeBarons,  0, intent.getIntExtra(EXTRA_HOME_BARONS,  0), 800)
            animateCounter(binding.tvStatAwayBarons,  0, intent.getIntExtra(EXTRA_AWAY_BARONS,  0), 800)
        }, 1400)

        // 6. Cards de stats e prêmio: slide up sequencial
        fadeSlideIn(binding.cardStats,  delay = 1500)
        fadeSlideIn(binding.cardPrize,  delay = 1800)
        fadeSlideIn(binding.btnContinue, delay = 2100)

        // 7. Se vitória: pulso dourado no label
        if (playerWon) {
            binding.tvResultLabel.postDelayed({
                val pulse = ObjectAnimator.ofFloat(binding.tvResultLabel, "scaleX", 1f, 1.1f, 1f)
                pulse.repeatCount = 3
                pulse.duration    = 400
                pulse.interpolator = AccelerateDecelerateInterpolator()
                val pulseY = ObjectAnimator.ofFloat(binding.tvResultLabel, "scaleY", 1f, 1.1f, 1f)
                pulseY.repeatCount = 3
                pulseY.duration    = 400
                pulseY.interpolator = AccelerateDecelerateInterpolator()
                AnimatorSet().apply { playTogether(pulse, pulseY); start() }
            }, 1200)
        }
    }

    private fun fadeSlideIn(view: View, delay: Long) {
        view.postDelayed({
            view.translationY = 30f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(view, "translationY", 30f, 0f)
                )
                duration     = 450
                interpolator = DecelerateInterpolator()
                start()
            }
        }, delay)
    }

    private fun animateCounter(tv: TextView, from: Int, to: Int, duration: Long) {
        if (to == 0) { tv.text = "0"; return }
        ValueAnimator.ofInt(from, to).apply {
            this.duration    = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { tv.text = (it.animatedValue as Int).toString() }
            start()
        }
    }

    // ── Barras proporcionais ──────────────────────────────────────────────
    private fun setStatBars(homeBar: View, awayBar: View, homeVal: Int, awayVal: Int) {
        val total = (homeVal + awayVal).coerceAtLeast(1)
        val homeWeight = homeVal.toFloat() / total
        val awayWeight = awayVal.toFloat() / total

        (homeBar.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let {
            it.weight = homeWeight
            homeBar.layoutParams = it
        }
        (awayBar.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let {
            it.weight = awayWeight
            awayBar.layoutParams = it
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private data class ResultDisplay(
        val icon: String,
        val label: String,
        val labelColor: String,
        val bgColor: String
    )

    override fun onBackPressed() {
        // Consome o back — usuário deve usar o botão CONTINUAR
    }
}
