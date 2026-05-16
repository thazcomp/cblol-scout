package com.cblol.scout.util

import com.cblol.scout.data.Champion
import com.cblol.scout.data.ChampionTag
import com.cblol.scout.data.Player

/**
 * Motor de sugestão de picks contextual.
 *
 * Avalia cada campeão disponível combinando múltiplas variáveis:
 *  - Quanto melhora a composição atual (CompositionRepository)
 *  - Se é um dos mains do jogador da role atual
 *  - Quão bem responde ao que o inimigo já pickou (counter)
 *  - Sinergia de tags com os picks já feitos (CC, anti-tank, dano misto…)
 *
 * Retorna até [MAX_SUGGESTIONS] sugestões com **motivos categorizados** para que a UI
 * exiba badges visuais distintos por reason ("MAIN", "COMP", "COUNTER", "SINERGIA").
 *
 * SOLID:
 * - **SRP**: cada heurística mora num scorer próprio ([scoreMain], [scoreCompFit], …).
 * - **OCP**: novas heurísticas se adicionam declarando um novo scorer e somando o
 *   resultado em [evaluate]; o pipeline geral não muda.
 * - **DIP**: depende apenas de [ChampionRepository], [CompositionRepository] e do
 *   modelo de domínio (Player, Champion, ChampionTag).
 */
object PickSuggestionEngine {

    /** Motivo categorizado de uma sugestão. Mapeado para badges coloridos na UI. */
    enum class Reason {
        MAIN,        // 🎯 É um dos mains do jogador → +bônus de força garantido
        COMPOSITION, // ⚡ Encaixa em uma comp que o time está montando
        COUNTER,     // 🛡 Contra-ataca uma característica do oponente
        SYNERGY,     // ✨ Preenche uma tag faltante (CC, anti-tank, AP/AD)
        META         // ⭐ Pick forte em qualquer situação (sem motivo específico)
    }

    /** Uma sugestão de pick com seu score total e a lista de motivos que a sustentam. */
    data class Suggestion(
        val champion: Champion,
        val score: Int,
        val reasons: List<ReasonItem>
    ) {
        /** Motivo principal (maior peso). Usado para o badge "destaque" da UI. */
        val primaryReason: Reason get() = reasons.maxByOrNull { it.weight }?.reason ?: Reason.META
    }

    /** Detalhe de um motivo individual: a categoria, peso e texto curto explicativo. */
    data class ReasonItem(
        val reason: Reason,
        val weight: Int,
        val label: String   // ex: "Main de Tinowns", "Quebra tank line"
    )

    /**
     * Calcula as melhores sugestões para o próximo pick do **jogador atual**.
     *
     * @param myPicks       campeões já pickados pelo meu time (case-original)
     * @param opponentPicks campeões já pickados pelo oponente
     * @param bans          todos os bans (meus + oponente)
     * @param currentPlayer jogador que vai pickar agora (role + championPool)
     * @param maxResults    máximo de sugestões (default [MAX_SUGGESTIONS])
     */
    fun suggest(
        myPicks: List<String>,
        opponentPicks: List<String>,
        bans: List<String>,
        currentPlayer: Player?,
        maxResults: Int = MAX_SUGGESTIONS
    ): List<Suggestion> {
        val used = (myPicks + opponentPicks + bans).map { it.lowercase() }.toSet()
        val role = currentPlayer?.role
        val pool = if (role != null) ChampionRepository.getByRole(role)
                   else              ChampionRepository.getAll()

        val myChamps = myPicks.mapNotNull { ChampionRepository.getById(it) }
        val opChamps = opponentPicks.mapNotNull { ChampionRepository.getById(it) }

        return pool
            .asSequence()
            .filter { it.id.lowercase() !in used && it.name.lowercase() !in used }
            .map { evaluate(it, myChamps, opChamps, currentPlayer, myPicks, opponentPicks, bans) }
            .filter { it.score >= MIN_SCORE }
            .sortedByDescending { it.score }
            .take(maxResults)
            .toList()
    }

    /** Avalia UM campeão somando todas as heurísticas. */
    private fun evaluate(
        candidate: Champion,
        myChamps: List<Champion>,
        opChamps: List<Champion>,
        player: Player?,
        myPicks: List<String>,
        opponentPicks: List<String>,
        bans: List<String>
    ): Suggestion {
        val reasons = mutableListOf<ReasonItem>()

        scoreMain(candidate, player)?.let               { reasons += it }
        scoreCompFit(candidate, myPicks, bans).forEach  { reasons += it }
        scoreCounter(candidate, opChamps).forEach       { reasons += it }
        scoreSynergy(candidate, myChamps, opChamps).forEach { reasons += it }

        if (reasons.isEmpty()) {
            // Pick "META": ainda assim pode ser bom — pontuamos por overall de tags fortes
            val metaBoost = metaScore(candidate)
            if (metaBoost > 0) {
                reasons += ReasonItem(Reason.META, metaBoost, LABEL_META)
            }
        }

        return Suggestion(
            champion = candidate,
            score    = reasons.sumOf { it.weight },
            reasons  = reasons
        )
    }

    // ── Heurísticas individuais (SRP) ────────────────────────────────────

    /**
     * MAIN — campeão está no champion pool do jogador atual.
     * Peso alto porque é determinístico (jogador joga melhor neste pick).
     */
    private fun scoreMain(candidate: Champion, player: Player?): ReasonItem? {
        if (player == null) return null
        val isMain = player.championPool.any { it.equals(candidate.id, ignoreCase = true) ||
                                                it.equals(candidate.name, ignoreCase = true) }
        return if (isMain) ReasonItem(Reason.MAIN, WEIGHT_MAIN, "Main de ${player.nome_jogo}")
               else null
    }

    /**
     * COMPOSITION — picar este campeão progride alguma comp que o time já está montando.
     * Compara o estado atual de comps detectadas com o estado HIPOTÉTICO (pegando ele).
     */
    private fun scoreCompFit(
        candidate: Champion,
        myPicks: List<String>,
        bans: List<String>
    ): List<ReasonItem> {
        val currentDetected  = CompositionRepository.analyzeAll(myPicks, bans)
        val hypotheticalPicks = myPicks + candidate.name
        val newDetected       = CompositionRepository.analyzeAll(hypotheticalPicks, bans)

        // Comps que ganhamos ou cresceram com este pick
        val gains = newDetected.mapNotNull { afterComp ->
            val before = currentDetected.find { it.composition.id == afterComp.composition.id }
            val delta  = afterComp.bonus - (before?.bonus ?: 0)
            if (delta > 0) afterComp to delta else null
        }
        return gains.map { (comp, delta) ->
            val tierBoost = when (comp.composition.tier) { "S" -> 3; "A" -> 2; else -> 1 }
            ReasonItem(
                reason = Reason.COMPOSITION,
                weight = delta + tierBoost,
                label  = "Encaixa em ${comp.composition.name} (Tier ${comp.composition.tier})"
            )
        }
    }

    /**
     * COUNTER — responde a uma característica do oponente.
     * Ex: oponente com 2+ tanks → anti-tank vira counter; squishies → assassino vira counter.
     */
    private fun scoreCounter(candidate: Champion, opChamps: List<Champion>): List<ReasonItem> {
        if (opChamps.isEmpty()) return emptyList()
        val items = mutableListOf<ReasonItem>()

        val opTanks = opChamps.count { it.hasTag(ChampionTag.TANK) || it.hasTag(ChampionTag.FIGHTER) }
        if (opTanks >= 2 && (candidate.hasTag(ChampionTag.ANTI_TANK) ||
                             candidate.hasTag(ChampionTag.TRUE_DAMAGE))) {
            items += ReasonItem(Reason.COUNTER, WEIGHT_COUNTER, "Quebra a tank line inimiga")
        }

        val opSquishies = opChamps.count { it.hasTag(ChampionTag.MARKSMAN) || it.hasTag(ChampionTag.MAGE) }
        if (opSquishies >= 2 && (candidate.hasTag(ChampionTag.ASSASSIN) ||
                                  candidate.hasTag(ChampionTag.BURST))) {
            items += ReasonItem(Reason.COUNTER, WEIGHT_COUNTER, "One-shot nos carries deles")
        }

        val opEngage = opChamps.count { it.hasTag(ChampionTag.ENGAGE) }
        if (opEngage >= 2 && (candidate.hasTag(ChampionTag.DISENGAGE) ||
                               candidate.hasTag(ChampionTag.SHIELD) ||
                               candidate.hasTag(ChampionTag.HEAL))) {
            items += ReasonItem(Reason.COUNTER, WEIGHT_COUNTER, "Disengage contra engage pesado")
        }

        val opHyper = opChamps.count { it.hasTag(ChampionTag.HYPERCARRY) ||
                                        it.hasTag(ChampionTag.LATE_GAME) }
        if (opHyper >= 2 && (candidate.hasTag(ChampionTag.EARLY_GAME) ||
                              candidate.hasTag(ChampionTag.SPLIT_PUSH))) {
            items += ReasonItem(Reason.COUNTER, WEIGHT_COUNTER_MILD, "Pressão antes do late inimigo")
        }

        val opPoke = opChamps.count { it.hasTag(ChampionTag.POKE) }
        if (opPoke >= 2 && (candidate.hasTag(ChampionTag.SUSTAIN) ||
                             candidate.hasTag(ChampionTag.HEAL) ||
                             candidate.hasTag(ChampionTag.ENGAGE))) {
            items += ReasonItem(Reason.COUNTER, WEIGHT_COUNTER_MILD, "Anula o poke deles")
        }

        return items
    }

    /**
     * SYNERGY — preenche uma "lacuna" do nosso time (sem AP, sem CC, sem frontline…).
     */
    private fun scoreSynergy(
        candidate: Champion,
        myChamps: List<Champion>,
        opChamps: List<Champion>
    ): List<ReasonItem> {
        val items = mutableListOf<ReasonItem>()
        if (myChamps.isEmpty()) return items

        val hasAD       = myChamps.any { it.hasTag(ChampionTag.PHYSICAL_DAMAGE) }
        val hasAP       = myChamps.any { it.hasTag(ChampionTag.MAGIC_DAMAGE) }
        val hasEngage   = myChamps.any { it.hasTag(ChampionTag.ENGAGE) }
        val hasCC       = myChamps.count { it.hasTag(ChampionTag.CROWD_CONTROL) } >= 2
        val hasTank     = myChamps.any { it.hasTag(ChampionTag.TANK) }
        val hasHeal     = myChamps.any { it.hasTag(ChampionTag.HEAL) || it.hasTag(ChampionTag.SHIELD) }

        if (!hasEngage && candidate.hasTag(ChampionTag.ENGAGE))
            items += ReasonItem(Reason.SYNERGY, WEIGHT_SYNERGY, "Time precisa de engage")
        if (!hasCC && candidate.hasTag(ChampionTag.CROWD_CONTROL))
            items += ReasonItem(Reason.SYNERGY, WEIGHT_SYNERGY, "Adiciona CC ao time")
        if (!hasTank && candidate.hasTag(ChampionTag.TANK))
            items += ReasonItem(Reason.SYNERGY, WEIGHT_SYNERGY, "Vira frontline do time")
        if (!hasAP && candidate.hasTag(ChampionTag.MAGIC_DAMAGE))
            items += ReasonItem(Reason.SYNERGY, WEIGHT_SYNERGY_MILD, "Equilibra com dano mágico")
        if (!hasAD && candidate.hasTag(ChampionTag.PHYSICAL_DAMAGE))
            items += ReasonItem(Reason.SYNERGY, WEIGHT_SYNERGY_MILD, "Equilibra com dano físico")
        if (!hasHeal && (candidate.hasTag(ChampionTag.HEAL) || candidate.hasTag(ChampionTag.SHIELD)))
            items += ReasonItem(Reason.SYNERGY, WEIGHT_SYNERGY_MILD, "Adiciona sustain")

        return items
    }

    /** Pontuação "META" — campeão tem tags de impacto sem motivo contextual. */
    private fun metaScore(candidate: Champion): Int {
        val flagshipTags = listOf(
            ChampionTag.GAME_CHANGING_ULT,
            ChampionTag.HYPERCARRY,
            ChampionTag.TEAMFIGHT,
            ChampionTag.OBJECTIVE_CONTROL
        )
        return flagshipTags.count { candidate.hasTag(it) }
    }

    // ── Constantes (companion) ───────────────────────────────────────────

    private const val MAX_SUGGESTIONS    = 3
    private const val MIN_SCORE          = 3

    private const val WEIGHT_MAIN        = 6
    private const val WEIGHT_COUNTER     = 4
    private const val WEIGHT_COUNTER_MILD = 2
    private const val WEIGHT_SYNERGY     = 3
    private const val WEIGHT_SYNERGY_MILD = 2

    private const val LABEL_META = "Pick forte do meta"
}
