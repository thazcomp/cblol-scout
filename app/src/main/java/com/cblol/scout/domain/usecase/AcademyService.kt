package com.cblol.scout.domain.usecase

import com.cblol.scout.data.Academy
import com.cblol.scout.data.AcademyProspect
import com.cblol.scout.data.AcademyTier
import com.cblol.scout.data.GameState
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Sistema de **categoria de base (academia)**.
 *
 * A academia é o pipeline de talentos da própria organização: jovens promessas
 * (16-19 anos) que começam com overall baixo mas têm **potencial** oculto, e
 * que o gerente desenvolve ao longo do tempo até promovê-los ao elenco
 * principal.
 *
 * **Como se diferencia dos outros sistemas:**
 *  - **2ª divisão / mercado** ([com.cblol.scout.util.SecondDivisionGenerator]):
 *    jogadores PRONTOS que você compra. Caro, imediato.
 *  - **Academia** (este serviço): talentos CRUS que você forma do zero. Barato
 *    no curto prazo (só manutenção semanal), mas leva tempo — e o retorno é
 *    incerto (o potencial pode ser baixo).
 *
 * **Ciclo de vida de um prospect:**
 *  1. **Recrutado** (gerado pela academia) com overall atual baixo + potencial
 *  *     oculto.
 *  *  2. **Avaliado** (opcional, custa dinheiro) → revela o potencial exato.
 *  3. **Desenvolvido** dia-a-dia: o overall sobe rumo ao potencial, mais rápido
 *     quanto melhor o tier da academia e quanto mais jovem o prospect.
 *  4. **Promovido** ao elenco principal quando o gerente decidir (vira um
 *     [com.cblol.scout.data.Player] de fato), OU **liberado** se não vingou.
 *  5. Se chegar a [AcademyProspect.MAX_ACADEMY_AGE] sem ser promovido, para de
 *     evoluir (envelheceu na base).
 *
 * **SOLID:**
 *  - **SRP**: cuida só da academia. A promoção em si (criar o Player no roster)
 *    é delegada ao chamador via [PromotionResult] — o serviço não conhece
 *    Android/Repository.
 *  - **OCP**: novos tiers entram no enum [AcademyTier]; novos gatilhos de
 *    desenvolvimento entram em [developmentStepFor] sem mexer no tick.
 *  - **DIP**: JVM-puro; opera sobre [GameState]. Sem Android.
 */
object AcademyService {

    // ── Constantes de desenvolvimento ───────────────────────────────────

    /** A cada quantos dias o prospect dá um "passo" de desenvolvimento. */
    private const val DEV_INTERVAL_DAYS = 7

    /** Ganho-base de overall por passo de desenvolvimento (antes de fatores). */
    private const val BASE_GROWTH_PER_STEP = 2

    /** Intervalo (dias) entre recrutamentos automáticos de novos talentos. */
    private const val RECRUIT_INTERVAL_DAYS = 30

    /** Custo para avaliar (revelar o potencial de) um prospect. */
    const val EVALUATION_COST = 15_000L

    /** Custo para recrutar manualmente um novo talento (fora do automático). */
    const val MANUAL_RECRUIT_COST = 50_000L

    /** Tamanho máximo do log de desenvolvimento por prospect. */
    private const val DEV_LOG_MAX = 8

    private val ROLES = listOf("TOP", "JNG", "MID", "ADC", "SUP")

    // ── Acesso ──────────────────────────────────────────────────────────

    /** Garante a existência (não-nula) da academia no estado. */
    fun academyOf(state: GameState): Academy {
        var a = state.academy
        if (a == null) {
            a = Academy()
            state.academy = a
        }
        return a
    }

    /** Tier atual da academia. */
    fun tier(state: GameState): AcademyTier = academyOf(state).tier

    /** Prospects atuais (lista não-nula). */
    fun prospects(state: GameState): List<AcademyProspect> = academyOf(state).prospects

    /** Prospect por id, ou null. */
    fun prospectById(state: GameState, id: String): AcademyProspect? =
        academyOf(state).prospects.find { it.id == id }

    /** Há espaço para mais um prospect? */
    fun hasFreeSlot(state: GameState): Boolean =
        academyOf(state).prospects.size < tier(state).capacity

    // ── Inicialização ───────────────────────────────────────────────────

    /**
     * Inicializa a academia na criação da carreira: cria a estrutura BASIC e
     * recruta uma leva inicial de prospects (metade da capacidade), para o
     * gerente já ter com o que trabalhar.
     */
    fun initializeForNewCareer(state: GameState) {
        val academy = academyOf(state)
        if (academy.prospects.isNotEmpty()) return  // já inicializada

        val initialCount = (academy.tier.capacity / 2).coerceAtLeast(2)
        repeat(initialCount) {
            val prospect = generateProspect(state, academy.tier)
            academy.prospects.add(prospect)
        }
        academy.lastRecruitDate = state.currentDate
    }

    // ── Geração de prospects ────────────────────────────────────────────

    /**
     * Gera um novo prospect coerente com o [tier] da academia. Overall atual
     * baixo (45-62), potencial sorteado até o teto do tier, idade 16-19.
     */
    fun generateProspect(state: GameState, tier: AcademyTier, rng: Random = Random.Default): AcademyProspect {
        val role = ROLES.random(rng)
        val idade = rng.nextInt(16, 20)  // 16-19

        // Overall atual baixo — é um projeto, não um produto pronto.
        val currentOverall = rng.nextInt(45, 63)

        // Potencial: piso acima do overall atual, teto limitado pelo tier.
        // Tiers melhores tendem a recrutar talentos com teto mais alto.
        val potentialFloor = (currentOverall + 8).coerceAtMost(tier.maxPotential)
        val potential = rng.nextInt(potentialFloor, tier.maxPotential + 1)

        val seq = academyOf(state).prospects.size + state.currentDate.hashCode().and(0xFFFF)
        val id = "prospect_${role.lowercase()}_${seq}_${rng.nextInt(1000, 9999)}"

        return AcademyProspect(
            id = id,
            nome = NAMES_TAGS.random(rng),
            nomeReal = "${FIRST_NAMES.random(rng)} ${LAST_NAMES.random(rng)}",
            role = role,
            idade = idade,
            currentOverall = currentOverall,
            potential = potential,
            evaluated = false,
            joinedOn = state.currentDate,
            championPool = pickPool(role, rng)
        )
    }

    // ── Tick diário (desenvolvimento + recrutamento) ────────────────────

    /** Resultado de um tick: marcos de desenvolvimento e novos recrutas. */
    data class TickResult(
        val readyNow: List<AcademyProspect> = emptyList(),   // ficaram prontos hoje
        val recruited: List<AcademyProspect> = emptyList()   // novos talentos chegaram
    )

    /**
     * Avança o desenvolvimento da academia. Idempotente por data via
     * [GameState.lastAcademyTickDate]. Para cada dia decorrido:
     *  - prospects acumulam desenvolvimento; a cada [DEV_INTERVAL_DAYS] dias
     *    ganham overall rumo ao potencial (modulado por tier e idade);
     *  - a cada [RECRUIT_INTERVAL_DAYS] dias, se houver vaga, recruta um novo
     *    talento automaticamente.
     *
     * @return marcos para o motor logar (prospects prontos, novos recrutas).
     */
    fun tickDaily(state: GameState): TickResult {
        val today = runCatching { LocalDate.parse(state.currentDate) }.getOrNull()
            ?: return TickResult()

        val last = state.lastAcademyTickDate?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val days = if (last == null) 1 else ChronoUnit.DAYS.between(last, today).toInt()
        if (days <= 0) {
            state.lastAcademyTickDate = state.currentDate
            return TickResult()
        }
        state.lastAcademyTickDate = state.currentDate

        val academy = academyOf(state)
        val readyNow = mutableListOf<AcademyProspect>()
        val growthFactor = academy.tier.growthFactor

        academy.prospects.forEach { p ->
            val wasReady = p.isReady()

            // Desenvolvimento: só evolui se ainda não atingiu o potencial e não
            // envelheceu na base.
            if (p.currentOverall < p.potential && p.idade < AcademyProspect.MAX_ACADEMY_AGE) {
                // Quantos passos de DEV_INTERVAL_DAYS couberam neste avanço.
                val steps = (days / DEV_INTERVAL_DAYS) +
                    if (Random.Default.nextInt(DEV_INTERVAL_DAYS) < (days % DEV_INTERVAL_DAYS)) 1 else 0
                if (steps > 0) {
                    val gain = developmentStepFor(p, growthFactor) * steps
                    if (gain > 0) {
                        val newOverall = (p.currentOverall + gain).coerceAtMost(p.potential)
                        if (newOverall != p.currentOverall) {
                            p.currentOverall = newOverall
                            addDevLog(p, "${state.currentDate}: evoluiu para overall $newOverall")
                        }
                    }
                }
            }

            if (!wasReady && p.isReady()) readyNow += p
        }

        // Recrutamento automático periódico.
        val recruited = mutableListOf<AcademyProspect>()
        val lastRecruit = academy.lastRecruitDate?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val daysSinceRecruit = if (lastRecruit == null) RECRUIT_INTERVAL_DAYS
                               else ChronoUnit.DAYS.between(lastRecruit, today).toInt()
        if (daysSinceRecruit >= RECRUIT_INTERVAL_DAYS && hasFreeSlot(state)) {
            val newProspect = generateProspect(state, academy.tier)
            academy.prospects.add(newProspect)
            academy.lastRecruitDate = state.currentDate
            recruited += newProspect
        }

        return TickResult(readyNow = readyNow, recruited = recruited)
    }

    /**
     * Ganho de overall por passo de desenvolvimento, modulado por:
     *  - **tier** da academia ([AcademyTier.growthFactor]) — estrutura melhor
     *    forma mais rápido;
     *  - **idade** — quanto mais jovem, mais rápido aprende (curva de talento);
     *  - **gap** até o potencial — fica mais lento perto do teto (rendimentos
     *    decrescentes).
     */
    private fun developmentStepFor(p: AcademyProspect, growthFactor: Double): Int {
        val gap = p.potential - p.currentOverall
        if (gap <= 0) return 0

        // Jovens (16) aprendem ~30% mais rápido que os mais velhos (19).
        val ageFactor = when (p.idade) {
            16   -> 1.3
            17   -> 1.15
            18   -> 1.0
            else -> 0.8
        }

        // Rendimento decrescente perto do teto.
        val gapFactor = if (gap <= 4) 0.5 else 1.0

        val raw = BASE_GROWTH_PER_STEP * growthFactor * ageFactor * gapFactor
        return raw.roundToInt().coerceIn(1, gap)
    }

    // ── Avaliação (revelar potencial) ───────────────────────────────────

    enum class EvaluateResult { OK, ALREADY_EVALUATED, NOT_FOUND, INSUFFICIENT_FUNDS }

    /**
     * Avalia um prospect, revelando seu potencial exato. Custa [EVALUATION_COST].
     */
    fun evaluateProspect(state: GameState, prospectId: String): EvaluateResult {
        val prospect = prospectById(state, prospectId) ?: return EvaluateResult.NOT_FOUND
        if (prospect.evaluated) return EvaluateResult.ALREADY_EVALUATED
        if (state.budget < EVALUATION_COST) return EvaluateResult.INSUFFICIENT_FUNDS
        state.budget -= EVALUATION_COST
        prospect.evaluated = true
        return EvaluateResult.OK
    }

    // ── Recrutamento manual ─────────────────────────────────────────────

    enum class RecruitResult { OK, CAPACITY_FULL, INSUFFICIENT_FUNDS }

    /**
     * Recruta manualmente um novo talento (paga [MANUAL_RECRUIT_COST]). Útil
     * quando o gerente quer encher a base sem esperar o ciclo automático.
     */
    fun recruitManually(state: GameState): Pair<RecruitResult, AcademyProspect?> {
        if (!hasFreeSlot(state)) return RecruitResult.CAPACITY_FULL to null
        if (state.budget < MANUAL_RECRUIT_COST) return RecruitResult.INSUFFICIENT_FUNDS to null
        state.budget -= MANUAL_RECRUIT_COST
        val prospect = generateProspect(state, tier(state))
        academyOf(state).prospects.add(prospect)
        return RecruitResult.OK to prospect
    }

    // ── Liberar prospect ────────────────────────────────────────────────

    /** Remove um prospect da base (dispensado). Não devolve custo. */
    fun releaseProspect(state: GameState, prospectId: String): Boolean =
        academyOf(state).prospects.removeAll { it.id == prospectId }

    // ── Promoção ao elenco principal ────────────────────────────────────

    /**
     * Dados necessários para o chamador (Repository/UI) materializar o prospect
     * como um [com.cblol.scout.data.Player] no elenco principal. O serviço NÃO
     * cria o Player diretamente para não acoplar a camada de domínio ao
     * snapshot/Android (DIP) — devolve os dados e remove o prospect da base.
     *
     * @property suggestedSalary salário mensal sugerido para o contrato base
     */
    data class PromotionResult(
        val prospect: AcademyProspect,
        val suggestedSalary: Long
    )

    enum class PromoteError { NOT_FOUND }

    /**
     * Promove um prospect: remove-o da base e devolve os dados para o chamador
     * criar o jogador no elenco. Salário sugerido escala com o overall atual.
     *
     * @return [PromotionResult] em caso de sucesso, ou null se o prospect não
     *   foi encontrado.
     */
    fun promoteProspect(state: GameState, prospectId: String): PromotionResult? {
        val prospect = prospectById(state, prospectId) ?: return null
        academyOf(state).prospects.removeAll { it.id == prospectId }
        return PromotionResult(prospect, suggestedSalary = suggestedSalaryFor(prospect))
    }

    /** Salário mensal sugerido para um prospect promovido (jovem = barato). */
    fun suggestedSalaryFor(prospect: AcademyProspect): Long = when {
        prospect.currentOverall >= 75 -> 40_000L
        prospect.currentOverall >= 68 -> 25_000L
        prospect.currentOverall >= 60 -> 15_000L
        else                          -> 10_000L
    }

    // ── Upgrade de tier ─────────────────────────────────────────────────

    enum class UpgradeResult { OK, ALREADY_MAX, LOW_REPUTATION, INSUFFICIENT_FUNDS }

    /** Faz upgrade da academia para o próximo tier. */
    fun upgrade(state: GameState): UpgradeResult {
        val current = tier(state)
        val next = nextTier(current) ?: return UpgradeResult.ALREADY_MAX
        if (state.coachProfile.reputation < next.minReputation) return UpgradeResult.LOW_REPUTATION
        if (state.budget < next.upgradeCost) return UpgradeResult.INSUFFICIENT_FUNDS
        state.budget -= next.upgradeCost
        academyOf(state).tier = next
        return UpgradeResult.OK
    }

    fun nextTier(current: AcademyTier): AcademyTier? = when (current) {
        AcademyTier.BASIC -> AcademyTier.PRO
        AcademyTier.PRO   -> AcademyTier.ELITE
        AcademyTier.ELITE -> null
    }

    /** Custo de manutenção semanal do tier atual (descontado pelo motor). */
    fun weeklyMaintenanceCost(state: GameState): Long = tier(state).weeklyCost

    // ── Helpers internos ────────────────────────────────────────────────

    private fun addDevLog(p: AcademyProspect, entry: String) {
        p.developmentLog.add(0, entry)
        while (p.developmentLog.size > DEV_LOG_MAX) {
            p.developmentLog.removeAt(p.developmentLog.size - 1)
        }
    }

    private fun pickPool(role: String, rng: Random): List<String> {
        val pool = CHAMPION_POOLS[role] ?: emptyList()
        if (pool.isEmpty()) return emptyList()
        return pool.shuffled(rng).take(rng.nextInt(2, 4))
    }

    // ── Pools de nomes / campeões (estilo base brasileira) ──────────────

    private val NAMES_TAGS = listOf(
        "Pétala", "Júnior", "Garoto", "Novato", "Promessa", "Talento",
        "Estrela", "Joia", "Diamante", "Fênix", "Raio", "Cometa",
        "Aurora", "Ímpeto", "Brasa", "Lumen", "Vértice", "Ápice",
        "Nimbus", "Zênite", "Faísca", "Orion", "Pulsar", "Quasar"
    )

    private val FIRST_NAMES = listOf(
        "Lucas", "Pedro", "Gabriel", "Matheus", "João", "Felipe",
        "Bruno", "Rafael", "Vinícius", "Daniel", "Henrique", "Thiago",
        "Arthur", "Bernardo", "Davi", "Enzo", "Murilo", "Nicolas"
    )

    private val LAST_NAMES = listOf(
        "Silva", "Santos", "Oliveira", "Pereira", "Costa", "Rodrigues",
        "Almeida", "Souza", "Lima", "Carvalho", "Ferreira", "Martins",
        "Barbosa", "Rocha", "Dias", "Teixeira", "Nunes", "Mendes"
    )

    private val CHAMPION_POOLS = mapOf(
        "TOP" to listOf("Aatrox", "Camille", "Fiora", "Gnar", "Jax", "Ornn",
                        "Renekton", "Sett", "Gwen", "Jayce"),
        "JNG" to listOf("Viego", "LeeSin", "Vi", "Sejuani", "Maokai", "Diana",
                        "Wukong", "Graves", "Xinzhao", "Nidalee"),
        "MID" to listOf("Azir", "Ahri", "Akali", "Sylas", "Yone", "Orianna",
                        "LeBlanc", "Vladimir", "Cassiopeia", "Syndra"),
        "ADC" to listOf("Caitlyn", "Jinx", "Aphelios", "Lucian", "Varus",
                        "Xayah", "Ezreal", "Zeri", "Ashe", "Kaisa"),
        "SUP" to listOf("Thresh", "Nautilus", "Leona", "Lulu", "Karma",
                        "Rakan", "Braum", "Pyke", "Renata", "Alistar")
    )
}
