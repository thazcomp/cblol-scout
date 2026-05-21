package com.cblol.scout.domain.usecase

import com.cblol.scout.data.GameState
import com.cblol.scout.data.TransferWindow
import com.cblol.scout.data.TransferWindowKind
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Serviço de **janelas de transferência**.
 *
 * O mercado de transferências (compra/venda de jogadores) só fica aberto
 * durante janelas específicas do calendário, espelhando o funcionamento real
 * de ligas de e-sports:
 *
 *  - **Pré-temporada** ([TransferWindowKind.PRE_SEASON]): vai do início do jogo
 *    (uma semana antes do split) até a véspera da primeira partida. É a janela
 *    em que o jogador monta o elenco antes do split começar. **O jogo sempre
 *    começa aqui.**
 *
 *  - **Inter-temporada** ([TransferWindowKind.MID_SEASON]): uma pausa curta no
 *    meio do split (entre o turno e o returno do round-robin duplo), em que o
 *    técnico pode fazer ajustes pontuais no elenco.
 *
 * Fora dessas janelas o mercado fica **fechado** — tentativas de compra/venda
 * são bloqueadas pelo [com.cblol.scout.game.TransferMarket].
 *
 * **Design das datas** (calculadas a partir do split):
 *  - O split tem 14 rodadas a cada 4 dias (~56 dias). A rodada 8 (returno)
 *    começa em `splitStart + 28 dias`.
 *  - Pré-temporada: `[gameStart, splitStart)` — i.e. termina 1 dia antes do
 *    primeiro jogo.
 *  - Inter-temporada: janela de [MID_SEASON_DURATION_DAYS] dias terminando na
 *    véspera do returno (rodada 8), centrada na pausa do meio.
 *
 * As datas são persistidas no [GameState] na criação da carreira, então o
 * serviço apenas as lê — não recalcula em runtime (evita divergência se as
 * regras mudarem entre versões).
 *
 * **SOLID:**
 *  - **SRP**: só decide *quando* o mercado está aberto. Não move jogadores nem
 *    altera orçamento (isso é do TransferMarket).
 *  - **OCP**: novo tipo de janela = novo valor em [TransferWindowKind] +
 *    geração em [buildWindowsForSplit]. O resto (status, dias restantes) é
 *    genérico sobre a lista de janelas.
 *  - **DIP**: JVM-puro, depende só de [GameState] e modelos de dados.
 */
object TransferWindowService {

    /** Duração (em dias) da janela de inter-temporada. */
    const val MID_SEASON_DURATION_DAYS = 6L

    /**
     * Dias após o `splitStart` em que o returno (rodada 8 de 14) começa.
     * 7 rodadas × 4 dias = 28. A janela inter-temporada termina na véspera.
     */
    private const val SECOND_HALF_OFFSET_DAYS = 28L

    /**
     * Constrói a lista de janelas de transferência de um split, dado o início
     * do jogo (`gameStart`, ~1 semana antes) e o início do split (`splitStart`).
     *
     * Chamado uma vez na criação da carreira; o resultado é salvo no GameState.
     */
    fun buildWindowsForSplit(gameStart: String, splitStart: String): List<TransferWindow> {
        val start = LocalDate.parse(gameStart)
        val split = LocalDate.parse(splitStart)

        // Pré-temporada: do início do jogo até a véspera do split.
        val preSeason = TransferWindow(
            kind = TransferWindowKind.PRE_SEASON,
            startDate = start.toString(),
            endDate = split.minusDays(1).toString()
        )

        // Inter-temporada: janela curta terminando na véspera do returno.
        val secondHalfStart = split.plusDays(SECOND_HALF_OFFSET_DAYS)
        val midEnd = secondHalfStart.minusDays(1)
        val midStart = midEnd.minusDays(MID_SEASON_DURATION_DAYS - 1)
        val midSeason = TransferWindow(
            kind = TransferWindowKind.MID_SEASON,
            startDate = midStart.toString(),
            endDate = midEnd.toString()
        )

        return listOf(preSeason, midSeason)
    }

    /**
     * Janela atualmente aberta na data corrente do [state], ou null se o
     * mercado está fechado.
     */
    fun currentWindow(state: GameState): TransferWindow? =
        windowAt(state, state.currentDate)

    /** True se o mercado está aberto (há uma janela ativa) na data corrente. */
    fun isMarketOpen(state: GameState): Boolean = currentWindow(state) != null

    /**
     * Próxima janela que ainda vai abrir (após a data corrente), ou null se não
     * há nenhuma janela futura no calendário atual.
     */
    fun nextWindow(state: GameState): TransferWindow? {
        val today = parseOrNull(state.currentDate) ?: return null
        return state.transferWindows
            .filter { window ->
                val s = parseOrNull(window.startDate)
                s != null && s.isAfter(today)
            }
            .minByOrNull { parseOrNull(it.startDate) ?: LocalDate.MAX }
    }

    /**
     * Dias restantes até o fechamento da janela aberta (inclusive o dia atual).
     * Retorna null se o mercado está fechado.
     */
    fun daysUntilWindowCloses(state: GameState): Long? {
        val window = currentWindow(state) ?: return null
        val today = parseOrNull(state.currentDate) ?: return null
        val end = parseOrNull(window.endDate) ?: return null
        return ChronoUnit.DAYS.between(today, end).coerceAtLeast(0)
    }

    /**
     * Dias até a próxima janela abrir (a partir da data corrente). Retorna null
     * se não há janela futura.
     */
    fun daysUntilNextWindow(state: GameState): Long? {
        val next = nextWindow(state) ?: return null
        val today = parseOrNull(state.currentDate) ?: return null
        val start = parseOrNull(next.startDate) ?: return null
        return ChronoUnit.DAYS.between(today, start).coerceAtLeast(0)
    }

    /**
     * Mensagem amigável (PT-BR) sobre o estado do mercado, para exibir no Hub
     * e no Mercado. Ex.:
     *  - "Mercado ABERTO · Pré-temporada · fecha em 5 dias"
     *  - "Mercado FECHADO · abre na inter-temporada em 12 dias"
     *  - "Mercado FECHADO · sem novas janelas neste split"
     */
    fun statusMessage(state: GameState): String {
        val current = currentWindow(state)
        if (current != null) {
            val days = daysUntilWindowCloses(state) ?: 0
            val closeText = when (days) {
                0L -> "fecha hoje"
                1L -> "fecha amanhã"
                else -> "fecha em $days dias"
            }
            return "Mercado ABERTO · ${current.kind.label} · $closeText"
        }

        val next = nextWindow(state)
        if (next != null) {
            val days = daysUntilNextWindow(state) ?: 0
            val openText = when (days) {
                0L -> "abre hoje"
                1L -> "abre amanhã"
                else -> "abre em $days dias"
            }
            return "Mercado FECHADO · ${next.kind.label} $openText"
        }

        return "Mercado FECHADO · sem novas janelas neste split"
    }

    private fun parseOrNull(iso: String?): LocalDate? =
        iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /**
     * Resultado da detecção de transição de janela entre duas datas.
     */
    enum class WindowTransition { OPENED, CLOSED, NONE }

    /**
     * Compara o estado do mercado em [previousDate] e [currentDate] e diz se
     * houve abertura, fechamento ou nenhuma mudança. Centraliza a regra para
     * que [com.cblol.scout.game.GameEngine] e a UI de simulação não dupliquem
     * a lógica de transição.
     *
     * @return par (transição, janela-atual-se-abriu). A janela vem preenchida
     *   apenas quando a transição é [WindowTransition.OPENED], para o chamador
     *   poder mostrar o nome ("Pré-temporada", "Inter-temporada").
     */
    fun detectTransition(
        state: GameState,
        previousDate: String,
        currentDate: String
    ): Pair<WindowTransition, TransferWindow?> {
        val wasOpen = isOpenAt(state, previousDate)
        val nowWindow = windowAt(state, currentDate)
        val isOpen = nowWindow != null
        return when {
            !wasOpen && isOpen -> WindowTransition.OPENED to nowWindow
            wasOpen && !isOpen -> WindowTransition.CLOSED to null
            else               -> WindowTransition.NONE to null
        }
    }

    /** Janela ativa numa data específica (helper interno reutilizável). */
    private fun windowAt(state: GameState, dateIso: String): TransferWindow? {
        val day = parseOrNull(dateIso) ?: return null
        return state.transferWindows.firstOrNull { window ->
            val s = parseOrNull(window.startDate)
            val e = parseOrNull(window.endDate)
            s != null && e != null && !day.isBefore(s) && !day.isAfter(e)
        }
    }

    private fun isOpenAt(state: GameState, dateIso: String): Boolean =
        windowAt(state, dateIso) != null
}
