package com.cblol.scout.domain.usecase

import com.cblol.scout.data.GameState
import com.cblol.scout.data.Mood
import com.cblol.scout.data.MoodEvent
import com.cblol.scout.data.Player
import com.cblol.scout.data.PlayerOverride
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.sign
import kotlin.random.Random

/**
 * Sistema de moral dos jogadores.
 *
 * Cada jogador tem um valor de moral (0-100) que parte de um valor aleatório
 * dentro da faixa neutra na primeira leitura, e depois é modificado pelos
 * eventos do jogo (vitória/derrota, transferências, banca, decay temporal).
 *
 * **Histórico**: cada mudança é registrada em [PlayerOverride.moodHistory]
 * com data, motivo, delta e valor após. Limitado a [HISTORY_MAX_ENTRIES]
 * para não inflar o save.
 *
 * **Decay temporal**: jogadores que ficam muitas semanas sem jogar (lastPlayedDate
 * antigo) convergem gradualmente para o valor neutro (50). Isso simula que
 * tanto a euforia quanto o desânimo diminuem com o tempo se nada acontece.
 *
 * **Modificador de overall**: a moral atual ajusta o overall efetivo do jogador
 * na simulação. Triste = −3, Feliz = +2, Em êxtase (95+) = +5. Ver
 * [moodOverallModifier].
 *
 * **Eventos exclusivos**:
 *  - Moral 95+: jogador entra em modo "em êxtase" com bônus de overall maior
 *  - Moral 5-: jogador pode pedir transferência (flag persistida)
 *
 * **SOLID:**
 * - **SRP**: cada função tem uma responsabilidade (record evento X, calcular
 *   modificador, decay, etc.).
 * - **OCP**: novos eventos viram novas funções `recordX` sem alterar as existentes.
 * - **DIP**: depende só de [GameState] e do modelo de domínio.
 */
object MoraleService {

    // ── Constantes ───────────────────────────────────────────────────────

    private const val MIN_MOOD = 0
    private const val MAX_MOOD = 100

    /** Tamanho máximo do histórico de mudanças por jogador. */
    const val HISTORY_MAX_ENTRIES = 20

    /** Faixa inicial dos jogadores ao começar a carreira (sempre na zona neutra). */
    private val INITIAL_RANGE = 40..70

    // Deltas de cada evento
    private const val DELTA_MAP_WIN          = +5
    private const val DELTA_MAP_LOSE         = -5
    private const val DELTA_SERIES_WIN_BONUS = +10
    private const val DELTA_SERIES_LOSE_BONUS = -10
    private const val DELTA_BECAME_RESERVE   = -15
    private const val DELTA_BECAME_STARTER   = +15
    private const val DELTA_CONTRACT_RENEW   = +20
    private const val DELTA_SOLD             = -25
    private const val DELTA_HIRED            = +25

    // ── Decay temporal ───────────────────────────────────────────────────

    /** Valor neutro para o qual o decay converge. */
    private const val NEUTRAL_VALUE = 50

    /** Dias sem jogar antes do decay começar a atuar. */
    private const val DECAY_GRACE_DAYS = 7

    /**
     * Velocidade do decay: quantos pontos de moral o jogador perde (ou ganha)
     * por dia adicional de inatividade após o período de carência.
     * Move SEMPRE em direção ao NEUTRAL_VALUE (50).
     */
    private const val DECAY_POINTS_PER_DAY = 1

    // ── Eventos exclusivos ──────────────────────────────────────────────

    /** Acima deste valor, o jogador está em "êxtase" — bônus extra na simulação. */
    const val ECSTASY_THRESHOLD = 95

    /** Abaixo deste valor, jogador entra na zona "extremamente triste". */
    const val TRANSFER_REQUEST_THRESHOLD = 10

    /**
     * Chance (0-1) de pedir transferência por dia em que a moral está
     * abaixo de [TRANSFER_REQUEST_THRESHOLD]. Verificado pelo decay diário.
     */
    private const val TRANSFER_REQUEST_DAILY_PROB = 0.15

    // ── Modificadores de overall na simulação ───────────────────────────

    /** Modificador (em pontos de overall) aplicado conforme [Mood]. */
    private fun modifierForMood(mood: Mood): Int = when (mood) {
        Mood.SAD     -> -3
        Mood.NEUTRAL ->  0
        Mood.HAPPY   -> +2
    }

    // ── API pública: leitura ─────────────────────────────────────────────

    /**
     * Retorna o nível de moral atual do jogador (0-100).
     *
     * Se não há override ou o campo `mood` é null (primeira leitura), gera um
     * valor aleatório em [INITIAL_RANGE] e persiste no state, retornando esse
     * valor.
     */
    fun moodOf(state: GameState, playerId: String): Int {
        val override = state.playerOverrides[playerId]
        val current  = override?.mood
        if (current != null) return current

        val initial = INITIAL_RANGE.random(Random.Default)
        // Primeira leitura: inicializa sem entrada no histórico (não foi um "evento")
        setMoodInternal(state, playerId, initial, addToHistory = null)
        return initial
    }

    /** Estado [Mood] discreto (SAD/NEUTRAL/HAPPY). */
    fun moodStateOf(state: GameState, playerId: String): Mood =
        Mood.from(moodOf(state, playerId))

    /** Histórico de mudanças do jogador (mais recente primeiro). */
    fun historyOf(state: GameState, playerId: String): List<MoodEvent> =
        state.playerOverrides[playerId]?.moodHistory.orEmpty()

    /**
     * Modificador a aplicar ao overall do jogador na simulação.
     * Considera o estado discreto + bônus extra de êxtase.
     *
     * Exemplos:
     *  - Moral 20 (SAD): -3
     *  - Moral 50 (NEUTRAL): 0
     *  - Moral 80 (HAPPY): +2
     *  - Moral 96 (HAPPY + êxtase): +5
     */
    fun moodOverallModifier(state: GameState, playerId: String): Int {
        val value = moodOf(state, playerId)
        val base  = modifierForMood(Mood.from(value))
        val ecstasyBonus = if (value >= ECSTASY_THRESHOLD) 3 else 0
        return base + ecstasyBonus
    }

    /** True quando o jogador pediu transferência por insatisfação. */
    fun hasRequestedTransfer(state: GameState, playerId: String): Boolean =
        state.playerOverrides[playerId]?.transferRequestedOn != null

    // ── Mutação interna ─────────────────────────────────────────────────

    /**
     * Atualiza moral aplicando um delta, registrando o evento no histórico
     * e ativando triggers de eventos exclusivos (pedido de transferência).
     *
     * @param reason texto curto em PT-BR mostrado no dialog de histórico
     */
    private fun applyDeltaWithReason(
        state: GameState,
        playerId: String,
        delta: Int,
        reason: String
    ) {
        if (delta == 0) return
        val current  = moodOf(state, playerId)
        val newValue = (current + delta).coerceIn(MIN_MOOD, MAX_MOOD)
        val effectiveDelta = newValue - current  // pode ser menor que `delta` por causa do clamp
        if (effectiveDelta == 0) return

        val event = MoodEvent(
            date       = state.currentDate,
            reason     = reason,
            delta      = effectiveDelta,
            valueAfter = newValue
        )
        setMoodInternal(state, playerId, newValue, addToHistory = event)

        // Eventos exclusivos: se mergulhou abaixo do threshold, marca elegível para
        // pedir transferência (o sorteio acontece no decay diário).
        if (newValue <= TRANSFER_REQUEST_THRESHOLD && current > TRANSFER_REQUEST_THRESHOLD) {
            // Não pede AINDA — só fica elegível. O pedido em si vem do decay,
            // sorteando dia a dia se a moral continuar baixa.
        }
    }

    /**
     * Update centralizado: substitui o `mood` no override e opcionalmente
     * adiciona uma entrada ao histórico (mantendo no máximo HISTORY_MAX_ENTRIES).
     */
    private fun setMoodInternal(
        state: GameState,
        playerId: String,
        newValue: Int,
        addToHistory: MoodEvent?
    ) {
        val clamped  = newValue.coerceIn(MIN_MOOD, MAX_MOOD)
        val existing = state.playerOverrides[playerId] ?: PlayerOverride(playerId)
        val newHistory = if (addToHistory != null) {
            (listOf(addToHistory) + (existing.moodHistory ?: emptyList())).take(HISTORY_MAX_ENTRIES)
        } else {
            existing.moodHistory ?: emptyList()
        }
        state.playerOverrides[playerId] = existing.copy(
            mood        = clamped,
            moodHistory = newHistory
        )
    }

    // ── Eventos de partida ───────────────────────────────────────────────

    /**
     * Aplica o efeito de um resultado de mapa ao roster inteiro do time.
     * Também marca `lastPlayedDate` em cada titular para o decay temporal saber
     * que esses jogadores jogaram hoje.
     */
    fun recordMapResult(
        state: GameState,
        roster: List<Player>,
        won: Boolean,
        opponentName: String = ""
    ) {
        val delta  = if (won) DELTA_MAP_WIN else DELTA_MAP_LOSE
        val reason = if (opponentName.isNotBlank()) {
            if (won) "Vitória contra $opponentName" else "Derrota para $opponentName"
        } else {
            if (won) "Vitória de mapa" else "Derrota de mapa"
        }
        roster.forEach { player ->
            applyDeltaWithReason(state, player.id, delta, reason)
            markPlayedToday(state, player.id)
        }
    }

    /** Bônus de fim de série (BO3) ao roster inteiro do time. */
    fun recordSeriesResult(
        state: GameState,
        roster: List<Player>,
        won: Boolean,
        opponentName: String = ""
    ) {
        val delta = if (won) DELTA_SERIES_WIN_BONUS else DELTA_SERIES_LOSE_BONUS
        val reason = if (opponentName.isNotBlank()) {
            if (won) "Série vencida vs $opponentName" else "Série perdida vs $opponentName"
        } else {
            if (won) "Série vencida (BO3)" else "Série perdida (BO3)"
        }
        roster.forEach { applyDeltaWithReason(state, it.id, delta, reason) }
    }

    /** Marca que o jogador participou de partida hoje (reseta clock de decay). */
    private fun markPlayedToday(state: GameState, playerId: String) {
        val existing = state.playerOverrides[playerId] ?: PlayerOverride(playerId)
        state.playerOverrides[playerId] = existing.copy(lastPlayedDate = state.currentDate)
    }

    // ── Eventos individuais (mercado, banca) ────────────────────────────

    fun recordPlayerSold(state: GameState, playerId: String) {
        applyDeltaWithReason(state, playerId, DELTA_SOLD, "Vendido pelo clube")
    }

    fun recordPlayerHired(state: GameState, playerId: String) {
        applyDeltaWithReason(state, playerId, DELTA_HIRED, "Contratado pelo clube")
    }

    fun recordContractRenewed(state: GameState, playerId: String) {
        applyDeltaWithReason(state, playerId, DELTA_CONTRACT_RENEW, "Contrato renovado")
    }

    fun recordBecameReserve(state: GameState, playerId: String) {
        applyDeltaWithReason(state, playerId, DELTA_BECAME_RESERVE, "Rebaixado para reserva")
    }

    fun recordBecameStarter(state: GameState, playerId: String) {
        applyDeltaWithReason(state, playerId, DELTA_BECAME_STARTER, "Promovido a titular")
    }

    // ── Decay temporal ──────────────────────────────────────────────────

    /**
     * Resultado de uma rodada de decay diário, contendo eventos disparados
     * (jogadores que pediram transferência hoje).
     */
    data class DecayResult(
        val transferRequests: List<String>  // playerIds que pediram transferência
    )

    /**
     * Aplica o decay temporal para todos os jogadores de um roster.
     * Chamado uma vez por dia avançado (ex: pelo loop de avanço do tempo).
     *
     * Comportamento:
     *  - Para cada jogador, se `currentDate - lastPlayedDate > GRACE_DAYS`, move
     *    a moral 1 ponto em direção a 50 (NEUTRAL_VALUE).
     *  - Se a moral está abaixo de TRANSFER_REQUEST_THRESHOLD, sorteia se o
     *    jogador pede transferência neste dia.
     *
     * Retorna lista de jogadores que pediram transferência (para mostrar como
     * notificação no Hub, por exemplo).
     */
    fun applyDailyDecay(state: GameState, roster: List<Player>): DecayResult {
        val today = runCatching { LocalDate.parse(state.currentDate) }.getOrNull()
            ?: return DecayResult(emptyList())

        val transferRequests = mutableListOf<String>()

        roster.forEach { player ->
            val override = state.playerOverrides[player.id]
            val current  = override?.mood ?: return@forEach  // só decai quem já tem moral inicializada

            // ── Decay em direção ao neutro ──
            val lastPlayed = override.lastPlayedDate?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            }
            val daysIdle = if (lastPlayed != null) {
                ChronoUnit.DAYS.between(lastPlayed, today).toInt()
            } else {
                // Nunca jogou nesta carreira: usa start date como referência
                ChronoUnit.DAYS.between(LocalDate.parse(state.splitStartDate), today).toInt()
            }

            if (daysIdle > DECAY_GRACE_DAYS) {
                val direction = (NEUTRAL_VALUE - current).sign  // +1, 0 ou -1
                if (direction != 0) {
                    val drift = direction * DECAY_POINTS_PER_DAY
                    val newValue = (current + drift).coerceIn(MIN_MOOD, MAX_MOOD)
                    // Só registra histórico se a moral cruza um limiar (SAD↔NEUTRAL↔HAPPY)
                    // — evita inflar o histórico com 1 entrada/dia
                    val crossedThreshold = Mood.from(current) != Mood.from(newValue)
                    if (crossedThreshold) {
                        val event = MoodEvent(
                            date       = state.currentDate,
                            reason     = "Tempo sem jogar",
                            delta      = drift,
                            valueAfter = newValue
                        )
                        setMoodInternal(state, player.id, newValue, addToHistory = event)
                    } else {
                        setMoodInternal(state, player.id, newValue, addToHistory = null)
                    }
                }
            }

            // ── Eventos exclusivos: pedido de transferência ──
            val updatedMood = state.playerOverrides[player.id]?.mood ?: current
            if (updatedMood <= TRANSFER_REQUEST_THRESHOLD &&
                state.playerOverrides[player.id]?.transferRequestedOn == null
            ) {
                if (Random.nextDouble() < TRANSFER_REQUEST_DAILY_PROB) {
                    flagTransferRequest(state, player.id)
                    transferRequests += player.id
                }
            }
        }

        return DecayResult(transferRequests)
    }

    /**
     * Marca que o jogador pediu transferência hoje + adiciona ao histórico
     * para aparecer no dialog detalhado.
     */
    private fun flagTransferRequest(state: GameState, playerId: String) {
        val existing = state.playerOverrides[playerId] ?: return
        val event = MoodEvent(
            date       = state.currentDate,
            reason     = "Pediu transferência",
            delta      = 0,
            valueAfter = existing.mood ?: 0
        )
        state.playerOverrides[playerId] = existing.copy(
            transferRequestedOn = state.currentDate,
            moodHistory         = (listOf(event) + (existing.moodHistory ?: emptyList())).take(HISTORY_MAX_ENTRIES)
        )
    }

    /** Limpa o pedido de transferência (ex: após renovação ou venda). */
    fun clearTransferRequest(state: GameState, playerId: String) {
        val existing = state.playerOverrides[playerId] ?: return
        state.playerOverrides[playerId] = existing.copy(transferRequestedOn = null)
    }
}
