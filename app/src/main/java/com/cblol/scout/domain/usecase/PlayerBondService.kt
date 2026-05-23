package com.cblol.scout.domain.usecase

import com.cblol.scout.data.BondEvent
import com.cblol.scout.data.BondTier
import com.cblol.scout.data.GameState
import com.cblol.scout.data.Player
import com.cblol.scout.data.PlayerBond
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Sistema de **laços (química) entre jogadores**.
 *
 * Cada par de jogadores do elenco tem um [PlayerBond] numa escala de -100 a
 * +100. O laço **leva tempo** para se formar: cresce devagar conforme os
 * jogadores convivem no mesmo elenco, e é empurrado para cima ou para baixo por
 * vitórias, derrotas, humor, pedidos de transferência e eventos fora de partida
 * (jogadas ensaiadas, brigas).
 *
 * **Efeito na simulação:** a média dos laços do time vira um bônus (ou
 * penalidade) de força aplicado em [com.cblol.scout.game.LiveMatchEngine].
 * Um time entrosado joga acima da soma das partes; um vestiário rachado joga
 * abaixo.
 *
 * **Como os sistemas se conectam:**
 *  - **Tempo** ([tickDaily]): cada dia de convivência aproxima o laço de um teto
 *    natural ([NATURAL_DRIFT_TARGET]) — relações tendem a esquentar com o
 *    tempo, mas devagar.
 *  - **Humor** ([MoraleService]): a evolução diária é modulada pelo humor médio
 *    da dupla. Dois jogadores felizes formam laços mais rápido; dois tristes
 *    azedam a relação.
 *  - **Contrato** ([MoraleService.hasRequestedTransfer]): um jogador que pediu
 *    para sair corrói os laços com todos os colegas (clima ruim no vestiário).
 *  - **Resultado** ([recordSeriesResult]): vitórias unem o grupo; derrotas
 *    podem unir (raramente) ou desgastar.
 *  - **Eventos fora de partida** ([recordCombo] / [recordFight]): jogadas
 *    ensaiadas fortalecem um par; brigas o deterioram. Disparados pelo
 *    [OffMatchEventService].
 *
 * **SOLID:**
 *  - **SRP**: só cuida de laços. Humor/contrato são lidos do [MoraleService] e
 *    do estado, não reimplementados.
 *  - **OCP**: novos gatilhos viram novos `record*` sem mexer no [tickDaily].
 *  - **DIP**: JVM-puro; recebe [GameState] e [Player]. Sem Android.
 */
object PlayerBondService {

    // ── Escala e limites ────────────────────────────────────────────────

    const val MIN_LEVEL = -100
    const val MAX_LEVEL = 100

    /** Tamanho máximo do histórico por laço (evita inflar o save). */
    const val HISTORY_MAX = 12

    // ── Evolução temporal ───────────────────────────────────────────────

    /**
     * Alvo natural para onde o laço deriva com o tempo de convivência. Positivo
     * porque, em geral, conviver e jogar junto aproxima as pessoas — mas o humor
     * pode inverter essa tendência (ver [tickDaily]).
     */
    private const val NATURAL_DRIFT_TARGET = 45

    /** A cada quantos dias de convivência o laço dá 1 passo de drift. */
    private const val DRIFT_INTERVAL_DAYS = 3

    /** Tamanho de cada passo de drift natural (antes do modulador de humor). */
    private const val DRIFT_STEP = 2

    /** Humor médio (0-100) acima do qual a dupla forma laços mais rápido. */
    private const val MOOD_HIGH = 66

    /** Humor médio abaixo do qual a relação tende a azedar. */
    private const val MOOD_LOW = 34

    /** Penalidade diária ao laço quando um dos dois pediu transferência. */
    private const val TRANSFER_REQUEST_BOND_PENALTY = -2

    // ── Deltas de eventos ───────────────────────────────────────────────

    private const val DELTA_COMBO            = +12  // jogada ensaiada (evento)
    private const val DELTA_FIGHT            = -16  // briga (evento)
    private const val DELTA_SERIES_WIN       = +3   // série vencida (todo o par)
    private const val DELTA_SERIES_LOSS      = -2   // série perdida
    private const val DELTA_NEW_TEAMMATE     = 0    // laço começa neutro

    // ── Bônus de força na simulação ─────────────────────────────────────

    /**
     * Converte o laço médio do time (-100..100) em pontos de força. Com fator
     * 0.08, um time totalmente entrosado (laço médio +100) ganha +8 de força —
     * comparável a uma boa composição. Um vestiário tóxico (-100) perde 8.
     */
    private const val TEAM_BOND_STRENGTH_FACTOR = 0.08

    // ── API: acesso ─────────────────────────────────────────────────────

    /** Garante a existência (não-nula) do mapa de laços no estado. */
    private fun bondsOf(state: GameState): MutableMap<String, PlayerBond> {
        var map = state.playerBonds
        if (map == null) {
            map = mutableMapOf()
            state.playerBonds = map
        }
        return map
    }

    /** Retorna o laço entre dois jogadores, ou null se ainda não existe. */
    fun bondBetween(state: GameState, id1: String, id2: String): PlayerBond? {
        if (id1 == id2) return null
        return state.playerBonds?.get(PlayerBond.keyFor(id1, id2))
    }

    /** Nível do laço entre dois jogadores (0 se não existe). */
    fun levelBetween(state: GameState, id1: String, id2: String): Int =
        bondBetween(state, id1, id2)?.level ?: 0

    /** Todos os laços que envolvem um jogador específico. */
    fun bondsForPlayer(state: GameState, playerId: String): List<PlayerBond> =
        (state.playerBonds ?: emptyMap()).values
            .filter { it.playerAId == playerId || it.playerBId == playerId }
            .sortedByDescending { it.level }

    /**
     * Laço médio do time (média dos níveis de todos os pares do roster). Retorna
     * 0 se há menos de 2 jogadores ou nenhum laço formado.
     */
    fun averageTeamBond(state: GameState, roster: List<Player>): Int {
        if (roster.size < 2) return 0
        val ids = roster.map { it.id }
        var sum = 0
        var count = 0
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                sum += levelBetween(state, ids[i], ids[j])
                count++
            }
        }
        return if (count == 0) 0 else sum / count
    }

    /**
     * Bônus (ou penalidade) de força do time derivado do laço médio. Usado pelo
     * motor de simulação. Arredonda para o inteiro mais próximo.
     */
    fun teamStrengthBonus(state: GameState, roster: List<Player>): Int {
        val avg = averageTeamBond(state, roster)
        return Math.round(avg * TEAM_BOND_STRENGTH_FACTOR).toInt()
    }

    // ── Inicialização / sincronização do roster ─────────────────────────

    /**
     * Garante que existe um laço (neutro) para cada par do roster atual e
     * inicializa o relógio de bond tick. Idempotente — chamado ao iniciar a
     * carreira e sempre que o roster muda (compra/venda).
     *
     * Não remove laços de jogadores que saíram: se o jogador voltar, a química
     * antiga é preservada (memória de relacionamento). Laços órfãos são inertes
     * (não entram na média do time porque o jogador não está no roster).
     */
    fun ensureBondsFor(state: GameState, roster: List<Player>) {
        val bonds = bondsOf(state)
        val ids = roster.map { it.id }
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                val key = PlayerBond.keyFor(ids[i], ids[j])
                if (!bonds.containsKey(key)) {
                    val (a, b) = listOf(ids[i], ids[j]).sorted()
                    bonds[key] = PlayerBond(playerAId = a, playerBId = b, level = DELTA_NEW_TEAMMATE)
                }
            }
        }
    }

    // ── Evolução diária ─────────────────────────────────────────────────

    /**
     * Avança a química dos laços do roster por [days] dias. Para cada par:
     *  1. Soma [days] a `daysTogether`.
     *  2. A cada [DRIFT_INTERVAL_DAYS] dias acumulados, aplica um passo de drift
     *     em direção ao alvo natural, MODULADO pelo humor médio da dupla:
     *       - ambos felizes (média > [MOOD_HIGH]): drift acelerado (+50%).
     *       - clima ruim (média < [MOOD_LOW]): drift invertido (relação azeda).
     *       - neutro: drift normal rumo ao alvo.
     *  3. Se um dos dois pediu transferência, aplica [TRANSFER_REQUEST_BOND_PENALTY]
     *     por dia (clima pesado no vestiário).
     *
     * Idempotente por data: usa [GameState.lastBondTickDate] para não processar
     * o mesmo dia duas vezes. Seguro chamar a cada avanço de calendário.
     *
     * @return lista de [BondMilestone] (laços que cruzaram um limiar de faixa),
     *   para o motor logar/notificar.
     */
    fun tickDaily(state: GameState, roster: List<Player>): List<BondMilestone> {
        val today = runCatching { LocalDate.parse(state.currentDate) }.getOrNull()
            ?: return emptyList()

        val last = state.lastBondTickDate?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val days = if (last == null) 1 else ChronoUnit.DAYS.between(last, today).toInt()
        if (days <= 0) {
            state.lastBondTickDate = state.currentDate
            return emptyList()
        }
        state.lastBondTickDate = state.currentDate

        ensureBondsFor(state, roster)
        val bonds = bondsOf(state)
        val milestones = mutableListOf<BondMilestone>()

        // Pré-calcula humor e flag de transferência por jogador (uma vez).
        val moodById = roster.associate { it.id to MoraleService.moodOf(state, it.id) }
        val requestedById = roster.associate {
            it.id to MoraleService.hasRequestedTransfer(state, it.id)
        }

        val idList = roster.map { it.id }
        for (a in idList.indices) {
            for (b in a + 1 until idList.size) {
                val idA = idList[a]
                val idB = idList[b]
                val key = PlayerBond.keyFor(idA, idB)
                val bond = bonds[key] ?: continue

                val prevTier = BondTier.from(bond.level)
                val newDaysTogether = bond.daysTogether + days

                // Passos de drift completados neste avanço.
                val stepsBefore = bond.daysTogether / DRIFT_INTERVAL_DAYS
                val stepsAfter  = newDaysTogether / DRIFT_INTERVAL_DAYS
                val driftSteps  = stepsAfter - stepsBefore

                var newLevel = bond.level

                if (driftSteps > 0) {
                    val avgMood = ((moodById[idA] ?: 50) + (moodById[idB] ?: 50)) / 2
                    val perStep = driftStepFor(bond.level, avgMood)
                    newLevel += perStep * driftSteps
                }

                // Penalidade por pedido de transferência (clima ruim).
                val anyRequested = (requestedById[idA] == true) || (requestedById[idB] == true)
                if (anyRequested) {
                    newLevel += TRANSFER_REQUEST_BOND_PENALTY * days
                }

                newLevel = newLevel.coerceIn(MIN_LEVEL, MAX_LEVEL)

                bonds[key] = bond.copy(level = newLevel, daysTogether = newDaysTogether)

                // Detecta mudança de faixa para notificar (só marcos relevantes).
                val newTier = BondTier.from(newLevel)
                if (newTier != prevTier && (newTier == BondTier.BONDED || newTier == BondTier.TOXIC)) {
                    milestones += BondMilestone(idA, idB, newTier, newLevel)
                }
            }
        }

        return milestones
    }

    /**
     * Tamanho e direção de um passo de drift, dado o nível atual e o humor médio
     * da dupla.
     *
     * - Humor alto: relação esquenta — drift para cima (até o teto positivo),
     *   acelerado.
     * - Humor baixo: relação esfria — drift para baixo (rumo ao atrito).
     * - Humor neutro: drift suave rumo ao [NATURAL_DRIFT_TARGET].
     */
    private fun driftStepFor(currentLevel: Int, avgMood: Int): Int = when {
        avgMood >= MOOD_HIGH -> {
            // Esquenta: sobe rumo ao máximo, com passo reforçado.
            if (currentLevel < MAX_LEVEL) DRIFT_STEP + 1 else 0
        }
        avgMood <= MOOD_LOW -> {
            // Azeda: desce rumo ao atrito.
            if (currentLevel > MIN_LEVEL) -DRIFT_STEP else 0
        }
        else -> {
            // Neutro: converge devagar para o alvo natural.
            val dir = when {
                currentLevel < NATURAL_DRIFT_TARGET -> +1
                currentLevel > NATURAL_DRIFT_TARGET -> -1
                else -> 0
            }
            dir * DRIFT_STEP
        }
    }

    // ── Eventos de resultado ────────────────────────────────────────────

    /**
     * Aplica o efeito de uma série (BO3) a TODOS os pares do roster titular.
     * Vitória aproxima o grupo; derrota desgasta levemente.
     */
    fun recordSeriesResult(state: GameState, roster: List<Player>, won: Boolean) {
        ensureBondsFor(state, roster)
        val delta = if (won) DELTA_SERIES_WIN else DELTA_SERIES_LOSS
        val reason = if (won) "Série vencida juntos" else "Série perdida"
        val ids = roster.map { it.id }
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                applyDelta(state, ids[i], ids[j], delta, reason, logHistory = false)
            }
        }
    }

    // ── Eventos fora de partida (combo / briga) ─────────────────────────

    /**
     * Registra uma JOGADA ENSAIADA entre dois jogadores (evento fora de partida
     * positivo). Fortalece bastante o laço e entra no histórico.
     */
    fun recordCombo(state: GameState, idA: String, idB: String, reason: String = "Jogada ensaiada no treino") {
        applyDelta(state, idA, idB, DELTA_COMBO, reason, logHistory = true)
    }

    /**
     * Registra uma BRIGA entre dois jogadores (evento fora de partida negativo).
     * Deteriora o laço e entra no histórico.
     */
    fun recordFight(state: GameState, idA: String, idB: String, reason: String = "Briga no vestiário") {
        applyDelta(state, idA, idB, DELTA_FIGHT, reason, logHistory = true)
    }

    /**
     * Escolhe o par de jogadores mais provável para uma JOGADA ENSAIADA: a dupla
     * com melhor química atual (e humor decente), pois jogadas nascem de quem já
     * se entende. Retorna null se o roster tem menos de 2 jogadores.
     */
    fun pickComboPair(state: GameState, roster: List<Player>): Pair<Player, Player>? {
        if (roster.size < 2) return null
        return bestPairBy(roster) { a, b ->
            levelBetween(state, a.id, b.id) +
                (MoraleService.moodOf(state, a.id) + MoraleService.moodOf(state, b.id)) / 10
        }
    }

    /**
     * Escolhe o par mais provável para uma BRIGA: a dupla com pior química
     * (e/ou humor baixo), pois conflitos nascem de quem já tem atrito. Retorna
     * null se o roster tem menos de 2 jogadores.
     */
    fun pickFightPair(state: GameState, roster: List<Player>): Pair<Player, Player>? {
        if (roster.size < 2) return null
        return bestPairBy(roster) { a, b ->
            // Quanto MENOR o laço e o humor, maior a chance de briga → invertemos
            // o sinal para reaproveitar o "maior score vence".
            -(levelBetween(state, a.id, b.id)) -
                (MoraleService.moodOf(state, a.id) + MoraleService.moodOf(state, b.id)) / 10
        }
    }

    /** Retorna o par com maior score segundo [score], com leve ruído de desempate. */
    private fun bestPairBy(roster: List<Player>, score: (Player, Player) -> Int): Pair<Player, Player>? {
        var best: Pair<Player, Player>? = null
        var bestScore = Int.MIN_VALUE
        for (i in roster.indices) {
            for (j in i + 1 until roster.size) {
                val s = score(roster[i], roster[j]) + Random.nextInt(-3, 4)
                if (s > bestScore) {
                    bestScore = s
                    best = roster[i] to roster[j]
                }
            }
        }
        return best
    }

    // ── Mutação central ─────────────────────────────────────────────────

    /**
     * Aplica um delta ao laço entre dois jogadores, criando o laço (neutro) se
     * ainda não existe. Opcionalmente registra no histórico.
     */
    private fun applyDelta(
        state: GameState,
        id1: String,
        id2: String,
        delta: Int,
        reason: String,
        logHistory: Boolean
    ) {
        if (id1 == id2 || delta == 0) return
        val bonds = bondsOf(state)
        val key = PlayerBond.keyFor(id1, id2)
        val (a, b) = listOf(id1, id2).sorted()
        val existing = bonds[key] ?: PlayerBond(playerAId = a, playerBId = b)

        val newLevel = (existing.level + delta).coerceIn(MIN_LEVEL, MAX_LEVEL)
        val effective = newLevel - existing.level
        if (effective == 0 && !logHistory) return

        val history = if (logHistory) {
            val ev = BondEvent(
                date = state.currentDate,
                reason = reason,
                delta = effective,
                levelAfter = newLevel
            )
            (listOf(ev) + existing.history).take(HISTORY_MAX)
        } else {
            existing.history
        }
        bonds[key] = existing.copy(level = newLevel, history = history)
    }

    /**
     * Marco de laço (cruzou para parceria forte ou rivalidade tóxica), para o
     * motor logar/notificar.
     */
    data class BondMilestone(
        val playerAId: String,
        val playerBId: String,
        val tier: BondTier,
        val level: Int
    )
}
