package com.cblol.scout.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cblol.scout.R
import com.cblol.scout.data.OffMatchEvent
import com.cblol.scout.data.OffMatchEventSentiment
import com.cblol.scout.domain.usecase.OffMatchEventService
import com.cblol.scout.game.GameRepository

/**
 * Tela cinematográfica de evento fora de jogo.
 *
 * Aparece entre BO3s para narrar acontecimentos que afetam o estado emocional
 * e/ou físico dos jogadores: entrevista, lesão, relacionamento, presença da
 * família, polêmica, treino, etc.
 *
 * **Ciclo de vida:**
 *  1. [OffMatchEventService.maybeGenerateEvent] gerou o evento ao final da BO3 anterior.
 *  2. [ManagerHubActivity] (ou outra Activity de transição) detecta
 *     `gs.pendingOffMatchEvent != null` e abre esta tela.
 *  3. Usuário vê a narrativa + efeitos aplicados.
 *  4. Ao tocar em CONTINUAR, chamamos [OffMatchEventService.consumePending] e
 *     voltamos ao Hub.
 *
 * **SOLID:**
 *  - **SRP**: a Activity APENAS apresenta o evento. Os efeitos (moral, overall)
 *    já foram aplicados pelo Service no momento da geração.
 *  - **OCP**: novas categorias se renderizam automaticamente pela cor do
 *    sentimento + emoji do enum.
 *  - **DIP**: depende apenas de [OffMatchEvent] (modelo) e [GameRepository].
 */
class OffMatchEventActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_off_match_event)

        val event = GameRepository.current().pendingOffMatchEvent
        if (event == null) {
            // Sem evento pendente — não deveria acontecer, mas blinda contra
            // race conditions (ex: usuário fechou e reabriu o app).
            finish()
            return
        }

        bindEvent(event)
        animateIn()

        findViewById<View>(R.id.btn_continue).setOnClickListener { onContinue() }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Consome o back — usuário deve usar o botão CONTINUAR para garantir
        // que o evento é marcado como visto (evita o usuário pular o efeito
        // narrativo achando que foi um popup acidental).
    }

    // ── Bind do evento ───────────────────────────────────────────────────

    private fun bindEvent(event: OffMatchEvent) {
        findViewById<TextView>(R.id.tv_emoji).text       = event.category.emoji
        findViewById<TextView>(R.id.tv_title).text       = event.title
        findViewById<TextView>(R.id.tv_description).text = event.description
        findViewById<TextView>(R.id.tv_subtitle).text    = buildSubtitle(event)

        val sentimentColor = colorForSentiment(event.sentiment)
        findViewById<View>(R.id.view_top_bar).setBackgroundColor(
            ContextCompat.getColor(this, sentimentColor)
        )
        findViewById<TextView>(R.id.tv_title).setTextColor(
            ContextCompat.getColor(this, sentimentColor)
        )

        bindEffects(event)
    }

    private fun buildSubtitle(event: OffMatchEvent): String {
        // Eventos de dupla (jogada ensaiada / briga) mostram os dois nomes.
        if (event.secondPlayerName != null && event.targetPlayerName != null) {
            return getString(
                R.string.off_match_subtitle_duo,
                event.category.label,
                event.targetPlayerName,
                event.secondPlayerName
            )
        }
        val target = event.targetPlayerName ?: getString(R.string.off_match_target_team)
        return getString(R.string.off_match_subtitle_team, event.category.label, target)
    }

    /**
     * Popula o card de efeitos. Cada efeito (moral, overall) vira uma linha
     * com cor verde para positivo e vermelho para negativo. Linhas com delta
     * zero ficam escondidas — uma narrativa puramente emocional pode não ter
     * efeito mecânico.
     */
    private fun bindEffects(event: OffMatchEvent) {
        val tvMood    = findViewById<TextView>(R.id.tv_effect_mood)
        val tvOverall = findViewById<TextView>(R.id.tv_effect_overall)
        val cardEffects = findViewById<View>(R.id.card_effects)

        // Moral
        when {
            event.moodDelta > 0 -> {
                tvMood.visibility = View.VISIBLE
                tvMood.text = getString(R.string.off_match_effect_mood_positive, event.moodDelta)
                tvMood.setTextColor(ContextCompat.getColor(this, R.color.state_success))
            }
            event.moodDelta < 0 -> {
                tvMood.visibility = View.VISIBLE
                tvMood.text = getString(R.string.off_match_effect_mood_negative, event.moodDelta)
                tvMood.setTextColor(ContextCompat.getColor(this, R.color.state_danger))
            }
            else -> tvMood.visibility = View.GONE
        }

        // Modificador de overall temporário OU efeito de laço (reusa a linha).
        // Combo/briga não têm modificador de overall, mas têm bondDelta — então
        // a mesma TextView serve para mostrar a variação de química da dupla.
        when {
            event.bondDelta > 0 -> {
                tvOverall.visibility = View.VISIBLE
                tvOverall.text = getString(R.string.off_match_effect_bond_positive, event.bondDelta)
                tvOverall.setTextColor(ContextCompat.getColor(this, R.color.state_success))
            }
            event.bondDelta < 0 -> {
                tvOverall.visibility = View.VISIBLE
                tvOverall.text = getString(R.string.off_match_effect_bond_negative, event.bondDelta)
                tvOverall.setTextColor(ContextCompat.getColor(this, R.color.state_danger))
            }
            event.overallModifierDelta > 0 && event.durationDays > 0 -> {
                tvOverall.visibility = View.VISIBLE
                tvOverall.text = getString(
                    R.string.off_match_effect_overall_positive,
                    event.overallModifierDelta, event.durationDays
                )
                tvOverall.setTextColor(ContextCompat.getColor(this, R.color.state_success))
            }
            event.overallModifierDelta < 0 && event.durationDays > 0 -> {
                tvOverall.visibility = View.VISIBLE
                tvOverall.text = getString(
                    R.string.off_match_effect_overall_negative,
                    event.overallModifierDelta, event.durationDays
                )
                tvOverall.setTextColor(ContextCompat.getColor(this, R.color.state_danger))
            }
            else -> tvOverall.visibility = View.GONE
        }

        // Se nenhum efeito mecânico, esconde o card inteiro
        val hasAnyEffect = tvMood.visibility == View.VISIBLE ||
                          tvOverall.visibility == View.VISIBLE
        cardEffects.visibility = if (hasAnyEffect) View.VISIBLE else View.GONE
    }

    // ── Animações de entrada ────────────────────────────────────────────

    private fun animateIn() {
        val emoji = findViewById<View>(R.id.tv_emoji)
        emoji.scaleX = 0f
        emoji.scaleY = 0f
        emoji.postDelayed({
            ObjectAnimator.ofFloat(emoji, "scaleX", 0f, 1f).apply {
                duration = 600L
                interpolator = OvershootInterpolator(2.5f)
                start()
            }
            ObjectAnimator.ofFloat(emoji, "scaleY", 0f, 1f).apply {
                duration = 600L
                interpolator = OvershootInterpolator(2.5f)
                start()
            }
        }, 150L)

        val title = findViewById<View>(R.id.tv_title)
        title.alpha = 0f
        title.translationY = 30f
        title.postDelayed({
            ObjectAnimator.ofFloat(title, "alpha", 0f, 1f).apply {
                duration = 500L
                start()
            }
            ObjectAnimator.ofFloat(title, "translationY", 30f, 0f).apply {
                duration = 500L
                interpolator = DecelerateInterpolator()
                start()
            }
        }, 400L)
    }

    // ── Continue ────────────────────────────────────────────────────────

    private fun onContinue() {
        OffMatchEventService.consumePending(GameRepository.current())
        GameRepository.save(applicationContext)
        finish()
    }

    @ColorRes
    private fun colorForSentiment(s: OffMatchEventSentiment): Int = when (s) {
        OffMatchEventSentiment.POSITIVE -> R.color.state_success
        OffMatchEventSentiment.NEGATIVE -> R.color.state_danger
        OffMatchEventSentiment.NEUTRAL  -> R.color.champion_gold
    }

    companion object {
        /** Cria intent que pode ser disparado de qualquer lugar. */
        fun intent(context: android.content.Context): Intent =
            Intent(context, OffMatchEventActivity::class.java)
    }
}
