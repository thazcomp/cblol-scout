package com.cblol.scout.domain.usecase

import com.cblol.scout.data.GameState
import com.cblol.scout.data.Player
import com.cblol.scout.data.PlayerOverride
import com.cblol.scout.data.TrainingOutcome
import com.cblol.scout.data.TrainingSession
import com.cblol.scout.data.TrainingType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Sistema de treinos.
 *
 * Cada [TrainingType] tem chance de produzir um [TrainingOutcome] (GREAT a
 * DISASTER) dependendo da moral média do time e do level do técnico.
 * O outcome determina:
 *  - Quanto de moral é ganho/perdido pelos jogadores (via [MoraleService])
 *  - Se aplica modificador temporário de overall (ex: Boot Camp dá +5 overall por 14 dias)
 *  - Se há risco de lesão (Solo Queue e Boot Camp podem causar tilt/burnout)
 *  - Texto narrativo registrado no histórico
 *
 * **Como funciona:**
 *  - [canRunTraining] checa cooldown + orçamento — UI deve chamar antes de oferecer
 *  - [runTraining] executa o treino: desconta custo, avança N dias, aplica efeitos,
 *    registra no histórico e retorna a [TrainingSession] criada
 *  - [daysUntilAvailable] mostra quantos dias faltam para destravar o tipo (cooldown)
 *
 * **SOLID:**
 *  - **SRP**: cada tipo de treino tem seu próprio método `apply*` privado.
 *  - **OCP**: novos tipos = adicionar enum no [TrainingType] + clausula no
 *    [applyEffects]. Resto do código não muda.
 *  - **DIP**: depende só de [GameState], [Player] e [MoraleService]. JVM-puro.
 *
 * **Importante**: este service NÃO avança o calendário sozinho — apenas
 * consome dias via [com.cblol.scout.game.GameEngine.advanceDays] que a UI
 * chama. Isso garante que partidas pendentes e pagamentos rolam durante o
 * período de treino, mantendo consistência com o resto do jogo.
 */
object TrainingService {

    /** Máximo de entradas no histórico (mais antigas são descartadas). */
    const val HISTORY_MAX = 30

    // ── API pública: checks ────────────────────────────────────────────

    /**
     * Resultado de validação ANTES de tentar rodar um treino. UI usa isso
     * para mostrar mensagens específicas ("já fez hoje", "sem dinheiro").
     */
    sealed class Availability {
        object Available : Availability()
        data class OnCooldown(val daysRemaining: Int) : Availability()
        data class InsufficientFunds(val needed: Long, val current: Long) : Availability()
        /**
         * Há uma partida do gerente agendada DENTRO do período que o treino
         * consumiria. Treinar avançaria o calendário por cima desse dia de
         * jogo, fazendo a partida ser auto-simulada (o jogador perderia o
         * pick & ban manual). Por isso o treino é bloqueado até ele jogar.
         *
         * @property matchDateIso data ISO da partida que bloqueia o treino
         * @property daysUntilMatch dias a partir de hoje até a partida
         */
        data class MatchInWindow(val matchDateIso: String, val daysUntilMatch: Int) : Availability()
    }

    /** Verifica se o treino pode ser executado agora. */
    fun checkAvailability(state: GameState, type: TrainingType): Availability {
        val cooldownLeft = daysUntilAvailable(state, type)
        if (cooldownLeft > 0) return Availability.OnCooldown(cooldownLeft)
        if (state.budget < type.cost) {
            return Availability.InsufficientFunds(type.cost, state.budget)
        }
        // Bloqueia se uma partida do gerente cair dentro da janela do treino
        // — senão o avanço de dias auto-simularia o jogo dele (bug: "treino
        // sobrescreve dia de jogo").
        managerMatchWithin(state, type.durationDays)?.let { (iso, daysUntil) ->
            return Availability.MatchInWindow(iso, daysUntil)
        }
        return Availability.Available
    }

    /**
     * Procura a primeira partida PENDENTE do gerente cuja data caia no
     * intervalo `(hoje, hoje + durationDays]` — ou seja, que seria
     * ultrapassada se o treino avançasse [durationDays] dias.
     *
     * Retorna `(dataIso, diasAtéAData)` ou `null` se nenhuma partida do
     * gerente cair na janela. Treinos de duração 0 nunca bloqueiam.
     *
     * **Por que `<=`:** se o treino dura 3 dias e há jogo exatamente no 3º
     * dia, treinar passaria por cima dele. Já um jogo HOJE (dia 0, ainda não
     * jogado) também bloqueia, pois o jogador deve resolver a partida do dia
     * antes de gastar dias treinando.
     */
    fun managerMatchWithin(state: GameState, durationDays: Int): Pair<String, Int>? {
        val today = runCatching { LocalDate.parse(state.currentDate) }.getOrNull() ?: return null
        val limit = today.plusDays(durationDays.toLong())
        val managerId = state.managerTeamId

        return state.matches
            .asSequence()
            .filter { !it.played }
            .filter { it.homeTeamId == managerId || it.awayTeamId == managerId }
            .mapNotNull { m ->
                val d = runCatching { LocalDate.parse(m.date) }.getOrNull() ?: return@mapNotNull null
                d to m
            }
            // Partida de hoje (inclusive) até o fim da janela do treino.
            .filter { (d, _) -> !d.isBefore(today) && !d.isAfter(limit) }
            .minByOrNull { (d, _) -> d }
            ?.let { (d, _) ->
                d.toString() to ChronoUnit.DAYS.between(today, d).toInt()
            }
    }

    /** Quantos dias faltam para o treino sair do cooldown (0 se já liberado). */
    fun daysUntilAvailable(state: GameState, type: TrainingType): Int {
        val last = state.lastTrainingByType?.get(type.name) ?: return 0
        val lastDate = runCatching { LocalDate.parse(last) }.getOrNull() ?: return 0
        val today = runCatching { LocalDate.parse(state.currentDate) }.getOrNull() ?: return 0
        val daysSince = ChronoUnit.DAYS.between(lastDate, today).toInt()
        return (type.cooldownDays - daysSince).coerceAtLeast(0)
    }

    // ── API pública: execução ──────────────────────────────────────────

    /**
     * Executa o treino. Pré-requisito: [checkAvailability] retornou
     * [Availability.Available]. Caso contrário, retorna null.
     *
     * **NÃO avança o calendário** — a UI deve, antes de chamar este método,
     * orquestrar o `GameEngine.advanceDays(type.durationDays)` para descontar
     * os dias consumidos pelo treino.
     */
    fun runTraining(
        state: GameState,
        type: TrainingType,
        roster: List<Player>
    ): TrainingSession? {
        if (checkAvailability(state, type) !is Availability.Available) return null

        // Ensure maps/lists are not null (backward compatibility with old saves)
        if (state.lastTrainingByType == null) state.lastTrainingByType = mutableMapOf()
        if (state.trainingHistory == null) state.trainingHistory = mutableListOf()

        state.budget -= type.cost
        state.lastTrainingByType!![type.name] = state.currentDate

        val outcome  = rollOutcome(state, roster)
        val summary  = applyEffects(state, type, outcome, roster)
        val session  = TrainingSession(
            date    = state.currentDate,
            type    = type,
            outcome = outcome,
            cost    = type.cost,
            summary = summary
        )
        state.trainingHistory!!.add(0, session)
        while (state.trainingHistory!!.size > HISTORY_MAX) {
            state.trainingHistory!!.removeAt(state.trainingHistory!!.lastIndex)
        }
        return session
    }

    // ── Sorteio do outcome ─────────────────────────────────────────────

    /**
     * Sorteia o resultado do treino. Probabilidades são baseadas em:
     *  - Moral média do roster (maior = mais chance de sucesso)
     *  - Level do técnico (cap melhor a curva)
     *
     * Distribuição base (técnico level 1, moral média 50):
     *  - DISASTER:  5%
     *  - BAD:      15%
     *  - NEUTRAL:  35%
     *  - GOOD:     35%
     *  - GREAT:    10%
     *
     * A cada +1 level do técnico, transfere 0.5% de DISASTER+BAD para
     * GOOD+GREAT. A cada +10 de moral média acima de 50, transfere mais
     * 2% para GREAT.
     */
    private fun rollOutcome(state: GameState, roster: List<Player>): TrainingOutcome {
        val avgMood = if (roster.isEmpty()) 50 else {
            roster.map { MoraleService.moodOf(state, it.id) }.average().toInt()
        }
        val coachLevel = state.coachProfile.let {
            CoachProgressionService.compute(it, state.managerName).level
        }
        // Bônus de "boost" — em pontos percentuais empurrados de baixo para cima
        val moodBoost  = ((avgMood - 50) / 10.0).coerceIn(-5.0, 5.0)  // -5..+5
        val coachBoost = (coachLevel * 0.5).coerceAtMost(15.0)        // até +15

        val disasterPct = (5.0 - coachBoost * 0.3 - moodBoost * 0.3).coerceAtLeast(1.0)
        val badPct      = (15.0 - coachBoost * 0.5 - moodBoost * 0.5).coerceAtLeast(3.0)
        val greatPct    = (10.0 + coachBoost * 0.8 + moodBoost * 0.8).coerceAtMost(40.0)
        val neutralPct  = 35.0
        val goodPct     = (100.0 - disasterPct - badPct - greatPct - neutralPct).coerceAtLeast(5.0)

        val roll = Random.nextDouble() * 100.0
        var cumulative = 0.0
        cumulative += disasterPct; if (roll < cumulative) return TrainingOutcome.DISASTER
        cumulative += badPct;      if (roll < cumulative) return TrainingOutcome.BAD
        cumulative += neutralPct;  if (roll < cumulative) return TrainingOutcome.NEUTRAL
        cumulative += goodPct;     if (roll < cumulative) return TrainingOutcome.GOOD
        return TrainingOutcome.GREAT
    }

    // ── Aplicação de efeitos ───────────────────────────────────────────

    /**
     * Aplica os efeitos do treino conforme o tipo + outcome.
     * Retorna texto narrativo curto descrevendo o que aconteceu.
     */
    private fun applyEffects(
        state: GameState,
        type: TrainingType,
        outcome: TrainingOutcome,
        roster: List<Player>
    ): String = when (type) {
        TrainingType.SCRIM         -> applyScrim(state, outcome, roster)
        TrainingType.VOD_REVIEW    -> applyVodReview(state, outcome, roster)
        TrainingType.SOLO_QUEUE    -> applySoloQueue(state, outcome, roster)
        TrainingType.GYM           -> applyGym(state, outcome, roster)
        TrainingType.TEAM_BUILDING -> applyTeamBuilding(state, outcome, roster)
        TrainingType.BOOT_CAMP     -> applyBootCamp(state, outcome, roster)
    }

    // ── Efeitos específicos por treino ─────────────────────────────────

    private fun applyScrim(state: GameState, outcome: TrainingOutcome, roster: List<Player>): String {
        return when (outcome) {
            TrainingOutcome.GREAT -> {
                applyOverallBoost(state, roster, +5, days = 7, reason = "Scrim excelente")
                bumpMoral(state, roster, +8, "Scrim excelente")
                "O time desmontou o adversário em scrim. Confiança lá em cima e estratégias afiadas para a próxima série."
            }
            TrainingOutcome.GOOD -> {
                applyOverallBoost(state, roster, +3, days = 5, reason = "Bom scrim")
                bumpMoral(state, roster, +5, "Bom scrim")
                "Scrim produtivo. O time testou composições e saiu com aprendizado claro."
            }
            TrainingOutcome.NEUTRAL -> {
                bumpMoral(state, roster, +1, "Scrim regular")
                "Scrim equilibrado, com vitórias e derrotas. Sem grandes revelações."
            }
            TrainingOutcome.BAD -> {
                bumpMoral(state, roster, -5, "Scrim fraco")
                "O time perdeu vários scrims. Saiu da sessão frustrado e questionando a estratégia."
            }
            TrainingOutcome.DISASTER -> {
                bumpMoral(state, roster, -10, "Desastre em scrim")
                "Massacre completo. O time saiu desmoralizado e duvidando da preparação para a próxima série."
            }
        }
    }

    private fun applyVodReview(state: GameState, outcome: TrainingOutcome, roster: List<Player>): String {
        return when (outcome) {
            TrainingOutcome.GREAT -> {
                applyOverallBoost(state, roster, +4, days = 10, reason = "VOD esclarecedor")
                bumpMoral(state, roster, +3, "Revisão de VOD reveladora")
                "A revisão revelou padrões cruciais nos adversários. O time tem agora um plano de jogo claro."
            }
            TrainingOutcome.GOOD -> {
                applyOverallBoost(state, roster, +2, days = 7, reason = "Boa revisão de VOD")
                bumpMoral(state, roster, +2, "Boa revisão de VOD")
                "Sessão produtiva — alguns erros recorrentes foram identificados e discutidos."
            }
            TrainingOutcome.NEUTRAL -> {
                "Revisão normal de partidas. Anotações feitas, mas nada de extraordinário."
            }
            TrainingOutcome.BAD -> {
                bumpMoral(state, roster, -3, "VOD com brigas")
                "A discussão sobre erros virou uma briga entre jogadores. A sessão acabou cedo."
            }
            TrainingOutcome.DISASTER -> {
                bumpMoral(state, roster, -6, "VOD desastrosa")
                "A revisão expôs problemas profundos no time. Egos feridos, clima péssimo na gaming house."
            }
        }
    }

    private fun applySoloQueue(state: GameState, outcome: TrainingOutcome, roster: List<Player>): String {
        return when (outcome) {
            TrainingOutcome.GREAT -> {
                applyOverallBoost(state, roster, +4, days = 7, reason = "Solo queue em fogo")
                bumpMoral(state, roster, +5, "Solo queue em alta")
                "Os jogadores subiram MMR e adquiriram confiança individual. Mecânica e tomada de decisão melhoraram."
            }
            TrainingOutcome.GOOD -> {
                applyOverallBoost(state, roster, +2, days = 5, reason = "Solo queue produtivo")
                "Sessão produtiva. Cada jogador testou novos picks e refinou padrões."
            }
            TrainingOutcome.NEUTRAL -> {
                "Solo queue normal. Resultados mistos individualmente."
            }
            TrainingOutcome.BAD -> {
                bumpMoral(state, roster, -7, "Tilt geral")
                // Aplica modificador NEGATIVO temporário em 1-2 jogadores aleatórios
                roster.shuffled().take(2).forEach { player ->
                    applyOverallBoost(state, listOf(player), -2, days = 3, reason = "Tilt de solo queue")
                }
                "Sessão terrível. Vários losing streaks, jogadores tiltados e quebrando setup."
            }
            TrainingOutcome.DISASTER -> {
                bumpMoral(state, roster, -12, "Tilt catastrófico")
                roster.forEach { player ->
                    applyOverallBoost(state, listOf(player), -3, days = 5, reason = "Burnout de solo queue")
                }
                "Catástrofe. Os jogadores ficaram noites jogando sem dormir e estão em burnout grave."
            }
        }
    }

    private fun applyGym(state: GameState, outcome: TrainingOutcome, roster: List<Player>): String {
        return when (outcome) {
            TrainingOutcome.GREAT -> {
                applyOverallBoost(state, roster, +2, days = 14, reason = "Forma física excelente")
                bumpMoral(state, roster, +8, "Academia rendeu muito")
                "Os jogadores se sentiram revigorados. Postura, foco e disposição em alta."
            }
            TrainingOutcome.GOOD -> {
                applyOverallBoost(state, roster, +1, days = 10, reason = "Bom treino físico")
                bumpMoral(state, roster, +5, "Bom treino físico")
                "Treino físico produtivo. Energia visivelmente maior nos dias seguintes."
            }
            TrainingOutcome.NEUTRAL -> {
                bumpMoral(state, roster, +2, "Treino físico ok")
                "Treino físico básico, sem grandes destaques."
            }
            TrainingOutcome.BAD -> {
                bumpMoral(state, roster, -2, "Reclamações pós-treino")
                "Alguns jogadores reclamaram da intensidade. Saíram cansados e mal-humorados."
            }
            TrainingOutcome.DISASTER -> {
                // Lesão leve em 1 jogador aleatório
                val unlucky = roster.randomOrNull()
                if (unlucky != null) {
                    applyOverallBoost(state, listOf(unlucky), -4, days = 7,
                        reason = "Lesão no treino físico")
                }
                bumpMoral(state, roster, -5, "Lesão no treino físico")
                val name = unlucky?.nome_jogo ?: "Um jogador"
                "$name se machucou durante o treino físico. Vai precisar pegar leve por uma semana."
            }
        }
    }

    private fun applyTeamBuilding(state: GameState, outcome: TrainingOutcome, roster: List<Player>): String {
        return when (outcome) {
            TrainingOutcome.GREAT -> {
                bumpMoral(state, roster, +15, "Team building memorável")
                "Atividade incrível — todos voltaram unidos, rindo e com clima excelente na casa."
            }
            TrainingOutcome.GOOD -> {
                bumpMoral(state, roster, +10, "Bom team building")
                "Atividade agradável. O grupo se aproximou e o ambiente melhorou notavelmente."
            }
            TrainingOutcome.NEUTRAL -> {
                bumpMoral(state, roster, +5, "Team building ok")
                "Atividade normal. Alguns gostaram, outros nem tanto, mas serviu como distração."
            }
            TrainingOutcome.BAD -> {
                bumpMoral(state, roster, -3, "Team building frustrante")
                "A atividade foi mal escolhida e gerou pequenos atritos entre os jogadores."
            }
            TrainingOutcome.DISASTER -> {
                bumpMoral(state, roster, -8, "Team building tóxico")
                "A atividade gerou uma briga séria entre dois jogadores. O clima na casa azedou."
            }
        }
    }

    private fun applyBootCamp(state: GameState, outcome: TrainingOutcome, roster: List<Player>): String {
        return when (outcome) {
            TrainingOutcome.GREAT -> {
                applyOverallBoost(state, roster, +7, days = 21, reason = "Boot camp lendário")
                bumpMoral(state, roster, +10, "Boot camp lendário")
                "O boot camp transformou o time. Estratégias novas, sinergia perfeita e altíssima confiança."
            }
            TrainingOutcome.GOOD -> {
                applyOverallBoost(state, roster, +5, days = 18, reason = "Bom boot camp")
                bumpMoral(state, roster, +6, "Bom boot camp")
                "Sete dias de imersão pagaram bem — todos saíram mais afiados e alinhados."
            }
            TrainingOutcome.NEUTRAL -> {
                applyOverallBoost(state, roster, +2, days = 10, reason = "Boot camp regular")
                bumpMoral(state, roster, -2, "Boot camp cansativo")
                "O boot camp foi cansativo mas teve seu valor. Algumas melhorias, alguma exaustão."
            }
            TrainingOutcome.BAD -> {
                applyOverallBoost(state, roster, -3, days = 7, reason = "Boot camp mal aproveitado")
                bumpMoral(state, roster, -10, "Boot camp esgotante")
                "O boot camp drenou a energia do time. Conflitos surgiram e pouco foi aprendido."
            }
            TrainingOutcome.DISASTER -> {
                applyOverallBoost(state, roster, -5, days = 14, reason = "Boot camp catastrófico")
                bumpMoral(state, roster, -18, "Boot camp catastrófico")
                "Tragédia. O boot camp foi mal planejado, ninguém aguentou a pressão e o time saiu pior do que entrou."
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Aplica modificador de overall temporário a um grupo de jogadores. */
    private fun applyOverallBoost(
        state: GameState,
        players: List<Player>,
        delta: Int,
        days: Int,
        reason: String
    ) {
        if (delta == 0 || days == 0 || players.isEmpty()) return
        val today = runCatching { LocalDate.parse(state.currentDate) }.getOrNull() ?: return
        val expires = today.plusDays(days.toLong()).toString()
        players.forEach { player ->
            val existing = state.playerOverrides[player.id] ?: PlayerOverride(player.id)
            // Se já tem um modificador ativo, SOMA (não substitui) — boot camp em cima
            // de gym deveria empilhar. Mas para evitar abuso, limita em ±10.
            val existingDelta = existing.overallModifier
            val combined = (existingDelta + delta).coerceIn(-10, 10)
            state.playerOverrides[player.id] = existing.copy(
                overallModifier          = combined,
                overallModifierExpiresOn = expires,
                overallModifierReason    = reason
            )
        }
    }

    /** Aplica delta de moral a todos os jogadores da lista. */
    private fun bumpMoral(state: GameState, roster: List<Player>, delta: Int, reason: String) {
        if (delta == 0) return
        roster.forEach { player ->
            val current  = MoraleService.moodOf(state, player.id)
            val newValue = (current + delta).coerceIn(0, 100)
            val effective = newValue - current
            if (effective == 0) return@forEach

            val event = com.cblol.scout.data.MoodEvent(
                date       = state.currentDate,
                reason     = reason,
                delta      = effective,
                valueAfter = newValue
            )
            val existing = state.playerOverrides[player.id] ?: PlayerOverride(player.id)
            state.playerOverrides[player.id] = existing.copy(
                mood        = newValue,
                moodHistory = (listOf(event) + existing.moodHistory).take(MoraleService.HISTORY_MAX_ENTRIES)
            )
        }
    }
}
