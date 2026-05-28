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

        // Banco: saves anteriores ao sistema não têm o estado bancário. Como é
        // nullable, basta garantir uma estrutura vazia; o BankService cria sob
        // demanda de qualquer forma. Carreiras em andamento começam sem dívida.
        if (gs.bank == null) {
            gs.bank = com.cblol.scout.data.BankState()
        }

        // Divisão + estado da 2ª divisão: saves anteriores ao modo não têm
        // estes campos. Como `division` tem default não-nullable
        // (Division.FIRST) e as listas são não-nullable com default vazio, o
        // Gson pode mesmo assim deixar `division` null em saves antigos
        // (default values do Kotlin não se aplicam quando o reflection do Gson
        // popula o campo). Blindamos defensivamente.
        @Suppress("SENSELESS_COMPARISON")
        if (gs.division == null) {
            gs.division = com.cblol.scout.data.Division.FIRST
        }
        @Suppress("SENSELESS_COMPARISON")
        if (gs.secondDivisionTeams == null) {
            gs.secondDivisionTeams = mutableListOf()
        }
        @Suppress("SENSELESS_COMPARISON")
        if (gs.secondDivisionPlayers == null) {
            gs.secondDivisionPlayers = mutableListOf()
        }

        // Feed de notícias: saves anteriores ao sistema não têm o campo. Como é
        // nullable, basta garantir uma lista mutável; o NewsService publica sob
        // demanda. Carreiras em andamento começam com o feed vazio.
        if (gs.news == null) {
            gs.news = mutableListOf()
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
     * Une **quatro fontes** possíveis:
     *  1. Snapshot oficial (1ª divisão)
     *  2. [com.cblol.scout.util.SecondDivisionGenerator] (free agents do CD)
     *  3. [com.cblol.scout.data.GameState.promotedPlayers] (academia)
     *  4. [com.cblol.scout.data.GameState.secondDivisionPlayers] (times da 2ª
     *     divisão quando a carreira começou lá)
     *
     * O filtro final por `teamId` garante que cada jogador apareça apenas no
     * time em que está atualmente vinculado (considerando overrides).
     */
    fun rosterOf(context: Context, teamId: String): List<Player> {
        val snap = snapshot(context)
        val gs = state
        val overrides = gs?.playerOverrides ?: emptyMap()

        val fromSnapshot = snap.jogadores.map { applyOverride(it, overrides[it.id]) }
        val fromSecondDivAgents = com.cblol.scout.util.SecondDivisionGenerator.generate()
            .map { applyOverride(it, overrides[it.id]) }
        val fromAcademy = (gs?.promotedPlayers ?: emptyList())
            .map { applyOverride(it, overrides[it.id]) }
        // Roster procedural dos TIMES da 2ª divisão (modo "começar de baixo").
        // Vazio em carreiras de 1ª divisão — sem custo.
        val fromSecondDivTeams = (gs?.secondDivisionPlayers ?: emptyList())
            .map { applyOverride(it, overrides[it.id]) }

        return (fromSnapshot + fromSecondDivAgents + fromAcademy + fromSecondDivTeams)
            .filter { it.time_id == teamId }
    }

    /**
     * Lista os 8 times da divisão ATIVA da carreira. Em [com.cblol.scout.data.Division.FIRST]
     * vem do snapshot; em [com.cblol.scout.data.Division.SECOND] vem do estado
     * persistido. Use sempre que precisar dos adversários do gerente — evita
     * a armadilha de listar times de 1ª div em carreira de 2ª div (ou vice-versa).
     */
    fun teamsForCurrentDivision(context: Context): List<com.cblol.scout.data.Team> {
        val gs = state ?: return snapshot(context).times
        return when (gs.division) {
            com.cblol.scout.data.Division.FIRST  -> snapshot(context).times
            com.cblol.scout.data.Division.SECOND -> gs.secondDivisionTeams.toList()
        }
    }

    /**
     * Retorna jogadores que NÃO pertencem ao time do gerente (mercado).
     *
     * **Conforme a divisão da carreira**, monta o mercado adequado:
     *  - **1ª divisão**: jogadores das outras orgs do snapshot + free agents
     *    procedurais do CD (opção barata para quem precisa renovar).
     *  - **2ª divisão**: jogadores dos OUTROS times da 2ª divisão + os mesmos
     *    free agents do CD. Jogadores da 1ª divisão ficam **fora de alcance**
     *    — um time da 2ª div não teria como contratar um titular do CBLOL
     *    (e mesmo se tivesse, o orçamento não cobre). É uma trava narrativa
     *    coerente com o modo.
     */
    fun marketRoster(context: Context): List<Player> {
        val gs = current()
        val freeAgents = com.cblol.scout.util.SecondDivisionGenerator.generate()
            .map { applyOverride(it, gs.playerOverrides[it.id]) }
            .filter { it.time_id != gs.managerTeamId }

        val rivals = when (gs.division) {
            com.cblol.scout.data.Division.FIRST -> {
                snapshot(context).jogadores
                    .map { applyOverride(it, gs.playerOverrides[it.id]) }
                    .filter { it.time_id != gs.managerTeamId }
            }
            com.cblol.scout.data.Division.SECOND -> {
                gs.secondDivisionPlayers
                    .map { applyOverride(it, gs.playerOverrides[it.id]) }
                    .filter { it.time_id != gs.managerTeamId }
            }
        }
        return rivals + freeAgents
    }

    /** Aplica override num jogador (cria nova instância). */
    private fun applyOverride(p: Player, ov: PlayerOverride?): Player {
        if (ov == null) return p
        val newContract = p.contrato.copy(
            termino = ov.newContractEnd ?: p.contrato.termino,
            salario_mensal_estimado_brl = ov.newSalary ?: p.contrato.salario_mensal_estimado_brl,
            fonte_salario = if (ov.newSalary != null) "renegociado" else p.contrato.fonte_salario
        )
        // Quando o jogador muda de time (newTeamId), procura o nome em ambas as
        // fontes — snapshot oficial (1ª div) ou times procedurais da 2ª div do
        // estado atual. Em carreira na 2ª div, sem essa busca o time_nome
        // ficaria com o id cru ao trocar de time.
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
