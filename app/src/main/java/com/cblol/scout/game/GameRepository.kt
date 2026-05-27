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
            .map { migrateLoaded(it) }
            .onSuccess { state = it }
            .getOrNull()
    }

    /**
     * Aplica migrações defensivas em carreiras salvas antes de campos novos
     * existirem. O Gson não respeita default values do Kotlin quando o JSON tem
     * o campo como `null` (ou não tem o campo, dependendo da versão), então
     * precisamos blindar campos opcionais aqui.
     */
    private fun migrateLoaded(gs: GameState): GameState {
        // coachProfile foi adicionado em uma versão posterior — saves antigos
        // não têm esse campo no JSON; Gson o deixa null mesmo com default value.
        @Suppress("SENSELESS_COMPARISON")
        if (gs.coachProfile == null) {
            gs.coachProfile = CoachProfile()
        }

        // Defesa contra `moodHistory == null` em saves antigos. O Gson via
        // reflection põe null em fields List quando o JSON salvo não contém
        // a key, mesmo que o campo Kotlin tenha default = emptyList(). Como
        // o tipo declarado é List<MoodEvent> não-nullable, deixar null
        // crasharia em qualquer leitura. Reconstruímos os overrides afetados.
        val overridesToFix = gs.playerOverrides.entries.filter { entry ->
            @Suppress("SENSELESS_COMPARISON")
            entry.value.moodHistory == null
        }
        overridesToFix.forEach { entry ->
            gs.playerOverrides[entry.key] = entry.value.copy(moodHistory = emptyList())
        }

        // Sistema de olheiros: saves anteriores ao sistema não têm o departamento.
        // Como o tipo é nullable agora, o ScoutingService cria default BASIC na
        // primeira leitura via `department(state)` — mas inicializamos aqui pra
        // que o estado seja persistido no próximo save sem surpresas.
        @Suppress("SENSELESS_COMPARISON")
        if (gs.scoutingDepartment == null) {
            gs.scoutingDepartment = com.cblol.scout.data.ScoutingDepartment()
        }

        // Janelas de transferência: saves anteriores ao sistema não têm o campo
        // (Gson deixa null mesmo com default). Reconstruímos a partir das datas
        // do split salvas no próprio estado, de modo que carreiras em andamento
        // passem a ter mercado com janelas sem precisar reiniciar.
        @Suppress("SENSELESS_COMPARISON")
        if (gs.transferWindows == null || gs.transferWindows.isEmpty()) {
            val gameStart = runCatching {
                com.cblol.scout.domain.usecase.TransferWindowService.gameStartFor(gs.splitStartDate)
            }.getOrDefault(gs.currentDate)
            gs.transferWindows = com.cblol.scout.domain.usecase.TransferWindowService
                .buildWindowsForSplit(gameStart, gs.splitStartDate)
                .toMutableList()
        }

        // Ofertas recebidas: saves anteriores ao sistema não têm o campo. Como
        // é nullable, basta garantir uma lista mutável para o motor preencher.
        if (gs.incomingOffers == null) {
            gs.incomingOffers = mutableListOf()
        }

        // Laços entre jogadores: saves anteriores ao sistema não têm o campo.
        // Como é nullable, basta garantir um mapa mutável; o PlayerBondService
        // popula os pares (neutros) na primeira leitura/tick. A química começa
        // do zero para carreiras já em andamento (não há histórico a recuperar).
        if (gs.playerBonds == null) {
            gs.playerBonds = mutableMapOf()
        }

        // Categoria de base: saves anteriores ao sistema não têm a academia nem
        // a lista de promovidos. Inicializamos vazios; o AcademyService cria a
        // estrutura BASIC sob demanda. Carreiras em andamento ganham uma base
        // vazia (sem prospects) — o gerente pode recrutar quando quiser.
        if (gs.academy == null) {
            gs.academy = com.cblol.scout.data.Academy()
        }
        if (gs.promotedPlayers == null) {
            gs.promotedPlayers = mutableListOf()
        }

        return gs
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

    /**
     * Retorna jogadores efetivamente lotados num time, considerando transferências.
     *
     * Inclui tanto jogadores do snapshot original (1ª divisão) quanto
     * jogadores procedurais da 2ª divisão — desde que estejam atualmente
     * vinculados ao time consultado via override. Sem essa união, um jogador
     * comprado da 2ª divisão sumiria do elenco do gerente.
     */
    fun rosterOf(context: Context, teamId: String): List<Player> {
        val snap = snapshot(context)
        val gs = state
        val overrides = gs?.playerOverrides ?: emptyMap()

        val fromSnapshot = snap.jogadores.map { applyOverride(it, overrides[it.id]) }
        val fromSecondDiv = com.cblol.scout.util.SecondDivisionGenerator.generate()
            .map { applyOverride(it, overrides[it.id]) }
        // Jogadores promovidos da base: vivem no próprio GameState (não há fonte
        // estática). Recebem overrides normalmente (salário, titularidade).
        val fromAcademy = (gs?.promotedPlayers ?: emptyList())
            .map { applyOverride(it, overrides[it.id]) }

        return (fromSnapshot + fromSecondDiv + fromAcademy).filter { it.time_id == teamId }
    }

    /**
     * Retorna jogadores que NÃO pertencem ao time do gerente (mercado).
     *
     * Inclui:
     *  - Jogadores das outras orgs da 1ª divisão (do snapshot original)
     *  - **Jogadores da 2ª divisão** (Circuito Desafiante) — procedurais via
     *    [com.cblol.scout.util.SecondDivisionGenerator]. São mais jovens e mais
     *    baratos, alternativas viáveis para times com orçamento apertado.
     *
     * Filtra jogadores da 2ª divisão que já foram contratados pelo gerente
     * (via override `newTeamId`) para não aparecerem duplicados no mercado.
     */
    fun marketRoster(context: Context): List<Player> {
        val gs = current()
        val snap = snapshot(context)

        // 1ª divisão (snapshot original) com overrides aplicados
        val firstDivision = snap.jogadores.map { applyOverride(it, gs.playerOverrides[it.id]) }
            .filter { it.time_id != gs.managerTeamId }

        // 2ª divisão (procedural) também com overrides — importante para não
        // re-listar jogador já contratado pelo gerente.
        val secondDivision = com.cblol.scout.util.SecondDivisionGenerator.generate()
            .map { applyOverride(it, gs.playerOverrides[it.id]) }
            .filter { it.time_id != gs.managerTeamId }

        return firstDivision + secondDivision
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

    /**
     * Materializa um prospect promovido da base como um [Player] real no elenco
     * do gerente e o persiste em [GameState.promotedPlayers].
     *
     * O [Player] criado entra como **reserva** (`titular = false`); o
     * [SquadManager.validateAndFixRoster] decide depois se há vaga de titular.
     * Atributos derivados são distribuídos em torno do overall atual do prospect
     * (mesma técnica do gerador da 2ª divisão). O salário vem do
     * [com.cblol.scout.domain.usecase.AcademyService.suggestedSalaryFor].
     *
     * @param prospect prospect já REMOVIDO da academia (ver AcademyService.promoteProspect)
     * @param salary salário mensal do contrato base
     * @param teamName nome do time do gerente (para o campo time_nome)
     */
    fun addPromotedProspect(
        context: Context,
        prospect: com.cblol.scout.data.AcademyProspect,
        salary: Long,
        teamName: String
    ): Player {
        val gs = current()
        val ov = prospect.currentOverall
        // Espalha o overall pelos 5 atributos com leve variação (±2), mantendo
        // a média próxima do overall atual.
        fun jitter() = (ov + (-2..2).random()).coerceIn(35, 95)
        val attrs = com.cblol.scout.data.AtributosDeriv(
            lane_phase   = jitter(),
            team_fight   = jitter(),
            criatividade = jitter(),
            consistencia = jitter(),
            clutch       = jitter()
        )
        val player = Player(
            id            = prospect.id,
            nome_jogo     = prospect.nome,
            nome_real     = prospect.nomeReal,
            time_id       = gs.managerTeamId,
            time_nome     = teamName,
            role          = prospect.role,
            titular       = false,
            idade         = prospect.idade,
            nacionalidade = prospect.nacionalidade,
            contrato      = com.cblol.scout.data.Contrato(
                termino                     = gs.splitEndDate,
                valor_estimado_brl          = salary * 12,
                salario_mensal_estimado_brl = salary,
                fonte_salario               = "base"
            ),
            stats_brutas        = com.cblol.scout.data.StatsBrutas(
                jogos = 0, kda = 0.0, kp_pct = 0.0, cs_min = 0.0,
                gd15 = 0, xpd15 = 0, damage_share_pct = 0.0, vision_score_min = 0.0
            ),
            atributos_derivados = attrs,
            championPool        = prospect.championPool
        )
        val list = gs.promotedPlayers ?: mutableListOf<Player>().also { gs.promotedPlayers = it }
        list.add(player)
        return player
    }

    fun log(type: String, message: String) {
        val gs = current()
        gs.gameLog.add(0, LogEntry(gs.currentDate, type, message))
        if (gs.gameLog.size > 80) gs.gameLog.removeAt(gs.gameLog.size - 1)
    }
}
