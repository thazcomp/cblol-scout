package com.cblol.scout.domain.usecase

import com.cblol.scout.data.GameState
import com.cblol.scout.data.Player
import com.cblol.scout.data.PlayerOverride
import com.cblol.scout.data.ScoutingDepartment
import com.cblol.scout.data.ScoutingDepartmentTier

/**
 * Sistema de olheiros (scouting).
 *
 * Jogadores de outros times começam **ocultos**: a UI mostra apenas
 * informações públicas (nome, role, time, idade, salário). Para revelar
 * overall, atributos derivados, stats e champion pool, o treinador precisa
 * investir em scouting, que progride dia-a-dia conforme o tier do
 * departamento de olheiros.
 *
 * **Tabela de visibilidade por nível:**
 *  | Nível | O que fica visível                                     |
 *  |-------|--------------------------------------------------------|
 *  | 0     | nome, role, time, idade, salário                       |
 *  | 1     | + faixa aproximada de overall ("70-80")                |
 *  | 2     | + overall exato                                        |
 *  | 3     | + lane_phase, team_fight                               |
 *  | 4     | + criatividade, consistencia, clutch (5 atributos)     |
 *  | 5     | + stats brutos (KDA, CS/min) + champion pool           |
 *
 * O jogador do próprio time é sempre tratado como nível 5 (visibilidade total).
 *
 * **Velocidade**: o departamento de olheiros (BASIC/PRO/ELITE) define quantos
 * dias entre upgrades de nível ([ScoutingDepartmentTier.daysPerLevel]).
 * BASIC = 5 dias/nível (25 dias até nível 5); ELITE = 2 dias/nível (10 dias).
 *
 * **SOLID:**
 * - **SRP**: cada função tem uma responsabilidade (visibilidade, start, tick, upgrade).
 * - **OCP**: novos tiers ou novos níveis de visibilidade = estender o enum/when.
 * - **DIP**: depende só de [GameState] e modelos. JVM-puro, testável.
 */
object ScoutingService {

    /** Nível máximo (visibilidade total). */
    const val MAX_LEVEL = 5

    /** Faixas de overall mostradas no nível 1 (substitui o número exato). */
    fun overallBand(overall: Int): String = when {
        overall >= 90 -> "90+"
        overall >= 80 -> "80-89"
        overall >= 70 -> "70-79"
        overall >= 60 -> "60-69"
        else          -> "<60"
    }

    // ── Visibilidade ────────────────────────────────────────────────────

    /**
     * Snapshot do que está visível para o jogador olhar sobre um Player.
     * Construído a partir do nível de scouting atual + se é do próprio time.
     */
    data class Visibility(
        val level: Int,
        val showOverallBand: Boolean,
        val showOverallExact: Boolean,
        val showLaneAndTeamfight: Boolean,
        val showAllAttributes: Boolean,
        val showRawStats: Boolean,
        val showChampionPool: Boolean
    )

    /**
     * Visibilidade de [player] dado o estado da carreira. Jogadores do roster
     * do gerente sempre retornam nível 5 (visibilidade total).
     */
    fun visibilityOf(state: GameState, player: Player): Visibility {
        // Jogador do próprio time: tudo aberto
        if (player.time_id == state.managerTeamId) {
            return Visibility(
                level                = MAX_LEVEL,
                showOverallBand      = true,
                showOverallExact     = true,
                showLaneAndTeamfight = true,
                showAllAttributes    = true,
                showRawStats         = true,
                showChampionPool     = true
            )
        }
        val level = state.playerOverrides[player.id]?.scoutLevel ?: 0
        return Visibility(
            level                = level,
            showOverallBand      = level >= 1,
            showOverallExact     = level >= 2,
            showLaneAndTeamfight = level >= 3,
            showAllAttributes    = level >= 4,
            showRawStats         = level >= 5,
            showChampionPool     = level >= 5
        )
    }

    /** Conveniência: nível atual de scouting de um jogador. */
    fun scoutLevelOf(state: GameState, playerId: String): Int =
        state.playerOverrides[playerId]?.scoutLevel ?: 0

    /** Conveniência: jogador está sendo escotado AGORA (nível < MAX e iniciado)? */
    fun isCurrentlyScouting(state: GameState, playerId: String): Boolean {
        val ov = state.playerOverrides[playerId] ?: return false
        return ov.scoutStartedOn != null && ov.scoutLevel < MAX_LEVEL
    }

    /** Lista de jogadores atualmente em scouting (para a UI mostrar progresso). */
    fun activeScouts(state: GameState): List<String> =
        state.playerOverrides.entries
            .filter { it.value.scoutStartedOn != null && it.value.scoutLevel < MAX_LEVEL }
            .map { it.key }

    // ── Departamento ────────────────────────────────────────────────────

    /** Retorna o departamento atual (cria default BASIC se ainda não existe). */
    fun department(state: GameState): ScoutingDepartment {
        var dept = state.scoutingDepartment
        if (dept == null) {
            dept = ScoutingDepartment()
            state.scoutingDepartment = dept
        }
        return dept
    }

    /** Tier atual do departamento. */
    fun tier(state: GameState): ScoutingDepartmentTier = department(state).tier

    /**
     * Tenta fazer upgrade para o próximo tier. Retorna [UpgradeResult] indicando
     * sucesso ou motivo do erro.
     */
    fun upgradeDepartment(state: GameState): UpgradeResult {
        val current = department(state).tier
        val next = nextTier(current) ?: return UpgradeResult.ALREADY_MAX
        if (state.coachProfile.reputation < next.minReputation) {
            return UpgradeResult.LOW_REPUTATION
        }
        if (state.budget < next.upgradeCost) {
            return UpgradeResult.INSUFFICIENT_FUNDS
        }
        state.budget -= next.upgradeCost
        department(state).tier = next
        return UpgradeResult.OK
    }

    private fun nextTier(current: ScoutingDepartmentTier): ScoutingDepartmentTier? = when (current) {
        ScoutingDepartmentTier.BASIC -> ScoutingDepartmentTier.PRO
        ScoutingDepartmentTier.PRO   -> ScoutingDepartmentTier.ELITE
        ScoutingDepartmentTier.ELITE -> null
    }

    /** Resultados possíveis da tentativa de upgrade. */
    enum class UpgradeResult {
        OK, ALREADY_MAX, LOW_REPUTATION, INSUFFICIENT_FUNDS
    }

    // ── Iniciar / parar scouting ────────────────────────────────────────

    /** Quanto custa iniciar um scouting (descontado do orçamento). */
    const val START_SCOUT_COST = 5_000L

    /** Resultado possível de iniciar um scouting. */
    enum class StartResult {
        OK,
        ALREADY_MAX_LEVEL,
        ALREADY_SCOUTING,
        SLOTS_FULL,
        INSUFFICIENT_FUNDS,
        SAME_TEAM
    }

    /**
     * Inicia o scouting de um jogador. Custa [START_SCOUT_COST] e ocupa um
     * slot do departamento até o jogador atingir nível 5 ou ser cancelado.
     */
    fun startScouting(state: GameState, player: Player): StartResult {
        if (player.time_id == state.managerTeamId) return StartResult.SAME_TEAM
        val ov = state.playerOverrides[player.id] ?: PlayerOverride(player.id)
        if (ov.scoutLevel >= MAX_LEVEL) return StartResult.ALREADY_MAX_LEVEL
        if (ov.scoutStartedOn != null) return StartResult.ALREADY_SCOUTING

        val activeCount = activeScouts(state).size
        if (activeCount >= tier(state).maxConcurrentScouts) return StartResult.SLOTS_FULL

        if (state.budget < START_SCOUT_COST) return StartResult.INSUFFICIENT_FUNDS

        state.budget -= START_SCOUT_COST
        state.playerOverrides[player.id] = ov.copy(
            scoutStartedOn       = state.currentDate,
            scoutDaysAccumulated = 0
        )
        return StartResult.OK
    }

    /** Cancela scouting em andamento (libera o slot, não devolve o custo). */
    fun cancelScouting(state: GameState, playerId: String) {
        val ov = state.playerOverrides[playerId] ?: return
        state.playerOverrides[playerId] = ov.copy(
            scoutStartedOn       = null,
            scoutDaysAccumulated = 0
        )
    }

    // ── Tick diário (chamado pelo GameEngine.advanceDays) ───────────────

    /**
     * Resultado de uma rodada de tick — qual jogador subiu de nível,
     * para que o GameEngine possa logar.
     */
    data class TickResult(
        val levelUps: List<LevelUp>
    ) {
        data class LevelUp(val playerId: String, val newLevel: Int)
    }

    /**
     * Avança um dia para todos os scoutings ativos. Sobe o nível quando o
     * acumulado atinge [ScoutingDepartmentTier.daysPerLevel] dias.
     *
     * Quando o jogador chega a [MAX_LEVEL], o scouting fica concluído
     * (`scoutStartedOn = null`) e o slot é liberado automaticamente.
     */
    fun tickDaily(state: GameState): TickResult {
        val daysPerLevel = tier(state).daysPerLevel
        val levelUps = mutableListOf<TickResult.LevelUp>()

        val activeIds = state.playerOverrides.entries
            .filter { it.value.scoutStartedOn != null && it.value.scoutLevel < MAX_LEVEL }
            .map { it.key }

        for (playerId in activeIds) {
            val ov = state.playerOverrides[playerId] ?: continue
            val newAccumulated = ov.scoutDaysAccumulated + 1

            if (newAccumulated >= daysPerLevel) {
                val newLevel = (ov.scoutLevel + 1).coerceAtMost(MAX_LEVEL)
                val completed = newLevel >= MAX_LEVEL
                state.playerOverrides[playerId] = ov.copy(
                    scoutLevel           = newLevel,
                    scoutDaysAccumulated = 0,
                    // Se completou o nível máximo, libera o slot
                    scoutStartedOn       = if (completed) null else ov.scoutStartedOn
                )
                levelUps += TickResult.LevelUp(playerId, newLevel)
            } else {
                state.playerOverrides[playerId] = ov.copy(scoutDaysAccumulated = newAccumulated)
            }
        }
        return TickResult(levelUps)
    }

    // ── Custo semanal ───────────────────────────────────────────────────

    /** Custo de manutenção semanal do departamento atual. */
    fun weeklyMaintenanceCost(state: GameState): Long =
        tier(state).weeklyMaintenanceCost
}
