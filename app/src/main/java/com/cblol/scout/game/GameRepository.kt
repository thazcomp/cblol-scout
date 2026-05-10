package com.cblol.scout.game

import android.content.Context
import com.cblol.scout.data.*
import com.cblol.scout.util.JsonLoader
import com.google.gson.Gson

/**
 * Singleton com o estado da carreira em memória + persistência simples em SharedPreferences.
 * Para um app real eu usaria Room/DataStore; aqui SharedPreferences é suficiente porque o
 * estado é pequeno (algumas dezenas de KB).
 */
object GameRepository {
    private const val PREFS = "cblol_scout_game"
    private const val KEY_STATE = "game_state"

    private val gson = Gson()
    private var state: GameState? = null
    private var snapshot: SnapshotData? = null

    /** Snapshot original (imutável) carregado do assets. */
    fun snapshot(context: Context): SnapshotData {
        if (snapshot == null) snapshot = JsonLoader.loadSnapshot(context)
        return snapshot!!
    }

    fun hasSave(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.contains(KEY_STATE)
    }

    fun load(context: Context): GameState? {
        if (state != null) return state
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_STATE, null) ?: return null
        return runCatching { gson.fromJson(json, GameState::class.java) }
            .onSuccess { state = it }
            .getOrNull()
    }

    fun save(context: Context, gs: GameState? = null) {
        val target = gs ?: state ?: return
        state = target
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STATE, gson.toJson(target)).apply()
    }

    fun clear(context: Context) {
        state = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_STATE).apply()
    }

    fun current(): GameState = state ?: error("GameState não carregado")

    // ───── Helpers de leitura considerando overrides ─────

    /** Retorna jogadores efetivamente lotados num time, considerando transferências. */
    fun rosterOf(context: Context, teamId: String): List<Player> {
        val snap = snapshot(context)
        val gs = state
        val overrides = gs?.playerOverrides ?: emptyMap()
        return snap.jogadores.map { applyOverride(it, overrides[it.id]) }
            .filter { it.time_id == teamId }
    }

    /** Retorna jogadores que NÃO pertencem ao time do gerente (mercado). */
    fun marketRoster(context: Context): List<Player> {
        val gs = current()
        val snap = snapshot(context)
        return snap.jogadores.map { applyOverride(it, gs.playerOverrides[it.id]) }
            .filter { it.time_id != gs.managerTeamId }
    }

    /** Aplica override num jogador (cria nova instância). */
    private fun applyOverride(p: Player, ov: PlayerOverride?): Player {
        if (ov == null) return p
        val newContract = p.contrato.copy(
            termino = ov.newContractEnd ?: p.contrato.termino,
            salario_mensal_estimado_brl = ov.newSalary ?: p.contrato.salario_mensal_estimado_brl,
            fonte_salario = if (ov.newSalary != null) "renegociado" else p.contrato.fonte_salario
        )
        return p.copy(
            time_id = ov.newTeamId ?: p.time_id,
            time_nome = ov.newTeamId?.let { id ->
                snapshot?.times?.find { it.id == id }?.nome ?: p.time_nome
            } ?: p.time_nome,
            titular = ov.titular ?: p.titular,
            contrato = newContract
        )
    }

    /** Atualiza override (cria se não existir). */
    fun updateOverride(playerId: String, transform: (PlayerOverride) -> PlayerOverride) {
        val gs = current()
        val current = gs.playerOverrides[playerId] ?: PlayerOverride(playerId)
        gs.playerOverrides[playerId] = transform(current)
    }

    fun log(type: String, message: String) {
        val gs = current()
        gs.gameLog.add(0, LogEntry(gs.currentDate, type, message))
        if (gs.gameLog.size > 80) gs.gameLog.removeAt(gs.gameLog.size - 1)
    }
}
