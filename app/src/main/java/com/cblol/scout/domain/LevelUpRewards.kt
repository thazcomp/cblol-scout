package com.cblol.scout.domain

/**
 * Catálogo de **recompensas por level up** do técnico.
 *
 * Cada milestone (level específico) desbloqueia uma BADGE + um conjunto de
 * BÔNUS passivos que ficam ativos pelo resto da carreira. Levels que não são
 * milestones ainda mostram a tela de level up (com "continue evoluindo"), mas
 * sem nova badge — o ganho é só a curva natural dos atributos derivados do
 * técnico no [com.cblol.scout.domain.usecase.CoachProgressionService].
 *
 * **Filosofia de design:**
 *  - **Badges** = identidade narrativa do técnico. Aparecem no perfil.
 *  - **Bônus** = mecânicas concretas que aceleram o time. Lidos pelos serviços
 *    relevantes (MoraleService, ScoutingService, TrainingService, SponsorService,
 *    AcademyService, BankService) quando aplicam suas regras.
 *
 * **Por que essas escolhas de bônus?** Cada badge escala UM aspecto do jogo,
 * dando ao jogador a sensação de que cada level up "destrava" algo novo:
 *  - **Tático** (lv 2) — mais sugestões no pick & ban (early game = ajudar drafting)
 *  - **Carismático** (lv 4) — moral inicial dos contratados (estabiliza elenco)
 *  - **Mente Estratégica** (lv 7) — scouting mais rápido (informação mais cedo)
 *  - **Pai de Família** (lv 10) — moral + química acelerada (elenco coeso)
 *  - **Mestre do Draft** (lv 15) — treinos com mais chance de outcome bom
 *  - **Olho de Negociador** (lv 18) — juros do banco menores
 *  - **Negociador** (lv 20) — patrocínio extra + propostas acima do mercado
 *  - **Lenda Viva** (lv 25) — academia acelerada (semi-endgame: foco em base)
 *  - **Imortal** (lv 30) — combo de bônus (recompensa máxima)
 *
 * Os atributos derivados do técnico (leadership/drafting/etc.) continuam
 * crescendo independentemente — esses bônus são EXTRAS por cima da curva.
 *
 * **SOLID/OCP:** novo milestone = adicionar entrada em [milestones]. Os
 * serviços que leem [com.cblol.scout.data.CoachBonuses] vão pegar
 * automaticamente.
 *
 * @see com.cblol.scout.data.CoachBonuses
 * @see com.cblol.scout.data.CoachProfile.unlockedBadges
 */
object LevelUpRewards {

    /**
     * Recompensa de um milestone. Os bônus listados aqui se SOMAM ao
     * [com.cblol.scout.data.CoachBonuses] do jogador quando o milestone é
     * atingido (não substituem — somam, pra que técnicos avançados acumulem
     * todas as vantagens).
     *
     * @property level level em que é desbloqueado (inclusive)
     * @property badgeId chave estável da badge (não muda entre versões)
     * @property badgeEmoji ícone visual
     * @property badgeName nome curto da badge (mostrado nos cards)
     * @property description texto narrativo curto (1 frase)
     * @property bonusBullets lista de strings legíveis pra UI mostrar tipo
     *   "📈 +5 moral inicial em contratações" — uma por bônus aplicado
     * @property apply muta um [com.cblol.scout.data.CoachBonuses] aplicando os
     *   ganhos deste milestone. Mantém o "como aplicar" junto da declaração
     *   pra não dispersar a lógica.
     */
    data class LevelUpReward(
        val level: Int,
        val badgeId: String,
        val badgeEmoji: String,
        val badgeName: String,
        val description: String,
        val bonusBullets: List<String>,
        val apply: (com.cblol.scout.data.CoachBonuses) -> Unit
    )

    /**
     * Badge desbloqueada no início da carreira (level 1). Não é recompensa de
     * level up — fica na lista desde o começo pra o perfil ter algo a mostrar.
     */
    val initialBadge = LevelUpReward(
        level = 1,
        badgeId = "rookie",
        badgeEmoji = "🎯",
        badgeName = "Estreante",
        description = "Começou a jornada. Boa sorte, técnico!",
        bonusBullets = emptyList(),
        apply = { /* nenhum bônus */ }
    )

    /** Recompensas por milestone, ordenadas por level. */
    val milestones: List<LevelUpReward> = listOf(
        LevelUpReward(
            level = 2,
            badgeId = "tactical",
            badgeEmoji = "🎲",
            badgeName = "Tático",
            description = "Você lê o jogo. Aparece mais uma sugestão no pick & ban.",
            bonusBullets = listOf(
                "✨ +1 sugestão extra no Pick & Ban"
            ),
            apply = { it.extraPickBanSuggestions += 1 }
        ),
        LevelUpReward(
            level = 4,
            badgeId = "charismatic",
            badgeEmoji = "💬",
            badgeName = "Carismático",
            description = "Jogadores chegam motivados — seu carisma quebra o gelo.",
            bonusBullets = listOf(
                "🎭 +5 de moral inicial em jogadores contratados"
            ),
            apply = { it.contractedMoraleBonus += 5 }
        ),
        LevelUpReward(
            level = 7,
            badgeId = "strategist",
            badgeEmoji = "🧠",
            badgeName = "Mente Estratégica",
            description = "Seu departamento de olheiros traz informação um dia mais cedo.",
            bonusBullets = listOf(
                "🔍 -1 dia por nível de scouting"
            ),
            apply = { it.scoutingDaysReduction += 1 }
        ),
        LevelUpReward(
            level = 10,
            badgeId = "father",
            badgeEmoji = "👨‍👩‍👧",
            badgeName = "Pai de Família",
            description = "Vestiário leve, elenco coeso. Os laços se formam mais rápido.",
            bonusBullets = listOf(
                "🎭 +5 adicional de moral inicial em contratações",
                "🤝 Laços crescem 25% mais rápido"
            ),
            apply = {
                it.contractedMoraleBonus += 5
                it.bondGrowthMultiplier += 0.25
            }
        ),
        LevelUpReward(
            level = 15,
            badgeId = "draft_master",
            badgeEmoji = "🎴",
            badgeName = "Mestre do Draft",
            description = "Seus treinos surpreendem mais — outcome melhor é mais comum.",
            bonusBullets = listOf(
                "🏋️ +10% de chance de outcome bom em treinos"
            ),
            apply = { it.trainingOutcomeBonus += 10 }
        ),
        LevelUpReward(
            level = 18,
            badgeId = "negotiator_eye",
            badgeEmoji = "📈",
            badgeName = "Olho de Negociador",
            description = "Os bancos confiam em você — juros menores.",
            bonusBullets = listOf(
                "🏦 -2% nos juros de novos empréstimos"
            ),
            apply = { it.loanInterestReduction += 0.02 }
        ),
        LevelUpReward(
            level = 20,
            badgeId = "negotiator",
            badgeEmoji = "💼",
            badgeName = "Negociador",
            description = "Patrocinadores te pagam um plus e times rivais valorizam seus jogadores.",
            bonusBullets = listOf(
                "💰 +R$ 30.000/sem extra de patrocínio",
                "🔄 Propostas recebidas +10% sobre o mercado"
            ),
            apply = {
                it.sponsorWeeklyBonusBrl += 30_000L
                it.incomingOfferBonusPercent += 10
            }
        ),
        LevelUpReward(
            level = 25,
            badgeId = "living_legend",
            badgeEmoji = "🏆",
            badgeName = "Lenda Viva",
            description = "Sua categoria de base é referência — prospects crescem mais rápido.",
            bonusBullets = listOf(
                "🌱 Prospects da base crescem 25% mais rápido"
            ),
            apply = { it.academyGrowthMultiplier += 0.25 }
        ),
        LevelUpReward(
            level = 30,
            badgeId = "immortal",
            badgeEmoji = "⚡",
            badgeName = "Imortal",
            description = "Lenda absoluta. Tudo que você toca vira ouro.",
            bonusBullets = listOf(
                "🎭 +10 adicional de moral inicial em contratações",
                "🔍 -1 dia adicional por nível de scouting",
                "🌱 Academia +25% mais rápida adicional",
                "🏋️ +10% adicional em treinos",
                "💰 +R$ 50.000/sem extra de patrocínio"
            ),
            apply = {
                it.contractedMoraleBonus += 10
                it.scoutingDaysReduction += 1
                it.academyGrowthMultiplier += 0.25
                it.trainingOutcomeBonus += 10
                it.sponsorWeeklyBonusBrl += 50_000L
            }
        )
    )

    /** Catálogo completo (initialBadge + milestones), útil para a UI. */
    val all: List<LevelUpReward> by lazy { listOf(initialBadge) + milestones }

    /** Retorna a [LevelUpReward] para o level dado, ou null se não é milestone. */
    fun rewardFor(level: Int): LevelUpReward? =
        milestones.find { it.level == level }

    /** Retorna o catálogo de [LevelUpReward]s pelos badgeIds desbloqueados. */
    fun badgesFor(unlockedIds: List<String>): List<LevelUpReward> =
        all.filter { it.badgeId in unlockedIds }

    /** Próximo milestone após o level dado (null se já passou todos). */
    fun nextMilestoneAfter(level: Int): LevelUpReward? =
        milestones.firstOrNull { it.level > level }
}
