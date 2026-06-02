package com.cblol.scout.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cblol.scout.R
import com.cblol.scout.domain.LevelUpRewards
import com.cblol.scout.domain.usecase.CoachProgressionService
import com.cblol.scout.game.GameRepository

/**
 * Tela mostrada quando o técnico ganha um level novo.
 *
 * **Fluxo:**
 *  1. [ManagerHubActivity.onResume] verifica
 *     [com.cblol.scout.data.GameState.pendingCoachLevelUps]. Se há ao menos um
 *     level enfileirado, abre esta Activity passando o level via Intent.
 *  2. Aqui mostramos a tela animada (level antigo → novo, título, badge se
 *     milestone, lista de bônus passivos).
 *  3. Ao tocar CONTINUAR, removemos o level da fila e salvamos. Se ainda há
 *     mais levels enfileirados, o Hub vai abrir esta Activity de novo no
 *     próximo `onResume` (cadeia natural — sem precisar fila explícita aqui).
 *
 * **Dois layouts internos** controlados via [View.VISIBLE]/[View.GONE]:
 *  - **Milestone** (level ∈ [LevelUpRewards.milestones]) → mostra badge + descrição + bullets de bônus
 *  - **Intermediário** (level normal) → mostra "continue evoluindo, próximo marco no level X"
 *
 * **SOLID:**
 *  - **SRP**: só renderiza a tela. Aplicação de bônus + atualização de
 *    [com.cblol.scout.data.GameState] já foram feitas no
 *    [CoachProgressionService.detectAndQueueLevelUps] ANTES desta Activity abrir.
 *  - **OCP**: novo milestone = nova entrada em [LevelUpRewards.milestones];
 *    UI ramifica via lookup, não via cases hard-coded.
 *  - **DIP**: depende de [GameRepository] (estado) e [LevelUpRewards] (catálogo)
 *    — ambos puros, sem framework.
 */
class CoachLevelUpActivity : AppCompatActivity() {

    private var level: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coach_level_up)

        level = intent.getIntExtra(EXTRA_LEVEL, 1)
        renderHeader()
        renderBody()
        setupContinueButton()
        animateEntrance()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Consome o back — usuário deve usar CONTINUAR para limpar a fila.
        // Sem isto, o jogador conseguiria sair sem desempilhar o level e o
        // Hub abriria a tela infinitamente em loop no próximo onResume.
    }

    // ── Renderização ─────────────────────────────────────────────────────

    private fun renderHeader() {
        val oldLevel = (level - 1).coerceAtLeast(1)
        findViewById<TextView>(R.id.tv_levelup_old_level).text =
            getString(R.string.coach_levelup_level_label, oldLevel)
        findViewById<TextView>(R.id.tv_levelup_new_level).text =
            getString(R.string.coach_levelup_level_label, level)
        findViewById<TextView>(R.id.tv_levelup_title).text =
            CoachProgressionService.titleFor(level)
    }

    private fun renderBody() {
        val reward = LevelUpRewards.rewardFor(level)
        if (reward != null) {
            renderMilestone(reward)
        } else {
            renderIntermediate()
        }
    }

    /** Level é um milestone: badge gigante + descrição + bullets de bônus. */
    private fun renderMilestone(reward: LevelUpRewards.LevelUpReward) {
        findViewById<View>(R.id.ll_milestone_block).visibility = View.VISIBLE
        findViewById<View>(R.id.ll_intermediate_block).visibility = View.GONE

        findViewById<TextView>(R.id.tv_levelup_badge_emoji).text = reward.badgeEmoji
        findViewById<TextView>(R.id.tv_levelup_badge_name).text = reward.badgeName
        findViewById<TextView>(R.id.tv_levelup_badge_description).text = reward.description

        // Constrói os bullets dinamicamente (a quantidade varia por milestone).
        val container = findViewById<LinearLayout>(R.id.ll_levelup_bullets)
        container.removeAllViews()
        reward.bonusBullets.forEach { line ->
            container.addView(buildBulletView(line))
        }
    }

    /**
     * Level intermediário (não-milestone): incentiva o jogador apontando o
     * próximo marco. Se já passou de todos, mostra mensagem genérica de
     * cap (raro porque cap é level 30 e MAX_LEVEL é 30).
     */
    private fun renderIntermediate() {
        findViewById<View>(R.id.ll_milestone_block).visibility = View.GONE
        findViewById<View>(R.id.ll_intermediate_block).visibility = View.VISIBLE

        val nextMilestone = LevelUpRewards.nextMilestoneAfter(level)
        val tvNext = findViewById<TextView>(R.id.tv_levelup_next_milestone)
        tvNext.text = if (nextMilestone == null) {
            getString(R.string.coach_levelup_no_more_milestones)
        } else {
            getString(
                R.string.coach_levelup_next_milestone_format,
                nextMilestone.level,
                nextMilestone.badgeEmoji,
                nextMilestone.badgeName
            )
        }
    }

    private fun buildBulletView(line: String): TextView {
        val tv = TextView(this)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(4)
        params.bottomMargin = dp(4)
        tv.layoutParams = params
        tv.text = line
        tv.textSize = 13f
        tv.setTextColor(ContextCompat.getColor(this, R.color.color_on_surface))
        tv.setLineSpacing(0f, 1.3f)
        return tv
    }

    // ── Ação ─────────────────────────────────────────────────────────────

    private fun setupContinueButton() {
        findViewById<View>(R.id.btn_levelup_continue).setOnClickListener {
            consumeLevelFromQueue()
            finish()
        }
    }

    /**
     * Remove ESTE level da fila de level ups pendentes e persiste. Se o
     * jogador ganhou dois levels seguidos, ainda restará outro na fila — o
     * [ManagerHubActivity.onResume] abrirá esta Activity de novo após o
     * `finish()`. Cadeia natural sem laço explícito.
     */
    private fun consumeLevelFromQueue() {
        val gs = GameRepository.current()
        val queue = gs.pendingCoachLevelUps ?: return
        // Remove apenas a primeira ocorrência deste level (idempotente).
        queue.remove(level)
        GameRepository.save(applicationContext)
    }

    // ── Animação de entrada ─────────────────────────────────────────────

    /**
     * Animação dramática: o headline e o novo level chegam em escala 0→1 com
     * overshoot, dando o "pop" visual de conquista. Não muito longa pra não
     * cansar quando há vários level ups encadeados.
     */
    private fun animateEntrance() {
        val headline = findViewById<View>(R.id.tv_levelup_headline)
        val newLevel = findViewById<View>(R.id.tv_levelup_new_level)
        listOf(headline, newLevel).forEach { v ->
            v.scaleX = 0f
            v.scaleY = 0f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(v, "scaleX", 0f, 1f),
                    ObjectAnimator.ofFloat(v, "scaleY", 0f, 1f)
                )
                duration = ANIM_DURATION_MS
                interpolator = OvershootInterpolator(OVERSHOOT_TENSION)
                start()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_LEVEL = "extra_levelup_level"

        private const val ANIM_DURATION_MS = 500L
        private const val OVERSHOOT_TENSION = 2f

        fun intent(context: Context, level: Int): Intent =
            Intent(context, CoachLevelUpActivity::class.java)
                .putExtra(EXTRA_LEVEL, level)
    }
}
