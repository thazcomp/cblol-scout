package com.cblol.scout.game.live

import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Calcula as decisões probabilísticas que definem o RESULTADO de um mapa:
 * quem vence, quantos kills, a duração. Não gera eventos — só números.
 *
 * Os eventos em si (kills, torres, dragões etc.) ficam no
 * [TimedEventGenerator], que recebe estes números e os distribui no tempo.
 *
 * Extraído do [com.cblol.scout.game.LiveMatchEngine] para isolar as
 * heurísticas estatísticas em um único lugar — facilita ajustar as fórmulas
 * (probabilidade, faixa de kills, duração mínima/máxima) sem mexer no resto.
 *
 * **SOLID:**
 *  - **SRP**: só calcula números do resultado.
 *  - **DIP**: stateless; recebe forças de equipe como input.
 */
internal object GameOutcomeCalculator {

    /**
     * Calcula o resultado completo de um mapa a partir da diferença de força.
     *
     * - **Probabilidade do home vencer**: clamp em [10%..90%] em volta de 50%,
     *   com 1 ponto de força ≈ 1.67% de vantagem. Sem força absurda, nem
     *   vitórias mortas dos dois lados.
     * - **Duração**: jogos com diferença grande (>12 de força) tendem a
     *   acabar mais cedo (24–30 min); jogos parelhos esticam (28–40 min).
     * - **Kills**: total entre 12 e 28, distribuídos com vantagem para o
     *   vencedor (55–65% se vencedor, 30–40% se perdedor).
     */
    fun calculate(homeStr: Int, awayStr: Int): GameOutcome {
        val diff = homeStr - awayStr
        val homeWinProb = (0.5 + (diff / STRENGTH_TO_PROB_DIVISOR)).coerceIn(MIN_WIN_PROB, MAX_WIN_PROB)
        val homeWon = Random.nextDouble() < homeWinProb

        val duration = if (diff.absoluteValue > UNBALANCED_THRESHOLD) {
            (DURATION_SHORT_MIN..DURATION_SHORT_MAX).random()
        } else {
            (DURATION_LONG_MIN..DURATION_LONG_MAX).random()
        }

        val totalKills = (KILLS_TOTAL_MIN..KILLS_TOTAL_MAX).random()
        val homeShare = if (homeWon) {
            WINNER_SHARE_BASE + Random.nextDouble(SHARE_VARIANCE)
        } else {
            LOSER_SHARE_BASE + Random.nextDouble(SHARE_VARIANCE)
        }
        val homeKills = (totalKills * homeShare).toInt()
        val awayKills = totalKills - homeKills

        return GameOutcome(
            homeWon = homeWon,
            homeWinProb = homeWinProb,
            duration = duration,
            homeKills = homeKills,
            awayKills = awayKills
        )
    }

    /** Sorteia um lado seguindo a probabilidade do home (usado em objetivos do jogo). */
    fun sideByOdds(homeProb: Double): com.cblol.scout.data.Side =
        if (Random.nextDouble() < homeProb) com.cblol.scout.data.Side.HOME
        else com.cblol.scout.data.Side.AWAY

    /** Resultado completo de um mapa (números puros, sem eventos). */
    data class GameOutcome(
        val homeWon: Boolean,
        val homeWinProb: Double,
        val duration: Int,
        val homeKills: Int,
        val awayKills: Int
    )

    // ── Constantes ───────────────────────────────────────────────────────

    /**
     * Divisor que converte diferença de força em probabilidade. 60 = 1 ponto
     * de força adiciona ~1.67% à chance do home. Ajustar muda o quanto
     * "elenco melhor" matters em relação ao acaso.
     */
    private const val STRENGTH_TO_PROB_DIVISOR = 60.0

    /** Probabilidade mínima/máxima de vitória (evita 0% / 100%). */
    private const val MIN_WIN_PROB = 0.1
    private const val MAX_WIN_PROB = 0.9

    /** Diferença de força acima da qual o jogo é considerado "desequilibrado". */
    private const val UNBALANCED_THRESHOLD = 12

    private const val DURATION_SHORT_MIN = 24
    private const val DURATION_SHORT_MAX = 30
    private const val DURATION_LONG_MIN  = 28
    private const val DURATION_LONG_MAX  = 40

    private const val KILLS_TOTAL_MIN = 12
    private const val KILLS_TOTAL_MAX = 28

    /** Vencedor pega ~55–65% dos kills; perdedor pega ~30–40%. */
    private const val WINNER_SHARE_BASE = 0.55
    private const val LOSER_SHARE_BASE  = 0.30
    private const val SHARE_VARIANCE    = 0.10
}
