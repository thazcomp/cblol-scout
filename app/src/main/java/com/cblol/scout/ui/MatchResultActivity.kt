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
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cblol.scout.R
import com.cblol.scout.databinding.ActivityMatchResultBinding
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.util.TeamColors

/**
 * Tela animada de resultado pós-partida.
 *
 * SOLID:
 * - **SRP**: cada estágio da animação tem método próprio ([animateBackground],
 *   [animateIcon], [animateLabel], [animateScoreboard], [animateCounters], etc.).
 * - **OCP**: o esquema visual (vitória/derrota/outra) é encapsulado em
 *   [ResultDisplay] e selecionado em [computeResultDisplay]; novos esquemas
 *   não tocam na lógica de animação.
 * - **DIP**: depende de recursos (R.string, R.color) e de [GameConstants.Result]
 *   para todos os valores numéricos.
 */
class MatchResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMatchResultBinding

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val data = MatchResultData.fromIntent(intent)
        renderHeader(data)
        renderStats(data)
        renderPrize(data)
        setupContinueButton(data)
        runEntranceAnimations(data)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Consome o back — usuário deve usar o botão CONTINUAR
    }

    // ── Renderização ─────────────────────────────────────────────────────

    private fun renderHeader(data: MatchResultData) {
        binding.tvHomeName.text     = data.homeName
        binding.tvAwayName.text     = data.awayName
        binding.tvSeriesScore.text  = getString(R.string.result_score_format, data.homeScore, data.awayScore)
        binding.viewHomeColor.setBackgroundColor(TeamColors.forTeam(data.homeId))
        binding.viewAwayColor.setBackgroundColor(TeamColors.forTeam(data.awayId))

        val display = computeResultDisplay(data)
        binding.tvResultIcon.text  = getString(display.iconRes)
        binding.tvResultLabel.text = getString(display.labelRes)
        binding.tvResultLabel.setTextColor(color(display.labelColorRes))

        binding.viewResultBg.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(color(display.bgColorRes), color(R.color.bg_screen))
        )
    }

    private fun renderStats(data: MatchResultData) {
        binding.tvStatHomeKills.text   = data.homeKills.toString()
        binding.tvStatAwayKills.text   = data.awayKills.toString()
        binding.tvStatHomeTowers.text  = data.homeTowers.toString()
        binding.tvStatAwayTowers.text  = data.awayTowers.toString()
        binding.tvStatHomeDragons.text = data.homeDragons.toString()
        binding.tvStatAwayDragons.text = data.awayDragons.toString()
        binding.tvStatHomeBarons.text  = data.homeBarons.toString()
        binding.tvStatAwayBarons.text  = data.awayBarons.toString()

        setStatBars(binding.barKillsHome,   binding.barKillsAway,   data.homeKills,   data.awayKills)
        setStatBars(binding.barTowersHome,  binding.barTowersAway,  data.homeTowers,  data.awayTowers)
        setStatBars(binding.barDragonsHome, binding.barDragonsAway, data.homeDragons, data.awayDragons)
        setStatBars(binding.barBaronsHome,  binding.barBaronsAway,  data.homeBarons,  data.awayBarons)
    }

    private fun renderPrize(data: MatchResultData) {
        if (data.prize > 0L && data.isMyMatch) {
            binding.cardPrize.visibility = View.VISIBLE
            binding.tvPrize.text = getString(R.string.result_prize_format, "%,d".format(data.prize))
        } else {
            binding.cardPrize.visibility = View.GONE
        }
    }

    private fun setupContinueButton(data: MatchResultData) {
        binding.btnContinue.setOnClickListener {
            if (data.seriesFinished || data.matchId.isEmpty()) {
                startActivity(Intent(this, ManagerHubActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
            } else {
                showNextMapDialog(data)
            }
        }
    }

    private fun showNextMapDialog(data: MatchResultData) {
        val nextMap = data.mapNumber + 1
        stylizedDialog(this)
            .setTitle(getString(R.string.result_next_map_title, nextMap))
            .setMessage(R.string.result_next_map_message)
            .setPositiveButton(R.string.btn_do_pickban) { _, _ ->
                finish()
                startActivity(Intent(this, PickBanRouterActivity::class.java).apply {
                    putExtra(PickBanRouterActivity.EXTRA_MATCH_ID,       data.matchId)
                    putExtra(PickBanRouterActivity.EXTRA_MAP_NUMBER,     nextMap)
                    putExtra(PickBanRouterActivity.EXTRA_PLAYER_TEAM_ID, data.playerTeamId)
                    putExtra(PickBanRouterActivity.EXTRA_OPPONENT_ID,    data.opponentTeamId)
                })
            }
            .setNeutralButton(R.string.btn_skip_simulation) { _, _ ->
                finish()
                startActivity(Intent(this, MatchSimulationActivity::class.java)
                    .putExtra(MatchSimulationActivity.EXTRA_MATCH_ID, data.matchId))
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ── Escolha do esquema visual ────────────────────────────────────────

    private fun computeResultDisplay(data: MatchResultData): ResultDisplay = when {
        !data.isMyMatch  -> ResultDisplay(
            R.string.icon_controller, R.string.result_other_match,
            R.color.result_other_text, R.color.result_other_bg
        )
        data.playerWon   -> ResultDisplay(
            R.string.icon_trophy, R.string.result_victory,
            R.color.result_victory_text, R.color.result_victory_bg
        )
        else             -> ResultDisplay(
            R.string.icon_heart_broken, R.string.result_defeat,
            R.color.result_defeat_text, R.color.result_defeat_bg
        )
    }

    // ── Animações ────────────────────────────────────────────────────────

    private fun runEntranceAnimations(data: MatchResultData) {
        animateBackground()
        animateIcon()
        animateLabel()
        animateScoreboard()
        animateCounters(data)
        fadeSlideIn(binding.cardStats,   GameConstants.Result.ANIM_CARD_STATS_DELAY_MS)
        fadeSlideIn(binding.cardPrize,   GameConstants.Result.ANIM_CARD_PRIZE_DELAY_MS)
        fadeSlideIn(binding.btnContinue, GameConstants.Result.ANIM_BUTTON_DELAY_MS)
        if (data.playerWon) animateVictoryPulse()
    }

    private fun animateBackground() {
        ObjectAnimator.ofFloat(binding.viewResultBg, "alpha",
            0f, GameConstants.Result.ANIM_BG_TARGET_ALPHA).apply {
            duration     = GameConstants.Result.ANIM_BG_DURATION_MS
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun animateIcon() {
        binding.tvResultIcon.postDelayed({
            binding.tvResultIcon.scaleX = 0f
            binding.tvResultIcon.scaleY = 0f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(binding.tvResultIcon, "alpha",  0f, 1f),
                    ObjectAnimator.ofFloat(binding.tvResultIcon, "scaleX", 0f, 1f),
                    ObjectAnimator.ofFloat(binding.tvResultIcon, "scaleY", 0f, 1f)
                )
                duration     = GameConstants.Result.ANIM_ICON_DURATION_MS
                interpolator = BounceInterpolator()
                start()
            }
        }, GameConstants.Result.ANIM_ICON_DELAY_MS)
    }

    private fun animateLabel() {
        binding.tvResultLabel.postDelayed({
            binding.tvResultLabel.translationY = LABEL_SLIDE_OFFSET
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(binding.tvResultLabel, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(binding.tvResultLabel, "translationY", LABEL_SLIDE_OFFSET, 0f)
                )
                duration     = GameConstants.Result.ANIM_LABEL_DURATION_MS
                interpolator = DecelerateInterpolator()
                start()
            }
        }, GameConstants.Result.ANIM_LABEL_DELAY_MS)
    }

    private fun animateScoreboard() {
        binding.llScoreboard.postDelayed({
            binding.llScoreboard.scaleX = GameConstants.Result.ANIM_SCOREBOARD_START_SCALE
            binding.llScoreboard.scaleY = GameConstants.Result.ANIM_SCOREBOARD_START_SCALE
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(binding.llScoreboard, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(binding.llScoreboard, "scaleX",
                        GameConstants.Result.ANIM_SCOREBOARD_START_SCALE, 1f),
                    ObjectAnimator.ofFloat(binding.llScoreboard, "scaleY",
                        GameConstants.Result.ANIM_SCOREBOARD_START_SCALE, 1f)
                )
                duration     = GameConstants.Result.ANIM_SCOREBOARD_DURATION_MS
                interpolator = OvershootInterpolator(GameConstants.Result.ANIM_OVERSHOOT_TENSION)
                start()
            }
        }, GameConstants.Result.ANIM_SCOREBOARD_DELAY_MS)
    }

    private fun animateCounters(data: MatchResultData) {
        binding.llScoreboard.postDelayed({
            val dur = GameConstants.Result.ANIM_COUNTERS_DURATION_MS
            animateCounter(binding.tvStatHomeKills,   data.homeKills,   dur)
            animateCounter(binding.tvStatAwayKills,   data.awayKills,   dur)
            animateCounter(binding.tvStatHomeTowers,  data.homeTowers,  dur)
            animateCounter(binding.tvStatAwayTowers,  data.awayTowers,  dur)
            animateCounter(binding.tvStatHomeDragons, data.homeDragons, dur)
            animateCounter(binding.tvStatAwayDragons, data.awayDragons, dur)
            animateCounter(binding.tvStatHomeBarons,  data.homeBarons,  dur)
            animateCounter(binding.tvStatAwayBarons,  data.awayBarons,  dur)
        }, GameConstants.Result.ANIM_COUNTERS_DELAY_MS)
    }

    private fun animateVictoryPulse() {
        binding.tvResultLabel.postDelayed({
            val from = 1f
            val to   = GameConstants.Result.ANIM_VICTORY_PULSE_SCALE
            val pulseX = ObjectAnimator.ofFloat(binding.tvResultLabel, "scaleX", from, to, from)
            val pulseY = ObjectAnimator.ofFloat(binding.tvResultLabel, "scaleY", from, to, from)
            listOf(pulseX, pulseY).forEach {
                it.repeatCount  = GameConstants.Result.ANIM_PULSE_REPEAT_COUNT
                it.duration     = GameConstants.Result.ANIM_PULSE_DURATION_MS
                it.interpolator = AccelerateDecelerateInterpolator()
            }
            AnimatorSet().apply { playTogether(pulseX, pulseY); start() }
        }, GameConstants.Result.ANIM_PULSE_DELAY_MS)
    }

    private fun fadeSlideIn(view: View, delay: Long) {
        view.postDelayed({
            view.translationY = CARD_SLIDE_OFFSET
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(view, "translationY", CARD_SLIDE_OFFSET, 0f)
                )
                duration     = CARD_FADE_DURATION_MS
                interpolator = DecelerateInterpolator()
                start()
            }
        }, delay)
    }

    private fun animateCounter(tv: TextView, target: Int, duration: Long) {
        if (target == 0) { tv.text = "0"; return }
        ValueAnimator.ofInt(0, target).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { tv.text = (it.animatedValue as Int).toString() }
            start()
        }
    }

    // ── Barras proporcionais ─────────────────────────────────────────────

    private fun setStatBars(homeBar: View, awayBar: View, homeVal: Int, awayVal: Int) {
        val total      = (homeVal + awayVal).coerceAtLeast(1)
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

    private fun color(@ColorRes res: Int) = ContextCompat.getColor(this, res)

    // ── Tipos auxiliares ─────────────────────────────────────────────────

    /** Esquema visual de um resultado (vitória/derrota/outra partida). */
    private data class ResultDisplay(
        @StringRes val iconRes: Int,
        @StringRes val labelRes: Int,
        @ColorRes  val labelColorRes: Int,
        @ColorRes  val bgColorRes: Int
    )

    /** Snapshot imutável dos dados que chegam via Intent. */
    private data class MatchResultData(
        val homeName: String, val awayName: String,
        val homeId: String,   val awayId: String,
        val homeScore: Int,   val awayScore: Int,
        val winnerId: String, val managerId: String,
        val homeKills: Int,   val awayKills: Int,
        val homeTowers: Int,  val awayTowers: Int,
        val homeDragons: Int, val awayDragons: Int,
        val homeBarons: Int,  val awayBarons: Int,
        val prize: Long,
        val matchId: String,
        val mapNumber: Int,
        val playerTeamId: String,
        val opponentTeamId: String,
        val seriesFinished: Boolean
    ) {
        val isMyMatch:  Boolean get() = managerId == homeId || managerId == awayId
        val playerWon:  Boolean get() = isMyMatch && winnerId == managerId

        companion object {
            fun fromIntent(intent: Intent) = MatchResultData(
                homeName       = intent.getStringExtra(EXTRA_HOME_NAME)         ?: "",
                awayName       = intent.getStringExtra(EXTRA_AWAY_NAME)         ?: "",
                homeId         = intent.getStringExtra(EXTRA_HOME_ID)           ?: "",
                awayId         = intent.getStringExtra(EXTRA_AWAY_ID)           ?: "",
                homeScore      = intent.getIntExtra(EXTRA_HOME_SCORE, 0),
                awayScore      = intent.getIntExtra(EXTRA_AWAY_SCORE, 0),
                winnerId       = intent.getStringExtra(EXTRA_WINNER_ID)         ?: "",
                managerId      = intent.getStringExtra(EXTRA_MANAGER_ID)        ?: "",
                homeKills      = intent.getIntExtra(EXTRA_HOME_KILLS, 0),
                awayKills      = intent.getIntExtra(EXTRA_AWAY_KILLS, 0),
                homeTowers     = intent.getIntExtra(EXTRA_HOME_TOWERS, 0),
                awayTowers     = intent.getIntExtra(EXTRA_AWAY_TOWERS, 0),
                homeDragons    = intent.getIntExtra(EXTRA_HOME_DRAGONS, 0),
                awayDragons    = intent.getIntExtra(EXTRA_AWAY_DRAGONS, 0),
                homeBarons     = intent.getIntExtra(EXTRA_HOME_BARONS, 0),
                awayBarons     = intent.getIntExtra(EXTRA_AWAY_BARONS, 0),
                prize          = intent.getLongExtra(EXTRA_PRIZE, 0L),
                matchId        = intent.getStringExtra(EXTRA_MATCH_ID)          ?: "",
                mapNumber      = intent.getIntExtra(EXTRA_MAP_NUMBER, 1),
                playerTeamId   = intent.getStringExtra(EXTRA_PLAYER_TEAM_ID)    ?: "",
                opponentTeamId = intent.getStringExtra(EXTRA_OPPONENT_TEAM_ID)  ?: "",
                seriesFinished = intent.getBooleanExtra(EXTRA_SERIES_FINISHED, true)
            )
        }
    }

    companion object {
        const val EXTRA_HOME_NAME        = "res_home_name"
        const val EXTRA_AWAY_NAME        = "res_away_name"
        const val EXTRA_HOME_ID          = "res_home_id"
        const val EXTRA_AWAY_ID          = "res_away_id"
        const val EXTRA_HOME_SCORE       = "res_home_score"
        const val EXTRA_AWAY_SCORE       = "res_away_score"
        const val EXTRA_WINNER_ID        = "res_winner_id"
        const val EXTRA_MANAGER_ID       = "res_manager_id"
        const val EXTRA_HOME_KILLS       = "res_home_kills"
        const val EXTRA_AWAY_KILLS       = "res_away_kills"
        const val EXTRA_HOME_TOWERS      = "res_home_towers"
        const val EXTRA_AWAY_TOWERS      = "res_away_towers"
        const val EXTRA_HOME_DRAGONS     = "res_home_dragons"
        const val EXTRA_AWAY_DRAGONS     = "res_away_dragons"
        const val EXTRA_HOME_BARONS      = "res_home_barons"
        const val EXTRA_AWAY_BARONS      = "res_away_barons"
        const val EXTRA_PRIZE            = "res_prize"
        const val EXTRA_MATCH_ID         = "res_match_id"
        const val EXTRA_MAP_NUMBER       = "res_map_number"
        const val EXTRA_PLAYER_TEAM_ID   = "res_player_team_id"
        const val EXTRA_OPPONENT_TEAM_ID = "res_opponent_team_id"
        const val EXTRA_SERIES_FINISHED  = "res_series_finished"

        // Constantes de animação locais (offsets em px-equivalente — pequenos o suficiente
        // para não justificar entrada em dimens.xml)
        private const val LABEL_SLIDE_OFFSET = 40f
        private const val CARD_SLIDE_OFFSET  = 30f
        private const val CARD_FADE_DURATION_MS = 450L
    }
}
