package com.cblol.scout.game.live

import com.cblol.scout.data.GameState
import com.cblol.scout.data.Player
import com.cblol.scout.domain.GameConstants
import com.cblol.scout.domain.usecase.MoraleService
import com.cblol.scout.domain.usecase.OffMatchEventService
import com.cblol.scout.domain.usecase.PlayerBondService
import com.cblol.scout.util.ChampionPoolRepository
import com.cblol.scout.util.CompositionRepository

/**
 * Calcula a força final de cada lado para uma partida, agregando TODOS os
 * modificadores que afetam o resultado:
 *  - **Base**: overall ajustado por moral e eventos fora de jogo (lesão, etc.)
 *  - **Lado**: bônus do lado azul (home) por jogar em casa
 *  - **Composição**: sinergias detectadas e insights (analisados via
 *    [CompositionRepository])
 *  - **Mains**: bônus por jogadores em champion pool natural
 *  - **Laços**: química do elenco ([PlayerBondService])
 *  - **Rota errada**: penalidade do lado do jogador se ele atribuiu campeão
 *    fora da role natural
 *
 * Extraído do [com.cblol.scout.game.LiveMatchEngine] para que o motor não
 * precise conhecer cada uma dessas regras — ele só pede "qual a força final?"
 * e recebe um [StrengthBreakdown] já com tudo somado e os números para
 * narrar no feed (contagens de mains, penalidade aplicada, etc).
 *
 * **SOLID:**
 *  - **SRP**: agrega forças; não joga, não decide vencedor.
 *  - **OCP**: novos modificadores entram aqui e somam ao `total`. O motor
 *    não muda.
 */
internal object MapStrengthCalculator {

    /**
     * Calcula a força final dos dois lados de um mapa.
     */
    fun calculate(
        sides: SideNormalizer.NormalizedSides,
        homeRoster: List<Player>,
        awayRoster: List<Player>,
        gs: GameState
    ): StrengthBreakdown {
        // Composição (sinergias + insights detectados nos picks)
        val homeComp = CompositionRepository.analyzeWithTags(sides.homePicks, sides.awayPicks, sides.awayBans)
        val awayComp = CompositionRepository.analyzeWithTags(sides.awayPicks, sides.homePicks, sides.homeBans)

        // Champion pool (mains de cada jogador)
        val homeMainsCount = ChampionPoolRepository.countMainsPicked(homeRoster, sides.homePicks)
        val awayMainsCount = ChampionPoolRepository.countMainsPicked(awayRoster, sides.awayPicks)
        val homeMainBonus  = homeMainsCount * GameConstants.Player.CHAMP_POOL_MAIN_BONUS
        val awayMainBonus  = awayMainsCount * GameConstants.Player.CHAMP_POOL_MAIN_BONUS

        // Penalidade por jogadores em rota errada (só do lado do jogador)
        val wrongRoleCount = sides.playerAssignments.count { it.isWrongRole }
        val wrongRolePenalty = wrongRoleCount * GameConstants.Player.WRONG_ROLE_PENALTY

        // Forças finais: base + lado + composição + mains + bonds − penalidade (lado do jogador)
        val homeStr = teamStrengthWithMood(homeRoster, gs) +
                      GameConstants.Series.HOME_SIDE_BONUS +
                      homeComp.totalBonus + homeMainBonus +
                      PlayerBondService.teamStrengthBonus(gs, homeRoster) -
                      (if (sides.playerIsHome) wrongRolePenalty else 0)
        val awayStr = teamStrengthWithMood(awayRoster, gs) +
                      awayComp.totalBonus + awayMainBonus +
                      PlayerBondService.teamStrengthBonus(gs, awayRoster) -
                      (if (!sides.playerIsHome) wrongRolePenalty else 0)

        return StrengthBreakdown(
            homeStr = homeStr,
            awayStr = awayStr,
            homeComp = homeComp,
            awayComp = awayComp,
            homeMainsCount = homeMainsCount,
            awayMainsCount = awayMainsCount,
            homeMainBonus = homeMainBonus,
            awayMainBonus = awayMainBonus,
            wrongRoleCount = wrongRoleCount,
            wrongRolePenalty = wrongRolePenalty
        )
    }

    /**
     * Variante de força com moral e modificadores fora de jogo SOMADOS por
     * jogador (mesmo cálculo da v1 do LiveMatchEngine).
     *
     * Cada jogador tem seu overall efetivo ajustado por:
     *  1. [MoraleService.moodOverallModifier]: SAD/NEUTRAL/HAPPY/ÊXTASE
     *  2. [OffMatchEventService.activeOverallModifierFor]: lesão, família, etc.
     *
     * Os dois efeitos se SOMAM — jogador FELIZ (+2) com família (+3) ganha +5,
     * jogador TRISTE (-3) lesionado (-4) perde -7.
     */
    private fun teamStrengthWithMood(roster: List<Player>, gs: GameState): Int {
        if (roster.isEmpty()) return 50
        val total = roster.sumOf { player ->
            val baseOvr     = player.overallRating()
            val moodMod     = MoraleService.moodOverallModifier(gs, player.id)
            val offMatchMod = OffMatchEventService.activeOverallModifierFor(gs, player.id)
            (baseOvr + moodMod + offMatchMod).coerceIn(1, 99)
        }
        return total / roster.size
    }

    /**
     * Resultado do cálculo de força. Inclui os números agregados (que entram
     * em [GameOutcomeCalculator]) e as contagens individuais (que o motor usa
     * para narrar no feed: "X jogadores no main", "Y em rota errada").
     */
    data class StrengthBreakdown(
        val homeStr: Int,
        val awayStr: Int,
        val homeComp: CompositionRepository.TaggedAnalysisResult,
        val awayComp: CompositionRepository.TaggedAnalysisResult,
        val homeMainsCount: Int,
        val awayMainsCount: Int,
        val homeMainBonus: Int,
        val awayMainBonus: Int,
        val wrongRoleCount: Int,
        val wrongRolePenalty: Int
    )
}
