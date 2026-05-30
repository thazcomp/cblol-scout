package com.cblol.scout.game.live

import com.cblol.scout.data.Match
import com.cblol.scout.data.PickBanPlan
import com.cblol.scout.data.RoleAssignment
import com.cblol.scout.game.GameRepository

/**
 * Converte um [PickBanPlan] (que vive em coordenadas azul/vermelho) para as
 * coordenadas home/away do calendário.
 *
 * **Por que essa normalização existe**: o `PickBanPlan` armazena picks em
 * `bluePicks`/`redPicks` (lados azul e vermelho da UI), enquanto o `Match`
 * usa `homeTeamId`/`awayTeamId` (calendário). Não são sinônimos — o time do
 * jogador alterna de lado a cada mapa (azul nos mapas ímpares, vermelho nos
 * pares), então picks que vieram do lado "blue" no mapa 1 vêm do lado "red"
 * no mapa 2.
 *
 * Extraído do [com.cblol.scout.game.LiveMatchEngine] para isolar essa regra
 * espinhosa de mapeamento em um único lugar com nome — antes vivia inline
 * num bloco enorme de comentários no meio do `generateSingleMap`.
 *
 * **SOLID:**
 *  - **SRP**: única responsabilidade: traduzir azul/vermelho ↔ home/away.
 *  - **DIP**: stateless; recebe `match`, `plan` e `gameNumber` como input,
 *    devolve [NormalizedSides] puro.
 */
internal object SideNormalizer {

    /**
     * Resolve qual lado (azul/vermelho) cada time (home/away) jogou neste mapa
     * e reordena os picks/bans/assignments do plano para alinhar com home/away.
     *
     * @param match partida do calendário (com home/away ids)
     * @param plan plano de pick & ban (em coordenadas azul/vermelho), ou null
     *   se o jogador não fez pick manual neste mapa
     * @param gameNumber número do mapa (1-based; ímpares = jogador azul)
     */
    fun normalize(match: Match, plan: PickBanPlan?, gameNumber: Int): NormalizedSides {
        val playerTeamId = GameRepository.current().managerTeamId
        val playerIsHome = (match.homeTeamId == playerTeamId)
        // Jogador foi azul neste mapa? Mapa 1 ímpar = sim; mapa 2 par = não.
        val playerWasBlue = (gameNumber % 2 == 1)
        // Logo, o HOME foi azul se (jogador é home AND jogador foi azul) ou
        // (jogador é away AND jogador foi vermelho).
        val homeWasBlue = (playerIsHome == playerWasBlue)

        val homePicks = if (homeWasBlue) plan?.bluePicks.orEmpty() else plan?.redPicks.orEmpty()
        val awayPicks = if (homeWasBlue) plan?.redPicks.orEmpty()  else plan?.bluePicks.orEmpty()
        val homeBans  = if (homeWasBlue) plan?.blueBans.orEmpty()  else plan?.redBans.orEmpty()
        val awayBans  = if (homeWasBlue) plan?.redBans.orEmpty()   else plan?.blueBans.orEmpty()

        // roleAssignments referem-se SEMPRE ao time do jogador (independente do lado).
        val playerAssignments = plan?.roleAssignments.orEmpty()
        val homeAssignments = if (playerIsHome) playerAssignments else emptyList()
        val awayAssignments = if (!playerIsHome) playerAssignments else emptyList()

        return NormalizedSides(
            playerIsHome = playerIsHome,
            homePicks = homePicks,
            awayPicks = awayPicks,
            homeBans = homeBans,
            awayBans = awayBans,
            homeAssignments = homeAssignments,
            awayAssignments = awayAssignments,
            playerAssignments = playerAssignments
        )
    }

    /**
     * Picks/bans/assignments do plano traduzidos para o eixo home/away.
     *
     * @property playerIsHome se o time do gerente é o home da partida (útil
     *   para outras decisões que dependem do lado dele, como aplicar
     *   penalidade de rota errada).
     * @property playerAssignments os assignments originais do jogador (sem
     *   reordenação) — preservados para quem precise contá-los.
     */
    data class NormalizedSides(
        val playerIsHome: Boolean,
        val homePicks: List<String>,
        val awayPicks: List<String>,
        val homeBans: List<String>,
        val awayBans: List<String>,
        val homeAssignments: List<RoleAssignment>,
        val awayAssignments: List<RoleAssignment>,
        val playerAssignments: List<RoleAssignment>
    )
}
