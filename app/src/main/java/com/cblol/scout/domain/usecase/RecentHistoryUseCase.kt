package com.cblol.scout.domain.usecase

import android.content.Context
import com.cblol.scout.data.BondEvent
import com.cblol.scout.data.GameState
import com.cblol.scout.data.LogEntry
import com.cblol.scout.data.MoodEvent
import com.cblol.scout.data.NewsItem
import com.cblol.scout.data.TrainingSession
import com.cblol.scout.game.GameRepository
import java.time.LocalDate

/**
 * **Histórico recente** unificado de TODOS os subsistemas da carreira.
 *
 * Diferente do [CalendarEventsAggregator] que projeta o futuro (o que VAI
 * acontecer), este agregador olha o passado: o que já aconteceu na carreira,
 * reunido de várias fontes e ordenado cronologicamente.
 *
 * **Fontes agregadas:**
 *  - [GameState.gameLog]            → fonte principal (motor cobre 90% dos
 *                                     eventos: partidas, transferências,
 *                                     patrocínios, banco, scouting, academia)
 *  - [GameState.trainingHistory]    → sessões de treino com outcome detalhado
 *  - [GameState.news]               → manchetes do feed editorial
 *  - [PlayerOverride.moodHistory]   → mood events de todos os jogadores do elenco
 *  - [PlayerBond.history]           → bond events de todos os pares com laço
 *
 * **Por que múltiplas fontes em vez de só o gameLog?** Algumas categorias
 * (mood, bond, training) têm campos estruturados extras (delta numérico,
 * outcome enum) que valem a pena preservar para a UI exibir cores/sinais. O
 * gameLog é texto cru, sem essa estrutura. Para esses casos vamos direto na
 * fonte primária.
 *
 * **Ordenação:** mais recente primeiro. Sem limite — a UI usa scroll.
 *
 * **SOLID:**
 *  - **SRP**: agrega; não joga, não simula, não persiste.
 *  - **OCP**: novos subsistemas entram como nova subclasse de
 *    [RecentHistoryEvent] + novo `collect*` no [invoke].
 *  - **DIP**: depende de [GameRepository] + Context para resolver nomes.
 */
class RecentHistoryAggregator(private val context: Context) {

    /**
     * Constrói o histórico completo da carreira. Não-bloqueante, leve mesmo
     * com centenas de eventos.
     *
     * @param categories filtro opcional — quando passado, só inclui eventos
     *   dessas categorias. Quando vazio ou null, devolve tudo.
     */
    operator fun invoke(
        categories: Set<RecentHistoryCategory>? = null
    ): List<RecentHistoryEvent> {
        val gs = GameRepository.current()
        val events = mutableListOf<RecentHistoryEvent>()

        collectFromGameLog(gs, events)
        collectFromTraining(gs.trainingHistory, events)
        collectFromNews(gs.news, events)
        collectFromMood(gs, events)
        collectFromBonds(gs, events)

        val filtered = if (categories.isNullOrEmpty()) events
                       else events.filter { it.category in categories }

        // Ordena por data desc; usa o índice original como desempate para
        // manter estabilidade quando há vários eventos no mesmo dia.
        return filtered
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<RecentHistoryEvent>> { it.value.date }
                    .thenByDescending { it.index }
            )
            .map { it.value }
    }

    // ── Coleta por fonte ────────────────────────────────────────────────

    /**
     * Lê o [GameState.gameLog] e mapeia cada entrada para uma categoria via
     * [categoryFromLogType]. Isto cobre quase tudo: SPONSOR, BANK, ACADEMY,
     * TRANSFER, SCOUTING, OFF_MATCH, MATCH, etc., porque o motor escreve no
     * log em cada momento relevante.
     *
     * Eventos com tipo desconhecido vão para [RecentHistoryCategory.SYSTEM]
     * para não ficarem invisíveis.
     */
    private fun collectFromGameLog(gs: GameState, out: MutableList<RecentHistoryEvent>) {
        gs.gameLog.forEach { entry ->
            out += RecentHistoryEvent.LogBased(
                date = entry.date,
                category = categoryFromLogType(entry.type),
                title = entry.message,
                subtitle = null,
                rawType = entry.type
            )
        }
    }

    /**
     * [TrainingSession] tem outcome enum + custo — vale virar uma subclasse
     * própria para a UI mostrar o emoji do outcome (✨/✅/🟡/⚠️/💥) em vez
     * do texto cru do gameLog.
     */
    private fun collectFromTraining(
        sessions: List<TrainingSession>?,
        out: MutableList<RecentHistoryEvent>
    ) {
        sessions?.forEach { s ->
            out += RecentHistoryEvent.Training(
                date = s.date,
                trainingType = s.type.label,
                trainingEmoji = s.type.emoji,
                outcomeLabel = s.outcome.label,
                outcomeEmoji = s.outcome.emoji,
                summary = s.summary,
                cost = s.cost
            )
        }
    }

    private fun collectFromNews(news: List<NewsItem>?, out: MutableList<RecentHistoryEvent>) {
        news?.forEach { n ->
            out += RecentHistoryEvent.News(
                date = n.date,
                source = n.source,
                sourceEmoji = n.sourceEmoji,
                headline = n.headline,
                body = n.body
            )
        }
    }

    /**
     * Itera todos os overrides e extrai os [MoodEvent]s. Como os events não
     * carregam o playerId, resolvemos o nome aqui via roster do gerente.
     *
     * Lê o roster ATUAL do gerente como referência para os nomes — jogadores
     * vendidos ainda podem ter entradas mas o nome não estará disponível;
     * nesse caso usamos o playerId como fallback (raro na prática porque o
     * histórico do override sai junto quando o jogador deixa o elenco).
     */
    private fun collectFromMood(gs: GameState, out: MutableList<RecentHistoryEvent>) {
        val roster = runCatching {
            GameRepository.rosterOf(context, gs.managerTeamId)
        }.getOrDefault(emptyList())
        val namesById = roster.associate { it.id to it.nome_jogo }

        gs.playerOverrides.forEach { (playerId, override) ->
            val playerName = namesById[playerId] ?: playerId
            override.moodHistory.forEach { ev ->
                out += RecentHistoryEvent.Mood(
                    date = ev.date,
                    playerName = playerName,
                    reason = ev.reason,
                    delta = ev.delta,
                    valueAfter = ev.valueAfter
                )
            }
        }
    }

    /**
     * Itera todos os laços e extrai os [BondEvent]s. Resolve os nomes dos
     * dois jogadores envolvidos a partir do par de ids do bond key.
     */
    private fun collectFromBonds(gs: GameState, out: MutableList<RecentHistoryEvent>) {
        val bonds = gs.playerBonds ?: return
        val roster = runCatching {
            GameRepository.rosterOf(context, gs.managerTeamId)
        }.getOrDefault(emptyList())
        val namesById = roster.associate { it.id to it.nome_jogo }

        bonds.values.forEach { bond ->
            val nameA = namesById[bond.playerAId] ?: bond.playerAId
            val nameB = namesById[bond.playerBId] ?: bond.playerBId
            bond.history.forEach { ev ->
                out += RecentHistoryEvent.Bond(
                    date = ev.date,
                    playerAName = nameA,
                    playerBName = nameB,
                    reason = ev.reason,
                    delta = ev.delta,
                    levelAfter = ev.levelAfter
                )
            }
        }
    }

    // ── Resolução de categoria do gameLog ──────────────────────────────

    /**
     * Mapeia o `type` cru do [LogEntry] (string técnica do motor) para a
     * categoria visual exibida na UI. Caso o tipo seja desconhecido (motor
     * adicionou um novo tipo e ainda não atualizamos esta tabela), cai para
     * [RecentHistoryCategory.SYSTEM] — fica visível mas sem cor especial.
     *
     * **SOLID/OCP:** adicionar nova categoria = adicionar caso aqui + novo
     * elemento no enum [RecentHistoryCategory]. Não mexe no resto.
     */
    private fun categoryFromLogType(rawType: String): RecentHistoryCategory =
        when (rawType.uppercase()) {
            "SPONSOR"               -> RecentHistoryCategory.SPONSOR
            "BANK"                  -> RecentHistoryCategory.FINANCE
            "PAYROLL"               -> RecentHistoryCategory.FINANCE
            "FINANCE"               -> RecentHistoryCategory.FINANCE
            "ACADEMY"               -> RecentHistoryCategory.ACADEMY
            "TRANSFER", "MARKET"    -> RecentHistoryCategory.TRANSFER
            "SCOUT", "SCOUTING"     -> RecentHistoryCategory.SCOUTING
            "OFF_MATCH", "OFFMATCH" -> RecentHistoryCategory.OFF_MATCH
            "MATCH", "SERIES"       -> RecentHistoryCategory.MATCH
            "CONTRACT"              -> RecentHistoryCategory.TRANSFER
            "TRAINING"              -> RecentHistoryCategory.TRAINING
            "NEWS"                  -> RecentHistoryCategory.NEWS
            "MOOD", "MORALE"        -> RecentHistoryCategory.MOOD
            "BOND", "CHEMISTRY"     -> RecentHistoryCategory.BOND
            "COACH"                 -> RecentHistoryCategory.COACH
            else                    -> RecentHistoryCategory.SYSTEM
        }
}

/**
 * Categoria visual de um [RecentHistoryEvent]. Cada uma tem emoji, cor e
 * label para chip de filtro.
 *
 * O usuário escolhe quais categorias ver no dialog via chips. Por padrão
 * todas estão ligadas.
 */
enum class RecentHistoryCategory(val emoji: String, val label: String) {
    MATCH    ("🏆", "Partidas"),
    TRANSFER ("🔄", "Mercado"),
    SPONSOR  ("💼", "Patrocínio"),
    FINANCE  ("💰", "Finanças"),
    ACADEMY  ("🌱", "Base"),
    SCOUTING ("🔍", "Olheiros"),
    TRAINING ("🏋️", "Treinos"),
    OFF_MATCH("📰", "Fora de jogo"),
    MOOD     ("🎭", "Moral"),
    BOND     ("🤝", "Química"),
    NEWS     ("📢", "Notícias"),
    COACH    ("🎓", "Técnico"),
    SYSTEM   ("⚙️", "Sistema")
}

/**
 * Um evento do histórico recente. Subclasses carregam estrutura específica
 * (delta de moral, outcome de treino, fonte de notícia) para a UI exibir cores
 * e ícones apropriados — o agregador não jogou esses dados fora.
 *
 * **Contrato comum** ([date], [category], [title], [subtitle]): garante que
 * a UI consegue renderizar QUALQUER subclasse com um único layout de linha,
 * caindo nos campos extras só quando souber o tipo concreto.
 *
 * **SOLID/OCP:** novos tipos viram nova `data class` aqui — UI continua
 * funcionando via `title`/`subtitle`, e pode ser estendida via `when` para
 * tratamento especial.
 */
sealed class RecentHistoryEvent {
    abstract val date: String
    abstract val category: RecentHistoryCategory
    abstract val title: String
    abstract val subtitle: String?

    /**
     * Evento extraído do [GameState.gameLog]. Cobre 90% dos eventos do motor —
     * as outras subclasses são especializações para fontes com mais estrutura.
     *
     * @property rawType o tipo cru do [LogEntry] (preservado para debug e
     *   para casos em que a UI queira ramificar por sub-tipo)
     */
    data class LogBased(
        override val date: String,
        override val category: RecentHistoryCategory,
        override val title: String,
        override val subtitle: String?,
        val rawType: String
    ) : RecentHistoryEvent()

    /**
     * Sessão de treino. Carrega outcome enum para a UI mostrar o emoji
     * correto (✨/✅/🟡/⚠️/💥) e a faixa colorida apropriada.
     */
    data class Training(
        override val date: String,
        val trainingType: String,
        val trainingEmoji: String,
        val outcomeLabel: String,
        val outcomeEmoji: String,
        val summary: String,
        val cost: Long
    ) : RecentHistoryEvent() {
        override val category = RecentHistoryCategory.TRAINING
        override val title = "$trainingEmoji $trainingType · $outcomeEmoji $outcomeLabel"
        override val subtitle: String? = summary
    }

    /** Manchete do feed editorial. Inclui fonte para a UI mostrar como crédito. */
    data class News(
        override val date: String,
        val source: String,
        val sourceEmoji: String,
        val headline: String,
        val body: String
    ) : RecentHistoryEvent() {
        override val category = RecentHistoryCategory.NEWS
        override val title = headline
        override val subtitle: String? = "$sourceEmoji $source · $body"
    }

    /**
     * Mudança de moral de um jogador. Carrega delta numérico para a UI pintar
     * de verde (positivo) / vermelho (negativo) / amarelo (zero = aviso).
     */
    data class Mood(
        override val date: String,
        val playerName: String,
        val reason: String,
        val delta: Int,
        val valueAfter: Int
    ) : RecentHistoryEvent() {
        override val category = RecentHistoryCategory.MOOD
        override val title = "$playerName · $reason"
        override val subtitle: String? = buildString {
            when {
                delta > 0 -> append("+$delta moral")
                delta < 0 -> append("$delta moral")
                else      -> append("Aviso")
            }
            append(" · agora $valueAfter/100")
        }
    }

    /**
     * Mudança de laço entre dois jogadores. Delta positivo = aproximação;
     * negativo = atrito. A UI pinta usando a mesma lógica de delta de Mood.
     */
    data class Bond(
        override val date: String,
        val playerAName: String,
        val playerBName: String,
        val reason: String,
        val delta: Int,
        val levelAfter: Int
    ) : RecentHistoryEvent() {
        override val category = RecentHistoryCategory.BOND
        override val title = "$playerAName ↔ $playerBName · $reason"
        override val subtitle: String? = buildString {
            when {
                delta > 0 -> append("+$delta laço")
                delta < 0 -> append("$delta laço")
                else      -> append("Sem mudança")
            }
            append(" · nível $levelAfter")
        }
    }
}
