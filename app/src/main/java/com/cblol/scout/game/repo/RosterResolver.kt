package com.cblol.scout.game.repo

import android.content.Context
import com.cblol.scout.data.Division
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Player
import com.cblol.scout.data.PlayerOverride
import com.cblol.scout.data.SnapshotData
import com.cblol.scout.data.Team
import com.cblol.scout.util.SecondDivisionGenerator

/**
 * Resolve rosters e listagens de times agregando MÚLTIPLAS FONTES de dados
 * (snapshot oficial, geradores procedurais, estado persistido) e aplicando
 * overrides de transferência/contrato em cima.
 *
 * Extraído do [com.cblol.scout.game.GameRepository] para isolar a regra de
 * união-de-fontes + override de uma só vez: quem consulta rosters não precisa
 * conhecer os 4 lugares onde jogadores podem viver no save.
 *
 * Recebe o [snapshot] e o [state] como parâmetros (em vez de chamar o
 * GameRepository) para ser puro/testável e evitar dependência circular.
 */
internal object RosterResolver {

    /**
     * Retorna jogadores efetivamente lotados num time, considerando transferências.
     *
     * Une **quatro fontes** possíveis:
     *  1. Snapshot oficial (1ª divisão)
     *  2. [SecondDivisionGenerator] (free agents do CD)
     *  3. [GameState.promotedPlayers] (academia)
     *  4. [GameState.secondDivisionPlayers] (times da 2ª divisão quando a
     *     carreira começou lá)
     *
     * O filtro final por `teamId` garante que cada jogador apareça apenas no
     * time em que está atualmente vinculado (considerando overrides).
     */
    fun rosterOf(
        snapshot: SnapshotData,
        state: GameState?,
        teamId: String
    ): List<Player> {
        val overrides = state?.playerOverrides ?: emptyMap()
        val fromSnapshot = snapshot.jogadores.map { applyOverride(snapshot, state, it, overrides[it.id]) }
        val fromSecondDivAgents = SecondDivisionGenerator.generate()
            .map { applyOverride(snapshot, state, it, overrides[it.id]) }
        val fromAcademy = (state?.promotedPlayers ?: emptyList())
            .map { applyOverride(snapshot, state, it, overrides[it.id]) }
        // Roster procedural dos TIMES da 2ª divisão (modo "começar de baixo").
        // Vazio em carreiras de 1ª divisão — sem custo.
        val fromSecondDivTeams = (state?.secondDivisionPlayers ?: emptyList())
            .map { applyOverride(snapshot, state, it, overrides[it.id]) }

        return (fromSnapshot + fromSecondDivAgents + fromAcademy + fromSecondDivTeams)
            .filter { it.time_id == teamId }
    }

    /**
     * Lista os 8 times da divisão ATIVA da carreira. Em [Division.FIRST] vem
     * do snapshot; em [Division.SECOND] vem do estado persistido.
     */
    fun teamsForCurrentDivision(snapshot: SnapshotData, state: GameState?): List<Team> {
        if (state == null) return snapshot.times
        return when (state.division) {
            Division.FIRST  -> snapshot.times
            Division.SECOND -> state.secondDivisionTeams.toList()
        }
    }

    /**
     * Retorna jogadores que NÃO pertencem ao time do gerente (mercado),
     * filtrados por divisão:
     *
     *  - **1ª divisão**: jogadores das outras orgs do snapshot + free agents
     *    procedurais do CD.
     *  - **2ª divisão**: jogadores dos OUTROS times da 2ª divisão + free agents.
     *    Jogadores da 1ª divisão ficam **fora de alcance** (não aparecem) —
     *    trava narrativa coerente com o orçamento reduzido do CD.
     */
    fun marketRoster(snapshot: SnapshotData, state: GameState): List<Player> {
        val freeAgents = SecondDivisionGenerator.generate()
            .map { applyOverride(snapshot, state, it, state.playerOverrides[it.id]) }
            .filter { it.time_id != state.managerTeamId }

        val rivals = when (state.division) {
            Division.FIRST -> {
                snapshot.jogadores
                    .map { applyOverride(snapshot, state, it, state.playerOverrides[it.id]) }
                    .filter { it.time_id != state.managerTeamId }
            }
            Division.SECOND -> {
                state.secondDivisionPlayers
                    .map { applyOverride(snapshot, state, it, state.playerOverrides[it.id]) }
                    .filter { it.time_id != state.managerTeamId }
            }
        }
        return rivals + freeAgents
    }

    /**
     * Aplica override num jogador (cria nova instância imutável).
     *
     * Resolve o `time_nome` em ambas as fontes — snapshot oficial (1ª div) ou
     * times procedurais da 2ª div do estado atual. Em carreira na 2ª div, sem
     * essa busca o `time_nome` ficaria com o id cru ao trocar de time.
     */
    fun applyOverride(
        snapshot: SnapshotData?,
        state: GameState?,
        p: Player,
        ov: PlayerOverride?
    ): Player {
        if (ov == null) return p
        val newContract = p.contrato.copy(
            termino = ov.newContractEnd ?: p.contrato.termino,
            salario_mensal_estimado_brl = ov.newSalary ?: p.contrato.salario_mensal_estimado_brl,
            fonte_salario = if (ov.newSalary != null) "renegociado" else p.contrato.fonte_salario
        )
        val resolvedTeamName = ov.newTeamId?.let { id ->
            snapshot?.times?.find { it.id == id }?.nome
                ?: state?.secondDivisionTeams?.find { it.id == id }?.nome
                ?: p.time_nome
        } ?: p.time_nome
        return p.copy(
            time_id = ov.newTeamId ?: p.time_id,
            time_nome = resolvedTeamName,
            titular = ov.titular ?: p.titular,
            contrato = newContract
        )
    }
}
