package com.cblol.scout.domain.usecase

import com.cblol.scout.data.GameState
import com.cblol.scout.data.OffMatchEvent
import com.cblol.scout.data.OffMatchEventCategory
import com.cblol.scout.data.OffMatchEventSentiment
import com.cblol.scout.data.Player
import com.cblol.scout.data.PlayerOverride
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/**
 * Sistema de eventos fora de jogo (between BO3s).
 *
 * Gera ocasionalmente narrativas que afetam a moral ou o overall temporário
 * de jogadores específicos ou do time inteiro. Ex: entrevista mal recebida,
 * lesão num dedo, família vai assistir ao jogo, polêmica nas redes sociais.
 *
 * **Como funciona:**
 *  - [maybeGenerateEvent] é chamado após o fim de cada BO3.
 *  - Sorteia probabilisticamente se um evento vai acontecer ([EVENT_CHANCE]).
 *  - Se sim, escolhe uma categoria por peso (alguns eventos são mais comuns).
 *  - Constrói o evento a partir de um catálogo de templates narrativos.
 *  - Aplica os efeitos imediatos (moral via [MoraleService], overall temporário).
 *  - Salva no [GameState.pendingOffMatchEvent] para a UI exibir.
 *
 * **SOLID:**
 *  - **SRP**: cada categoria tem seu método `build*` privado.
 *  - **OCP**: adicionar nova categoria é só estender o enum + criar `buildX` +
 *    registrar no [CATEGORY_WEIGHTS] e no `switch` do [pickAndBuild].
 *  - **DIP**: depende apenas de [GameState], [Player], [MoraleService]. Sem
 *    Android no caminho — testável em JVM pura.
 */
object OffMatchEventService {

    // ── Probabilidades de gatilho ───────────────────────────────────────

    /** Chance de qualquer evento acontecer entre duas BO3s (0..1). */
    private const val EVENT_CHANCE = 0.75

    /**
     * Pesos relativos de cada categoria — a soma não precisa ser 100, apenas
     * proporcional. Categorias mais comuns têm peso maior.
     *
     * Tragédia pessoal e escândalos são raros para não ser um show de horrores
     * a cada partida.
     */
    private val CATEGORY_WEIGHTS = mapOf(
        OffMatchEventCategory.INTERVIEW             to 18,
        OffMatchEventCategory.TRAINING_BREAKTHROUGH to 15,
        OffMatchEventCategory.FAMILY_SUPPORT        to 14,
        OffMatchEventCategory.FAN_SUPPORT           to 12,
        OffMatchEventCategory.TEAM_COMBO            to 12,
        OffMatchEventCategory.SPONSOR_VISIT         to 10,
        OffMatchEventCategory.INJURY                to 10,
        OffMatchEventCategory.TEAM_FIGHT            to 8,
        OffMatchEventCategory.RELATIONSHIP_START    to 7,
        OffMatchEventCategory.RELATIONSHIP_END      to 6,
        OffMatchEventCategory.SCANDAL               to 5,
        OffMatchEventCategory.PERSONAL_TRAGEDY      to 3
    )

    // ── API pública ──────────────────────────────────────────────────────

    /**
     * Tenta gerar um evento entre BO3s. Retorna o evento criado e o salva em
     * [GameState.pendingOffMatchEvent] se gerou, ou retorna null se a chance
     * não foi sorteada nesta rodada.
     *
     * Os efeitos (moral, modificador temporário) já são aplicados aqui — a UI
     * apenas exibe a narrativa. Isso garante que mesmo se o usuário fechar o
     * app antes de ver, o efeito já está no save.
     *
     * @param roster jogadores do time do usuário (usado para escolher alvo)
     */
    fun maybeGenerateEvent(state: GameState, roster: List<Player>): OffMatchEvent? {
        if (roster.isEmpty()) return null
        if (Random.nextDouble() >= EVENT_CHANCE) return null

        val category = weightedRandom(CATEGORY_WEIGHTS)
        val event    = pickAndBuild(category, state, roster) ?: return null

        applyEffects(state, event, roster)
        state.pendingOffMatchEvent = event
        return event
    }

    /** Limpa o evento pendente (chamado pela Activity após o usuário ver). */
    fun consumePending(state: GameState) {
        state.pendingOffMatchEvent = null
    }

    /**
     * Retorna o modificador de overall ativo de um jogador (já considerando
     * expiração). Chamado pelo [LiveMatchEngine] ao calcular force teams.
     *
     * Se o modificador já expirou pela data corrente, retorna 0 (mas o save
     * não é mexido — limpeza preguiçosa no próximo evento).
     */
    fun activeOverallModifierFor(state: GameState, playerId: String): Int {
        val override = state.playerOverrides[playerId] ?: return 0
        val modifier = override.overallModifier
        if (modifier == 0) return 0

        val expires = override.overallModifierExpiresOn ?: return 0
        val today   = runCatching { LocalDate.parse(state.currentDate) }.getOrNull()
            ?: return modifier  // Se não consegue parsear, mantém o modificador (fail-safe)
        val expiresDate = runCatching { LocalDate.parse(expires) }.getOrNull()
            ?: return modifier
        return if (today.isAfter(expiresDate)) 0 else modifier
    }

    // ── Aplicação dos efeitos ───────────────────────────────────────────

    private fun applyEffects(state: GameState, event: OffMatchEvent, roster: List<Player>) {
        // Quais jogadores são afetados
        val targets: List<Player> = event.targetPlayerId?.let { tid ->
            roster.filter { it.id == tid }
        } ?: roster

        // Efeito de moral via MoraleService (atualiza histórico do jogador também)
        if (event.moodDelta != 0) {
            val reason = "${event.category.emoji} ${event.title}"
            targets.forEach { player ->
                applyMoodViaMoraleService(state, player.id, event.moodDelta, reason)
            }
        }

        // Modificador temporário de overall
        if (event.overallModifierDelta != 0 && event.durationDays > 0) {
            val expires = runCatching {
                LocalDate.parse(state.currentDate).plusDays(event.durationDays.toLong()).toString()
            }.getOrDefault(state.currentDate)
            targets.forEach { player ->
                val existing = state.playerOverrides[player.id] ?: PlayerOverride(player.id)
                state.playerOverrides[player.id] = existing.copy(
                    overallModifier          = event.overallModifierDelta,
                    overallModifierExpiresOn = expires,
                    overallModifierReason    = event.title
                )
            }
        }
    }

    /**
     * Wrapper que delega ao MoraleService sem expor diretamente a função
     * privada `applyDelta`. Como `applyDelta` é privado, usamos os helpers
     * públicos `record*` quando o evento se enquadra, ou implementamos via
     * leitura+escrita aqui.
     *
     * Para simplicidade, usamos o que já existe: lê o mood atual, calcula o
     * novo valor com clamp, escreve via setMood + adiciona ao histórico.
     * Como o MoraleService não expõe um método genérico, copiamos a lógica
     * para evitar tornar o método interno público.
     */
    private fun applyMoodViaMoraleService(
        state: GameState,
        playerId: String,
        delta: Int,
        reason: String
    ) {
        val current  = MoraleService.moodOf(state, playerId)
        val newValue = (current + delta).coerceIn(0, 100)
        val effective = newValue - current
        if (effective == 0) return

        val moodEvent = com.cblol.scout.data.MoodEvent(
            date       = state.currentDate,
            reason     = reason,
            delta      = effective,
            valueAfter = newValue
        )
        val existing = state.playerOverrides[playerId] ?: PlayerOverride(playerId)
        val newHistory = (listOf(moodEvent) + existing.moodHistory)
            .take(MoraleService.HISTORY_MAX_ENTRIES)
        state.playerOverrides[playerId] = existing.copy(
            mood        = newValue,
            moodHistory = newHistory
        )
    }

    // ── Seleção e construção ────────────────────────────────────────────

    /** Sorteia uma chave pelos pesos do mapa. */
    private fun <T> weightedRandom(weights: Map<T, Int>): T {
        val total = weights.values.sum()
        var roll  = Random.nextInt(total)
        for ((key, w) in weights) {
            roll -= w
            if (roll < 0) return key
        }
        return weights.keys.first()  // unreachable
    }

    private fun pickAndBuild(
        category: OffMatchEventCategory,
        state: GameState,
        roster: List<Player>
    ): OffMatchEvent? = when (category) {
        OffMatchEventCategory.INTERVIEW             -> buildInterview(state, roster)
        OffMatchEventCategory.INJURY                -> buildInjury(state, roster)
        OffMatchEventCategory.RELATIONSHIP_START    -> buildRelationshipStart(state, roster)
        OffMatchEventCategory.RELATIONSHIP_END      -> buildRelationshipEnd(state, roster)
        OffMatchEventCategory.FAMILY_SUPPORT        -> buildFamilySupport(state, roster)
        OffMatchEventCategory.SCANDAL               -> buildScandal(state, roster)
        OffMatchEventCategory.TRAINING_BREAKTHROUGH -> buildTrainingBreakthrough(state, roster)
        OffMatchEventCategory.SPONSOR_VISIT         -> buildSponsorVisit(state, roster)
        OffMatchEventCategory.FAN_SUPPORT           -> buildFanSupport(state, roster)
        OffMatchEventCategory.PERSONAL_TRAGEDY      -> buildPersonalTragedy(state, roster)
        OffMatchEventCategory.TEAM_COMBO            -> buildTeamCombo(state, roster)
        OffMatchEventCategory.TEAM_FIGHT            -> buildTeamFight(state, roster)
    }

    // ── Templates: ENTREVISTA ───────────────────────────────────────────

    private fun buildInterview(state: GameState, roster: List<Player>): OffMatchEvent {
        val player = roster.random()
        val positive = Random.nextDouble() < 0.55

        val (title, desc, mood) = if (positive) {
            Triple(
                "Entrevista marcante",
                "${player.nome_jogo} deu uma entrevista impecável depois do treino — falou sobre " +
                "estratégia, demonstrou maturidade e arrancou elogios da imprensa. A repercussão foi " +
                "ótima nas redes e o time toda ganhou visibilidade positiva.",
                8
            )
        } else {
            Triple(
                "Entrevista mal recebida",
                "${player.nome_jogo} se confundiu durante a entrevista pós-treino e fez declarações " +
                "controversas sobre o time rival. A repercussão foi negativa, com torcedores e analistas " +
                "criticando o tom da fala. A pressão aumentou.",
                -8
            )
        }

        return OffMatchEvent(
            id           = UUID.randomUUID().toString(),
            date         = state.currentDate,
            category     = OffMatchEventCategory.INTERVIEW,
            sentiment    = if (positive) OffMatchEventSentiment.POSITIVE else OffMatchEventSentiment.NEGATIVE,
            title        = title,
            description  = desc,
            targetPlayerId   = player.id,
            targetPlayerName = player.nome_jogo,
            moodDelta    = mood
        )
    }

    // ── Templates: LESÃO ────────────────────────────────────────────────

    private fun buildInjury(state: GameState, roster: List<Player>): OffMatchEvent {
        val player = roster.random()
        val parts = listOf("dedo", "punho", "pulso", "cervical", "ombro", "costas")
        val part  = parts.random()

        return OffMatchEvent(
            id           = UUID.randomUUID().toString(),
            date         = state.currentDate,
            category     = OffMatchEventCategory.INJURY,
            sentiment    = OffMatchEventSentiment.NEGATIVE,
            title        = "Lesão no $part",
            description  = "${player.nome_jogo} sentiu um desconforto no $part durante o treino de hoje. " +
                "O fisioterapeuta confirmou uma lesão leve — vai jogar com dor pelos próximos dias " +
                "até a recuperação completa.",
            targetPlayerId        = player.id,
            targetPlayerName      = player.nome_jogo,
            moodDelta             = -5,
            overallModifierDelta  = -4,
            durationDays          = 7
        )
    }

    // ── Templates: RELACIONAMENTOS ──────────────────────────────────────

    private fun buildRelationshipStart(state: GameState, roster: List<Player>): OffMatchEvent {
        val player = roster.random()
        return OffMatchEvent(
            id           = UUID.randomUUID().toString(),
            date         = state.currentDate,
            category     = OffMatchEventCategory.RELATIONSHIP_START,
            sentiment    = OffMatchEventSentiment.POSITIVE,
            title        = "Novo relacionamento",
            description  = "${player.nome_jogo} começou um relacionamento e chegou ao treino sorrindo o " +
                "dia inteiro. O resto do time tirou sarro o tempo todo, mas ele está claramente mais " +
                "motivado e focado nas próximas partidas.",
            targetPlayerId        = player.id,
            targetPlayerName      = player.nome_jogo,
            moodDelta             = 12,
            overallModifierDelta  = 2,
            durationDays          = 14
        )
    }

    private fun buildRelationshipEnd(state: GameState, roster: List<Player>): OffMatchEvent {
        val player = roster.random()
        return OffMatchEvent(
            id           = UUID.randomUUID().toString(),
            date         = state.currentDate,
            category     = OffMatchEventCategory.RELATIONSHIP_END,
            sentiment    = OffMatchEventSentiment.NEGATIVE,
            title        = "Término de relacionamento",
            description  = "${player.nome_jogo} terminou um relacionamento sério essa semana e está " +
                "claramente abalado. Chegou atrasado ao treino e teve dificuldade de manter o foco. " +
                "Vai precisar de tempo para se recompor.",
            targetPlayerId        = player.id,
            targetPlayerName      = player.nome_jogo,
            moodDelta             = -15,
            overallModifierDelta  = -3,
            durationDays          = 10
        )
    }

    // ── Templates: FAMÍLIA ──────────────────────────────────────────────

    private fun buildFamilySupport(state: GameState, roster: List<Player>): OffMatchEvent {
        val player = roster.random()
        return OffMatchEvent(
            id           = UUID.randomUUID().toString(),
            date         = state.currentDate,
            category     = OffMatchEventCategory.FAMILY_SUPPORT,
            sentiment    = OffMatchEventSentiment.POSITIVE,
            title        = "Família virá assistir",
            description  = "A família de ${player.nome_jogo} confirmou presença na arena para a " +
                "próxima partida. É a primeira vez que os pais conseguem viajar para ver ele " +
                "jogando profissionalmente — a motivação extra está estampada na cara.",
            targetPlayerId        = player.id,
            targetPlayerName      = player.nome_jogo,
            moodDelta             = 10,
            overallModifierDelta  = 3,
            durationDays          = 3
        )
    }

    // ── Templates: ESCÂNDALO (time inteiro) ─────────────────────────────

    private fun buildScandal(state: GameState, roster: List<Player>): OffMatchEvent {
        val player = roster.random()
        return OffMatchEvent(
            id           = UUID.randomUUID().toString(),
            date         = state.currentDate,
            category     = OffMatchEventCategory.SCANDAL,
            sentiment    = OffMatchEventSentiment.NEGATIVE,
            title        = "Polêmica nas redes sociais",
            description  = "Um post antigo de ${player.nome_jogo} viralizou e foi mal interpretado " +
                "pela comunidade. A polêmica respingou no time inteiro — todos estão sendo " +
                "questionados em entrevistas. O ambiente no treino ficou pesado.",
            targetPlayerId        = null,  // afeta time todo
            targetPlayerName      = null,
            moodDelta             = -6
        )
    }

    // ── Templates: TREINO ───────────────────────────────────────────────

    private fun buildTrainingBreakthrough(state: GameState, roster: List<Player>): OffMatchEvent {
        // Afeta o time inteiro (descoberta tática coletiva)
        val teamWide = Random.nextDouble() < 0.5
        return if (teamWide) {
            OffMatchEvent(
                id           = UUID.randomUUID().toString(),
                date         = state.currentDate,
                category     = OffMatchEventCategory.TRAINING_BREAKTHROUGH,
                sentiment    = OffMatchEventSentiment.POSITIVE,
                title        = "Sinergia tática descoberta",
                description  = "Durante uma sessão de scrim, o time encontrou uma combinação " +
                    "tática poderosa que nunca tinha tentado. Treinaram exaustivamente a execução — " +
                    "vão entrar na próxima partida com uma carta na manga.",
                targetPlayerId        = null,
                moodDelta             = 8,
                overallModifierDelta  = 2,
                durationDays          = 5
            )
        } else {
            val player = roster.random()
            OffMatchEvent(
                id           = UUID.randomUUID().toString(),
                date         = state.currentDate,
                category     = OffMatchEventCategory.TRAINING_BREAKTHROUGH,
                sentiment    = OffMatchEventSentiment.POSITIVE,
                title        = "Insight individual",
                description  = "${player.nome_jogo} teve um insight estudando replays e descobriu " +
                    "uma micro-rotação que melhora consideravelmente sua lane phase. Treinou " +
                    "horas no modo prática e está confiante para aplicar no próximo jogo.",
                targetPlayerId        = player.id,
                targetPlayerName      = player.nome_jogo,
                moodDelta             = 6,
                overallModifierDelta  = 4,
                durationDays          = 5
            )
        }
    }

    // ── Templates: PATROCINADOR ─────────────────────────────────────────

    private fun buildSponsorVisit(state: GameState, roster: List<Player>): OffMatchEvent {
        val positive = Random.nextDouble() < 0.5
        return if (positive) {
            OffMatchEvent(
                id           = UUID.randomUUID().toString(),
                date         = state.currentDate,
                category     = OffMatchEventCategory.SPONSOR_VISIT,
                sentiment    = OffMatchEventSentiment.POSITIVE,
                title        = "Patrocinador empolgado",
                description  = "Um dos principais patrocinadores visitou a gaming house e " +
                    "elogiou bastante o desempenho recente do time. Prometeu um bônus extra " +
                    "se a próxima série for vencida — todo mundo está ligado.",
                targetPlayerId        = null,
                moodDelta             = 5,
                overallModifierDelta  = 1,
                durationDays          = 3
            )
        } else {
            OffMatchEvent(
                id           = UUID.randomUUID().toString(),
                date         = state.currentDate,
                category     = OffMatchEventCategory.SPONSOR_VISIT,
                sentiment    = OffMatchEventSentiment.NEGATIVE,
                title        = "Patrocinador exigente",
                description  = "O patrocinador veio à gaming house e cobrou resultado de forma " +
                    "ríspida. Os jogadores saíram da reunião pressionados e tensos — alguns " +
                    "questionaram se vale a pena tanto estresse.",
                targetPlayerId        = null,
                moodDelta             = -7,
                overallModifierDelta  = -2,
                durationDays          = 3
            )
        }
    }

    // ── Templates: TORCIDA ──────────────────────────────────────────────

    private fun buildFanSupport(state: GameState, roster: List<Player>): OffMatchEvent {
        return OffMatchEvent(
            id           = UUID.randomUUID().toString(),
            date         = state.currentDate,
            category     = OffMatchEventCategory.FAN_SUPPORT,
            sentiment    = OffMatchEventSentiment.POSITIVE,
            title        = "Torcida fez um vídeo",
            description  = "Um grupo de fãs organizou um vídeo de apoio enorme nas redes — " +
                "depoimentos, edits, montagens com as melhores jogadas do time. Todos os " +
                "jogadores assistiram juntos e ficaram visivelmente emocionados.",
            targetPlayerId        = null,
            moodDelta             = 7,
            overallModifierDelta  = 2,
            durationDays          = 3
        )
    }

    // ── Templates: TRAGÉDIA ─────────────────────────────────────────────

    private fun buildPersonalTragedy(state: GameState, roster: List<Player>): OffMatchEvent {
        val player = roster.random()
        return OffMatchEvent(
            id           = UUID.randomUUID().toString(),
            date         = state.currentDate,
            category     = OffMatchEventCategory.PERSONAL_TRAGEDY,
            sentiment    = OffMatchEventSentiment.NEGATIVE,
            title        = "Notícia difícil de casa",
            description  = "${player.nome_jogo} recebeu uma notícia pessoal muito difícil " +
                "envolvendo um parente próximo. Conversou com a comissão técnica e quer continuar " +
                "competindo, mas está claramente abalado. O time se mobilizou para apoiá-lo.",
            targetPlayerId        = player.id,
            targetPlayerName      = player.nome_jogo,
            moodDelta             = -20,
            overallModifierDelta  = -5,
            durationDays          = 14
        )
    }

    // ── Templates: JOGADA ENSAIADA (laço +) ─────────────────────────────

    /**
     * Dois jogadores criam uma jogada ensaiada que dá super certo — fortalece o
     * **laço** entre eles. Escolhe a dupla mais entrosada (via
     * [PlayerBondService.pickComboPair]) porque combos nascem de quem já se lê
     * bem. O efeito de laço é aplicado AQUI (não em applyEffects), pois envolve
     * dois jogadores; a moral leve vai para ambos.
     */
    private fun buildTeamCombo(state: GameState, roster: List<Player>): OffMatchEvent? {
        val pair = PlayerBondService.pickComboPair(state, roster) ?: return null
        val (a, b) = pair

        // Aplica o efeito de laço imediatamente (registra no histórico do bond).
        PlayerBondService.recordCombo(state, a.id, b.id, "Jogada ensaiada em treino")
        val bondDelta = com.cblol.scout.domain.usecase.PlayerBondService
            .bondBetween(state, a.id, b.id)?.history?.firstOrNull()?.delta ?: 0

        // Moral leve para os dois (o applyEffects cuida do primeiro; o segundo
        // aplicamos manualmente para não complicar o fluxo de targets).
        applyMoodViaMoraleService(state, b.id, COMBO_MOOD, "🎯 Jogada ensaiada")

        return OffMatchEvent(
            id           = UUID.randomUUID().toString(),
            date         = state.currentDate,
            category     = OffMatchEventCategory.TEAM_COMBO,
            sentiment    = OffMatchEventSentiment.POSITIVE,
            title        = "Jogada ensaiada nasceu",
            description  = "${a.nome_jogo} e ${b.nome_jogo} passaram o treino inteiro burilando uma " +
                "jogada combinada — e ela funcionou na perfeição no scrim. A dupla saiu do treino " +
                "rindo e mais entrosada do que nunca. A química entre os dois deu um salto.",
            targetPlayerId        = a.id,
            targetPlayerName      = a.nome_jogo,
            secondPlayerId        = b.id,
            secondPlayerName      = b.nome_jogo,
            bondDelta             = bondDelta,
            moodDelta             = COMBO_MOOD
        )
    }

    // ── Templates: BRIGA (laço -) ───────────────────────────────────

    /**
     * Dois jogadores se desentendem (briga no vestiário) — deteriora o **laço**
     * entre eles. Escolhe a dupla com pior química/humor (via
     * [PlayerBondService.pickFightPair]), pois conflitos nascem de atrito
     * pré-existente. Efeito de laço aplicado aqui; moral negativa para ambos.
     */
    private fun buildTeamFight(state: GameState, roster: List<Player>): OffMatchEvent? {
        val pair = PlayerBondService.pickFightPair(state, roster) ?: return null
        val (a, b) = pair

        PlayerBondService.recordFight(state, a.id, b.id, "Briga no vestiário")
        val bondDelta = com.cblol.scout.domain.usecase.PlayerBondService
            .bondBetween(state, a.id, b.id)?.history?.firstOrNull()?.delta ?: 0

        applyMoodViaMoraleService(state, b.id, FIGHT_MOOD, "🥊 Briga no vestiário")

        return OffMatchEvent(
            id           = UUID.randomUUID().toString(),
            date         = state.currentDate,
            category     = OffMatchEventCategory.TEAM_FIGHT,
            sentiment    = OffMatchEventSentiment.NEGATIVE,
            title        = "Briga no vestiário",
            description  = "${a.nome_jogo} e ${b.nome_jogo} bateram de frente feio depois de uma " +
                "discussão sobre a chamada de uma jogada. O clima ficou pesado e os dois mal se " +
                "falaram no resto do dia. A relação entre eles esfriou.",
            targetPlayerId        = a.id,
            targetPlayerName      = a.nome_jogo,
            secondPlayerId        = b.id,
            secondPlayerName      = b.nome_jogo,
            bondDelta             = bondDelta,
            moodDelta             = FIGHT_MOOD
        )
    }

    /** Moral aplicada a cada um da dupla numa jogada ensaiada. */
    private const val COMBO_MOOD = 5

    /** Moral aplicada a cada um da dupla numa briga. */
    private const val FIGHT_MOOD = -6
}
