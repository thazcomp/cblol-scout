package com.cblol.scout.game.repo

import com.cblol.scout.data.AcademyProspect
import com.cblol.scout.data.AtributosDeriv
import com.cblol.scout.data.Contrato
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Player
import com.cblol.scout.data.StatsBrutas

/**
 * Materializa um [AcademyProspect] em um [Player] real do elenco.
 *
 * Extraído do [com.cblol.scout.game.GameRepository] porque a criação de um
 * Player a partir de um Prospect tem regras próprias (distribuição dos
 * atributos derivados, contrato base, stats zerados) que não dependem da
 * persistência — só do estado em memória + do prospect promovido.
 *
 * O contrato base usa a data de término do split atual e a fonte de salário
 * "base" (indicando origem na categoria de base). Stats são zerados porque o
 * jogador acabou de subir ao profissional — ainda não tem histórico.
 */
internal object PromotedPlayerFactory {

    /**
     * Cria o [Player] real de um prospect promovido.
     *
     * @param prospect prospect já removido da academia (responsabilidade do
     *   [com.cblol.scout.domain.usecase.AcademyService.promoteProspect])
     * @param salary salário mensal do contrato base
     * @param teamName nome do time do gerente (para `time_nome`)
     * @param gs estado atual (usado para `managerTeamId` e data de término do split)
     */
    fun build(prospect: AcademyProspect, salary: Long, teamName: String, gs: GameState): Player {
        return Player(
            id            = prospect.id,
            nome_jogo     = prospect.nome,
            nome_real     = prospect.nomeReal,
            time_id       = gs.managerTeamId,
            time_nome     = teamName,
            role          = prospect.role,
            titular       = false,
            idade         = prospect.idade,
            nacionalidade = prospect.nacionalidade,
            contrato      = Contrato(
                termino                     = gs.splitEndDate,
                valor_estimado_brl          = salary * 12,
                salario_mensal_estimado_brl = salary,
                fonte_salario               = "base"
            ),
            stats_brutas        = blankStats(),
            atributos_derivados = derivedAttributesAround(prospect.currentOverall),
            championPool        = prospect.championPool
        )
    }

    /** Distribuição dos 5 atributos em torno do overall atual (±2 cada), clamp [35..95]. */
    private fun derivedAttributesAround(overall: Int): AtributosDeriv {
        fun jitter() = (overall + (-2..2).random()).coerceIn(35, 95)
        return AtributosDeriv(
            lane_phase   = jitter(),
            team_fight   = jitter(),
            criatividade = jitter(),
            consistencia = jitter(),
            clutch       = jitter()
        )
    }

    /** Stats brutos zerados — jogador acabou de subir, sem histórico. */
    private fun blankStats() = StatsBrutas(
        jogos = 0, kda = 0.0, kp_pct = 0.0, cs_min = 0.0,
        gd15 = 0, xpd15 = 0, damage_share_pct = 0.0, vision_score_min = 0.0
    )
}
